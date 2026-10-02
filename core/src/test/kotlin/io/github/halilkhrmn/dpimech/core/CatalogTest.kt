package io.github.halilkhrmn.dpimech.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogTest {
    @Test
    fun packsAreConsistent() {
        assertEquals(DomainPack.ALL.size, DomainPack.ALL.map { it.id }.toSet().size)
        for (p in DomainPack.ALL) {
            assertTrue(p.probes.isNotEmpty(), p.id)
            // Every pack but the news sites has an Android app.
            assertTrue(p.packages.isNotEmpty() || p.id.startsWith("news-"), p.id)
            for (d in p.domains + p.probes) assertEquals(d, Hostlist.normalize(d), "${p.id}: $d")
            // Every probe is covered by the hostlist (ciadpi matches subdomains).
            for (probe in p.probes) {
                assertTrue(p.domains.any { probe == it || probe.endsWith(".$it") }, "${p.id}: $probe")
            }
        }
    }

    @Test
    fun hostnamesAreSanitised() {
        assertEquals("discord.com", Hostlist.normalize("  *.Discord.COM. "))
        assertNull(Hostlist.normalize("evil.com\n-x"))
        assertNull(Hostlist.normalize("a..b"))
        assertNull(Hostlist.normalize(""))
        assertNull(Hostlist.normalize("-flag"))
        assertEquals("a.com\nb.com\n", Hostlist.body(listOf("A.com", "b.com", "a.com", "bad host")))
    }

    @Test
    fun packFileIsSharedWithDesktopAndChecked() {
        // The built-in copy is the desktop repository's file; Discord stays first.
        assertEquals("discord", DomainPack.embedded.first().id)
        assertEquals(listOf("com.discord"), DomainPack.byId("discord")!!.packages)
        val ok = """{"format":1,"packs":[{"id":"a","name":"A","domains":["a.com"],"probes":["a.com"]}]}"""
        assertEquals(emptyList(), DomainPack.parse(ok).getOrThrow().single().packages)
        val named = DomainPack.parse(ok.replace("}]}", ",\"names\":{\"tr\":\"Bir\"},\"countries\":[\"*\",\"TR\"]}]}")).getOrThrow().single()
        assertEquals("Bir" to "A", named.displayName("tr") to named.displayName("ru"))
        for (bad in listOf(
            ok.replace("\"format\":1", "\"format\":2"),
            ok.replace("[\"a.com\"],\"probes", "[\"a.com/x\"],\"probes"),
            ok.replace("[\"a.com\"],\"probes", "[\"A.com\"],\"probes"),
            ok.replace("\"probes\":[\"a.com\"]", "\"probes\":[]"),
            ok.replace("}]}", ",\"android_packages\":[\"no package\"]}]}"),
            ok.replace("}]}", "},{\"id\":\"a\",\"name\":\"B\",\"domains\":[\"b.com\"],\"probes\":[\"b.com\"]}]}"),
            ok.replace("}]}", ",\"countries\":[\"tr\"]}]}"),
            ok.replace("\"id\":\"a\"", "\"id\":\"A b\""),
            "not json",
        )) {
            assertTrue(DomainPack.parse(bad).isFailure, bad)
        }
    }

    @Test
    fun profileCollectsDomainsAndApps() {
        val s = StrategyEntry("x", "-s1")
        val p = Profile.fromPack("1", DomainPack.byId("youtube")!!, s, setOf("org.schabi.newpipe", "com.other"))
        assertEquals(listOf("org.schabi.newpipe"), p.apps)
        val q = p.copy(packs = listOf("youtube", "x"), extraDomains = listOf("YouTube.com", "example.org"))
        assertTrue("x.com" in q.domains && "example.org" in q.domains)
        assertEquals(q.domains.distinct(), q.domains)
    }

    @Test
    fun ispMatching() {
        assertEquals("Türk Telekom", Isp.match(9121, "")?.name)
        assertEquals("TurkNet", Isp.match(null, "TurkNet Iletisim")?.name)
        assertEquals("Vodafone Türkiye", Isp.match(1, "VODAFONE NET")?.name)
        assertNull(Isp.match(3320, "Deutsche Telekom AG"))
        assertTrue(Isp.match(9121, "")!!.isPresetFor("Türk Telekom — fake"))
    }

    @Test
    fun labScoring() {
        fun r(name: String, ok: Int, ms: Int, confirmed: Boolean = false, error: String? = null) =
            LabResult(LabStrategy(name, "-s1", LabResult.STANDARD_SET), ok, 10, ms, confirmed = confirmed, error = error)
        val baseline = LabResult(null, 10, 10, 1)
        val fast = r("fast", 9, 100)
        val slow = r("slow", 9, 300)
        val confirmed = r("confirmed", 8, 500, confirmed = true)
        val broken = r("broken", 10, 10, error = "exit 1")
        assertTrue(fast > slow && confirmed > fast)
        assertEquals("confirmed", LabResult.best(listOf(baseline, slow, fast, confirmed, broken))?.strategy?.name)
        assertNull(LabResult.best(listOf(baseline, r("none", 0, 0))))
        assertTrue(fast.sameStrategy(fast.copy(ok = 1)) && !fast.sameStrategy(slow) && !baseline.sameStrategy(baseline))
    }
}
