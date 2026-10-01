package io.github.halilkhrmn.dpimech.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import io.github.halilkhrmn.dpimech.DpimechApp
import io.github.halilkhrmn.dpimech.engine.EngineLog

/**
 * "Start when the phone starts": turns the selected profile on after boot. Android's own
 * always-on VPN setting does the same without this switch (the service restarts the last
 * profile); this is for people who do not want to touch system settings.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as DpimechApp
        if (!app.settings.settings.value.startOnBoot) return
        val profile = app.profiles.saved.value.selected ?: return
        // Without the VPN permission only the app itself can ask for it.
        if (VpnService.prepare(context) != null) {
            EngineLog.add("boot: VPN permission missing, not starting")
            return
        }
        EngineLog.add("boot: turning on \"${profile.name}\"")
        app.startBypass(context, profile)
    }
}
