package io.github.halilkhrmn.dpimech.engine

import android.util.Log
import io.github.halilkhrmn.dpimech.core.LogCollapser
import io.github.halilkhrmn.dpimech.core.LogFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Recent engine and app events kept in memory for the Logs screen and problem reports, and in a
 * small file so the lines before a crash survive it ([CrashRecorder]). Nothing leaves the phone
 * unless the user sends a report. No telemetry.
 */
object EngineLog {
    private const val TAG = "DPIMech"
    private const val MAX_LINES = 1000
    private val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
    private val lines = ArrayDeque<String>()
    private val state = MutableStateFlow<List<String>>(emptyList())
    private val collapser = LogCollapser()
    private var file: LogFile? = null

    /**
     * Writes every new line to [path] as well; returns the last [previous] lines the file held
     * from the previous run.
     */
    @Synchronized
    fun attach(path: File, previous: Int): List<String> {
        val log = LogFile(path)
        val before = log.tail(previous)
        log.append("${time.format(Date())} --- app started ---")
        file = log
        return before
    }

    /**
     * The newest [n] lines across app restarts, for problem reports: Android ends the app in the
     * background, and what happened before (another VPN stopping, issue #38) would be gone from
     * memory. Falls back to memory when there is no file.
     */
    @Synchronized
    fun history(n: Int): List<String> = file?.tail(n)?.takeIf { it.isNotEmpty() } ?: lines.toList().takeLast(n)

    /** The newest lines, oldest first; updates live. */
    val flow: StateFlow<List<String>> = state.asStateFlow()

    @Synchronized
    fun add(line: String) {
        Log.i(TAG, line)
        val (shown, replaces) = collapser.add(line)
        if (replaces && lines.isNotEmpty()) {
            lines.removeLast()
        } else if (lines.size == MAX_LINES) {
            lines.removeFirst()
        }
        val stamped = "${time.format(Date())} $shown"
        lines.addLast(stamped)
        // A burst of the same line is written once; the count stays in memory.
        if (!replaces) file?.append(stamped)
        state.value = lines.toList()
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    fun clear() {
        lines.clear()
        collapser.reset()
        file?.clear()
        state.value = emptyList()
    }
}
