package io.github.halilkhrmn.dpimech.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class StrategyEntry(val name: String, val args: String)

/**
 * The shared strategy file (`strategies/default.json` in the desktop repository, format 1).
 * The APK ships a copy ([embedded]) and fetches the newest one from [URL] at runtime.
 */
class StrategyFile private constructor(private val engines: Map<String, List<StrategyEntry>>) {

    /** ByeDPI strategies, the only engine this app runs. */
    val byeDpi: List<StrategyEntry> get() = engines[BYE_DPI].orEmpty()

    fun forEngine(key: String): List<StrategyEntry> = engines[key].orEmpty()

    @Serializable
    private data class Raw(val format: Int, val engines: Map<String, List<StrategyEntry>> = emptyMap())

    companion object {
        const val URL = "https://raw.githubusercontent.com/halilkhrmn/dpimech/main/strategies/default.json"
        const val BYE_DPI = "bye_dpi"

        /** The file format this build understands; a newer one is ignored until the app is updated. */
        const val FORMAT = 1

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Parses and sanity-checks a strategy file. Arguments are not trusted here: the argument
         * policy checks them again at every launch.
         */
        fun parse(text: String): Result<StrategyFile> = runCatching {
            val raw = try {
                json.decodeFromString(Raw.serializer(), text)
            } catch (e: SerializationException) {
                throw IllegalArgumentException("not a strategy file: ${e.message}", e)
            }
            require(raw.format == FORMAT) { "unsupported strategy file format ${raw.format}" }
            var count = 0
            for ((engine, list) in raw.engines) {
                for (s in list) {
                    require(s.name.isNotBlank() && s.args.isNotBlank()) {
                        "$engine: a strategy without name or arguments"
                    }
                    require(s.name.length <= 200 && s.args.length <= 4000) {
                        "$engine: strategy \"${s.name}\" is too long"
                    }
                }
                count += list.size
            }
            require(count > 0) { "the strategy file has no strategies" }
            // A file without ByeDPI strategies is useless here even if it is valid for desktop.
            require(!raw.engines[BYE_DPI].isNullOrEmpty()) { "the strategy file has no ByeDPI strategies" }
            StrategyFile(raw.engines)
        }

        /** The copy built into this app (a resource of this module). */
        val embedded: StrategyFile by lazy {
            val text = StrategyFile::class.java.getResourceAsStream("/strategies/default.json")
                ?.use { it.readBytes().decodeToString() }
                ?: error("default.json missing from resources")
            parse(text).getOrThrow()
        }
    }
}
