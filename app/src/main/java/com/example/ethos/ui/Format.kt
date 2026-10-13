package com.example.ethos.ui

import com.example.ethos.data.Habit
import com.example.ethos.data.HabitSchedule
import com.example.ethos.data.HabitType
import com.example.ethos.data.Reminder
import com.example.ethos.data.ReminderMode
import com.example.ethos.data.RecurrenceType
import com.example.ethos.data.parseDays
import com.example.ethos.domain.ReminderPlanner
import java.time.format.DateTimeFormatter
import java.util.Locale

val WEEKDAY_SHORT = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)
val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Symbol is derived automatically from the habit type; there is no manual selection. */
fun habitSymbol(type: HabitType): String = if (type == HabitType.BUILD) "🌱" else "🚫"

fun Habit.displayTitle(): String = "${habitSymbol(type)} $title"

val BUILD_LAW_LABELS = listOf("Offensichtlich machen", "Attraktiv machen", "Einfach machen", "Befriedigend machen")
val AVOID_LAW_LABELS = listOf("Unsichtbar machen", "Unattraktiv machen", "Schwierig machen", "Unbefriedigend machen")

val BUILD_LAW_HINTS = listOf(
    "Wann und wo machst du es? Woran wirst du erinnert?",
    "Womit verbindest du es, damit es dir Freude macht?",
    "Wie machst du den Start so leicht wie möglich?",
    "Wie belohnst du dich direkt danach?",
)
val AVOID_LAW_HINTS = listOf(
    "Wie entfernst du die Auslöser aus deinem Umfeld?",
    "Woran erinnerst du dich, damit es seinen Reiz verliert?",
    "Welche Hürde baust du davor ein?",
    "Welche Konsequenz macht es unangenehm?",
)

fun scheduleSummary(schedule: HabitSchedule?): String =
    when (schedule?.recurrenceType ?: RecurrenceType.DAILY) {
        RecurrenceType.DAILY -> "Täglich"
        RecurrenceType.WEEKDAYS ->
            parseDays(schedule?.weekdays).sorted().joinToString(", ") { WEEKDAY_SHORT[it - 1] }
                .ifEmpty { "Keine Tage" }
        RecurrenceType.TIMES_PER_WEEK -> "${schedule?.weeklyTarget ?: 1}× pro Woche"
    }

fun reminderSummary(r: Reminder): String {
    val window = "${r.startTime}–${r.endTime}"
    val mode = when (r.mode) {
        ReminderMode.FIXED_TIMES ->
            ReminderPlanner.fixedTimes(r).joinToString(", ") { it.format(TIME_FMT) }.ifEmpty { "Keine Zeiten" }
        ReminderMode.FIXED_INTERVAL -> "Alle ${r.minIntervalMinutes ?: 60} Min · $window"
        ReminderMode.RANDOM_INTERVAL ->
            "Zufällig ${r.minIntervalMinutes ?: 30}–${r.maxIntervalMinutes ?: r.minIntervalMinutes ?: 30} Min · $window"
    }
    val days = parseDays(r.weekdays)
    val dayText = if (days.size == 7 || days.isEmpty()) "täglich" else days.sorted().joinToString(", ") { WEEKDAY_SHORT[it - 1] }
    return "$mode · $dayText"
}
