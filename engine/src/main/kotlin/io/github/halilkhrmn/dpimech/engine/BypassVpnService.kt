package io.github.halilkhrmn.dpimech.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.halilkhrmn.dpimech.core.ByeDpiCommand
import io.github.halilkhrmn.dpimech.core.IspLookup
import io.github.halilkhrmn.dpimech.core.LabRequest
import io.github.halilkhrmn.dpimech.core.LabResult
import io.github.halilkhrmn.dpimech.core.LabRunner
import io.github.halilkhrmn.dpimech.core.LabStrategy
import io.github.halilkhrmn.dpimech.core.StrategyFile
import io.github.halilkhrmn.dpimech.core.Hostlist
import io.github.halilkhrmn.dpimech.core.Profile
import io.github.halilkhrmn.dpimech.core.TunnelConfig
import io.github.halilkhrmn.dpimech.core.Socks5
import io.github.halilkhrmn.dpimech.core.StrategyEntry
import io.github.halilkhrmn.dpimech.core.VpnApps
import io.github.halilkhrmn.dpimech.core.Watchdog
import java.io.File
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Captures the selected apps' traffic with a TUN interface and hands it to ciadpi through
 * hev-socks5-tunnel. Nothing leaves the phone except the apps' own connections.
 */
class BypassVpnService : VpnService() {
    private val worker = Executors.newSingleThreadExecutor()
    private var tun: ParcelFileDescriptor? = null
    private var engine: Ciadpi? = null
    private var profile: Profile? = null
    private var engineArgs: List<String> = emptyList()
    private var enginePort = 0
    private var strategy: StrategyEntry? = null
    private var hostlist: File? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var networkJob: Job? = null
    private var dns = TunnelConfig(socksPort = 1).dns
    private var autoStrategy = false
    private var notifyStrategy = true
    private var notifyErrors = true
    /** Networks the automatic strategy already tested in this session. */
    private val autoTested = mutableSetOf<String>()
    @Volatile
    private var autoRun: Thread? = null
    private var watchdog: Thread? = null

