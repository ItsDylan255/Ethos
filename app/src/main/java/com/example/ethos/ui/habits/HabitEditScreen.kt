package com.example.ethos.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ethos.data.HabitType
import com.example.ethos.data.RecurrenceType
import com.example.ethos.ui.AppViewModelProvider
import com.example.ethos.ui.components.EthosTopBar
import com.example.ethos.ui.components.FormLabel
import com.example.ethos.ui.components.HairlineDivider
import com.example.ethos.ui.components.IconSuggestions
import com.example.ethos.ui.components.IntStepper
import com.example.ethos.ui.components.OptionalDateField
import com.example.ethos.ui.components.WeekdayPicker

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitEditScreen(
    onBack: () -> Unit,
    vm: HabitEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val form = vm.form
    val categories by vm.categories.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EthosTopBar(
                title = if (vm.isEditing) "Habit bearbeiten" else "Neues Habit",
                onBack = onBack,
                actions = {
                    TextButton(enabled = form.canSave, onClick = { vm.save(onBack) }) { Text("Speichern") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OutlinedTextField(
                value = form.title,
                onValueChange = { v -> vm.update { it.copy(title = v) } },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> vm.update { it.copy(description = v) } },
                label = { Text("Beschreibung (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormLabel("Symbol")
                IconSuggestions(selected = form.icon, onSelect = { v -> vm.update { it.copy(icon = v) } })
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormLabel("Art")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(HabitType.BUILD to "Aufbauen", HabitType.AVOID to "Vermeiden")
                        .forEachIndexed { index, (type, label) ->
                            SegmentedButton(
                                selected = form.type == type,
                                onClick = { vm.update { it.copy(type = type) } },
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                            ) { Text(label) }
                        }
                }
                Text(
                    if (form.type == HabitType.BUILD) "Du bestätigst jeden Tag selbst, ob du es gemacht hast."
                    else "Gilt als geschafft, solange du nichts meldest. Rückfälle trägst du ein.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (categories.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormLabel("Kategorie")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = form.categoryId == null,
                            onClick = { vm.update { it.copy(categoryId = null) } },
                            label = { Text("Keine") },
                        )
                        categories.forEach { c ->
                            FilterChip(
                                selected = form.categoryId == c.id,
                                onClick = { vm.update { it.copy(categoryId = c.id) } },
                                label = { Text(listOf(c.icon, c.name).filter { s -> s.isNotBlank() }.joinToString(" ")) },
                            )
                        }
                    }
                }
            }

            HairlineDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormLabel("Zeitplan")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = form.recurrence == RecurrenceType.DAILY,
                        onClick = { vm.update { it.copy(recurrence = RecurrenceType.DAILY) } },
                        label = { Text("Täglich") },
                    )
                    FilterChip(
                        selected = form.recurrence == RecurrenceType.WEEKDAYS,
                        onClick = {
                            vm.update {
                                it.copy(
                                    recurrence = RecurrenceType.WEEKDAYS,
                                    weekdays = it.weekdays.ifEmpty { setOf(1, 2, 3, 4, 5) },
                                )
                            }
                        },
                        label = { Text("Wochentage") },
                    )
                    if (form.type == HabitType.BUILD) {
                        FilterChip(
                            selected = form.recurrence == RecurrenceType.TIMES_PER_WEEK,
                            onClick = { vm.update { it.copy(recurrence = RecurrenceType.TIMES_PER_WEEK) } },
                            label = { Text("Mehrmals pro Woche") },
                        )
                    }
                }
                when (form.recurrence) {
                    RecurrenceType.WEEKDAYS -> WeekdayPicker(
                        selected = form.weekdays,
                        onChange = { v -> vm.update { it.copy(weekdays = v) } },
                    )
                    RecurrenceType.TIMES_PER_WEEK -> IntStepper(
                        label = "Ziel pro Woche",
                        value = form.weeklyTarget,
                        onChange = { v -> vm.update { it.copy(weeklyTarget = v) } },
                        min = 1,
                        max = 7,
                        suffix = "×",
                    )
                    RecurrenceType.DAILY -> Unit
                }
            }

            Column {
                OptionalDateField("Startdatum", form.startDate) { v -> vm.update { it.copy(startDate = v) } }
                OptionalDateField("Enddatum", form.endDate) { v -> vm.update { it.copy(endDate = v) } }
            }

            if (vm.isEditing) {
                HairlineDivider()
                TextButton(onClick = { vm.toggleArchive(onBack) }) {
                    Text(if (form.isArchived) "Reaktivieren" else "Archivieren")
                }
            }
        }
    }
}
