package com.example.ethos.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ethos.data.EthosRepository
import com.example.ethos.data.HabitWithSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HabitsUiState(
    val active: List<HabitWithSchedule> = emptyList(),
    val archived: List<HabitWithSchedule> = emptyList(),
    val showArchived: Boolean = false,
)

class HabitsViewModel(private val repo: EthosRepository) : ViewModel() {
    private val showArchived = MutableStateFlow(false)

    val uiState: StateFlow<HabitsUiState> = combine(
        repo.observeActiveHabits(),
        repo.observeArchivedHabits(),
        showArchived,
    ) { active, archived, show ->
        HabitsUiState(active = active, archived = archived, showArchived = show)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState())

    fun toggleArchive() { showArchived.value = !showArchived.value }

    fun archive(habitId: Long) = viewModelScope.launch { repo.setArchived(habitId, true) }
    fun restore(habitId: Long) = viewModelScope.launch { repo.setArchived(habitId, false) }

    fun move(habitId: Long, delta: Int) {
        val list = uiState.value.active
        val index = list.indexOfFirst { it.habit.id == habitId }
        val other = list.getOrNull(index + delta) ?: return
        viewModelScope.launch { repo.swapOrder(list[index].habit, other.habit) }
    }
}
