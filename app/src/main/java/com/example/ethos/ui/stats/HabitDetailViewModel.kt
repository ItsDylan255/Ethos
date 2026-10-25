package com.example.ethos.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ethos.data.EthosRepository
import com.example.ethos.data.ExceptionKind
import com.example.ethos.data.Habit
import com.example.ethos.data.HabitSchedule
import com.example.ethos.data.HabitType
import com.example.ethos.data.LogStatus
import com.example.ethos.data.RecurrenceType
import com.example.ethos.domain.DayState
import com.example.ethos.domain.HabitLogic
import com.example.ethos.domain.ScheduleExceptions
import com.example.ethos.domain.WeekResult
import com.example.ethos.domain.todayTicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class DetailUiState(
    val habit: Habit,
    val schedule: HabitSchedule?,
    val logs: Map<LocalDate, LogStatus>,
    val start: LocalDate,
    val today: LocalDate,
    val month: YearMonth,
    val streak: Int?,
    val rate30: Int?,
    val rateAll: Int?,
    val weekDone: Int?,
    val weekTarget: Int?,
    val weeks: List<WeekResult>,
    val notes: Map<LocalDate, String>,
    val exceptions: ScheduleExceptions,
) {
    /** Only habits you build on fixed days can have days skipped. */
    val canSkip: Boolean
        get() = habit.type == HabitType.BUILD && schedule?.recurrenceType != RecurrenceType.TIMES_PER_WEEK

    fun stateOn(date: LocalDate): DayState? =
        HabitLogic.cellState(habit, schedule, logs, date, today, start, exceptions)

    /** Dropped from the schedule (skipped, or moved somewhere else). */
    fun isCrossedOut(date: LocalDate): Boolean = exceptions.isRemoved(date) && exceptions.movedFrom(date) == null
}

class HabitDetailViewModel(
    private val repo: EthosRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val habitId: Long = savedStateHandle["id"] ?: -1L
    private val month = MutableStateFlow(YearMonth.now())

    private val history = combine(
        repo.observeAllLogs(),
        repo.observeAllNotes(),
        repo.observeAllExceptions(),
    ) { logs, notes, exceptions -> Triple(logs, notes, exceptions) }

    val uiState: StateFlow<DetailUiState?> = combine(
        repo.observeHabit(habitId),
        history,
        month,
        todayTicker(),
    ) { hs, (allLogs, allNotes, allExceptions), ym, today ->
        if (hs == null) return@combine null
        val logs = allLogs.filter { it.habitId == habitId }
            .associate { LocalDate.parse(it.localDate) to it.status }
        val notes = allNotes.filter { it.habitId == habitId }.associate { LocalDate.parse(it.localDate) to it.text }
        val ex = ScheduleExceptions(allExceptions.filter { it.habitId == habitId })
        val start = HabitLogic.startOf(hs.habit, hs.schedule, logs.keys.minByOrNull { it.toEpochDay() })
        val weeks = HabitLogic.weeklyHistory(hs.habit, hs.schedule, logs, today)
        DetailUiState(
            habit = hs.habit,
            schedule = hs.schedule,
            logs = logs,
            start = start,
            today = today,
            month = ym,
            streak = HabitLogic.currentStreak(hs.habit, hs.schedule, logs, today, ex),
            rate30 = HabitLogic.successRate(hs.habit, hs.schedule, logs, today.minusDays(29), today, ex).percent,
            rateAll = HabitLogic.successRate(hs.habit, hs.schedule, logs, null, today, ex).percent,
            weekDone = weeks.firstOrNull()?.done,
            weekTarget = weeks.firstOrNull()?.target,
            weeks = weeks,
            notes = notes,
            exceptions = ex,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { month.value = month.value.plusMonths(1) }

    fun save(date: LocalDate, status: LogStatus?, note: String, skipped: Boolean) {
        viewModelScope.launch {
            val wasSkipped = uiState.value?.exceptions?.byOrigin?.get(date)?.kind == ExceptionKind.SKIPPED
            repo.saveEntry(habitId, date, if (skipped) null else status, note)
            if (skipped && !wasSkipped) repo.skipDay(habitId, date)
            if (!skipped && wasSkipped) repo.undoException(habitId, date)
        }
    }

    fun undoMove(date: LocalDate) {
        viewModelScope.launch { repo.undoException(habitId, date) }
    }
}
