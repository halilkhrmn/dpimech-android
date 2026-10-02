package io.github.halilkhrmn.dpimech.core

import kotlin.test.Test
import kotlin.test.assertEquals

class HostlistInputTest {
    @Test
    fun splitsAndCleansPastedAddresses() {
        assertEquals(
            listOf("example.com", "www.wattpad.com", "discord.gg", "cdn.x.com"),
            Hostlist.parseInput("Example.com, https://www.wattpad.com/story/1?x=2\ndiscord.gg:443  *.cdn.x.com example.com"),
        )
    }

    @Test
    fun dropsInvalidEntries() {
        assertEquals(listOf("ok.net"), Hostlist.parseInput("bad_name ok.net .. -x.com"))
        assertEquals(emptyList(), Hostlist.parseInput("   "))
    }

    @Test
    fun typingOnlyMakesPillsOfFinishedNames() {
        // Keyboards that add a space after "." must not cut "example." off.
        assertEquals(emptyList<String>() to "example.", Hostlist.splitTyped("example. "))
        assertEquals(emptyList<String>() to "example.com", Hostlist.splitTyped("example.com"))
        assertEquals(listOf("example.com") to "", Hostlist.splitTyped("example.com "))
        assertEquals(listOf("a.com", "www.b.org") to "c", Hostlist.splitTyped("a.com, https://www.b.org/x?y=1 c"))
        // A word without a dot is not a site.
        assertEquals(emptyList<String>() to "example x", Hostlist.splitTyped("example x"))
        assertEquals(emptyList(), Hostlist.parseInput("localhost"))
    }
}

