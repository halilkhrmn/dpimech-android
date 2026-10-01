package io.github.halilkhrmn.dpimech.core

import java.io.DataInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/** The few SOCKS5 calls DPIMech needs to talk to ciadpi on 127.0.0.1. */
object Socks5 {
    /** Health probe: ciadpi answers the greeting (no auth) when its event loop is alive. */
    fun greets(port: Int, timeoutMs: Int = 3000): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(ByeDpiCommand.LOCALHOST, port), timeoutMs)
            s.soTimeout = timeoutMs
            s.getOutputStream().write(byteArrayOf(5, 1, 0))
            val reply = ByteArray(2)
            DataInputStream(s.getInputStream()).readFully(reply)
            reply[0].toInt() == 5 && reply[1].toInt() == 0
        }
    } catch (_: IOException) {
        false
    }

    /**
     * CONNECT through the proxy to [host]:[port]. A host name is sent as a name (like
     * `socks5h`), so ciadpi resolves it, the way a bypassed app's traffic would go.
     */
    fun connect(proxyPort: Int, host: String, port: Int, timeoutMs: Int): Socket {
        val s = Socket()
        try {
            s.connect(InetSocketAddress(ByeDpiCommand.LOCALHOST, proxyPort), timeoutMs)
            s.soTimeout = timeoutMs
            val out = s.getOutputStream()
            val inp = DataInputStream(s.getInputStream())
            out.write(byteArrayOf(5, 1, 0))
            val greet = ByteArray(2).also { inp.readFully(it) }
            if (greet[0].toInt() != 5 || greet[1].toInt() != 0) throw IOException("SOCKS5 greeting refused")

            val ipv4 = host.split('.').takeIf { p -> p.size == 4 && p.all { it.toIntOrNull() in 0..255 } }
            val req = buildList<Byte> {
                addAll(listOf(5, 1, 0).map { it.toByte() })
                if (ipv4 != null) {
                    add(1)
                    ipv4.forEach { add(it.toInt().toByte()) }
                } else {
                    val name = host.toByteArray()
                    require(name.size in 1..255) { "bad host name" }
                    add(3)
                    add(name.size.toByte())
                    name.forEach { add(it) }
                }
                add((port shr 8).toByte())
                add(port.toByte())
            }
            out.write(req.toByteArray())
            val head = ByteArray(4).also { inp.readFully(it) }
            if (head[1].toInt() != 0) throw IOException("SOCKS5 connect failed (code ${head[1]})")
            // Skip the bound address in the reply.
            val skip = when (head[3].toInt()) {
                1 -> 4
                4 -> 16
                3 -> inp.readUnsignedByte()
                else -> throw IOException("bad SOCKS5 reply")
            } + 2
            inp.readFully(ByteArray(skip))
            return s
        } catch (e: Exception) {
            s.close()
            throw e
        }
    }
}
