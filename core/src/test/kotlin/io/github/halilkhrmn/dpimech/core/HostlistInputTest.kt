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
}
