package com.example.ethos.domain

import com.example.ethos.data.Reminder
import com.example.ethos.data.ReminderMode
import com.example.ethos.data.parseDays
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/** Pure function that decides when a reminder fires next. No Android dependencies. */
object ReminderPlanner {

    fun nextTrigger(
        reminder: Reminder,
        after: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
        random: Random = Random.Default,
    ): Instant? {
        val pausedUntil = reminder.pausedUntil?.let { Instant.ofEpochMilli(it) }
        val from = if (pausedUntil != null && pausedUntil.isAfter(after)) pausedUntil else after
        val allowed = parseDays(reminder.weekdays).ifEmpty { (1..7).toSet() }
        val start = parseTime(reminder.startTime) ?: LocalTime.of(9, 0)
        val end = parseTime(reminder.endTime) ?: LocalTime.of(21, 0)
        val firstDay = from.atZone(zone).toLocalDate()

        for (offset in 0L..7L) {
            val date = firstDay.plusDays(offset)
            if (date.dayOfWeek.value !in allowed) continue
            val windowStart = at(date, start, zone)
            val windowEnd = if (end.isAfter(start)) at(date, end, zone) else at(date.plusDays(1), end, zone)

            val candidate: Instant? = when (reminder.mode) {
                ReminderMode.FIXED_TIMES ->
                    fixedTimes(reminder).map { at(date, it, zone) }.firstOrNull { it.isAfter(from) }

                ReminderMode.FIXED_INTERVAL -> {
                    val stepMs = (reminder.minIntervalMinutes ?: 60).coerceAtLeast(1) * 60_000L
                    val t = if (from.isBefore(windowStart)) {
                        windowStart
                    } else {
                        val elapsed = from.toEpochMilli() - windowStart.toEpochMilli()
                        windowStart.plusMillis((elapsed / stepMs + 1) * stepMs)
                    }
                    if (!t.isAfter(windowEnd)) t else null
                }

                ReminderMode.RANDOM_INTERVAL -> {
                    val minM = (reminder.minIntervalMinutes ?: 30).coerceAtLeast(1)
                    val maxM = (reminder.maxIntervalMinutes ?: minM).coerceAtLeast(minM)
                    val delayMin = random.nextInt(minM, maxM + 1)
                    val base = if (from.isBefore(windowStart)) windowStart else from
                    val t = base.plusSeconds(delayMin * 60L)
                    if (!t.isAfter(windowEnd)) t else null
                }
            }
            if (candidate != null) return candidate
        }
        return null
    }

    fun parseTime(value: String?): LocalTime? = runCatching { LocalTime.parse(value) }.getOrNull()

    fun fixedTimes(reminder: Reminder): List<LocalTime> =
        reminder.fixedTimes.orEmpty().split(",").mapNotNull { parseTime(it.trim()) }.sorted()

    private fun at(date: LocalDate, time: LocalTime, zone: ZoneId): Instant =
        date.atTime(time).atZone(zone).toInstant()
}
