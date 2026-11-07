package io.github.halilkhrmn.dpimech.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.halilkhrmn.dpimech.core.LabResult
import io.github.halilkhrmn.dpimech.core.LabStrategy
import io.github.halilkhrmn.dpimech.data.LabState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi")
class WizardTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * The provider is looked up when the test starts; the country it finds must not replace the
     * sites the user tested with, or the saved profile gets other sites than the tested ones.
     */
    @Test
    fun countryFoundAtTestKeepsChosenSites() {
        var country by mutableStateOf<String?>(null)
        var lab by mutableStateOf(LabState())
        var tested: List<String>? = null
        var saved: WizardChoice? = null
        compose.setContent {
            DpimechTheme {
                WizardScreen(
                    lab = lab,
                    country = country,
                    onDetectIsp = { country = "TR" },
                    onTest = { packs, _, _ -> tested = packs },
                    onCancelTest = {},
                    onDone = { saved = it },
                    onSkip = {},
                    initialStep = 1,
                )
            }
        }
        compose.onNodeWithText("Test my connection").performScrollTo().performClick()
        compose.waitForIdle()
        val best = LabResult(LabStrategy("TLS record split", "-r 1+s", LabResult.STANDARD_SET), 2, 2, 100, confirmed = true)
        lab = LabState(finished = true, results = listOf(best))
        compose.onNodeWithText("Save without turning on").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(listOf("discord", "youtube"), tested)
        assertEquals(tested, saved?.packs)
    }
}
