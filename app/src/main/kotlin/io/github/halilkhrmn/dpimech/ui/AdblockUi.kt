package io.github.halilkhrmn.dpimech.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.halilkhrmn.dpimech.R
import io.github.halilkhrmn.dpimech.core.BlockList
import io.github.halilkhrmn.dpimech.engine.AdblockLog
import io.github.halilkhrmn.dpimech.engine.AdblockStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The block lists of a profile: those of the catalog (downloaded from GitHub, see
 * [io.github.halilkhrmn.dpimech.core.BlockListCatalog]) and the profile's own, added by address.
 * Each has a tick, what it is, its licence and whether it is downloaded. A list is fetched from its
 * source only when it is ticked (the profile would fetch it at its start anyway).
 */
@Composable
fun AdblockLists(selected: List<String>, onChange: (List<String>) -> Unit) {
    val status by AdblockStore.status.collectAsStateWithLifecycle()
    val catalog by AdblockStore.catalog.collectAsStateWithLifecycle()
    var catalogBusy by remember { mutableStateOf(false) }
    var catalogFailed by remember { mutableStateOf(false) }
    val language = LocalConfiguration.current.locales[0].language
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(emptySet<String>()) }
    var failed by remember { mutableStateOf(emptySet<String>()) }
    var url by rememberSaveable { mutableStateOf("") }
    val locale = LocalConfiguration.current.locales[0]

    fun fetch(list: BlockList) {
        if (list.id in busy) return
        busy = busy + list.id
        failed = failed - list.id
        scope.launch {
            val ok = withContext(Dispatchers.IO) { AdblockStore.download(list).isSuccess }
            busy = busy - list.id
            if (!ok) failed = failed + list.id
        }
    }

    fun refreshCatalog() {
        if (catalogBusy) return
        catalogBusy = true
        catalogFailed = false
        scope.launch {
            val ok = withContext(Dispatchers.IO) { AdblockStore.refreshCatalog().isSuccess }
            catalogBusy = false
            catalogFailed = !ok
        }
    }

    val lists = catalog + selected.mapNotNull { id -> BlockList.byId(id, emptyList()) }
    // The catalog on first use (or a week later), then the ticked lists not downloaded yet (the
    // default one on a first use).
    LaunchedEffect(Unit) {
        if (catalog.isEmpty() || System.currentTimeMillis() - AdblockStore.catalogUpdated > AdblockStore.MAX_AGE_MS) refreshCatalog()
    }
    LaunchedEffect(catalog) {
        selected.mapNotNull { BlockList.byId(it, catalog) }.filter { it.id !in status }.forEach(::fetch)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.adblock_lists), style = MaterialTheme.typography.bodyLarge)
        when {
            catalogBusy && catalog.isEmpty() -> Text(stringResource(R.string.adblock_catalog_loading), style = MaterialTheme.typography.bodySmall)
            catalogFailed && catalog.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.adblock_catalog_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = ::refreshCatalog) { Text(stringResource(R.string.adblock_catalog_retry)) }
            }
        }
        lists.forEach { list ->
            val on = list.id in selected
            val s = status[list.id]
            val line = when {
                list.id in busy -> stringResource(R.string.adblock_list_downloading)
                list.id in failed -> stringResource(R.string.adblock_list_failed)
                s != null -> stringResource(R.string.adblock_list_status, s.names, formatTime(s.updated, locale))
                else -> stringResource(R.string.adblock_list_missing)
            }
            ListItem(
                headlineContent = { Text(list.name) },
                supportingContent = {
                    val source = listOf(list.license, list.homepage.removePrefix("https://")).filter { it.isNotEmpty() }.joinToString(" · ")
                    Text(listOfNotNull(list.note(language), source.ifEmpty { null }, line).joinToString("\n"))
                },
                leadingContent = { Checkbox(checked = on, onCheckedChange = null) },
                trailingContent = if (list.custom) {
                    {
                        IconButton(onClick = { onChange(selected - list.id) }) {
                            Icon(Icons.Default.Close, stringResource(R.string.adblock_custom_remove, list.name))
                        }
                    }
                } else {
                    null
                },
                modifier = Modifier.toggleable(value = on, role = Role.Checkbox) { now ->
                    onChange(if (now) selected + list.id else selected - list.id)
                    if (now && s == null) fetch(list)
                },
            )
        }
        val custom = BlockList.custom(url)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.adblock_custom)) },
            supportingText = { Text(stringResource(R.string.adblock_custom_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = {
                custom?.let { onChange((selected + it.id).distinct()); fetch(it); url = "" }
            }),
            trailingIcon = {
                IconButton(enabled = custom != null, onClick = {
                    custom?.let { onChange((selected + it.id).distinct()); fetch(it); url = "" }
                }) { Icon(Icons.Default.Add, stringResource(R.string.adblock_custom_add)) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            enabled = selected.isNotEmpty() && busy.isEmpty(),
            onClick = {
                refreshCatalog()
                selected.mapNotNull { BlockList.byId(it, catalog) }.forEach(::fetch)
            },
        ) { Text(stringResource(R.string.adblock_update)) }
    }
}

/**
 * The names blocked since the profile started, newest first, with the app that asked when
 * Android tells. "Allow" adds a name to the running profile's allowlist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedScreen(profileName: String?, onAllow: (String) -> Unit, onBack: () -> Unit) {
    val entries by AdblockLog.flow.collectAsStateWithLifecycle()
    val pm = LocalContext.current.packageManager
    val locale = LocalConfiguration.current.locales[0]
    val labels = remember { HashMap<String, String>() }
    var allowed by remember { mutableStateOf(emptySet<String>()) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.blocked_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Text(
                    stringResource(R.string.blocked_hint, profileName ?: "—"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (entries.isEmpty()) {
                item { Text(stringResource(R.string.blocked_empty), modifier = Modifier.padding(16.dp)) }
            }
            items(entries, key = { "${it.time}-${it.name}" }) { e ->
                val app = e.app?.let { pkg -> labels.getOrPut(pkg) { appLabel(pm, pkg) } }
                ListItem(
                    headlineContent = { Text(e.name) },
                    supportingContent = {
                        Text(listOfNotNull(timeOfDay(e.time, locale), app).joinToString(" · "))
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (e.name in allowed) {
                                Text(stringResource(R.string.blocked_allowed), style = MaterialTheme.typography.labelLarge)
                            } else {
                                TextButton(enabled = profileName != null, onClick = {
                                    allowed = allowed + e.name
                                    onAllow(e.name)
                                }) { Text(stringResource(R.string.blocked_allow)) }
                            }
                        }
                    },
                )
            }
        }
    }
}

private fun appLabel(pm: PackageManager, pkg: String): String =
    runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

private fun timeOfDay(ms: Long, locale: java.util.Locale): String =
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM, locale).format(java.util.Date(ms))
