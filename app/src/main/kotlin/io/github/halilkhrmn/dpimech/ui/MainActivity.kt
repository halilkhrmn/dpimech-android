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
import io.github.halilkhrmn.dpimech.core.Profile
import io.github.halilkhrmn.dpimech.engine.BypassVpnService
import io.github.halilkhrmn.dpimech.engine.EngineState

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
                // null: home screen; "": a new profile; otherwise the id being edited.
                var editing by rememberSaveable { mutableStateOf<String?>(null) }

                BackHandler(enabled = editing != null) { editing = null }
                val edit = editing
                if (edit == null) {
                    HomeScreen(
                        saved = saved,
                        engine = engine,
                        onSelect = app.profiles::select,
                        onToggle = { p -> if (engine.isActive()) BypassVpnService.stop(this) else turnOn(p) },
                        onEdit = { editing = it },
                        onNew = { editing = "" },
                        onRefreshStrategies = { app.strategies.refresh() },
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
