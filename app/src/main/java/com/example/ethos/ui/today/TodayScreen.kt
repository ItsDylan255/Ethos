package com.example.ethos.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.example.ethos.ui.components.EthosTopBar
import com.example.ethos.ui.components.HairlineDivider
import com.example.ethos.ui.components.SectionLabel
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
            LazyColumn(Modifier.padding(padding)) {
                if (build.isNotEmpty()) {
                    val done = build.count { it.state == DayState.COMPLETED }
                    item { SectionLabel("Aufbauen · $done von ${build.size}") }
                    items(build, key = { it.habit.id }) { item ->
                        TodayRow(item, onSet = { vm.setStatus(item.habit.id, it) }, onOpen = { onOpenHabit(item.habit.id) })
                        HairlineDivider()
                    }
                }
                if (avoid.isNotEmpty()) {
                    item { SectionLabel("Vermeiden") }
                    items(avoid, key = { it.habit.id }) { item ->
                        TodayRow(item, onSet = { vm.setStatus(item.habit.id, it) }, onOpen = { onOpenHabit(item.habit.id) })
                        HairlineDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayRow(item: TodayItem, onSet: (LogStatus?) -> Unit, onOpen: () -> Unit) {
    val habit = item.habit
    val isBuild = habit.type == HabitType.BUILD
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusMark(
            state = item.state,
            onClick = if (isBuild) {
                { onSet(if (item.state == DayState.COMPLETED) null else LogStatus.COMPLETED) }
            } else null,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOf(habit.icon, habit.title).filter { it.isNotBlank() }.joinToString(" "),
                style = MaterialTheme.typography.bodyLarge,
            )
            val sub = buildList {
                if (item.weekTarget != null) add("${item.weekDone ?: 0} von ${item.weekTarget} diese Woche")
                if (!isBuild) add(if (item.state == DayState.FAILED) "Doch gemacht" else "Vermieden")
                if ((item.streak ?: 0) >= 2) add("${item.streak} Tage in Folge")
            }.joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    color = if (failed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun StatusMark(state: DayState, onClick: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    val background = if (state == DayState.COMPLETED) cs.primary else Color.Transparent
    val ring = when (state) {
        DayState.COMPLETED -> cs.primary
        DayState.AVOIDED -> cs.primary.copy(alpha = 0.5f)
        DayState.FAILED -> cs.error
        DayState.NOT_RECORDED -> cs.outline
    }
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(background)
            .border(1.5.dp, ring, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            DayState.COMPLETED -> Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = cs.onPrimary)
            DayState.AVOIDED -> Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = cs.primary)
            DayState.FAILED -> Icon(Icons.Default.Close, null, Modifier.size(16.dp), tint = cs.error)
            DayState.NOT_RECORDED -> Unit
        }
    }
}
