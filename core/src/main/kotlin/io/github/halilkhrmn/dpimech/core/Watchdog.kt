package io.github.halilkhrmn.dpimech.core

/**
 * Decides when the running ciadpi must be restarted. The engine service calls [tick] about
 * once a second: a dead process is restarted at once; a living one gets a SOCKS5 health probe
 * every [probeEverySec] seconds and is restarted after [failuresBeforeRestart] failed probes in
 * a row (it can hang without exiting). More than [maxRestarts] restarts within [windowSec]
 * means something is wrong that a restart will not fix, so the watchdog gives up.
 */
class Watchdog(
    private val probeEverySec: Int = 20,
    private val failuresBeforeRestart: Int = 2,
    private val maxRestarts: Int = 5,
    private val windowSec: Long = 10 * 60,
) {
    enum class Action { NONE, RESTART, GIVE_UP }

    private var sinceProbe = 0
    private var failures = 0
    private val restarts = ArrayDeque<Long>()

    /** [nowSec] is a monotonic clock; [greets] runs the probe only when one is due. */
    fun tick(nowSec: Long, alive: Boolean, greets: () -> Boolean): Pair<Action, String?> {
        val reason = when {
            !alive -> "ByeDPI exited"
            ++sinceProbe < probeEverySec -> null
            else -> {
                sinceProbe = 0
                if (greets()) {
                    failures = 0
                    null
                } else if (++failures >= failuresBeforeRestart) {
                    "ByeDPI stopped answering ($failures health checks failed)"
                } else {
                    null
                }
            }
        } ?: return Action.NONE to null

        sinceProbe = 0
        failures = 0
        while (restarts.isNotEmpty() && nowSec - restarts.first() > windowSec) restarts.removeFirst()
        if (restarts.size >= maxRestarts) return Action.GIVE_UP to reason
        restarts.addLast(nowSec)
        return Action.RESTART to reason
    }
}
