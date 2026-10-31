package com.example.ethos.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ethos.R
import com.example.ethos.data.HabitType
import com.example.ethos.data.RecurrenceType
import com.example.ethos.ui.AppViewModelProvider
import com.example.ethos.ui.DATE_FMT
import com.example.ethos.ui.WEEKDAY_SHORT
import com.example.ethos.ui.components.CardTitle
import com.example.ethos.ui.components.EthosCard
import com.example.ethos.ui.components.EthosTopBar
import com.example.ethos.ui.components.HairlineDivider
import com.example.ethos.ui.components.IntStepper
import com.example.ethos.ui.components.OptionalDateField
import com.example.ethos.ui.components.TypeSymbol
import com.example.ethos.ui.components.Pill
import com.example.ethos.ui.theme.typeColor
import com.example.ethos.ui.theme.typeContainerColor

@Composable
fun HabitEditScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    vm: HabitEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val form = vm.form
    val isBuild = form.type == HabitType.BUILD
    val accent = typeColor(form.type)
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val lawLabels = stringArrayResource(if (isBuild) R.array.build_law_labels else R.array.avoid_law_labels)
    val lawHints = stringArrayResource(if (isBuild) R.array.build_law_hints else R.array.avoid_law_hints)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EthosTopBar(
                title = stringResource(if (vm.isEditing) R.string.habit_edit_title else R.string.habit_new_title),
                onBack = onBack,
                actions = {
                    TextButton(enabled = form.canSave, onClick = { vm.save(onBack) }) { Text(stringResource(R.string.save)) }
                },
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
            // Type
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TypeTile(
                    type = HabitType.BUILD,
                    label = stringResource(R.string.type_build_long),
                    selected = isBuild,
                    onClick = { vm.update { it.copy(type = HabitType.BUILD) } },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                TypeTile(
                    type = HabitType.AVOID,
                    label = stringResource(R.string.type_avoid_long),
                    selected = !isBuild,
                    onClick = { vm.update { it.copy(type = HabitType.AVOID) } },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }

            // Basics
            EthosCard(spacing = 12.dp) {
                OutlinedTextField(
                    value = form.title,
                    onValueChange = { v -> vm.update { it.copy(title = v) } },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.description,
                    onValueChange = { v -> vm.update { it.copy(description = v) } },
                    label = { Text(stringResource(R.string.field_description)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // The four laws
            EthosCard(spacing = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardTitle(stringResource(R.string.four_laws), Modifier.weight(1f))
                    Pill(
                        stringResource(R.string.laws_filled, form.filledLaws),
                        if (form.filledLaws == 4) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                lawLabels.forEachIndexed { index, label ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberBadge(index + 1, accent, Modifier.padding(top = 14.dp))
                        OutlinedTextField(
                            value = form.laws[index],
                            onValueChange = { vm.setLaw(index, it) },
                            label = { Text("$label *") },
                            placeholder = { Text(lawHints[index]) },
                            minLines = 2,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Personal motivation (shown when the habit was missed once)
            EthosCard(spacing = 12.dp) {
                CardTitle(stringResource(R.string.motivation))
                OutlinedTextField(
                    value = form.motivation,
                    onValueChange = { v -> vm.update { it.copy(motivation = v) } },
                    label = { Text(stringResource(R.string.field_motivation)) },
                    placeholder = { Text(stringResource(R.string.motivation_hint)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Schedule
            EthosCard(spacing = 12.dp) {
                CardTitle(stringResource(R.string.schedule))
                val weekly = form.recurrence == RecurrenceType.TIMES_PER_WEEK
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactWeekdayPicker(
                        selected = form.weekdays,
                        enabled = !weekly,
                        accent = accent,
                        onChange = vm::setWeekdays,
                        modifier = Modifier.weight(1f),
                    )
                    if (isBuild) {
                        FrequencyToggle(selected = weekly, accent = accent, onClick = vm::toggleTimesPerWeek)
                    }
                }
                if (weekly) {
                    IntStepper(
                        label = stringResource(R.string.weekly_goal),
                        value = form.weeklyTarget,
                        onChange = { v -> vm.update { it.copy(weeklyTarget = v) } },
                        min = 1,
                        max = 7,
                        suffix = "×",
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.catch_up), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.catch_up_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = form.catchUp,
                            onCheckedChange = { v -> vm.update { it.copy(catchUp = v) } },
                        )
                    }
                }
                HairlineDivider()
                OptionalDateField(stringResource(R.string.start_date), form.startDate) { v -> vm.update { it.copy(startDate = v) } }
                OptionalDateField(stringResource(R.string.end_date), form.endDate) { v -> vm.update { it.copy(endDate = v) } }
            }

            if (vm.isEditing) {
                // Own row: three buttons do not fit side by side on narrow screens.
                TextButton(onClick = { confirmReset = true }) { Text(stringResource(R.string.reset_stats)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.toggleArchive(onBack) }) {
                        Text(stringResource(if (form.isArchived) R.string.reactivate else R.string.archive))
                    }
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_stats_title)) },
            text = {
                Text(stringResource(R.string.reset_stats_text))
            },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; vm.resetStats() }) {
                    Text(stringResource(R.string.reset), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    vm.pendingPurge?.let { count ->
        val start = form.startDate?.format(DATE_FMT).orEmpty()
        AlertDialog(
            onDismissRequest = vm::cancelPurge,
            title = { Text(stringResource(R.string.purge_title)) },
            text = {
                Text(stringResource(R.string.purge_text, start, count))
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmPurge(onBack) }) {
                    Text(stringResource(R.string.purge_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = vm::cancelPurge) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_habit_title, form.title)) },
            text = { Text(stringResource(R.string.delete_habit_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(onDeleted) }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun TypeTile(type: HabitType, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) typeContainerColor(type) else cs.surfaceContainerLow)
            .border(if (selected) 2.dp else 1.dp, if (selected) typeColor(type) else cs.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TypeSymbol(type, iconSize = 32.dp)
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NumberBadge(number: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) { Text("$number", style = MaterialTheme.typography.labelMedium, color = color) }
}

/** Seven day circles share the available width, so they shrink on narrow screens instead of being cut off. */
@Composable
private fun CompactWeekdayPicker(
    selected: Set<Int>,
    enabled: Boolean,
    accent: Color,
    onChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.alpha(if (enabled) 1f else 0.38f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        WEEKDAY_SHORT.forEachIndexed { index, label ->
            val day = index + 1
            val on = day in selected
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(if (on) accent else cs.surfaceVariant)
                    .then(
                        if (enabled) Modifier.clickable { onChange(if (on) selected - day else selected + day) }
                        else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (on) cs.onPrimary else cs.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun FrequencyToggle(selected: Boolean, accent: Color, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent else cs.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.several_per_week),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) cs.onPrimary else cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
