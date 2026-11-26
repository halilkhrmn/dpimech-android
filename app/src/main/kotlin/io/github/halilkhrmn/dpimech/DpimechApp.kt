package io.github.halilkhrmn.dpimech

import android.app.Application
import android.content.Context
import android.os.Build
import io.github.halilkhrmn.dpimech.engine.EngineLog
import io.github.halilkhrmn.dpimech.core.Profile
import io.github.halilkhrmn.dpimech.data.LabController
import io.github.halilkhrmn.dpimech.data.ProfileRepository
import io.github.halilkhrmn.dpimech.data.SettingsRepository
import io.github.halilkhrmn.dpimech.data.StrategyRepository
import io.github.halilkhrmn.dpimech.core.BlockListCatalog
import io.github.halilkhrmn.dpimech.engine.AdblockStore
import io.github.halilkhrmn.dpimech.engine.BypassVpnService
import io.github.halilkhrmn.dpimech.engine.CrashRecorder
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
        // First: the log file and the crash handler, so the next report shows how a crash ended.
        CrashRecorder.install(filesDir)
        // The block list catalog comes from main; a debug build made by CI reads its own branch's.
        AdblockStore.init(filesDir, BlockListCatalog.url(BuildConfig.CATALOG_BRANCH))
        profiles = ProfileRepository(filesDir)
        strategies = StrategyRepository(filesDir)
        settings = SettingsRepository(filesDir)
        lab = LabController(this)
        NetworkIdentity.start(this)
        val scope = MainScope()
        // The bypass notification offers "next profile" when there is another profile.
        BypassVpnService.nextProfileAction = { ctx ->
            if (profiles.saved.value.profiles.size > 1) BypassWidget.nextProfileIntent(ctx) else null
        }
        updateListsIfOld(scope)
        // ipwho.is sees the IP address: automatic lookups only with the automatic strategy, after
        // the wizard; tests the user starts may look up unless the setting is off.
        scope.launch {
            settings.settings
                .map { (it.providerLookup && it.autoStrategy && it.wizardDone) to it.providerLookup }
                .distinctUntilChanged()
                .collect { (auto, allowed) -> NetworkIdentity.configure(auto, allowed) }
        }
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

    /** Strategy lists older than [LIST_MAX_AGE_MS] are downloaded again in the background. */
    private fun updateListsIfOld(scope: kotlinx.coroutines.CoroutineScope) {
        if (!settings.settings.value.autoUpdateLists || Build.FINGERPRINT == "robolectric") return
        // Block lists used by a profile with ad blocking, a week after their last download.
        val now = System.currentTimeMillis()
        val used = profiles.saved.value.profiles.filter { it.adblock }.flatMap { it.blockLists }.distinct()
        if (used.isNotEmpty()) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                if (now - AdblockStore.catalogUpdated > AdblockStore.MAX_AGE_MS) AdblockStore.refreshCatalog()
                used.filter { id -> AdblockStore.status.value[id]?.let { now - it.updated > AdblockStore.MAX_AGE_MS } == true }
                    .mapNotNull(AdblockStore::list)
                    .forEach(AdblockStore::download)
            }
        }
        val last = strategies.lastUpdated.value
        if (last != null && System.currentTimeMillis() - last < LIST_MAX_AGE_MS) return
        scope.launch {
            val errors = strategies.refresh()
            EngineLog.add(if (errors.isEmpty()) "strategy lists updated" else "strategy list update failed: ${errors.joinToString("; ")}")
        }
    }

    /** Starts the bypass with the current settings (VPN permission must already be granted). */
    fun startBypass(context: Context, profile: Profile) {
        val s = settings.settings.value
        BypassVpnService.start(context, profile, s.dns, s.autoStrategy, s.notifyStrategy, s.notifyErrors, s.encryptedDns, s.blockQuic)
    }

    private companion object {
        const val LIST_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
