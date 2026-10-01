package io.github.halilkhrmn.dpimech.core

/**
 * Ready-made site packs, ported from the desktop app (`crates/core/src/catalog.rs`).
 * Android adds [packages]: choosing a pack ticks these apps in the profile when installed.
 */
data class DomainPack(
    val id: String,
    val name: String,
    /** Everything the engine should act on (goes into the hostlist). */
    val domains: List<String>,
    /** Hosts the Strategy Lab requests; each answers HTTPS on `/`. */
    val probes: List<String>,
    /** Android apps that use these domains. */
    val packages: List<String>,
) {
    companion object {
        val ALL: List<DomainPack> = listOf(
            DomainPack(
                id = "discord",
                name = "Discord",
                domains = listOf(
                    "discord.com", "discordapp.com", "discord.gg", "discord.media", "discordapp.net",
                    "gateway.discord.gg", "cdn.discordapp.com", "media.discordapp.net",
                    "images-ext-1.discordapp.net", "updates.discord.com", "dis.gd",
                ),
                probes = listOf(
                    "discord.com", "discordapp.com", "discord.gg", "gateway.discord.gg",
                    "cdn.discordapp.com", "media.discordapp.net", "updates.discord.com", "dis.gd",
                ),
                packages = listOf("com.discord"),
            ),
            DomainPack(
                id = "youtube",
                name = "YouTube",
                domains = listOf(
                    "youtube.com", "www.youtube.com", "youtu.be", "i.ytimg.com", "yt3.ggpht.com",
                    "youtubei.googleapis.com", "manifest.googlevideo.com",
                    "redirector.googlevideo.com", "googlevideo.com",
                ),
                probes = listOf(
                    "www.youtube.com", "youtube.com", "youtu.be", "i.ytimg.com", "yt3.ggpht.com",
                    "youtubei.googleapis.com", "redirector.googlevideo.com",
                ),
                // Official apps plus well-known FLOSS / patched clients (see PLAN.md open items).
                packages = listOf(
                    "com.google.android.youtube",
                    "com.google.android.apps.youtube.music",
                    "app.revanced.android.youtube",
                    "app.revanced.android.apps.youtube.music",
                    "org.schabi.newpipe",
                    "com.github.libretube",
                ),
            ),
            DomainPack(
                id = "roblox",
                name = "Roblox",
                domains = listOf("roblox.com", "www.roblox.com", "rbxcdn.com", "apis.roblox.com"),
                probes = listOf("www.roblox.com", "roblox.com", "apis.roblox.com"),
                packages = listOf("com.roblox.client"),
            ),
            DomainPack(
                id = "x",
                name = "X / Twitter",
                domains = listOf("x.com", "twitter.com", "twimg.com", "pbs.twimg.com"),
                probes = listOf("x.com", "twitter.com", "pbs.twimg.com"),
                packages = listOf("com.twitter.android"),
            ),
            DomainPack(
                id = "instagram",
                name = "Instagram",
                domains = listOf("instagram.com", "www.instagram.com", "cdninstagram.com"),
                probes = listOf("www.instagram.com", "instagram.com"),
                packages = listOf("com.instagram.android"),
            ),
            DomainPack(
                id = "wattpad",
                name = "Wattpad",
                domains = listOf("wattpad.com", "www.wattpad.com"),
                probes = listOf("www.wattpad.com", "wattpad.com"),
                packages = listOf("wp.wattpad"),
            ),
            DomainPack(
                id = "imgur",
                name = "Imgur",
                domains = listOf("imgur.com", "i.imgur.com", "i.stack.imgur.com"),
                probes = listOf("imgur.com", "i.imgur.com"),
                packages = listOf("com.imgur.mobile"),
            ),
            DomainPack(
                id = "facebook",
                name = "Facebook",
                domains = listOf("facebook.com", "www.facebook.com", "fbcdn.net", "messenger.com", "fb.com"),
                probes = listOf("www.facebook.com", "facebook.com"),
                packages = listOf("com.facebook.katana", "com.facebook.orca", "com.facebook.lite"),
            ),
            DomainPack(
                id = "linkedin",
                name = "LinkedIn",
                domains = listOf("linkedin.com", "www.linkedin.com", "licdn.com"),
                probes = listOf("www.linkedin.com"),
                packages = listOf("com.linkedin.android"),
            ),
            DomainPack(
                id = "signal",
                name = "Signal",
                domains = listOf("signal.org", "whispersystems.org", "signal.art", "updates.signal.org"),
                probes = listOf("signal.org"),
                packages = listOf("org.thoughtcrime.securesms"),
            ),
            DomainPack(
                id = "viber",
                name = "Viber",
                domains = listOf("viber.com", "www.viber.com"),
                probes = listOf("www.viber.com", "viber.com"),
                packages = listOf("com.viber.voip"),
            ),
        )

        fun byId(id: String): DomainPack? = ALL.find { it.id == id }
    }
}
