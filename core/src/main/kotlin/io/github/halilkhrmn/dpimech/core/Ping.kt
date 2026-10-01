package io.github.halilkhrmn.dpimech.core

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Average "ping" of a profile's sites through the bypass, as on the desktop app's profile card. */
data class PingResult(
    /** Average connect + TLS handshake time of the sites that opened, null when none did. */
    val averageMs: Long?,
    val ok: Int,
    val total: Int,
    /** When it was measured (epoch ms). */
    val at: Long,
)

object Ping {
    /** At most this many sites are tried, in parallel. */
    const val MAX_HOSTS = 6

    fun run(hosts: List<String>, measure: (String) -> Long?, now: () -> Long = System::currentTimeMillis): PingResult {
        val list = hosts.distinct().take(MAX_HOSTS)
        if (list.isEmpty()) return PingResult(null, 0, 0, now())
        val pool = Executors.newFixedThreadPool(list.size)
        try {
            val times = list.map { h -> pool.submit<Long?> { runCatching { measure(h) }.getOrNull() } }
                .map { runCatching { it.get(30, TimeUnit.SECONDS) }.getOrNull() }
            val ok = times.filterNotNull()
            return PingResult(if (ok.isEmpty()) null else ok.sum() / ok.size, ok.size, list.size, now())
        } finally {
            pool.shutdownNow()
        }
    }
}
