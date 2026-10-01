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
import io.github.halilkhrmn.dpimech.core.Hostlist
import io.github.halilkhrmn.dpimech.core.Profile
import io.github.halilkhrmn.dpimech.core.TunnelConfig
import io.github.halilkhrmn.dpimech.core.VpnApps
import java.io.File
import java.util.concurrent.Executors
import kotlin.concurrent.thread
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
                goForeground(p)
                worker.execute { startEngine(p) }
            }
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        // Another VPN took over or the user turned us off in system settings.
        worker.execute { stopEngine(EngineState.Stopped) }
    }

    override fun onDestroy() {
        worker.execute { stopEngine(EngineState.Stopped, stopService = false) }
        worker.shutdown()
        super.onDestroy()
    }

    private fun startEngine(p: Profile) {
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
            val args = ByeDpiCommand.build(
                ByeDpiCommand.Request(
                    strategyArgs = p.strategy.args,
                    port = port,
                    listsDir = lists,
                    hostlist = hostlist,
                    domainFilter = p.domainFilter && hostlist != null,
                ),
            ).getOrThrow()
            val ciadpi = Ciadpi.start(this, args, port, p.name).also { engine = it }

            val config = TunnelConfig(socksPort = port)
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
            watch(ciadpi)
            EngineState.set(EngineState.Running(p.id, p.name))
            EngineLog.add("started profile ${p.name} on port $port")
        } catch (e: Exception) {
            EngineLog.add("start failed: $e")
            stopEngine(EngineState.Failed(p.id, e.message ?: e.toString()))
        }
    }

    /** Reports an engine that exits on its own (Phase 2 adds restart and health probes). */
    private fun watch(ciadpi: Ciadpi) {
        thread(name = "ciadpi-watch", isDaemon = true) {
            val code = ciadpi.waitFor()
            if (!stopping && engine === ciadpi) {
                worker.execute {
                    stopEngine(EngineState.Failed(profile?.id, getString(R.string.engine_error_died, code)))
                }
            }
        }
    }

    /** Must run on [worker]. A null [final] keeps the current state (used before a restart). */
    private fun stopEngine(final: EngineState?, stopService: Boolean = true) {
        stopping = true
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
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        const val ACTION_START = "io.github.halilkhrmn.dpimech.START"
        const val ACTION_STOP = "io.github.halilkhrmn.dpimech.STOP"
        const val EXTRA_PROFILE = "profile"
        private const val CHANNEL = "engine"
        private const val NOTIFICATION_ID = 1

        /** Call only after [VpnService.prepare] returned null (permission granted). */
        fun start(context: Context, profile: Profile) {
            val intent = Intent(context, BypassVpnService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PROFILE, Json.encodeToString(Profile.serializer(), profile))
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, BypassVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
