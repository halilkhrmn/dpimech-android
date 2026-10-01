package io.github.halilkhrmn.dpimech.core

import java.io.File

/**
 * Allowlist for ciadpi arguments, ported from the desktop app (`argpolicy.rs`, ByeDPI table).
 *
 * Strategies come from downloaded lists and QR codes. Options that change where the proxy
 * listens (exposing it to the local network), write files, or read files outside the app's
 * lists directory are rejected. Arguments are parsed the way ciadpi's getopt would (long-option
 * prefixes, `--opt=value`, clustered short flags), and unknown options are rejected so a new
 * engine release cannot silently introduce an unsafe flag.
 */
object ArgPolicy {
    private enum class Arg { NONE, REQUIRED }

    private enum class Policy {
        ALLOW,

        /** Set by the app (listen address, port, protect socket). */
        MANAGED,

        /** `:inline text` or a file in a readable directory. */
        INLINE_OR_FILE,

        /** Only `-` (stdout). */
        STDOUT_ONLY,
    }

    private class Opt(val short: Char, val long: String, val arg: Arg, val policy: Policy)

    private val N = Arg.NONE
    private val R = Arg.REQUIRED

    /** ciadpi 0.17 (`main.c` options), Linux build. */
    private val BYEDPI = listOf(
        Opt('i', "ip", R, Policy.MANAGED),
        Opt('p', "port", R, Policy.MANAGED),
        Opt('P', "protect-path", R, Policy.MANAGED),
        Opt('c', "max-conn", R, Policy.ALLOW),
        Opt('N', "no-domain", N, Policy.ALLOW),
        Opt('U', "no-udp", N, Policy.ALLOW),
        Opt('I', "conn-ip", R, Policy.ALLOW),
        Opt('b', "buf-size", R, Policy.ALLOW),
        Opt('x', "debug", R, Policy.ALLOW),
        Opt('g', "def-ttl", R, Policy.ALLOW),
        Opt('F', "tfo", N, Policy.ALLOW),
        Opt('A', "auto", R, Policy.ALLOW),
        Opt('L', "auto-mode", R, Policy.ALLOW),
        Opt('u', "cache-ttl", R, Policy.ALLOW),
        Opt('y', "cache-dump", R, Policy.STDOUT_ONLY),
        Opt('T', "timeout", R, Policy.ALLOW),
        Opt('K', "proto", R, Policy.ALLOW),
        Opt('H', "hosts", R, Policy.INLINE_OR_FILE),
        Opt('j', "ipset", R, Policy.INLINE_OR_FILE),
        Opt('V', "pf", R, Policy.ALLOW),
        Opt('R', "round", R, Policy.ALLOW),
        Opt('s', "split", R, Policy.ALLOW),
        Opt('d', "disorder", R, Policy.ALLOW),
        Opt('o', "oob", R, Policy.ALLOW),
        Opt('q', "disoob", R, Policy.ALLOW),
        Opt('f', "fake", R, Policy.ALLOW),
        Opt('S', "md5sig", N, Policy.ALLOW),
        Opt('n', "fake-sni", R, Policy.ALLOW),
        Opt('t', "ttl", R, Policy.ALLOW),
        Opt('O', "fake-offset", R, Policy.ALLOW),
        Opt('l', "fake-data", R, Policy.INLINE_OR_FILE),
        Opt('Q', "fake-tls-mod", R, Policy.ALLOW),
        Opt('e', "oob-data", R, Policy.ALLOW),
        Opt('M', "mod-http", R, Policy.ALLOW),
        Opt('r', "tlsrec", R, Policy.ALLOW),
        Opt('m', "tlsminor", R, Policy.ALLOW),
        Opt('a', "udp-fake", R, Policy.ALLOW),
        Opt('Y', "drop-sack", N, Policy.ALLOW),
    )

    /** One option as ciadpi's getopt sees it; [end] is the index just after its last token. */
    internal data class Parsed(val long: String, val value: String?, val end: Int)

    /**
     * Checks strategy arguments. Files may only be read from [readableDirs] (the app's lists
     * directory). The failure message is user-facing.
     */
    fun check(args: List<String>, readableDirs: List<File>): Result<Unit> = runCatching {
        for (p in parse(args)) {
            val opt = BYEDPI.first { it.long == p.long }
            checkValue(opt, p.value, readableDirs)
        }
    }

    /** Parses like getopt_long: long prefixes, `--opt=value`, clustered short flags. */
    internal fun parse(args: List<String>): List<Parsed> {
        val out = mutableListOf<Parsed>()
        var i = 0
        while (i < args.size) {
            val arg = args[i++]
            if (arg.startsWith("--")) {
                val long = arg.removePrefix("--")
                val name = long.substringBefore('=')
                val inline = if ('=' in long) long.substringAfter('=') else null
                val opt = findLong(name)
                val value = when {
                    opt.arg == Arg.NONE && inline != null -> fail("$arg: option takes no value")
                    opt.arg == Arg.NONE -> null
                    inline != null -> inline
                    else -> args.getOrNull(i++) ?: fail("$arg needs a value")
                }
                out += Parsed(opt.long, value, i)
            } else if (arg.startsWith("-") && arg.length > 1) {
                // An option with a value consumes the rest of the cluster, or the next argument
                // when nothing is left.
                val cluster = arg.substring(1)
                for ((pos, c) in cluster.withIndex()) {
                    val opt = BYEDPI.find { it.short == c } ?: fail("unknown option -$c")
                    if (opt.arg == Arg.NONE) {
                        out += Parsed(opt.long, null, i)
                        continue
                    }
                    val rest = cluster.substring(pos + 1)
                    val value = rest.ifEmpty { args.getOrNull(i++) ?: fail("$arg needs a value") }
                    out += Parsed(opt.long, value, i)
                    break
                }
            } else {
                fail("unexpected argument \"$arg\"")
            }
        }
        return out
    }

    /** getopt_long accepts any unambiguous prefix of a long option. */
    private fun findLong(name: String): Opt {
        BYEDPI.find { it.long == name }?.let { return it }
        val matches = BYEDPI.filter { name.isNotEmpty() && it.long.startsWith(name) }
        return when (matches.size) {
            1 -> matches[0]
            0 -> fail("unknown option --$name")
            else -> fail("ambiguous option --$name")
        }
    }

    private fun checkValue(opt: Opt, value: String?, dirs: List<File>) {
        val name = "--${opt.long}"
        val v = value.orEmpty()
        when (opt.policy) {
            Policy.ALLOW -> Unit
            Policy.MANAGED -> fail("$name is set by DPIMech (listen address, port); remove it")
            Policy.STDOUT_ONLY -> if (v != "-") fail("$name may only write to \"-\" (log)")
            Policy.INLINE_OR_FILE -> if (!v.startsWith(':') && dirs.none { isInside(File(v), it) }) {
                fail("$name accepts \":inline text\" or a file from DPIMech's lists")
            }
        }
    }

    /** Lexical check after normalising `..`; the app's own directories have no symlinks. */
    internal fun isInside(path: File, dir: File): Boolean {
        if (!path.isAbsolute) return false
        val p = path.toPath().normalize()
        val d = dir.toPath().normalize()
        return p != d && p.startsWith(d)
    }

    private fun fail(message: String): Nothing = throw IllegalArgumentException(message)
}
