package com.example.ethos.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ethos.data.EthosRepository
import com.example.ethos.data.Habit
import com.example.ethos.data.HabitSchedule
import com.example.ethos.data.LogStatus
import com.example.ethos.domain.DayState
import com.example.ethos.domain.HabitLogic
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
    val weeks: List<WeekResult>,
) {
    fun stateOn(date: LocalDate): DayState? =
        HabitLogic.cellState(habit, schedule, logs, date, today, start)
}

class HabitDetailViewModel(
    private val repo: EthosRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val habitId: Long = savedStateHandle["id"] ?: -1L
    private val month = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<DetailUiState?> = combine(
        repo.observeHabit(habitId),
        repo.observeAllLogs(),
        month,
        todayTicker(),
    ) { hs, allLogs, ym, today ->
        if (hs == null) return@combine null
        val logs = allLogs.filter { it.habitId == habitId }
            .associate { LocalDate.parse(it.localDate) to it.status }
        val weeks = HabitLogic.weeklyHistory(hs.habit, hs.schedule, logs, today)
        DetailUiState(
            habit = hs.habit,
            schedule = hs.schedule,
            logs = logs,
            start = HabitLogic.startOf(hs.habit, hs.schedule, logs.keys.minByOrNull { it.toEpochDay() }),
            today = today,
            month = ym,
            streak = HabitLogic.currentStreak(hs.habit, hs.schedule, logs, today),
            rate30 = HabitLogic.successRate(hs.habit, hs.schedule, logs, today.minusDays(29), today).percent,
            rateAll = HabitLogic.successRate(hs.habit, hs.schedule, logs, null, today).percent,
            weekDone = weeks.firstOrNull()?.done,
            weeks = weeks,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { month.value = month.value.plusMonths(1) }

    fun setStatus(date: LocalDate, status: LogStatus?) {
        viewModelScope.launch { repo.setLog(habitId, date, status) }
    }
}
