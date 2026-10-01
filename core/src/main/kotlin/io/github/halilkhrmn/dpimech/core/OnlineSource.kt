package io.github.halilkhrmn.dpimech.core

/** A frequently updated community strategy list, fetched at runtime (never bundled). */
data class OnlineSource(
    /** What the user sees next to each strategy. */
    val label: String,
    /** Where the list comes from (credited in the UI). */
    val origin: String,
    val url: String,
) {
    companion object {
        val COMMUNITY = OnlineSource(
            label = "Community list",
            origin = "github.com/romanvht/ByeDPIManager",
            url = "https://raw.githubusercontent.com/romanvht/ByeDPIManager/main/proxytest/strategies.txt",
        )

        /**
         * Parses an args-per-line list into strategies named `#<line>`. Blank lines and `#`
         * comments are skipped.
         */
        fun parseArgsPerLine(text: String): List<StrategyEntry> =
            text.lines().mapIndexedNotNull { i, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith('#')) null else StrategyEntry("#${i + 1}", line)
            }
    }
}
