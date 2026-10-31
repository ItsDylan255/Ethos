package com.example.ethos.data

import android.content.res.Resources
import com.example.ethos.R
import com.example.ethos.domain.DayState
import com.example.ethos.domain.HabitLogic
import com.example.ethos.domain.ScheduleExceptions
import com.example.ethos.domain.toExceptionIndex
import com.example.ethos.domain.toIndex
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Table export for analysis: one row per habit and day, from the habit's start until today.
 * Texts follow the app language. UTF-8 with BOM; semicolons in German (German Excel), commas in
 * English, so it opens directly in Excel / LibreOffice
 * (pandas: `read_csv(path, sep=None, engine="python", encoding="utf-8-sig")`).
 */
object CsvExport {
    fun encode(s: BackupSnapshot, res: Resources, today: LocalDate = LocalDate.now()): String {
        val separator = res.getString(R.string.csv_separator)
        val locale = res.configuration.locales[0] ?: Locale.getDefault()
        val schedules = s.schedules.associateBy { it.habitId }
        val logIndex = s.logs.toIndex()
        val exIndex = s.exceptions.toExceptionIndex()
        val noteIndex = s.notes.associate { (it.habitId to it.localDate) to it.text }

        val rows = s.habits.sortedWith(compareBy({ it.sortOrder }, { it.id })).flatMap { habit ->
            val schedule = schedules[habit.id]
            val logs = logIndex[habit.id].orEmpty()
            val ex = exIndex[habit.id] ?: ScheduleExceptions.NONE
            val start = HabitLogic.startOf(habit, schedule, logs.keys.minByOrNull { it.toEpochDay() })
            generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.mapNotNull { date ->
                val note = noteIndex[habit.id to date.toString()]
                val removed = ex.byOrigin[date]
                val scheduled = HabitLogic.isScheduledOn(schedule, date, start, ex)
                val status = logs[date]
                val label: String
                var success = ""
                var movedTo = ""
                when {
                    removed?.kind == ExceptionKind.SKIPPED -> label = res.getString(R.string.csv_skipped)
                    removed?.kind == ExceptionKind.MOVED -> {
                        label = res.getString(R.string.csv_moved)
                        movedTo = removed?.targetDate.orEmpty()
                    }
                    scheduled || status != null -> {
                        val state = HabitLogic.dayState(habit.type, status)
                        // An AVOID day only counts as avoided once it is over.
                        val open = state == DayState.AVOIDED && date == today
                        label = if (open) res.getString(R.string.csv_open) else stateLabel(res, habit.type, state)
                        success = when {
                            open -> ""
                            state == DayState.COMPLETED || state == DayState.AVOIDED -> "1"
                            state == DayState.FAILED -> "0"
                            else -> ""
                        }
                    }
                    note != null -> label = res.getString(R.string.csv_not_planned)
                    else -> return@mapNotNull null
                }
                date to listOf(
                    date.toString(),
                    date.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
                    habit.title,
                    res.getString(if (habit.type == HabitType.BUILD) R.string.csv_type_build else R.string.csv_type_avoid),
                    label,
                    success,
                    movedTo,
                    note.orEmpty(),
                    res.getString(if (habit.isArchived) R.string.csv_yes else R.string.csv_no),
                )
            }.toList()
        }.sortedBy { it.first } // stable: habits keep their order within a day

        return buildString {
            append('﻿')
            append(res.getStringArray(R.array.csv_header).joinToString(separator) { escape(it, separator) }).append("\r\n")
            rows.forEach { (_, cells) -> append(cells.joinToString(separator) { escape(it, separator) }).append("\r\n") }
        }
    }

    private fun stateLabel(res: Resources, type: HabitType, state: DayState) = res.getString(
        when (state) {
            DayState.COMPLETED -> R.string.status_done
            DayState.FAILED -> if (type == HabitType.BUILD) R.string.status_not_done else R.string.status_done_anyway
            DayState.AVOIDED -> R.string.status_avoided
            DayState.NOT_RECORDED -> R.string.status_no_entry
        },
    )

    private fun escape(value: String, separator: String): String =
        if (value.contains(separator) || value.any { it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\""
        else value
}
