package io.github.halilkhrmn.dpimech.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.halilkhrmn.dpimech.R
import io.github.halilkhrmn.dpimech.core.DomainPack

/**
 * The ready-made sites as one summary row; tapping it opens a bottom sheet with a searchable
 * multi-select list of every pack. Each row says on the right whether the pack also covers an
 * Android app ("App + site") or only a web site. Used by the profile editor, the wizard and the
 * Strategy Lab, so the long pack list never fills those screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitePicker(
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val language = LocalConfiguration.current.locales[0].language
    val chosen = DomainPack.ALL.filter { it.id in selected }
    val resources = LocalResources.current
    Card(modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Text(
                    chosen.joinToString(", ") { it.displayName(language) }
                        .ifEmpty { stringResource(R.string.site_picker_none) },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    if (chosen.isEmpty()) stringResource(R.string.site_picker_tap)
                    else resources.getQuantityString(R.plurals.site_picker_count, chosen.size, chosen.size),
                )
            },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.clickable(enabled = enabled, role = Role.Button) { open = true },
        )
    }
    if (open) {
        SitePickerSheet(selected, onDone = { onChange(it); open = false }, onDismiss = { open = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SitePickerSheet(initial: List<String>, onDone: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var picked by remember { mutableStateOf(initial) }
    var query by rememberSaveable { mutableStateOf("") }
    val language = LocalConfiguration.current.locales[0].language
    val resources = LocalResources.current
    val q = query.trim().lowercase()
    val shown = DomainPack.ALL.filter { p ->
        q.isEmpty() || p.displayName(language).lowercase().contains(q) || p.name.lowercase().contains(q) ||
            p.domains.any { it.contains(q) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.site_picker_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    resources.getQuantityString(R.plurals.site_picker_selected, picked.size, picked.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text(stringResource(R.string.site_picker_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 520.dp)) {
                items(shown, key = { it.id }) { p ->
                    val on = p.id in picked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .toggleable(value = on, role = Role.Checkbox) { picked = if (on) picked - p.id else picked + p.id }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The row is the toggle; the box only shows its state.
                        Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                        Text(p.label(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        // Fixed width, aligned to the end: the labels line up in every row.
                        Text(
                            stringResource(if (p.packages.isNotEmpty()) R.string.site_kind_app else R.string.site_kind_web),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(112.dp),
                        )
                    }
                }
                if (shown.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.site_picker_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { picked = emptyList() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.site_picker_clear))
                }
                Button(onClick = { onDone(DomainPack.ALL.map { it.id }.filter { it in picked }) }, modifier = Modifier.weight(2f)) {
                    Text(stringResource(R.string.done))
                }
            }
            Spacer(Modifier.padding(bottom = 4.dp))
        }
    }
}
