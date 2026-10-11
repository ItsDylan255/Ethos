package com.example.ethos.ui.reminders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ethos.ui.AppViewModelProvider
import com.example.ethos.ui.components.EmptyState
import com.example.ethos.ui.components.EthosTopBar
import com.example.ethos.ui.components.HairlineDivider
import com.example.ethos.ui.reminderSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val NEXT_FMT = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.GERMAN)

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
                title = "Erinnerungen",
                actions = {
                    IconButton(onClick = onAddReminder) {
                        Icon(Icons.Default.Add, contentDescription = "Erinnerung hinzufügen")
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState("Noch keine Erinnerungen.\nSie können an ein Habit gebunden oder frei sein.", Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(items, key = { it.reminder.id }) { item ->
                    ReminderRow(item, vm, onClick = { onEditReminder(item.reminder.id) })
                    HairlineDivider()
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(item: ReminderItem, vm: RemindersViewModel, onClick: () -> Unit) {
    val r = item.reminder
    var menu by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val paused = r.pausedUntil != null && r.pausedUntil > now
    val status = when {
        !r.isEnabled -> "Aus"
        paused -> "Pausiert bis ${fmt(r.pausedUntil!!)}"
        r.nextTriggerAt != null -> "Nächste: ${fmt(r.nextTriggerAt)}"
        else -> "Nichts geplant"
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(r.title.ifBlank { item.habitTitle ?: "Erinnerung" }, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(item.habitTitle?.takeIf { r.title.isNotBlank() }, reminderSummary(r)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = r.isEnabled, onCheckedChange = { vm.setEnabled(r, it) })
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Mehr") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Bearbeiten") }, onClick = { menu = false; onClick() })
                if (paused) {
                    DropdownMenuItem(text = { Text("Pause beenden") }, onClick = { menu = false; vm.resume(r) })
                } else {
                    DropdownMenuItem(text = { Text("Bis morgen pausieren") }, onClick = { menu = false; vm.pauseUntilTomorrow(r) })
                }
                DropdownMenuItem(text = { Text("Löschen") }, onClick = { menu = false; vm.delete(r) })
            }
        }
    }
}

private fun fmt(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(NEXT_FMT)
