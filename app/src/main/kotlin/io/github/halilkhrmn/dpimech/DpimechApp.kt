package io.github.halilkhrmn.dpimech

import android.app.Application
import io.github.halilkhrmn.dpimech.data.LabController
import io.github.halilkhrmn.dpimech.data.ProfileRepository
import io.github.halilkhrmn.dpimech.data.StrategyRepository

class DpimechApp : Application() {
    lateinit var profiles: ProfileRepository
        private set
    lateinit var strategies: StrategyRepository
        private set
    lateinit var lab: LabController
        private set

    override fun onCreate() {
        super.onCreate()
        profiles = ProfileRepository(filesDir)
        strategies = StrategyRepository(filesDir)
        lab = LabController(this)
    }
}
