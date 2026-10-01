package io.github.halilkhrmn.dpimech.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TrafficStatsTest {
    @Test
    fun ratesAndTotalsFromCounters() {
        var s = TrafficStats(since = 1).sample(1000, 100, 10_000)
        assertEquals(0, s.downTotal)
        s = s.sample(3000, 300, 11_000)
        assertEquals(listOf(2000L), s.down)
        assertEquals(200, s.upRate)
        s = s.sample(4000, 300, 13_000) // two seconds later
        assertEquals(500, s.downRate)
        assertEquals(3000, s.downTotal)
    }

    @Test
    fun counterResetCountsAsNew() {
        val s = TrafficStats().sample(5000, 0, 1000).sample(700, 0, 2000)
        assertEquals(700, s.downTotal)
    }

    @Test
    fun historyIsCapped() {
        var s = TrafficStats().sample(0, 0, 1000)
        for (i in 1..100) s = s.sample(i * 10L, 0, 1000L + i * 1000)
        assertEquals(TrafficStats.HISTORY, s.down.size)
    }

    @Test
    fun formatsBytes() {
        assertEquals("12 B", TrafficStats.bytes(12))
        assertEquals("340 KB", TrafficStats.bytes(340_500))
        assertEquals("1.2 MB", TrafficStats.bytes(1_234_567))
    }
}
