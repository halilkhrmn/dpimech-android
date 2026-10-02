package io.github.halilkhrmn.dpimech.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.halilkhrmn.dpimech.R
import androidx.compose.ui.platform.LocalConfiguration
import io.github.halilkhrmn.dpimech.core.AppMode
import io.github.halilkhrmn.dpimech.core.ArgPolicy
import io.github.halilkhrmn.dpimech.core.DomainPack
import io.github.halilkhrmn.dpimech.core.Hostlist
import io.github.halilkhrmn.dpimech.core.Profile
import io.github.halilkhrmn.dpimech.core.StrategyEntry
import io.github.halilkhrmn.dpimech.core.splitArgs
import io.github.halilkhrmn.dpimech.data.StrategyOption
import io.github.halilkhrmn.dpimech.shortcut.Shortcuts
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileEditor(
    initial: Profile?,
    strategies: List<StrategyOption>,
    onSave: (Profile) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val installed = remember { InstalledApps.packageNames(context.packageManager) }
    val id = remember { initial?.id ?: UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var packs by rememberSaveable { mutableStateOf(initial?.packs.orEmpty()) }
    var domains by rememberSaveable { mutableStateOf(initial?.extraDomains.orEmpty()) }
    var domainInput by rememberSaveable { mutableStateOf("") }
    var strategyName by rememberSaveable { mutableStateOf(initial?.strategy?.name ?: strategies.firstOrNull()?.entry?.name.orEmpty()) }
    var strategyArgs by rememberSaveable { mutableStateOf(initial?.strategy?.args ?: strategies.firstOrNull()?.entry?.args.orEmpty()) }
    var mode by rememberSaveable { mutableStateOf(initial?.appMode ?: AppMode.ONLY_SELECTED) }
    var apps by rememberSaveable { mutableStateOf(initial?.apps.orEmpty()) }
    var domainFilter by rememberSaveable { mutableStateOf(initial?.domainFilter ?: true) }
    var pickStrategy by remember { mutableStateOf(false) }
    var pickApps by remember { mutableStateOf(false) }

    // Same check as at launch, so a bad custom strategy is caught while editing.
    val argsError = ArgPolicy.check(splitArgs(strategyArgs.replace("{sni}", "x").replace("{hostlist}", "/l/h")), listOf(File("/l")))
        .exceptionOrNull()?.message
    val canSave = name.isNotBlank() && strategyArgs.isNotBlank() && argsError == null &&
        (mode == AppMode.ALL_EXCEPT || apps.isNotEmpty())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (initial == null) R.string.profile_new else R.string.profile_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (initial != null) {
                        IconButton(onClick = { onDelete(initial.id) }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.profile_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(stringResource(R.string.profile_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            Section(stringResource(R.string.profile_sites))
            val language = LocalConfiguration.current.locales[0].language
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DomainPack.ALL.forEach { pack ->
                    val on = pack.id in packs
                    FilterChip(
                        selected = on,
                        onClick = {
                            packs = if (on) packs - pack.id else packs + pack.id
                            if (!on) {
                                // Ticking a pack also ticks its installed apps.
                                apps = (apps + pack.packages.filter { it in installed }).distinct()
                                if (name.isBlank()) name = pack.displayName(language)
                            }
                        },
                        label = { Text(pack.label()) },
                    )
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
                    domains = (domains + Hostlist.parseInput(domainInput)).distinct()
                    domainInput = ""
                },
                onRemove = { d -> domains = domains - d },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_domain_filter), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.profile_domain_filter_hint), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = domainFilter, onCheckedChange = { domainFilter = it })
            }

            Section(stringResource(R.string.profile_strategy))
            OutlinedButton(onClick = { pickStrategy = true }, modifier = Modifier.fillMaxWidth()) {
                Text(strategyName.ifBlank { stringResource(R.string.profile_strategy_choose) })
            }
            OutlinedTextField(
                value = strategyArgs, onValueChange = { strategyArgs = it },
                label = { Text(stringResource(R.string.profile_strategy_args)) },
                isError = argsError != null,
                supportingText = argsError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Section(stringResource(R.string.profile_apps))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AppMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { mode = m },
                        shape = SegmentedButtonDefaults.itemShape(i, AppMode.entries.size),
                    ) {
                        Text(stringResource(if (m == AppMode.ONLY_SELECTED) R.string.mode_only else R.string.mode_except))
                    }
                }
            }
            OutlinedButton(onClick = { pickApps = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.profile_apps_choose, apps.size))
            }
            if (mode == AppMode.ONLY_SELECTED && apps.isEmpty()) {
                Text(stringResource(R.string.profile_apps_needed), color = MaterialTheme.colorScheme.error)
            }

            if (initial != null && Shortcuts.canPin(context)) {
                OutlinedButton(onClick = { Shortcuts.pin(context, initial) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.shortcut_pin))
                }
            }
            Button(
                enabled = canSave,
                onClick = {
                    onSave(
                        Profile(
                            id = id,
                            name = name.trim(),
                            packs = packs,
                            extraDomains = (domains + Hostlist.parseInput(domainInput)).distinct(),
                            strategy = StrategyEntry(strategyName.ifBlank { strategyArgs }, strategyArgs.trim()),
                            appMode = mode,
                            apps = apps,
                            domainFilter = domainFilter,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
        }
    }

    if (pickStrategy) {
        StrategyDialog(strategies, onPick = {
            strategyName = it.entry.name
            strategyArgs = it.entry.args
            pickStrategy = false
        }, onDismiss = { pickStrategy = false })
    }
    if (pickApps) {
        AppPicker(selected = apps.toSet(), onDone = { apps = it.toList(); pickApps = false })
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun StrategyDialog(options: List<StrategyOption>, onPick: (StrategyOption) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.profile_strategy_choose)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                itemsIndexed(options) { i, o ->
                    if (i > 0 && options[i - 1].source != o.source) HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(o.entry.name) },
                        supportingContent = {
                            Text(o.entry.args + "\n" + sourceLabel(o.source) + if (o.origin.isNotEmpty()) " · ${o.origin}" else "")
                        },
                        modifier = Modifier.clickable { onPick(o) },
                    )
                }
            }
        },
    )
}
