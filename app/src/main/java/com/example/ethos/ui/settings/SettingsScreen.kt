package com.example.ethos.ui.settings

import android.Manifest
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
        topBar = { EthosTopBar(title = "Einstellungen", onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EthosCard(spacing = 12.dp) {
                CardTitle("Darstellung")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Hell", ThemeMode.DARK to "Dunkel")
                        .forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { vm.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, 3),
                            ) { Text(label) }
                        }
                }
                SettingRow(
                    title = "Material You",
                    subtitle = if (supportsDynamic) "Farben vom Hintergrundbild" else "Erst ab Android 12 verfügbar",
                    checked = settings.dynamicColor && supportsDynamic,
                    enabled = supportsDynamic,
                    onChange = vm::setDynamicColor,
                )
            }

            EthosCard(spacing = 12.dp) {
                CardTitle("Hinweise")
                SettingRow(
                    title = "Nie zweimal verpassen",
                    subtitle = "Dezenter Hinweis, wenn ein Habit gestern ausgefallen ist",
                    checked = settings.missedHint,
                    enabled = true,
                    onChange = vm::setMissedHint,
                )
            }

            EthosCard(spacing = 12.dp) {
                CardTitle("Benachrichtigungen")
                SettingRow(
                    title = "Tagesübersicht",
                    subtitle = "Alle Habits des Tages, in der Reihenfolge von „Heute“",
                    checked = settings.dailyReportEnabled,
                    enabled = true,
                    onChange = { on -> vm.setDailyReport(on); if (on) askNotificationPermission() },
                )
                if (settings.dailyReportEnabled) {
                    TimeField("Uhrzeit", minutesToTime(settings.dailyReportMinutes)) {
                        vm.setDailyReportTime(it.hour * 60 + it.minute)
                    }
                }
                HairlineDivider()
                SettingRow(
                    title = "Motivation bei ausgelassenem Habit",
                    subtitle = "Deine eigene Motivation, wenn du ein Habit einmal ausgelassen hast",
                    checked = settings.motivationEnabled,
                    enabled = true,
                    onChange = { on -> vm.setMotivation(on); if (on) askNotificationPermission() },
                )
                if (settings.motivationEnabled) {
                    TimeField("Uhrzeit", minutesToTime(settings.motivationMinutes)) {
                        vm.setMotivationTime(it.hour * 60 + it.minute)
                    }
                }
            }

            EthosCard(spacing = 12.dp) {
                CardTitle("Datensicherung")
                Text(
                    "Speichert Habits, Verlauf, Notizen und Erinnerungen als Datei. " +
                        "Beim Importieren werden alle aktuellen Daten ersetzt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("ethos-backup-${LocalDate.now()}.json") },
                        modifier = Modifier.weight(1f),
                    ) { Text("Exportieren") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Importieren") }
                }
                HairlineDivider()
                Text(
                    "Auswertung: eine Tabelle (CSV) mit einer Zeile pro Habit und Tag – Status, Erfolg (1/0) und Notiz. " +
                        "Öffnet sich direkt in Excel oder LibreOffice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { csvLauncher.launch("ethos-auswertung-${LocalDate.now()}.csv") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Als Tabelle exportieren") }
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Daten ersetzen?") },
            text = { Text("Alle aktuellen Daten werden durch die Sicherung ersetzt. Das lässt sich nicht rückgängig machen.") },
            confirmButton = {
                TextButton(onClick = { vm.importFrom(uri); pendingImport = null }) {
                    Text("Ersetzen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Abbrechen") } },
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
