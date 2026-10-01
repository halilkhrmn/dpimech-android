package io.github.halilkhrmn.dpimech.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArgsTest {
    @Test
    fun splitsPlainAndQuoted() {
        assertEquals(listOf("-r", "1+s"), splitArgs("-r 1+s"))
        assertEquals(
            listOf("--hostlist", "C:\\my lists\\a.txt", "-x", ""),
            splitArgs("--hostlist \"C:\\my lists\\a.txt\"  -x ''"),
        )
        assertTrue(splitArgs("   ").isEmpty())
    }

    @Test
    fun joinRoundTrips() {
        val cases = listOf(
            listOf("-r", "1+s"),
            listOf("-H", "/data/my lists/a.txt", "-x", ""),
            listOf("-l", ":say \"hi\""),
            listOf("-l", ":it's"),
        )
        for (c in cases) assertEquals(c, splitArgs(joinArgs(c)), joinArgs(c))
    }
}
