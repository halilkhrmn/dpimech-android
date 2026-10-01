package io.github.halilkhrmn.dpimech.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** App-wide settings in `files/settings.json`. */
@Serializable
data class AppSettings(
    /** BCP-47 tag (`tr`, `en`, `ru`) or empty for the phone's language. */
    val language: String = "",
    /** DNS server announced to the bypassed apps (see [DNS_SERVERS]). */
    val dns: String = DNS_SERVERS.first().address,
    /**
     * Automatic strategy: on a provider without a remembered strategy, test the standard set
     * in the background and switch to the best confirmed one.
     */
    val autoStrategy: Boolean = true,
    /**
     * Answer the bypassed apps' DNS over HTTPS (to the [dns] server's DoH endpoint), so the
     * provider cannot read or replace it; plain DNS through the tunnel when DoH is unreachable.
     */
    val encryptedDns: Boolean = true,
    /** Drop QUIC (UDP 443) of the bypassed apps, so browsers and YouTube use TCP, where the bypass works. */
    val blockQuic: Boolean = false,
    /** Turn the selected profile on when the phone starts (needs the VPN permission already). */
    val startOnBoot: Boolean = false,
    /** Download the strategy lists when the app starts and the copy is older than a week. */
    val autoUpdateLists: Boolean = true,
    /** Material You: take the colours from the wallpaper (Android 12+). */
    val dynamicColor: Boolean = true,
    /** Notify when the automatic strategy switches to another strategy. */
    val notifyStrategy: Boolean = true,
    /** Notify when the bypass stops on its own (engine kept failing, VPN taken over). */
    val notifyErrors: Boolean = true,
    /** The first-start wizard was finished or skipped. */
    val wizardDone: Boolean = false,
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    data class DnsServer(val name: String, val address: String)

    companion object {
        val LANGUAGES = listOf("", "en", "tr", "ru")

        val DNS_SERVERS = listOf(
            DnsServer("Cloudflare", "1.1.1.1"),
            DnsServer("Google", "8.8.8.8"),
            DnsServer("Quad9", "9.9.9.9"),
            DnsServer("AdGuard", "94.140.14.14"),
        )

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun decode(text: String): AppSettings =
            runCatching { json.decodeFromString(serializer(), text) }.getOrDefault(AppSettings())

        /** A plain IPv4 address (the VPN builder rejects anything else). */
        fun isValidDns(s: String) = s.split('.').let { p -> p.size == 4 && p.all { it.toIntOrNull() in 0..255 } }
    }
}
