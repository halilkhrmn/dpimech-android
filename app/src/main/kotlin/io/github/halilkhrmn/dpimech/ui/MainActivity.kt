package io.github.halilkhrmn.dpimech.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.halilkhrmn.dpimech.DpimechApp
import io.github.halilkhrmn.dpimech.core.DomainPack
import io.github.halilkhrmn.dpimech.core.Profile
import io.github.halilkhrmn.dpimech.core.StrategyEntry
import io.github.halilkhrmn.dpimech.engine.BypassVpnService
import io.github.halilkhrmn.dpimech.engine.EngineState
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var pending: Profile? = null

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val p = pending
        pending = null
        if (result.resultCode == RESULT_OK && p != null) BypassVpnService.start(this, p)
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as DpimechApp
        setContent {
            DpimechTheme {
                val saved by app.profiles.saved.collectAsStateWithLifecycle()
                val engine by EngineState.flow.collectAsStateWithLifecycle()
                val strategies by app.strategies.options.collectAsStateWithLifecycle()
                val lab by app.lab.flow.collectAsStateWithLifecycle()
                // null: home screen; "": a new profile; otherwise the id being edited.
                var editing by rememberSaveable { mutableStateOf<String?>(null) }
                var inLab by rememberSaveable { mutableStateOf(false) }

                BackHandler(enabled = editing != null || inLab) {
                    if (editing != null) editing = null else inLab = false
                }
                val edit = editing
                if (inLab && edit == null) {
                    LabScreen(
                        state = lab,
                        options = strategies,
                        initialPacks = saved.selected?.packs.orEmpty(),
                        targetProfile = saved.selected?.name,
                        onDetectIsp = app.lab::detectIsp,
                        onStart = app.lab::start,
                        onCancel = app.lab::cancel,
                        onUse = { result, packs, start ->
                            val s = result.strategy!!
                            val entry = StrategyEntry(s.name, s.args)
                            val current = saved.selected
                            val networkKey = lab.isp?.networkKey
                            if (current != null) {
                                val updated = current.withStrategy(entry, networkKey)
                                app.profiles.save(updated)
                                inLab = false
                                // A start on a running service replaces its engine, so no stop first.
                                if (start) turnOn(updated)
                            } else {
                                // No profile yet: make one from the tested sites and open it.
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
                                inLab = false
                                // Straight on when the pack's apps are installed; otherwise pick apps first.
                                if (start && p.apps.isNotEmpty()) turnOn(p) else editing = p.id
                            }
                        },
                        onBack = { inLab = false },
                    )
                } else if (edit == null) {
                    HomeScreen(
                        saved = saved,
                        engine = engine,
                        onSelect = app.profiles::select,
                        onToggle = { p -> if (engine.isActive()) BypassVpnService.stop(this) else turnOn(p) },
                        onEdit = { editing = it },
                        onNew = { editing = "" },
                        onRefreshStrategies = { app.strategies.refresh() },
                        onLab = { inLab = true },
                    )
                } else {
                    ProfileEditor(
                        initial = saved.profiles.find { it.id == edit },
                        strategies = strategies,
                        onSave = { app.profiles.save(it); app.profiles.select(it.id); editing = null },
                        onDelete = { app.profiles.remove(it); editing = null },
                        onBack = { editing = null },
                    )
                }
            }
        }
    }

    private fun turnOn(profile: Profile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val ask = VpnService.prepare(this)
        if (ask == null) {
            BypassVpnService.start(this, profile)
        } else {
            pending = profile
            vpnPermission.launch(ask)
        }
    }
}

fun EngineState.isActive() = this is EngineState.Running || this is EngineState.Starting
