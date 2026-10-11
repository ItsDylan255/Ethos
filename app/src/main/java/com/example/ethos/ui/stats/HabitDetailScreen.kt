package com.example.ethos.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ethos.data.HabitType
import com.example.ethos.data.LogStatus
import com.example.ethos.domain.DayState
import com.example.ethos.ui.AppViewModelProvider
import com.example.ethos.ui.WEEKDAY_SHORT
import com.example.ethos.ui.components.EthosTopBar
import com.example.ethos.ui.components.HairlineDivider
import com.example.ethos.ui.components.SectionLabel
import com.example.ethos.ui.scheduleSummary
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
                title = s?.let { listOf(it.habit.icon, it.habit.title).filter { p -> p.isNotBlank() }.joinToString(" ") } ?: "",
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
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SummaryRow(s)
            HairlineDivider()
            MonthHeader(
                month = s.month,
                canGoForward = s.month.isBefore(YearMonth.from(s.today)),
                onPrevious = vm::previousMonth,
                onNext = vm::nextMonth,
            )
            MonthGrid(s, onPick = { editing = it })
            Text(
                "Tippe auf einen Tag, um ihn nachträglich zu korrigieren.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (s.weeks.isNotEmpty()) {
                HairlineDivider()
                SectionLabel("Wochenziele")
                s.weeks.forEach { w ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("KW ${w.weekNumber}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${w.done} von ${w.target}" + if (w.met) "  ✓" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (w.met) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        editing?.let { date ->
            CorrectionDialog(
                date = date,
                type = s.habit.type,
                current = s.logs[date],
                onPick = { vm.setStatus(date, it); editing = null },
                onDismiss = { editing = null },
            )
        }
    }
}

@Composable
private fun SummaryRow(s: DetailUiState) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 16.dp)) {
        val first = if (s.streak != null) "${s.streak}" to "Tage in Folge"
        else "${s.weekDone ?: 0}/${s.schedule?.weeklyTarget ?: 1}" to "Diese Woche"
        StatCell(first.first, first.second, Modifier.weight(1f))
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        StatCell(s.rate30?.let { "$it %" } ?: "–", "30 Tage", Modifier.weight(1f))
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        StatCell(s.rateAll?.let { "$it %" } ?: "–", "Gesamt", Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonthHeader(month: YearMonth, canGoForward: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
private fun MonthGrid(s: DetailUiState, onPick: (LocalDate) -> Unit) {
    val first = s.month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val days = s.month.lengthOfMonth()
    val rows = (offset + days + 6) / 7
    Column(Modifier.padding(horizontal = 12.dp)) {
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
private fun DayCell(day: Int, state: DayState?, isToday: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (state) {
        DayState.COMPLETED -> cs.primary to cs.onPrimary
        DayState.AVOIDED -> cs.primary.copy(alpha = 0.15f) to cs.onSurface
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
        Text("$day", style = MaterialTheme.typography.bodyMedium, color = fg)
    }
}

@Composable
private fun CorrectionDialog(
    date: LocalDate,
    type: HabitType,
    current: LogStatus?,
    onPick: (LogStatus?) -> Unit,
    onDismiss: () -> Unit,
) {
    val options: List<Pair<LogStatus?, String>> = if (type == HabitType.BUILD) {
        listOf(LogStatus.COMPLETED to "Erledigt", LogStatus.FAILED to "Nicht gemacht", null to "Keine Angabe")
    } else {
        listOf(null to "Vermieden", LogStatus.FAILED to "Doch gemacht")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(DAY_FMT)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEach { (status, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(status) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = current == status, onClick = { onPick(status) })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
