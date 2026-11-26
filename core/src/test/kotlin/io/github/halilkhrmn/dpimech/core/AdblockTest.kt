package io.github.halilkhrmn.dpimech.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdblockTest {
    @Test
    fun parsesHostsDomainsAndAdblockRules() {
        val text = """
            # hosts file
            127.0.0.1 localhost
            0.0.0.0 ads.example.com tracker.example.net # inline comment
            ::1 ip6-localhost
            ! adblock list
            [Adblock Plus 2.0]
            ||doubleclick.net^
            ||metrics.example.org^${'$'}important
            ||client.example^${'$'}client=1.2.3.4
            ||wild*.example.com^
            /^139\.45\.197\.2/
            @@||good.doubleclick.net^
            *.telemetry.example.io
            plain.example.co.uk
            not a domain
            1.2.3.4
        """.trimIndent()
        val parsed = BlockListParser.parse(text)
        assertEquals(
            listOf("ads.example.com", "tracker.example.net", "doubleclick.net", "metrics.example.org", "telemetry.example.io", "plain.example.co.uk"),
            parsed.block,
        )
        assertEquals(listOf("good.doubleclick.net"), parsed.allow)
    }

    @Test
    fun domainSetMatchesNamesAndSubdomains() {
        val set = DomainSet.of(listOf("doubleclick.net", "ads.example.com", "a.b", "zz.top"))
        assertEquals(4, set.size)
        assertTrue(set.matches("doubleclick.net"))
        assertTrue(set.matches("stats.g.doubleclick.net"))
        assertTrue(set.matches("ads.example.com"))
        assertFalse(set.matches("example.com"))
        assertFalse(set.matches("notdoubleclick.net"))
        assertFalse(set.matches("doubleclick.net.evil"))
        assertTrue(set.contains("a.b"))
        assertFalse(set.contains("a"))
        assertFalse(set.contains("a.bc"))
    }

    @Test
    fun domainSetSurvivesItsFileFormat() {
        val names = (1..5000).map { "host$it.example${it % 7}.com" }
        val set = DomainSet.of(names)
        val again = assertNotNull(DomainSet.load(set.bytes))
        assertEquals(5000, again.size)
        names.forEach { assertTrue(again.contains(it), it) }
        assertFalse(again.contains("host5001.example0.com"))
        assertNull(DomainSet.load("b.com\na.com".toByteArray()), "no trailing newline")
        assertEquals(0, DomainSet.load(ByteArray(0))!!.size)
    }

    @Test
    fun blockerHonoursExceptionsAndTheProfileAllowlist() {
        val lists = listOf(DomainSet.of(listOf("doubleclick.net", "tracker.io")), DomainSet.of(listOf("ads.site.com")))
        val blocker = DnsBlocker(lists, DomainSet.of(listOf("good.doubleclick.net")), listOf("tracker.io"))
        assertTrue(blocker.blocks("ad.doubleclick.net"))
        assertTrue(blocker.blocks("ADS.Site.com."))
        assertFalse(blocker.blocks("good.doubleclick.net"), "list exception")
        assertFalse(blocker.blocks("x.good.doubleclick.net"))
        assertFalse(blocker.blocks("cdn.tracker.io"), "profile allowlist")
        assertFalse(blocker.blocks("example.com"))
        assertEquals(3, blocker.size)
    }

    private fun query(name: String, type: Int, id: Int = 0x1234): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf((id shr 8).toByte(), id.toByte(), 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 1))
        name.split('.').forEach { l -> out.write(l.length); out.write(l.toByteArray()) }
        out.write(0)
        out.write(byteArrayOf(0, type.toByte(), 0, 1))
        // An EDNS OPT record, as Android sends it: dropped from the answer.
        out.write(byteArrayOf(0, 0, 41, 0x10, 0, 0, 0, 0, 0, 0, 0))
        return out.toByteArray()
    }

    @Test
    fun blockedAnswersAreEmptyAddresses() {
        val q = query("Ads.Example.com", DnsBlockAnswer.TYPE_A)
        assertEquals("ads.example.com" to 1, DnsBlockAnswer.question(q))
        val a = assertNotNull(DnsBlockAnswer.answer(q))
        assertEquals(0x1234, DnsMessage.id(a))
        assertEquals(0x81, a[2].toInt() and 0xFF, "response, recursion desired kept")
        assertEquals(0x80, a[3].toInt() and 0xFF, "NOERROR")
        assertEquals(1, a[7].toInt(), "one answer")
        assertEquals(0, a[11].toInt(), "no additional records")
        assertContentEquals(byteArrayOf(0, 4, 0, 0, 0, 0), a.copyOfRange(a.size - 6, a.size))

        val aaaa = assertNotNull(DnsBlockAnswer.answer(query("ads.example.com", DnsBlockAnswer.TYPE_AAAA)))
        assertContentEquals(ByteArray(16), aaaa.copyOfRange(aaaa.size - 16, aaaa.size))

        val https = assertNotNull(DnsBlockAnswer.answer(query("ads.example.com", 65)))
        assertEquals(0, https[7].toInt(), "no records for other types")
        assertEquals(DnsMessage.questionEnd(https), https.size)
    }

    @Test
    fun customListsAndLookup() {
        val catalog = listOf(BlockList("a", "A", "https://example.org/a.txt"))
        assertEquals("A", BlockList.byId("a", catalog)?.name)
        assertNull(BlockList.byId("b", catalog))
        val custom = assertNotNull(BlockList.custom(" https://lists.example.org/ads.txt "))
        assertEquals("lists.example.org", custom.name)
        assertTrue(custom.custom)
        assertEquals(custom, BlockList.byId(custom.id, catalog))
        assertNull(BlockList.custom("ftp://example.org/list"))
        assertNull(BlockList.custom("https://exa mple.org/list"))
        assertNull(BlockList.custom("example.org/list"))
    }

    @Test
    fun catalogParsingSkipsUnusableEntries() {
        val text = """{"format":1,"lists":[
            {"id":"ok","name":"OK","url":"https://raw.githubusercontent.com/x/y/main/l.txt","notes":{"en":"E","tr":"T"}},
            {"id":"ok","name":"Again","url":"https://raw.githubusercontent.com/x/y/main/m.txt"},
            {"id":"plain","name":"Plain","url":"http://example.org/l.txt"},
            {"id":"url:https://x.org/l","name":"Custom","url":"https://x.org/l"}
        ],"other":1}"""
        val lists = BlockListCatalog.parse(text)
        assertEquals(listOf("ok"), lists.map { it.id })
        assertEquals("T", lists[0].note("tr"))
        assertEquals("E", lists[0].note("ru"), "English when a language is missing")
        assertTrue(runCatching { BlockListCatalog.parse("""{"format":2,"lists":[]}""") }.isFailure)
    }

    @Test
    fun oldProfilesReadWithBlockingOffAndTheBypassOn() {
        val json = """{"id":"p","name":"Discord","strategy":{"name":"s","args":"-s1"}}"""
        val p = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(Profile.serializer(), json)
        assertTrue(p.bypass)
        assertFalse(p.adblock)
        assertEquals(listOf(BlockList.DEFAULT), p.blockLists)
        assertFalse(p.idle)
        assertTrue(p.copy(bypass = false).idle)
    }
}
