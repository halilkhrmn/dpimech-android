package io.github.halilkhrmn.dpimech.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.halilkhrmn.dpimech.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(versionName: String, onReport: () -> Unit, onLogs: () -> Unit, bottomPadding: PaddingValues) {
    val context = LocalContext.current
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.tab_about)) }) }) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding(), bottom = bottomPadding.calculateBottomPadding())
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Pixel art: no smoothing.
            Image(ImageBitmap.imageResource(R.drawable.logo), null, Modifier.size(112.dp), filterQuality = FilterQuality.None)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.about_version, versionName), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Card(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.notice_not_vpn), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            }
            Column(Modifier.fillMaxWidth()) {
                listOf(
                    R.string.about_website to "https://halilkhrmn.github.io/dpimech-android/",
                    R.string.about_source to "https://github.com/halilkhrmn/dpimech-android",
                    R.string.about_desktop to "https://halilkhrmn.github.io/dpimech/",
                ).forEach { (label, url) ->
                    ListItem(
                        headlineContent = { Text(stringResource(label)) },
                        supportingContent = { Text(url, style = MaterialTheme.typography.bodySmall) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                        modifier = Modifier.clickable { open(url) },
                    )
                }
            }
            Column(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.report_title)) },
                    supportingContent = { Text(stringResource(R.string.report_hint)) },
                    trailingContent = { Icon(Icons.Default.BugReport, null) },
                    modifier = Modifier.clickable(onClick = onReport),
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.logs_title)) },
                    supportingContent = { Text(stringResource(R.string.logs_hint)) },
                    trailingContent = { Icon(Icons.Default.Article, null) },
                    modifier = Modifier.clickable(onClick = onLogs),
                )
            }
            Text(stringResource(R.string.about_credits), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}
