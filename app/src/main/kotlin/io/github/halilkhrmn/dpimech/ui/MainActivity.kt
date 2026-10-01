package io.github.halilkhrmn.dpimech.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.halilkhrmn.dpimech.DpimechApp
import io.github.halilkhrmn.dpimech.R
import io.github.halilkhrmn.dpimech.core.AppMode
import io.github.halilkhrmn.dpimech.core.CountryPreset
import io.github.halilkhrmn.dpimech.core.DomainPack
import io.github.halilkhrmn.dpimech.core.LabResult
import io.github.halilkhrmn.dpimech.core.Profile
import io.github.halilkhrmn.dpimech.core.StrategyEntry
import io.github.halilkhrmn.dpimech.engine.BypassVpnService
import io.github.halilkhrmn.dpimech.engine.EngineState
import java.util.UUID

private enum class Tab(val label: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Default.Home),
    TEST(R.string.tab_test, Icons.Default.Science),
    SETTINGS(R.string.tab_settings, Icons.Default.Settings),
    ABOUT(R.string.tab_about, Icons.Default.Info),
}

class MainActivity : AppCompatActivity() {
    private var pending: Profile? = null
    private val app get() = application as DpimechApp

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val p = pending
        pending = null
        if (result.resultCode == RESULT_OK && p != null) app.startBypass(this, p)
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DpimechTheme {
                val saved by app.profiles.saved.collectAsStateWithLifecycle()
                val engine by EngineState.flow.collectAsStateWithLifecycle()
                val strategies by app.strategies.options.collectAsStateWithLifecycle()
                val lab by app.lab.flow.collectAsStateWithLifecycle()
                val settings by app.settings.settings.collectAsStateWithLifecycle()
                var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
                // null: no editor; "": a new profile; otherwise the id being edited.
                var editing by rememberSaveable { mutableStateOf<String?>(null) }
                var wizard by rememberSaveable { mutableStateOf(!settings.wizardDone && saved.profiles.isEmpty()) }

                BackHandler(enabled = editing != null || tab != Tab.HOME) {
                    if (editing != null) editing = null else tab = Tab.HOME
                }

                val edit = editing
                when {
                    wizard -> WizardScreen(
                        lab = lab,
                        country = lab.isp?.country,
                        onDetectIsp = app.lab::detectIsp,
                        onTest = { packs, community ->
                            app.lab.start(
                                DomainPack.ALL.filter { it.id in packs },
                                strategies.filter { community || it.source == LabResult.STANDARD_SET },
                            )
                        },
                        onCancelTest = app.lab::cancel,
                        onDone = { choice ->
                            app.settings.update { it.copy(wizardDone = true) }
                            wizard = false
                            tab = Tab.HOME
                            val p = profileFromWizard(choice, lab.isp?.country, lab.isp?.networkKey)
                            app.profiles.save(p)
                            app.profiles.select(p.id)
                            when {
                                p.appMode == AppMode.ONLY_SELECTED && p.apps.isEmpty() -> editing = p.id
                                choice.turnOn -> turnOn(p)
                            }
                        },
                        onSkip = {
                            app.settings.update { it.copy(wizardDone = true) }
                            wizard = false
                        },
                    )
                    edit != null -> ProfileEditor(
                        initial = saved.profiles.find { it.id == edit },
                        strategies = strategies,
                        onSave = { p ->
                            app.profiles.save(p)
                            app.profiles.select(p.id)
                            editing = null
                            // Changes to the running profile apply at once.
                            if ((engine as? EngineState.Running)?.profileId == p.id) turnOn(p)
                        },
                        onDelete = { app.profiles.remove(it); editing = null },
                        onBack = { editing = null },
                    )
                    else -> Scaffold(
                        bottomBar = {
                            NavigationBar {
                                Tab.entries.forEach { t ->
                                    NavigationBarItem(
                                        selected = tab == t,
                                        onClick = { tab = t },
                                        icon = { Icon(t.icon, null) },
                                        label = { Text(stringResource(t.label)) },
                                    )
                                }
                            }
                        },
                    ) { padding -> Content(tab, padding, onEdit = { editing = it }, onWizard = { wizard = true }) }
                }
            }
        }
    }

    @Composable
    private fun Content(tab: Tab, padding: PaddingValues, onEdit: (String) -> Unit, onWizard: () -> Unit) {
        val saved by app.profiles.saved.collectAsStateWithLifecycle()
        val engine by EngineState.flow.collectAsStateWithLifecycle()
        val strategies by app.strategies.options.collectAsStateWithLifecycle()
        val lab by app.lab.flow.collectAsStateWithLifecycle()
        val settings by app.settings.settings.collectAsStateWithLifecycle()
        when (tab) {
            Tab.HOME -> HomeScreen(
                saved = saved,
                engine = engine,
                onSelect = { id ->
                    app.profiles.select(id)
                    // Switching profiles while on moves the bypass to the new one.
                    if (engine.isActive()) saved.profiles.find { it.id == id }?.let(::turnOn)
                },
                onToggle = { p -> if (engine.isActive()) BypassVpnService.stop(this) else turnOn(p) },
                onEdit = onEdit,
                onNew = { onEdit("") },
                onWizard = onWizard,
                bottomPadding = padding,
            )
            Tab.TEST -> LabScreen(
                state = lab,
                options = strategies,
                initialPacks = saved.selected?.packs.orEmpty(),
                targetProfile = saved.selected?.name,
                onDetectIsp = app.lab::detectIsp,
                onStart = app.lab::start,
                onCancel = app.lab::cancel,
                onUse = { result, packs, start -> useLabResult(result, packs, start, lab.isp?.networkKey, onEdit) },
                onBack = null,
                bottomPadding = padding,
            )
            Tab.SETTINGS -> SettingsScreen(
                settings = settings,
                onLanguage = { tag ->
                    app.settings.update { it.copy(language = tag) }
                    AppCompatDelegate.setApplicationLocales(
                        if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
                    )
                },
                onChange = { change ->
                    app.settings.update(change)
                    // DNS and the automatic strategy are read at start: apply them to a running bypass.
                    if (engine is EngineState.Running) saved.selected?.let(::turnOn)
                },
                onWizard = onWizard,
                onRefreshStrategies = { app.strategies.refresh() },
                bottomPadding = padding,
            )
            Tab.ABOUT -> AboutScreen(
                versionName = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty(),
                bottomPadding = padding,
            )
        }
    }

    private fun useLabResult(result: LabResult, packs: List<String>, start: Boolean, networkKey: String?, onEdit: (String) -> Unit) {
        val s = result.strategy!!
        val entry = StrategyEntry(s.name, s.args)
        val current = app.profiles.saved.value.selected
        if (current != null) {
            val updated = current.withStrategy(entry, networkKey)
            app.profiles.save(updated)
            // A start on a running service replaces its engine, so no stop first.
            if (start || EngineState.flow.value is EngineState.Running) turnOn(updated)
            return
        }
        // No profile yet: make one from the tested sites.
        val installed = InstalledApps.packageNames(packageManager)
        val chosen = DomainPack.ALL.filter { it.id in packs }
        val p = Profile(
            id = UUID.randomUUID().toString(),
            name = chosen.joinToString(" + ") { it.name },
            packs = chosen.map { it.id },
            strategy = entry,
            perNetwork = networkKey?.let { mapOf(it to entry) }.orEmpty(),
            apps = chosen.flatMap { it.packages }.filter { it in installed },
        )
        app.profiles.save(p)
        app.profiles.select(p.id)
        if (start && p.apps.isNotEmpty()) turnOn(p) else onEdit(p.id)
    }

    private fun profileFromWizard(choice: WizardChoice, country: String?, networkKey: String?): Profile {
        val entry = choice.best?.strategy?.let { StrategyEntry(it.name, it.args) }
            ?: app.strategies.options.value.first().entry
        val id = UUID.randomUUID().toString()
        val base = if (choice.wholePhone) {
            CountryPreset(country.orEmpty(), choice.packs).profile(id, getString(R.string.wizard_profile_whole_phone), entry)
        } else {
            val installed = InstalledApps.packageNames(packageManager)
            val chosen = DomainPack.ALL.filter { it.id in choice.packs }
            Profile(
                id = id,
                name = chosen.joinToString(" + ") { it.name },
                packs = choice.packs,
                strategy = entry,
                apps = chosen.flatMap { it.packages }.filter { it in installed },
            )
        }
        return if (choice.best != null) base.withStrategy(entry, networkKey) else base
    }

    private fun turnOn(profile: Profile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val ask = VpnService.prepare(this)
        if (ask == null) {
            app.startBypass(this, profile)
        } else {
            pending = profile
            vpnPermission.launch(ask)
        }
    }
}

fun EngineState.isActive() = this is EngineState.Running || this is EngineState.Starting
