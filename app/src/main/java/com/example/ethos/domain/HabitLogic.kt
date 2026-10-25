package com.example.ethos.domain

import com.example.ethos.data.ExceptionKind
import com.example.ethos.data.Habit
import com.example.ethos.data.HabitException
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

data class WeekResult(val weekStart: LocalDate, val done: Int, val target: Int, val extra: Int = 0) {
    val met: Boolean get() = done >= target
    val weekNumber: Int get() = weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
}

/** Weekly goal of a TIMES_PER_WEEK habit: the base target plus carried-over sessions from last week. */
data class WeekTarget(val base: Int, val extra: Int) {
    val total: Int get() = base + extra
}

/** The exceptions (skipped / moved days) of one habit. */
class ScheduleExceptions(list: List<HabitException> = emptyList()) {
    /** Days that are dropped from the schedule, with the exception that dropped them. */
    val byOrigin: Map<LocalDate, HabitException> = list.associateBy { LocalDate.parse(it.originDate) }

    /** Target day -> origin day for moved habits. */
    val movedIn: Map<LocalDate, LocalDate> = list.mapNotNull { e ->
        if (e.kind == ExceptionKind.MOVED && e.targetDate != null) {
            LocalDate.parse(e.targetDate) to LocalDate.parse(e.originDate)
        } else null
    }.toMap()

    fun isRemoved(date: LocalDate): Boolean = byOrigin.containsKey(date)
    fun movedFrom(date: LocalDate): LocalDate? = movedIn[date]

    companion object {
        val NONE = ScheduleExceptions()
    }
}

object HabitLogic {
    /** At most this many missed sessions are carried into the next week. */
    const val MAX_CATCH_UP = 2

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

    /**
     * Is the habit planned for [date]? A day that another day was moved to is always planned;
     * a skipped or moved-away day never is.
     */
    fun isScheduledOn(
        schedule: HabitSchedule?,
        date: LocalDate,
        start: LocalDate,
        ex: ScheduleExceptions = ScheduleExceptions.NONE,
    ): Boolean {
        if (ex.movedIn.containsKey(date)) return true
        if (ex.isRemoved(date)) return false
        if (date.isBefore(start)) return false
        val end = schedule?.endDate
        if (end != null && date.toEpochDay() > end) return false
        if (schedule != null && schedule.recurrenceType == RecurrenceType.WEEKDAYS) {
            return date.dayOfWeek.value in parseDays(schedule.weekdays)
        }
        return true // DAILY and TIMES_PER_WEEK (any day counts towards the weekly target)
    }

    /**
     * "Never miss twice": the habit is due today, was due yesterday and did not succeed then,
     * and is not done today. Weekly-target habits are excluded (a free day is normal there).
     */
    fun missedOnce(
        habit: Habit,
        schedule: HabitSchedule?,
        logs: Map<LocalDate, LogStatus>,
        today: LocalDate,
        ex: ScheduleExceptions = ScheduleExceptions.NONE,
    ): Boolean {
        if (schedule?.recurrenceType == RecurrenceType.TIMES_PER_WEEK) return false
        val start = startOf(habit, schedule, logs.keys.minByOrNull { it.toEpochDay() })
        if (!isScheduledOn(schedule, today, start, ex)) return false
        val yesterday = today.minusDays(1)
        return isScheduledOn(schedule, yesterday, start, ex) &&
            !isSuccess(dayState(habit.type, logs[yesterday])) &&
            logs[today] != LogStatus.COMPLETED
    }

    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Number of distinct COMPLETED days in the calendar week (Mon-Sun). */
    fun weekProgress(logs: Map<LocalDate, LogStatus>, weekStart: LocalDate): Int =
        (0L..6L).count { logs[weekStart.plusDays(it)] == LogStatus.COMPLETED }

    /**
     * Goal for the week starting at [weekStart]. With catch-up enabled, the shortfall of the previous
     * week (measured against the base target, never against an already raised one) is added, capped at
     * [MAX_CATCH_UP] and at 7 sessions in total. A week only carries over if the habit existed for all of it.
     */
    fun weekTarget(
        habit: Habit,
        schedule: HabitSchedule?,
        logs: Map<LocalDate, LogStatus>,
        weekStart: LocalDate,
        start: LocalDate,
    ): WeekTarget {
        val base = schedule?.weeklyTarget ?: 1
        if (schedule?.catchUp != true) return WeekTarget(base, 0)
        val previous = weekStart.minusWeeks(1)
        if (previous.isBefore(start)) return WeekTarget(base, 0)
        val shortfall = (base - weekProgress(logs, previous)).coerceAtLeast(0)
        val extra = shortfall.coerceAtMost(MAX_CATCH_UP).coerceAtMost((7 - base).coerceAtLeast(0))
        return WeekTarget(base, extra)
    }

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
        ex: ScheduleExceptions = ScheduleExceptions.NONE,
    ): DayState? {
        if (date.isAfter(today)) return null
        val status = logs[date]
        if (status == null && !isScheduledOn(schedule, date, start, ex)) return null
        return dayState(habit.type, status)
    }

    /**
     * Consecutive successful scheduled days up to today. Skipped days neither count nor break the streak.
     * Today without an entry never breaks a BUILD streak. Not available for TIMES_PER_WEEK (null).
     */
    fun currentStreak(
        habit: Habit,
        schedule: HabitSchedule?,
        logs: Map<LocalDate, LogStatus>,
        today: LocalDate,
        ex: ScheduleExceptions = ScheduleExceptions.NONE,
    ): Int? {
        if (schedule?.recurrenceType == RecurrenceType.TIMES_PER_WEEK) return null
        val start = startOf(habit, schedule, logs.keys.minByOrNull { it.toEpochDay() })
        var streak = 0
        var d = today
        while (!d.isBefore(start)) {
            if (isScheduledOn(schedule, d, start, ex)) {
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
        ex: ScheduleExceptions = ScheduleExceptions.NONE,
    ): SuccessRate {
        val start = startOf(habit, schedule, logs.keys.minByOrNull { it.toEpochDay() })
        var d = if (from != null && from.isAfter(start)) from else start
        var success = 0
        var evaluated = 0
        while (!d.isAfter(today)) {
            if (isScheduledOn(schedule, d, start, ex)) {
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

    /** Last [weeks] calendar weeks (newest first) for TIMES_PER_WEEK habits, including carried-over targets. */
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
        return (0 until weeks)
            .map { weekStart(today).minusWeeks(it.toLong()) }
            .filter { !it.isBefore(firstWeek) }
            .map { ws ->
                val target = weekTarget(habit, schedule, logs, ws, start)
                WeekResult(ws, weekProgress(logs, ws), target.total, target.extra)
            }
    }
}

fun List<HabitLog>.toIndex(): Map<Long, Map<LocalDate, LogStatus>> =
    groupBy { it.habitId }.mapValues { (_, list) ->
        list.associate { LocalDate.parse(it.localDate) to it.status }
    }

fun List<HabitException>.toExceptionIndex(): Map<Long, ScheduleExceptions> =
    groupBy { it.habitId }.mapValues { (_, list) -> ScheduleExceptions(list) }

/** Emits today's date now and again right after each midnight. */
fun todayTicker(): Flow<LocalDate> = flow {
    while (true) {
        val now = LocalDateTime.now()
        emit(now.toLocalDate())
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
        delay(Duration.between(now, nextMidnight).toMillis() + 1_000)
    }
}
