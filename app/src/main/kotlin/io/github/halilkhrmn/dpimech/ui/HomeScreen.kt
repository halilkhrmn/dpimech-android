package io.github.halilkhrmn.dpimech.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.halilkhrmn.dpimech.R
import io.github.halilkhrmn.dpimech.core.AppMode
import io.github.halilkhrmn.dpimech.core.Profile
import io.github.halilkhrmn.dpimech.core.SavedProfiles
import io.github.halilkhrmn.dpimech.engine.EngineState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    saved: SavedProfiles,
    engine: EngineState,
    onSelect: (String) -> Unit,
    onToggle: (Profile) -> Unit,
    onEdit: (String) -> Unit,
    onNew: () -> Unit,
    onWizard: () -> Unit,
    bottomPadding: PaddingValues,
) {
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = bottomPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { PowerButton(saved.selected, engine, onToggle, onWizard) }
            saved.selected?.let { p -> item { ActiveProfileCard(p, engine, onEdit = { onEdit(p.id) }) } }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.profiles_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = onNew) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.profile_new), Modifier.padding(start = 6.dp))
                    }
                }
            }
            items(saved.profiles, key = { it.id }) { p ->
                ProfileRow(p, selected = p.id == saved.selected?.id, onSelect = { onSelect(p.id) }, onEdit = { onEdit(p.id) })
            }
            item {
                Text(
                    stringResource(R.string.notice_not_vpn),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** The one big switch: colour, icon and a word tell at a glance whether the bypass is on. */
@Composable
private fun PowerButton(profile: Profile?, engine: EngineState, onToggle: (Profile) -> Unit, onWizard: () -> Unit) {
    val on = engine is EngineState.Running
    val busy = engine is EngineState.Starting
    // Green for on regardless of the wallpaper colours: the state must read at a glance.
    val container by animateColorAsState(
        when {
            on -> OnGreen
            engine is EngineState.Failed -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "power",
    )
    val content = if (on) Color.White else MaterialTheme.colorScheme.onSurface
    val word = stringResource(
        when {
            on -> R.string.power_on
            busy -> R.string.power_starting
            else -> R.string.power_off
        },
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
        Surface(
            shape = CircleShape,
            color = container,
            contentColor = content,
            border = if (on) null else BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = if (on) 8.dp else 0.dp,
            modifier = Modifier
                .size(200.dp)
                .semantics { contentDescription = word }
                .clickable(enabled = !busy, role = Role.Switch) {
                    if (profile == null && !on) onWizard() else profile?.let(onToggle)
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(80.dp))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PowerSettingsNew, null, Modifier.size(72.dp))
                        Text(word, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            when (engine) {
                is EngineState.Running -> stringResource(R.string.status_running_hint)
                is EngineState.Starting -> stringResource(R.string.status_starting)
                is EngineState.Failed -> engine.message
                EngineState.Stopped -> if (profile == null) stringResource(R.string.status_no_profile) else stringResource(R.string.status_off_hint)
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = if (engine is EngineState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (profile == null && engine !is EngineState.Running) {
            Button(onClick = onWizard, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.easy_button)) }
        }
        if (engine is EngineState.Running && engine.autoTesting) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            Text(stringResource(R.string.auto_testing), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

private val OnGreen = Color(0xFF1E8E3E)

@Composable
private fun ActiveProfileCard(p: Profile, engine: EngineState, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleLarge)
                Text(profileScope(p), style = MaterialTheme.typography.bodyMedium)
                val strategy = (engine as? EngineState.Running)?.strategyName?.ifEmpty { null } ?: p.strategy.name
                Text(stringResource(R.string.profile_strategy_line, strategy), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.profile_edit)) }
        }
    }
}

/** "All apps · 4 sites" or "2 apps · 1 site". */
@Composable
fun profileScope(p: Profile): String {
    val apps = if (p.appMode == AppMode.ALL_EXCEPT) {
        if (p.apps.isEmpty()) stringResource(R.string.scope_all_apps) else stringResource(R.string.scope_all_apps_except, p.apps.size)
    } else {
        stringResource(R.string.scope_apps, p.apps.size)
    }
    val sites = if (p.domainFilter) stringResource(R.string.scope_sites, p.packs.size + p.extraDomains.size) else stringResource(R.string.scope_all_sites)
    return "$apps · $sites"
}

@Composable
private fun ProfileRow(p: Profile, selected: Boolean, onSelect: () -> Unit, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onSelect)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                Text(profileScope(p), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.profile_edit)) }
        }
    }
}
