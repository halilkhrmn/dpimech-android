package io.github.halilkhrmn.dpimech.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.halilkhrmn.dpimech.R

/** Live engine log: what ByeDPI, the watchdog, the Lab and the automatic strategy did. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(lines: List<String>, onClear: () -> Unit, onReport: () -> Unit, onBack: () -> Unit) {
    @Suppress("DEPRECATION") // the new Clipboard API is suspend-only; this is a plain copy
    val clipboard = LocalClipboardManager.current
    val list = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.scrollToItem(lines.lastIndex) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logs_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(lines.joinToString("\n"))) }) {
                        Icon(Icons.Default.ContentCopy, stringResource(R.string.logs_copy))
                    }
                    IconButton(onClick = onClear) { Icon(Icons.Default.DeleteSweep, stringResource(R.string.logs_clear)) }
                    IconButton(onClick = onReport) { Icon(Icons.Default.BugReport, stringResource(R.string.report_title)) }
                },
            )
        },
    ) { padding ->
        if (lines.isEmpty()) {
            Text(stringResource(R.string.logs_empty), Modifier.padding(padding).padding(16.dp), style = MaterialTheme.typography.bodyLarge)
            return@Scaffold
        }
        SelectionContainer(Modifier.padding(padding)) {
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(12.dp)) {
                items(lines) { line ->
                    Text(line, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}
