package com.example.musicApp.ui.screens.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsMenuLink
import com.example.musicApp.data.repository.CrashReportRepository
import com.example.musicApp.viewmodels.AppViewModel
import com.example.musicApp.viewmodels.UpdateCheckState
import com.example.musicApp.viewmodels.UpdateStatus
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SupportSettingsGroup(
    appViewModel: AppViewModel,
    colors: ListItemColors
) {
    val updateCheckState by appViewModel.checkState.collectAsState()
    val updateStatus by appViewModel.updateStatus.collectAsState()
    val pendingCrashReports = remember { CrashReportRepository.getUnsentReports().size }

    SettingsGroup(
        title = { Text(stringResource(Res.string.section_support)) },
        colors = colors,
    ) {
        val downloading = updateStatus as? UpdateStatus.Downloading
        val ready = updateStatus is UpdateStatus.Ready
        SettingsMenuLink(
            icon = { Icon(Icons.Rounded.SystemUpdate, null) },
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
            title = { Text(stringResource(Res.string.check_updates)) },
            subtitle = {
                Text(
                    when {
                        ready -> stringResource(Res.string.check_updates_ready)
                        downloading != null -> {
                            val pct = downloading.progress
                            if (pct >= 0f) "${stringResource(Res.string.check_updates_downloading)} ${(pct * 100).toInt()}%"
                            else stringResource(Res.string.check_updates_downloading)
                        }
                        updateCheckState is UpdateCheckState.Checking -> stringResource(Res.string.check_updates_checking)
                        updateCheckState is UpdateCheckState.UpToDate -> stringResource(Res.string.check_updates_up_to_date)
                        updateCheckState is UpdateCheckState.Failed -> stringResource(Res.string.check_updates_failed)
                        else -> stringResource(Res.string.check_updates_subtitle)
                    }
                )
            },
            colors = colors,
            action = {
                when {
                    ready -> {
                        FilledTonalButton(onClick = { appViewModel.checkForUpdates(manual = true) }) {
                            Text(stringResource(Res.string.btn_install_update))
                        }
                    }
                    downloading != null || updateCheckState is UpdateCheckState.Checking ->
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else -> TextButton(onClick = { appViewModel.checkForUpdates(manual = true) }) {
                        Text(stringResource(Res.string.btn_check))
                    }
                }
            },
            onClick = {
                if (ready) appViewModel.checkForUpdates(manual = true)
                else if (downloading == null && updateCheckState !is UpdateCheckState.Checking)
                    appViewModel.checkForUpdates(manual = true)
            }
        )
        ActionRow(
            label = stringResource(Res.string.report_bug),
            subtitle = stringResource(Res.string.report_bug_subtitle),
            icon = Icons.Rounded.BugReport,
            btnLabel = stringResource(Res.string.btn_report),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 1, count = 3),
            onClick = { openReportBugPage() },
            colors = colors
        )
        SettingsMenuLink(
            icon = {
                if (pendingCrashReports > 0) {
                    BadgedBox(
                        badge = {
                            Badge(containerColor = MaterialTheme.colorScheme.error) {
                                Text("$pendingCrashReports")
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, null)
                    }
                } else {
                    Icon(Icons.Rounded.BugReport, null)
                }
            },
            shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
            title = { Text(stringResource(Res.string.send_crash_report)) },
            subtitle = {
                Text(
                    if (pendingCrashReports > 0)
                        stringResource(Res.string.crash_pending_count, pendingCrashReports)
                    else
                        stringResource(Res.string.no_crash_reports)
                )
            },
            colors = colors,
            action = {
                if (pendingCrashReports > 0) {
                    FilledTonalButton(onClick = {
                        val reports = CrashReportRepository.getUnsentReports()
                        reports.forEach { (_, report) ->
                            CrashReportRepository.openCrashAsGitHubIssue(report)
                        }
                        CrashReportRepository.markAllAsSent()
                    }) {
                        Text(stringResource(Res.string.crash_send))
                    }
                }
            },
            onClick = {
                val reports = CrashReportRepository.getUnsentReports()
                if (reports.isNotEmpty()) {
                    reports.forEach { (_, report) ->
                        CrashReportRepository.openCrashAsGitHubIssue(report)
                    }
                    CrashReportRepository.markAllAsSent()
                }
            }
        )
    }
}
