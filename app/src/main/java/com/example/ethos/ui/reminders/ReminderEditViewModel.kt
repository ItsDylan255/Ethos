package com.example.ethos.ui.reminders

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ethos.data.EthosRepository
import com.example.ethos.data.Habit
import com.example.ethos.data.Reminder
import com.example.ethos.data.ReminderMode
import com.example.ethos.data.daysToCsv
import com.example.ethos.data.parseDays
import com.example.ethos.domain.ReminderPlanner
import com.example.ethos.ui.TIME_FMT
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

data class ReminderForm(
    val id: Long = 0,
    val habitId: Long? = null,
    val title: String = "",
    val mode: ReminderMode = ReminderMode.FIXED_TIMES,
    val fixedTimes: List<LocalTime> = listOf(LocalTime.of(9, 0)),
    val windowStart: LocalTime = LocalTime.of(9, 0),
    val windowEnd: LocalTime = LocalTime.of(21, 0),
    val weekdays: Set<Int> = (1..7).toSet(),
    val intervalMin: Int = 60,
    val intervalMax: Int = 120,
    val snoozeMinutes: Int = 10,
    val isEnabled: Boolean = true,
    val pausedUntil: Long? = null,
    /** Daily limit for interval modes; null = unlimited. */
    val maxPerDay: Int? = null,
    /** Kept so that editing does not reset today's counter. */
    val firedDate: String? = null,
    val firedCount: Int = 0,
) {
    val canSave: Boolean
        get() = (title.isNotBlank() || habitId != null) &&
            weekdays.isNotEmpty() &&
            (mode != ReminderMode.FIXED_TIMES || fixedTimes.isNotEmpty())
}

class ReminderEditViewModel(
    private val repo: EthosRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val reminderId: Long = savedStateHandle["id"] ?: -1L

    val isEditing: Boolean = reminderId > 0

    var form by mutableStateOf(ReminderForm())
        private set

    val habits: StateFlow<List<Habit>> = repo.observeActiveHabits()
        .map { list -> list.map { it.habit } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (isEditing) {
            viewModelScope.launch { repo.getReminder(reminderId)?.let { form = it.toForm() } }
        }
    }

    fun update(transform: (ReminderForm) -> ReminderForm) {
        form = transform(form)
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val f = form
            val min = if (f.mode == ReminderMode.FIXED_TIMES) null else f.intervalMin
            val max = if (f.mode == ReminderMode.RANDOM_INTERVAL) f.intervalMax.coerceAtLeast(f.intervalMin) else null
            repo.saveReminder(
                Reminder(
                    id = f.id,
                    habitId = f.habitId,
                    title = f.title.trim(),
                    mode = f.mode,
                    isEnabled = f.isEnabled,
                    startTime = f.windowStart.format(TIME_FMT),
                    endTime = f.windowEnd.format(TIME_FMT),
                    weekdays = daysToCsv(f.weekdays),
                    minIntervalMinutes = min,
                    maxIntervalMinutes = max,
                    fixedTimes = if (f.mode == ReminderMode.FIXED_TIMES) {
                        f.fixedTimes.sorted().joinToString(",") { it.format(TIME_FMT) }
                    } else null,
                    snoozeMinutes = f.snoozeMinutes,
                    pausedUntil = f.pausedUntil,
                    maxPerDay = if (f.mode == ReminderMode.FIXED_TIMES) null else f.maxPerDay,
                    firedDate = f.firedDate,
                    firedCount = f.firedCount,
                ),
            )
            onDone()
        }
    }
}

private fun Reminder.toForm() = ReminderForm(
    id = id,
    habitId = habitId,
    title = title,
    mode = mode,
    fixedTimes = ReminderPlanner.fixedTimes(this).ifEmpty { listOf(LocalTime.of(9, 0)) },
    windowStart = ReminderPlanner.parseTime(startTime) ?: LocalTime.of(9, 0),
    windowEnd = ReminderPlanner.parseTime(endTime) ?: LocalTime.of(21, 0),
    weekdays = parseDays(weekdays).ifEmpty { (1..7).toSet() },
    intervalMin = minIntervalMinutes ?: 60,
    intervalMax = maxIntervalMinutes ?: ((minIntervalMinutes ?: 60) * 2),
    snoozeMinutes = snoozeMinutes,
    isEnabled = isEnabled,
    pausedUntil = pausedUntil,
    maxPerDay = maxPerDay,
    firedDate = firedDate,
    firedCount = firedCount,
)
