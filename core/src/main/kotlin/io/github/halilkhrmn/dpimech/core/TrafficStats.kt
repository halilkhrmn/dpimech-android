package io.github.halilkhrmn.dpimech.core

/**
 * What the home screen chart and the notification show while the bypass is on: traffic of the
 * bypassed apps (from hev-socks5-tunnel's counters), DNS and QUIC counts, engine restarts.
 */
data class TrafficStats(
    /** When the bypass started (epoch ms), 0 when off. */
    val since: Long = 0,
    val downTotal: Long = 0,
    val upTotal: Long = 0,
    /** Bytes per second, oldest first, at most [HISTORY] samples. */
    val down: List<Long> = emptyList(),
    val up: List<Long> = emptyList(),
    val dnsQueries: Long = 0,
    val dnsEncrypted: Long = 0,
    val quicBlocked: Long = 0,
    val restarts: Int = 0,
    val encryptedDns: Boolean = false,
    val blockQuic: Boolean = false,
    /** Last average ping of the profile's sites through the bypass, null before the first one. */
    val ping: PingResult? = null,
    val pinging: Boolean = false,
    /** Raw counters of the last sample, to turn the next one into a rate. */
    val lastDown: Long = 0,
    val lastUp: Long = 0,
    val lastAt: Long = 0,
) {
    /**
     * Adds a sample of hev's byte counters taken at [now]. Counters start again from zero when
     * the tunnel is rebuilt (network gained or lost IPv6); a smaller value counts as all new.
     */
    fun sample(downCounter: Long, upCounter: Long, now: Long): TrafficStats {
        if (lastAt == 0L) return copy(lastDown = downCounter, lastUp = upCounter, lastAt = now)
        val dDown = if (downCounter >= lastDown) downCounter - lastDown else downCounter
        val dUp = if (upCounter >= lastUp) upCounter - lastUp else upCounter
        val secs = ((now - lastAt).coerceAtLeast(1)) / 1000.0
        return copy(
            downTotal = downTotal + dDown,
            upTotal = upTotal + dUp,
            down = (down + (dDown / secs).toLong()).takeLast(HISTORY),
            up = (up + (dUp / secs).toLong()).takeLast(HISTORY),
            lastDown = downCounter,
            lastUp = upCounter,
            lastAt = now,
        )
    }

    val downRate get() = down.lastOrNull() ?: 0
    val upRate get() = up.lastOrNull() ?: 0

    companion object {
        /** One sample a second: the last minute. */
        const val HISTORY = 60

        /** 1.2 MB, 340 KB, 12 B (decimal units, like Android's own data usage screen). */
        fun bytes(n: Long): String = when {
            n >= 1_000_000_000 -> "%.1f GB".format(java.util.Locale.ROOT, n / 1e9)
            n >= 1_000_000 -> "%.1f MB".format(java.util.Locale.ROOT, n / 1e6)
            n >= 1_000 -> "${n / 1000} KB"
            else -> "$n B"
        }
    }
}