    /** Set while a stop is intended, so the engine watcher does not report a crash. */
    @Volatile
    private var stopping = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> worker.execute { stopEngine(EngineState.Stopped) }
            ACTION_START -> {
                val json = intent.getStringExtra(EXTRA_PROFILE) ?: return START_NOT_STICKY
                val p = runCatching { Json.decodeFromString(Profile.serializer(), json) }.getOrElse {
                    EngineState.set(EngineState.Failed(null, it.message ?: "bad profile"))
                    return START_NOT_STICKY
                }
                dns = intent.getStringExtra(EXTRA_DNS) ?: dns
                autoStrategy = intent.getBooleanExtra(EXTRA_AUTO, false)
                notifyStrategy = intent.getBooleanExtra(EXTRA_NOTIFY_STRATEGY, true)
                notifyErrors = intent.getBooleanExtra(EXTRA_NOTIFY_ERRORS, true)
                goForeground(p)
                worker.execute { startEngine(p) }
            }
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        // Another VPN took over or the user turned us off in system settings.
        EngineLog.add("VPN permission revoked (another VPN started or turned off in settings)")
        if (notifyErrors && engine != null) event(getString(R.string.event_revoked))
        worker.execute { stopEngine(EngineState.Stopped) }
    }

    override fun onDestroy() {
        scope.cancel()
        worker.execute { stopEngine(EngineState.Stopped, stopService = false) }
        worker.shutdown()
        super.onDestroy()
    }

    private fun startEngine(p: Profile) {
        if (profile?.id != p.id) autoTested.clear()
        stopEngine(null, stopService = false)
        stopping = false
        EngineState.set(EngineState.Starting(p.id))
        try {
            val apps = VpnApps.plan(p, packageName, installedPackages()).getOrThrow()
            val lists = File(filesDir, "lists").apply { mkdirs() }
            val domains = p.domains
            val hostlist = if (domains.isEmpty()) {
                null
            } else {
                File(lists, "profile-${p.id.filter { it.isLetterOrDigit() }}.txt").apply { writeText(Hostlist.body(domains)) }
            }
            val port = Ciadpi.freePort()
            val entry = p.strategyFor(NetworkIdentity.flow.value?.networkKey)
            val args = buildArgs(p, entry, port, hostlist).getOrThrow()
            engine = Ciadpi.start(this, args, port, p.name)
            engineArgs = args
            enginePort = port
            strategy = entry
            this.hostlist = hostlist

            val config = TunnelConfig(socksPort = port, dns = dns)
            val builder = Builder()
                .setSession(p.name)
                .setMtu(config.mtu)
                .addAddress(config.ipv4, 32)
                .addRoute("0.0.0.0", 0)
                .addAddress(config.ipv6, 128)
                .addRoute("::", 0)
                .addDnsServer(config.dns)
                .setBlocking(false)
            apps.allowed.forEach { builder.addAllowedApplication(it) }
            apps.disallowed.forEach { builder.addDisallowedApplication(it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
            val fd = builder.establish() ?: error(getString(R.string.engine_error_no_permission))
            tun = fd

            val yaml = File(cacheDir, "hev.yml").apply { writeText(config.hevYaml()) }
            check(TProxy.TProxyStartService(yaml.path, fd.fd)) { "tunnel did not start" }

            profile = p
            startWatchdog(port)
            watchNetwork()
            EngineState.set(EngineState.Running(p.id, p.name, entry.name))
            EngineLog.add("started profile ${p.name} on port $port")
        } catch (e: Exception) {
            EngineLog.add("start failed: $e")
            stopEngine(EngineState.Failed(p.id, e.message ?: e.toString()))
        }
    }

    /**
     * Restarts ciadpi on the same port when it exits or stops answering, so hev-socks5-tunnel
     * keeps working without a new VPN interface. Gives up after a restart loop (see [Watchdog]).
     */
    private fun startWatchdog(port: Int) {
        val policy = Watchdog()
        watchdog = thread(name = "ciadpi-watchdog", isDaemon = true) {
            val started = System.nanoTime()
            while (!stopping) {
                try {
                    Thread.sleep(1000)
                } catch (_: InterruptedException) {
                    return@thread
                }
                val current = engine ?: return@thread
                val now = (System.nanoTime() - started) / 1_000_000_000
                val (action, reason) = policy.tick(now, current.isAlive) { Socks5.greets(port) }
                when (action) {
                    Watchdog.Action.NONE -> Unit
                    Watchdog.Action.RESTART -> worker.execute { restartEngine(current, port, reason) }
                    Watchdog.Action.GIVE_UP -> {
                        if (notifyErrors) event(getString(R.string.engine_error_gave_up))
                        worker.execute {
                            stopEngine(EngineState.Failed(profile?.id, getString(R.string.engine_error_gave_up)))
                        }
                        return@thread
                    }
                }
            }
        }
    }

    private fun buildArgs(p: Profile, entry: StrategyEntry, port: Int, hostlist: File?) = ByeDpiCommand.build(
        ByeDpiCommand.Request(
            strategyArgs = entry.args,
            port = port,
            listsDir = File(filesDir, "lists"),
            hostlist = hostlist,
            domainFilter = p.domainFilter && hostlist != null,
        ),
    )

    /**
     * Per-network memory: when the phone moves to another provider (Wi-Fi ↔ mobile data, another
     * Wi-Fi), look the provider up and switch to the strategy that worked there, if one is known.
     * DPIMech's default network is the real one, since DPIMech itself is outside the VPN.
     */
    private fun watchNetwork() {
        // NetworkIdentity follows the real network for the whole app; act on each new provider.
        NetworkIdentity.start(this)
        networkJob?.cancel()
        networkJob = scope.launch {
            NetworkIdentity.flow
                .map { it?.networkKey to it?.known }
                .distinctUntilChanged()
                .collect { (key, known) ->
                    if (key != null) {
                        worker.execute {
                            switchStrategyFor(key)
                            maybeAutoStrategy(key, known)
                        }
                    }
                }
        }
    }

    /** Must run on [worker]. */
    private fun switchStrategyFor(networkKey: String) {
        val p = profile ?: return
        val old = engine ?: return
        val entry = p.strategyFor(networkKey)
        if (stopping || entry == strategy) return
        val args = buildArgs(p, entry, enginePort, hostlist).getOrElse {
            EngineLog.add("remembered strategy for $networkKey is invalid: ${it.message}")
            return
        }
        EngineLog.add("network $networkKey: switching to \"${entry.name}\"")
        engineArgs = args
        strategy = entry
        restartEngine(old, enginePort, "network changed")
        (EngineState.flow.value as? EngineState.Running)?.let { EngineState.set(it.copy(strategyName = entry.name)) }
    }

    /**
     * Automatic strategy: on a provider with no remembered strategy, test the standard set
     * against the profile's sites in the background (the bypass stays on meanwhile) and switch
     * to the best confirmed one. Must run on [worker].
     */
    private fun maybeAutoStrategy(networkKey: String, isp: io.github.halilkhrmn.dpimech.core.Isp?) {
        val p = profile ?: return
        if (!autoStrategy || stopping || networkKey in p.perNetwork || !autoTested.add(networkKey)) return
        val probes = p.probes.take(6)
        if (probes.isEmpty() || autoRun?.isAlive == true) return
        val standard = StrategyFile.load(File(filesDir, "strategies.json")).byeDpi
        val strategies = IspLookup.markRecommended(standard.map { LabStrategy(it.name, it.args, LabResult.STANDARD_SET) }, isp)
        setAutoTesting(true)
        EngineLog.add("automatic strategy: testing ${strategies.size} strategies for $networkKey")
        autoRun = thread(name = "auto-strategy", isDaemon = true) {
            val results = runCatching {
                LabRunner(AndroidEngineLauncher(this), File(filesDir, "lists"))
                    .run(LabRequest(strategies, probes, repeats = 1), { _, _, _ -> }, cancelled = { stopping })
            }.onFailure { EngineLog.add("automatic strategy failed: $it") }.getOrNull()
            worker.execute {
                setAutoTesting(false)
                val best = results?.let(LabResult::best)?.takeIf { it.confirmed } ?: run {
                    EngineLog.add("automatic strategy: nothing reliable found for $networkKey")
                    return@execute
                }
                val s = best.strategy!!
                val entry = StrategyEntry(s.name, s.args)
                EngineLog.add("automatic strategy: \"${s.name}\" works on $networkKey (${best.ok}/${best.total})")
                profile = profile?.withStrategy(entry, networkKey)
                EngineEvents.emit(StrategyLearned(p.id, networkKey, entry))
                if (notifyStrategy && entry != strategy) event(getString(R.string.event_strategy, s.name))
                switchStrategyFor(networkKey)
            }
        }
    }

    private fun setAutoTesting(on: Boolean) {
        (EngineState.flow.value as? EngineState.Running)?.let { EngineState.set(it.copy(autoTesting = on)) }
    }

    /** Must run on [worker]. */
    private fun restartEngine(old: Ciadpi, port: Int, reason: String?) {
        if (stopping || engine !== old) return
        EngineLog.add("restarting ByeDPI: $reason")
        old.stop()
        try {
            engine = Ciadpi.start(this, engineArgs, port, profile?.name ?: "engine")
        } catch (e: Exception) {
            // The next watchdog tick sees a dead engine and tries again (or gives up).
            EngineLog.add("restart failed: $e")
        }
    }

    /** Must run on [worker]. A null [final] keeps the current state (used before a restart). */
    private fun stopEngine(final: EngineState?, stopService: Boolean = true) {
        stopping = true
        watchdog?.interrupt()
        watchdog = null
        networkJob?.cancel()
        networkJob = null
        runCatching { if (TProxy.TProxyIsRunning()) TProxy.TProxyStopService() }
        runCatching { tun?.close() }
        tun = null
        engine?.stop()
        engine = null
        profile = null
        final?.let(EngineState::set)
        if (stopService) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** A one-off notification on the events channel (strategy found, bypass stopped). */
    private fun event(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(EVENTS_CHANNEL, getString(R.string.event_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 2, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val n = NotificationCompat.Builder(this, EVENTS_CHANNEL)
            .setSmallIcon(R.drawable.ic_engine)
            .setContentTitle(getString(R.string.app_name_engine))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(EVENT_ID, n) } // no POST_NOTIFICATIONS permission: nothing to show
    }

    private fun installedPackages(): Set<String> =
        packageManager.getInstalledApplications(PackageManager.GET_META_DATA).mapTo(HashSet()) { it.packageName }

    private fun goForeground(p: Profile) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.engine_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val stop = PendingIntent.getService(
            this, 0, Intent(this, BypassVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 1, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_engine)
            .setContentTitle(getString(R.string.engine_running_title, p.name))
            .setContentText(getString(R.string.engine_running_text))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.engine_stop), stop)
            .build()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
            return
        }
        // systemExempted is meant for VPN apps; if this Android does not grant it, specialUse
        // keeps the service in the foreground instead of crashing it.
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } catch (e: RuntimeException) {
            EngineLog.add("foreground type systemExempted refused ($e), using specialUse")
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        }
    }

    companion object {
        const val ACTION_START = "io.github.halilkhrmn.dpimech.START"
        const val ACTION_STOP = "io.github.halilkhrmn.dpimech.STOP"
        const val EXTRA_PROFILE = "profile"
        const val EXTRA_DNS = "dns"
        const val EXTRA_AUTO = "auto"
        private const val CHANNEL = "engine"
        private const val EVENTS_CHANNEL = "events"
        private const val EVENT_ID = 2
        const val EXTRA_NOTIFY_STRATEGY = "notify_strategy"
        const val EXTRA_NOTIFY_ERRORS = "notify_errors"
        private const val NOTIFICATION_ID = 1

        /** Call only after [VpnService.prepare] returned null (permission granted). */
        fun start(
            context: Context,
            profile: Profile,
            dns: String,
            autoStrategy: Boolean,
            notifyStrategy: Boolean = true,
            notifyErrors: Boolean = true,
        ) {
            val intent = Intent(context, BypassVpnService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PROFILE, Json.encodeToString(Profile.serializer(), profile))
                .putExtra(EXTRA_DNS, dns)
                .putExtra(EXTRA_AUTO, autoStrategy)
                .putExtra(EXTRA_NOTIFY_STRATEGY, notifyStrategy)
                .putExtra(EXTRA_NOTIFY_ERRORS, notifyErrors)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, BypassVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
