package io.github.halilkhrmn.dpimech.core

/**
 * Just enough IPv4/IPv6 + UDP for the packet filter in front of hev-socks5-tunnel: recognise a
 * UDP datagram and build the reply to it. Fragments and IPv6 extension headers are left alone
 * (they pass through untouched).
 */
object IpPacket {
    private const val UDP = 17

    class Udp(
        val v6: Boolean,
        val src: ByteArray,
        val dst: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val payload: ByteArray,
    )

    /** Destination port of a UDP packet in the first [len] bytes of [buf], or -1 when it is not one. */
    fun udpDstPort(buf: ByteArray, len: Int): Int {
        val off = udpOffset(buf, len)
        return if (off < 0) -1 else u16(buf, off + 2)
    }

    /** The UDP datagram in the first [len] bytes of [buf], or null for anything else. */
    fun udp(buf: ByteArray, len: Int): Udp? {
        val off = udpOffset(buf, len)
        if (off < 0) return null
        val v6 = version(buf) == 6
        val ipEnd = if (v6) minOf(len, 40 + u16(buf, 4)) else minOf(len, u16(buf, 2))
        val end = minOf(ipEnd, off + u16(buf, off + 4))
        if (end < off + 8) return null
        return Udp(
            v6 = v6,
            src = if (v6) buf.copyOfRange(8, 24) else buf.copyOfRange(12, 16),
            dst = if (v6) buf.copyOfRange(24, 40) else buf.copyOfRange(16, 20),
            srcPort = u16(buf, off),
            dstPort = u16(buf, off + 2),
            payload = buf.copyOfRange(off + 8, end),
        )
    }

    /** The answer to [req]: addresses and ports swapped, carrying [payload]. */
    fun reply(req: Udp, payload: ByteArray): ByteArray = build(req.v6, req.dst, req.dstPort, req.src, req.srcPort, payload)

    /** An IP/UDP packet with valid IP and UDP checksums. */
    fun build(v6: Boolean, from: ByteArray, fromPort: Int, to: ByteArray, toPort: Int, payload: ByteArray): ByteArray {
        val udpLen = 8 + payload.size
        val ipLen = if (v6) 40 else 20
        val p = ByteArray(ipLen + udpLen)
        if (v6) {
            p[0] = 0x60
            put16(p, 4, udpLen)
            p[6] = UDP.toByte()
            p[7] = 64
            from.copyInto(p, 8)
            to.copyInto(p, 24)
        } else {
            p[0] = 0x45
            put16(p, 2, p.size)
            p[8] = 64
            p[9] = UDP.toByte()
            from.copyInto(p, 12)
            to.copyInto(p, 16)
            put16(p, 10, checksum(p, 0, 20, 0))
        }
        put16(p, ipLen, fromPort)
        put16(p, ipLen + 2, toPort)
        put16(p, ipLen + 4, udpLen)
        payload.copyInto(p, ipLen + 8)
        // Pseudo-header: addresses, protocol and UDP length.
        val pseudo = sum(from, 0, from.size) + sum(to, 0, to.size) + UDP + udpLen
        var c = checksum(p, ipLen, udpLen, pseudo)
        if (c == 0) c = 0xFFFF
        put16(p, ipLen + 6, c)
        return p
    }

    private fun version(buf: ByteArray) = (buf[0].toInt() ushr 4) and 0xF

    /** Offset of the UDP header, or -1. */
    private fun udpOffset(buf: ByteArray, len: Int): Int {
        if (len < 1) return -1
        return when (version(buf)) {
            4 -> {
                val ihl = (buf[0].toInt() and 0xF) * 4
                when {
                    ihl < 20 || len < ihl + 8 -> -1
                    buf[9].toInt() != UDP -> -1
                    u16(buf, 6) and 0x3FFF != 0 -> -1 // a fragment (more fragments or an offset)
                    else -> ihl
                }
            }
            6 -> if (len < 48 || buf[6].toInt() != UDP) -1 else 40
            else -> -1
        }
    }

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun put16(b: ByteArray, i: Int, v: Int) {
        b[i] = (v ushr 8).toByte()
        b[i + 1] = v.toByte()
    }

    private fun sum(b: ByteArray, off: Int, len: Int): Int {
        var s = 0
        var i = 0
        while (i + 1 < len) {
            s += u16(b, off + i)
            i += 2
        }
        if (len % 2 == 1) s += (b[off + len - 1].toInt() and 0xFF) shl 8
        return s
    }

    private fun checksum(b: ByteArray, off: Int, len: Int, start: Int): Int {
        var s = start.toLong() + sum(b, off, len)
        while (s ushr 16 != 0L) s = (s and 0xFFFF) + (s ushr 16)
        return s.toInt().inv() and 0xFFFF
    }

    /** Ones' complement check of a whole IPv4 header or UDP segment (for tests). */
    internal fun verify(b: ByteArray, off: Int, len: Int, start: Int = 0) = checksum(b, off, len, start) == 0
}
