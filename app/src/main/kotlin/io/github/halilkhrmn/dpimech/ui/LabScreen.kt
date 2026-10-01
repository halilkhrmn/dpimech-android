package io.github.halilkhrmn.dpimech.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.halilkhrmn.dpimech.R
import io.github.halilkhrmn.dpimech.core.DnsCheck
import io.github.halilkhrmn.dpimech.core.DomainPack
import io.github.halilkhrmn.dpimech.core.LabResult
import io.github.halilkhrmn.dpimech.data.LabState
import io.github.halilkhrmn.dpimech.data.StrategyOption

/** Strategy Lab: test strategies against the chosen sites and use the winner. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LabScreen(
    state: LabState,
    options: List<StrategyOption>,
    initialPacks: List<String>,
    targetProfile: String?,
    onDetectIsp: () -> Unit,
    onStart: (List<DomainPack>, List<StrategyOption>) -> Unit,
    onCancel: () -> Unit,
    onUse: (result: LabResult, packs: List<String>, turnOn: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) { onDetectIsp() }
    var packs by rememberSaveable { mutableStateOf(initialPacks.ifEmpty { listOf("discord") }) }
    var community by rememberSaveable { mutableStateOf(false) }
    val chosen = options.filter { community || it.source == LabResult.STANDARD_SET }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lab_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    state.isp?.let { stringResource(R.string.lab_isp, it.known?.name ?: it.provider) }
                        ?: stringResource(R.string.lab_isp_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                Text(stringResource(R.string.profile_sites), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DomainPack.ALL.forEach { p ->
                        val on = p.id in packs
                        FilterChip(
                            selected = on,
                            enabled = !state.running,
                            onClick = { packs = if (on) packs - p.id else packs + p.id },
                            label = { Text(p.name) },
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.lab_community), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.lab_count, chosen.size), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = community, enabled = !state.running, onCheckedChange = { community = it })
                }
            }
            item {
                if (state.running) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
                    LinearProgressIndicator(
                        progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Text(stringResource(R.string.lab_progress, state.done, state.total), style = MaterialTheme.typography.bodySmall)
                } else {
                    Button(
                        enabled = packs.isNotEmpty() && chosen.isNotEmpty(),
                        onClick = { onStart(DomainPack.ALL.filter { it.id in packs }, chosen) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.lab_start)) }
                }
            }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (state.dns.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.dns_title), style = MaterialTheme.typography.titleSmall)
                            state.dns.forEach { f ->
                                val via = if (f.via == "system") stringResource(R.string.dns_via_system) else f.via
                                Text(
                                    if (f.verdict == DnsCheck.Verdict.NOT_RESOLVED) stringResource(R.string.dns_not_resolved, f.name, via)
                                    else stringResource(R.string.dns_different, f.name, via, f.got.take(2).joinToString(", "), f.doh.take(2).joinToString(", ")),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(stringResource(R.string.dns_advice), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            state.baseline?.let { b ->
                item {
                    Text(
                        stringResource(R.string.lab_baseline, b.ok, b.total) +
                            if (b.failedDomains.isEmpty()) "\n" + stringResource(R.string.lab_baseline_all_open)
                            else "\n" + stringResource(R.string.lab_failed, b.failedDomains.joinToString(", ")),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (state.finished) {
                item {
                    val best = LabResult.best(state.results)
                    Text(
                        when {
                            best == null -> stringResource(R.string.lab_none_worked)
                            best.confirmed -> stringResource(R.string.lab_best_confirmed, best.strategy!!.name)
                            else -> stringResource(R.string.lab_best_unconfirmed, best.strategy!!.name)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (best != null) {
                        Button(onClick = { onUse(best, packs, true) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(stringResource(R.string.lab_use_and_turn_on))
                        }
                    }
                }
            }
            items(state.results, key = { r -> r.strategy!!.let { it.name + it.args } }) { r ->
                ResultCard(r, targetProfile, enabled = !state.running, onUse = { onUse(r, packs, false) })
            }
        }
    }
}

@Composable
private fun ResultCard(r: LabResult, targetProfile: String?, enabled: Boolean, onUse: () -> Unit) {
    val s = r.strategy!!
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (r.confirmed) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (r.confirmed) Icon(Icons.Default.CheckCircle, stringResource(R.string.lab_confirmed), Modifier.padding(end = 6.dp))
                if (s.recommended) Icon(Icons.Default.Star, stringResource(R.string.lab_recommended), Modifier.padding(end = 6.dp))
                Text(s.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    r.error?.let { stringResource(R.string.lab_error) } ?: "${r.ok}/${r.total} · ${r.avgMs} ms",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(s.args, style = MaterialTheme.typography.bodySmall)
            Text(
                s.source + (if (s.origin.isNotEmpty()) " · ${s.origin}" else "") +
                    (r.error?.let { "\n$it" } ?: if (r.failedDomains.isNotEmpty()) "\n" + stringResource(R.string.lab_failed, r.failedDomains.joinToString(", ")) else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (r.error == null && r.ok > 0) {
                TextButton(onClick = onUse, enabled = enabled) {
                    Text(
                        targetProfile?.let { stringResource(R.string.lab_use_for, it) }
                            ?: stringResource(R.string.lab_use_new),
                    )
                }
            }
        }
    }
}
