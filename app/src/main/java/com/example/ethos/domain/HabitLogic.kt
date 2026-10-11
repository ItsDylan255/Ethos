package com.example.ethos.domain

import com.example.ethos.data.Habit
import com.example.ethos.data.HabitLog
import com.example.ethos.data.HabitSchedule
import com.example.ethos.data.HabitType
import com.example.ethos.data.LogStatus
import com.example.ethos.data.RecurrenceType
import com.example.ethos.data.parseDays
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** Effective state of a day, derived from the (optional) log entry and the habit type. */
enum class DayState { COMPLETED, FAILED, NOT_RECORDED, AVOIDED }

data class SuccessRate(val success: Int, val evaluated: Int) {
    val percent: Int? get() = if (evaluated == 0) null else (success * 100f / evaluated).roundToInt()
}

data class WeekResult(val weekStart: LocalDate, val done: Int, val target: Int) {
    val met: Boolean get() = done >= target
    val weekNumber: Int get() = weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
}

object HabitLogic {

    fun dayState(type: HabitType, status: LogStatus?): DayState = when (status) {
        LogStatus.COMPLETED -> DayState.COMPLETED
        LogStatus.FAILED -> DayState.FAILED
        null -> if (type == HabitType.AVOID) DayState.AVOIDED else DayState.NOT_RECORDED
    }

    fun isSuccess(state: DayState): Boolean = state == DayState.COMPLETED || state == DayState.AVOIDED

    /** First day that counts for this habit: start date or creation day, or an earlier backfilled log. */
    fun startOf(
        habit: Habit,
        schedule: HabitSchedule?,
        earliestLog: LocalDate?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalDate {
        val created = Instant.ofEpochMilli(habit.createdAt).atZone(zone).toLocalDate()
        val base = schedule?.startDate?.let { LocalDate.ofEpochDay(it) } ?: created
        return if (earliestLog != null && earliestLog.isBefore(base)) earliestLog else base
    }

    fun isScheduledOn(schedule: HabitSchedule?, date: LocalDate, start: LocalDate): Boolean {
        if (date.isBefore(start)) return false
        val end = schedule?.endDate
        if (end != null && date.toEpochDay() > end) return false
        if (schedule != null && schedule.recurrenceType == RecurrenceType.WEEKDAYS) {
            return date.dayOfWeek.value in parseDays(schedule.weekdays)
        }
        return true // DAILY and TIMES_PER_WEEK (any day counts towards the weekly target)
    }

    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Number of distinct COMPLETED days in the calendar week (Mon-Sun). */
    fun weekProgress(logs: Map<LocalDate, LogStatus>, weekStart: LocalDate): Int =
        (0L..6L).count { logs[weekStart.plusDays(it)] == LogStatus.COMPLETED }

    /**
     * State shown on a calendar cell. null = nothing to show (future day or not scheduled).
     * A recorded log is always shown, even on unscheduled days.
     */
    fun cellState(
        habit: Habit,
        schedule: HabitSchedule?,
        logs: Map<LocalDate, LogStatus>,
        date: LocalDate,
        today: LocalDate,
        start: LocalDate,
    ): DayState? {
        if (date.isAfter(today)) return null
        val status = logs[date]
        if (status == null && !isScheduledOn(schedule, date, start)) return null
        return dayState(habit.type, status)
    }

    /**
     * Consecutive successful scheduled days up to today.
     * Today without an entry never breaks a BUILD streak. Not available for TIMES_PER_WEEK (null).
     */
    fun currentStreak(
        habit: Habit,
        schedule: HabitSchedule?,
        logs: Map<LocalDate, LogStatus>,
        today: LocalDate,
    ): Int? {
        if (schedule?.recurrenceType == RecurrenceType.TIMES_PER_WEEK) return null
        val start = startOf(habit, schedule, logs.keys.minByOrNull { it.toEpochDay() })
        var streak = 0
        var d = today
        while (!d.isBefore(start)) {
            if (isScheduledOn(schedule, d, start)) {
                val state = dayState(habit.type, logs[d])
                if (isSuccess(state)) {
                    streak++
                } else if (!(d == today && state == DayState.NOT_RECORDED)) {
                    break
                }
            }
            d = d.minusDays(1)
        }
        return streak
    }

    /**
     * Success rate over scheduled days in [from, today].
     * BUILD: COMPLETED / (COMPLETED + FAILED); days without entry are not counted as failures.
     * AVOID: unreported past days count as success; today only counts when it already failed.
     */
    fun successRate(
        habit: Habit,
        schedule: HabitSchedule?,
        logs: Map<LocalDate, LogStatus>,
        from: LocalDate?,
        today: LocalDate,
    ): SuccessRate {
        val start = startOf(habit, schedule, logs.keys.minByOrNull { it.toEpochDay() })
        var d = if (from != null && from.isAfter(start)) from else start
        var success = 0
        var evaluated = 0
        while (!d.isAfter(today)) {
            if (isScheduledOn(schedule, d, start)) {
                when (dayState(habit.type, logs[d])) {
                    DayState.COMPLETED -> { success++; evaluated++ }
                    DayState.FAILED -> evaluated++
                    DayState.AVOIDED -> if (d != today) { success++; evaluated++ }
                    DayState.NOT_RECORDED -> Unit
                }
            }
            d = d.plusDays(1)
        }
        return SuccessRate(success, evaluated)
    }

    /** Last [weeks] calendar weeks (newest first) for TIMES_PER_WEEK habits. */
    fun weeklyHistory(
        habit: Habit,
        schedule: HabitSchedule?,
        logs: Map<LocalDate, LogStatus>,
        today: LocalDate,
        weeks: Int = 8,
    ): List<WeekResult> {
        if (schedule?.recurrenceType != RecurrenceType.TIMES_PER_WEEK) return emptyList()
        val start = startOf(habit, schedule, logs.keys.minByOrNull { it.toEpochDay() })
        val firstWeek = weekStart(start)
        val target = schedule.weeklyTarget ?: 1
        return (0 until weeks)
            .map { weekStart(today).minusWeeks(it.toLong()) }
            .filter { !it.isBefore(firstWeek) }
            .map { ws -> WeekResult(ws, weekProgress(logs, ws), target) }
    }
}

fun List<HabitLog>.toIndex(): Map<Long, Map<LocalDate, LogStatus>> =
    groupBy { it.habitId }.mapValues { (_, list) ->
        list.associate { LocalDate.parse(it.localDate) to it.status }
    }

/** Emits today's date now and again right after each midnight. */
fun todayTicker(): Flow<LocalDate> = flow {
    while (true) {
        val now = LocalDateTime.now()
        emit(now.toLocalDate())
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
        delay(Duration.between(now, nextMidnight).toMillis() + 1_000)
    }
}
