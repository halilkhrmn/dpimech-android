package io.github.halilkhrmn.dpimech.core

import java.io.File

/**
 * What the app keeps on the phone when it dies, so the next problem report can show it. The log
 * lives only in memory, so a crash used to take the lines that explain it with it. Nothing here
 * leaves the phone: it ends up in the log, which the user sends only in a report.
 */
object CrashRecord {
    /** Stack lines kept per exception in the chain. */
    const val TRACE_LINES = 12

    /** Log lines of the previous run shown before its crash. */
    const val PREVIOUS_LINES = 30

    /** The exception and its causes, short enough for an issue link. */
    fun trace(e: Throwable, thread: String): List<String> = buildList {
        add("crash in thread \"$thread\": $e")
        e.stackTrace.take(TRACE_LINES).forEach { add("  at $it") }
        var cause = e.cause
        var depth = 0
        while (cause != null && cause !== e && depth < 3) {
            add("caused by: $cause")
            cause.stackTrace.take(TRACE_LINES / 2).forEach { add("  at $it") }
            cause = cause.cause
            depth++
        }
    }

    /**
     * Lines for the log at the next start: empty when the previous run ended normally. A run
     * "ended unexpectedly" when it left a [crash] trace, or when it died while turning a profile
     * on ([startInterrupted]); a crash in native code leaves only the second.
     */
    fun previousRun(crash: List<String>, startInterrupted: Boolean, lastLines: List<String>): List<String> {
        if (crash.isEmpty() && !startInterrupted) return emptyList()
        return buildList {
            add("previous run ended unexpectedly" + if (startInterrupted) " while turning on" else "")
            if (lastLines.isNotEmpty()) {
                add("its last lines:")
                lastLines.takeLast(PREVIOUS_LINES).forEach { add("  | $it") }
            }
            addAll(crash)
            add("(end of the previous run)")
        }
    }
}

/**
 * The log on disk as well as in memory, so the lines before a crash survive it. Kept small: when
 * the file grows past [maxBytes], only its newest [keepLines] lines stay.
 */
class LogFile(private val file: File, private val maxBytes: Long = 128 * 1024, private val keepLines: Int = 300) {
    fun tail(n: Int): List<String> =
        runCatching { if (file.exists()) file.readLines().takeLast(n) else emptyList() }.getOrDefault(emptyList())

    @Synchronized
    fun append(line: String) {
        runCatching {
            if (file.length() > maxBytes) file.writeText(file.readLines().takeLast(keepLines).joinToString("\n", postfix = "\n"))
            file.appendText(line + "\n")
        }
    }

    @Synchronized
    fun clear() {
        runCatching { file.writeText("") }
    }
}
