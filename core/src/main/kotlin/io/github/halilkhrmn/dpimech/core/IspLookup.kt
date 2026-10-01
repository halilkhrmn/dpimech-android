package io.github.halilkhrmn.dpimech.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class IspInfo(
    val provider: String,
    val asn: Int?,
    val country: String,
    /** The entry from the built-in ISP table, when recognised. */
    val known: Isp?,
) {
    /**
     * What the per-network memory is keyed by: the provider's AS number. DPI depends on the
     * provider, not on the Wi-Fi name, and reading Wi-Fi names would need the location permission.
     */
    val networkKey: String get() = asn?.let { "AS$it" } ?: "name:${provider.lowercase()}"
}

/** ISP detection through ipwho.is, the same service the desktop app asks. */
object IspLookup {
    const val URL = "https://ipwho.is/?fields=success,country_code,connection"

    @Serializable
    private data class Reply(
        val success: Boolean = false,
        val country_code: String = "",
        val connection: Connection? = null,
    )

    @Serializable
    private data class Connection(val asn: Int? = null, val isp: String = "", val org: String = "")

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): Result<IspInfo> = runCatching {
        val r = json.decodeFromString(Reply.serializer(), text)
        require(r.success) { "ISP lookup failed" }
        val c = requireNotNull(r.connection) { "ISP lookup returned no provider" }
        val provider = c.isp.ifEmpty { c.org }
        IspInfo(provider, c.asn, r.country_code, Isp.match(c.asn, provider))
    }

    /** Lab strategies with the ISP's presets marked and moved to the front. */
    fun markRecommended(strategies: List<LabStrategy>, isp: Isp?): List<LabStrategy> = strategies
        .map { it.copy(recommended = isp?.isPresetFor(it.name) == true) }
        .sortedBy { !it.recommended }
}
