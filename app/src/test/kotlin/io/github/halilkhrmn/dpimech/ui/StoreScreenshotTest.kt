package io.github.halilkhrmn.dpimech.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.halilkhrmn.dpimech.R
import io.github.halilkhrmn.dpimech.core.AppMode
import io.github.halilkhrmn.dpimech.core.AppSettings
import io.github.halilkhrmn.dpimech.core.CountryPreset
import io.github.halilkhrmn.dpimech.core.Isp
import io.github.halilkhrmn.dpimech.core.IspInfo
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
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Store screenshots (fastlane phoneScreenshots) in each app language, 2:1 so IzzyOnDroid and
 * F-Droid accept them. `tools/fastlane-images.py` copies them into fastlane/.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h822dp-xxhdpi")
class StoreScreenshotTest(private val locale: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = listOf("en", "tr", "ru", "fa", "ar")
    }

    @get:Rule
    val compose = createComposeRule()

    /** The wizard and the whole-phone profile show a country that speaks the language. */
    private val country = mapOf("ru" to "RU", "fa" to "IR", "ar" to "EG").getOrDefault(locale, "TR")

    private fun shot(n: Int, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("+$locale")
        compose.setContent { DpimechTheme(dynamicColor = false, content = content) }
        val dir = System.getProperty("roborazzi.output.dir", "build/screenshots")
        compose.onRoot().captureRoboImage("$dir/store/$locale/$n.png")
    }

    private fun standardOptions() = io.github.halilkhrmn.dpimech.core.StrategyFile.embedded.byeDpi
        .map { io.github.halilkhrmn.dpimech.data.StrategyOption(it, LabResult.STANDARD_SET) }

    private fun str(id: Int) = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    @Composable
    private fun WithBar(selected: Int, content: @Composable (PaddingValues) -> Unit) {
        val tabs = listOf(R.string.tab_home to Icons.Default.Home, R.string.tab_test to Icons.Default.Science, R.string.tab_settings to Icons.Default.Settings, R.string.tab_about to Icons.Default.Info)
        Scaffold(bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(selected = i == selected, onClick = {}, icon = { Icon(icon, null) }, label = { Text(stringResource(label)) })
                }
            }
        }) { content(it) }
    }

    private fun profiles(): SavedProfiles {
        val s = StrategyEntry("TLS record split", "-r 1+s")
        val whole = CountryPreset.forCountry(country).profile("1", str(R.string.wizard_profile_whole_phone), s)
        val discord = Profile("2", "Discord", packs = listOf("discord"), strategy = s, apps = listOf("com.discord"), appMode = AppMode.ONLY_SELECTED)
        return SavedProfiles(listOf(whole, discord), "1")
    }

    @Test fun home() = shot(1) {
        WithBar(0) { p ->
            HomeScreen(
                profiles(), EngineState.Running("1", "", "TLS record split"), {}, {}, {}, {}, {}, p,
                network = io.github.halilkhrmn.dpimech.core.NetworkInfo(io.github.halilkhrmn.dpimech.core.Transport.WIFI, isp = IspInfo("Turk Telekom", 9121, "TR", Isp.match(9121, ""))),
                stats = sampleStats(),
            )
        }
    }

    @Test fun wizard() = shot(2) {
        WizardScreen(LabState(), country, {}, { _, _, _ -> }, {}, {}, {}, initialStep = 1)
    }

    @Test fun lab() = shot(3) {
        val std = LabResult.STANDARD_SET
        val state = LabState(
            finished = true, done = 18, total = 18,
            isp = IspInfo("Turk Telekom", 9121, "TR", Isp.match(9121, "")),
            baseline = LabResult(null, 4, 8, 120, listOf("discord.com", "roblox.com")),
            results = listOf(
                LabResult(LabStrategy("TLS record split", "-r 1+s", std, recommended = true), 32, 32, 210, confirmed = true),
                LabResult(LabStrategy("Disorder SNI", "-d1 -s1+s", std), 6, 8, 180, listOf("roblox.com")),
            ),
        )
        WithBar(1) { p -> LabScreen(state, standardOptions(), listOf("discord", "roblox"), str(R.string.wizard_profile_whole_phone), {}, { _, _, _ -> }, {}, { _, _, _, _ -> }, null, p) }
    }

    @Test fun settings() = shot(4) {
        WithBar(2) { p -> SettingsScreen(AppSettings(language = locale), {}, {}, {}, { emptyList() }, 1_790_000_000_000, {}, {}, p) }
    }

    @Test fun about() = shot(5) { WithBar(3) { p -> AboutScreen("0.2.1", {}, {}, p) } }
}
