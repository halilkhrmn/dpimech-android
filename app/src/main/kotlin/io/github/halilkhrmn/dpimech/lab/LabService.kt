package io.github.halilkhrmn.dpimech.lab

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.halilkhrmn.dpimech.DpimechApp
import io.github.halilkhrmn.dpimech.R
import io.github.halilkhrmn.dpimech.core.LabResult
import io.github.halilkhrmn.dpimech.data.LabState
import io.github.halilkhrmn.dpimech.engine.LiveProgress
import io.github.halilkhrmn.dpimech.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import io.github.halilkhrmn.dpimech.engine.R as EngineR

/**
 * Keeps the Strategy Lab running while the user is in another app: a foreground service with
 * the test's progress (a Live Update on Android 16) and a cancel button, and a notification
 * with the result when it ends.
 */
class LabService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val lab = (application as DpimechApp).lab
        if (intent?.action == ACTION_CANCEL) {
            lab.cancel()
            return START_NOT_STICKY
        }
        startInForeground(progress(lab.flow.value))
        if (job == null) {
            job = scope.launch {
                lab.flow.distinctUntilChangedBy { Triple(it.running, it.done, it.total) }.collect { s ->
                    if (s.running) {
                        notify(progress(s))
                    } else {
                        finished(s)
                        ServiceCompat.stopForeground(this@LabService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(n: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(this, PROGRESS_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            ServiceCompat.startForeground(this, PROGRESS_ID, n, 0)
        }
    }

    private fun notify(n: android.app.Notification) {
        runCatching { getSystemService(NotificationManager::class.java).notify(PROGRESS_ID, n) }
    }

    private fun channel(): String {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.lab_channel), NotificationManager.IMPORTANCE_LOW),
        )
        return CHANNEL
    }

    private fun openTest() = PendingIntent.getActivity(
        this, 20,
        Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SCREEN, MainActivity.SCREEN_TEST)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progress(s: LabState) = NotificationCompat.Builder(this, channel())
        .setSmallIcon(EngineR.drawable.ic_engine)
        .setContentTitle(getString(R.string.lab_title))
        .setContentText(if (s.total > 0) getString(R.string.lab_progress, s.done, s.total) else getString(R.string.lab_preparing))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openTest())
        .addAction(
            0, getString(R.string.cancel),
            PendingIntent.getService(
                this, 21, Intent(this, LabService::class.java).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .let { LiveProgress.apply(it, s.done, s.total, if (s.total > 0) "${s.done}/${s.total}" else null) }
        .build()

    /** The result once the test ends, so the user sees it without opening the app. */
    private fun finished(s: LabState) {
        if (!s.finished && s.error == null) return // cancelled
        val best = LabResult.best(s.results)
        val text = when {
            s.error != null -> getString(R.string.lab_failed, s.error)
            best == null || best.ok == 0 -> getString(R.string.lab_none_worked)
            best.confirmed -> getString(R.string.lab_best_confirmed, best.strategy?.name.orEmpty())
            else -> getString(R.string.lab_best_unconfirmed, best.strategy?.name.orEmpty())
        }
        val n = NotificationCompat.Builder(this, channel())
            .setSmallIcon(EngineR.drawable.ic_engine)
            .setContentTitle(getString(R.string.lab_done_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openTest())
            .setAutoCancel(true)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(RESULT_ID, n) }
    }

    companion object {
        private const val CHANNEL = "lab"
        private const val PROGRESS_ID = 30
        private const val RESULT_ID = 31
        private const val ACTION_CANCEL = "io.github.halilkhrmn.dpimech.lab.CANCEL"

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, LabService::class.java)) }
        }
    }
}
