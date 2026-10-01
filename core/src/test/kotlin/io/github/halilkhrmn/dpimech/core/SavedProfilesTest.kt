package io.github.halilkhrmn.dpimech.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SavedProfilesTest {
    private val s = StrategyEntry("TLS record split", "-r 1+s")
    private val a = Profile("a", "Discord", packs = listOf("discord"), strategy = s, apps = listOf("com.discord"))
    private val b = Profile("b", "YouTube", packs = listOf("youtube"), strategy = s, appMode = AppMode.ALL_EXCEPT)

    @Test
    fun roundTripsAndKeepsSelection() {
        val saved = SavedProfiles().upsert(a).upsert(b)
        assertEquals("a", saved.selected?.id)
        val back = SavedProfiles.decode(saved.select("b").encode())
        assertEquals(listOf(a, b), back.profiles)
        assertEquals("b", back.selected?.id)
    }

    @Test
    fun updateRemoveAndDamagedFiles() {
        val saved = SavedProfiles().upsert(a).upsert(b).upsert(a.copy(name = "Discord 2"))
        assertEquals(listOf("Discord 2", "YouTube"), saved.profiles.map { it.name })
        assertEquals("b", saved.remove("a").selected?.id)
        assertNull(saved.remove("a").remove("b").selected)
        assertEquals("a", saved.select("missing").selected?.id)
        assertEquals(SavedProfiles(), SavedProfiles.decode("{not json"))
        // Fields added by newer versions are ignored.
        assertEquals("x", SavedProfiles.decode("""{"profiles":[],"selectedId":"x","future":1}""").selectedId)
    }
}
