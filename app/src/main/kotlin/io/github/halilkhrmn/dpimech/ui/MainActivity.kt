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
import androidx.compose.runtime.remember
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
import io.github.halilkhrmn.dpimech.core.ProblemReport
import io.github.halilkhrmn.dpimech.core.Profile
import io.github.halilkhrmn.dpimech.core.StrategyEntry
import io.github.halilkhrmn.dpimech.engine.BypassVpnService
import io.github.halilkhrmn.dpimech.engine.EngineLog
import io.github.halilkhrmn.dpimech.engine.EngineState
import io.github.halilkhrmn.dpimech.engine.NetworkIdentity
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
            val themeSettings by app.settings.settings.collectAsStateWithLifecycle()
            DpimechTheme(dynamicColor = themeSettings.dynamicColor) {
                val saved by app.profiles.saved.collectAsStateWithLifecycle()
                val engine by EngineState.flow.collectAsStateWithLifecycle()
                val strategies by app.strategies.options.collectAsStateWithLifecycle()
                val lab by app.lab.flow.collectAsStateWithLifecycle()
                val settings by app.settings.settings.collectAsStateWithLifecycle()
                var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
                // null: no editor; "": a new profile; otherwise the id being edited.
                var editing by rememberSaveable { mutableStateOf<String?>(null) }
                var wizard by rememberSaveable { mutableStateOf(!settings.wizardDone && saved.profiles.isEmpty()) }
                var logs by rememberSaveable { mutableStateOf(false) }
                var report by remember { mutableStateOf<ProblemReport?>(null) }
                val logLines by EngineLog.flow.collectAsStateWithLifecycle()
                val network by NetworkIdentity.flow.collectAsStateWithLifecycle()
                val openReport = { report = buildReport() }

                BackHandler(enabled = editing != null || logs || tab != Tab.HOME) {
                    when {
                        editing != null -> editing = null
                        logs -> logs = false
                        else -> tab = Tab.HOME
                    }
                }
                report?.let { ReportDialog(it, onDismiss = { report = null }) }

                val edit = editing
                when {
                    wizard -> WizardScreen(
                        lab = lab,
                        country = network?.country ?: lab.isp?.country ?: simCountry(),
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
                            val p = profileFromWizard(choice, network?.country ?: lab.isp?.country, network?.networkKey ?: lab.isp?.networkKey)
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
                    logs -> LogsScreen(logLines, onClear = EngineLog::clear, onReport = openReport, onBack = { logs = false })
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
                    ) { padding ->
                        Content(tab, padding, onEdit = { editing = it }, onWizard = { wizard = true }, onLogs = { logs = true }, onReport = openReport)
                    }
                }
            }
        }
    }

    @Composable
    private fun Content(
        tab: Tab,
        padding: PaddingValues,
        onEdit: (String) -> Unit,
        onWizard: () -> Unit,
        onLogs: () -> Unit,
        onReport: () -> Unit,
    ) {
        val saved by app.profiles.saved.collectAsStateWithLifecycle()
        val engine by EngineState.flow.collectAsStateWithLifecycle()
        val strategies by app.strategies.options.collectAsStateWithLifecycle()
        val lab by app.lab.flow.collectAsStateWithLifecycle()
        val settings by app.settings.settings.collectAsStateWithLifecycle()
        val network by NetworkIdentity.flow.collectAsStateWithLifecycle()
        val lastUpdated by app.strategies.lastUpdated.collectAsStateWithLifecycle()
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
                network = network,
                autoStrategy = settings.autoStrategy,
            )
            Tab.TEST -> LabScreen(
                state = lab,
                options = strategies,
                initialPacks = saved.selected?.packs.orEmpty(),
                targetProfile = saved.selected?.name,
                onDetectIsp = app.lab::detectIsp,
                onStart = app.lab::start,
                onCancel = app.lab::cancel,
                onUse = { result, packs, start -> useLabResult(result, packs, start, network?.networkKey ?: lab.isp?.networkKey, onEdit) },
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
                    val before = app.settings.settings.value
                    app.settings.update(change)
                    val after = app.settings.settings.value
                    // DNS and the automatic strategy are read at start: apply them to a running bypass.
                    val engineSettingChanged = before.dns != after.dns || before.autoStrategy != after.autoStrategy
                    if (engineSettingChanged && engine is EngineState.Running) saved.selected?.let(::turnOn)
                },
                onWizard = onWizard,
                onRefreshStrategies = { app.strategies.refresh() },
                lastUpdated = lastUpdated,
                onLogs = onLogs,
                onReport = onReport,
                bottomPadding = padding,
            )
            Tab.ABOUT -> AboutScreen(
                versionName = versionName(),
                onReport = onReport,
                onLogs = onLogs,
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

    /** The SIM's country when nothing better is known (no permission needed). */
    private fun simCountry(): String? = runCatching {
        getSystemService(android.telephony.TelephonyManager::class.java)?.simCountryIso?.uppercase()?.ifEmpty { null }
    }.getOrNull()

    private fun versionName() = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()

    private fun buildReport(): ProblemReport {
        val engine = EngineState.flow.value
        val state = when (engine) {
            is EngineState.Running -> "on (${engine.profileName}, ${engine.strategyName})"
            is EngineState.Starting -> "starting"
            is EngineState.Failed -> "failed: ${engine.message}"
            EngineState.Stopped -> "off"
        }
        return ProblemReport.build(
            appVersion = versionName(),
            device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            settings = app.settings.settings.value,
            profiles = app.profiles.saved.value,
            engineState = state,
            isp = NetworkIdentity.flow.value?.isp ?: app.lab.flow.value.isp,
            log = EngineLog.snapshot().takeLast(300),
        )
    }

    private fun turnOn(profile: Profile) {
        EngineLog.add("turning on profile \"${profile.name}\" (${profile.strategy.name})")
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
