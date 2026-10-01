package io.github.halilkhrmn.dpimech.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.halilkhrmn.dpimech.R
import io.github.halilkhrmn.dpimech.core.AppSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onLanguage: (String) -> Unit,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onWizard: () -> Unit,
    onRefreshStrategies: suspend () -> List<String>,
    lastUpdated: Long?,
    onLogs: () -> Unit,
    onReport: () -> Unit,
    bottomPadding: PaddingValues,
    onExport: (android.net.Uri) -> Boolean = { false },
    onImport: (android.net.Uri) -> Result<Int> = { Result.failure(UnsupportedOperationException()) },
) {
    val context = LocalContext.current
    var pickLanguage by remember { mutableStateOf(false) }
    var pickDns by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val backupScope = androidx.compose.runtime.rememberCoroutineScope()
    val resources = androidx.compose.ui.platform.LocalResources.current
    val exported = stringResource(R.string.backup_done)
    val exportFailed = stringResource(R.string.backup_failed)
    val importFailed = stringResource(R.string.restore_failed)
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { backupScope.launch { snackbar.showSnackbar(if (onExport(it)) exported else exportFailed) } } }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            val r = onImport(it)
            backupScope.launch {
                snackbar.showSnackbar(r.fold({ n -> resources.getQuantityString(R.plurals.restore_done, n, n) }, { e -> importFailed + "\n" + (e.message ?: "") }))
            }
        }
    }
    var refreshing by remember { mutableStateOf(false) }
    // Re-read when coming back from Android's battery screen.
    var ignoringBattery by remember { mutableStateOf(isIgnoringBattery(context)) }
    LifecycleResumeEffect(Unit) {
        ignoringBattery = isIgnoringBattery(context)
        onPauseOrDispose { }
    }
    val scope = rememberCoroutineScope()
    val updated = stringResource(R.string.strategies_updated)
    val failed = stringResource(R.string.strategies_update_failed)

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding(), bottom = bottomPadding.calculateBottomPadding())
                .verticalScroll(rememberScrollState()),
        ) {
            Section(stringResource(R.string.settings_section_look))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_language)) },
                supportingContent = { Text(languageName(settings.language)) },
                modifier = Modifier.clickable { pickLanguage = true },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.settings_dynamic_color_hint)) },
                    trailingContent = { Switch(settings.dynamicColor, { on -> onChange { it.copy(dynamicColor = on) } }) },
                    modifier = Modifier.clickable { onChange { it.copy(dynamicColor = !it.dynamicColor) } },
                )
            }
            Section(stringResource(R.string.settings_section_bypass))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_auto)) },
                supportingContent = { Text(stringResource(R.string.settings_auto_hint)) },
                trailingContent = { Switch(settings.autoStrategy, { on -> onChange { it.copy(autoStrategy = on) } }) },
                modifier = Modifier.clickable { onChange { it.copy(autoStrategy = !it.autoStrategy) } },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dns)) },
                supportingContent = {
                    Text((AppSettings.DNS_SERVERS.find { it.address == settings.dns }?.name ?: "") + " (${settings.dns})\n" + stringResource(R.string.settings_dns_hint))
                },
                modifier = Modifier.clickable { pickDns = true },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_doh)) },
                supportingContent = { Text(stringResource(R.string.settings_doh_hint)) },
                trailingContent = { Switch(settings.encryptedDns, { on -> onChange { it.copy(encryptedDns = on) } }) },
                modifier = Modifier.clickable { onChange { it.copy(encryptedDns = !it.encryptedDns) } },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_quic)) },
                supportingContent = { Text(stringResource(R.string.settings_quic_hint)) },
                trailingContent = { Switch(settings.blockQuic, { on -> onChange { it.copy(blockQuic = on) } }) },
                modifier = Modifier.clickable { onChange { it.copy(blockQuic = !it.blockQuic) } },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_auto_update)) },
                supportingContent = { Text(stringResource(R.string.settings_auto_update_hint)) },
                trailingContent = { Switch(settings.autoUpdateLists, { on -> onChange { it.copy(autoUpdateLists = on) } }) },
                modifier = Modifier.clickable { onChange { it.copy(autoUpdateLists = !it.autoUpdateLists) } },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.strategies_update)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.settings_update_hint) + "\n" +
                            (lastUpdated?.let { stringResource(R.string.settings_last_update, formatTime(it, LocalConfiguration.current.locales[0])) }
                                ?: stringResource(R.string.settings_never_updated)),
                    )
                },
                trailingContent = { if (refreshing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) },
                modifier = Modifier.clickable(enabled = !refreshing) {
                    refreshing = true
                    scope.launch {
                        val errors = onRefreshStrategies()
                        refreshing = false
                        snackbar.showSnackbar(if (errors.isEmpty()) updated else failed + "\n" + errors.joinToString("\n"))
                    }
                },
            )
            Section(stringResource(R.string.settings_section_notifications))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_notify_strategy)) },
                trailingContent = { Switch(settings.notifyStrategy, { on -> onChange { it.copy(notifyStrategy = on) } }) },
                modifier = Modifier.clickable { onChange { it.copy(notifyStrategy = !it.notifyStrategy) } },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_notify_errors)) },
                trailingContent = { Switch(settings.notifyErrors, { on -> onChange { it.copy(notifyErrors = on) } }) },
                modifier = Modifier.clickable { onChange { it.copy(notifyErrors = !it.notifyErrors) } },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_notifications)) },
                supportingContent = { Text(stringResource(R.string.settings_notifications_hint)) },
                modifier = Modifier.clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                },
            )
            Section(stringResource(R.string.settings_section_help))
            ListItem(
                headlineContent = { Text(stringResource(R.string.logs_title)) },
                supportingContent = { Text(stringResource(R.string.logs_hint)) },
                modifier = Modifier.clickable(onClick = onLogs),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.report_title)) },
                supportingContent = { Text(stringResource(R.string.report_hint)) },
                modifier = Modifier.clickable(onClick = onReport),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_wizard)) },
                supportingContent = { Text(stringResource(R.string.settings_wizard_hint)) },
                modifier = Modifier.clickable(onClick = onWizard),
            )
            Section(stringResource(R.string.settings_section_backup))
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup)) },
                supportingContent = { Text(stringResource(R.string.backup_hint)) },
                modifier = Modifier.clickable { exportLauncher.launch("dpimech-profiles.json") },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.restore)) },
                supportingContent = { Text(stringResource(R.string.restore_hint)) },
                modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            )
            Section(stringResource(R.string.settings_section_system))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_start_on_boot)) },
                supportingContent = { Text(stringResource(R.string.settings_start_on_boot_hint)) },
                trailingContent = { Switch(settings.startOnBoot, { on -> onChange { it.copy(startOnBoot = on) } }) },
                modifier = Modifier.clickable { onChange { it.copy(startOnBoot = !it.startOnBoot) } },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_always_on)) },
                supportingContent = { Text(stringResource(R.string.settings_always_on_hint)) },
                modifier = Modifier.clickable {
                    runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_battery)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.settings_battery_hint) + "\n" +
                            stringResource(if (ignoringBattery) R.string.settings_battery_ok else R.string.settings_battery_limited),
                    )
                },
                modifier = Modifier.clickable {
                    val intent = if (ignoringBattery) {
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    } else {
                        @SuppressLint("BatteryLife") // a VPN-style service the user turned on; the user confirms
                        val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
                        ask
                    }
                    // Some phones lack the direct dialog: fall back to the list of apps.
                    runCatching { context.startActivity(intent) }.onFailure {
                        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    }
                },
            )
        }
    }

    if (pickLanguage) {
        ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = AppSettings.LANGUAGES.map { it to languageName(it) },
            selected = settings.language,
            onPick = { pickLanguage = false; onLanguage(it) },
            onDismiss = { pickLanguage = false },
        )
    }
    if (pickDns) {
        ChoiceDialog(
            title = stringResource(R.string.settings_dns),
            options = AppSettings.DNS_SERVERS.map { it.address to "${it.name} (${it.address})" },
            selected = settings.dns,
            onPick = { dns -> pickDns = false; onChange { it.copy(dns = dns) } },
            onDismiss = { pickDns = false },
        )
    }
}

private fun isIgnoringBattery(context: android.content.Context) =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

/** In the app's language, which may differ from the phone's. */
private fun formatTime(ms: Long, locale: java.util.Locale): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, locale).format(java.util.Date(ms))

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun languageName(tag: String) = when (tag) {
    "en" -> "English"
    "tr" -> "Türkçe"
    "ru" -> "Русский"
    else -> stringResource(R.string.settings_language_system)
}

@Composable
fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(value) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = value == selected, onClick = { onPick(value) })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
