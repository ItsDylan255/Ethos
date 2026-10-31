package com.example.ethos.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ethos.R
import com.example.ethos.data.Habit
import com.example.ethos.data.HabitType
import com.example.ethos.data.LogStatus
import com.example.ethos.data.RecurrenceType
import com.example.ethos.domain.DayState
import com.example.ethos.ui.AppViewModelProvider
import com.example.ethos.ui.WEEKDAY_SHORT
import com.example.ethos.ui.components.EmptyState
import com.example.ethos.ui.components.EthosCard
import com.example.ethos.ui.components.EthosTopBar
import com.example.ethos.ui.components.FutureDatePickerDialog
import com.example.ethos.ui.components.NoteDialog
import com.example.ethos.ui.components.Pill
import com.example.ethos.ui.components.ProgressBar
import com.example.ethos.ui.components.SectionLabel
import com.example.ethos.ui.displayTitle
import com.example.ethos.ui.localizedFormatter
import com.example.ethos.ui.playCompletionSound
import com.example.ethos.ui.theme.typeColor
import kotlinx.coroutines.launch
import java.time.LocalDate

private val HEADER_DATE get() = localizedFormatter("EEEE, d. MMMM", "EEEE, MMMM d")
private val SHORT_DATE get() = localizedFormatter("EEE, d. MMM", "EEE, MMM d")

private data class NoteTarget(
    val habitId: Long,
    val habitTitle: String,
    val label: String,
    val initial: String,
    val afterFailure: Boolean,
    /** Skipping today: the reason is required and saving skips the day. */
    val skip: Boolean = false,
)

/** Everything a card can do; keeps the card signature short. */
private class CardActions(
    val onSet: (LogStatus?) -> Unit,
    val onFailed: () -> Unit,
    val onNote: () -> Unit,
    val onOpen: () -> Unit,
    val onSkip: () -> Unit,
    val onMoveTomorrow: () -> Unit,
    val onPickDay: () -> Unit,
    val onUndoMove: () -> Unit,
)

@Composable
fun TodayScreen(
    onAddHabit: () -> Unit,
    onOpenHabit: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    vm: TodayViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var noteTarget by remember { mutableStateOf<NoteTarget?>(null) }
    var moveFor by remember { mutableStateOf<TodayItem?>(null) }
    var showMotivation by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun target(item: TodayItem, afterFailure: Boolean) = NoteTarget(
        habitId = item.habit.id,
        habitTitle = item.habit.title,
        label = when {
            !afterFailure -> context.getString(R.string.note)
            item.habit.type == HabitType.BUILD -> context.getString(R.string.why_failed)
            else -> context.getString(R.string.today_what_led)
        },
        initial = item.note.orEmpty(),
        afterFailure = afterFailure,
    )

    fun announce(message: String, habitId: Long) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = context.getString(R.string.undo), duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) vm.undoException(habitId)
        }
    }

    fun actionsFor(item: TodayItem) = CardActions(
        onSet = {
            if (it == LogStatus.COMPLETED) playCompletionSound(context)
            vm.setStatus(item.habit.id, it)
        },
        onFailed = { noteTarget = target(item, afterFailure = true) },
        onNote = { noteTarget = target(item, afterFailure = false) },
        onOpen = { onOpenHabit(item.habit.id) },
        onSkip = {
            noteTarget = NoteTarget(
                habitId = item.habit.id,
                habitTitle = item.habit.title,
                label = context.getString(R.string.today_skip_why),
                initial = item.note.orEmpty(),
                afterFailure = false,
                skip = true,
            )
        },
        onMoveTomorrow = {
            vm.moveToday(item.habit.id, state.date.plusDays(1))
            announce(context.getString(R.string.today_moved_tomorrow), item.habit.id)
        },
        onPickDay = { moveFor = item },
        onUndoMove = { vm.undoException(item.habit.id) },
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            EthosTopBar(
                title = stringResource(R.string.tab_today),
                subtitle = state.date.format(HEADER_DATE),
                actions = {
                    IconButton(onClick = onAddHabit) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_habit))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        if (state.items.isEmpty()) {
            EmptyState(
                stringResource(R.string.today_empty),
                Modifier.padding(padding),
            )
        } else {
            val build = state.items.filter { it.habit.type == HabitType.BUILD }
            val avoid = state.items.filter { it.habit.type == HabitType.AVOID }
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { SummaryCard(build, avoid) }
                if (state.hints.isNotEmpty()) {
                    item {
                        MissedHintCard(
                            habits = state.hints,
                            onOpen = { showMotivation = true },
                            onDismiss = vm::dismissHints,
                        )
                    }
                }
                if (build.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.type_build), Modifier.padding(top = 6.dp)) }
                    items(build, key = { it.habit.id }) { item -> TodayCard(item, actionsFor(item)) }
                }
                if (avoid.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.type_avoid), Modifier.padding(top = 6.dp)) }
                    items(avoid, key = { it.habit.id }) { item -> TodayCard(item, actionsFor(item)) }
                }
            }
        }
    }

    noteTarget?.let { t ->
        NoteDialog(
            title = t.habitTitle,
            label = t.label,
            initial = t.initial,
            dismissLabel = stringResource(if (t.afterFailure) R.string.note_later else R.string.cancel),
            confirmLabel = stringResource(if (t.skip) R.string.skip_day else R.string.save),
            required = t.skip,
            onSave = { text ->
                if (t.skip) {
                    vm.skipToday(t.habitId, text)
                    announce(context.getString(R.string.today_skipped), t.habitId)
                } else {
                    vm.saveNote(t.habitId, text)
                }
                noteTarget = null
            },
            onDismiss = { noteTarget = null },
        )
    }

    moveFor?.let { item ->
        FutureDatePickerDialog(
            minDate = state.date.plusDays(1),
            onPick = { day ->
                vm.moveToday(item.habit.id, day)
                announce(context.getString(R.string.moved_to, day.format(SHORT_DATE)), item.habit.id)
                moveFor = null
            },
            onDismiss = { moveFor = null },
        )
    }

    if (showMotivation && state.hints.isNotEmpty()) {
        MotivationSheet(state.hints, onDismiss = { showMotivation = false })
    }
}

