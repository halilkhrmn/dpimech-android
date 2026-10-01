package io.github.halilkhrmn.dpimech.core

import java.io.File
import java.net.ServerSocket

/**
 * "Does this site open?" for one address, without a full Strategy Lab run: once directly and
 * once through a short-lived ciadpi with the given strategy, limited to that host.
 */
class QuickCheck(
    private val launcher: EngineLauncher,
    private val listsDir: File,
    private val measure: (Connector, String) -> Long? = { c, h -> SiteCheck().measure(c, h) },
    private val viaProxy: (Int) -> Connector = Connector::socks,
) {
    enum class Verdict {
        /** Opens without the bypass: nothing to do. */
        OPEN,
        /** Blocked, but opens with the strategy. */
        BYPASSED,
        /** Opens neither way: another strategy (Lab) may help, or it is blocked by IP. */
        BLOCKED,
    }

    data class Result(
        val host: String,
        val strategy: String,
        val directMs: Long?,
        val bypassMs: Long?,
        val error: String? = null,
    ) {
        val verdict: Verdict
            get() = when {
                directMs != null -> Verdict.OPEN
                bypassMs != null -> Verdict.BYPASSED
                else -> Verdict.BLOCKED
            }
    }

    /** The host from what the user typed or pasted, or null when it is not a domain. */
    fun hostOf(input: String): String? = input.trim().takeIf { ' ' !in it }
        ?.let { Hostlist.parseInput(it).firstOrNull() }
        ?.takeIf { '.' in it }

    fun run(host: String, strategy: StrategyEntry): Result {
        val direct = runCatching { measure(Connector.DIRECT, host) }.getOrNull()
        val port = ServerSocket(0).use { it.localPort }
        val hostlist = File(listsDir.apply { mkdirs() }, "quick-$port.txt")
        return try {
            hostlist.writeText(Hostlist.body(listOf(host)))
            val args = ByeDpiCommand.build(ByeDpiCommand.Request(strategy.args, port, listsDir, hostlist, domainFilter = true)).getOrElse {
                return Result(host, strategy.name, direct, null, it.message ?: "invalid arguments")
            }
            val engine = try {
                launcher.start(args, port)
            } catch (e: Exception) {
                return Result(host, strategy.name, direct, null, e.message ?: "engine did not start")
            }
            val viaBypass = engine.use { runCatching { measure(viaProxy(port), host) }.getOrNull() }
            Result(host, strategy.name, direct, viaBypass)
        } finally {
            hostlist.delete()
        }
    }
}
