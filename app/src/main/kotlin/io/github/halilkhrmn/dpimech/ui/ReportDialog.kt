package io.github.halilkhrmn.dpimech.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import io.github.halilkhrmn.dpimech.R
import io.github.halilkhrmn.dpimech.core.ProblemReport

/**
 * Shows the report before anything is sent; the user picks GitHub (an issue form filled in, the
 * newest log lines that fit) or e-mail (the whole report), or copies it.
 */
@Composable
fun ReportDialog(report: ProblemReport, onDismiss: () -> Unit) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val title = stringResource(R.string.report_issue_title)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_title)) },
        text = {
            Column {
                Text(stringResource(R.string.report_text), style = MaterialTheme.typography.bodyMedium)
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    Text(report.fullText, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                }
            }
        },
        confirmButton = {
            Column {
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, report.issueUrl(title).toUri())) }
                    onDismiss()
                }) { Text(stringResource(R.string.report_github)) }
                TextButton(onClick = {
                    val mail = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
                        .putExtra(Intent.EXTRA_EMAIL, arrayOf(ProblemReport.SUPPORT_EMAIL))
                        .putExtra(Intent.EXTRA_SUBJECT, "DPIMech for Android: $title")
                        .putExtra(Intent.EXTRA_TEXT, report.fullText)
                    runCatching { context.startActivity(Intent.createChooser(mail, null)) }
                    onDismiss()
                }) { Text(stringResource(R.string.report_email)) }
                TextButton(onClick = { clipboard.setText(AnnotatedString(report.fullText)); onDismiss() }) {
                    Text(stringResource(R.string.report_copy))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
