package io.github.halilkhrmn.dpimech.core

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DnsCheckTest {
    @Test
    fun verdicts() {
        val doh = setOf("162.159.135.232", "162.159.136.232")
        assertEquals(DnsCheck.Verdict.MATCHES, DnsCheck.compare("d", "sys", setOf("162.159.138.232"), doh).verdict)
        assertEquals(DnsCheck.Verdict.DIFFERENT, DnsCheck.compare("d", "sys", setOf("195.175.254.2"), doh).verdict)
        assertEquals(DnsCheck.Verdict.NOT_RESOLVED, DnsCheck.compare("d", "sys", null, doh).verdict)
        assertEquals(DnsCheck.Verdict.UNKNOWN, DnsCheck.compare("d", "sys", setOf("1.2.3.4"), emptySet()).verdict)
    }

    @Test
    fun parsesDohJson() {
        val text = """{"Status":0,"Answer":[{"name":"discord.com","type":5,"TTL":1,"data":"x.y."},
            {"name":"discord.com","type":1,"TTL":300,"data":"162.159.135.232"}]}"""
        assertEquals(setOf("162.159.135.232"), DnsCheck.parseDohJson(text))
        assertEquals(emptySet(), DnsCheck.parseDohJson("""{"Status":3}"""))
        // A real answer from cloudflare-dns.com (2026-10-01).
        val real = """{"Status":0,"TC":false,"RD":true,"RA":true,"AD":false,"CD":false,"Question":[{"name":"discord.com","type":1}],""" +
            """"Answer":[{"name":"discord.com","type":1,"TTL":27,"data":"162.159.138.232"},{"name":"discord.com","type":1,"TTL":27,"data":"162.159.128.233"}]}"""
        assertEquals(setOf("162.159.138.232", "162.159.128.233"), DnsCheck.parseDohJson(real))
        assertNull(DnsCheck.parseDohJson("<html>"))
    }

    @Test
    fun namesToCheckSkipsIpsAndDuplicates() {
        assertEquals(listOf("a.com", "b.com"), DnsCheck.namesToCheck(listOf("a.com", "1.2.3.4", "a.com/x", "b.com", "bad host")))
    }

    /** A fake DNS server answers with CNAME + A records using name compression. */
    @Test
    fun udpQueryRoundTrip() {
        val server = DatagramSocket(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
        thread(isDaemon = true) {
            val buf = ByteArray(512)
            val p = DatagramPacket(buf, buf.size)
            server.receive(p)
            val q = buf.copyOf(p.length)
            val reply = q.copyOf().also {
                it[2] = 0x81.toByte(); it[3] = 0x80.toByte(); it[7] = 2 // QR, RA, ANCOUNT=2
            } + byteArrayOf(
                0xc0.toByte(), 12, 0, 5, 0, 1, 0, 0, 0, 60, 0, 2, 0xc0.toByte(), 12, // CNAME → itself (compressed)
                0xc0.toByte(), 12, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4, 10, 1, 2, 3, // A 10.1.2.3
            )
            server.send(DatagramPacket(reply, reply.size, p.socketAddress))
        }
        val got = DnsCheck.queryUdp(server.localSocketAddress as InetSocketAddress, "discord.com", 3000)
        assertEquals(setOf("10.1.2.3"), got)
        server.close()
    }

    @Test
    fun rejectsWrongIdOrQuery() {
        val q = DnsCheck.encodeQuery("a.b", 7)
        assertNull(DnsCheck.decodeA(q, 7), "a query is not a reply")
        assertNull(DnsCheck.decodeA(q.also { it[2] = 0x81.toByte() }, 8))
    }
}
