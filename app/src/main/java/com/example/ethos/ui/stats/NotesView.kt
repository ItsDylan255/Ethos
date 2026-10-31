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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ethos.R
import com.example.ethos.domain.DayState
import com.example.ethos.ui.AppViewModelProvider
import com.example.ethos.ui.components.EmptyState
import com.example.ethos.ui.components.EthosCard
import com.example.ethos.ui.components.NoteDialog
import com.example.ethos.ui.components.Pill
import com.example.ethos.ui.components.SectionLabel
import com.example.ethos.ui.displayTitle
import com.example.ethos.ui.localizedFormatter

private val MONTH_FMT get() = localizedFormatter("LLLL yyyy", "LLLL yyyy")
private val NOTE_DATE_FMT get() = localizedFormatter("EEE, d. MMM", "EEE, MMM d")

/** All notes, newest first, grouped by month. Searchable and filterable by outcome and habit. */
@Composable
fun NotesView(vm: NotesViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<NoteItem?>(null) }
    var queryText by remember { mutableStateOf(vm.uiState.value.query) }
    val cs = MaterialTheme.colorScheme

    if (state.total == 0) {
        EmptyState(stringResource(R.string.notes_empty))
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = queryText,
                    onValueChange = { queryText = it; vm.setQuery(it) },
                    placeholder = { Text(stringResource(R.string.search_notes)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (queryText.isNotEmpty()) {
                            IconButton(onClick = { queryText = ""; vm.setQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
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
                        NoteFilter.ALL to stringResource(R.string.filter_all),
                        NoteFilter.MISSED to stringResource(R.string.filter_missed),
                        NoteFilter.DONE to stringResource(R.string.filter_done),
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
                                label = { Text(stringResource(R.string.all_habits)) },
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
                    if (state.shown == state.total) pluralStringResource(R.plurals.notes_count, state.total, state.total)
                    else stringResource(R.string.notes_shown, state.shown, state.total),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            if (state.groups.isEmpty()) {
                item { EmptyState(stringResource(R.string.no_matches), Modifier.padding(vertical = 32.dp)) }
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
            label = stringResource(R.string.note),
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
        DayState.COMPLETED -> stringResource(R.string.status_done) to cs.primary
        DayState.AVOIDED -> stringResource(R.string.status_avoided) to cs.tertiary
        DayState.FAILED -> stringResource(R.string.filter_missed) to cs.error
        DayState.NOT_RECORDED -> stringResource(R.string.status_no_entry) to cs.onSurfaceVariant
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
