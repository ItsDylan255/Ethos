package com.example.ethos.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ethos.data.EthosRepository
import com.example.ethos.data.Habit
import com.example.ethos.data.LogStatus
import com.example.ethos.data.RecurrenceType
import com.example.ethos.data.SettingsRepository
import com.example.ethos.domain.DayState
import com.example.ethos.domain.HabitLogic
import com.example.ethos.domain.ScheduleExceptions
import com.example.ethos.domain.toExceptionIndex
import com.example.ethos.domain.toIndex
import com.example.ethos.domain.todayTicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TodayItem(
    val habit: Habit,
    val recurrence: RecurrenceType,
    val status: LogStatus?,
    val state: DayState,
    val weekDone: Int?,
    /** Weekly goal including carried-over sessions. */
    val weekTarget: Int?,
    /** Sessions carried over from last week (catch-up). */
    val weekExtra: Int,
    val streak: Int?,
    /** Today's note, if any. */
    val note: String?,
    /** Scheduled yesterday and not successful ("never miss twice"). */
    val missedYesterday: Boolean,
    /** Origin day if this habit was moved to today. */
    val movedFrom: LocalDate?,
)

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val items: List<TodayItem> = emptyList(),
    /** Habits to show in the gentle "missed yesterday" hint. */
    val hints: List<Habit> = emptyList(),
)

class TodayViewModel(
    private val repo: EthosRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private data class Computed(val date: LocalDate, val items: List<TodayItem>, val hintsEnabled: Boolean)

    private val dismissed = MutableStateFlow<Set<String>>(emptySet())

    private val history = combine(
        repo.observeAllLogs(),
        repo.observeAllNotes(),
        repo.observeAllExceptions(),
    ) { logs, notes, exceptions -> Triple(logs, notes, exceptions) }

    private val computed = combine(
        todayTicker(),
        repo.observeActiveHabits(),
        history,
        settings.settings,
    ) { today, habits, (logs, notes, exceptions), appSettings ->
        val index = logs.toIndex()
        val exceptionIndex = exceptions.toExceptionIndex()
        val noteIndex = notes.associate { (it.habitId to it.localDate) to it.text }
        val weekStart = HabitLogic.weekStart(today)
        val yesterday = today.minusDays(1)
        val items = habits.mapNotNull { hs ->
            val habitLogs = index[hs.habit.id].orEmpty()
            val ex = exceptionIndex[hs.habit.id] ?: ScheduleExceptions.NONE
            val start = HabitLogic.startOf(hs.habit, hs.schedule, habitLogs.keys.minByOrNull { it.toEpochDay() })
            if (!HabitLogic.isScheduledOn(hs.schedule, today, start, ex)) return@mapNotNull null
            val status = habitLogs[today]
            val recurrence = hs.schedule?.recurrenceType ?: RecurrenceType.DAILY
            val weekly = recurrence == RecurrenceType.TIMES_PER_WEEK
            val weekTarget = if (weekly) HabitLogic.weekTarget(hs.habit, hs.schedule, habitLogs, weekStart, start) else null
            val missed = HabitLogic.missedOnce(hs.habit, hs.schedule, habitLogs, today, ex)
            TodayItem(
                habit = hs.habit,
                recurrence = recurrence,
                status = status,
                state = HabitLogic.dayState(hs.habit.type, status),
                weekDone = if (weekly) HabitLogic.weekProgress(habitLogs, weekStart) else null,
                weekTarget = weekTarget?.total,
                weekExtra = weekTarget?.extra ?: 0,
                streak = HabitLogic.currentStreak(hs.habit, hs.schedule, habitLogs, today, ex),
                note = noteIndex[hs.habit.id to today.toString()],
                missedYesterday = missed,
                movedFrom = ex.movedFrom(today),
            )
        }
        Computed(today, items, appSettings.missedHint)
    }

    val uiState: StateFlow<TodayUiState> = combine(computed, dismissed) { c, hidden ->
        val hints = if (!c.hintsEnabled) emptyList() else {
            c.items.filter { it.missedYesterday && "${it.habit.id}:${c.date}" !in hidden }.map { it.habit }
        }
        TodayUiState(c.date, c.items, hints)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun setStatus(habitId: Long, status: LogStatus?) {
        viewModelScope.launch { repo.setLog(habitId, LocalDate.now(), status) }
    }

    fun saveNote(habitId: Long, text: String) {
        viewModelScope.launch { repo.setNote(habitId, LocalDate.now(), text) }
    }

    fun dismissHints() {
        val state = uiState.value
        dismissed.update { it + state.hints.map { h -> "${h.id}:${state.date}" } }
    }

    fun skipToday(habitId: Long, reason: String) {
        viewModelScope.launch { repo.skipDayWithReason(habitId, LocalDate.now(), reason) }
    }

    fun moveToday(habitId: Long, target: LocalDate) {
        viewModelScope.launch { repo.moveDay(habitId, LocalDate.now(), target) }
    }

    /** Undoes the skip/move that touches today. */
    fun undoException(habitId: Long) {
        viewModelScope.launch { repo.undoException(habitId, LocalDate.now()) }
    }
}
