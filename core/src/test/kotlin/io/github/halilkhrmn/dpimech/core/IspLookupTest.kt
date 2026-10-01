package io.github.halilkhrmn.dpimech.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IspLookupTest {
    @Test
    fun parsesIpWhoIs() {
        val info = IspLookup.parse(
            """{"success":true,"country_code":"TR","connection":{"asn":9121,"org":"TTNet","isp":"Turk Telekom","domain":"x"}}""",
        ).getOrThrow()
        assertEquals("Turk Telekom", info.provider)
        assertEquals("TR", info.country)
        assertEquals("Türk Telekom", info.known?.name)
        val byOrg = IspLookup.parse("""{"success":true,"country_code":"DE","connection":{"asn":3320,"isp":"","org":"DTAG"}}""").getOrThrow()
        assertEquals("DTAG", byOrg.provider)
        assertNull(byOrg.known)
        assertTrue(IspLookup.parse("""{"success":false}""").isFailure)
        assertTrue(IspLookup.parse("<html>").isFailure)
    }

    @Test
    fun recommendedPresetsComeFirst() {
        val std = LabResult.STANDARD_SET
        val list = listOf(LabStrategy("a", "-s1", std), LabStrategy("Superonline fake", "-f1", std))
        val marked = IspLookup.markRecommended(list, Isp.match(34984, ""))
        assertEquals(listOf("Superonline fake", "a"), marked.map { it.name })
        assertEquals(listOf(true, false), marked.map { it.recommended })
        assertTrue(IspLookup.markRecommended(list, null).none { it.recommended })
    }
}
