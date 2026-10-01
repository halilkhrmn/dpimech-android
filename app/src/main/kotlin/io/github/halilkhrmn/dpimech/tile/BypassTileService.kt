package io.github.halilkhrmn.dpimech.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.halilkhrmn.dpimech.DpimechApp
import io.github.halilkhrmn.dpimech.R
import io.github.halilkhrmn.dpimech.engine.BypassVpnService
import io.github.halilkhrmn.dpimech.engine.EngineState
import io.github.halilkhrmn.dpimech.ui.MainActivity
import io.github.halilkhrmn.dpimech.ui.isActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick Settings tile: turns the selected profile on or off from the notification shade. */
class BypassTileService : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        val s = CoroutineScope(Dispatchers.Main + Job()).also { scope = it }
        s.launch { EngineState.flow.collect { render(it) } }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
    }

    override fun onClick() {
        if (EngineState.flow.value.isActive()) {
            BypassVpnService.stop(this)
            return
        }
        val profile = (application as DpimechApp).profiles.saved.value.selected
        if (profile == null || VpnService.prepare(this) != null) {
            // No profile yet, or the VPN permission must be granted in the app first.
            openApp()
            return
        }
        BypassVpnService.start(this, profile)
    }

    private fun render(state: EngineState) {
        val tile = qsTile ?: return
        tile.state = when (state) {
            is EngineState.Running, is EngineState.Starting -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (state) {
                is EngineState.Running -> state.profileName
                is EngineState.Failed -> getString(R.string.status_failed)
                else -> (application as DpimechApp).profiles.saved.value.selected?.name
            }
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated") // the Intent overload is the only one before API 34
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
