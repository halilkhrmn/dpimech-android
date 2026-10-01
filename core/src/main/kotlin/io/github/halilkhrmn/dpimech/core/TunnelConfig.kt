package io.github.halilkhrmn.dpimech.core

/**
 * Settings for the VPN interface and hev-socks5-tunnel, which moves the TUN traffic of the
 * selected apps to ciadpi's local SOCKS5 port.
 */
data class TunnelConfig(
    val socksPort: Int,
    val mtu: Int = 8500,
    val ipv4: String = "198.18.0.1",
    val ipv6: String = "fc00::1",
    /** DNS server announced to the bypassed apps; its queries go through ciadpi over UDP. */
    val dns: String = "1.1.1.1",
    val logLevel: String = "warn",
) {
    init {
        require(socksPort in 1..65535) { "invalid port $socksPort" }
        require(mtu in 1280..65535) { "invalid MTU $mtu" }
    }

    /** hev-socks5-tunnel YAML (`conf/main.yml` layout). The TUN fd is passed separately. */
    fun hevYaml(): String = """
        |tunnel:
        |  mtu: $mtu
        |  ipv4: $ipv4
        |  ipv6: '$ipv6'
        |socks5:
        |  port: $socksPort
        |  address: ${ByeDpiCommand.LOCALHOST}
        |  udp: 'udp'
        |misc:
        |  task-stack-size: 81920
        |  log-level: $logLevel
        |
    """.trimMargin()
}
