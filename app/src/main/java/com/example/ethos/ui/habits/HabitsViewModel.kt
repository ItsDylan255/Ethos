package com.example.ethos.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ethos.data.Category
import com.example.ethos.data.EthosRepository
import com.example.ethos.data.HabitWithSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HabitsUiState(
    val visible: List<HabitWithSchedule> = emptyList(),
    val archived: List<HabitWithSchedule> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val showArchived: Boolean = false,
)

class HabitsViewModel(private val repo: EthosRepository) : ViewModel() {
    private val selected = MutableStateFlow<Long?>(null)
    private val showArchived = MutableStateFlow(false)

    val uiState: StateFlow<HabitsUiState> = combine(
        repo.observeActiveHabits(),
        repo.observeArchivedHabits(),
        repo.observeCategories(),
        selected,
        showArchived,
    ) { active, archived, categories, sel, show ->
        val validSel = sel?.takeIf { id -> categories.any { it.id == id } }
        HabitsUiState(
            visible = active.filter { validSel == null || it.habit.categoryId == validSel },
            archived = archived,
            categories = categories,
            selectedCategoryId = validSel,
            showArchived = show,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState())

    fun selectCategory(id: Long?) { selected.value = id }
    fun toggleArchive() { showArchived.value = !showArchived.value }

    fun archive(habitId: Long) = viewModelScope.launch { repo.setArchived(habitId, true) }
    fun restore(habitId: Long) = viewModelScope.launch { repo.setArchived(habitId, false) }

    /** Moves a habit within the currently visible list. */
    fun move(habitId: Long, delta: Int) {
        val list = uiState.value.visible
        val index = list.indexOfFirst { it.habit.id == habitId }
        val other = list.getOrNull(index + delta) ?: return
        viewModelScope.launch { repo.swapOrder(list[index].habit, other.habit) }
    }

    fun saveCategory(category: Category) = viewModelScope.launch { repo.saveCategory(category) }

    fun deleteCategory(category: Category) = viewModelScope.launch {
        if (selected.value == category.id) selected.value = null
        repo.deleteCategory(category)
    }
}
