package com.example.ethos.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ethos.R
import com.example.ethos.data.HabitType
import com.example.ethos.data.HabitWithSchedule
import com.example.ethos.ui.AppViewModelProvider
import com.example.ethos.ui.components.EmptyState
import com.example.ethos.ui.components.EthosCard
import com.example.ethos.ui.components.EthosTopBar
import com.example.ethos.ui.components.TypeBadge
import com.example.ethos.ui.scheduleSummary

@Composable
fun HabitsScreen(
    onAddHabit: () -> Unit,
    onEditHabit: (Long) -> Unit,
    vm: HabitsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<HabitWithSchedule?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EthosTopBar(
                title = stringResource(R.string.tab_habits),
                actions = {
                    IconButton(onClick = onAddHabit) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_habit))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.active.isEmpty()) {
                item { EmptyState(stringResource(R.string.habits_empty), Modifier.padding(vertical = 48.dp)) }
            }
            items(state.active, key = { it.habit.id }) { hs ->
                HabitCard(
                    hs = hs,
                    onClick = { onEditHabit(hs.habit.id) },
                    menu = { close ->
                        DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { close(); onEditHabit(hs.habit.id) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, onClick = { close(); vm.move(hs.habit.id, -1) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, onClick = { close(); vm.move(hs.habit.id, 1) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.archive)) }, onClick = { close(); vm.archive(hs.habit.id) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { close(); deleteTarget = hs })
                    },
                )
            }

            if (state.archived.isNotEmpty()) {
                item {
                    TextButton(onClick = vm::toggleArchive) {
                        Text(if (state.showArchived) stringResource(R.string.hide_archive) else stringResource(R.string.show_archive, state.archived.size))
                    }
                }
                if (state.showArchived) {
                    items(state.archived, key = { "a${it.habit.id}" }) { hs ->
                        HabitCard(
                            hs = hs,
                            dimmed = true,
                            onClick = { onEditHabit(hs.habit.id) },
                            menu = { close ->
                                DropdownMenuItem(text = { Text(stringResource(R.string.reactivate)) }, onClick = { close(); vm.restore(hs.habit.id) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { close(); deleteTarget = hs })
                            },
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_habit_title, target.habit.title)) },
            text = { Text(stringResource(R.string.delete_habit_text)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(target.habit.id); deleteTarget = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun HabitCard(
    hs: HabitWithSchedule,
    onClick: () -> Unit,
    menu: @Composable (close: () -> Unit) -> Unit,
    dimmed: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    EthosCard(
        modifier = Modifier.alpha(if (dimmed) 0.6f else 1f),
        onClick = onClick,
        contentPadding = PaddingValues(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypeBadge(hs.habit.type)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(hs.habit.title, style = MaterialTheme.typography.titleMedium)
                val sub = listOf(
                    stringResource(if (hs.habit.type == HabitType.BUILD) R.string.type_build else R.string.type_avoid),
                    scheduleSummary(hs.schedule),
                ).joinToString(" · ")
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more)) }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
            }
        }
    }
}
