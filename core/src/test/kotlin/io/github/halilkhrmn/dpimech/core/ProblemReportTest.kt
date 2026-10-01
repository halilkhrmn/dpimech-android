package io.github.halilkhrmn.dpimech.core

import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertTrue

class ProblemReportTest {
    private val strategy = StrategyEntry("TLS record split", "-r 1+s")
    private val profiles = SavedProfiles().upsert(
        CountryPreset.forCountry("TR").profile("1", "Blocked sites", strategy).withStrategy(strategy, "AS9121"),
    )

    private fun report(lines: Int) = ProblemReport.build(
        "0.1.0", "Pixel 8, Android 16 (API 36)", AppSettings(), profiles, "Running",
        IspInfo("Turk Telekom", 9121, "TR", Isp.match(9121, "")), List(lines) { "08:00:0$it line $it ".padEnd(80, 'x') },
    )

    @Test
    fun summaryNamesTheSetup() {
        val r = report(3)
        listOf("DPIMech for Android 0.1.0", "Türk Telekom (AS9121, TR)", "ALL_EXCEPT", "discord", "-r 1+s", "AS9121: TLS record split")
            .forEach { assertTrue(it in r.summary, it) }
        assertTrue(r.fullText.endsWith(r.log.last()))
    }

    @Test
    fun issueLinkStaysShortAndKeepsTheNewestLines() {
        val r = report(500)
        val url = r.issueUrl("Problem")
        assertTrue(url.length <= ProblemReport.MAX_ISSUE_URL, "${url.length}")
        val body = URLDecoder.decode(url.substringAfter("&body="), "UTF-8")
        assertTrue(r.log.last() in body, "newest line kept")
        assertTrue(r.log.first() !in body, "oldest dropped")
        assertTrue(url.startsWith("https://github.com/halilkhrmn/dpimech-android/issues/new?title=Problem&body="))
    }
}
