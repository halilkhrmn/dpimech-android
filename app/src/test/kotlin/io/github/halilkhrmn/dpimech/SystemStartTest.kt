package io.github.halilkhrmn.dpimech

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.github.halilkhrmn.dpimech.boot.BootReceiver
import io.github.halilkhrmn.dpimech.core.Profile
import io.github.halilkhrmn.dpimech.core.StrategyEntry
import io.github.halilkhrmn.dpimech.engine.BypassVpnService
import io.github.halilkhrmn.dpimech.engine.StartMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVpnService

/** Start on boot, Android's always-on VPN and the "next profile" notification button. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemStartTest {
    private val app get() = ApplicationProvider.getApplicationContext<DpimechApp>()

    @Before
    fun profile() {
        app.profiles.saved.value.profiles.forEach { app.profiles.remove(it.id) }
        app.profiles.save(Profile("w", "Blocked sites", strategy = StrategyEntry("s", "-s1")))
        app.profiles.select("w")
        ShadowVpnService.setPrepareResult(null)
    }

    private fun boot() = BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

    @Test
    fun bootStartsOnlyWhenSwitchedOn() {
        app.settings.update { it.copy(startOnBoot = false) }
        boot()
        assertNull(shadowOf(app).nextStartedService)

        app.settings.update { it.copy(startOnBoot = true) }
        boot()
        val started = shadowOf(app).nextStartedService
        assertEquals(BypassVpnService.ACTION_START, started?.action)
        assertTrue(started!!.getStringExtra(BypassVpnService.EXTRA_PROFILE)!!.contains("\"id\":\"w\""))
    }

    @Test
    fun bootWithoutVpnPermissionDoesNothing() {
        app.settings.update { it.copy(startOnBoot = true) }
        ShadowVpnService.setPrepareResult(Intent("android.net.vpn.SETTINGS"))
        boot()
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test
    fun lastStartIsRememberedForAlwaysOn() {
        assertNull(StartMemory.load(app.applicationContext.also { it.getSharedPreferences("bypass_service", 0).edit().clear().commit() }))
        val first = Intent(app, BypassVpnService::class.java).setAction(BypassVpnService.ACTION_START)
            .putExtra(BypassVpnService.EXTRA_PROFILE, "{\"id\":\"w\"}")
            .putExtra(BypassVpnService.EXTRA_DNS, "9.9.9.9")
            .putExtra(BypassVpnService.EXTRA_BLOCK_QUIC, true)
            .putExtra(BypassVpnService.EXTRA_DOH, false)
        StartMemory.save(app, first)
        val again = StartMemory.load(app)!!
        assertEquals(BypassVpnService.ACTION_START, again.action)
        assertEquals("{\"id\":\"w\"}", again.getStringExtra(BypassVpnService.EXTRA_PROFILE))
        assertEquals("9.9.9.9", again.getStringExtra(BypassVpnService.EXTRA_DNS))
        assertTrue(again.getBooleanExtra(BypassVpnService.EXTRA_BLOCK_QUIC, false))
        assertFalse(again.getBooleanExtra(BypassVpnService.EXTRA_DOH, true))
        // Settings not sent last time keep the service's defaults.
        assertFalse(again.hasExtra(BypassVpnService.EXTRA_AUTO))
    }

    @Test
    fun nextProfileButtonOnlyWithAnotherProfile() {
        assertNull(BypassVpnService.nextProfileAction!!(app))
        app.profiles.save(Profile("d", "Discord", strategy = StrategyEntry("s", "-s1")))
        assertTrue(BypassVpnService.nextProfileAction!!(app) != null)
    }
}
