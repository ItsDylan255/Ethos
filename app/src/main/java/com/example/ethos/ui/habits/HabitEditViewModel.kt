package com.example.ethos.ui.habits

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ethos.data.EthosRepository
import com.example.ethos.data.Habit
import com.example.ethos.data.HabitSchedule
import com.example.ethos.data.HabitType
import com.example.ethos.data.HabitWithSchedule
import com.example.ethos.data.RecurrenceType
import com.example.ethos.data.daysToCsv
import com.example.ethos.data.parseDays
import kotlinx.coroutines.launch
import java.time.LocalDate

private val ALL_DAYS = (1..7).toSet()
private fun emptyLaws() = List(4) { "" }

data class HabitForm(
    val title: String = "",
    val description: String = "",
    val motivation: String = "",
    val type: HabitType = HabitType.BUILD,
    /** Kept per type so switching the type does not discard what was typed. */
    val buildLaws: List<String> = emptyLaws(),
    val avoidLaws: List<String> = emptyLaws(),
    val recurrence: RecurrenceType = RecurrenceType.DAILY,
    val weekdays: Set<Int> = ALL_DAYS,
    val weeklyTarget: Int = 3,
    val catchUp: Boolean = false,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val isArchived: Boolean = false,
) {
    val laws: List<String> get() = if (type == HabitType.BUILD) buildLaws else avoidLaws
    val filledLaws: Int get() = laws.count { it.isNotBlank() }

    val canSave: Boolean
        get() = title.isNotBlank() &&
            laws.all { it.isNotBlank() } &&
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
        // "Several times per week" only makes sense for habits you build.
        if (next.type == HabitType.AVOID && next.recurrence == RecurrenceType.TIMES_PER_WEEK) {
            next = next.copy(recurrence = restoreDayRecurrence(next.weekdays))
        }
        form = next
    }

    fun setLaw(index: Int, text: String) = update { f ->
        fun List<String>.with() = mapIndexed { i, s -> if (i == index) text else s }
        if (f.type == HabitType.BUILD) f.copy(buildLaws = f.buildLaws.with()) else f.copy(avoidLaws = f.avoidLaws.with())
    }

    /** All seven days = daily, otherwise selected weekdays. */
    fun setWeekdays(days: Set<Int>) = update { it.copy(weekdays = days, recurrence = restoreDayRecurrence(days)) }

    fun toggleTimesPerWeek() = update {
        if (it.recurrence == RecurrenceType.TIMES_PER_WEEK) it.copy(recurrence = restoreDayRecurrence(it.weekdays))
        else it.copy(recurrence = RecurrenceType.TIMES_PER_WEEK)
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val f = form
            val laws = f.laws.map { it.trim() }
            val habit = (original ?: Habit(title = "")).copy(
                title = f.title.trim(),
                description = f.description.trim().ifEmpty { null },
                motivation = f.motivation.trim(),
                type = f.type,
                law1 = laws[0],
                law2 = laws[1],
                law3 = laws[2],
                law4 = laws[3],
            )
            val schedule = HabitSchedule(
                habitId = habit.id,
                recurrenceType = f.recurrence,
                weekdays = if (f.recurrence == RecurrenceType.WEEKDAYS) daysToCsv(f.weekdays) else "",
                weeklyTarget = if (f.recurrence == RecurrenceType.TIMES_PER_WEEK) f.weeklyTarget else null,
                catchUp = f.recurrence == RecurrenceType.TIMES_PER_WEEK && f.catchUp,
                startDate = f.startDate?.toEpochDay(),
                endDate = f.endDate?.toEpochDay(),
            )
            repo.saveHabit(habit, schedule)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteHabit(habitId)
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

private fun restoreDayRecurrence(days: Set<Int>) =
    if (days.size == 7) RecurrenceType.DAILY else RecurrenceType.WEEKDAYS

private fun HabitWithSchedule.toForm(): HabitForm {
    val recurrence = schedule?.recurrenceType ?: RecurrenceType.DAILY
    val laws = listOf(habit.law1, habit.law2, habit.law3, habit.law4)
    return HabitForm(
        title = habit.title,
        description = habit.description.orEmpty(),
        motivation = habit.motivation,
        type = habit.type,
        buildLaws = if (habit.type == HabitType.BUILD) laws else emptyLaws(),
        avoidLaws = if (habit.type == HabitType.AVOID) laws else emptyLaws(),
        recurrence = recurrence,
        weekdays = if (recurrence == RecurrenceType.WEEKDAYS) parseDays(schedule?.weekdays) else ALL_DAYS,
        weeklyTarget = schedule?.weeklyTarget ?: 3,
        catchUp = schedule?.catchUp ?: false,
        startDate = schedule?.startDate?.let { LocalDate.ofEpochDay(it) },
        endDate = schedule?.endDate?.let { LocalDate.ofEpochDay(it) },
        isArchived = habit.isArchived,
    )
}
