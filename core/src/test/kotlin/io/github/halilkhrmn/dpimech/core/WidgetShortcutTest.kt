package io.github.halilkhrmn.dpimech.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WidgetShortcutTest {
    private val s = StrategyEntry("s", "-s1")
    private val a = Profile("a", "A", strategy = s)
    private val b = Profile("b", "B", strategy = s)
    private val c = Profile("c", "C", strategy = s)

    @Test
    fun nextProfileWrapsAround() {
        val saved = SavedProfiles(listOf(a, b, c), "a")
        assertEquals("b", saved.selectNext().selectedId)
        assertEquals("a", saved.select("c").selectNext().selectedId)
        assertEquals("a", SavedProfiles(listOf(a)).selectNext().selectedId)
        assertEquals(SavedProfiles(), SavedProfiles().selectNext())
    }

    @Test
    fun appToOpen() {
        val discord = Profile("1", "Discord", packs = listOf("discord"), strategy = s, apps = listOf("com.gone", "com.discord"))
        assertEquals("com.discord", discord.appToOpen(setOf("com.discord")))
        assertNull(discord.appToOpen(emptySet()))
        val whole = CountryPreset.forCountry("TR").profile("2", "Blocked", s)
        assertEquals("com.discord", whole.appToOpen(setOf("com.roblox.client", "com.discord")), "first pack with an installed app")
        assertEquals("com.roblox.client", whole.appToOpen(setOf("com.roblox.client")))
    }
}
