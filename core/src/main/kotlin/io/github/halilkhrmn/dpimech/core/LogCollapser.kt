package io.github.halilkhrmn.dpimech.core

/**
 * Folds a run of identical log lines into one, e.g. "connect: Network is unreachable (×12)", so a
 * burst from the engine does not push everything else out of the log.
 */
class LogCollapser {
    private var last: String? = null
    private var count = 0

    /** Returns the line to show and whether it replaces the previous one. */
    fun add(line: String): Pair<String, Boolean> {
        if (line == last) {
            count++
            return "$line (×$count)" to true
        }
        last = line
        count = 1
        return line to false
    }

    fun reset() {
        last = null
        count = 0
    }
}
