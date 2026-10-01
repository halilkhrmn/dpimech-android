package io.github.halilkhrmn.dpimech.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetworkInfoTest {
    @Test
    fun mobileDataIsRecognisedBeforeTheLookup() {
        val n = NetworkInfo(Transport.CELLULAR, mccMnc = "28601", operatorName = "Turkcell")
        assertEquals("Turkcell (mobile)", n.known?.name)
        assertEquals("mobile:28601", n.networkKey)
        assertEquals("TR", n.country)
        assertEquals("Vodafone Türkiye", NetworkInfo(Transport.CELLULAR, "28602", "Vodafone TR").known?.name)
        assertEquals("Türk Telekom", NetworkInfo(Transport.CELLULAR, null, "TÜRK TELEKOM").known?.name)
        assertEquals("Türk Telekom", Isp.match(20978, "")?.name, "mobile AS of Türk Telekom")
    }

    @Test
    fun lookupWinsWhenItArrives() {
        val isp = IspInfo("TurkNet Iletisim", 12735, "TR", Isp.match(12735, ""))
        val wifi = NetworkInfo(Transport.WIFI, isp = isp)
        assertEquals("TurkNet", wifi.providerName)
        assertEquals("AS12735", wifi.networkKey)
        val mobile = NetworkInfo(Transport.CELLULAR, "28601", "Turkcell", IspInfo("Turkcell", 16135, "TR", Isp.match(16135, "")))
        assertEquals("AS16135", mobile.networkKey)
    }

    @Test
    fun unknownWifiHasNoKeyUntilLookedUp() {
        val n = NetworkInfo(Transport.WIFI)
        assertNull(n.networkKey)
        assertNull(n.known)
        assertNull(n.country)
        assertEquals("Some ISP", NetworkInfo(Transport.WIFI, isp = IspInfo("Some ISP", 1, "DE", null)).providerName)
    }
}
