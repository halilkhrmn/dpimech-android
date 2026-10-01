package io.github.halilkhrmn.dpimech.core

import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.random.Random
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Spots DNS blocking, ported from the desktop app (`dnscheck.rs`) and extended for Android:
 * besides the phone's own DNS it asks the tunnel's DNS server over plain UDP port 53, because
 * some providers answer every port-53 query themselves, whichever server it was meant for.
 * DNS over HTTPS (1.1.1.1) is the reference.
 */
object DnsCheck {
    enum class Verdict { MATCHES, NOT_RESOLVED, DIFFERENT, UNKNOWN }

    data class Finding(val name: String, val via: String, val verdict: Verdict, val got: Set<String>, val doh: Set<String>)

    /** Compares two IPv4 answers; different /16 networks count as a mismatch (CDNs vary within). */
    fun compare(name: String, via: String, got: Set<String>?, doh: Set<String>?): Finding = Finding(
        name, via,
        when {
            doh.isNullOrEmpty() -> Verdict.UNKNOWN
            got.isNullOrEmpty() -> Verdict.NOT_RESOLVED
            differentNetworks(got, doh) -> Verdict.DIFFERENT
            else -> Verdict.MATCHES
        },
        got.orEmpty(), doh.orEmpty(),
    )

    fun differentNetworks(a: Set<String>, b: Set<String>): Boolean {
        fun net(ip: String) = ip.split('.').take(2)
        val nets = b.map(::net).toSet()
        return a.none { net(it) in nets }
    }

    /** `https://1.1.1.1/dns-query?name=…&type=A` with `accept: application/dns-json`. */
    fun dohUrl(name: String) = "https://1.1.1.1/dns-query?name=$name&type=A"

    fun parseDohJson(text: String): Set<String>? = runCatching {
        Json.parseToJsonElement(text).jsonObject["Answer"]?.jsonArray.orEmpty()
            .map { it.jsonObject }
            .filter { it["type"]?.jsonPrimitive?.intOrNull == 1 }
            .mapNotNull { it["data"]?.jsonPrimitive?.content?.takeIf(::isIpv4) }
            .toSet()
    }.getOrNull()

    /** One A query over UDP; null when nothing (or garbage) came back in time. */
    fun queryUdp(server: InetSocketAddress, name: String, timeoutMs: Int = 3000): Set<String>? = try {
        DatagramSocket().use { s ->
            s.soTimeout = timeoutMs
            val id = Random.nextInt(0, 0x10000)
            val q = encodeQuery(name, id)
            s.send(DatagramPacket(q, q.size, server))
            val buf = ByteArray(1500)
            val p = DatagramPacket(buf, buf.size)
            s.receive(p)
            decodeA(buf.copyOf(p.length), id)
        }
    } catch (_: Exception) {
        null
    }

    fun encodeQuery(name: String, id: Int): ByteArray = ByteArrayOutputStream().apply {
        write(byteArrayOf((id shr 8).toByte(), id.toByte(), 1, 0, 0, 1, 0, 0, 0, 0, 0, 0))
        for (label in name.trimEnd('.').split('.')) {
            val b = label.toByteArray()
            require(b.size in 1..63) { "bad name" }
            write(b.size)
            write(b)
        }
        write(byteArrayOf(0, 0, 1, 0, 1))
    }.toByteArray()

    /** IPv4 addresses from the answer section of a reply to query [id]; null if malformed. */
    fun decodeA(msg: ByteArray, id: Int): Set<String>? = runCatching {
        fun u16(i: Int) = ((msg[i].toInt() and 0xff) shl 8) or (msg[i + 1].toInt() and 0xff)
        if (u16(0) != id || msg[2].toInt() and 0x80 == 0) return null
        val qd = u16(4)
        val an = u16(6)
        var i = 12
        fun skipName() {
            while (true) {
                val len = msg[i].toInt() and 0xff
                if (len and 0xc0 == 0xc0) { i += 2; return }
                i += 1
                if (len == 0) return
                i += len
            }
        }
        repeat(qd) { skipName(); i += 4 }
        val out = mutableSetOf<String>()
        repeat(an) {
            skipName()
            val type = u16(i)
            val rdlen = u16(i + 8)
            i += 10
            if (type == 1 && rdlen == 4) {
                out += (0 until 4).joinToString(".") { (msg[i + it].toInt() and 0xff).toString() }
            }
            i += rdlen
        }
        out
    }.getOrNull()

    private fun isIpv4(s: String) = s.split('.').let { p -> p.size == 4 && p.all { it.toIntOrNull() in 0..255 } }

    /** Plain host names worth checking (IP literals and odd strings are skipped). */
    fun namesToCheck(hosts: List<String>, max: Int = 4): List<String> = hosts
        .map { it.substringBefore('/') }
        .filter { h -> h.all { it.isLetterOrDigit() || it == '.' || it == '-' } && !isIpv4(h) }
        .distinct()
        .take(max)

    /** The phone's own resolver (DPIMech is outside the VPN, so this is the network's DNS). */
    fun system(name: String): Set<String>? = runCatching {
        InetAddress.getAllByName(name).filter { it.address.size == 4 }.map { it.hostAddress!! }.toSet()
    }.getOrNull()
}
