package io.github.halilkhrmn.dpimech.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.RemoteViews
import io.github.halilkhrmn.dpimech.DpimechApp
import io.github.halilkhrmn.dpimech.R
import io.github.halilkhrmn.dpimech.engine.BypassVpnService
import io.github.halilkhrmn.dpimech.engine.EngineLog
import io.github.halilkhrmn.dpimech.engine.EngineState
import io.github.halilkhrmn.dpimech.ui.MainActivity
import io.github.halilkhrmn.dpimech.ui.isActive

/**
 * Home-screen widget: the on/off state at a glance (green ON / grey OFF, like the app), the
 * selected profile and a button to switch to the next profile. Redrawn by [DpimechApp] whenever
 * the engine state or the profiles change.
 */
class BypassWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val app = context.applicationContext as DpimechApp
        when (intent.action) {
            ACTION_TOGGLE -> {
                if (EngineState.flow.value.isActive()) {
                    BypassVpnService.stop(context)
                } else {
                    app.profiles.saved.value.selected?.let {
                        EngineLog.add("widget: turning on \"${it.name}\"")
                        app.startBypass(context, it)
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
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, views(context))
        }

        fun views(context: Context): RemoteViews {
            val app = context.applicationContext as DpimechApp
            val engine = EngineState.flow.value
            val selected = app.profiles.saved.value.selected
            val on = engine is EngineState.Running
            val v = RemoteViews(context.packageName, R.layout.widget_bypass)
            v.setInt(R.id.widget_power, "setBackgroundResource", if (on) R.drawable.widget_power_on else R.drawable.widget_power_off)
            v.setTextViewText(
                R.id.widget_state,
                context.getString(
                    when (engine) {
                        is EngineState.Running -> R.string.power_on
                        is EngineState.Starting -> R.string.status_starting
                        is EngineState.Failed -> R.string.status_failed
                        EngineState.Stopped -> R.string.power_off
                    },
                ),
            )
            v.setTextViewText(R.id.widget_profile, selected?.name ?: context.getString(R.string.widget_no_profile))

            val openApp = PendingIntent.getActivity(
                context, 10, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            // Without a profile or the VPN permission the app has to ask first: open it instead.
            val canToggle = engine.isActive() || (selected != null && VpnService.prepare(context) == null)
            v.setOnClickPendingIntent(R.id.widget_power, if (canToggle) broadcast(context, ACTION_TOGGLE, 11) else openApp)
            v.setOnClickPendingIntent(R.id.widget_next, broadcast(context, ACTION_NEXT, 12))
            v.setOnClickPendingIntent(R.id.widget_root, openApp)
            return v
        }

        private fun broadcast(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
            context, code, Intent(context, BypassWidget::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
