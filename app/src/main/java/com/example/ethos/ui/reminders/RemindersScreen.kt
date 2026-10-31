package com.example.ethos.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ethos.R
import com.example.ethos.ui.AppViewModelProvider
import com.example.ethos.ui.components.EmptyState
import com.example.ethos.ui.components.EthosCard
import com.example.ethos.ui.components.EthosTopBar
import com.example.ethos.ui.components.Pill
import com.example.ethos.ui.localizedFormatter
import com.example.ethos.ui.reminderSummary
import java.time.Instant
import java.time.ZoneId

private val NEXT_FMT get() = localizedFormatter("EEE HH:mm", "EEE HH:mm")

@Composable
fun RemindersScreen(
    onAddReminder: () -> Unit,
    onEditReminder: (Long) -> Unit,
    vm: RemindersViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val items by vm.items.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EthosTopBar(
                title = stringResource(R.string.tab_reminders),
                actions = {
                    IconButton(onClick = onAddReminder) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_reminder))
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(stringResource(R.string.reminders_empty), Modifier.padding(padding))
        } else {
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.reminder.id }) { item ->
                    ReminderCard(item, vm, onClick = { onEditReminder(item.reminder.id) })
                }
            }
        }
    }
}

@Composable
private fun ReminderCard(item: ReminderItem, vm: RemindersViewModel, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val r = item.reminder
    var menu by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val paused = r.pausedUntil != null && r.pausedUntil > now
    val (status, statusColor) = when {
        !r.isEnabled -> stringResource(R.string.reminder_off) to cs.onSurfaceVariant
        paused -> stringResource(R.string.reminder_paused_until, fmt(r.pausedUntil!!)) to cs.tertiary
        r.nextTriggerAt != null -> stringResource(R.string.reminder_next, fmt(r.nextTriggerAt)) to cs.primary
        else -> stringResource(R.string.reminder_nothing) to cs.onSurfaceVariant
    }
    EthosCard(
        onClick = onClick,
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(r.title.ifBlank { item.habitTitle ?: stringResource(R.string.reminder_default_title) }, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(item.habitTitle?.takeIf { r.title.isNotBlank() }, reminderSummary(r)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
                Pill(status, statusColor)
            }
            Switch(checked = r.isEnabled, onCheckedChange = { vm.setEnabled(r, it) })
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { menu = false; onClick() })
                    if (paused) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.end_pause)) }, onClick = { menu = false; vm.resume(r) })
                    } else {
                        DropdownMenuItem(text = { Text(stringResource(R.string.pause_tomorrow)) }, onClick = { menu = false; vm.pauseUntilTomorrow(r) })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; vm.delete(r) })
                }
            }
        }
    }
}

private fun fmt(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(NEXT_FMT)
