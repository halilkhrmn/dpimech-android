package io.github.halilkhrmn.dpimech.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PingTest {
    @Test
    fun averagesTheSitesThatOpen() {
        val times = mapOf("a.com" to 80L, "b.com" to 120L, "c.com" to null)
        val r = Ping.run(listOf("a.com", "b.com", "c.com", "a.com"), { times[it] }, now = { 5 })
        assertEquals(100, r.averageMs)
        assertEquals(2, r.ok)
        assertEquals(3, r.total)
        assertEquals(5, r.at)
    }

    @Test
    fun noneOpenOrNoSites() {
        assertNull(Ping.run(listOf("x.com"), { error("reset") }).averageMs)
        assertEquals(0, Ping.run(emptyList(), { 1 }).total)
    }

    @Test
    fun atMostSixSites() {
        assertEquals(Ping.MAX_HOSTS, Ping.run((1..10).map { "s$it.com" }, { 10 }).total)
    }
}
