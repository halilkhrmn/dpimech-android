package io.github.halilkhrmn.dpimech.ui

import io.github.halilkhrmn.dpimech.core.TrafficStats
import kotlin.math.sin

/** A minute of plausible traffic (a video starting, then steady) for screen renders. */
fun sampleStats(): TrafficStats {
    val start = System.currentTimeMillis() - 754_000
    var s = TrafficStats(since = start, encryptedDns = true, dnsQueries = 312, dnsEncrypted = 309, restarts = 0)
    var down = 0L
    var up = 0L
    s = s.sample(0, 0, start)
    for (i in 1..60) {
        val rate = (if (i < 12) 150_000 else 1_400_000) + (sin(i / 3.0) * 350_000).toLong() + (i % 7) * 40_000
        down += rate.coerceAtLeast(20_000)
        up += 35_000 + (i % 5) * 9_000
        s = s.sample(down, up, start + i * 1000L)
    }
    return s.copy(downTotal = 184_300_000, upTotal = 9_800_000)
}
