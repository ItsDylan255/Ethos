package com.example.ethos.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ethos.data.ExceptionKind
import com.example.ethos.data.HabitType
import com.example.ethos.data.LogStatus
import com.example.ethos.domain.DayState
import com.example.ethos.ui.AVOID_LAW_LABELS
import com.example.ethos.ui.AppViewModelProvider
import com.example.ethos.ui.BUILD_LAW_LABELS
import com.example.ethos.ui.WEEKDAY_SHORT
import com.example.ethos.ui.components.CardTitle
import com.example.ethos.ui.components.EthosCard
import com.example.ethos.ui.components.EthosTopBar
import com.example.ethos.ui.displayTitle
import com.example.ethos.ui.scheduleSummary
import com.example.ethos.ui.theme.typeColor
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_FMT = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.GERMAN)
private val DAY_FMT = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

@Composable
fun HabitDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    vm: HabitDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val s = state
    var editing by remember { mutableStateOf<LocalDate?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EthosTopBar(
                title = s?.habit?.displayTitle() ?: "",
                subtitle = s?.let { scheduleSummary(it.schedule) },
                onBack = onBack,
                actions = {
                    if (s != null) {
                        IconButton(onClick = { onEdit(s.habit.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Bearbeiten")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (s == null) return@Scaffold
        val accent = typeColor(s.habit.type)
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryRow(s, accent)

            EthosCard(spacing = 8.dp) {
                MonthHeader(
                    month = s.month,
                    canGoForward = s.month.isBefore(YearMonth.from(s.today)),
                    onPrevious = vm::previousMonth,
                    onNext = vm::nextMonth,
                )
                MonthGrid(s, accent, onPick = { editing = it })
                Text(
                    "Tippe auf einen Tag, um ihn nachträglich zu korrigieren.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (s.weeks.isNotEmpty()) {
                EthosCard(spacing = 10.dp) {
                    CardTitle("Wochenziele")
                    s.weeks.forEach { w ->
                        Row(Modifier.fillMaxWidth()) {
                            Text("KW ${w.weekNumber}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${w.done} von ${w.target}" + (if (w.extra > 0) " (+${w.extra})" else "") + if (w.met) "  ✓" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (w.met) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (s.notes.isNotEmpty()) {
                EthosCard(spacing = 12.dp) {
                    CardTitle("Notizen")
                    s.notes.entries.sortedByDescending { it.key.toEpochDay() }.take(10).forEach { (date, text) ->
                        Column(
                            Modifier.fillMaxWidth().clickable { editing = date },
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                date.format(DAY_FMT),
                                style = MaterialTheme.typography.labelMedium,
                                color = accent,
                            )
                            Text(text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (s.habit.motivation.isNotBlank()) {
                EthosCard(spacing = 8.dp) {
                    CardTitle("Motivation")
                    Text(s.habit.motivation, style = MaterialTheme.typography.bodyMedium)
                }
            }

            val laws = listOf(s.habit.law1, s.habit.law2, s.habit.law3, s.habit.law4)
            if (laws.any { it.isNotBlank() }) {
                val labels = if (s.habit.type == HabitType.BUILD) BUILD_LAW_LABELS else AVOID_LAW_LABELS
                EthosCard(spacing = 12.dp) {
                    CardTitle("Meine Strategie")
                    laws.forEachIndexed { index, text ->
                        if (text.isNotBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "${index + 1}. ${labels[index]}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = accent,
                                )
                                Text(text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        editing?.let { date ->
            CorrectionDialog(
                date = date,
                type = s.habit.type,
                current = s.logs[date],
                initialNote = s.notes[date].orEmpty(),
                canSkip = s.canSkip,
                initialSkipped = s.exceptions.byOrigin[date]?.kind == ExceptionKind.SKIPPED,
                moveTarget = s.exceptions.byOrigin[date]
                    ?.takeIf { it.kind == ExceptionKind.MOVED }?.targetDate?.let { LocalDate.parse(it) },
                onSave = { status, note, skipped -> vm.save(date, status, note, skipped); editing = null },
                onUndoMove = { vm.undoMove(date); editing = null },
                onDismiss = { editing = null },
            )
        }
    }
}

@Composable
private fun SummaryRow(s: DetailUiState, accent: Color) {
    val first = if (s.streak != null) "${s.streak}" to "Tage in Folge"
    else "${s.weekDone ?: 0}/${s.weekTarget ?: s.schedule?.weeklyTarget ?: 1}" to "Diese Woche"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCell(first.first, first.second, accent, Modifier.weight(1f))
        StatCell(s.rate30?.let { "$it %" } ?: "–", "30 Tage", accent, Modifier.weight(1f))
        StatCell(s.rateAll?.let { "$it %" } ?: "–", "Gesamt", accent, Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(value: String, label: String, accent: Color, modifier: Modifier) {
    EthosCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = accent)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonthHeader(month: YearMonth, canGoForward: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Vorheriger Monat") }
        Text(
            month.format(MONTH_FMT),
            Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Nächster Monat")
        }
    }
}

@Composable
private fun MonthGrid(s: DetailUiState, accent: Color, onPick: (LocalDate) -> Unit) {
    val first = s.month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val days = s.month.lengthOfMonth()
    val rows = (offset + days + 6) / 7
    Column {
        Row(Modifier.fillMaxWidth()) {
            WEEKDAY_SHORT.forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { c ->
                    val day = r * 7 + c - offset + 1
                    if (day in 1..days) {
                        val date = s.month.atDay(day)
                        DayCell(
                            day = day,
                            state = s.stateOn(date),
                            hasNote = s.notes.containsKey(date),
                            crossedOut = s.isCrossedOut(date),
                            accent = accent,
                            isToday = date == s.today,
                            enabled = !date.isAfter(s.today),
                            onClick = { onPick(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    state: DayState?,
    hasNote: Boolean,
    crossedOut: Boolean,
    accent: Color,
    isToday: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (state) {
        DayState.COMPLETED -> accent to cs.onPrimary
        DayState.AVOIDED -> accent.copy(alpha = 0.18f) to cs.onSurface
        DayState.FAILED -> cs.error to cs.onError
        DayState.NOT_RECORDED -> Color.Transparent to cs.onSurfaceVariant
        null -> Color.Transparent to cs.onSurface.copy(alpha = if (enabled) 0.6f else 0.3f)
    }
    Box(
        modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(CircleShape)
            .background(bg)
            .then(
                when {
                    isToday -> Modifier.border(1.5.dp, cs.onSurface, CircleShape)
                    state == DayState.NOT_RECORDED -> Modifier.border(1.dp, cs.outline, CircleShape)
                    else -> Modifier
                },
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$day",
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
            textDecoration = if (crossedOut) TextDecoration.LineThrough else null,
        )
        if (hasNote) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(fg),
            )
        }
    }
}

private data class DayOption(val status: LogStatus?, val skipped: Boolean, val label: String)

@Composable
private fun CorrectionDialog(
    date: LocalDate,
    type: HabitType,
    current: LogStatus?,
    initialNote: String,
    canSkip: Boolean,
    initialSkipped: Boolean,
    moveTarget: LocalDate?,
    onSave: (LogStatus?, String, Boolean) -> Unit,
    onUndoMove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val options = if (type == HabitType.BUILD) {
        buildList {
            add(DayOption(LogStatus.COMPLETED, false, "Erledigt"))
            add(DayOption(LogStatus.FAILED, false, "Nicht gemacht"))
            add(DayOption(null, false, "Keine Angabe"))
            if (canSkip) add(DayOption(null, true, "Ausgelassen (z. B. krank)"))
        }
    } else {
        buildList {
            add(DayOption(null, false, "Vermieden"))
            add(DayOption(LogStatus.FAILED, false, "Doch gemacht"))
            if (canSkip) add(DayOption(null, true, "Ausgelassen (z. B. krank)"))
        }
    }
    var selected by remember {
        mutableStateOf(
            if (initialSkipped) options.indexOfFirst { it.skipped }.coerceAtLeast(0)
            else options.indexOfFirst { !it.skipped && it.status == current }.coerceAtLeast(0),
        )
    }
    var note by remember { mutableStateOf(initialNote) }
    val choice = options[selected]
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(DAY_FMT)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (moveTarget != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Verschoben auf ${moveTarget.format(DAY_FMT)}",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        TextButton(onClick = onUndoMove) { Text("Aufheben") }
                    }
                }
                options.forEachIndexed { index, option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = index }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == index, onClick = { selected = index })
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = {
                        Text(
                            when {
                                choice.skipped -> "Warum lässt du aus? *"
                                choice.status == LogStatus.FAILED -> "Warum hat es nicht geklappt?"
                                else -> "Notiz (optional)"
                            },
                        )
                    },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            // Skipping always needs a reason.
            TextButton(
                enabled = !choice.skipped || note.isNotBlank(),
                onClick = { onSave(choice.status, note, choice.skipped) },
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
