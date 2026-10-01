package io.github.halilkhrmn.dpimech.core

/** How the phone is connected right now (DPIMech's own default network, outside its VPN). */
enum class Transport { WIFI, CELLULAR, ETHERNET, OTHER }

/**
 * The current network: the transport, what Android knows about a mobile operator (MCC+MNC and
 * name, no permission needed) and, once looked up, the provider from ipwho.is. Wi-Fi names are
 * not read: that needs the location permission, and the provider is what decides the DPI.
 */
data class NetworkInfo(
    val transport: Transport,
    val mccMnc: String? = null,
    val operatorName: String? = null,
    val isp: IspInfo? = null,
) {
    /** The known provider: from the lookup, else from the mobile operator code or name. */
    val known: Isp? get() = isp?.known ?: if (transport == Transport.CELLULAR) Isp.matchMobile(mccMnc, operatorName) else null

    /**
     * Per-network memory key: the AS number once looked up; on mobile data the operator code
     * works before (or without) the lookup.
     */
    val networkKey: String?
        get() = isp?.networkKey ?: mccMnc?.takeIf { transport == Transport.CELLULAR }?.let { "mobile:$it" }

    /** Country code (ISO), from the lookup or the mobile network. */
    val country: String?
        get() = isp?.country?.ifEmpty { null } ?: mccMnc?.take(3)?.let(MCC_COUNTRY::get)

    /** "Türk Telekom", or the raw provider / operator name. */
    val providerName: String?
        get() = known?.name ?: isp?.provider?.ifEmpty { null } ?: operatorName?.ifEmpty { null }

    companion object {
        /** Mobile country codes of the countries with presets (and a few neighbours). */
        val MCC_COUNTRY = mapOf("286" to "TR", "250" to "RU", "255" to "UA", "257" to "BY", "401" to "KZ", "432" to "IR")
    }
}
