package com.example.ethos.data

import com.example.ethos.domain.DayState
import com.example.ethos.domain.HabitLogic
import com.example.ethos.domain.ScheduleExceptions
import com.example.ethos.domain.toExceptionIndex
import com.example.ethos.domain.toIndex
import java.time.LocalDate

/**
 * Table export for analysis: one row per habit and day, from the habit's start until today.
 * Semicolon-separated UTF-8 with BOM, so it opens directly in German Excel / LibreOffice
 * (pandas: `read_csv(path, sep=";", encoding="utf-8-sig")`).
 */
object CsvExport {
    private val HEADER = listOf(
        "Datum", "Wochentag", "Habit", "Typ", "Status", "Erfolg", "Verschoben auf", "Notiz", "Archiviert",
    )
    private val WEEKDAYS = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")

    fun encode(s: BackupSnapshot, today: LocalDate = LocalDate.now()): String {
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
                    removed?.kind == ExceptionKind.SKIPPED -> label = "Ausgelassen"
                    removed?.kind == ExceptionKind.MOVED -> {
                        label = "Verschoben"
                        movedTo = removed?.targetDate.orEmpty()
                    }
                    scheduled || status != null -> {
                        val state = HabitLogic.dayState(habit.type, status)
                        // An AVOID day only counts as avoided once it is over.
                        label = if (state == DayState.AVOIDED && date == today) "Offen" else stateLabel(habit.type, state)
                        success = when (label) {
                            "Erledigt", "Vermieden" -> "1"
                            "Nicht gemacht", "Doch gemacht" -> "0"
                            else -> ""
                        }
                    }
                    note != null -> label = "Nicht geplant"
                    else -> return@mapNotNull null
                }
                date to listOf(
                    date.toString(),
                    WEEKDAYS[date.dayOfWeek.value - 1],
                    habit.title,
                    if (habit.type == HabitType.BUILD) "Aufbauen" else "Ablegen",
                    label,
                    success,
                    movedTo,
                    note.orEmpty(),
                    if (habit.isArchived) "ja" else "nein",
                )
            }.toList()
        }.sortedBy { it.first } // stable: habits keep their order within a day

        return buildString {
            append('﻿')
            append(HEADER.joinToString(";") { escape(it) }).append("\r\n")
            rows.forEach { (_, cells) -> append(cells.joinToString(";") { escape(it) }).append("\r\n") }
        }
    }

    private fun stateLabel(type: HabitType, state: DayState) = when (state) {
        DayState.COMPLETED -> "Erledigt"
        DayState.FAILED -> if (type == HabitType.BUILD) "Nicht gemacht" else "Doch gemacht"
        DayState.AVOIDED -> "Vermieden"
        DayState.NOT_RECORDED -> "Keine Angabe"
    }

    private fun escape(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\""
        else value
}
