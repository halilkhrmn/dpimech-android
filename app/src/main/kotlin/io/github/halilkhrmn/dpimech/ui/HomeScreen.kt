package io.github.halilkhrmn.dpimech.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.halilkhrmn.dpimech.R
import io.github.halilkhrmn.dpimech.core.Profile
import io.github.halilkhrmn.dpimech.core.SavedProfiles
import io.github.halilkhrmn.dpimech.engine.EngineState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    saved: SavedProfiles,
    engine: EngineState,
    onSelect: (String) -> Unit,
    onToggle: (Profile) -> Unit,
    onEdit: (String) -> Unit,
    onNew: () -> Unit,
    onRefreshStrategies: suspend () -> List<String>,
    onLab: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val updated = stringResource(R.string.strategies_updated)
    val failed = stringResource(R.string.strategies_update_failed)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onLab) { Icon(Icons.Default.Science, stringResource(R.string.lab_title)) }
                    IconButton(onClick = {
                        scope.launch {
                            val errors = onRefreshStrategies()
                            snackbar.showSnackbar(if (errors.isEmpty()) updated else failed + "\n" + errors.joinToString("\n"))
                        }
                    }) { Icon(Icons.Default.Refresh, stringResource(R.string.strategies_update)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.profile_new)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatusCard(saved.selected, engine, onToggle) }
            if (saved.profiles.isEmpty()) {
                item {
                    Column(Modifier.padding(vertical = 16.dp)) {
                        Text(stringResource(R.string.easy_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.easy_text), style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = onLab, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Text(stringResource(R.string.easy_button))
                        }
                        Text(stringResource(R.string.profiles_empty), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            items(saved.profiles, key = { it.id }) { p ->
                ProfileRow(p, selected = p.id == saved.selected?.id, onSelect = { onSelect(p.id) }, onEdit = { onEdit(p.id) })
            }
            item { NoticeText() }
        }
    }
}

@Composable
private fun StatusCard(profile: Profile?, engine: EngineState, onToggle: (Profile) -> Unit) {
    val active = engine.isActive()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (engine is EngineState.Running) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when (engine) {
                        is EngineState.Running -> stringResource(R.string.status_on, engine.profileName)
                        is EngineState.Starting -> stringResource(R.string.status_starting)
                        is EngineState.Failed -> stringResource(R.string.status_failed)
                        EngineState.Stopped -> stringResource(R.string.status_off)
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        engine is EngineState.Failed -> engine.message
                        profile == null -> stringResource(R.string.status_no_profile)
                        else -> stringResource(R.string.status_profile, profile.name)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (engine is EngineState.Starting) {
                CircularProgressIndicator(Modifier.size(56.dp))
            } else {
                FilledIconToggleButton(
                    checked = active,
                    onCheckedChange = { profile?.let(onToggle) },
                    enabled = profile != null || active,
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        Icons.Default.PowerSettingsNew,
                        stringResource(if (active) R.string.turn_off else R.string.turn_on),
                        Modifier.size(40.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(p: Profile, selected: Boolean, onSelect: () -> Unit, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onSelect)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.profile_summary, p.strategy.name, p.apps.size),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.profile_edit)) }
        }
    }
}

@Composable
private fun NoticeText() {
    Text(
        stringResource(R.string.notice_not_vpn),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}