@Composable
private fun SummaryCard(build: List<TodayItem>, avoid: List<TodayItem>) {
    val cs = MaterialTheme.colorScheme
    val done = build.count { it.state == DayState.COMPLETED }
    val held = avoid.count { it.state == DayState.AVOIDED }
    EthosCard(spacing = 8.dp) {
        if (build.isNotEmpty()) {
            Text(stringResource(R.string.today_done_of, done, build.size), style = MaterialTheme.typography.headlineSmall)
            ProgressBar(done.toFloat() / build.size, cs.primary)
        }
        if (avoid.isNotEmpty()) {
            if (build.isNotEmpty()) Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.today_held_of, held, avoid.size),
                style = if (build.isEmpty()) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium,
                color = if (build.isEmpty()) cs.onSurface else cs.tertiary,
            )
            ProgressBar(held.toFloat() / avoid.size, cs.tertiary)
        }
    }
}

/** "Never miss twice": habits missed once. Tap to see each one with its own motivation. */
@Composable
private fun MissedHintCard(habits: List<Habit>, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    EthosCard(onClick = onOpen, spacing = 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.never_miss_twice).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = cs.tertiary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.hide)) }
        }
        Text(habits.joinToString(", ") { it.title }, style = MaterialTheme.typography.titleMedium)
        Text(
            pluralStringResource(R.plurals.missed_hint, habits.size, habits.size),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}

