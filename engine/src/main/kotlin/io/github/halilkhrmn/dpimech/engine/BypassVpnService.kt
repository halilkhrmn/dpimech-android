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
import io.github.halilkhrmn.dpimech.core.Connector
import io.github.halilkhrmn.dpimech.core.DohClient
import io.github.halilkhrmn.dpimech.core.Ping
import io.github.halilkhrmn.dpimech.core.SiteCheck
import io.github.halilkhrmn.dpimech.core.TrafficStats
import io.github.halilkhrmn.dpimech.core.TunnelFilter
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    private var filter: PacketFilter? = null
    private var tunnelFilter: TunnelFilter? = null
    private var doh: DohClient? = null
    private var encryptDns = true
    private var blockQuic = false
    private var statsJob: Job? = null
    private var pingJob: Job? = null
    private val pingBusy = java.util.concurrent.atomic.AtomicBoolean(false)
    private var restarts = 0
    /** Progress of the automatic strategy test (done, total), shown in the notification. */
    @Volatile
    private var autoProgress: Pair<Int, Int>? = null
    private var engine: Ciadpi? = null
    private var profile: Profile? = null
    private var engineArgs: List<String> = emptyList()
    private var enginePort = 0
    private var strategy: StrategyEntry? = null
    private var hostlist: File? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var networkJob: Job? = null
    private var ipv6Job: Job? = null
    private var dns = TunnelConfig(socksPort = 1).dns
    private var vpnApps: VpnApps? = null
    private var tunnelProfileName = ""
    /** Whether the open TUN takes IPv6 (only when the real network has it). */
    private var tunnelIpv6: Boolean? = null
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
            ACTION_PING -> if (engine != null) pingNow()
            // Android's always-on VPN (or a restart by the system) starts the service without
            // our extras: turn on the last profile with the last settings.
            SERVICE_INTERFACE, null -> {
                val last = StartMemory.load(this)
                if (last == null) {
                    EngineLog.add("started by the system, but no profile was ever turned on")
                    stopSelf()
                    return START_NOT_STICKY
                }
                EngineLog.add("started by the system (always-on VPN): last profile")
                return onStartCommand(last, flags, startId)
            }
            ACTION_START -> {
                StartMemory.save(this, intent)
                val json = intent.getStringExtra(EXTRA_PROFILE) ?: return START_NOT_STICKY
                val p = runCatching { Json.decodeFromString(Profile.serializer(), json) }.getOrElse {
                    EngineState.set(EngineState.Failed(null, it.message ?: "bad profile"))
                    return START_NOT_STICKY
                }
                dns = intent.getStringExtra(EXTRA_DNS) ?: dns
                autoStrategy = intent.getBooleanExtra(EXTRA_AUTO, false)
                notifyStrategy = intent.getBooleanExtra(EXTRA_NOTIFY_STRATEGY, true)
                notifyErrors = intent.getBooleanExtra(EXTRA_NOTIFY_ERRORS, true)
                encryptDns = intent.getBooleanExtra(EXTRA_DOH, true)
                blockQuic = intent.getBooleanExtra(EXTRA_BLOCK_QUIC, false)
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

            vpnApps = apps
            tunnelProfileName = p.name
            openTunnel(port)

            profile = p
            startWatchdog(port)
            watchNetwork()
            startStats(p)
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

    /**
     * Builds the TUN for the selected apps and hands it to hev-socks5-tunnel. IPv6 is routed in
     * only when the real network has it: otherwise every IPv6 attempt would fail inside ciadpi
     * ("Network is unreachable") before the app falls back to IPv4. Must run on [worker].
     */
    private fun openTunnel(port: Int) {
        val apps = vpnApps ?: error("no app list")
        val ipv6 = NetworkIdentity.flow.value?.hasIpv6 ?: true
        val config = TunnelConfig(socksPort = port, dns = dns, useIpv6 = ipv6)
        val builder = Builder()
            .setSession(tunnelProfileName)
            .setMtu(config.mtu)
            .addAddress(config.ipv4, 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(config.dns)
            .setBlocking(false)
        if (ipv6) builder.addAddress(config.ipv6, 128).addRoute("::", 0)
        apps.allowed.forEach { builder.addAllowedApplication(it) }
        apps.disallowed.forEach { builder.addDisallowedApplication(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        val fd = builder.establish() ?: error(getString(R.string.engine_error_no_permission))
        tun = fd
        tunnelIpv6 = ipv6
        // DoH and the QUIC switch need to see the packets first; otherwise hev gets the TUN.
        val rules = tunnelFilter ?: TunnelFilter(encryptDns, blockQuic).also { tunnelFilter = it }
        val pf = if (rules.active) {
            PacketFilter(fd, rules, doh ?: DohClient(DohClient.urlFor(dns)).also { doh = it }, config.mtu)
        } else {
            null
        }
        filter = pf
        val yaml = File(cacheDir, "hev.yml").apply { writeText(config.hevYaml()) }
        check(TProxy.TProxyStartService(yaml.path, pf?.hevFd?.fd ?: fd.fd)) { "tunnel did not start" }
        EngineLog.add(
            "tunnel up (IPv6 ${if (ipv6) "on" else "off"}" +
                (if (encryptDns) ", DNS over HTTPS ${doh?.url}" else "") +
                (if (blockQuic) ", QUIC blocked" else "") + ")",
        )
    }

    /** The network gained or lost IPv6: rebuild the TUN to match. Must run on [worker]. */
    private fun reopenTunnelFor(ipv6: Boolean) {
        if (stopping || engine == null || tunnelIpv6 == ipv6) return
        runCatching { if (TProxy.TProxyIsRunning()) TProxy.TProxyStopService() }
        val old = tun
        val oldFilter = filter
        oldFilter?.stop()
        try {
            openTunnel(enginePort)
        } catch (e: Exception) {
            EngineLog.add("tunnel rebuild failed: $e")
            stopEngine(EngineState.Failed(profile?.id, e.message ?: e.toString()))
        } finally {
            // Closed after the new one exists, so the VPN does not drop in between.
            runCatching { old?.close() }
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
        ipv6Job?.cancel()
        ipv6Job = scope.launch {
            NetworkIdentity.flow.map { it?.hasIpv6 }.distinctUntilChanged().collect { v6 ->
                if (v6 != null) worker.execute { reopenTunnelFor(v6) }
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
        scope.launch {
            delay(PING_FIRST_MS)
            pingNow()
        }
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
                    .run(LabRequest(strategies, probes, repeats = 1), { done, total, _ ->
                        autoProgress = done to total
                        profile?.let { runCatching { goForeground(it, EngineStats.flow.value) } }
                    }, cancelled = { stopping })
            }.onFailure { EngineLog.add("automatic strategy failed: $it") }.getOrNull()
            autoProgress = null
            worker.execute {
                setAutoTesting(false)
                profile?.let { runCatching { goForeground(it, EngineStats.flow.value) } }
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
        if (reason != "network changed") EngineStats.update { it.copy(restarts = ++restarts) }
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
        ipv6Job?.cancel()
        ipv6Job = null
        tunnelIpv6 = null
        statsJob?.cancel()
        statsJob = null
        pingJob?.cancel()
        pingJob = null
        runCatching { if (TProxy.TProxyIsRunning()) TProxy.TProxyStopService() }
        filter?.stop()
        filter = null
        tunnelFilter = null
        doh = null
        runCatching { tun?.close() }
        tun = null
        EngineStats.reset()
        engine?.stop()
        engine = null
        profile = null
        final?.let(EngineState::set)
        if (stopService) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * Samples hev's counters once a second for the home screen chart and refreshes the ongoing
     * notification with the same numbers every few seconds.
     */
    private fun startStats(p: Profile) {
        restarts = 0
        EngineStats.reset(TrafficStats(since = System.currentTimeMillis(), encryptedDns = encryptDns, blockQuic = blockQuic))
        statsJob?.cancel()
        statsJob = scope.launch {
            var tick = 0
            while (isActive) {
                val c = runCatching { TProxy.TProxyGetStats() }.getOrNull()
                val rules = tunnelFilter
                val resolver = doh
                EngineStats.update { s ->
                    (if (c != null && c.size >= 4) s.sample(downCounter = c[3], upCounter = c[1], now = System.currentTimeMillis()) else s)
                        .copy(
                            dnsQueries = rules?.dnsQueries?.get() ?: 0,
                            dnsEncrypted = resolver?.answered?.get() ?: 0,
                            quicBlocked = rules?.quicDropped?.get() ?: 0,
                        )
                }
                if (tick++ % NOTIFY_EVERY == 0 && !stopping) runCatching { goForeground(p, EngineStats.flow.value) }
                delay(1000)
            }
        }
        pingJob?.cancel()
        pingJob = scope.launch {
            delay(PING_FIRST_MS)
            while (isActive) {
                pingNow()
                delay(PING_EVERY_MS)
            }
        }
    }

    /**
     * Average ping of the profile's sites through ciadpi (connect + TLS handshake), like the
     * desktop app's profile card; also shows whether the sites open right now.
     */
    private fun pingNow() {
        val p = profile ?: return
        val port = enginePort
        val hosts = p.probes
        if (hosts.isEmpty() || port == 0 || !pingBusy.compareAndSet(false, true)) return
        EngineStats.update { it.copy(pinging = true) }
        thread(name = "ping", isDaemon = true) {
            try {
                val check = SiteCheck()
                val r = Ping.run(hosts, { check.measure(Connector.socks(port), it) })
                if (!stopping && profile?.id == p.id) {
                    EngineLog.add("ping: ${r.averageMs?.let { "$it ms" } ?: "-"}, ${r.ok}/${r.total} sites open")
                    EngineStats.update { it.copy(ping = r, pinging = false) }
                }
            } finally {
                pingBusy.set(false)
                if (stopping) EngineStats.update { it.copy(pinging = false) }
            }
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

    private fun goForeground(p: Profile, stats: TrafficStats? = null) {
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
            .setContentText(stats?.let(::statsLine) ?: getString(R.string.engine_running_text))
            .apply { stats?.let { setStyle(NotificationCompat.BigTextStyle().bigText(statsText(it))) } }
            .setOnlyAlertOnce(true)
            .setShowWhen(stats != null)
            .apply { stats?.since?.takeIf { it > 0 }?.let { setWhen(it).setUsesChronometer(true) } }
            .setOngoing(true)
            .apply {
                autoProgress?.let { (done, total) ->
                    setContentText(getString(R.string.engine_auto_progress, done, total))
                    LiveProgress.apply(this, done, total, "$done/$total")
                }
            }
            .setContentIntent(open)
            .addAction(0, getString(R.string.engine_stop), stop)
            .apply { nextProfileAction?.invoke(this@BypassVpnService)?.let { addAction(0, getString(R.string.engine_next_profile), it) } }
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

    private fun statsLine(s: TrafficStats) =
        getString(R.string.engine_stats_line, TrafficStats.bytes(s.downRate), TrafficStats.bytes(s.upRate))

    private fun statsText(s: TrafficStats) = buildString {
        append(statsLine(s))
        append('\n').append(getString(R.string.engine_stats_total, TrafficStats.bytes(s.downTotal), TrafficStats.bytes(s.upTotal)))
        (EngineState.flow.value as? EngineState.Running)?.strategyName?.takeIf { it.isNotEmpty() }?.let {
            append('\n').append(getString(R.string.engine_stats_strategy, it))
        }
        if (s.encryptedDns) append('\n').append(getString(R.string.engine_stats_dns, s.dnsEncrypted, s.dnsQueries))
        s.ping?.let {
            append('\n').append(
                if (it.averageMs != null) getString(R.string.engine_stats_ping, it.averageMs, it.ok, it.total)
                else getString(R.string.engine_stats_ping_none, it.total),
            )
        }
        if (s.blockQuic) append('\n').append(getString(R.string.engine_stats_quic, s.quicBlocked))
        append('\n').append(getString(R.string.engine_stats_restarts, s.restarts))
    }

    companion object {
        private const val NOTIFY_EVERY = 5
        private const val PING_FIRST_MS = 3000L
        private const val PING_EVERY_MS = 5 * 60_000L
        const val ACTION_PING = "io.github.halilkhrmn.dpimech.PING"
        const val EXTRA_DOH = "doh"
        const val EXTRA_BLOCK_QUIC = "block_quic"
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
            encryptDns: Boolean = true,
            blockQuic: Boolean = false,
        ) {
            val intent = Intent(context, BypassVpnService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PROFILE, Json.encodeToString(Profile.serializer(), profile))
                .putExtra(EXTRA_DNS, dns)
                .putExtra(EXTRA_AUTO, autoStrategy)
                .putExtra(EXTRA_NOTIFY_STRATEGY, notifyStrategy)
                .putExtra(EXTRA_NOTIFY_ERRORS, notifyErrors)
                .putExtra(EXTRA_DOH, encryptDns)
                .putExtra(EXTRA_BLOCK_QUIC, blockQuic)
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Set by the app: the "next profile" button of the ongoing notification, or null when
         * there is no other profile to switch to.
         */
        @Volatile
        var nextProfileAction: ((Context) -> PendingIntent?)? = null

        /** Measures the average ping again (home screen refresh button). */
        fun ping(context: Context) {
            context.startService(Intent(context, BypassVpnService::class.java).setAction(ACTION_PING))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, BypassVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
