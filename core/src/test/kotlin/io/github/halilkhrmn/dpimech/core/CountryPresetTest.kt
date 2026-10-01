package io.github.halilkhrmn.dpimech.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CountryPresetTest {
    @Test
    fun presetsUseExistingPacks() {
        for (c in CountryPreset.ALL + CountryPreset.GENERIC) {
            assertEquals(c.packs.size, c.domainPacks.size, c.country)
        }
        assertEquals("TR", CountryPreset.forCountry("tr").country)
        assertEquals(CountryPreset.GENERIC, CountryPreset.forCountry("DE"))
        assertEquals(CountryPreset.GENERIC, CountryPreset.forCountry(null))
    }

    @Test
    fun wholePhoneProfileOnlyTouchesTheListedSites() {
        val p = CountryPreset.forCountry("TR").profile("1", "Türkiye", StrategyEntry("s", "-s1"))
        assertEquals(AppMode.ALL_EXCEPT, p.appMode)
        assertTrue(p.domainFilter && p.apps.isEmpty())
        assertTrue("discord.com" in p.domains && "roblox.com" in p.domains)
        // The VPN takes every app except DPIMech itself.
        assertEquals(VpnApps(emptyList(), listOf("own")), VpnApps.plan(p, "own", emptySet()).getOrThrow())
    }

    @Test
    fun settingsRoundTrip() {
        val s = AppSettings(language = "tr", dns = "9.9.9.9", autoStrategy = false, dynamicColor = false, wizardDone = true)
        assertEquals(s, AppSettings.decode(s.encode()))
        assertEquals(AppSettings(), AppSettings.decode("{broken"))
        assertTrue(AppSettings.isValidDns("94.140.14.14"))
        assertFalse(AppSettings.isValidDns("dns.google"))
        assertTrue(AppSettings.DNS_SERVERS.all { AppSettings.isValidDns(it.address) })
    }
}
