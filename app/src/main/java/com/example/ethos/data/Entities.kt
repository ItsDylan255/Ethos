package com.example.ethos.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class HabitType { BUILD, AVOID }
enum class RecurrenceType { DAILY, WEEKDAYS, TIMES_PER_WEEK }
enum class LogStatus { COMPLETED, FAILED }
enum class ReminderMode { FIXED_TIMES, FIXED_INTERVAL, RANDOM_INTERVAL }

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String? = null,
    val type: HabitType = HabitType.BUILD,
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    /**
     * The four laws of behavior change, as concrete personal strategies.
     * BUILD: 1 obvious, 2 attractive, 3 easy, 4 satisfying.
     * AVOID: 1 invisible, 2 unattractive, 3 difficult, 4 unsatisfying.
     */
    val law1: String = "",
    val law2: String = "",
    val law3: String = "",
    val law4: String = "",
)

@Entity(
    tableName = "habit_schedules",
    foreignKeys = [
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // One schedule per habit.
    indices = [Index("habitId", unique = true)],
)
data class HabitSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val recurrenceType: RecurrenceType = RecurrenceType.DAILY,
    /** CSV of ISO weekday numbers, 1 = Monday ... 7 = Sunday. */
    val weekdays: String = "",
    val weeklyTarget: Int? = null,
    /** LocalDate.toEpochDay() */
    val startDate: Long? = null,
    /** LocalDate.toEpochDay() */
    val endDate: Long? = null,
)

/** No row = NOT_RECORDED. */
@Entity(
    tableName = "habit_logs",
    foreignKeys = [
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["habitId", "localDate"], unique = true)],
)
data class HabitLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    /** ISO yyyy-MM-dd */
    val localDate: String,
    val status: LogStatus,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId")],
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** null = free reminder */
    val habitId: Long? = null,
    val title: String = "",
    val mode: ReminderMode = ReminderMode.FIXED_TIMES,
    val isEnabled: Boolean = true,
    val startTime: String = "09:00",
    val endTime: String = "21:00",
    val weekdays: String = "1,2,3,4,5,6,7",
    /** FIXED_INTERVAL uses only this value as its interval. */
    val minIntervalMinutes: Int? = null,
    val maxIntervalMinutes: Int? = null,
    /** CSV of HH:mm */
    val fixedTimes: String? = null,
    val snoozeMinutes: Int = 10,
    // Planning data
    val pausedUntil: Long? = null,
    val nextTriggerAt: Long? = null,
)

data class HabitWithSchedule(
    @Embedded val habit: Habit,
    @Relation(parentColumn = "id", entityColumn = "habitId") val schedule: HabitSchedule?,
)

fun parseDays(csv: String?): Set<Int> =
    csv.orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()

fun daysToCsv(days: Set<Int>): String = days.sorted().joinToString(",")
