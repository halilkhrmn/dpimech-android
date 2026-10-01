package io.github.halilkhrmn.dpimech.data

import io.github.halilkhrmn.dpimech.core.Profile
import io.github.halilkhrmn.dpimech.core.SavedProfiles
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Profiles in `files/profiles.json`, written atomically on every change. */
class ProfileRepository(dir: File) {
    private val file = File(dir, "profiles.json")
    private val state = MutableStateFlow(if (file.exists()) SavedProfiles.decode(file.readText()) else SavedProfiles())
    val saved: StateFlow<SavedProfiles> = state.asStateFlow()

    fun save(profile: Profile) = update { it.upsert(profile) }
    fun remove(id: String) = update { it.remove(id) }
    fun select(id: String) = update { it.select(id) }

    @Synchronized
    private fun update(change: (SavedProfiles) -> SavedProfiles) {
        val next = change(state.value)
        val tmp = File(file.path + ".tmp")
        tmp.writeText(next.encode())
        tmp.renameTo(file)
        state.value = next
    }
}
