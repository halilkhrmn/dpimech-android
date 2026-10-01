package io.github.halilkhrmn.dpimech.core

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuickCheckBackupTest {
    private val dir: File = Files.createTempDirectory("quick").toFile()
    private val s = StrategyEntry("TLS record split", "-r 1+s")

    private fun check(direct: Long?, bypass: Long?, started: MutableList<List<String>> = mutableListOf()) = QuickCheck(
        launcher = { args, _ -> started += args; AutoCloseable {} },
        listsDir = dir,
        measure = { c, _ -> if (c === Connector.DIRECT) direct else bypass },
        viaProxy = { Connector { _, _, _ -> error("not used") } },
    )

    @Test
    fun verdicts() {
        assertEquals(QuickCheck.Verdict.OPEN, check(40, 50).run("a.com", s).verdict)
        assertEquals(QuickCheck.Verdict.BYPASSED, check(null, 90).run("a.com", s).verdict)
        assertEquals(QuickCheck.Verdict.BLOCKED, check(null, null).run("a.com", s).verdict)
    }

    @Test
    fun engineIsLimitedToTheHostAndTheListIsRemoved() {
        val started = mutableListOf<List<String>>()
        check(null, 90, started).run("discord.com", s)
        val args = started.single()
        assertTrue("--hosts" in args, args.toString())
        assertTrue(dir.listFiles().orEmpty().none { it.name.startsWith("quick-") })
    }

    @Test
    fun badStrategyIsReported() {
        val r = check(null, 1).run("a.com", StrategyEntry("bad", "--ip 1.2.3.4"))
        assertTrue(r.error != null)
        assertNull(r.bypassMs)
    }

    @Test
    fun hostFromPastedAddress() {
        assertEquals("www.wattpad.com", check(1, 1).hostOf("https://www.wattpad.com/story/1"))
        assertNull(check(1, 1).hostOf("not a host!"))
    }

    @Test
    fun backupRoundTripAndMerge() {
        val a = Profile("a", "Discord", strategy = s)
        val b = Profile("b", "YouTube", strategy = s).withStrategy(StrategyEntry("x", "-d1"), "AS9121")
        val text = ProfileBackup.of(SavedProfiles(listOf(a, b), "a")).encode()
        val back = ProfileBackup.decode(text).getOrThrow()
        assertEquals(listOf(a, b), back.profiles)

        val existing = SavedProfiles(listOf(Profile("a", "Old name", strategy = s), Profile("c", "Mine", strategy = s)), "c")
        val merged = ProfileBackup.merge(existing, back)
        assertEquals(listOf("a", "c", "b"), merged.profiles.map { it.id })
        assertEquals("Discord", merged.profiles.first().name)
        assertEquals("c", merged.selectedId)
    }

    @Test
    fun rejectsOtherFilesAndNewerVersions() {
        assertTrue(ProfileBackup.decode("{\"profiles\":[]}").isSuccess) // defaults: our format
        assertTrue(ProfileBackup.decode("{\"format\":\"other\"}").isFailure)
        assertTrue(ProfileBackup.decode("{\"format\":\"dpimech-profiles\",\"version\":99}").isFailure)
        assertTrue(ProfileBackup.decode("not json").isFailure)
    }
}
