package com.example.ethos.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ethos.domain.DayState
import com.example.ethos.ui.AppViewModelProvider
import com.example.ethos.ui.components.EmptyState
import com.example.ethos.ui.components.EthosCard
import com.example.ethos.ui.components.NoteDialog
import com.example.ethos.ui.components.Pill
import com.example.ethos.ui.components.SectionLabel
import com.example.ethos.ui.displayTitle
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_FMT = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.GERMAN)
private val NOTE_DATE_FMT = DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN)

/** All notes, newest first, grouped by month. Searchable and filterable by outcome and habit. */
@Composable
fun NotesView(vm: NotesViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<NoteItem?>(null) }
    var queryText by remember { mutableStateOf(vm.uiState.value.query) }
    val cs = MaterialTheme.colorScheme

    if (state.total == 0) {
        EmptyState("Noch keine Notizen.\nSie entstehen beim Abhaken, bei einem Rückfall oder im Kalender eines Habits.")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = queryText,
                    onValueChange = { queryText = it; vm.setQuery(it) },
                    placeholder = { Text("Notizen durchsuchen") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (queryText.isNotEmpty()) {
                            IconButton(onClick = { queryText = ""; vm.setQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Suche löschen")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        NoteFilter.ALL to "Alle",
                        NoteFilter.MISSED to "Nicht geklappt",
                        NoteFilter.DONE to "Geklappt",
                    ).forEach { (filter, label) ->
                        FilterChip(
                            selected = state.filter == filter,
                            onClick = { vm.setFilter(filter) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            if (state.habits.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = state.habitId == null,
                                onClick = { vm.setHabit(null) },
                                label = { Text("Alle Habits") },
                            )
                        }
                        items(state.habits, key = { it.id }) { habit ->
                            FilterChip(
                                selected = state.habitId == habit.id,
                                onClick = { vm.setHabit(habit.id) },
                                label = { Text(habit.title) },
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    if (state.shown == state.total) "${state.total} Notizen" else "${state.shown} von ${state.total} Notizen",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            if (state.groups.isEmpty()) {
                item { EmptyState("Keine Treffer.", Modifier.padding(vertical = 32.dp)) }
            }
            state.groups.forEach { group ->
                item(key = "m${group.month}") { SectionLabel(group.month.format(MONTH_FMT), Modifier.padding(top = 6.dp)) }
                items(group.items, key = { it.note.id }) { item -> NoteCard(item, onClick = { editing = item }) }
            }
        }
    }

    editing?.let { item ->
        NoteDialog(
            title = "${item.habit.title} · ${item.date.format(NOTE_DATE_FMT)}",
            label = "Notiz",
            initial = item.note.text,
            onSave = { vm.saveNote(item.habit.id, item.date, it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun NoteCard(item: NoteItem, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (label, color) = when (item.state) {
        DayState.COMPLETED -> "Erledigt" to cs.primary
        DayState.AVOIDED -> "Vermieden" to cs.tertiary
        DayState.FAILED -> "Nicht geklappt" to cs.error
        DayState.NOT_RECORDED -> "Keine Angabe" to cs.onSurfaceVariant
    }
    EthosCard(onClick = onClick, spacing = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(item.habit.displayTitle(), style = MaterialTheme.typography.titleMedium)
                Text(
                    item.date.format(NOTE_DATE_FMT),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            Pill(label, color)
        }
        Text(item.note.text, style = MaterialTheme.typography.bodyMedium)
    }
}
