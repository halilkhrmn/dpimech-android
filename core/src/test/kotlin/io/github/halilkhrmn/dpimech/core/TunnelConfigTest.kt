package io.github.halilkhrmn.dpimech.core

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TunnelConfigTest {
    @Test
    fun yamlPointsAtLocalSocks() {
        val yaml = TunnelConfig(socksPort = 10808).hevYaml()
        assertTrue("socks5:\n  port: 10808\n  address: 127.0.0.1\n  udp: 'udp'\n" in yaml, yaml)
        assertTrue("tunnel:\n  mtu: 8500\n  ipv4: 198.18.0.1\n  ipv6: 'fc00::1'\n" in yaml, yaml)
        assertTrue(yaml.lines().none { it.startsWith(" ") && !it.startsWith("  ") }, "two-space indent")
    }

    @Test
    fun rejectsBadValues() {
        assertFailsWith<IllegalArgumentException> { TunnelConfig(socksPort = 0) }
        assertFailsWith<IllegalArgumentException> { TunnelConfig(socksPort = 1080, mtu = 100) }
    }
}
