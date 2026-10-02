package io.github.halilkhrmn.dpimech.data

import android.content.Context
import io.github.halilkhrmn.dpimech.core.DnsCheck
import io.github.halilkhrmn.dpimech.core.DomainPack
import io.github.halilkhrmn.dpimech.core.TunnelConfig
import java.net.InetSocketAddress
import io.github.halilkhrmn.dpimech.core.IspInfo
import io.github.halilkhrmn.dpimech.core.IspLookup
import io.github.halilkhrmn.dpimech.core.LabRequest
import io.github.halilkhrmn.dpimech.core.LabResult
import io.github.halilkhrmn.dpimech.core.LabRunner
import io.github.halilkhrmn.dpimech.core.LabStrategy
import io.github.halilkhrmn.dpimech.engine.AndroidEngineLauncher
import io.github.halilkhrmn.dpimech.engine.EngineLog
import io.github.halilkhrmn.dpimech.engine.NetworkIdentity
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LabState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val baseline: LabResult? = null,
    /** Strategy results, best first. */
    val results: List<LabResult> = emptyList(),
    val isp: IspInfo? = null,
    val error: String? = null,
    val finished: Boolean = false,
    /** DNS answers that differ from DNS over HTTPS (signs of DNS blocking). */
    val dns: List<DnsCheck.Finding> = emptyList(),
)

/** The quick site check under the Test tab: one address, directly and through the bypass. */
data class QuickState(
    val running: Boolean = false,
    val result: io.github.halilkhrmn.dpimech.core.QuickCheck.Result? = null,
    /** The input was not a site address. */
    val badInput: Boolean = false,
)

/** Runs the Strategy Lab in the background and keeps its progress for the UI (app-wide). */
class LabController(private val context: Context) {
    private val state = MutableStateFlow(LabState())
    val flow: StateFlow<LabState> = state.asStateFlow()

    @Volatile
    private var cancelled = false

    private val quickState = MutableStateFlow(QuickState())
    val quick: StateFlow<QuickState> = quickState.asStateFlow()

    /** Checks one site with [strategy] (the selected profile's strategy for this network). */
    fun quickCheck(input: String, strategy: io.github.halilkhrmn.dpimech.core.StrategyEntry) {
        if (quickState.value.running) return
        val lists = File(context.filesDir, "lists")
        val check = io.github.halilkhrmn.dpimech.core.QuickCheck(AndroidEngineLauncher(context), lists)
        val host = check.hostOf(input) ?: run {
            quickState.value = QuickState(badInput = true)
            return
        }
        quickState.value = QuickState(running = true)
        thread(name = "quick-check", isDaemon = true) {
            val r = runCatching { check.run(host, strategy) }.getOrElse {
                io.github.halilkhrmn.dpimech.core.QuickCheck.Result(host, strategy.name, null, null, it.message ?: it.toString())
            }
            EngineLog.add("quick check $host: direct ${r.directMs ?: "-"} ms, bypass (${r.strategy}) ${r.bypassMs ?: "-"} ms${r.error?.let { " ($it)" } ?: ""}")
            quickState.value = QuickState(result = r)
        }
    }

    fun detectIsp() {
        if (state.value.isp != null) return
        thread(name = "isp", isDaemon = true) {
            (NetworkIdentity.flow.value?.isp ?: NetworkIdentity.refresh())?.let { info -> state.update { it.copy(isp = info) } }
        }
    }

    /** Tests [options] on the probe hosts of [packs] plus [domains] typed in by hand. */
    fun start(packs: List<DomainPack>, domains: List<String>, options: List<StrategyOption>) {
        if (state.value.running) return
        cancelled = false
        val strategies = IspLookup.markRecommended(
            options.map { LabStrategy(it.entry.name, it.entry.args, it.source, it.origin) },
            state.value.isp?.known,
        )
        val probes = (packs.flatMap { it.probes } + domains).distinct()
        state.update { LabState(running = true, isp = it.isp) }
        // Keeps the process (and the test) alive while the user is in another app.
        io.github.halilkhrmn.dpimech.lab.LabService.start(context)
        thread(name = "lab", isDaemon = true) {
            val lists = File(context.filesDir, "lists").apply { mkdirs() }
            val runner = LabRunner(AndroidEngineLauncher(context), lists)
            try {
                val dns = checkDns(probes)
                state.update { it.copy(dns = dns) }
                runner.run(
                    LabRequest(strategies, probes),
                    listener = { done, total, r -> state.update { it.add(done, total, r) } },
                    cancelled = { cancelled },
                )
                state.update { it.copy(running = false, finished = !cancelled) }
            } catch (e: Exception) {
                EngineLog.add("lab failed: $e")
                state.update { it.copy(running = false, error = e.message ?: e.toString()) }
            }
        }
    }

    /**
     * The phone's DNS and the tunnel's DNS server (plain UDP 53, as the bypassed apps use it)
     * against DNS over HTTPS. Only mismatches are kept.
     */
    private fun checkDns(probes: List<String>): List<DnsCheck.Finding> {
        val tunnelDns = TunnelConfig(socksPort = 1).dns
        return DnsCheck.namesToCheck(probes).flatMap { name ->
            val doh = doh(name)
            listOf(
                DnsCheck.compare(name, "system", DnsCheck.system(name), doh),
                DnsCheck.compare(name, tunnelDns, DnsCheck.queryUdp(InetSocketAddress(tunnelDns, 53), name), doh),
            )
        }.filter { it.verdict == DnsCheck.Verdict.NOT_RESOLVED || it.verdict == DnsCheck.Verdict.DIFFERENT }
            .onEach { EngineLog.add("DNS: ${it.name} via ${it.via} -> ${it.got} (DoH: ${it.doh})") }
    }

    /** 1.1.1.1 by address first (no DNS needed), the host name as a fallback. */
    private fun doh(name: String): Set<String>? = listOf("https://1.1.1.1", "https://cloudflare-dns.com").firstNotNullOfOrNull { base ->
        runCatching {
            val conn = URL("$base/dns-query?name=$name&type=A").openConnection() as HttpURLConnection
            conn.setRequestProperty("accept", "application/dns-json")
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            try {
                DnsCheck.parseDohJson(conn.inputStream.use { it.readBytes().decodeToString() })
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    fun cancel() {
        cancelled = true
    }

    private fun LabState.add(done: Int, total: Int, r: LabResult): LabState {
        if (r.strategy == null) return copy(done = done, total = total, baseline = r)
        // A confirmation replaces the quick result of the same strategy.
        val list = results.filterNot { it.sameStrategy(r) } + r
        return copy(done = done, total = total, results = list.sortedDescending())
    }
}
