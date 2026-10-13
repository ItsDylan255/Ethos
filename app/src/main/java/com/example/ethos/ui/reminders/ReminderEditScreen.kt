package com.example.ethos.ui.reminders

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ethos.data.ReminderMode
import com.example.ethos.ui.AppViewModelProvider
import com.example.ethos.ui.displayTitle
import com.example.ethos.ui.TIME_FMT
import com.example.ethos.ui.components.EthosCard
import com.example.ethos.ui.components.EthosTopBar
import com.example.ethos.ui.components.FormLabel
import com.example.ethos.ui.components.HairlineDivider
import com.example.ethos.ui.components.IntStepper
import com.example.ethos.ui.components.TimeField
import com.example.ethos.ui.components.TimePickerDialog
import com.example.ethos.ui.components.WeekdayPicker
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderEditScreen(
    onBack: () -> Unit,
    vm: ReminderEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val form = vm.form
    val habits by vm.habits.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Save regardless of the answer; without permission the reminder just stays silent.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.save(onBack)
    }
    fun onSave() {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.save(onBack)
    }

    // null = closed, -1 = add new time, >= 0 = edit time at index
    var timeDialog by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EthosTopBar(
                title = if (vm.isEditing) "Erinnerung bearbeiten" else "Neue Erinnerung",
                onBack = onBack,
                actions = { TextButton(enabled = form.canSave, onClick = ::onSave) { Text("Speichern") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          EthosCard(spacing = 14.dp) {
            OutlinedTextField(
                value = form.title,
                onValueChange = { v -> vm.update { it.copy(title = v) } },
                label = { Text(if (form.habitId == null) "Titel" else "Titel (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormLabel("Verknüpfung")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = form.habitId == null,
                        onClick = { vm.update { it.copy(habitId = null) } },
                        label = { Text("Frei") },
                    )
                    habits.forEach { h ->
                        FilterChip(
                            selected = form.habitId == h.id,
                            onClick = { vm.update { it.copy(habitId = h.id) } },
                            label = { Text(h.displayTitle()) },
                        )
                    }
                }
                Text(
                    "Eine Erinnerung hakt das Habit nie automatisch ab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

          }

          EthosCard(spacing = 14.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormLabel("Modus")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ReminderMode.FIXED_TIMES to "Feste Uhrzeiten",
                        ReminderMode.FIXED_INTERVAL to "Intervall",
                        ReminderMode.RANDOM_INTERVAL to "Zufälliges Intervall",
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = form.mode == mode,
                            onClick = { vm.update { it.copy(mode = mode) } },
                            label = { Text(label) },
                        )
                    }
                }
            }

            when (form.mode) {
                ReminderMode.FIXED_TIMES -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormLabel("Uhrzeiten")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        form.fixedTimes.forEachIndexed { index, time ->
                            InputChip(
                                selected = false,
                                onClick = { timeDialog = index },
                                label = { Text(time.format(TIME_FMT)) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Entfernen",
                                        modifier = Modifier.size(18.dp).clickable {
                                            vm.update { it.copy(fixedTimes = it.fixedTimes.filterIndexed { i, _ -> i != index }) }
                                        },
                                    )
                                },
                            )
                        }
                        AssistChip(onClick = { timeDialog = -1 }, label = { Text("+ Zeit") })
                    }
                }

                ReminderMode.FIXED_INTERVAL -> Column {
                    IntStepper(
                        label = "Alle",
                        value = form.intervalMin,
                        onChange = { v -> vm.update { it.copy(intervalMin = v) } },
                        step = 15, min = 5, max = 720, suffix = " Min",
                    )
                    WindowFields(form.windowStart, form.windowEnd) { s, e -> vm.update { it.copy(windowStart = s, windowEnd = e) } }
                }

                ReminderMode.RANDOM_INTERVAL -> Column {
                    IntStepper(
                        label = "Frühestens nach",
                        value = form.intervalMin,
                        onChange = { v -> vm.update { it.copy(intervalMin = v, intervalMax = it.intervalMax.coerceAtLeast(v)) } },
                        step = 15, min = 5, max = 720, suffix = " Min",
                    )
                    IntStepper(
                        label = "Spätestens nach",
                        value = form.intervalMax,
                        onChange = { v -> vm.update { it.copy(intervalMax = v.coerceAtLeast(it.intervalMin)) } },
                        step = 15, min = 5, max = 1440, suffix = " Min",
                    )
                    WindowFields(form.windowStart, form.windowEnd) { s, e -> vm.update { it.copy(windowStart = s, windowEnd = e) } }
                }
            }

          }

          EthosCard(spacing = 14.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormLabel("Wochentage")
                WeekdayPicker(form.weekdays, onChange = { v -> vm.update { it.copy(weekdays = v) } })
            }

            IntStepper(
                label = "Später erinnern nach",
                value = form.snoozeMinutes,
                onChange = { v -> vm.update { it.copy(snoozeMinutes = v) } },
                step = 5, min = 5, max = 120, suffix = " Min",
            )
          }
        }
    }

    timeDialog?.let { index ->
        TimePickerDialog(
            initial = form.fixedTimes.getOrNull(index) ?: LocalTime.of(9, 0),
            onConfirm = { picked ->
                vm.update {
                    val list = if (index < 0) it.fixedTimes + picked
                    else it.fixedTimes.mapIndexed { i, t -> if (i == index) picked else t }
                    it.copy(fixedTimes = list.distinct().sorted())
                }
                timeDialog = null
            },
            onDismiss = { timeDialog = null },
        )
    }
}

@Composable
private fun WindowFields(start: LocalTime, end: LocalTime, onChange: (LocalTime, LocalTime) -> Unit) {
    Column {
        TimeField("Von", start) { onChange(it, end) }
        TimeField("Bis", end) { onChange(start, it) }
    }
}
