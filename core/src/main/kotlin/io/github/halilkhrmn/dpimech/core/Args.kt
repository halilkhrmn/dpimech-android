package io.github.halilkhrmn.dpimech.core

/**
 * Splits a command-line string into arguments, honouring double and single quotes.
 * Same rules as the desktop app (`crates/core/src/args.rs`) so strategy strings behave alike.
 */
fun splitArgs(input: String): List<String> {
    val out = mutableListOf<String>()
    val cur = StringBuilder()
    var inToken = false
    var quote: Char? = null

    for (c in input) {
        when {
            quote != null && c == quote -> quote = null
            quote != null -> cur.append(c)
            c == '"' || c == '\'' -> {
                quote = c
                inToken = true
            }
            c.isWhitespace() -> if (inToken) {
                out += cur.toString()
                cur.clear()
                inToken = false
            }
            else -> {
                cur.append(c)
                inToken = true
            }
        }
    }
    if (inToken) out += cur.toString()
    return out
}

/** Inverse of [splitArgs] for display and storage: quotes tokens that would not survive a split. */
fun joinArgs(args: List<String>): String = args.joinToString(" ") { arg ->
    when {
        arg.isEmpty() -> "''"
        arg.none { it.isWhitespace() || it == '"' || it == '\'' } -> arg
        '"' !in arg -> "\"$arg\""
        else -> "'$arg'"
    }
}
