package io.github.halilkhrmn.dpimech.engine

import android.content.Context
import android.content.Intent
import android.net.VpnService

/**
 * [VpnService.prepare] with a log line. Once the user has allowed DPIMech, Android hands the VPN to
 * the app that calls prepare() and stops any other VPN app on the spot (DuckDuckGo's App Tracking
 * Protection, issue #38), even if DPIMech then starts nothing. So it is called only when the user
 * turns DPIMech on (button, tile, widget, shortcut, start on boot), and every call is logged.
 */
object VpnPermission {
    private const val PREFS = "vpn_permission"
    private const val GRANTED = "granted"

    /** Null when DPIMech may start its VPN; otherwise the system's consent screen. */
    fun ask(context: Context, why: String): Intent? {
        val consent = VpnService.prepare(context)
        EngineLog.add("VPN permission ($why): ${if (consent == null) "granted" else "must ask"}")
        remember(context, consent == null)
        return consent
    }

    /** After the system's consent screen, with its answer. */
    fun remember(context: Context, granted: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(GRANTED, granted).apply()
    }

    /** The last known answer, without asking Android (asking stops other VPN apps). */
    fun lastKnown(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(GRANTED, false)
}
