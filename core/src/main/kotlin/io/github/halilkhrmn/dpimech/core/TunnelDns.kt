package io.github.halilkhrmn.dpimech.core

import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicLong

/**
 * What the packet filter in front of hev-socks5-tunnel does with each packet the bypassed apps
 * send: DNS queries (UDP port 53, to any server) are answered by the app itself when
 * [interceptDns] (DNS over HTTPS, or the network's own DNS), QUIC (UDP port 443) is dropped when
 * the switch is on so apps fall back to TCP, the rest passes.
 */
class TunnelFilter(val interceptDns: Boolean, val blockQuic: Boolean) {
    enum class Verdict { PASS, DROP, DNS }

    val dnsQueries = AtomicLong()
    val quicDropped = AtomicLong()

    /** Whether the filter has anything to do; otherwise hev gets the TUN directly. */
    val active get() = interceptDns || blockQuic

    fun classify(buf: ByteArray, len: Int): Verdict = when (IpPacket.udpDstPort(buf, len)) {
        DNS_PORT -> if (interceptDns) Verdict.DNS.also { dnsQueries.incrementAndGet() } else Verdict.PASS
        QUIC_PORT -> if (blockQuic) Verdict.DROP.also { quicDropped.incrementAndGet() } else Verdict.PASS
        else -> Verdict.PASS
    }

    companion object {
        const val DNS_PORT = 53
        const val QUIC_PORT = 443
    }
}

/** DNS messages in wire format (RFC 1035), only what DoH needs. */
object DnsMessage {
    fun id(msg: ByteArray) = if (msg.size < 2) 0 else ((msg[0].toInt() and 0xFF) shl 8) or (msg[1].toInt() and 0xFF)

    fun withId(msg: ByteArray, id: Int): ByteArray = msg.copyOf().also {
        if (it.size >= 2) {
            it[0] = (id ushr 8).toByte()
            it[1] = id.toByte()
        }
    }

    /** A plausible query: header, one question, not a response. */
    fun isQuery(msg: ByteArray) = msg.size >= 17 && msg[2].toInt() and 0x80 == 0 && questionEnd(msg) > 0

    /** SERVFAIL for [query], so the app gives up at once instead of waiting for a timeout. */
    fun servfail(query: ByteArray): ByteArray {
        val end = questionEnd(query).takeIf { it > 0 } ?: 12.coerceAtMost(query.size)
        val r = query.copyOf(end)
        if (r.size >= 12) {
            r[2] = (0x80 or (query[2].toInt() and 0x01)).toByte() // QR, keep RD
            r[3] = 0x82.toByte() // RA, rcode 2
            for (i in 6 until 12) r[i] = 0 // no answer, authority or additional records
        }
        return r
    }

    /** End of the first question (after QTYPE and QCLASS), or -1. */
    fun questionEnd(msg: ByteArray): Int {
        if (msg.size < 12 || ((msg[4].toInt() and 0xFF) shl 8 or (msg[5].toInt() and 0xFF)) < 1) return -1
        var i = 12
        while (i < msg.size) {
            val l = msg[i].toInt() and 0xFF
            if (l == 0) return (i + 5).takeIf { it <= msg.size } ?: -1
            if (l and 0xC0 != 0) return -1 // no compression in a question
            i += l + 1
        }
        return -1
    }
}

/**
 * DNS over HTTPS (RFC 8484, POST) to the server picked in the settings. DPIMech is outside the
 * VPN, so the request goes straight out. After a few failures in a row the client rests for a
 * while and [resolve] returns null at once, so the filter can fall back to plain DNS instead of
 * making every lookup wait for a timeout.
 */
class DohClient(
    val url: String,
    private val transport: (url: String, body: ByteArray) -> ByteArray = ::post,
    private val now: () -> Long = System::currentTimeMillis,
) : DnsUpstream {
    private var failures = 0
    private var restUntil = 0L
    val answered = AtomicLong()
    val failed = AtomicLong()

    /** The answer to [query] (its id kept), or null when DoH is not available right now. */
    override fun resolve(query: ByteArray): ByteArray? {
        if (!DnsMessage.isQuery(query)) return null
        synchronized(this) { if (now() < restUntil) return null }
        val answer = runCatching { transport(url, DnsMessage.withId(query, 0)) }.getOrNull()
            ?.takeIf { it.size >= 12 }
        if (answer == null) {
            failed.incrementAndGet()
            synchronized(this) {
                if (++failures >= MAX_FAILURES) {
                    restUntil = now() + REST_MS
                    failures = 0
                }
            }
            return null
        }
        synchronized(this) { failures = 0 }
        answered.incrementAndGet()
        return DnsMessage.withId(answer, DnsMessage.id(query))
    }

    companion object {
        const val MAX_FAILURES = 3
        const val REST_MS = 60_000L
        private const val TIMEOUT_MS = 4000

        /**
         * DoH endpoints of the servers offered in the settings: by IP where the server's
         * certificate allows it, so no lookup is needed first; DNS4EU and Comss only answer DoH on
         * their names (looked up by the system resolver: DPIMech itself is outside the VPN).
         */
        private val KNOWN = mapOf(
            "1.1.1.1" to "https://1.1.1.1/dns-query",
            "1.0.0.1" to "https://1.0.0.1/dns-query",
            "8.8.8.8" to "https://8.8.8.8/dns-query",
            "8.8.4.4" to "https://8.8.4.4/dns-query",
            "9.9.9.9" to "https://9.9.9.9/dns-query",
            "94.140.14.14" to "https://94.140.14.14/dns-query",
            "86.54.11.100" to "https://unfiltered.joindns4.eu/dns-query",
            "83.220.169.155" to "https://dns.comss.one/dns-query",
        )

        /** DoH URL for a DNS server address or the user's own DoH address; unknown servers use Cloudflare. */
        fun urlFor(server: String) = if (AppSettings.isCustomDoh(server)) server else KNOWN[server] ?: KNOWN.getValue("1.1.1.1")

        fun post(url: String, body: ByteArray): ByteArray {
            // Not disconnected afterwards: the connection stays in the keep-alive pool.
            val c = URI(url).toURL().openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.doOutput = true
            c.setRequestProperty("content-type", "application/dns-message")
            c.setRequestProperty("accept", "application/dns-message")
            c.outputStream.use { it.write(body) }
            check(c.responseCode == 200) { "DoH HTTP ${c.responseCode}" }
            return c.inputStream.use { it.readBytes() }
        }
    }
}

/** Somewhere DNS queries can go: the answer to a wire-format query, or null when it cannot answer now. */
fun interface DnsUpstream {
    fun resolve(query: ByteArray): ByteArray?
}

/**
 * The chosen server first ([primary], DNS over HTTPS); when it cannot answer, the network's own
 * DNS ([fallback]). Some providers block Google, Cloudflare and the like, DoH included, but their
 * own resolver always works. [onFallback] runs once, the first time the fallback answers, so the
 * user can be told. With no primary (the "system DNS" choice) only the fallback is asked.
 */
class DnsChain(
    private val primary: DnsUpstream?,
    private val fallback: DnsUpstream?,
    private val onFallback: () -> Unit = {},
) : DnsUpstream {
    /** Queries the fallback answered. */
    val fallbackAnswered = AtomicLong()
    private val told = java.util.concurrent.atomic.AtomicBoolean(primary == null)

    override fun resolve(query: ByteArray): ByteArray? {
        primary?.resolve(query)?.let { return it }
        val answer = fallback?.resolve(query) ?: return null
        fallbackAnswered.incrementAndGet()
        if (told.compareAndSet(false, true)) onFallback()
        return answer
    }
}

