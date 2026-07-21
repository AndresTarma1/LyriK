package com.example.musicApp.bootstrap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicApp.data.repository.CrashReport
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import java.io.File

@Composable
fun CrashReportDialog(
    reports: List<Pair<File, CrashReport>>,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (reports.isEmpty()) return

    val count = reports.size
    val latest = reports.first().second

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (count == 1) stringResource(Res.string.crash_detected)
                else stringResource(Res.string.crash_detected_count, count)
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = if (count == 1) stringResource(Res.string.crash_description)
                    else stringResource(Res.string.crash_description_count, count),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.crash_version_info, latest.appVersion, latest.os),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${latest.exception}: ${latest.message}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.crash_send_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSend) {
                Text(stringResource(Res.string.crash_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.crash_no_send))
            }
        },
    )
}
