package io.github.halilkhrmn.dpimech.core

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TunnelDnsTest {
    private val app4 = InetAddress.getByName("198.18.0.1").address
    private val dns4 = InetAddress.getByName("1.1.1.1").address
    private val app6 = InetAddress.getByName("fc00::1").address
    private val dns6 = InetAddress.getByName("2606:4700:4700::1111").address

    /** Query for example.com A, id 0x1234, RD set. */
    private val query = byteArrayOf(0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0) +
        byteArrayOf(7) + "example".toByteArray() + byteArrayOf(3) + "com".toByteArray() + byteArrayOf(0, 0, 1, 0, 1)

    /** Independent ones' complement sum check: a valid header or segment sums to 0xFFFF. */
    private fun onesSum(vararg parts: ByteArray): Int {
        var s = 0L
        for (p in parts) {
            var i = 0
            while (i < p.size) {
                val hi = p[i].toInt() and 0xFF
                val lo = if (i + 1 < p.size) p[i + 1].toInt() and 0xFF else 0
                s += (hi shl 8) or lo
                i += 2
            }
        }
        while (s ushr 16 != 0L) s = (s and 0xFFFF) + (s ushr 16)
        return s.toInt()
    }

    private fun pseudo(src: ByteArray, dst: ByteArray, len: Int) =
        src + dst + byteArrayOf(0, 17, (len ushr 8).toByte(), len.toByte())

    @Test
    fun ipv4UdpRoundTripWithValidChecksums() {
        val p = IpPacket.build(false, app4, 40000, dns4, 53, query)
        assertEquals(0xFFFF, onesSum(p.copyOf(20)), "IPv4 header checksum")
        val seg = p.copyOfRange(20, p.size)
        assertEquals(0xFFFF, onesSum(pseudo(app4, dns4, seg.size), seg), "UDP checksum")

        val u = assertNotNull(IpPacket.udp(p, p.size))
        assertEquals(53, u.dstPort)
        assertEquals(40000, u.srcPort)
        assertContentEquals(query, u.payload)
        assertEquals(53, IpPacket.udpDstPort(p, p.size))

        val r = IpPacket.reply(u, byteArrayOf(1, 2, 3))
        val back = assertNotNull(IpPacket.udp(r, r.size))
        assertContentEquals(dns4, back.src)
        assertContentEquals(app4, back.dst)
        assertEquals(53, back.srcPort)
        assertEquals(40000, back.dstPort)
    }

    @Test
    fun ipv6UdpRoundTripWithValidChecksum() {
        val p = IpPacket.build(true, app6, 5353, dns6, 53, query)
        val seg = p.copyOfRange(40, p.size)
        assertEquals(0xFFFF, onesSum(pseudo(app6, dns6, seg.size), seg))
        val u = assertNotNull(IpPacket.udp(p, p.size))
        assertTrue(u.v6)
        assertContentEquals(query, u.payload)
    }

    @Test
    fun ignoresTcpFragmentsAndGarbage() {
        val p = IpPacket.build(false, app4, 1, dns4, 53, query)
        val tcp = p.copyOf().also { it[9] = 6 }
        assertEquals(-1, IpPacket.udpDstPort(tcp, tcp.size))
        val frag = p.copyOf().also { it[6] = 0x20 } // more fragments
        assertNull(IpPacket.udp(frag, frag.size))
        assertNull(IpPacket.udp(byteArrayOf(0x45, 0), 2))
        assertNull(IpPacket.udp(ByteArray(0), 0))
    }

    @Test
    fun filterSendsDnsToDohAndDropsQuicOnlyWhenAsked() {
        val dns = IpPacket.build(false, app4, 1, dns4, 53, query)
        val quic = IpPacket.build(true, app6, 1, dns6, 443, ByteArray(20))
        val other = IpPacket.build(false, app4, 1, dns4, 3478, ByteArray(20))

        val both = TunnelFilter(encryptDns = true, blockQuic = true)
        assertEquals(TunnelFilter.Verdict.DNS, both.classify(dns, dns.size))
        assertEquals(TunnelFilter.Verdict.DROP, both.classify(quic, quic.size))
        assertEquals(TunnelFilter.Verdict.PASS, both.classify(other, other.size))
        assertEquals(1, both.dnsQueries.get())
        assertEquals(1, both.quicDropped.get())

        val none = TunnelFilter(encryptDns = false, blockQuic = false)
        assertEquals(false, none.active)
        assertEquals(TunnelFilter.Verdict.PASS, none.classify(dns, dns.size))
        assertEquals(TunnelFilter.Verdict.PASS, none.classify(quic, quic.size))
    }

    @Test
    fun servfailKeepsIdAndQuestion() {
        val r = DnsMessage.servfail(query)
        assertEquals(0x1234, DnsMessage.id(r))
        assertEquals(2, r[3].toInt() and 0x0F)
        assertTrue(r[2].toInt() and 0x80 != 0)
        assertEquals(query.size, r.size)
        assertEquals(query.size, DnsMessage.questionEnd(query))
        assertTrue(DnsMessage.isQuery(query))
        assertTrue(!DnsMessage.isQuery(r))
    }

    @Test
    fun dohSendsIdZeroAndRestoresTheId() {
        var sent: ByteArray? = null
        val doh = DohClient("https://x/dns-query", transport = { _, body -> sent = body; DnsMessage.withId(body, 0).also { it[2] = 0x81.toByte() } })
        val answer = assertNotNull(doh.resolve(query))
        assertEquals(0, DnsMessage.id(sent!!))
        assertEquals(0x1234, DnsMessage.id(answer))
    }

    @Test
    fun dohRestsAfterRepeatedFailures() {
        var time = 0L
        var calls = 0
        val doh = DohClient("https://x/dns-query", transport = { _, _ -> calls++; error("blocked") }, now = { time })
        repeat(DohClient.MAX_FAILURES) { assertNull(doh.resolve(query)) }
        assertEquals(DohClient.MAX_FAILURES, calls)
        assertNull(doh.resolve(query))
        assertEquals(DohClient.MAX_FAILURES, calls, "no request while resting")
        time += DohClient.REST_MS + 1
        doh.resolve(query)
        assertEquals(DohClient.MAX_FAILURES + 1, calls)
    }

    @Test
    fun knownServersHaveDohUrls() {
        AppSettings.DNS_SERVERS.forEach { assertTrue(DohClient.urlFor(it.address).startsWith("https://${it.address}/")) }
        assertEquals("https://1.1.1.1/dns-query", DohClient.urlFor("10.0.0.1"))
    }
}
