package io.github.halilkhrmn.dpimech.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Everything the user saved: profiles and which one the on/off switch uses. */
@Serializable
data class SavedProfiles(
    val profiles: List<Profile> = emptyList(),
    val selectedId: String? = null,
) {
    val selected: Profile? get() = profiles.find { it.id == selectedId } ?: profiles.firstOrNull()

    fun upsert(profile: Profile): SavedProfiles {
        val list = if (profiles.any { it.id == profile.id }) {
            profiles.map { if (it.id == profile.id) profile else it }
        } else {
            profiles + profile
        }
        return copy(profiles = list, selectedId = selectedId ?: profile.id)
    }

    fun remove(id: String): SavedProfiles {
        val list = profiles.filterNot { it.id == id }
        return copy(profiles = list, selectedId = selectedId.takeIf { it != id } ?: list.firstOrNull()?.id)
    }

    /** The widget's "next profile" button: the one after the selected, wrapping around. */
    fun selectNext(): SavedProfiles {
        if (profiles.isEmpty()) return this
        val i = profiles.indexOfFirst { it.id == selected?.id }
        return copy(selectedId = profiles[(i + 1) % profiles.size].id)
    }

    fun select(id: String): SavedProfiles = if (profiles.any { it.id == id }) copy(selectedId = id) else this

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** A damaged file gives an empty list rather than a crash at start. */
        fun decode(text: String): SavedProfiles =
            runCatching { json.decodeFromString(serializer(), text) }.getOrDefault(SavedProfiles())
    }
}
