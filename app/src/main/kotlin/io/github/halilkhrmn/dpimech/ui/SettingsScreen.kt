package io.github.halilkhrmn.dpimech.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
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
    onLogs: () -> Unit,
    onReport: () -> Unit,
    bottomPadding: PaddingValues,
) {
    val context = LocalContext.current
    var pickLanguage by remember { mutableStateOf(false) }
    var pickDns by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
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
                headlineContent = { Text(stringResource(R.string.strategies_update)) },
                supportingContent = { Text(stringResource(R.string.settings_update_hint)) },
                modifier = Modifier.clickable {
                    scope.launch {
                        val errors = onRefreshStrategies()
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
            Section(stringResource(R.string.settings_section_system))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_always_on)) },
                supportingContent = { Text(stringResource(R.string.settings_always_on_hint)) },
                modifier = Modifier.clickable {
                    runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
                },
            )
            val pm = context.getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_battery)) },
                    supportingContent = { Text(stringResource(R.string.settings_battery_hint)) },
                    modifier = Modifier.clickable {
                        @Suppress("BatteryLife") // a VPN-style service the user turned on; the user confirms
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
                        runCatching { context.startActivity(intent) }
                    },
                )
            }
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
