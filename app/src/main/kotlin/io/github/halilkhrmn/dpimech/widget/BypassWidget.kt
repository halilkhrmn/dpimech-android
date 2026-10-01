package io.github.halilkhrmn.dpimech.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.edit
import io.github.halilkhrmn.dpimech.DpimechApp
import io.github.halilkhrmn.dpimech.R
import io.github.halilkhrmn.dpimech.core.Profile
import io.github.halilkhrmn.dpimech.engine.BypassVpnService
import io.github.halilkhrmn.dpimech.engine.EngineLog
import io.github.halilkhrmn.dpimech.engine.EngineState
import io.github.halilkhrmn.dpimech.ui.MainActivity
import io.github.halilkhrmn.dpimech.ui.isActive

/**
 * Home-screen widget: the on/off state at a glance (green ON / grey OFF, like the app). Each
 * widget either follows the selected profile (with a button to switch to the next one) or is
 * bound to one profile in [WidgetConfigActivity], so a "Discord" widget turns Discord's
 * profile on and off. Redrawn by [DpimechApp] whenever the engine state or the profiles change.
 */
class BypassWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context)

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetPrefs.unbind(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val app = context.applicationContext as DpimechApp
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        when (intent.action) {
            ACTION_TOGGLE -> {
                val engine = EngineState.flow.value
                val target = profileFor(context, widgetId)
                val runningId = (engine as? EngineState.Running)?.profileId ?: (engine as? EngineState.Starting)?.profileId
                when {
                    // On with this widget's profile (or a following widget): turn off.
                    engine.isActive() && (target == null || runningId == target.id || WidgetPrefs.boundProfile(context, widgetId) == null) ->
                        BypassVpnService.stop(context)
                    target != null -> {
                        EngineLog.add("widget: turning on \"${target.name}\"")
                        app.profiles.select(target.id)
                        app.startBypass(context, target)
                    }
                }
            }
            ACTION_NEXT -> {
                app.profiles.selectNext()
                val next = app.profiles.saved.value.selected
                // Switching while on moves the bypass to the new profile.
                if (next != null && EngineState.flow.value.isActive() && VpnService.prepare(context) == null) {
                    app.startBypass(context, next)
                }
                render(context)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "io.github.halilkhrmn.dpimech.widget.TOGGLE"
        const val ACTION_NEXT = "io.github.halilkhrmn.dpimech.widget.NEXT"

        fun render(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, BypassWidget::class.java))
            ids.forEach { manager.updateAppWidget(it, views(context, it)) }
        }

        /** The bound profile, or the selected one for a widget that follows the app. */
        fun profileFor(context: Context, widgetId: Int): Profile? {
            val saved = (context.applicationContext as DpimechApp).profiles.saved.value
            val bound = WidgetPrefs.boundProfile(context, widgetId)
            return if (bound != null) saved.profiles.find { it.id == bound } else saved.selected
        }

        fun views(context: Context, widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID): RemoteViews {
            val engine = EngineState.flow.value
            val bound = WidgetPrefs.boundProfile(context, widgetId)
            val profile = profileFor(context, widgetId)
            val runningId = (engine as? EngineState.Running)?.profileId
            // A bound widget shows ON only for its own profile.
            val on = engine is EngineState.Running && (bound == null || runningId == bound)
            val v = RemoteViews(context.packageName, R.layout.widget_bypass)
            v.setInt(R.id.widget_power, "setBackgroundResource", if (on) R.drawable.widget_power_on else R.drawable.widget_power_off)
            v.setTextViewText(
                R.id.widget_state,
                context.getString(
                    when {
                        on -> R.string.power_on
                        engine is EngineState.Starting && (bound == null || engine.profileId == bound) -> R.string.status_starting
                        engine is EngineState.Failed && bound == null -> R.string.status_failed
                        else -> R.string.power_off
                    },
                ),
            )
            v.setTextViewText(
                R.id.widget_profile,
                when {
                    profile != null -> profile.name
                    bound != null -> context.getString(R.string.widget_profile_gone)
                    else -> context.getString(R.string.widget_no_profile)
                },
            )

            val openApp = PendingIntent.getActivity(
                context, 10, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            // Without a profile or the VPN permission the app has to ask first: open it instead.
            val canToggle = on || (profile != null && VpnService.prepare(context) == null)
            v.setOnClickPendingIntent(R.id.widget_power, if (canToggle) broadcast(context, ACTION_TOGGLE, widgetId) else openApp)
            // Only a widget that follows the selected profile can switch profiles.
            v.setViewVisibility(R.id.widget_next, if (bound == null) View.VISIBLE else View.GONE)
            v.setOnClickPendingIntent(R.id.widget_next, broadcast(context, ACTION_NEXT, widgetId))
            v.setOnClickPendingIntent(R.id.widget_root, openApp)
            return v
        }

        /** "Next profile", as the widget's arrow does; for the ongoing notification. */
        fun nextProfileIntent(context: Context): PendingIntent = broadcast(context, ACTION_NEXT, AppWidgetManager.INVALID_APPWIDGET_ID)

        private fun broadcast(context: Context, action: String, widgetId: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            // One request code per widget and action, so the widgets' intents stay apart.
            (widgetId.coerceAtLeast(0) * 2 + if (action == ACTION_TOGGLE) 11 else 12),
            Intent(context, BypassWidget::class.java).setAction(action).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** Which profile each widget is bound to; no entry = follows the selected profile. */
object WidgetPrefs {
    private fun prefs(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun boundProfile(context: Context, widgetId: Int): String? =
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) null else prefs(context).getString("profile_$widgetId", null)

    fun bind(context: Context, widgetId: Int, profileId: String?) {
        prefs(context).edit {
            if (profileId == null) remove("profile_$widgetId") else putString("profile_$widgetId", profileId)
        }
    }

    fun unbind(context: Context, widgetId: Int) = bind(context, widgetId, null)
}
