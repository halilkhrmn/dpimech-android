package io.github.halilkhrmn.dpimech.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Profiles saved to a file (Settings → Back up) and read back, e.g. on a new phone. Includes the
 * per-network strategies the profiles learned. App lists are package names, so apps that are not
 * installed on the new phone are simply not caught.
 */
@Serializable
data class ProfileBackup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val profiles: List<Profile> = emptyList(),
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val FORMAT = "dpimech-profiles"
        const val VERSION = 1
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

        fun of(saved: SavedProfiles) = ProfileBackup(profiles = saved.profiles)

        /** The backup in [text], or a failure that says why (not a backup, newer version, damaged). */
        fun decode(text: String): Result<ProfileBackup> = runCatching {
            val b = json.decodeFromString(serializer(), text)
            require(b.format == FORMAT) { "not a DPIMech profile backup" }
            require(b.version <= VERSION) { "made by a newer DPIMech (backup version ${b.version})" }
            b
        }

        /**
         * Adds the backup's profiles: a profile with the same id is replaced, the others are
         * added. The selected profile stays, or becomes the first imported one if none was set.
         */
        fun merge(into: SavedProfiles, backup: ProfileBackup): SavedProfiles =
            backup.profiles.fold(into) { acc, p -> acc.upsert(p) }
    }
}
