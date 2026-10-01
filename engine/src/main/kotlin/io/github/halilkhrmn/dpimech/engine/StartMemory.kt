package io.github.halilkhrmn.dpimech.engine

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import io.github.halilkhrmn.dpimech.engine.BypassVpnService.Companion.ACTION_START
import io.github.halilkhrmn.dpimech.engine.BypassVpnService.Companion.EXTRA_AUTO
import io.github.halilkhrmn.dpimech.engine.BypassVpnService.Companion.EXTRA_BLOCK_QUIC
import io.github.halilkhrmn.dpimech.engine.BypassVpnService.Companion.EXTRA_DNS
import io.github.halilkhrmn.dpimech.engine.BypassVpnService.Companion.EXTRA_DOH
import io.github.halilkhrmn.dpimech.engine.BypassVpnService.Companion.EXTRA_NOTIFY_ERRORS
import io.github.halilkhrmn.dpimech.engine.BypassVpnService.Companion.EXTRA_NOTIFY_STRATEGY
import io.github.halilkhrmn.dpimech.engine.BypassVpnService.Companion.EXTRA_PROFILE

/**
 * The last start request (profile and settings), kept so that Android's always-on VPN or a
 * restart by the system can turn the bypass on again without the app.
 */
object StartMemory {
    private val BOOLEANS = listOf(EXTRA_AUTO, EXTRA_NOTIFY_STRATEGY, EXTRA_NOTIFY_ERRORS, EXTRA_DOH, EXTRA_BLOCK_QUIC)

    private fun prefs(context: Context) = context.getSharedPreferences("bypass_service", Context.MODE_PRIVATE)

    fun save(context: Context, intent: Intent) {
        val profile = intent.getStringExtra(EXTRA_PROFILE) ?: return
        prefs(context).edit {
            putString(EXTRA_PROFILE, profile)
            putString(EXTRA_DNS, intent.getStringExtra(EXTRA_DNS))
            for (k in BOOLEANS) if (intent.hasExtra(k)) putBoolean(k, intent.getBooleanExtra(k, false)) else remove(k)
        }
    }

    /** An [ACTION_START] intent for the last profile, or null when none was ever started. */
    fun load(context: Context): Intent? {
        val p = prefs(context)
        val profile = p.getString(EXTRA_PROFILE, null) ?: return null
        return Intent(context, BypassVpnService::class.java).setAction(ACTION_START).apply {
            putExtra(EXTRA_PROFILE, profile)
            p.getString(EXTRA_DNS, null)?.let { putExtra(EXTRA_DNS, it) }
            for (k in BOOLEANS) if (p.contains(k)) putExtra(k, p.getBoolean(k, false))
        }
    }
}
