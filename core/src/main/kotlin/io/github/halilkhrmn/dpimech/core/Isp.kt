package io.github.halilkhrmn.dpimech.core

/**
 * Well-known ISPs, matched on the ASN or the provider name reported by the lookup service.
 * Ported from the desktop app (`catalog.rs`). [presetKeys] are matched case-insensitively
 * against preset names such as "Türk Telekom".
 */
data class Isp(
    val name: String,
    val asns: List<Int>,
    val nameHints: List<String>,
    val presetKeys: List<String>,
    /** Mobile network codes (MCC + MNC) of the operator; Android only, no permission needed. */
    val mobileCodes: List<String> = emptyList(),
) {
    /** Whether a strategy named [strategyName] was made for this ISP. */
    fun isPresetFor(strategyName: String): Boolean {
        val n = strategyName.lowercase()
        return presetKeys.any { it in n }
    }

    companion object {
        val ALL: List<Isp> = listOf(
            // 20978: Türk Telekom's mobile network (formerly Avea).
            Isp("Türk Telekom", listOf(9121, 20978), listOf("turk telekom", "türk telekom", "ttnet", "avea"), listOf("türk telekom", "turk telekom"), listOf("28603")),
            Isp("Superonline (Turkcell)", listOf(34984), listOf("superonline"), listOf("superonline")),
            Isp("Turkcell (mobile)", listOf(16135), listOf("turkcell"), listOf("turkcell"), listOf("28601")),
            Isp("Vodafone Türkiye", listOf(15897), listOf("vodafone"), listOf("vodafone"), listOf("28602")),
            Isp("TurkNet", listOf(12735), listOf("turknet", "turk net"), listOf("turknet")),
            Isp("Kablonet (Türksat)", listOf(47524), listOf("turksat", "türksat", "kablonet"), listOf("kablonet")),
        )

        /** The mobile operator from Android's MCC+MNC (e.g. "28601") or its display name. */
        fun matchMobile(mccMnc: String?, operatorName: String?): Isp? =
            ALL.find { mccMnc != null && mccMnc in it.mobileCodes } ?: operatorName?.let { match(null, it) }

        fun match(asn: Int?, provider: String): Isp? {
            val p = provider.lowercase()
            return ALL.find { isp ->
                (asn != null && asn in isp.asns) || isp.nameHints.any { it in p }
            }
        }
    }
}
