package io.github.halilkhrmn.dpimech.core

import kotlinx.serialization.Serializable

/** Which apps enter the VPN (`addAllowedApplication` / `addDisallowedApplication`). */
@Serializable
enum class AppMode {
    /** Only the selected apps get the bypass. */
    ONLY_SELECTED,

    /** Every app except the selected ones gets the bypass. */
    ALL_EXCEPT,
}

@Serializable
data class Profile(
    val id: String,
    val name: String,
    /** [DomainPack] ids, e.g. `discord`. */
    val packs: List<String> = emptyList(),
    /** Extra domains typed by the user. */
    val extraDomains: List<String> = emptyList(),
    val strategy: StrategyEntry,
    val appMode: AppMode = AppMode.ONLY_SELECTED,
    /** Android package names for [appMode]. */
    val apps: List<String> = emptyList(),
    /** Apply the strategy only to the profile's domains; other connections pass untouched. */
    val domainFilter: Boolean = true,
) {
    /** Hostlist domains: the packs' domains plus the user's own, de-duplicated and sanitised. */
    val domains: List<String>
        get() = (packs.flatMap { DomainPack.byId(it)?.domains.orEmpty() } + extraDomains)
            .mapNotNull(Hostlist::normalize)
            .distinct()

    companion object {
        /** A new profile for [pack], with the pack's apps that are installed. */
        fun fromPack(id: String, pack: DomainPack, strategy: StrategyEntry, installed: Set<String>) = Profile(
            id = id,
            name = pack.name,
            packs = listOf(pack.id),
            strategy = strategy,
            apps = pack.packages.filter { it in installed },
        )
    }
}

object Hostlist {
    /**
     * Lower-cased hostname without a leading `*.`, or null when it is not a plain hostname
     * (ciadpi matches subdomains of every entry anyway).
     */
    fun normalize(domain: String): String? {
        val d = domain.trim().removePrefix("*.").trimEnd('.').lowercase()
        if (d.isEmpty() || d.length > 253 || d.startsWith('.') || d.startsWith('-')) return null
        if (d.any { !(it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-') }) return null
        if (".." in d) return null
        return d
    }

    /** File body for ciadpi `--hosts <file>`: one hostname per line. */
    fun body(domains: List<String>): String =
        domains.mapNotNull(::normalize).distinct().joinToString("") { "$it\n" }
}
