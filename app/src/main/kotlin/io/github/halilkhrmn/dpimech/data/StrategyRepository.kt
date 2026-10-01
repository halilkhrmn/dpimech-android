package io.github.halilkhrmn.dpimech.data

import io.github.halilkhrmn.dpimech.core.LabResult
import io.github.halilkhrmn.dpimech.core.OnlineSource
import io.github.halilkhrmn.dpimech.core.StrategyEntry
import io.github.halilkhrmn.dpimech.core.StrategyFile
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** A strategy and where it came from (shown next to it, community lists are credited). */
data class StrategyOption(val entry: StrategyEntry, val source: String, val origin: String = "")

/**
 * The standard set from the desktop repository's `default.json` (newest copy cached, the
 * built-in one as fallback) plus the community list. Downloads are plain data, never code.
 */
class StrategyRepository(dir: File) {
    private val standardCache = File(dir, "strategies.json")
    private val communityCache = File(dir, "community.txt")
    private val state = MutableStateFlow(load())
    val options: StateFlow<List<StrategyOption>> = state.asStateFlow()
    private val updated = MutableStateFlow(lastDownload())

    /** When the lists were last downloaded (epoch ms), or null if only the built-in copy is used. */
    val lastUpdated: StateFlow<Long?> = updated.asStateFlow()

    private fun lastDownload(): Long? =
        listOf(standardCache, communityCache).filter { it.exists() }.maxOfOrNull { it.lastModified() }

    private fun load(): List<StrategyOption> {
        val standard = standardCache.takeIf { it.exists() }
            ?.let { StrategyFile.parse(it.readText()).getOrNull() }
            ?: StrategyFile.embedded
        val community = communityCache.takeIf { it.exists() }
            ?.let { OnlineSource.parseArgsPerLine(it.readText()) }.orEmpty()
        val src = OnlineSource.COMMUNITY
        return standard.byeDpi.map { StrategyOption(it, LabResult.STANDARD_SET) } +
            community.map { StrategyOption(it, src.label, src.origin) }
    }

    /** Fetches both lists; keeps the old copies when a download fails. Returns failures. */
    suspend fun refresh(): List<String> = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        runCatching {
            val text = download(StrategyFile.URL)
            StrategyFile.parse(text).getOrThrow()
            standardCache.writeText(text)
        }.onFailure { errors += "${LabResult.STANDARD_SET}: ${it.message}" }
        runCatching {
            val text = download(OnlineSource.COMMUNITY.url)
            require(OnlineSource.parseArgsPerLine(text).isNotEmpty()) { "empty list" }
            communityCache.writeText(text)
        }.onFailure { errors += "${OnlineSource.COMMUNITY.label}: ${it.message}" }
        state.value = load()
        updated.value = lastDownload()
        errors
    }

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            val bytes = conn.inputStream.use { it.readNBytesCompat(MAX_SIZE + 1) }
            if (bytes.size > MAX_SIZE) throw IOException("file too large")
            return bytes.decodeToString()
        } finally {
            conn.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        const val MAX_SIZE = 512 * 1024
    }
}
