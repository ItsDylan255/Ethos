package com.example.ethos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Habit::class, HabitSchedule::class, HabitLog::class, Reminder::class],
    version = 2,
    exportSchema = true,
)
abstract class EthosDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun logDao(): LogDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        /**
         * v1 -> v2: removes categories and habit icons, adds the four laws to habits.
         * Existing habits, schedules, logs and reminders are kept; existing habits get empty laws.
         *
         * SQLite cannot drop columns on API < 34, so `habits` has to be rebuilt. With foreign keys on,
         * dropping `habits` would cascade-delete its child rows, so the child tables are copied away
         * and restored afterwards.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE habit_schedules_bak AS SELECT * FROM habit_schedules")
                db.execSQL("CREATE TABLE habit_logs_bak AS SELECT * FROM habit_logs")
                db.execSQL("CREATE TABLE reminders_bak AS SELECT * FROM reminders")
                db.execSQL("DROP TABLE habit_schedules")
                db.execSQL("DROP TABLE habit_logs")
                db.execSQL("DROP TABLE reminders")

                db.execSQL(
                    "CREATE TABLE habits_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "description TEXT, " +
                        "type TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "isArchived INTEGER NOT NULL, " +
                        "sortOrder INTEGER NOT NULL, " +
                        "law1 TEXT NOT NULL, law2 TEXT NOT NULL, law3 TEXT NOT NULL, law4 TEXT NOT NULL)",
                )
                db.execSQL(
                    "INSERT INTO habits_new (id, title, description, type, createdAt, isArchived, sortOrder, law1, law2, law3, law4) " +
                        "SELECT id, title, description, type, createdAt, isArchived, sortOrder, '', '', '', '' FROM habits",
                )
                db.execSQL("DROP TABLE habits")
                db.execSQL("ALTER TABLE habits_new RENAME TO habits")
                db.execSQL("DROP TABLE categories")

                db.execSQL(
                    "CREATE TABLE habit_schedules (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "habitId INTEGER NOT NULL, " +
                        "recurrenceType TEXT NOT NULL, " +
                        "weekdays TEXT NOT NULL, " +
                        "weeklyTarget INTEGER, " +
                        "startDate INTEGER, " +
                        "endDate INTEGER, " +
                        "FOREIGN KEY (habitId) REFERENCES habits (id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE UNIQUE INDEX index_habit_schedules_habitId ON habit_schedules (habitId)")

                db.execSQL(
                    "CREATE TABLE habit_logs (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "habitId INTEGER NOT NULL, " +
                        "localDate TEXT NOT NULL, " +
                        "status TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "FOREIGN KEY (habitId) REFERENCES habits (id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE UNIQUE INDEX index_habit_logs_habitId_localDate ON habit_logs (habitId, localDate)")

                db.execSQL(
                    "CREATE TABLE reminders (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "habitId INTEGER, " +
                        "title TEXT NOT NULL, " +
                        "mode TEXT NOT NULL, " +
                        "isEnabled INTEGER NOT NULL, " +
                        "startTime TEXT NOT NULL, " +
                        "endTime TEXT NOT NULL, " +
                        "weekdays TEXT NOT NULL, " +
                        "minIntervalMinutes INTEGER, " +
                        "maxIntervalMinutes INTEGER, " +
                        "fixedTimes TEXT, " +
                        "snoozeMinutes INTEGER NOT NULL, " +
                        "pausedUntil INTEGER, " +
                        "nextTriggerAt INTEGER, " +
                        "FOREIGN KEY (habitId) REFERENCES habits (id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX index_reminders_habitId ON reminders (habitId)")

                db.execSQL("INSERT INTO habit_schedules SELECT * FROM habit_schedules_bak")
                db.execSQL("INSERT INTO habit_logs SELECT * FROM habit_logs_bak")
                db.execSQL("INSERT INTO reminders SELECT * FROM reminders_bak")
                db.execSQL("DROP TABLE habit_schedules_bak")
                db.execSQL("DROP TABLE habit_logs_bak")
                db.execSQL("DROP TABLE reminders_bak")
            }
        }

        // No fallbackToDestructiveMigration: add explicit Migrations when the schema changes.
        fun create(context: Context): EthosDatabase =
            Room.databaseBuilder(context, EthosDatabase::class.java, "ethos.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
