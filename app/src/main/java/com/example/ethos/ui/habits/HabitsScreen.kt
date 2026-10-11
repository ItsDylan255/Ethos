package com.example.ethos.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.example.ethos.data.Category
import com.example.ethos.data.HabitType
import com.example.ethos.data.HabitWithSchedule
import com.example.ethos.ui.AppViewModelProvider
import com.example.ethos.ui.components.EmptyState
import com.example.ethos.ui.components.EthosTopBar
import com.example.ethos.ui.components.HairlineDivider
import com.example.ethos.ui.scheduleSummary

private data class CategoryDialogState(val category: Category?)

private val CATEGORY_COLORS = listOf(
    0xFF5E6AD2, 0xFF2DA44E, 0xFFD9822B, 0xFFD14343, 0xFF8957E5, 0xFF1F9FBF,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    onAddHabit: () -> Unit,
    onEditHabit: (Long) -> Unit,
    vm: HabitsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<CategoryDialogState?>(null) }
    val selectedCategory = state.categories.firstOrNull { it.id == state.selectedCategoryId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EthosTopBar(
                title = "Habits",
                actions = {
                    IconButton(onClick = onAddHabit) {
                        Icon(Icons.Default.Add, contentDescription = "Habit hinzufügen")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedCategoryId == null,
                            onClick = { vm.selectCategory(null) },
                            label = { Text("Alle") },
                        )
                    }
                    items(state.categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = state.selectedCategoryId == category.id,
                            onClick = { vm.selectCategory(category.id) },
                            label = { Text(listOf(category.icon, category.name).filter { it.isNotBlank() }.joinToString(" ")) },
                            leadingIcon = {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(category.color)))
                            },
                        )
                    }
                    item {
                        AssistChip(onClick = { dialog = CategoryDialogState(null) }, label = { Text("+ Kategorie") })
                    }
                    if (selectedCategory != null) {
                        item {
                            IconButton(onClick = { dialog = CategoryDialogState(selectedCategory) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Kategorie bearbeiten", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                HairlineDivider()
            }

            if (state.visible.isEmpty()) {
                item { EmptyState("Keine Habits.", Modifier.padding(vertical = 48.dp)) }
            }
            items(state.visible, key = { it.habit.id }) { hs ->
                HabitListRow(
                    hs = hs,
                    categoryName = state.categories.firstOrNull { it.id == hs.habit.categoryId }?.name,
                    onClick = { onEditHabit(hs.habit.id) },
                    menu = { close ->
                        DropdownMenuItem(text = { Text("Bearbeiten") }, onClick = { close(); onEditHabit(hs.habit.id) })
                        DropdownMenuItem(text = { Text("Nach oben") }, onClick = { close(); vm.move(hs.habit.id, -1) })
                        DropdownMenuItem(text = { Text("Nach unten") }, onClick = { close(); vm.move(hs.habit.id, 1) })
                        DropdownMenuItem(text = { Text("Archivieren") }, onClick = { close(); vm.archive(hs.habit.id) })
                    },
                )
                HairlineDivider()
            }

            if (state.archived.isNotEmpty()) {
                item {
                    TextButton(onClick = vm::toggleArchive, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(if (state.showArchived) "Archiv ausblenden" else "Archiv anzeigen (${state.archived.size})")
                    }
                }
                if (state.showArchived) {
                    items(state.archived, key = { "a${it.habit.id}" }) { hs ->
                        HabitListRow(
                            hs = hs,
                            categoryName = null,
                            onClick = { onEditHabit(hs.habit.id) },
                            menu = { close ->
                                DropdownMenuItem(text = { Text("Reaktivieren") }, onClick = { close(); vm.restore(hs.habit.id) })
                            },
                        )
                        HairlineDivider()
                    }
                }
            }
        }
    }

    dialog?.let { d ->
        CategoryDialog(
            category = d.category,
            onDismiss = { dialog = null },
            onSave = { vm.saveCategory(it); dialog = null },
            onDelete = d.category?.let { c -> { vm.deleteCategory(c); dialog = null } },
        )
    }
}

@Composable
private fun HabitListRow(
    hs: HabitWithSchedule,
    categoryName: String?,
    onClick: () -> Unit,
    menu: @Composable (close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                listOf(hs.habit.icon, hs.habit.title).filter { it.isNotBlank() }.joinToString(" "),
                style = MaterialTheme.typography.bodyLarge,
            )
            val sub = listOfNotNull(
                if (hs.habit.type == HabitType.BUILD) "Aufbauen" else "Vermeiden",
                scheduleSummary(hs.schedule),
                categoryName,
            ).joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Mehr") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
        }
    }
}

@Composable
private fun CategoryDialog(
    category: Category?,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(category?.name.orEmpty()) }
    var icon by remember { mutableStateOf(category?.icon.orEmpty()) }
    var color by remember { mutableStateOf(category?.color ?: CATEGORY_COLORS.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "Neue Kategorie" else "Kategorie bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(icon, { icon = it.take(4) }, label = { Text("Symbol (Emoji)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CATEGORY_COLORS.forEach { c ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .then(
                                    if (c == color) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier,
                                )
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val base = category ?: Category(name = "", icon = "", color = color)
                    onSave(base.copy(name = name.trim(), icon = icon.trim(), color = color))
                },
            ) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}
