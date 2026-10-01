package io.github.halilkhrmn.dpimech.core

import java.io.File

/**
 * Turns a strategy into ciadpi's argument vector. Shared by profiles and the Strategy Lab so
 * both apply the same placeholders, defaults and argument policy.
 *
 * Placeholders: `{sni}` (harmless SNI for fake packets), `{hostlist}` (the generated hosts
 * file) and `{lists}` (the lists directory), as on desktop.
 */
object ByeDpiCommand {
    const val DEFAULT_SNI = "www.google.com"
    const val LOCALHOST = "127.0.0.1"

    /** ByeDPI's default (512 events ≈ 256 proxied connections) fills up after hours of use. */
    const val DEFAULT_MAX_CONN = "4096"

    data class Request(
        val strategyArgs: String,
        /** Local SOCKS5 port. */
        val port: Int,
        /** Directory with generated hostlists; the only place files may be read from. */
        val listsDir: File,
        /** Hostlist file for `{hostlist}` and for the domain filter, if the profile has domains. */
        val hostlist: File? = null,
        /** Restrict every strategy group to [hostlist] so other connections pass untouched. */
        val domainFilter: Boolean = false,
        /** Unix socket that `protect()`s ciadpi's outgoing sockets (VpnService). */
        val protectPath: String? = null,
    )

    /** Full argument list, without the program name. The failure message is user-facing. */
    fun build(req: Request): Result<List<String>> = runCatching {
        require(req.port in 1..65535) { "invalid port ${req.port}" }
        val needsHostlist = "{hostlist}" in req.strategyArgs || req.domainFilter
        require(!needsHostlist || req.hostlist != null) {
            "this strategy needs at least one domain (add Discord, YouTube, … to the profile)"
        }
        val hostlist = req.hostlist?.path.orEmpty()
        val user = splitArgs(req.strategyArgs).map {
            it.replace("{sni}", DEFAULT_SNI)
                .replace("{hostlist}", hostlist)
                .replace("{lists}", req.listsDir.path)
        }
        ArgPolicy.check(user, listOf(req.listsDir)).getOrThrow()
        val parsed = ArgPolicy.parse(user)

        val args = mutableListOf("-i", LOCALHOST, "-p", req.port.toString())
        req.protectPath?.let { args += listOf("--protect-path", it) }
        if (parsed.none { it.long == "max-conn" }) args += listOf("-c", DEFAULT_MAX_CONN)
        args += if (req.domainFilter && parsed.none { it.long == "hosts" }) {
            scopeToHosts(user, parsed, hostlist)
        } else {
            user
        }
        args
    }

    /**
     * Puts `--hosts <file>` at the start of every desync group (the first one and each one
     * opened by `--auto`). When every group is limited, ciadpi appends an empty group, so
     * connections to other hosts pass without any desync.
     */
    private fun scopeToHosts(user: List<String>, parsed: List<ArgPolicy.Parsed>, hostlist: String): List<String> {
        val hosts = listOf("--hosts", hostlist)
        val out = hosts.toMutableList()
        var from = 0
        for (p in parsed.filter { it.long == "auto" }) {
            out += user.subList(from, p.end)
            out += hosts
            from = p.end
        }
        out += user.subList(from, user.size)
        return out
    }
}
