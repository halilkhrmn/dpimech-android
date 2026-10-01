package io.github.halilkhrmn.dpimech.core

import java.io.File
import java.net.ServerSocket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Starts one ciadpi with [args] listening on [port]; closing the handle stops it. */
fun interface EngineLauncher {
    fun start(args: List<String>, port: Int): AutoCloseable
}

data class LabRequest(
    val strategies: List<LabStrategy>,
    /** Hosts requested during the test (the packs' probes). */
    val probes: List<String>,
    /** Requests per host in the quick round; more catch flaky strategies. */
    val repeats: Int = 2,
    val port: Int = 443,
)

/**
 * Strategy Lab, ported from the desktop service (`lab.rs`): a baseline run without bypass,
 * then every strategy on its own ciadpi ([parallel] at a time), then extra rounds for the best
 * candidates so a lucky quick round does not win.
 */
class LabRunner(
    private val launcher: EngineLauncher,
    private val listsDir: File,
    /** Whether a site opens over a connector; [SiteCheck.opens] in the app. */
    private val opens: (Connector, String, Int) -> Boolean = SiteCheck()::opens,
    private val parallel: Int = 4,
    /** How requests reach the sites through an engine on a port; tests point it at local servers. */
    private val viaProxy: (Int) -> Connector = Connector::socks,
    private val direct: Connector = Connector.DIRECT,
) {
    fun interface Listener {
        /** [result] with `strategy == null` is the baseline. Called from worker threads. */
        fun onResult(done: Int, total: Int, result: LabResult)
    }

    /** Runs the whole test; [cancelled] is polled between steps. Returns the final results. */
    fun run(req: LabRequest, listener: Listener, cancelled: () -> Boolean = { false }): List<LabResult> {
        require(req.probes.isNotEmpty()) { "choose at least one site to test" }
        require(req.strategies.isNotEmpty()) { "no strategies selected" }
        val repeats = req.repeats.coerceIn(1, 5)
        val total = 1 + req.strategies.size + minOf(req.strategies.size, CONFIRM_TOP)
        val done = AtomicInteger()
        val pool = Executors.newFixedThreadPool(maxOf(parallel * req.probes.size, 4))
        try {
            val baseline = checkSites(pool, direct, req, repeats).copy(strategy = null)
            listener.onResult(done.incrementAndGet(), total, baseline)

            val queue = ConcurrentLinkedQueue(req.strategies)
            val quick = ConcurrentLinkedQueue<LabResult>()
            val workers = (0 until parallel).map {
                Thread {
                    while (!cancelled()) {
                        val s = queue.poll() ?: break
                        val r = tryStrategy(pool, s, req, repeats)
                        quick += r
                        listener.onResult(done.incrementAndGet(), total, r)
                    }
                }.apply { start() }
            }
            workers.forEach { it.join() }

            val final = quick.toMutableList()
            for (candidate in confirmCandidates(quick.toList())) {
                if (cancelled()) break
                val extra = tryStrategy(pool, candidate.strategy!!, req, CONFIRM_ROUNDS)
                val merged = mergeConfirmation(candidate, extra)
                final[final.indexOfFirst { it.sameStrategy(candidate) }] = merged
                listener.onResult(done.incrementAndGet(), total, merged)
            }
            return listOf(baseline) + final
        } finally {
            pool.shutdownNow()
        }
    }

    private fun tryStrategy(pool: java.util.concurrent.ExecutorService, s: LabStrategy, req: LabRequest, repeats: Int): LabResult {
        val failed = { error: String ->
            LabResult(s, 0, req.probes.size * repeats, 0, req.probes, error = error)
        }
        val port = ServerSocket(0).use { it.localPort }
        val hostlist = File(listsDir, "lab-$port.txt")
        return try {
            hostlist.writeText(Hostlist.body(req.probes))
            val args = ByeDpiCommand.build(ByeDpiCommand.Request(s.args, port, listsDir, hostlist)).getOrElse {
                return failed(it.message ?: "invalid arguments")
            }
            val engine = try {
                launcher.start(args, port)
            } catch (e: Exception) {
                return failed(e.message ?: "engine did not start")
            }
            engine.use { checkSites(pool, viaProxy(port), req, repeats).copy(strategy = s) }
        } finally {
            hostlist.delete()
        }
    }

    /** Every host [repeats] times, hosts in parallel; average time of the successful requests. */
    private fun checkSites(pool: java.util.concurrent.ExecutorService, via: Connector, req: LabRequest, repeats: Int): LabResult {
        val perHost = req.probes.map { host ->
            pool.submit<Triple<String, Int, Long>> {
                var ok = 0
                var ms = 0L
                repeat(repeats) {
                    val started = System.nanoTime()
                    if (opens(via, host, req.port)) {
                        ok++
                        ms += (System.nanoTime() - started) / 1_000_000
                    }
                }
                Triple(host, ok, ms)
            }
        }.map { it.get(10, TimeUnit.MINUTES) }
        val ok = perHost.sumOf { it.second }
        val ms = perHost.sumOf { it.third }
        return LabResult(
            strategy = null,
            ok = ok,
            total = req.probes.size * repeats,
            avgMs = if (ok == 0) 0 else (ms / ok).toInt(),
            failedDomains = perHost.filter { it.second < repeats }.map { it.first },
        )
    }

    companion object {
        /** How many of the best strategies get extra rounds, and how many. */
        const val CONFIRM_TOP = 5
        const val CONFIRM_ROUNDS = 3

        /** The best quick-round results worth extra rounds. */
        fun confirmCandidates(results: List<LabResult>): List<LabResult> = results
            .filter { it.error == null && it.ok > 0 && it.strategy != null }
            .sortedDescending()
            .take(CONFIRM_TOP)

        /** Adds the extra rounds to the quick result; confirmed only if every request succeeded. */
        fun mergeConfirmation(quick: LabResult, extra: LabResult): LabResult {
            val ok = quick.ok + extra.ok
            val ms = quick.avgMs.toLong() * quick.ok + extra.avgMs.toLong() * extra.ok
            return quick.copy(
                ok = ok,
                total = quick.total + extra.total,
                avgMs = if (ok == 0) 0 else (ms / ok).toInt(),
                failedDomains = (quick.failedDomains + extra.failedDomains).distinct(),
                error = extra.error,
                confirmed = extra.error == null && quick.ok == quick.total && extra.ok == extra.total,
            )
        }
    }
}
