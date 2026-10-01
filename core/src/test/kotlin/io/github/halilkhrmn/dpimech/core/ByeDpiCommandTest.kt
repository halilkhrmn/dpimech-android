package io.github.halilkhrmn.dpimech.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ByeDpiCommandTest {
    private val lists = File("/data/lists")
    private val hosts = File(lists, "discord.txt")

    private fun build(args: String, filter: Boolean = false, hostlist: File? = hosts, protect: String? = null) =
        ByeDpiCommand.build(ByeDpiCommand.Request(args, 1080, lists, hostlist, filter, protect))

    @Test
    fun addsManagedOptionsAndResolvesPlaceholders() {
        assertEquals(
            listOf("-i", "127.0.0.1", "-p", "1080", "-c", "4096", "-f1", "-t6", "-n", "www.google.com"),
            build("-f1 -t6 -n {sni}").getOrThrow(),
        )
        assertEquals(
            listOf("-i", "127.0.0.1", "-p", "1080", "--protect-path", "/data/p.sock", "-c", "64", "-s1"),
            build("-c 64 -s1", protect = "/data/p.sock").getOrThrow(),
        )
        assertEquals(
            listOf("-i", "127.0.0.1", "-p", "1080", "-c", "4096", "-H", hosts.path, "-s1"),
            build("-H {hostlist} -s1").getOrThrow(),
        )
    }

    @Test
    fun domainFilterScopesEveryGroup() {
        val h = listOf("--hosts", hosts.path)
        val base = listOf("-i", "127.0.0.1", "-p", "1080", "-c", "4096")
        assertEquals(base + h + listOf("-r", "1+s"), build("-r 1+s", filter = true).getOrThrow())
        assertEquals(
            base + h + listOf("-d1", "-a1", "-At,r,s") + h + listOf("-s1+s", "-r1+s"),
            build("-d1 -a1 -At,r,s -s1+s -r1+s", filter = true).getOrThrow(),
        )
        assertEquals(
            base + h + listOf("-s1", "--auto", "t") + h + listOf("-d1", "--auto=r") + h,
            build("-s1 --auto t -d1 --auto=r", filter = true).getOrThrow(),
        )
        // A strategy with its own host list is left as it is.
        assertEquals(base + listOf("-H", ":x.com", "-s1"), build("-H :x.com -s1", filter = true).getOrThrow())
    }

    @Test
    fun failsWithoutHostlistOrOnBadArgs() {
        assertTrue(build("-s1", filter = true, hostlist = null).isFailure)
        assertTrue(build("-H {hostlist}", hostlist = null).isFailure)
        assertTrue(build("-p 99").isFailure)
        assertTrue(ByeDpiCommand.build(ByeDpiCommand.Request("-s1", 0, lists)).isFailure)
    }
}
