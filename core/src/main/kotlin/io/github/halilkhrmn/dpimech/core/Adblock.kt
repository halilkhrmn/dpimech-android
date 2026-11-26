package io.github.halilkhrmn.dpimech.core

/**
 * Ad and tracker blocking by DNS (DECISIONS #33): a profile can turn it on, pick block lists and
 * keep its own allowlist. The packet filter answers the profile's apps' DNS; a name on a list gets
 * an empty address, so the app cannot reach it. Lists are data downloaded at runtime (never
 * bundled), like the strategy lists.
 */
data class BlockList(val id: String, val name: String, val url: String, val license: String = "") {
    val custom get() = id.startsWith(CUSTOM_PREFIX)

    companion object {
        const val CUSTOM_PREFIX = "url:"
        private const val HAGEZI = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard"

        /** Offered in every profile; the user ticks the ones to use. Notes are app strings. */
        val BUILT_IN = listOf(
            BlockList("hagezi-light", "HaGeZi Light", "$HAGEZI/light-onlydomains.txt", "GPL-3.0"),
            BlockList("hagezi-normal", "HaGeZi Normal", "$HAGEZI/multi-onlydomains.txt", "GPL-3.0"),
            BlockList("hagezi-pro", "HaGeZi Pro", "$HAGEZI/pro-onlydomains.txt", "GPL-3.0"),
            BlockList("adguard-dns", "AdGuard DNS filter", "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt", "GPL-3.0"),
            BlockList("stevenblack", "StevenBlack hosts", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts", "MIT"),
            BlockList("oisd-small", "OISD small", "https://small.oisd.nl/domainswild2", "GPL-3.0"),
            BlockList(
                "peterlowe", "Peter Lowe's list",
                "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
            ),
        )

        /** Ticked when a profile turns blocking on for the first time. */
        const val DEFAULT = "hagezi-normal"

        /** A list the user added by its address (http or https), or null if it is not one. */
        fun custom(url: String): BlockList? {
            val u = url.trim()
            if (!(u.startsWith("https://") || u.startsWith("http://")) || u.length > 2000 || u.any { it.isWhitespace() }) return null
            val host = u.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':')
            if (Hostlist.normalize(host) == null) return null
            return BlockList(CUSTOM_PREFIX + u, host, u)
        }

        /** A built-in list by id, or a custom one from its `url:` id. */
        fun byId(id: String): BlockList? = BUILT_IN.find { it.id == id } ?: id.takeIf { it.startsWith(CUSTOM_PREFIX) }?.let { custom(it.removePrefix(CUSTOM_PREFIX)) }
    }
}

/** What a block list says: names to block and exceptions (both with their subdomains). */
data class ParsedList(val block: List<String>, val allow: List<String>)

/**
 * Reads the usual list formats: hosts files (`0.0.0.0 example.com`), plain domains (one per line,
 * `*.` allowed) and the DNS part of the adblock syntax (`||example.com^`, `@@||example.com^`).
 * Anything else (cosmetic rules, regular expressions, wildcards, other modifiers) is skipped.
 */
object BlockListParser {
    private val LOCAL = setOf(
        "localhost", "localhost.localdomain", "local", "broadcasthost", "ip6-localhost", "ip6-loopback",
        "ip6-localnet", "ip6-mcastprefix", "ip6-allnodes", "ip6-allrouters", "ip6-allhosts", "0.0.0.0",
    )
    private val HOSTS_ADDRESSES = setOf("0.0.0.0", "127.0.0.1", "::", "::1", "0", "::0")

    fun parse(text: String): ParsedList {
        val block = ArrayList<String>()
        val allow = ArrayList<String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line[0] == '!' || line[0] == '#' || line[0] == '[') continue
            when {
                line.startsWith("@@") -> adblock(line.substring(2))?.let(allow::add)
                line.startsWith("||") -> adblock(line)?.let(block::add)
                else -> {
                    val parts = line.substringBefore('#').trim().split(' ', '\t').filter { it.isNotEmpty() }
                    val names = if (parts.size > 1 && parts[0] in HOSTS_ADDRESSES) parts.drop(1) else parts.take(1)
                    names.forEach { name -> domain(name)?.let(block::add) }
                }
            }
        }
        return ParsedList(block, allow)
    }

    /** `||example.com^`, optionally `$important`; null for anything narrower or wider. */
    private fun adblock(rule: String): String? {
        if (!rule.startsWith("||")) return null
        val body = rule.substring(2)
        val modifiers = body.substringAfter('$', "")
        if (modifiers.isNotEmpty() && modifiers != "important") return null
        val name = body.substringBefore('$').removeSuffix("^").removeSuffix("|")
        return domain(name)
    }

    private fun domain(name: String): String? {
        if ('*' in name.removePrefix("*.") || '/' in name) return null
        val d = Hostlist.normalize(name) ?: return null
        if (d in LOCAL || '.' !in d || d.all { it.isDigit() || it == '.' }) return null
        return d
    }
}

/**
 * A large set of domain names, packed: one sorted byte array (names separated by `\n`) and their
 * start offsets, about 20 bytes a name instead of ~100 in a HashSet. [matches] is true for a name
 * in the set or any subdomain of one. [bytes] is also the file format, so a compiled list loads
 * without parsing.
 */
class DomainSet private constructor(val bytes: ByteArray, private val starts: IntArray) {
    val size get() = starts.size

