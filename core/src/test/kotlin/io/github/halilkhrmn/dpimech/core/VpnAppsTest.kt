package io.github.halilkhrmn.dpimech.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VpnAppsTest {
    private val own = "io.github.halilkhrmn.dpimech"
    private val profile = Profile("1", "Discord", strategy = StrategyEntry("s", "-s1"))

    @Test
    fun onlySelectedNeedsAnInstalledApp() {
        val p = profile.copy(apps = listOf("com.discord", "gone.app", own, "com.discord"))
        assertEquals(
            VpnApps(listOf("com.discord"), emptyList()),
            VpnApps.plan(p, own, setOf("com.discord", own)).getOrThrow(),
        )
        assertTrue(VpnApps.plan(p, own, setOf(own)).isFailure)
        assertTrue(VpnApps.plan(profile, own, setOf("com.discord")).isFailure)
    }

    @Test
    fun allExceptAlwaysExcludesItself() {
        val p = profile.copy(appMode = AppMode.ALL_EXCEPT, apps = listOf("com.bank"))
        assertEquals(VpnApps(emptyList(), listOf(own, "com.bank")), VpnApps.plan(p, own, setOf("com.bank")).getOrThrow())
        assertEquals(VpnApps(emptyList(), listOf(own)), VpnApps.plan(p.copy(apps = emptyList()), own, emptySet()).getOrThrow())
    }
}
