package com.example.ethos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Habit::class, HabitSchedule::class, HabitLog::class, Reminder::class, HabitNote::class,
        HabitException::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class EthosDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun logDao(): LogDao
    abstract fun reminderDao(): ReminderDao
    abstract fun noteDao(): NoteDao
    abstract fun exceptionDao(): ExceptionDao

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

        /** v2 -> v3: adds the per-day notes table. Purely additive, existing data is untouched. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE habit_notes (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "habitId INTEGER NOT NULL, " +
                        "localDate TEXT NOT NULL, " +
                        "text TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "FOREIGN KEY (habitId) REFERENCES habits (id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE UNIQUE INDEX index_habit_notes_habitId_localDate ON habit_notes (habitId, localDate)")
            }
        }

        /** v3 -> v4: schedule exceptions (skip / move) and the catch-up flag. Purely additive. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habit_schedules ADD COLUMN catchUp INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE habit_exceptions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "habitId INTEGER NOT NULL, " +
                        "originDate TEXT NOT NULL, " +
                        "kind TEXT NOT NULL, " +
                        "targetDate TEXT, " +
                        "createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY (habitId) REFERENCES habits (id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE UNIQUE INDEX index_habit_exceptions_habitId_originDate ON habit_exceptions (habitId, originDate)")
            }
        }

        /** v4 -> v5: daily limit for interval reminders. Purely additive. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN maxPerDay INTEGER")
                db.execSQL("ALTER TABLE reminders ADD COLUMN firedDate TEXT")
                db.execSQL("ALTER TABLE reminders ADD COLUMN firedCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v5 -> v6: personal motivation per habit. Purely additive. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN motivation TEXT NOT NULL DEFAULT ''")
            }
        }

        // No fallbackToDestructiveMigration: add explicit Migrations when the schema changes.
        fun create(context: Context): EthosDatabase =
            Room.databaseBuilder(context, EthosDatabase::class.java, "ethos.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
    }
}
