package io.github.halilkhrmn.dpimech

import android.app.Application
import android.content.Context
import io.github.halilkhrmn.dpimech.core.Profile
import io.github.halilkhrmn.dpimech.data.LabController
import io.github.halilkhrmn.dpimech.data.ProfileRepository
import io.github.halilkhrmn.dpimech.data.SettingsRepository
import io.github.halilkhrmn.dpimech.data.StrategyRepository
import io.github.halilkhrmn.dpimech.engine.BypassVpnService
import io.github.halilkhrmn.dpimech.engine.EngineEvents
import io.github.halilkhrmn.dpimech.engine.EngineState
import io.github.halilkhrmn.dpimech.shortcut.Shortcuts
import io.github.halilkhrmn.dpimech.widget.BypassWidget
import io.github.halilkhrmn.dpimech.engine.NetworkIdentity
import io.github.halilkhrmn.dpimech.ui.isActive
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DpimechApp : Application() {
    lateinit var profiles: ProfileRepository
        private set
    lateinit var strategies: StrategyRepository
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var lab: LabController
        private set

    override fun onCreate() {
        super.onCreate()
        profiles = ProfileRepository(filesDir)
        strategies = StrategyRepository(filesDir)
        settings = SettingsRepository(filesDir)
        lab = LabController(this)
        NetworkIdentity.start(this)
        val scope = MainScope()
        // Widget and launcher shortcuts follow the engine and the profiles.
        scope.launch { EngineState.flow.collect { BypassWidget.render(this@DpimechApp) } }
        scope.launch { profiles.saved.collect { BypassWidget.render(this@DpimechApp) } }
        scope.launch {
            combine(profiles.saved, EngineState.flow.map { it.isActive() }.distinctUntilChanged(), ::Pair)
                .collect { (saved, running) -> Shortcuts.publish(this@DpimechApp, saved, running) }
        }
        // The automatic strategy runs in the VPN service; keep what it learns with the profile.
        scope.launch {
            EngineEvents.strategyLearned.collect { e ->
                profiles.saved.value.profiles.find { it.id == e.profileId }
                    ?.let { profiles.save(it.withStrategy(e.strategy, e.networkKey)) }
            }
        }
    }

    /** Starts the bypass with the current settings (VPN permission must already be granted). */
    fun startBypass(context: Context, profile: Profile) {
        val s = settings.settings.value
        BypassVpnService.start(context, profile, s.dns, s.autoStrategy, s.notifyStrategy, s.notifyErrors, s.encryptedDns, s.blockQuic)
    }
}
