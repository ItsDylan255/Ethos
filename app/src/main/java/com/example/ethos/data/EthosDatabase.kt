package com.example.ethos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Category::class, Habit::class, HabitSchedule::class, HabitLog::class, Reminder::class],
    version = 1,
    exportSchema = true,
)
abstract class EthosDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun habitDao(): HabitDao
    abstract fun logDao(): LogDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        // No fallbackToDestructiveMigration: add explicit Migrations when the schema changes.
        fun create(context: Context): EthosDatabase =
            Room.databaseBuilder(context, EthosDatabase::class.java, "ethos.db").build()
    }
}
