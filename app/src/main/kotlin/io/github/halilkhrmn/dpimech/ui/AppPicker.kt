package io.github.halilkhrmn.dpimech.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.halilkhrmn.dpimech.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Full-screen list of installed apps with search and a "show system apps" switch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPicker(selected: Set<String>, onDone: (Set<String>) -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<InstalledApp>?>(null) {
        value = withContext(Dispatchers.IO) { InstalledApps.load(context.packageManager, context.packageName) }
    }
    var chosen by remember { mutableStateOf(selected) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { onDone(chosen) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.apps_title, chosen.size)) },
                    actions = { IconButton(onClick = { onDone(chosen) }) { Icon(Icons.Default.Check, stringResource(R.string.done)) } },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text(stringResource(R.string.apps_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.apps_show_system), Modifier.weight(1f))
                    Switch(checked = showSystem, onCheckedChange = { showSystem = it })
                }
                val list = apps
                if (list == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    val q = query.trim().lowercase()
                    // Chosen apps first so the current selection is easy to review.
                    val shown = list
                        .filter { showSystem || !it.system || it.packageName in chosen }
                        .filter { q.isEmpty() || q in it.label.lowercase() || q in it.packageName }
                        .sortedByDescending { it.packageName in chosen }
                    LazyColumn {
                        items(shown, key = { it.packageName }) { app ->
                            val on = app.packageName in chosen
                            val toggle = { chosen = if (on) chosen - app.packageName else chosen + app.packageName }
                            ListItem(
                                leadingContent = {
                                    app.icon?.let { Image(it, null, Modifier.size(40.dp)) } ?: Box(Modifier.size(40.dp))
                                },
                                headlineContent = { Text(app.label) },
                                supportingContent = { Text(app.packageName, style = MaterialTheme.typography.bodySmall) },
                                trailingContent = { Checkbox(checked = on, onCheckedChange = { toggle() }) },
                                modifier = Modifier.clickable { toggle() },
                            )
                        }
                    }
                }
            }
        }
    }
}
