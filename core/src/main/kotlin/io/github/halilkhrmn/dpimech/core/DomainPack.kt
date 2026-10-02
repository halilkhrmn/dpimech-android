package io.github.halilkhrmn.dpimech.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * A ready-made site pack, from `strategies/packs.json` in the desktop repository (format 1,
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
) {
    @Serializable
    private data class Raw(val format: Int, val packs: List<DomainPack> = emptyList())

    companion object {
        const val URL = "https://raw.githubusercontent.com/halilkhrmn/dpimech/main/strategies/packs.json"

        /** The file format this build understands; a newer one is ignored until the app is updated. */
        const val FORMAT = 1

        private val json = Json { ignoreUnknownKeys = true }
        private val HOST = Regex("(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?")
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
            require(raw.packs.isNotEmpty()) { "the pack file has no packs" }
            val ids = mutableSetOf<String>()
            for (p in raw.packs) {
                require(p.id.isNotEmpty() && p.name.isNotBlank() && ids.add(p.id)) { "pack \"${p.id}\": missing or repeated id or name" }
                require(p.domains.isNotEmpty() && p.probes.isNotEmpty()) { "pack \"${p.id}\": no domains or no probes" }
                (p.domains + p.probes).firstOrNull { !HOST.matches(it) }
                    ?.let { throw IllegalArgumentException("pack \"${p.id}\": \"$it\" is not a host name") }
                p.packages.firstOrNull { !PACKAGE.matches(it) }
                    ?.let { throw IllegalArgumentException("pack \"${p.id}\": \"$it\" is not a package name") }
            }
            raw.packs
        }

        /** The copy built into this app (a resource of this module). */
        val embedded: List<DomainPack> by lazy {
            val text = DomainPack::class.java.getResourceAsStream("/strategies/packs.json")
                ?.use { it.readBytes().decodeToString() }
                ?: error("packs.json missing from resources")
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
