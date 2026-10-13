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
}