    fun contains(name: String): Boolean {
        val key = name.toByteArray(Charsets.US_ASCII)
        var lo = 0
        var hi = starts.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = compare(mid, key)
            when {
                c < 0 -> lo = mid + 1
                c > 0 -> hi = mid - 1
                else -> return true
            }
        }
        return false
    }

    /** [host] or one of its parent domains is in the set. */
    fun matches(host: String): Boolean {
        var h = host
        while (true) {
            if (contains(h)) return true
            val dot = h.indexOf('.')
            if (dot < 0 || dot == h.length - 1) return false
            h = h.substring(dot + 1)
        }
    }

    private fun compare(i: Int, key: ByteArray): Int {
        var p = starts[i]
        val end = if (i + 1 < starts.size) starts[i + 1] - 1 else bytes.size - 1
        var k = 0
        while (p < end && k < key.size) {
            val d = (bytes[p].toInt() and 0xFF) - (key[k].toInt() and 0xFF)
            if (d != 0) return d
            p++
            k++
        }
        return (end - p) - (key.size - k)
    }

    companion object {
        val EMPTY = DomainSet(ByteArray(0), IntArray(0))

        fun of(names: Collection<String>): DomainSet {
            val sorted = names.mapNotNull(Hostlist::normalize).toSortedSet()
            if (sorted.isEmpty()) return EMPTY
            val out = java.io.ByteArrayOutputStream(sorted.sumOf { it.length + 1 })
            val starts = IntArray(sorted.size)
            sorted.forEachIndexed { i, n ->
                starts[i] = out.size()
                out.write(n.toByteArray(Charsets.US_ASCII))
                out.write('\n'.code)
            }
            return DomainSet(out.toByteArray(), starts)
        }

        /** Reads [bytes] written from [DomainSet.bytes]; null if they are not sorted names. */
        fun load(bytes: ByteArray): DomainSet? {
            if (bytes.isEmpty()) return EMPTY
            if (bytes.last() != '\n'.code.toByte()) return null
            var count = 0
            for (b in bytes) if (b == '\n'.code.toByte()) count++
            val starts = IntArray(count)
            var n = 0
            var start = 0
            for (i in bytes.indices) {
                if (bytes[i] == '\n'.code.toByte()) {
                    if (i == start) return null
                    starts[n++] = start
                    start = i + 1
                }
            }
            return DomainSet(bytes, starts)
        }
    }
}

/**
 * The profile's blocking rules: names on its lists, minus the lists' exceptions and the profile's
 * own allowlist. Thread-safe (read-only).
 */
class DnsBlocker(private val lists: List<DomainSet>, private val exceptions: DomainSet, allow: Collection<String>) {
    private val allow = DomainSet.of(allow)
    val size get() = lists.sumOf { it.size }

    fun blocks(host: String): Boolean {
        val h = Hostlist.normalize(host) ?: return false
        return lists.any { it.matches(h) } && !allow.matches(h) && !exceptions.matches(h)
    }
}

/** DNS answers for blocked names: an empty address, so apps fail at once instead of retrying. */
object DnsBlockAnswer {
    const val TYPE_A = 1
    const val TYPE_AAAA = 28

    /** The queried name (lower case, no trailing dot) and type, or null. */
    fun question(msg: ByteArray): Pair<String, Int>? {
        val end = DnsMessage.questionEnd(msg).takeIf { it > 0 } ?: return null
        val name = StringBuilder()
        var i = 12
        while (true) {
            val l = msg[i].toInt() and 0xFF
            if (l == 0) break
            if (name.isNotEmpty()) name.append('.')
            for (j in i + 1..i + l) name.append((msg[j].toInt() and 0xFF).toChar().lowercaseChar())
            i += l + 1
        }
        val type = ((msg[end - 4].toInt() and 0xFF) shl 8) or (msg[end - 3].toInt() and 0xFF)
        return name.toString() to type
    }

    /**
     * The answer to a blocked [query]: `0.0.0.0` for A, `::` for AAAA, no records for other
     * types (HTTPS, SVCB…), so the app falls back to an address it cannot reach.
     */
    fun answer(query: ByteArray): ByteArray? {
        val (_, type) = question(query) ?: return null
        val end = DnsMessage.questionEnd(query)
        val rdata = when (type) {
            TYPE_A -> ByteArray(4)
            TYPE_AAAA -> ByteArray(16)
            else -> null
        }
        val out = java.io.ByteArrayOutputStream()
        out.write(query, 0, end)
        val r = out.toByteArray()
        r[2] = (0x80 or (query[2].toInt() and 0x01)).toByte() // response, keep RD
        r[3] = 0x80.toByte() // RA, NOERROR
        r[4] = 0; r[5] = 1 // one question
        r[6] = 0; r[7] = if (rdata == null) 0 else 1
        for (k in 8 until 12) r[k] = 0
        if (rdata == null) return r
        val rr = java.io.ByteArrayOutputStream()
        rr.write(r)
        rr.write(byteArrayOf(0xC0.toByte(), 12)) // the name: pointer to the question
        rr.write(byteArrayOf(0, type.toByte(), 0, 1)) // type, class IN
        rr.write(byteArrayOf(0, 0, 0, 60)) // TTL 60 s: a list change applies soon
        rr.write(byteArrayOf(0, rdata.size.toByte()))
        rr.write(rdata)
        return rr.toByteArray()
    }
}
