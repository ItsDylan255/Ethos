package com.example.ethos.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Next moment at [minutesOfDay] (minutes after midnight, local time) that is strictly after [now]. */
fun nextDailyOccurrence(minutesOfDay: Int, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant {
    val time = LocalTime.ofSecondOfDay(minutesOfDay.coerceIn(0, 24 * 60 - 1) * 60L)
    val today = now.atZone(zone).toLocalDate()
    val candidate = today.atTime(time).atZone(zone).toInstant()
    return if (candidate.isAfter(now)) candidate else today.plusDays(1).atTime(time).atZone(zone).toInstant()
}