/** All habits that were missed once, each with the motivation the user wrote for it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MotivationSheet(habits: List<Habit>, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.never_miss_twice), style = MaterialTheme.typography.headlineSmall)
            habits.forEach { habit ->
                EthosCard(spacing = 6.dp) {
                    Text(habit.title, style = MaterialTheme.typography.titleMedium, color = typeColor(habit.type))
                    if (habit.motivation.isBlank()) {
                        Text(
                            stringResource(R.string.motivation_missing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(habit.motivation, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayCard(item: TodayItem, actions: CardActions) {
    val habit = item.habit
    val isBuild = habit.type == HabitType.BUILD
    val accent = typeColor(habit.type)
    val cs = MaterialTheme.colorScheme

    val weekText = item.weekTarget?.let { target ->
        if (item.weekExtra > 0) stringResource(R.string.week_progress_extra, item.weekDone ?: 0, target, item.weekExtra)
        else stringResource(R.string.week_progress, item.weekDone ?: 0, target)
    }
    val movedText = item.movedFrom?.let { stringResource(R.string.moved_from, WEEKDAY_SHORT[it.dayOfWeek.value - 1]) }
    val failed = item.state == DayState.FAILED
    val avoidText = stringResource(if (failed) R.string.status_done_anyway else R.string.status_avoided)
    val streak = item.streak ?: 0
    val streakText = pluralStringResource(R.plurals.streak_days, streak, streak)
    val pills = buildList<Pair<String, Color>> {
        if (weekText != null) add(weekText to accent)
        if (movedText != null) add(movedText to cs.tertiary)
        if (!isBuild) add(avoidText to if (failed) cs.error else accent)
        if (streak >= 2) add(streakText to cs.onSurfaceVariant)
    }

    EthosCard(
        onClick = actions.onOpen,
        contentPadding = PaddingValues(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusMark(
                state = item.state,
                accent = accent,
                onClick = {
                    if (isBuild) {
                        // open -> done -> not done -> open
                        when (item.status) {
                            null -> actions.onSet(LogStatus.COMPLETED)
                            LogStatus.COMPLETED -> {
                                actions.onSet(LogStatus.FAILED)
                                actions.onFailed()
                            }
                            LogStatus.FAILED -> actions.onSet(null)
                        }
                    } else if (item.state == DayState.FAILED) {
                        // done anyway -> avoided again
                        actions.onSet(null)
                    } else {
                        // avoided -> done anyway
                        actions.onSet(LogStatus.FAILED)
                        actions.onFailed()
                    }
                },
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(habit.displayTitle(), style = MaterialTheme.typography.titleMedium)
                if (pills.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) { pills.forEach { (text, color) -> Pill(text, color) } }
                }
                if (item.note != null) {
                    Text(
                        item.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Three-dot menu for both types; both can skip a day, moving only exists for habits you build.
            var menu by remember { mutableStateOf(false) }
            val weekdays = isBuild && item.recurrence == RecurrenceType.WEEKDAYS
            val canSkip = item.recurrence != RecurrenceType.TIMES_PER_WEEK
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (isBuild) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.status_done)) }, onClick = { actions.onSet(LogStatus.COMPLETED); menu = false })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.status_not_done)) },
                            onClick = { actions.onSet(LogStatus.FAILED); menu = false; actions.onFailed() },
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.reset_today)) }, onClick = { actions.onSet(null); menu = false })
                        DropdownMenuItem(
                            text = { Text(stringResource(if (item.note == null) R.string.add_note else R.string.edit_note)) },
                            onClick = { menu = false; actions.onNote() },
                        )
                        } else {
                        DropdownMenuItem(text = { Text(stringResource(R.string.status_avoided)) }, onClick = { actions.onSet(null); menu = false })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.status_done_anyway)) },
                            onClick = { actions.onSet(LogStatus.FAILED); menu = false; actions.onFailed() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (item.note == null) R.string.add_note else R.string.edit_note)) },
                            onClick = { menu = false; actions.onNote() },
                        )
                        }
                        if (canSkip || item.movedFrom != null) HorizontalDivider()
                        // Fixed weekdays can be moved; every non-weekly schedule can skip a day.
                        if (item.movedFrom != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.undo_move)) },
                                onClick = { menu = false; actions.onUndoMove() },
                            )
                        }
                        if (weekdays) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.move_tomorrow)) },
                                onClick = { menu = false; actions.onMoveTomorrow() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.pick_day)) },
                                onClick = { menu = false; actions.onPickDay() },
                            )
                        }
                        if (canSkip) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.skip_today)) },
                                onClick = { menu = false; actions.onSkip() },
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun StatusMark(state: DayState, accent: Color, onClick: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    val ring = when (state) {
        DayState.COMPLETED -> accent
        DayState.AVOIDED -> accent.copy(alpha = 0.55f)
        DayState.FAILED -> cs.error
        DayState.NOT_RECORDED -> cs.outline
    }
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (state == DayState.COMPLETED) accent else Color.Transparent)
            .border(1.5.dp, ring, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            DayState.COMPLETED -> Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = cs.onPrimary)
            DayState.AVOIDED -> Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = accent)
            DayState.FAILED -> Icon(Icons.Default.Close, null, Modifier.size(18.dp), tint = cs.error)
            DayState.NOT_RECORDED -> Unit
        }
    }
}
