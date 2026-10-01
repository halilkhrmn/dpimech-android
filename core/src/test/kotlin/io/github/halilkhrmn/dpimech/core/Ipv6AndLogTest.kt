package io.github.halilkhrmn.dpimech.core

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Ipv6AndLogTest {
    private fun ips(vararg s: String) = s.map { InetAddress.getByName(it) }

    @Test
    fun onlyGlobalIpv6Counts() {
        assertFalse(NetworkInfo.hasGlobalIpv6(ips("192.168.1.20", "fe80::1c2b:3cff:fe4d:5e6f")), "typical IPv4-only Wi-Fi")
        assertFalse(NetworkInfo.hasGlobalIpv6(ips("fd12:3456::1", "::1")), "unique-local and loopback")
        assertTrue(NetworkInfo.hasGlobalIpv6(ips("10.0.0.5", "2a02:e0:1:2::5")), "global address")
        assertTrue(NetworkInfo.hasGlobalIpv6(ips("2001:db8::1")))
    }

    @Test
    fun tunnelYamlLeavesIpv6OutWhenOff() {
        assertTrue("ipv6:" in TunnelConfig(1080).hevYaml())
        val off = TunnelConfig(1080, useIpv6 = false).hevYaml()
        assertFalse("ipv6" in off, off)
        assertTrue("  ipv4: 198.18.0.1\nsocks5:" in off, off)
    }

    @Test
    fun repeatedLinesFold() {
        val c = LogCollapser()
        assertEquals("a" to false, c.add("a"))
        assertEquals("a (×2)" to true, c.add("a"))
        assertEquals("a (×3)" to true, c.add("a"))
        assertEquals("b" to false, c.add("b"))
        assertEquals("a" to false, c.add("a"))
    }
}
