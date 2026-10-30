package com.example.ethos.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Transaction
    @Query("SELECT * FROM habits WHERE isArchived = :archived ORDER BY sortOrder, id")
    fun observeHabits(archived: Boolean): Flow<List<HabitWithSchedule>>

    @Transaction
    @Query("SELECT * FROM habits ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<HabitWithSchedule>>

    @Transaction
    @Query("SELECT * FROM habits WHERE id = :id")
    fun observeById(id: Long): Flow<HabitWithSchedule?>

    @Transaction
    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): HabitWithSchedule?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM habits")
    suspend fun nextSortOrder(): Int

    @Insert suspend fun insert(habit: Habit): Long
    @Update suspend fun update(habit: Habit)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedule(schedule: HabitSchedule)

    @Query("UPDATE habits SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE habits SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    // Backup / restore
    @Query("SELECT * FROM habits") suspend fun getAllHabits(): List<Habit>
    @Query("SELECT * FROM habits WHERE isArchived = 0 ORDER BY sortOrder, id") suspend fun getActiveHabits(): List<Habit>
    @Query("SELECT * FROM habit_schedules") suspend fun getAllSchedules(): List<HabitSchedule>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertHabits(list: List<Habit>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSchedules(list: List<HabitSchedule>)
    @Query("DELETE FROM habits") suspend fun deleteAllHabits()

    @Query("DELETE FROM habits WHERE id = :id") suspend fun deleteById(id: Long)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM habit_logs")
    fun observeAll(): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND localDate = :date LIMIT 1")
    suspend fun get(habitId: Long, date: String): HabitLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: HabitLog)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND localDate = :date")
    suspend fun delete(habitId: Long, date: String)

    /** ISO dates compare correctly as text. */
    @Query("SELECT COUNT(*) FROM habit_logs WHERE habitId = :habitId AND localDate < :before")
    suspend fun countBefore(habitId: Long, before: String): Int

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND localDate < :before")
    suspend fun deleteBefore(habitId: Long, before: String)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId")
    suspend fun deleteForHabit(habitId: Long)

    // Backup / restore
    @Query("SELECT * FROM habit_logs") suspend fun getAll(): List<HabitLog>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<HabitLog>)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM habit_notes")
    fun observeAll(): Flow<List<HabitNote>>

    @Query("SELECT * FROM habit_notes WHERE habitId = :habitId AND localDate = :date LIMIT 1")
    suspend fun get(habitId: Long, date: String): HabitNote?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: HabitNote)

    @Query("DELETE FROM habit_notes WHERE habitId = :habitId AND localDate = :date")
    suspend fun delete(habitId: Long, date: String)

    @Query("SELECT COUNT(*) FROM habit_notes WHERE habitId = :habitId AND localDate < :before")
    suspend fun countBefore(habitId: Long, before: String): Int

    @Query("DELETE FROM habit_notes WHERE habitId = :habitId AND localDate < :before")
    suspend fun deleteBefore(habitId: Long, before: String)

    @Query("DELETE FROM habit_notes WHERE habitId = :habitId")
    suspend fun deleteForHabit(habitId: Long)

    // Backup / restore
    @Query("SELECT * FROM habit_notes") suspend fun getAll(): List<HabitNote>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<HabitNote>)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY id")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE isEnabled = 1")
    suspend fun getEnabled(): List<Reminder>

    @Insert suspend fun insert(reminder: Reminder): Long
    @Update suspend fun update(reminder: Reminder)
    @Delete suspend fun delete(reminder: Reminder)

    @Query("UPDATE reminders SET nextTriggerAt = :next WHERE id = :id")
    suspend fun setNextTrigger(id: Long, next: Long?)

    // Backup / restore
    @Query("SELECT * FROM reminders") suspend fun getAll(): List<Reminder>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<Reminder>)
    @Query("DELETE FROM reminders") suspend fun deleteAll()
}

@Dao
interface ExceptionDao {
    @Query("SELECT * FROM habit_exceptions")
    fun observeAll(): Flow<List<HabitException>>

    @Query("SELECT * FROM habit_exceptions WHERE habitId = :habitId AND targetDate = :target LIMIT 1")
    suspend fun getByTarget(habitId: Long, target: String): HabitException?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(exception: HabitException)

    @Query("DELETE FROM habit_exceptions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Removes the exception that drops [date] or the one that moves a habit onto [date]. */
    @Query("DELETE FROM habit_exceptions WHERE habitId = :habitId AND (originDate = :date OR targetDate = :date)")
    suspend fun deleteAround(habitId: Long, date: String)

    /** Skips and moves that start before [before], or move a day to before it. */
    @Query("SELECT COUNT(*) FROM habit_exceptions WHERE habitId = :habitId AND (originDate < :before OR targetDate < :before)")
    suspend fun countBefore(habitId: Long, before: String): Int

    @Query("DELETE FROM habit_exceptions WHERE habitId = :habitId AND (originDate < :before OR targetDate < :before)")
    suspend fun deleteBefore(habitId: Long, before: String)

    @Query("DELETE FROM habit_exceptions WHERE habitId = :habitId")
    suspend fun deleteForHabit(habitId: Long)

    // Backup / restore
    @Query("SELECT * FROM habit_exceptions") suspend fun getAll(): List<HabitException>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<HabitException>)
}
