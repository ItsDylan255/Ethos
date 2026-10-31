package com.example.ethos.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.ethos.R
import com.example.ethos.data.Habit
import com.example.ethos.data.HabitSchedule
import com.example.ethos.data.Reminder
import com.example.ethos.data.ReminderMode
import com.example.ethos.data.RecurrenceType
import com.example.ethos.data.parseDays
import com.example.ethos.domain.ReminderPlanner
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Locale-dependent values are getters: the app language can change while the process lives.

/** Two letters per day (Mo, Di … / Mo, Tu …), small enough for the day circles. */
val WEEKDAY_SHORT: List<String>
    get() = DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2) }

/** Date pattern in the current app language; German and English order day and month differently. */
fun localizedFormatter(german: String, english: String): DateTimeFormatter {
    val locale = Locale.getDefault()
    return DateTimeFormatter.ofPattern(if (locale.language == "de") german else english, locale)
}

val DATE_FMT: DateTimeFormatter get() = localizedFormatter("d. MMM yyyy", "MMM d, yyyy")
val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** The type symbol is drawn by TypeSymbol/TypeBadge; titles stay plain text. */
fun Habit.displayTitle(): String = title

@Composable
fun scheduleSummary(schedule: HabitSchedule?): String =
    when (schedule?.recurrenceType ?: RecurrenceType.DAILY) {
        RecurrenceType.DAILY -> stringResource(R.string.schedule_daily)
        RecurrenceType.WEEKDAYS ->
            parseDays(schedule?.weekdays).sorted().joinToString(", ") { WEEKDAY_SHORT[it - 1] }
                .ifEmpty { stringResource(R.string.schedule_no_days) }
        RecurrenceType.TIMES_PER_WEEK -> stringResource(R.string.schedule_per_week, schedule?.weeklyTarget ?: 1)
    }

@Composable
fun reminderSummary(r: Reminder): String {
    val window = "${r.startTime}–${r.endTime}"
    val limit = r.maxPerDay?.let { " · " + stringResource(R.string.reminder_max_per_day, it) }.orEmpty()
    val mode = when (r.mode) {
        ReminderMode.FIXED_TIMES ->
            ReminderPlanner.fixedTimes(r).joinToString(", ") { it.format(TIME_FMT) }
                .ifEmpty { stringResource(R.string.reminder_no_times) }
        ReminderMode.FIXED_INTERVAL ->
            stringResource(R.string.reminder_every, r.minIntervalMinutes ?: 60) + " · $window$limit"
        ReminderMode.RANDOM_INTERVAL -> {
            val min = r.minIntervalMinutes ?: 30
            stringResource(R.string.reminder_random, min, r.maxIntervalMinutes ?: min) + " · $window$limit"
        }
    }
    val days = parseDays(r.weekdays)
    val dayText = if (days.size == 7 || days.isEmpty()) stringResource(R.string.reminder_daily)
    else days.sorted().joinToString(", ") { WEEKDAY_SHORT[it - 1] }
    return "$mode · $dayText"
}
