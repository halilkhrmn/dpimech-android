package io.github.halilkhrmn.dpimech.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrashRecordTest {
    @Test
    fun traceHasTheExceptionItsFramesAndCauses() {
        val e = IllegalStateException("tunnel", NoClassDefFoundError("android/net/Foo"))
        val lines = CrashRecord.trace(e, "pool-1-thread-1")
        assertEquals("crash in thread \"pool-1-thread-1\": java.lang.IllegalStateException: tunnel", lines.first())
        assertTrue(lines.any { it.startsWith("  at ") && "CrashRecordTest" in it })
        assertTrue(lines.any { it == "caused by: java.lang.NoClassDefFoundError: android/net/Foo" })
        assertTrue(lines.count { it.startsWith("  at ") } <= CrashRecord.TRACE_LINES * 2)
    }

    @Test
    fun aNormalRunAddsNothing() {
        assertEquals(emptyList(), CrashRecord.previousRun(emptyList(), false, listOf("12:00:00 network: Wi-Fi")))
    }

    @Test
    fun aCrashShowsTheLastLinesThenTheTrace() {
        val last = (1..50).map { "12:00:%02d line $it".format(it % 60) }
        val lines = CrashRecord.previousRun(listOf("crash in thread \"main\": boom"), false, last)
        assertEquals("previous run ended unexpectedly", lines.first())
        assertEquals(CrashRecord.PREVIOUS_LINES, lines.count { it.startsWith("  | ") })
        assertTrue(lines.contains("  | ${last.last()}"))
        assertEquals("crash in thread \"main\": boom", lines[lines.size - 2])
        assertEquals("(end of the previous run)", lines.last())
    }

    @Test
    fun aNativeCrashWhileTurningOnLeavesOnlyTheMarker() {
        val lines = CrashRecord.previousRun(emptyList(), true, listOf("13:10:30 turning on profile \"x\""))
        assertEquals("previous run ended unexpectedly while turning on", lines.first())
        assertTrue(lines.contains("  | 13:10:30 turning on profile \"x\""))
    }

    @Test
    fun logFileKeepsTheNewestLinesWhenItGrows() {
        val file = Files.createTempFile("log", ".txt").toFile()
        try {
            val log = LogFile(file, maxBytes = 2_000, keepLines = 20)
            repeat(500) { log.append("line $it") }
            assertTrue(file.length() < 2_000 + 200)
            assertEquals("line 499", log.tail(1).single())
            assertEquals(listOf("line 497", "line 498", "line 499"), log.tail(3))
            log.clear()
            assertEquals(emptyList(), log.tail(5))
        } finally {
            file.delete()
        }
    }
}
