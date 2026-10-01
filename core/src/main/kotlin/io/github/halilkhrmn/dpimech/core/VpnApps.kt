package io.github.halilkhrmn.dpimech.core

/**
 * Which packages the VPN builder allows or disallows. Android applies only one of the two
 * lists; an empty allow-list would capture every app, so that case is an error.
 */
data class VpnApps(val allowed: List<String>, val disallowed: List<String>) {
    companion object {
        /**
         * [installed] filters out apps that were removed (the builder throws on unknown packages).
         * DPIMech itself always stays outside the VPN: ciadpi's sockets belong to it and must
         * reach the network directly, or traffic would loop.
         */
        fun plan(profile: Profile, ownPackage: String, installed: Set<String>): Result<VpnApps> = runCatching {
            val apps = profile.apps.filter { it in installed && it != ownPackage }.distinct()
            when (profile.appMode) {
                AppMode.ONLY_SELECTED -> {
                    require(apps.isNotEmpty()) { "choose at least one installed app for this profile" }
                    VpnApps(allowed = apps, disallowed = emptyList())
                }
                AppMode.ALL_EXCEPT -> VpnApps(allowed = emptyList(), disallowed = listOf(ownPackage) + apps)
            }
        }
    }
}
