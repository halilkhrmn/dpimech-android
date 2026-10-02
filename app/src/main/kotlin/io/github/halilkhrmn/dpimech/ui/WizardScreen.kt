package io.github.halilkhrmn.dpimech.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.halilkhrmn.dpimech.R
import io.github.halilkhrmn.dpimech.core.CountryPreset
import io.github.halilkhrmn.dpimech.core.DomainPack
import io.github.halilkhrmn.dpimech.core.Hostlist
import io.github.halilkhrmn.dpimech.core.LabResult
import io.github.halilkhrmn.dpimech.data.LabState
import java.util.Locale

/** What the wizard produces; the activity turns it into a profile. */
data class WizardChoice(
    val wholePhone: Boolean,
    val packs: List<String>,
    val best: LabResult?,
    val turnOn: Boolean,
    /** Sites typed in by hand. */
    val domains: List<String> = emptyList(),
)

/**
 * First-start wizard: welcome → where (whole phone with the country's commonly blocked sites,
 * or only some apps) → test the strategies on this connection → turn on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizardScreen(
    lab: LabState,
    country: String?,
    onDetectIsp: () -> Unit,
    onTest: (packs: List<String>, domains: List<String>, community: Boolean) -> Unit,
    onCancelTest: () -> Unit,
    onDone: (WizardChoice) -> Unit,
    onSkip: () -> Unit,
    /** For screenshots: open at a later step. */
    initialStep: Int = 0,
) {
    LaunchedEffect(Unit) { onDetectIsp() }
    var step by rememberSaveable { mutableStateOf(initialStep) }
    var wholePhone by rememberSaveable { mutableStateOf(true) }
    val locale = LocalConfiguration.current.locales[0]
    val preset = CountryPreset.forCountry(country ?: locale.country)
    var packs by rememberSaveable(preset.country) { mutableStateOf(preset.packs) }
    var domains by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var domainInput by rememberSaveable { mutableStateOf("") }
    val allDomains = (domains + Hostlist.parseInput(domainInput)).distinct()
    // Named only when the country has its own list; otherwise the text would promise too much.
    val countryName = preset.country.takeIf { it.isNotEmpty() }
        ?.let { Locale("", it).getDisplayCountry(locale) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.wizard_step, step + 1, 3), style = MaterialTheme.typography.labelLarge)
            when (step) {
                0 -> {
                    Image(
                        ImageBitmap.imageResource(R.drawable.logo), null,
                        Modifier.size(120.dp).align(Alignment.CenterHorizontally), filterQuality = FilterQuality.None,
                    )
                    Text(stringResource(R.string.wizard_welcome_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.wizard_welcome_text), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.notice_not_vpn), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wizard_start)) }
                    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wizard_skip)) }
                }
                1 -> {
                    Text(stringResource(R.string.wizard_where_title), style = MaterialTheme.typography.headlineSmall)
                    ModeCard(
                        selected = wholePhone,
                        title = stringResource(R.string.wizard_whole_phone),
                        text = countryName?.let { stringResource(R.string.wizard_whole_phone_text_country, it) }
                            ?: stringResource(R.string.wizard_whole_phone_text),
                        recommended = true,
                        onClick = { wholePhone = true },
                    )
                    ModeCard(
                        selected = !wholePhone,
                        title = stringResource(R.string.wizard_apps),
                        text = stringResource(R.string.wizard_apps_text),
                        recommended = false,
                        onClick = { wholePhone = false },
                    )
                    Text(stringResource(R.string.wizard_sites), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DomainPack.ALL.forEach { p ->
                            val on = p.id in packs
                            FilterChip(selected = on, onClick = { packs = if (on) packs - p.id else packs + p.id }, label = { Text(p.label()) })
                        }
                    }
                    DomainPills(
                        domains = domains,
                        input = domainInput,
                        onInput = { text ->
                            val (done, left) = Hostlist.splitTyped(text)
                            domains = (domains + done).distinct()
                            domainInput = left
                        },
                        onAdd = {
                            domains = allDomains
                            domainInput = ""
                        },
                        onRemove = { d -> domains = domains - d },
                    )
                    Button(
                        enabled = packs.isNotEmpty() || allDomains.isNotEmpty(),
                        onClick = {
                            domains = allDomains
                            domainInput = ""
                            step = 2
                            onTest(packs, allDomains, false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.wizard_test)) }
                    TextButton(onClick = { step = 0 }) { Text(stringResource(R.string.back)) }
                }
                else -> TestStep(
                    lab = lab,
                    onRetryCommunity = { onTest(packs, domains, true) },
                    onCancel = { onCancelTest(); step = 1 },
                    onDone = { best, turnOn -> onDone(WizardChoice(wholePhone, packs, best, turnOn, domains)) },
                )
            }
        }
    }
}

@Composable
private fun ModeCard(selected: Boolean, title: String, text: String, recommended: Boolean, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.weight(1f)) {
                Text(title + if (recommended) " · " + stringResource(R.string.wizard_recommended) else "", style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TestStep(
    lab: LabState,
    onRetryCommunity: () -> Unit,
    onCancel: () -> Unit,
    onDone: (LabResult?, Boolean) -> Unit,
) {
    val best = LabResult.best(lab.results)
    Text(stringResource(R.string.wizard_test_title), style = MaterialTheme.typography.headlineSmall)
    if (lab.running || (!lab.finished && lab.error == null)) {
        Text(stringResource(R.string.wizard_test_text), style = MaterialTheme.typography.bodyLarge)
        LinearProgressIndicator(
            progress = { if (lab.total == 0) 0f else lab.done.toFloat() / lab.total },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.lab_progress, lab.done, lab.total), style = MaterialTheme.typography.bodySmall)
        best?.let { Text(stringResource(R.string.wizard_best_so_far, it.strategy!!.name), style = MaterialTheme.typography.bodyMedium) }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
        return
    }
    lab.baseline?.let { b ->
        if (b.failedDomains.isEmpty()) Text(stringResource(R.string.lab_baseline_all_open), style = MaterialTheme.typography.bodyMedium)
    }
    when {
        best != null -> {
            Text(
                stringResource(if (best.confirmed) R.string.lab_best_confirmed else R.string.lab_best_unconfirmed, best.strategy!!.name),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(R.string.wizard_result_text), style = MaterialTheme.typography.bodyMedium)
        }
        else -> Text(stringResource(R.string.lab_none_worked), style = MaterialTheme.typography.titleMedium)
    }
    lab.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = { onDone(best, true) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wizard_turn_on)) }
    if (best == null || !best.confirmed) {
        OutlinedButton(onClick = onRetryCommunity, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wizard_try_community)) }
    }
    TextButton(onClick = { onDone(best, false) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.wizard_save_only), textAlign = TextAlign.Center)
    }
}
