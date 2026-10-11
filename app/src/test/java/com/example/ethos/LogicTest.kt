package com.example.ethos

import com.example.ethos.data.Habit
import com.example.ethos.data.HabitType
import com.example.ethos.data.LogStatus
import com.example.ethos.data.Reminder
import com.example.ethos.data.ReminderMode
import com.example.ethos.domain.DayState
import com.example.ethos.domain.HabitLogic
import com.example.ethos.domain.ReminderPlanner
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
}
