package com.example.ethos.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.ethos.data.Reminder
import com.example.ethos.domain.ReminderPlanner
import java.time.Instant
import java.time.ZoneId

/**
 * Schedules one pending alarm per reminder.
 * Uses setAndAllowWhileIdle (battery friendly, no SCHEDULE_EXACT_ALARM permission needed);
 * delivery can be a few minutes late in Doze. Upgrade to exact alarms only if that proves too coarse.
 */
class ReminderScheduler(private val context: Context) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    /** Returns the scheduled trigger time (epoch millis) or null when nothing can be scheduled. */
    fun schedule(reminder: Reminder, now: Instant = Instant.now()): Long? {
        val next = ReminderPlanner.nextTrigger(reminder, now, ZoneId.systemDefault())
        if (next == null) {
            cancel(reminder.id)
            return null
        }
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.toEpochMilli(),
            pendingIntent(reminder.id, ACTION_FIRE, REGULAR_OFFSET),
        )
        return next.toEpochMilli()
    }

    fun scheduleSnooze(reminderId: Long, minutes: Int) {
        val at = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            at,
            pendingIntent(reminderId, ACTION_FIRE_SNOOZED, SNOOZE_OFFSET),
        )
    }

    fun cancel(reminderId: Long) {
        alarmManager.cancel(pendingIntent(reminderId, ACTION_FIRE, REGULAR_OFFSET))
        alarmManager.cancel(pendingIntent(reminderId, ACTION_FIRE_SNOOZED, SNOOZE_OFFSET))
    }

    private fun pendingIntent(id: Long, action: String, offset: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            context,
            (id + offset).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
        const val ACTION_FIRE = "com.example.ethos.REMINDER_FIRE"
        const val ACTION_FIRE_SNOOZED = "com.example.ethos.REMINDER_FIRE_SNOOZED"
        const val ACTION_SNOOZE = "com.example.ethos.REMINDER_SNOOZE"
        const val REGULAR_OFFSET = 0L
        const val SNOOZE_OFFSET = 1_000_000L
        const val NOTIFICATION_SNOOZE_OFFSET = 2_000_000L
        const val ACTION_DONE = "com.example.ethos.REMINDER_DONE"
        const val NOTIFICATION_DONE_OFFSET = 3_000_000L
    }
}
