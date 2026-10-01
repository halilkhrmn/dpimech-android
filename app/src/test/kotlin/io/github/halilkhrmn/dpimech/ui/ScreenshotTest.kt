package io.github.halilkhrmn.dpimech.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.halilkhrmn.dpimech.core.AppMode
import io.github.halilkhrmn.dpimech.core.AppSettings
import io.github.halilkhrmn.dpimech.core.CountryPreset
import io.github.halilkhrmn.dpimech.core.LabResult
import io.github.halilkhrmn.dpimech.core.LabStrategy
import io.github.halilkhrmn.dpimech.core.Profile
import io.github.halilkhrmn.dpimech.core.SavedProfiles
import io.github.halilkhrmn.dpimech.core.StrategyEntry
import io.github.halilkhrmn.dpimech.data.LabState
import io.github.halilkhrmn.dpimech.engine.EngineState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the main screens on the JVM (Robolectric) so the layout can be checked without a
 * phone. Images land in app/build/screenshots/; the test fails if a screen does not compose.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val strategy = StrategyEntry("TLS record split", "-r 1+s")
    private val whole = CountryPreset.forCountry("TR").profile("1", "Blocked sites", strategy)
    private val discord = Profile("2", "Discord", packs = listOf("discord"), strategy = strategy, apps = listOf("com.discord"), appMode = AppMode.ONLY_SELECTED)
    private val saved = SavedProfiles(listOf(whole, discord), "1")

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { DpimechTheme(content = content) }
        compose.onRoot().captureRoboImage("${System.getProperty("roborazzi.output.dir", "build/screenshots")}/$name.png")
    }

    private fun home(engine: EngineState, profiles: SavedProfiles = saved) = shot("home_${engine::class.simpleName}") {
        HomeScreen(profiles, engine, {}, {}, {}, {}, {}, PaddingValues())
    }

    @Test @Config(qualifiers = "+tr") fun homeTurkish() = shot("home_tr") {
        HomeScreen(saved, EngineState.Running("1", "Engelli siteler", "TLS record split"), {}, {}, {}, {}, {}, PaddingValues())
    }

    @Test @Config(qualifiers = "+tr") fun wizardTurkish() = shot("wizard_tr") { WizardScreen(LabState(), "TR", {}, { _, _ -> }, {}, {}, {}) }

    @Test @Config(qualifiers = "+night") fun homeDark() = shot("home_dark") {
        HomeScreen(saved, EngineState.Running("1", "Blocked sites", "TLS record split"), {}, {}, {}, {}, {}, PaddingValues())
    }

    @Test fun homeOff() = home(EngineState.Stopped)
    @Test fun homeOn() = shot("home_Running") {
        val tt = io.github.halilkhrmn.dpimech.core.IspInfo("Turk Telekom", 9121, "TR", io.github.halilkhrmn.dpimech.core.Isp.match(9121, ""))
        HomeScreen(
            saved.copy(profiles = listOf(whole.withStrategy(strategy, "AS9121"), discord)),
            EngineState.Running("1", "Blocked sites", "TLS record split"), {}, {}, {}, {}, {}, PaddingValues(),
            network = io.github.halilkhrmn.dpimech.core.NetworkInfo(io.github.halilkhrmn.dpimech.core.Transport.WIFI, isp = tt),
        )
    }
    @Test fun homeMobile() = shot("home_mobile") {
        HomeScreen(
            saved, EngineState.Stopped, {}, {}, {}, {}, {}, PaddingValues(),
            network = io.github.halilkhrmn.dpimech.core.NetworkInfo(io.github.halilkhrmn.dpimech.core.Transport.CELLULAR, "28601", "Turkcell"),
        )
    }
    @Test fun homeAutoTesting() = shot("home_auto") {
        HomeScreen(saved, EngineState.Running("1", "Blocked sites", "TLS record split", autoTesting = true), {}, {}, {}, {}, {}, PaddingValues())
    }
    @Test fun homeFailed() = home(EngineState.Failed("1", "ByeDPI did not start listening on port 1080"))
    @Test fun homeEmpty() = shot("home_empty") { HomeScreen(SavedProfiles(), EngineState.Stopped, {}, {}, {}, {}, {}, PaddingValues()) }

    @Test fun settings() = shot("settings") { SettingsScreen(AppSettings(language = "tr"), {}, {}, {}, { emptyList() }, 1_790_000_000_000, {}, {}, PaddingValues()) }

    /** The octopus purple used when wallpaper colours are off (or before Android 12). */
    @Test fun homePurple() {
        compose.setContent {
            DpimechTheme(dynamicColor = false) { HomeScreen(saved, EngineState.Stopped, {}, {}, {}, {}, {}, PaddingValues()) }
        }
        compose.onRoot().captureRoboImage("${System.getProperty("roborazzi.output.dir", "build/screenshots")}/home_purple.png")
    }

    @Test fun about() = shot("about") { AboutScreen("0.1.0", {}, {}, PaddingValues()) }

    @Test fun wizardWelcome() = shot("wizard_welcome") { WizardScreen(LabState(), "TR", {}, { _, _ -> }, {}, {}, {}) }

    @Test fun logs() = shot("logs") {
        LogsScreen(
            listOf(
                "08:00:01 turning on profile \"Blocked sites\" (TLS record split)",
                "08:00:01 started profile Blocked sites on port 41231",
                "08:00:03 automatic strategy: testing 12 strategies for AS9121",
                "08:00:41 automatic strategy: \"Disorder SNI\" works on AS9121 (8/8)",
                "08:00:41 network AS9121: switching to \"Disorder SNI\"",
            ),
            {}, {}, {},
        )
    }

    @Test fun report() = shot("report") {
        ReportDialog(
            io.github.halilkhrmn.dpimech.core.ProblemReport.build(
                "0.1.0", "Google Pixel 8, Android 16 (API 36)", AppSettings(), saved, "on (Blocked sites, TLS record split)", null,
                listOf("08:00:01 started profile Blocked sites on port 41231"),
            ),
        ) {}
    }

    @Test fun lab() {
        val std = LabResult.STANDARD_SET
        val state = LabState(
            running = false, finished = true, done = 18, total = 18,
            baseline = LabResult(null, 4, 8, 120, listOf("discord.com", "roblox.com")),
            results = listOf(
                LabResult(LabStrategy("TLS record split", "-r 1+s", std, recommended = true), 32, 32, 210, confirmed = true),
                LabResult(LabStrategy("Disorder SNI", "-d1 -s1+s", std), 6, 8, 180, listOf("roblox.com")),
                LabResult(LabStrategy("Fake + TTL", "-f1 -t6 -n {sni}", std), 0, 8, 0, error = "ByeDPI stopped at start (exit 1)"),
            ),
        )
        shot("lab") { LabScreen(state, emptyList(), listOf("discord"), "Blocked sites", {}, { _, _ -> }, {}, { _, _, _ -> }, null) }
    }
}
