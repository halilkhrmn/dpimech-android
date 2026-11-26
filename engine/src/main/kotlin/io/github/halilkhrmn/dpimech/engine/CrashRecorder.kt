package io.github.halilkhrmn.dpimech.engine

import io.github.halilkhrmn.dpimech.core.CrashRecord
import java.io.File

/**
 * Keeps what explains a crash for the next problem report, on the phone only: the log goes to a
 * file as well, an uncaught exception writes its stack trace, and [BypassVpnService] marks the
 * time it spends turning a profile on (a crash in native code leaves no trace, only that mark).
 * At the next start the previous run's end is put into the log, once.
 */
object CrashRecorder {
    private const val LOG = "log.txt"
    private const val CRASH = "crash.txt"
    private const val STARTING = "starting"
    private var dir: File? = null

    /** Call first thing in Application.onCreate. */
    @Synchronized
    fun install(filesDir: File) {
        if (dir != null) return
        dir = filesDir
        val crash = File(filesDir, CRASH)
        val marker = File(filesDir, STARTING)
        val last = EngineLog.attach(File(filesDir, LOG), CrashRecord.PREVIOUS_LINES)
        val trace = runCatching { if (crash.exists()) crash.readLines() else emptyList() }.getOrDefault(emptyList())
        CrashRecord.previousRun(trace, marker.exists(), last).forEach(EngineLog::add)
        crash.delete()
        marker.delete()
        val before = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { crash.writeText(CrashRecord.trace(e, thread.name).joinToString("\n")) }
            before?.uncaughtException(thread, e)
        }
    }

    /** Around turning a profile on: a run that dies in between is reported at the next start. */
    fun starting(on: Boolean) {
        val marker = File(dir ?: return, STARTING)
        runCatching { if (on) marker.writeText("") else marker.delete() }
    }
}
