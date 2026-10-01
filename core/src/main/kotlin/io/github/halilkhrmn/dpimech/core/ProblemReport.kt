package io.github.halilkhrmn.dpimech.core

import java.net.URLEncoder

/**
 * "Report a problem", as on desktop (`report.rs`): a plain-text report the user reviews first,
 * then sends as a GitHub issue or by e-mail. A link cannot carry much text, so the issue link
 * holds the summary plus the newest log lines that fit; e-mail carries the whole report.
 */
data class ProblemReport(val summary: String, val log: List<String>) {
    val fullText: String get() = summary + "\n\nLog (newest last):\n" + log.joinToString("\n")

    /** GitHub "new issue" link; trims the oldest log lines until the URL is short enough. */
    fun issueUrl(title: String): String {
        val base = "https://github.com/$APP_REPO/issues/new?title=${enc(title)}&body="
        for (keep in log.size downTo 0) {
            val body = summary + "\n\nLog (newest last):\n```\n" + log.takeLast(keep).joinToString("\n") + "\n```\n"
            val url = base + enc(body)
            if (url.length <= MAX_ISSUE_URL) return url
        }
        return base + enc(summary.take(2000))
    }

    companion object {
        const val APP_REPO = "halilkhrmn/dpimech-android"
        const val SUPPORT_EMAIL = "halilkahraman@yandex.com"

        /** A GitHub "new issue" link longer than this is rejected. */
        const val MAX_ISSUE_URL = 7_500

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        /**
         * Builds the summary. Profiles show what was set (sites, apps by count, strategy), never
         * the list of installed apps, so the report holds nothing personal beyond that.
         */
        fun build(
            appVersion: String,
            device: String,
            settings: AppSettings,
            profiles: SavedProfiles,
            engineState: String,
            isp: IspInfo?,
            log: List<String>,
        ): ProblemReport {
            val s = buildString {
                appendLine("DPIMech for Android $appVersion")
                appendLine("Device: $device")
                appendLine("State: $engineState")
                appendLine("Provider: ${isp?.let { "${it.known?.name ?: it.provider} (${it.networkKey}, ${it.country})" } ?: "unknown"}")
                appendLine("Settings: DNS ${settings.dns}, automatic strategy ${if (settings.autoStrategy) "on" else "off"}")
                appendLine("Profiles:")
                if (profiles.profiles.isEmpty()) appendLine("  (none)")
                for (p in profiles.profiles) {
                    val mark = if (p.id == profiles.selected?.id) "*" else "-"
                    appendLine(
                        "  $mark ${p.name} · ${p.appMode} (${p.apps.size} apps) · sites ${(p.packs + p.extraDomains).joinToString(",")}" +
                            " · filter ${if (p.domainFilter) "on" else "off"}",
                    )
                    appendLine("    strategy: ${p.strategy.name}: ${p.strategy.args}")
                    p.perNetwork.forEach { (k, v) -> appendLine("    $k: ${v.name}") }
                }
            }.trimEnd()
            return ProblemReport(s, log)
        }
    }
}
