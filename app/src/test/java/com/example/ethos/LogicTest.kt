package com.example.ethos

import com.example.ethos.data.ExceptionKind
import com.example.ethos.data.Habit
import com.example.ethos.data.HabitException
import com.example.ethos.data.HabitSchedule
import com.example.ethos.data.HabitType
import com.example.ethos.data.LogStatus
import com.example.ethos.data.Reminder
import com.example.ethos.data.RecurrenceType
import com.example.ethos.data.ReminderMode
import com.example.ethos.domain.DayState
import com.example.ethos.domain.HabitLogic
import com.example.ethos.domain.ReminderPlanner
import com.example.ethos.domain.nextDailyOccurrence
import com.example.ethos.domain.ScheduleExceptions
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class LogicTest {
    private val today = LocalDate.of(2026, 10, 11)

    private fun habit(type: HabitType) = Habit(
        id = 1,
        title = "x",
        type = type,
        createdAt = today.minusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    )

    @Test fun avoidWithoutEntryCountsAsAvoided() =
        assertEquals(DayState.AVOIDED, HabitLogic.dayState(HabitType.AVOID, null))

    @Test fun buildWithoutEntryIsNotRecorded() =
        assertEquals(DayState.NOT_RECORDED, HabitLogic.dayState(HabitType.BUILD, null))

    @Test fun buildStreakIgnoresPendingToday() {
        val logs = mapOf(
            today.minusDays(1) to LogStatus.COMPLETED,
            today.minusDays(2) to LogStatus.COMPLETED,
        )
        assertEquals(2, HabitLogic.currentStreak(habit(HabitType.BUILD), null, logs, today))
    }

    @Test fun avoidStreakBreaksOnFailure() {
        val logs = mapOf(today.minusDays(3) to LogStatus.FAILED)
        assertEquals(3, HabitLogic.currentStreak(habit(HabitType.AVOID), null, logs, today))
    }

    @Test fun fixedTimesPicksNextSlot() {
        val zone = ZoneId.of("UTC")
        val reminder = Reminder(mode = ReminderMode.FIXED_TIMES, fixedTimes = "08:00,20:00")
        val after = LocalDateTime.of(2026, 10, 11, 9, 0).atZone(zone).toInstant()
        val expected = LocalDateTime.of(2026, 10, 11, 20, 0).atZone(zone).toInstant()
        assertEquals(expected, ReminderPlanner.nextTrigger(reminder, after, zone))
    }

    @Test fun skippedDayNeitherBreaksNorCountsForStreak() {
        val logs = mapOf(
            today.minusDays(2) to LogStatus.COMPLETED,
            today.minusDays(3) to LogStatus.COMPLETED,
        )
        val skipYesterday = ScheduleExceptions(
            listOf(
                HabitException(
                    habitId = 1,
                    originDate = today.minusDays(1).toString(),
                    kind = ExceptionKind.SKIPPED,
                    createdAt = 0,
                ),
            ),
        )
        assertEquals(2, HabitLogic.currentStreak(habit(HabitType.BUILD), null, logs, today, skipYesterday))
    }

    @Test fun movedDayIsPlannedOnTargetOnly() {
        // Weekdays: Monday only. Monday 2026-10-05 is moved to Tuesday 2026-10-06.
        val schedule = HabitSchedule(habitId = 1, recurrenceType = RecurrenceType.WEEKDAYS, weekdays = "1")
        val ex = ScheduleExceptions(
            listOf(
                HabitException(
                    habitId = 1,
                    originDate = "2026-10-05",
                    kind = ExceptionKind.MOVED,
                    targetDate = "2026-10-06",
                    createdAt = 0,
                ),
            ),
        )
        val start = LocalDate.of(2026, 9, 1)
        assertEquals(false, HabitLogic.isScheduledOn(schedule, LocalDate.of(2026, 10, 5), start, ex))
        assertEquals(true, HabitLogic.isScheduledOn(schedule, LocalDate.of(2026, 10, 6), start, ex))
        assertEquals(true, HabitLogic.isScheduledOn(schedule, LocalDate.of(2026, 10, 12), start, ex))
    }

    @Test fun catchUpAddsLastWeeksShortfall() {
        val weekStart = HabitLogic.weekStart(today) // 2026-10-05
        val previous = weekStart.minusWeeks(1)
        val threeOfFour = (0L..2L).associate { previous.plusDays(it) to LogStatus.COMPLETED }
        val habit = habit(HabitType.BUILD)
        val start = HabitLogic.startOf(habit, null, null)
        fun schedule(catchUp: Boolean) =
            HabitSchedule(habitId = 1, recurrenceType = RecurrenceType.TIMES_PER_WEEK, weeklyTarget = 4, catchUp = catchUp)

        assertEquals(5, HabitLogic.weekTarget(habit, schedule(true), threeOfFour, weekStart, start).total)
        assertEquals(4, HabitLogic.weekTarget(habit, schedule(false), threeOfFour, weekStart, start).total)
        // Nothing done last week: capped at +2, not +4.
        assertEquals(6, HabitLogic.weekTarget(habit, schedule(true), emptyMap(), weekStart, start).total)
    }

    private fun intervalReminder(firedCount: Int) = Reminder(
        mode = ReminderMode.FIXED_INTERVAL,
        minIntervalMinutes = 60,
        startTime = "09:00",
        endTime = "12:00",
        maxPerDay = 2,
        firedDate = "2026-10-11",
        firedCount = firedCount,
    )

    @Test fun dailyLimitReachedMovesToNextDay() {
        val zone = ZoneId.of("UTC")
        val after = LocalDateTime.of(2026, 10, 11, 10, 30).atZone(zone).toInstant()
        val expected = LocalDateTime.of(2026, 10, 12, 9, 0).atZone(zone).toInstant()
        assertEquals(expected, ReminderPlanner.nextTrigger(intervalReminder(firedCount = 2), after, zone))
    }

    @Test fun belowDailyLimitKeepsTodaysNextSlot() {
        val zone = ZoneId.of("UTC")
        val after = LocalDateTime.of(2026, 10, 11, 10, 30).atZone(zone).toInstant()
        val expected = LocalDateTime.of(2026, 10, 11, 11, 0).atZone(zone).toInstant()
        assertEquals(expected, ReminderPlanner.nextTrigger(intervalReminder(firedCount = 1), after, zone))
    }

    @Test fun missedOnceWhenYesterdayWasMissedAndTodayIsOpen() {
        val logs = mapOf(today.minusDays(2) to LogStatus.COMPLETED)
        assertEquals(true, HabitLogic.missedOnce(habit(HabitType.BUILD), null, logs, today))
    }

    @Test fun notMissedOnceWhenYesterdayWasDoneOrTodayIsDone() {
        val doneYesterday = mapOf(today.minusDays(1) to LogStatus.COMPLETED)
        assertEquals(false, HabitLogic.missedOnce(habit(HabitType.BUILD), null, doneYesterday, today))
        val doneToday = mapOf(today to LogStatus.COMPLETED)
        assertEquals(false, HabitLogic.missedOnce(habit(HabitType.BUILD), null, doneToday, today))
    }

    @Test fun avoidHabitIsMissedOnceAfterSlipYesterday() {
        val logs = mapOf(today.minusDays(1) to LogStatus.FAILED)
        assertEquals(true, HabitLogic.missedOnce(habit(HabitType.AVOID), null, logs, today))
    }

    @Test fun dailyOccurrenceIsTodayIfStillAheadElseTomorrow() {
        val zone = ZoneId.of("UTC")
        val morning = LocalDateTime.of(2026, 10, 11, 7, 0).atZone(zone).toInstant()
        val evening = LocalDateTime.of(2026, 10, 11, 20, 0).atZone(zone).toInstant()
        val at8 = 8 * 60
        assertEquals(LocalDateTime.of(2026, 10, 11, 8, 0).atZone(zone).toInstant(), nextDailyOccurrence(at8, morning, zone))
        assertEquals(LocalDateTime.of(2026, 10, 12, 8, 0).atZone(zone).toInstant(), nextDailyOccurrence(at8, evening, zone))
    }
}
