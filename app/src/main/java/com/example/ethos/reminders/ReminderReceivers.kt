package com.example.ethos.reminders

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.ethos.EthosApp
import com.example.ethos.MainActivity
import com.example.ethos.R
import com.example.ethos.data.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ReminderNotifier {
    const val CHANNEL_ID = "reminders"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Erinnerungen", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission") // areNotificationsEnabled() covers permission + settings
    fun show(context: Context, reminder: Reminder, habitTitle: String?) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val open = PendingIntent.getActivity(
            context,
            reminder.id.toInt(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val snooze = PendingIntent.getBroadcast(
            context,
            (reminder.id + ReminderScheduler.NOTIFICATION_SNOOZE_OFFSET).toInt(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderScheduler.ACTION_SNOOZE)
                .putExtra(ReminderScheduler.EXTRA_ID, reminder.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = reminder.title.ifBlank { habitTitle ?: "Erinnerung" }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(habitTitle?.takeIf { it != title })
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Später (${reminder.snoozeMinutes} Min)", snooze)
            .build()
        manager.notify(reminder.id.toInt(), notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L)
        if (id < 0) return
        val app = context.applicationContext as EthosApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(app, intent.action, id)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(app: EthosApp, action: String?, id: Long) {
        val container = app.container
        val reminder = container.repository.getReminder(id) ?: return
        when (action) {
            ReminderScheduler.ACTION_SNOOZE -> {
                NotificationManagerCompat.from(app).cancel(id.toInt())
                container.scheduler.scheduleSnooze(id, reminder.snoozeMinutes)
            }

            ReminderScheduler.ACTION_FIRE, ReminderScheduler.ACTION_FIRE_SNOOZED -> {
                if (!reminder.isEnabled) return
                val habit = reminder.habitId?.let { container.repository.getHabit(it) }?.habit
                // Habit-bound reminders stay silent for archived habits.
                if (reminder.habitId == null || (habit != null && !habit.isArchived)) {
                    ReminderNotifier.show(app, reminder, habit?.title)
                }
                if (action == ReminderScheduler.ACTION_FIRE) {
                    container.repository.rescheduleReminder(reminder)
                }
            }
        }
    }
}

/** Restores alarms after reboot, app update, time or time-zone changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as EthosApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.repository.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
