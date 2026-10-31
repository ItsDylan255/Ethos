package com.example.ethos.data

import androidx.room.withTransaction
import com.example.ethos.domain.HabitLogic
import com.example.ethos.domain.ScheduleExceptions
import com.example.ethos.domain.toExceptionIndex
import com.example.ethos.domain.toIndex
import com.example.ethos.reminders.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class TodayEntry(
    val habit: Habit,
    val status: LogStatus?,
    val streak: Int?,
    val weekDone: Int?,
    val weekTarget: Int?,
)

class EthosRepository(
    private val db: EthosDatabase,
    private val scheduler: ReminderScheduler,
) {
    private val habits = db.habitDao()
    private val logs = db.logDao()
    private val reminders = db.reminderDao()
    private val notes = db.noteDao()
    private val exceptions = db.exceptionDao()

    // ---- Habits ----
    fun observeActiveHabits(): Flow<List<HabitWithSchedule>> = habits.observeHabits(false)
    fun observeArchivedHabits(): Flow<List<HabitWithSchedule>> = habits.observeHabits(true)
    fun observeAllHabits(): Flow<List<HabitWithSchedule>> = habits.observeAll()
    fun observeHabit(id: Long): Flow<HabitWithSchedule?> = habits.observeById(id)
    suspend fun getHabit(id: Long): HabitWithSchedule? = habits.getById(id)

    /** [purgeBefore] deletes logs, notes and skips/moves of days before that date (new start date). */
    suspend fun saveHabit(habit: Habit, schedule: HabitSchedule, purgeBefore: LocalDate? = null): Long = db.withTransaction {
        val id = if (habit.id == 0L) {
            habits.insert(habit.copy(sortOrder = habits.nextSortOrder()))
        } else {
            habits.update(habit)
            habit.id
        }
        habits.upsertSchedule(schedule.copy(id = 0, habitId = id))
        if (purgeBefore != null) {
            val key = purgeBefore.toString()
            logs.deleteBefore(id, key)
            notes.deleteBefore(id, key)
            exceptions.deleteBefore(id, key)
        }
        id
    }

    /** Number of logs, notes and skips/moves that lie before [date]. */
    suspend fun countEntriesBefore(habitId: Long, date: LocalDate): Int {
        val key = date.toString()
        return logs.countBefore(habitId, key) + notes.countBefore(habitId, key) + exceptions.countBefore(habitId, key)
    }

    /**
     * Clears the history (logs, notes, skips/moves) but keeps texts and schedule. The habit starts again
     * today; otherwise past days without entry would count as "avoided" again for AVOID habits.
     * Returns the new start date.
     */
    suspend fun resetStats(habitId: Long, today: LocalDate = LocalDate.now()): LocalDate = db.withTransaction {
        logs.deleteForHabit(habitId)
        notes.deleteForHabit(habitId)
        exceptions.deleteForHabit(habitId)
        val schedule = habits.getById(habitId)?.schedule ?: HabitSchedule(habitId = habitId)
        val start = schedule.startDate?.let { LocalDate.ofEpochDay(it) }?.takeIf { it.isAfter(today) } ?: today
        habits.upsertSchedule(schedule.copy(id = 0, startDate = start.toEpochDay()))
        start
    }

    suspend fun setArchived(id: Long, archived: Boolean) = habits.setArchived(id, archived)

    suspend fun swapOrder(a: Habit, b: Habit) = db.withTransaction {
        habits.setSortOrder(a.id, b.sortOrder)
        habits.setSortOrder(b.id, a.sortOrder)
    }

    // ---- Logs ----
    fun observeAllLogs(): Flow<List<HabitLog>> = logs.observeAll()

    suspend fun statusOn(habitId: Long, date: LocalDate): LogStatus? = logs.get(habitId, date.toString())?.status

    /** status == null removes the entry (= NOT_RECORDED for BUILD, implicitly "avoided" for AVOID). */
    suspend fun setLog(habitId: Long, date: LocalDate, status: LogStatus?) {
        val key = date.toString()
        if (status == null) {
            logs.delete(habitId, key)
            return
        }
        val now = System.currentTimeMillis()
        val existing = logs.get(habitId, key)
        logs.upsert(
            HabitLog(
                id = existing?.id ?: 0,
                habitId = habitId,
                localDate = key,
                status = status,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    // ---- Reminders ----
    fun observeReminders(): Flow<List<Reminder>> = reminders.observeAll()
    suspend fun getReminder(id: Long): Reminder? = reminders.getById(id)

    suspend fun saveReminder(reminder: Reminder) {
        val id = if (reminder.id == 0L) {
            reminders.insert(reminder)
        } else {
            reminders.update(reminder)
            reminder.id
        }
        rescheduleReminder(reminder.copy(id = id))
    }

    suspend fun deleteReminder(reminder: Reminder) {
        scheduler.cancel(reminder.id)
        reminders.delete(reminder)
    }

    suspend fun setReminderEnabled(reminder: Reminder, enabled: Boolean) {
        val updated = reminder.copy(isEnabled = enabled)
        reminders.update(updated)
        rescheduleReminder(updated)
    }

    suspend fun pauseReminder(reminder: Reminder, untilMillis: Long?) {
        val updated = reminder.copy(pausedUntil = untilMillis)
        reminders.update(updated)
        rescheduleReminder(updated)
    }

    suspend fun rescheduleReminder(reminder: Reminder) {
        val next = if (reminder.isEnabled) {
            scheduler.schedule(reminder)
        } else {
            scheduler.cancel(reminder.id)
            null
        }
        reminders.setNextTrigger(reminder.id, next)
    }

    /** Counts one shown notification for today, then plans the next one (respecting the daily limit). */
    suspend fun registerFire(reminder: Reminder, today: LocalDate = LocalDate.now()) {
        val key = today.toString()
        val count = if (reminder.firedDate == key) reminder.firedCount else 0
        val updated = reminder.copy(firedDate = key, firedCount = count + 1)
        reminders.update(updated)
        rescheduleReminder(updated)
    }

    suspend fun rescheduleAll() {
        reminders.getEnabled().forEach { rescheduleReminder(it) }
    }

    // ---- Notes ----
    fun observeAllNotes(): Flow<List<HabitNote>> = notes.observeAll()

    /** Blank text removes the note. */
    suspend fun setNote(habitId: Long, date: LocalDate, text: String?) {
        val clean = text?.trim().orEmpty()
        val key = date.toString()
        if (clean.isEmpty()) {
            notes.delete(habitId, key)
            return
        }
        val now = System.currentTimeMillis()
        val existing = notes.get(habitId, key)
        notes.upsert(
            HabitNote(
                id = existing?.id ?: 0,
                habitId = habitId,
                localDate = key,
                text = clean,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    suspend fun saveEntry(habitId: Long, date: LocalDate, status: LogStatus?, note: String?) = db.withTransaction {
        setLog(habitId, date, status)
        setNote(habitId, date, note)
    }

    // ---- Backup ----
    suspend fun snapshot(): BackupSnapshot = BackupSnapshot(
        habits = habits.getAllHabits(),
        schedules = habits.getAllSchedules(),
        logs = logs.getAll(),
        reminders = reminders.getAll(),
        notes = notes.getAll(),
        exceptions = exceptions.getAll(),
    )

    /** Replaces all data with the snapshot. Rows pointing to unknown habits are skipped. */
    suspend fun restore(s: BackupSnapshot) {
        reminders.getAll().forEach { scheduler.cancel(it.id) }
        val ids = s.habits.map { it.id }.toSet()
        db.withTransaction {
            reminders.deleteAll()
            habits.deleteAllHabits() // cascades to schedules, logs and notes
            habits.insertHabits(s.habits)
            habits.insertSchedules(s.schedules.filter { it.habitId in ids })
            logs.insertAll(s.logs.filter { it.habitId in ids })
            notes.insertAll(s.notes.filter { it.habitId in ids })
            exceptions.insertAll(s.exceptions.filter { it.habitId in ids })
            reminders.insertAll(s.reminders.filter { it.habitId == null || it.habitId in ids })
        }
        rescheduleAll()
    }

    // ---- Delete ----
    /** Deletes the habit with its schedule, logs, notes, exceptions and reminders. */
    suspend fun deleteHabit(id: Long) {
        reminders.getAll().filter { it.habitId == id }.forEach { scheduler.cancel(it.id) }
        habits.deleteById(id) // foreign keys cascade
    }

    // ---- Schedule exceptions ----
    fun observeAllExceptions(): Flow<List<HabitException>> = exceptions.observeAll()

    /** Skips [date] and stores the reason as that day's note. */
    suspend fun skipDayWithReason(habitId: Long, date: LocalDate, reason: String) = db.withTransaction {
        skipDay(habitId, date)
        setNote(habitId, date, reason)
    }

    /** Drops [date] from the schedule (e.g. sick). If the day was moved here, the move is turned into a skip. */
    suspend fun skipDay(habitId: Long, date: LocalDate) = db.withTransaction {
        val movedIn = exceptions.getByTarget(habitId, date.toString())
        if (movedIn != null) {
            exceptions.upsert(movedIn.copy(kind = ExceptionKind.SKIPPED, targetDate = null))
        } else {
            exceptions.upsert(
                HabitException(
                    habitId = habitId,
                    originDate = date.toString(),
                    kind = ExceptionKind.SKIPPED,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** Moves [date] to [target]. Moving a day that was itself moved here only changes the target. */
    suspend fun moveDay(habitId: Long, date: LocalDate, target: LocalDate) = db.withTransaction {
        require(target != date) { "Zieltag muss ein anderer Tag sein." }
        val movedIn = exceptions.getByTarget(habitId, date.toString())
        if (movedIn != null) {
            if (movedIn.originDate == target.toString()) {
                exceptions.deleteById(movedIn.id) // moved back to where it came from
            } else {
                exceptions.upsert(movedIn.copy(targetDate = target.toString()))
            }
        } else {
            exceptions.upsert(
                HabitException(
                    habitId = habitId,
                    originDate = date.toString(),
                    kind = ExceptionKind.MOVED,
                    targetDate = target.toString(),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun undoException(habitId: Long, date: LocalDate) = exceptions.deleteAround(habitId, date.toString())

    // ---- Daily notifications ----
    private class PlanData(
        val habits: List<Habit>,
        val scheduleMap: Map<Long, HabitSchedule>,
        val logMap: Map<Long, Map<java.time.LocalDate, LogStatus>>,
        val exceptionMap: Map<Long, ScheduleExceptions>,
    )

    private suspend fun loadPlan() = PlanData(
        habits = habits.getActiveHabits(),
        scheduleMap = habits.getAllSchedules().associateBy { it.habitId },
        logMap = logs.getAll().toIndex(),
        exceptionMap = exceptions.getAll().toExceptionIndex(),
    )

    private fun PlanData.isDue(habit: Habit, today: LocalDate): Boolean {
        val history = logMap[habit.id].orEmpty()
        val start = HabitLogic.startOf(habit, scheduleMap[habit.id], history.keys.minByOrNull { it.toEpochDay() })
        return HabitLogic.isScheduledOn(scheduleMap[habit.id], today, start, exceptionMap[habit.id] ?: ScheduleExceptions.NONE)
    }

    /** Habits planned for [today], in the same order as the Today tab (build first, then avoid). */
    suspend fun habitsDueToday(today: LocalDate = LocalDate.now()): List<Habit> {
        val plan = loadPlan()
        return plan.habits.filter { plan.isDue(it, today) }.sortedBy { if (it.type == HabitType.BUILD) 0 else 1 }
    }

    /** Today's habits with their state, in the order of the Today tab; this is what the watch shows. */
    suspend fun todayEntries(today: LocalDate = LocalDate.now()): List<TodayEntry> {
        val plan = loadPlan()
        val weekStart = HabitLogic.weekStart(today)
        return plan.habits.filter { plan.isDue(it, today) }
            .sortedBy { if (it.type == HabitType.BUILD) 0 else 1 }
            .map { habit ->
                val schedule = plan.scheduleMap[habit.id]
                val history = plan.logMap[habit.id].orEmpty()
                val ex = plan.exceptionMap[habit.id] ?: ScheduleExceptions.NONE
                val weekly = schedule?.recurrenceType == RecurrenceType.TIMES_PER_WEEK
                val start = HabitLogic.startOf(habit, schedule, history.keys.minByOrNull { it.toEpochDay() })
                TodayEntry(
                    habit = habit,
                    status = history[today],
                    streak = HabitLogic.currentStreak(habit, schedule, history, today, ex),
                    weekDone = if (weekly) HabitLogic.weekProgress(history, weekStart) else null,
                    weekTarget = if (weekly) HabitLogic.weekTarget(habit, schedule, history, weekStart, start).total else null,
                )
            }
    }

    /** Habits that were missed once and are due today ("never miss twice"). */
    suspend fun missedOnceToday(today: LocalDate = LocalDate.now()): List<Habit> {
        val plan = loadPlan()
        return plan.habits.filter { habit ->
            HabitLogic.missedOnce(
                habit,
                plan.scheduleMap[habit.id],
                plan.logMap[habit.id].orEmpty(),
                today,
                plan.exceptionMap[habit.id] ?: ScheduleExceptions.NONE,
            )
        }.sortedBy { if (it.type == HabitType.BUILD) 0 else 1 }
    }
}
