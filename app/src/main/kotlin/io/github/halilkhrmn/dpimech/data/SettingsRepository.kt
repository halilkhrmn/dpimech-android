package io.github.halilkhrmn.dpimech.data

import io.github.halilkhrmn.dpimech.core.AppSettings
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** [AppSettings] in `files/settings.json`, written atomically on every change. */
class SettingsRepository(dir: File) {
    private val file = File(dir, "settings.json")
    private val state = MutableStateFlow(if (file.exists()) AppSettings.decode(file.readText()) else AppSettings())
    val settings: StateFlow<AppSettings> = state.asStateFlow()

    @Synchronized
    fun update(change: (AppSettings) -> AppSettings) {
        val next = change(state.value)
        val tmp = File(file.path + ".tmp")
        tmp.writeText(next.encode())
        tmp.renameTo(file)
        state.value = next
    }
}
