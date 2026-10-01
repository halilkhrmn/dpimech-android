package io.github.halilkhrmn.dpimech

import android.content.Intent
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.halilkhrmn.dpimech.core.AppMode
import io.github.halilkhrmn.dpimech.core.Profile
import io.github.halilkhrmn.dpimech.core.StrategyEntry
import io.github.halilkhrmn.dpimech.engine.BypassVpnService
import io.github.halilkhrmn.dpimech.shortcut.ShortcutActivity
import io.github.halilkhrmn.dpimech.shortcut.Shortcuts
import io.github.halilkhrmn.dpimech.widget.BypassWidget
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVpnService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetShortcutTest {
    private val app get() = ApplicationProvider.getApplicationContext<DpimechApp>()
    private val s = StrategyEntry("TLS record split", "-r 1+s")

    @Before
    fun profiles() {
        app.profiles.saved.value.profiles.forEach { app.profiles.remove(it.id) }
        app.profiles.save(Profile("d", "Discord", packs = listOf("discord"), strategy = s, apps = listOf("com.discord"), appMode = AppMode.ONLY_SELECTED))
        app.profiles.save(Profile("y", "YouTube", packs = listOf("youtube"), strategy = s, apps = listOf("com.google.android.youtube")))
        app.profiles.select("d")
    }

    /** Renders the widget's RemoteViews like a launcher would, and saves a picture of it. */
    private fun widget(name: String): View {
        val parent = FrameLayout(app)
        val view = BypassWidget.views(app).apply(app, parent)
        view.measure(View.MeasureSpec.makeMeasureSpec(1050, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(220, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1050, 220)
        val bmp = Bitmap.createBitmap(1050, 220, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File(System.getProperty("roborazzi.output.dir", "build/screenshots")).apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return view
    }

    @Test
    fun widgetShowsStateAndProfile() {
        val v = widget("widget_off")
        assertEquals(app.getString(R.string.power_off), v.findViewById<TextView>(R.id.widget_state).text.toString())
        assertEquals("Discord", v.findViewById<TextView>(R.id.widget_profile).text.toString())
    }

    @Test
    fun nextButtonSwitchesProfile() {
        BypassWidget().onReceive(app, Intent(app, BypassWidget::class.java).setAction(BypassWidget.ACTION_NEXT))
        assertEquals("y", app.profiles.saved.value.selectedId)
        assertEquals("YouTube", widget("widget_next").findViewById<TextView>(R.id.widget_profile).text.toString())
    }

    @Test
    fun longPressShortcutsFollowProfiles() {
        Shortcuts.publish(app, app.profiles.saved.value)
        val list = app.getSystemService(ShortcutManager::class.java).dynamicShortcuts
        assertEquals(listOf("profile-d", "profile-y"), list.sortedBy { it.rank }.map { it.id })
        assertEquals("Discord", list.first { it.id == "profile-d" }.shortLabel.toString())
    }

    @Test
    fun shortcutTurnsTheProfileOn() {
        ShadowVpnService.setPrepareResult(null) // VPN permission already granted
        app.profiles.select("y")
        val intent = Intent(app, ShortcutActivity::class.java).putExtra(Shortcuts.EXTRA_PROFILE, "d")
        Robolectric.buildActivity(ShortcutActivity::class.java, intent).create()
        assertEquals("d", app.profiles.saved.value.selectedId)
        val started = shadowOf(app).nextStartedService
        assertEquals(BypassVpnService.ACTION_START, started?.action)
        assertTrue(started!!.getStringExtra(BypassVpnService.EXTRA_PROFILE)!!.contains("\"id\":\"d\""))
    }

    @Test
    fun shortcutWithoutPermissionAsksInTheApp() {
        ShadowVpnService.setPrepareResult(Intent("android.net.vpn.SETTINGS"))
        val intent = Intent(app, ShortcutActivity::class.java)
            .putExtra(Shortcuts.EXTRA_PROFILE, "d").putExtra(Shortcuts.EXTRA_OPEN, "com.discord")
        Robolectric.buildActivity(ShortcutActivity::class.java, intent).create()
        val next = shadowOf(app).nextStartedActivity
        assertEquals(io.github.halilkhrmn.dpimech.ui.MainActivity::class.java.name, next.component?.className)
        assertEquals("d", next.getStringExtra(io.github.halilkhrmn.dpimech.ui.MainActivity.EXTRA_START_PROFILE))
        assertEquals("com.discord", next.getStringExtra(io.github.halilkhrmn.dpimech.ui.MainActivity.EXTRA_OPEN_APP))
        assertEquals(null, shadowOf(app).nextStartedService)
    }
}
