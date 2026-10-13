package com.example.ethos.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ethos.data.HabitType
import com.example.ethos.data.LogStatus
import com.example.ethos.domain.DayState
import com.example.ethos.ui.AppViewModelProvider
import com.example.ethos.ui.components.EmptyState
import com.example.ethos.ui.components.EthosCard
import com.example.ethos.ui.components.EthosTopBar
import com.example.ethos.ui.components.Pill
import com.example.ethos.ui.components.ProgressBar
import com.example.ethos.ui.components.SectionLabel
import com.example.ethos.ui.displayTitle
import com.example.ethos.ui.theme.typeColor
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HEADER_DATE = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

@Composable
fun TodayScreen(
    onAddHabit: () -> Unit,
    onOpenHabit: (Long) -> Unit,
    vm: TodayViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EthosTopBar(
                title = "Heute",
                subtitle = state.date.format(HEADER_DATE),
                actions = {
                    IconButton(onClick = onAddHabit) {
                        Icon(Icons.Default.Add, contentDescription = "Habit hinzufügen")
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
                if (build.isNotEmpty()) {
                    item { SectionLabel("Gewohnheit aufbauen", Modifier.padding(top = 6.dp)) }
                    items(build, key = { it.habit.id }) { item ->
                        TodayCard(item, onSet = { vm.setStatus(item.habit.id, it) }, onOpen = { onOpenHabit(item.habit.id) })
                    }
                }
                if (avoid.isNotEmpty()) {
                    item { SectionLabel("Gewohnheit ablegen", Modifier.padding(top = 6.dp)) }
                    items(avoid, key = { it.habit.id }) { item ->
                        TodayCard(item, onSet = { vm.setStatus(item.habit.id, it) }, onOpen = { onOpenHabit(item.habit.id) })
                    }
                }
            }
        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayCard(item: TodayItem, onSet: (LogStatus?) -> Unit, onOpen: () -> Unit) {
    val habit = item.habit
    val isBuild = habit.type == HabitType.BUILD
    val accent = typeColor(habit.type)
    val cs = MaterialTheme.colorScheme

    val pills = buildList<Pair<String, Color>> {
        if (item.weekTarget != null) add("${item.weekDone ?: 0} von ${item.weekTarget} diese Woche" to accent)
        if (!isBuild) {
            if (item.state == DayState.FAILED) add("Doch gemacht" to cs.error) else add("Vermieden" to accent)
        }
        if ((item.streak ?: 0) >= 2) add("${item.streak} Tage in Folge" to cs.onSurfaceVariant)
    }

    EthosCard(
        onClick = onOpen,
        contentPadding = PaddingValues(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusMark(
                state = item.state,
                accent = accent,
                onClick = if (isBuild) {
                    { onSet(if (item.state == DayState.COMPLETED) null else LogStatus.COMPLETED) }
                } else null,
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
            }
            if (isBuild) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Mehr") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Erledigt") }, onClick = { onSet(LogStatus.COMPLETED); menu = false })
                        DropdownMenuItem(text = { Text("Nicht gemacht") }, onClick = { onSet(LogStatus.FAILED); menu = false })
                        DropdownMenuItem(text = { Text("Zurücksetzen") }, onClick = { onSet(null); menu = false })
                    }
                }
            } else {
                val failed = item.state == DayState.FAILED
                TextButton(onClick = { onSet(if (failed) null else LogStatus.FAILED) }) {
                    Text(
                        if (failed) "Rückgängig" else "Doch gemacht",
                        color = if (failed) cs.onSurfaceVariant else cs.error,
                    )
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
