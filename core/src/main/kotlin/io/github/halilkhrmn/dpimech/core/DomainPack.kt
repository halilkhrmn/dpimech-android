package io.github.halilkhrmn.dpimech.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * A ready-made site pack, from `strategies/domains.json` in the desktop repository (format 1,
 * shared with the desktop app). The APK ships a copy ([embedded]) and fetches the newest one
 * from [URL]; [ALL] is the list in use.
 */
@Serializable
data class DomainPack(
    val id: String,
    val name: String,
    /** Everything the engine should act on (goes into the hostlist). */
    val domains: List<String>,
    /** Hosts the Strategy Lab requests; each answers HTTPS on `/`. */
    val probes: List<String>,
    /** Android apps that use these domains: choosing the pack ticks them when installed. */
    @SerialName("android_packages") val packages: List<String> = emptyList(),
    /** The name in other languages, by language code; [name] is English. */
    val names: Map<String, String> = emptyMap(),
    /** ISO codes of the countries where the site is widely reported blocked ("*": everywhere). */
    val countries: List<String> = emptyList(),
) {
    /** The name in [language] (an ISO 639 code) when the file has one, else [name]. */
    fun displayName(language: String): String = names[language] ?: name

    @Serializable
    private data class Raw(val format: Int, val packs: List<DomainPack> = emptyList())

    companion object {
        const val URL = "https://raw.githubusercontent.com/halilkhrmn/dpimech/main/strategies/domains.json"

        /** The file format this build understands; a newer one is ignored until the app is updated. */
        const val FORMAT = 1

        private val json = Json { ignoreUnknownKeys = true }
        private val HOST = Regex("(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?")
        private val ID = Regex("[a-z0-9-]{1,40}")
        private val COUNTRY = Regex("\\*|[A-Z]{2}")
        private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

        /**
         * Parses and checks a pack file, like the desktop app does: unique ids, plain host names
         * (they go into ciadpi's host list), at least one probe each, valid package names.
         */
        fun parse(text: String): Result<List<DomainPack>> = runCatching {
            val raw = try {
                json.decodeFromString(Raw.serializer(), text)
            } catch (e: SerializationException) {
                throw IllegalArgumentException("not a pack file: ${e.message}", e)
            }
            require(raw.format == FORMAT) { "unsupported pack file format ${raw.format}" }
            require(raw.packs.size in 1..100) { "${raw.packs.size} packs" }
            val ids = mutableSetOf<String>()
            for (p in raw.packs) {
                require(ID.matches(p.id) && ids.add(p.id)) { "bad or repeated pack id \"${p.id}\"" }
                require((listOf(p.name) + p.names.values).all { it.isNotBlank() && it.length <= 120 }) { "${p.id}: bad name" }
                require(p.domains.isNotEmpty() && p.probes.isNotEmpty()) { "pack \"${p.id}\": no domains or no probes" }
                require(p.domains.size <= 200 && p.probes.size <= 20 && p.packages.size <= 20) { "${p.id}: too many entries" }
                p.countries.firstOrNull { !COUNTRY.matches(it) }
                    ?.let { throw IllegalArgumentException("${p.id}: \"$it\" is not a country code") }
                (p.domains + p.probes).firstOrNull { !HOST.matches(it) }
                    ?.let { throw IllegalArgumentException("pack \"${p.id}\": \"$it\" is not a host name") }
                p.packages.firstOrNull { !PACKAGE.matches(it) }
                    ?.let { throw IllegalArgumentException("pack \"${p.id}\": \"$it\" is not a package name") }
            }
            raw.packs
        }

        /** The copy built into this app (a resource of this module). */
        val embedded: List<DomainPack> by lazy {
            val text = DomainPack::class.java.getResourceAsStream("/strategies/domains.json")
                ?.use { it.readBytes().decodeToString() }
                ?: error("domains.json missing from resources")
            parse(text).getOrThrow()
        }

        /** The packs in use: the newest downloaded list, or the built-in one. */
        @Volatile
        var ALL: List<DomainPack> = embedded
            private set

        /** Switches to a downloaded list (already checked by [parse]). */
        fun use(packs: List<DomainPack>) {
            ALL = packs
        }

        fun byId(id: String): DomainPack? = ALL.find { it.id == id }
    }
}
