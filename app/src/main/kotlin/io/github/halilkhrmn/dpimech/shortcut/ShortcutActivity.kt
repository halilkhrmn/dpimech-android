package io.github.halilkhrmn.dpimech.shortcut

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.halilkhrmn.dpimech.DpimechApp
import io.github.halilkhrmn.dpimech.engine.EngineLog
import io.github.halilkhrmn.dpimech.engine.EngineState
import io.github.halilkhrmn.dpimech.ui.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Invisible: turns the shortcut's profile on, waits until the bypass runs (a few seconds at
 * most) and then opens the app, so its first connection already goes through ByeDPI. When the
 * VPN permission is still missing, the main screen takes over and asks for it.
 */
class ShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DpimechApp
        val profile = app.profiles.saved.value.profiles.find { it.id == intent.getStringExtra(Shortcuts.EXTRA_PROFILE) }
        val open = intent.getStringExtra(Shortcuts.EXTRA_OPEN)
        if (profile == null || VpnService.prepare(this) != null) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_START_PROFILE, profile?.id)
                    .putExtra(MainActivity.EXTRA_OPEN_APP, open)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            finish()
            return
        }
        app.profiles.select(profile.id)
        val running = EngineState.flow.value as? EngineState.Running
        if (running?.profileId != profile.id) {
            EngineLog.add("shortcut: turning on \"${profile.name}\"")
            app.startBypass(this, profile)
        }
        lifecycleScope.launch {
            withTimeoutOrNull(6000) {
                EngineState.flow.first { it is EngineState.Running && it.profileId == profile.id || it is EngineState.Failed }
            }
            open?.let { pkg -> packageManager.getLaunchIntentForPackage(pkg)?.let { runCatching { startActivity(it) } } }
            finish()
        }
    }
}
