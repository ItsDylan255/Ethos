package com.example.ethos.ui.settings

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ethos.R
import com.example.ethos.data.AppLanguage
import com.example.ethos.data.AppLocale
import com.example.ethos.data.ThemeMode
import com.example.ethos.ui.AppViewModelProvider
import com.example.ethos.ui.components.CardTitle
import com.example.ethos.ui.components.EthosCard
import com.example.ethos.ui.components.EthosTopBar
import com.example.ethos.ui.components.HairlineDivider
import com.example.ethos.ui.components.TimeField
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askNotificationPermission() {
        val missing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (missing) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) vm.export(uri) }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> if (uri != null) vm.exportCsv(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) pendingImport = uri }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { EthosTopBar(title = stringResource(R.string.settings), onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EthosCard(spacing = 12.dp) {
                CardTitle(stringResource(R.string.appearance))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                        ThemeMode.LIGHT to stringResource(R.string.theme_light),
                        ThemeMode.DARK to stringResource(R.string.theme_dark),
                    )
                        .forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { vm.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, 3),
                            ) { Text(label) }
                        }
                }
                SettingRow(
                    title = stringResource(R.string.material_you),
                    subtitle = stringResource(if (supportsDynamic) R.string.dynamic_on else R.string.dynamic_off),
                    checked = settings.dynamicColor && supportsDynamic,
                    enabled = supportsDynamic,
                    onChange = vm::setDynamicColor,
                )
            }

            EthosCard(spacing = 12.dp) {
                CardTitle(stringResource(R.string.language))
                // Stored outside DataStore and applied by recreating the screen (see AppLocale).
                val language = remember { AppLocale.get(context) }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(
                        AppLanguage.SYSTEM to stringResource(R.string.language_system),
                        AppLanguage.GERMAN to stringResource(R.string.language_de),
                        AppLanguage.ENGLISH to stringResource(R.string.language_en),
                    ).forEachIndexed { index, (option, label) ->
                        SegmentedButton(
                            selected = language == option,
                            onClick = {
                                if (option != language) {
                                    vm.setLanguage(option)
                                    (context as? Activity)?.recreate()
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, 3),
                        ) { Text(label) }
                    }
                }
            }

            EthosCard(spacing = 12.dp) {
                CardTitle(stringResource(R.string.hints))
                SettingRow(
                    title = stringResource(R.string.never_miss_twice),
                    subtitle = stringResource(R.string.missed_hint_sub),
                    checked = settings.missedHint,
                    enabled = true,
                    onChange = vm::setMissedHint,
                )
            }

            EthosCard(spacing = 12.dp) {
                CardTitle(stringResource(R.string.notifications))
                SettingRow(
                    title = stringResource(R.string.daily_overview),
                    subtitle = stringResource(R.string.daily_overview_sub),
                    checked = settings.dailyReportEnabled,
                    enabled = true,
                    onChange = { on -> vm.setDailyReport(on); if (on) askNotificationPermission() },
                )
                if (settings.dailyReportEnabled) {
                    TimeField(stringResource(R.string.time), minutesToTime(settings.dailyReportMinutes)) {
                        vm.setDailyReportTime(it.hour * 60 + it.minute)
                    }
                }
                HairlineDivider()
                SettingRow(
                    title = stringResource(R.string.motivation_notif),
                    subtitle = stringResource(R.string.motivation_notif_sub),
                    checked = settings.motivationEnabled,
                    enabled = true,
                    onChange = { on -> vm.setMotivation(on); if (on) askNotificationPermission() },
                )
                if (settings.motivationEnabled) {
                    TimeField(stringResource(R.string.time), minutesToTime(settings.motivationMinutes)) {
                        vm.setMotivationTime(it.hour * 60 + it.minute)
                    }
                }
            }

            EthosCard(spacing = 12.dp) {
                CardTitle(stringResource(R.string.backup))
                Text(
                    stringResource(R.string.backup_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("ethos-backup-${LocalDate.now()}.json") },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.export)) }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.import_)) }
                }
                HairlineDivider()
                Text(
                    stringResource(R.string.csv_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val csvPrefix = stringResource(R.string.csv_file_prefix)
                OutlinedButton(
                    onClick = { csvLauncher.launch("$csvPrefix-${LocalDate.now()}.csv") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.csv_export)) }
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.replace_title)) },
            text = { Text(stringResource(R.string.replace_text)) },
            confirmButton = {
                TextButton(onClick = { vm.importFrom(uri); pendingImport = null }) {
                    Text(stringResource(R.string.replace), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

private fun minutesToTime(minutes: Int): LocalTime = LocalTime.of((minutes / 60).coerceIn(0, 23), minutes % 60)
