package io.github.halilkhrmn.dpimech.core

/**
 * Sites commonly blocked with DPI in a country, for the "whole phone" setup: every app goes
 * through the VPN, but only these sites get the bypass (domain filter), so nothing else changes.
 * Lists are deliberately short and only name blocks that were widely reported.
 */
data class CountryPreset(val country: String, val packs: List<String>) {
    val domainPacks: List<DomainPack> get() = packs.mapNotNull(DomainPack::byId)

    /** A profile for every app, limited to the preset's sites. */
    fun profile(id: String, name: String, strategy: StrategyEntry): Profile = Profile(
        id = id,
        name = name,
        packs = packs,
        strategy = strategy,
        appMode = AppMode.ALL_EXCEPT,
        apps = emptyList(),
        domainFilter = true,
    )

    companion object {
        val ALL: List<CountryPreset> = listOf(
            CountryPreset("TR", listOf("discord", "roblox", "wattpad", "imgur")),
            CountryPreset("RU", listOf("youtube", "discord", "instagram", "x", "facebook", "linkedin", "signal", "viber")),
        )

        /** Used when the country has no preset of its own. */
        val GENERIC = CountryPreset("", listOf("discord", "youtube"))

        fun forCountry(code: String?): CountryPreset =
            ALL.find { it.country.equals(code, ignoreCase = true) } ?: GENERIC
    }
}
