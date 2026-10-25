package com.example.ethos.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ethos.data.EthosRepository
import com.example.ethos.data.Habit
import com.example.ethos.data.RecurrenceType
import com.example.ethos.domain.DayState
import com.example.ethos.domain.HabitLogic
import com.example.ethos.domain.ScheduleExceptions
import com.example.ethos.domain.toExceptionIndex
import com.example.ethos.domain.toIndex
import com.example.ethos.domain.todayTicker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatItem(
    val habit: Habit,
    /** Monday..Sunday of the current week; null = future day or not scheduled. */
    val week: List<DayState?>,
    val rate30: Int?,
    val streak: Int?,
    val weekDone: Int?,
    val weekTarget: Int?,
)

class StatsViewModel(repo: EthosRepository) : ViewModel() {

    val items: StateFlow<List<StatItem>> = combine(
        todayTicker(),
        repo.observeActiveHabits(),
        repo.observeAllLogs(),
        repo.observeAllExceptions(),
    ) { today, habits, logs, exceptions ->
        val index = logs.toIndex()
        val exceptionIndex = exceptions.toExceptionIndex()
        val weekStart = HabitLogic.weekStart(today)
        habits.map { hs ->
            val l = index[hs.habit.id].orEmpty()
            val ex = exceptionIndex[hs.habit.id] ?: ScheduleExceptions.NONE
            val start = HabitLogic.startOf(hs.habit, hs.schedule, l.keys.minByOrNull { it.toEpochDay() })
            val weekly = hs.schedule?.recurrenceType == RecurrenceType.TIMES_PER_WEEK
            StatItem(
                habit = hs.habit,
                week = (0L..6L).map {
                    HabitLogic.cellState(hs.habit, hs.schedule, l, weekStart.plusDays(it), today, start, ex)
                },
                rate30 = HabitLogic.successRate(hs.habit, hs.schedule, l, today.minusDays(29), today, ex).percent,
                streak = HabitLogic.currentStreak(hs.habit, hs.schedule, l, today, ex),
                weekDone = if (weekly) HabitLogic.weekProgress(l, weekStart) else null,
                weekTarget = if (weekly) HabitLogic.weekTarget(hs.habit, hs.schedule, l, weekStart, start).total else null,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
