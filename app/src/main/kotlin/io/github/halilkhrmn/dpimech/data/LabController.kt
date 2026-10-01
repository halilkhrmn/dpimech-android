package io.github.halilkhrmn.dpimech.data

import android.content.Context
import io.github.halilkhrmn.dpimech.core.DomainPack
import io.github.halilkhrmn.dpimech.core.IspInfo
import io.github.halilkhrmn.dpimech.core.IspLookup
import io.github.halilkhrmn.dpimech.core.LabRequest
import io.github.halilkhrmn.dpimech.core.LabResult
import io.github.halilkhrmn.dpimech.core.LabRunner
import io.github.halilkhrmn.dpimech.core.LabStrategy
import io.github.halilkhrmn.dpimech.engine.AndroidEngineLauncher
import io.github.halilkhrmn.dpimech.engine.EngineLog
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
)

/** Runs the Strategy Lab in the background and keeps its progress for the UI (app-wide). */
class LabController(private val context: Context) {
    private val state = MutableStateFlow(LabState())
    val flow: StateFlow<LabState> = state.asStateFlow()

    @Volatile
    private var cancelled = false

    fun detectIsp() {
        if (state.value.isp != null) return
        thread(name = "isp", isDaemon = true) {
            runCatching {
                val conn = URL(IspLookup.URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                try {
                    IspLookup.parse(conn.inputStream.use { it.readBytes().decodeToString() }).getOrThrow()
                } finally {
                    conn.disconnect()
                }
            }.onSuccess { info -> state.update { it.copy(isp = info) } }
                .onFailure { EngineLog.add("ISP lookup failed: $it") }
        }
    }

    fun start(packs: List<DomainPack>, options: List<StrategyOption>) {
        if (state.value.running) return
        cancelled = false
        val strategies = IspLookup.markRecommended(
            options.map { LabStrategy(it.entry.name, it.entry.args, it.source, it.origin) },
            state.value.isp?.known,
        )
        val probes = packs.flatMap { it.probes }.distinct()
        state.update { LabState(running = true, isp = it.isp) }
        thread(name = "lab", isDaemon = true) {
            val lists = File(context.filesDir, "lists").apply { mkdirs() }
            val runner = LabRunner(AndroidEngineLauncher(context), lists)
            try {
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
