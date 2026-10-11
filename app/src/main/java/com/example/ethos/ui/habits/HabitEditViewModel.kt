package com.example.ethos.ui.habits

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ethos.data.Category
import com.example.ethos.data.EthosRepository
import com.example.ethos.data.Habit
import com.example.ethos.data.HabitSchedule
import com.example.ethos.data.HabitType
import com.example.ethos.data.HabitWithSchedule
import com.example.ethos.data.RecurrenceType
import com.example.ethos.data.daysToCsv
import com.example.ethos.data.parseDays
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HabitForm(
    val title: String = "",
    val description: String = "",
    val icon: String = "",
    val type: HabitType = HabitType.BUILD,
    val categoryId: Long? = null,
    val recurrence: RecurrenceType = RecurrenceType.DAILY,
    val weekdays: Set<Int> = emptySet(),
    val weeklyTarget: Int = 3,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val isArchived: Boolean = false,
) {
    val canSave: Boolean
        get() = title.isNotBlank() &&
            !(recurrence == RecurrenceType.WEEKDAYS && weekdays.isEmpty()) &&
            !(startDate != null && endDate != null && endDate.isBefore(startDate))
}

class HabitEditViewModel(
    private val repo: EthosRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val habitId: Long = savedStateHandle["id"] ?: -1L
    private var original: Habit? = null

    val isEditing: Boolean = habitId > 0

    var form by mutableStateOf(HabitForm())
        private set

    val categories: StateFlow<List<Category>> = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (isEditing) {
            viewModelScope.launch {
                repo.getHabit(habitId)?.let { hs ->
                    original = hs.habit
                    form = hs.toForm()
                }
            }
        }
    }

    fun update(transform: (HabitForm) -> HabitForm) {
        var next = transform(form)
        // "X times per week" only makes sense for habits you build.
        if (next.type == HabitType.AVOID && next.recurrence == RecurrenceType.TIMES_PER_WEEK) {
            next = next.copy(recurrence = RecurrenceType.DAILY)
        }
        form = next
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val f = form
            val habit = (original ?: Habit(title = "")).copy(
                title = f.title.trim(),
                description = f.description.trim().ifEmpty { null },
                icon = f.icon.trim(),
                type = f.type,
                categoryId = f.categoryId,
            )
            val schedule = HabitSchedule(
                habitId = habit.id,
                recurrenceType = f.recurrence,
                weekdays = daysToCsv(f.weekdays),
                weeklyTarget = if (f.recurrence == RecurrenceType.TIMES_PER_WEEK) f.weeklyTarget else null,
                startDate = f.startDate?.toEpochDay(),
                endDate = f.endDate?.toEpochDay(),
            )
            repo.saveHabit(habit, schedule)
            onDone()
        }
    }

    fun toggleArchive(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.setArchived(habitId, !form.isArchived)
            onDone()
        }
    }
}

private fun HabitWithSchedule.toForm() = HabitForm(
    title = habit.title,
    description = habit.description.orEmpty(),
    icon = habit.icon,
    type = habit.type,
    categoryId = habit.categoryId,
    recurrence = schedule?.recurrenceType ?: RecurrenceType.DAILY,
    weekdays = parseDays(schedule?.weekdays),
    weeklyTarget = schedule?.weeklyTarget ?: 3,
    startDate = schedule?.startDate?.let { LocalDate.ofEpochDay(it) },
    endDate = schedule?.endDate?.let { LocalDate.ofEpochDay(it) },
    isArchived = habit.isArchived,
)
