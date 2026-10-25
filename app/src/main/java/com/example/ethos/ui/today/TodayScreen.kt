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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.example.ethos.ui.theme.typeColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HEADER_DATE = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val SHORT_DATE = DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN)

private data class NoteTarget(
    val habitId: Long,
    val habitTitle: String,
    val label: String,
    val initial: String,
    val afterFailure: Boolean,
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

    fun target(item: TodayItem, afterFailure: Boolean) = NoteTarget(
        habitId = item.habit.id,
        habitTitle = item.habit.title,
        label = when {
            !afterFailure -> "Notiz"
            item.habit.type == HabitType.BUILD -> "Warum hat es nicht geklappt?"
            else -> "Was hat dazu geführt?"
        },
        initial = item.note.orEmpty(),
        afterFailure = afterFailure,
    )

    fun announce(message: String, habitId: Long) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = "Rückgängig", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) vm.undoException(habitId)
        }
    }

    fun actionsFor(item: TodayItem) = CardActions(
        onSet = { vm.setStatus(item.habit.id, it) },
        onFailed = { noteTarget = target(item, afterFailure = true) },
        onNote = { noteTarget = target(item, afterFailure = false) },
        onOpen = { onOpenHabit(item.habit.id) },
        onSkip = {
            vm.skipToday(item.habit.id)
            announce("Heute ausgelassen", item.habit.id)
        },
        onMoveTomorrow = {
            vm.moveToday(item.habit.id, state.date.plusDays(1))
            announce("Auf morgen verschoben", item.habit.id)
        },
        onPickDay = { moveFor = item },
        onUndoMove = { vm.undoException(item.habit.id) },
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            EthosTopBar(
                title = "Heute",
                subtitle = state.date.format(HEADER_DATE),
                actions = {
                    IconButton(onClick = onAddHabit) {
                        Icon(Icons.Default.Add, contentDescription = "Habit hinzufügen")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                    }
                },
            )
        },
    ) { padding ->
        if (state.items.isEmpty()) {
            EmptyState(
                "Für heute ist nichts geplant.\nLege über + ein Habit an.",
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
                    item { SectionLabel("Gewohnheit aufbauen", Modifier.padding(top = 6.dp)) }
                    items(build, key = { it.habit.id }) { item -> TodayCard(item, actionsFor(item)) }
                }
                if (avoid.isNotEmpty()) {
                    item { SectionLabel("Gewohnheit ablegen", Modifier.padding(top = 6.dp)) }
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
            dismissLabel = if (t.afterFailure) "Überspringen" else "Abbrechen",
            onSave = { vm.saveNote(t.habitId, it); noteTarget = null },
            onDismiss = { noteTarget = null },
        )
    }

    moveFor?.let { item ->
        FutureDatePickerDialog(
            minDate = state.date.plusDays(1),
            onPick = { day ->
                vm.moveToday(item.habit.id, day)
                announce("Verschoben auf ${day.format(SHORT_DATE)}", item.habit.id)
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
            Text("$done von ${build.size} erledigt", style = MaterialTheme.typography.headlineSmall)
            ProgressBar(done.toFloat() / build.size, cs.primary)
        }
        if (avoid.isNotEmpty()) {
            if (build.isNotEmpty()) Spacer(Modifier.height(6.dp))
            Text(
                "$held von ${avoid.size} Gewohnheiten gehalten",
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
                "NIE ZWEIMAL VERPASSEN",
                style = MaterialTheme.typography.labelSmall,
                color = cs.tertiary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text("Ausblenden") }
        }
        Text(habits.joinToString(", ") { it.title }, style = MaterialTheme.typography.titleMedium)
        Text(
            if (habits.size == 1) "1 Gewohnheit ausgelassen – tippe für deine Motivation"
            else "${habits.size} Gewohnheiten ausgelassen – tippe für deine Motivation",
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
            Text("Nie zweimal verpassen", style = MaterialTheme.typography.headlineSmall)
            habits.forEach { habit ->
                EthosCard(spacing = 6.dp) {
                    Text(habit.title, style = MaterialTheme.typography.titleMedium, color = typeColor(habit.type))
                    if (habit.motivation.isBlank()) {
                        Text(
                            "Noch keine Motivation hinterlegt. Du kannst sie im Habit unter „Motivation“ ergänzen.",
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

    val pills = buildList<Pair<String, Color>> {
        if (item.weekTarget != null) {
            val extra = if (item.weekExtra > 0) " · +${item.weekExtra} nachzuholen" else ""
            add("${item.weekDone ?: 0} von ${item.weekTarget} diese Woche$extra" to accent)
        }
        if (item.movedFrom != null) {
            add("Verschoben von ${WEEKDAY_SHORT[item.movedFrom.dayOfWeek.value - 1]}" to cs.tertiary)
        }
        if (!isBuild) {
            if (item.state == DayState.FAILED) add("Doch gemacht" to cs.error) else add("Vermieden" to accent)
        }
        if ((item.streak ?: 0) >= 2) add("${item.streak} Tage in Folge" to cs.onSurfaceVariant)
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
            // Three-dot menu for both types; skip/move only exist for habits you build.
            var menu by remember { mutableStateOf(false) }
            val weekdays = isBuild && item.recurrence == RecurrenceType.WEEKDAYS
            val canSkip = isBuild && item.recurrence != RecurrenceType.TIMES_PER_WEEK
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Mehr") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (isBuild) {
                        DropdownMenuItem(text = { Text("Erledigt") }, onClick = { actions.onSet(LogStatus.COMPLETED); menu = false })
                        DropdownMenuItem(
                            text = { Text("Nicht gemacht") },
                            onClick = { actions.onSet(LogStatus.FAILED); menu = false; actions.onFailed() },
                        )
                        DropdownMenuItem(text = { Text("Zurücksetzen") }, onClick = { actions.onSet(null); menu = false })
                        DropdownMenuItem(
                            text = { Text(if (item.note == null) "Notiz hinzufügen" else "Notiz bearbeiten") },
                            onClick = { menu = false; actions.onNote() },
                        )
                        } else {
                        DropdownMenuItem(text = { Text("Vermieden") }, onClick = { actions.onSet(null); menu = false })
                        DropdownMenuItem(
                            text = { Text("Doch gemacht") },
                            onClick = { actions.onSet(LogStatus.FAILED); menu = false; actions.onFailed() },
                        )
                        DropdownMenuItem(
                            text = { Text(if (item.note == null) "Notiz hinzufügen" else "Notiz bearbeiten") },
                            onClick = { menu = false; actions.onNote() },
                        )
                        }
                        if (canSkip || item.movedFrom != null) HorizontalDivider()
                        // Fixed weekdays can be moved; every non-weekly schedule can skip a day.
                        if (item.movedFrom != null) {
                            DropdownMenuItem(
                                text = { Text("Verschiebung rückgängig") },
                                onClick = { menu = false; actions.onUndoMove() },
                            )
                        }
                        if (weekdays) {
                            DropdownMenuItem(
                                text = { Text("Auf morgen verschieben") },
                                onClick = { menu = false; actions.onMoveTomorrow() },
                            )
                            DropdownMenuItem(
                                text = { Text("Anderen Tag wählen …") },
                                onClick = { menu = false; actions.onPickDay() },
                            )
                        }
                        if (canSkip) {
                            DropdownMenuItem(
                                text = { Text("Heute auslassen") },
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
