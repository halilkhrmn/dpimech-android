package io.github.halilkhrmn.dpimech.engine

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Recent engine and app events kept in memory for the Logs screen and problem reports.
 * Nothing leaves the phone unless the user sends a report. No telemetry.
 */
object EngineLog {
    private const val TAG = "DPIMech"
    private const val MAX_LINES = 1000
    private val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
    private val lines = ArrayDeque<String>()
    private val state = MutableStateFlow<List<String>>(emptyList())

    /** The newest lines, oldest first; updates live. */
    val flow: StateFlow<List<String>> = state.asStateFlow()

    @Synchronized
    fun add(line: String) {
        Log.i(TAG, line)
        if (lines.size == MAX_LINES) lines.removeFirst()
        lines.addLast("${time.format(Date())} $line")
        state.value = lines.toList()
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    fun clear() {
        lines.clear()
        state.value = emptyList()
    }
}
