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
import kotlinx.coroutines.MainScope
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
        // The automatic strategy runs in the VPN service; keep what it learns with the profile.
        MainScope().launch {
            EngineEvents.strategyLearned.collect { e ->
                profiles.saved.value.profiles.find { it.id == e.profileId }
                    ?.let { profiles.save(it.withStrategy(e.strategy, e.networkKey)) }
            }
        }
    }

    /** Starts the bypass with the current settings (VPN permission must already be granted). */
    fun startBypass(context: Context, profile: Profile) {
        val s = settings.settings.value
        BypassVpnService.start(context, profile, s.dns, s.autoStrategy)
    }
}
