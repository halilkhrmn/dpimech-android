package io.github.halilkhrmn.dpimech.engine

import io.github.halilkhrmn.dpimech.core.BlockList
import io.github.halilkhrmn.dpimech.core.BlockListParser
import io.github.halilkhrmn.dpimech.core.DnsBlocker
import io.github.halilkhrmn.dpimech.core.DomainSet
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The downloaded block lists (DECISIONS #33), shared by every profile: each list is fetched once,
 * parsed and kept compiled in `files/blocklists/` ([DomainSet] bytes), so a start only reads it.
 * DPIMech is outside its own VPN, so downloads go straight out.
 */
object AdblockStore {
    /** A downloaded list: how many names it blocks and when it was fetched (epoch ms). */
    data class Status(val names: Int, val updated: Long)

    private var dir: File? = null
    private val state = MutableStateFlow<Map<String, Status>>(emptyMap())
    val status: StateFlow<Map<String, Status>> = state.asStateFlow()

    /** Lists older than this are fetched again when the app starts (with the automatic update). */
    const val MAX_AGE_MS = 7L * 24 * 3600 * 1000
    private const val MAX_BYTES = 40 shl 20
    private const val TIMEOUT_MS = 30_000

    @Synchronized
    fun init(filesDir: File) {
        if (dir != null) return
        val d = File(filesDir, "blocklists").apply { mkdirs() }
        dir = d
        state.value = d.listFiles { f -> f.name.endsWith(".meta") }.orEmpty().mapNotNull { meta ->
            val parts = runCatching { meta.readText().trim().split(' ', limit = 3) }.getOrNull() ?: return@mapNotNull null
            if (parts.size < 3) return@mapNotNull null
            parts[2] to Status(parts[0].toIntOrNull() ?: 0, parts[1].toLongOrNull() ?: 0)
        }.toMap()
    }

    private fun base(id: String): File {
        val d = dir ?: error("AdblockStore.init was not called")
        val hash = MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
        return File(d, hash)
    }

    /** Downloads [list] and keeps it compiled. Safe to call from any background thread. */
    fun download(list: BlockList): Result<Status> = runCatching {
        val text = fetch(list.url)
        val parsed = BlockListParser.parse(text)
        check(parsed.block.isNotEmpty()) { "no domains in ${list.name}" }
        val names = DomainSet.of(parsed.block)
        val allow = DomainSet.of(parsed.allow)
        val b = base(list.id)
        write(File(b.path + ".names"), names.bytes)
        write(File(b.path + ".allow"), allow.bytes)
        val s = Status(names.size, System.currentTimeMillis())
        write(File(b.path + ".meta"), "${s.names} ${s.updated} ${list.id}".toByteArray())
        synchronized(this) { state.value = state.value + (list.id to s) }
        EngineLog.add("block list ${list.name}: ${s.names} names")
        s
    }.onFailure { EngineLog.add("block list ${list.name}: download failed: $it") }

    /** Forgets a custom list the user removed. */
    fun remove(id: String) {
        val b = base(id)
        listOf(".names", ".allow", ".meta").forEach { File(b.path + it).delete() }
        synchronized(this) { state.value = state.value - id }
    }

    /**
     * The blocker for a profile: its lists (downloaded first when missing) and its allowlist.
     * Lists that cannot be had are left out and logged.
     */
    fun blocker(ids: List<String>, allow: List<String>): DnsBlocker {
        val lists = ArrayList<DomainSet>()
        val exceptions = ArrayList<String>()
        for (id in ids.distinct()) {
            val list = BlockList.byId(id) ?: continue
            if (id !in state.value) download(list)
            val b = base(id)
            val names = runCatching { DomainSet.load(File(b.path + ".names").readBytes()) }.getOrNull()
            if (names == null) {
                EngineLog.add("block list ${list.name}: not available, left out")
                continue
            }
            lists += names
            runCatching { DomainSet.load(File(b.path + ".allow").readBytes()) }.getOrNull()?.let { set ->
                if (set.size > 0) exceptions += set.bytes.toString(Charsets.US_ASCII).lines().filter { it.isNotEmpty() }
            }
        }
        return DnsBlocker(lists, DomainSet.of(exceptions), allow)
    }

    private fun write(file: File, bytes: ByteArray) {
        val tmp = File(file.path + ".tmp")
        tmp.writeBytes(bytes)
        check(tmp.renameTo(file)) { "could not write ${file.name}" }
    }

    private fun fetch(url: String): String {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.instanceFollowRedirects = true
            c.setRequestProperty("user-agent", "DPIMech")
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            val out = java.io.ByteArrayOutputStream()
            c.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    check(out.size() <= MAX_BYTES) { "list larger than ${MAX_BYTES shr 20} MB" }
                }
            }
            return out.toString(Charsets.UTF_8.name())
        } finally {
            c.disconnect()
        }
    }
}

/**
 * What blocking did while the profile runs: the count and the most recent names, with the app
 * that asked when Android tells (10+). Kept in memory only; reset at each start.
 */
object AdblockLog {
    data class Entry(val time: Long, val name: String, val app: String?)

    private const val KEEP = 300
    private val recent = ArrayDeque<Entry>()
    private val state = MutableStateFlow<List<Entry>>(emptyList())

    /** Newest first. */
    val flow: StateFlow<List<Entry>> = state.asStateFlow()
    private val counter = java.util.concurrent.atomic.AtomicLong()
    val count get() = counter.get()

    @Synchronized
    fun add(name: String, app: String?) {
        counter.incrementAndGet()
        // A burst for the same name (A + AAAA + HTTPS) is one line.
        val first = recent.firstOrNull()
        if (first != null && first.name == name && System.currentTimeMillis() - first.time < 2000) return
        recent.addFirst(Entry(System.currentTimeMillis(), name, app))
        while (recent.size > KEEP) recent.removeLast()
        state.value = recent.toList()
    }

    @Synchronized
    fun reset() {
        counter.set(0)
        recent.clear()
        state.value = emptyList()
    }
}
