package com.example.ethos.data

import androidx.room.withTransaction
import com.example.ethos.reminders.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class EthosRepository(
    private val db: EthosDatabase,
    private val scheduler: ReminderScheduler,
) {
    private val habits = db.habitDao()
    private val logs = db.logDao()
    private val categories = db.categoryDao()
    private val reminders = db.reminderDao()

    // ---- Habits ----
    fun observeActiveHabits(): Flow<List<HabitWithSchedule>> = habits.observeHabits(false)
    fun observeArchivedHabits(): Flow<List<HabitWithSchedule>> = habits.observeHabits(true)
    fun observeAllHabits(): Flow<List<HabitWithSchedule>> = habits.observeAll()
    fun observeHabit(id: Long): Flow<HabitWithSchedule?> = habits.observeById(id)
    suspend fun getHabit(id: Long): HabitWithSchedule? = habits.getById(id)

    suspend fun saveHabit(habit: Habit, schedule: HabitSchedule): Long = db.withTransaction {
        val id = if (habit.id == 0L) {
            habits.insert(habit.copy(sortOrder = habits.nextSortOrder()))
        } else {
            habits.update(habit)
            habit.id
        }
        habits.upsertSchedule(schedule.copy(id = 0, habitId = id))
        id
    }

    suspend fun setArchived(id: Long, archived: Boolean) = habits.setArchived(id, archived)

    suspend fun swapOrder(a: Habit, b: Habit) = db.withTransaction {
        habits.setSortOrder(a.id, b.sortOrder)
        habits.setSortOrder(b.id, a.sortOrder)
    }

    // ---- Logs ----
    fun observeAllLogs(): Flow<List<HabitLog>> = logs.observeAll()

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

    // ---- Categories ----
    fun observeCategories(): Flow<List<Category>> = categories.observeAll()

    suspend fun saveCategory(category: Category) {
        if (category.id == 0L) {
            categories.insert(category.copy(sortOrder = categories.nextSortOrder()))
        } else {
            categories.update(category)
        }
    }

    suspend fun deleteCategory(category: Category) = categories.delete(category)

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

    suspend fun rescheduleAll() {
        reminders.getEnabled().forEach { rescheduleReminder(it) }
    }
}
