package io.github.halilkhrmn.dpimech.engine

import android.util.Log

/** Recent engine output kept in memory for the logs screen and problem reports. No telemetry. */
object EngineLog {
    private const val TAG = "DPIMech"
    private const val MAX_LINES = 500
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun add(line: String) {
        Log.i(TAG, line)
        if (lines.size == MAX_LINES) lines.removeFirst()
        lines.addLast(line)
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()
}
