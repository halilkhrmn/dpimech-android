package io.github.halilkhrmn.dpimech.core

import kotlin.test.Test
import kotlin.test.assertEquals

class WatchdogTest {
    @Test
    fun deadProcessIsRestartedAtOnce() {
        val w = Watchdog()
        assertEquals(Watchdog.Action.RESTART, w.tick(0, alive = false) { error("no probe needed") }.first)
    }

    @Test
    fun probesOnlyWhenDueAndNeedsTwoFailures() {
        val w = Watchdog(probeEverySec = 3)
        var probes = 0
        val results = (1L..12L).map { t -> w.tick(t, alive = true) { probes++; false }.first }
        assertEquals(4, probes)
        // Probes at t=3,6 (restart), 9,12 (restart).
        assertEquals(listOf(6L, 12L), results.withIndex().filter { it.value == Watchdog.Action.RESTART }.map { it.index + 1L })
        val ok = Watchdog(probeEverySec = 1)
        repeat(10) { assertEquals(Watchdog.Action.NONE, ok.tick(it.toLong(), true) { true }.first) }
    }

    @Test
    fun givesUpOnRestartLoopsButForgetsOldOnes() {
        val w = Watchdog(maxRestarts = 3, windowSec = 60)
        repeat(3) { assertEquals(Watchdog.Action.RESTART, w.tick(it.toLong(), false) { true }.first) }
        assertEquals(Watchdog.Action.GIVE_UP, w.tick(10, false) { true }.first)
        assertEquals(Watchdog.Action.RESTART, w.tick(200, false) { true }.first)
    }
}
