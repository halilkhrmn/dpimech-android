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
    /** Strategies that worked on other providers, keyed by [IspInfo.networkKey]. */
    val perNetwork: Map<String, StrategyEntry> = emptyMap(),
) {
    /** Hosts to test strategies against: the packs' probes, or the user's own domains. */
    val probes: List<String>
        get() = (packs.flatMap { DomainPack.byId(it)?.probes.orEmpty() } + extraDomains.mapNotNull(Hostlist::normalize))
            .distinct()

    /**
     * The app a "turn on and open" shortcut starts: the profile's first chosen app, or for a
     * whole-phone profile the first app of its site packs, among those that can be launched.
     */
    fun appToOpen(launchable: Set<String>): String? {
        val candidates = if (appMode == AppMode.ONLY_SELECTED) apps else packs.flatMap { DomainPack.byId(it)?.packages.orEmpty() }
        return candidates.firstOrNull { it in launchable }
    }

    /** The strategy remembered for this provider, or the profile's default one. */
    fun strategyFor(networkKey: String?): StrategyEntry = networkKey?.let(perNetwork::get) ?: strategy

    /** Uses [entry] from now on, and remembers it for the provider it was tested on. */
    fun withStrategy(entry: StrategyEntry, networkKey: String?): Profile = copy(
        strategy = entry,
        perNetwork = if (networkKey == null) perNetwork else perNetwork + (networkKey to entry),
    )

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

    /**
     * Domains typed or pasted by the user: separated by spaces, commas or new lines; a pasted
     * address like `https://www.example.com:443/path` becomes `www.example.com`. Invalid
     * entries are dropped.
     */
    fun parseInput(text: String): List<String> =
        text.split(*SEPARATORS).mapNotNull(::fromTyped).distinct()

    /**
     * What a domain field does with text as it is typed: entries that are finished and followed
     * by a separator become pills, the rest stays in the field. Some keyboards put a space after
     * every "." (Samsung's address keyboard), so "example. " is not finished: it stays in the
     * field as "example." and the user goes on typing.
     */
    fun splitTyped(text: String): Pair<List<String>, String> {
        if (text.none { it in SEPARATOR_CHARS }) return emptyList<String>() to text
        val parts = text.split(*SEPARATORS)
        val done = mutableListOf<String>()
        val pending = StringBuilder()
        for (part in parts.dropLast(1)) {
            val host = fromTyped(part)
            when {
                part.trimEnd().endsWith('.') -> pending.append(part.trim())
                host != null -> done += host
                part.isNotBlank() -> pending.append(part.trim()).append(' ')
            }
        }
        return done.distinct() to pending.append(parts.last()).toString()
    }

    /** A typed or pasted entry (a domain or an address like `https://host:443/path`) as a hostname. */
    private fun fromTyped(entry: String): String? =
        normalize(entry.trim().substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':'))
            // A typed name needs a dot: "example" alone is a typo, not a site.
            ?.takeIf { '.' in it }

    private const val SEPARATOR_CHARS = "\n, \t;"
    private val SEPARATORS = SEPARATOR_CHARS.map { it.toString() }.toTypedArray()

    /** File body for ciadpi `--hosts <file>`: one hostname per line. */
    fun body(domains: List<String>): String =
        domains.mapNotNull(::normalize).distinct().joinToString("") { "$it\n" }
}
