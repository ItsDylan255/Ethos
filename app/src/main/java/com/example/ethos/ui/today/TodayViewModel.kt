package com.example.ethos.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ethos.data.EthosRepository
import com.example.ethos.data.Habit
import com.example.ethos.data.LogStatus
import com.example.ethos.data.RecurrenceType
import com.example.ethos.domain.DayState
import com.example.ethos.domain.HabitLogic
import com.example.ethos.domain.todayTicker
import com.example.ethos.domain.toIndex
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TodayItem(
    val habit: Habit,
    val status: LogStatus?,
    val state: DayState,
    val weekDone: Int?,
    val weekTarget: Int?,
    val streak: Int?,
)

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val items: List<TodayItem> = emptyList(),
)

class TodayViewModel(private val repo: EthosRepository) : ViewModel() {

    val uiState: StateFlow<TodayUiState> = combine(
        todayTicker(),
        repo.observeActiveHabits(),
        repo.observeAllLogs(),
    ) { today, habits, logs ->
        val index = logs.toIndex()
        val weekStart = HabitLogic.weekStart(today)
        val items = habits.mapNotNull { hs ->
            val habitLogs = index[hs.habit.id].orEmpty()
            val start = HabitLogic.startOf(hs.habit, hs.schedule, habitLogs.keys.minByOrNull { it.toEpochDay() })
            if (!HabitLogic.isScheduledOn(hs.schedule, today, start)) return@mapNotNull null
            val status = habitLogs[today]
            val weekly = hs.schedule?.recurrenceType == RecurrenceType.TIMES_PER_WEEK
            TodayItem(
                habit = hs.habit,
                status = status,
                state = HabitLogic.dayState(hs.habit.type, status),
                weekDone = if (weekly) HabitLogic.weekProgress(habitLogs, weekStart) else null,
                weekTarget = if (weekly) hs.schedule?.weeklyTarget ?: 1 else null,
                streak = HabitLogic.currentStreak(hs.habit, hs.schedule, habitLogs, today),
            )
        }
        TodayUiState(today, items)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun setStatus(habitId: Long, status: LogStatus?) {
        viewModelScope.launch { repo.setLog(habitId, LocalDate.now(), status) }
    }
}
