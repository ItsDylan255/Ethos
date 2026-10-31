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
import com.example.ethos.data.HabitType
import com.example.ethos.data.LogStatus
import com.example.ethos.data.Reminder
import com.example.ethos.data.localized
import com.example.ethos.watch.WatchSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

object ReminderNotifier {
    const val CHANNEL_ID = "reminders"

    fun ensureChannel(context: Context) {
        val name = context.localized().getString(R.string.tab_reminders)
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** [canComplete]: the reminder belongs to a habit you build that is not done today -> "done" button. */
    @SuppressLint("MissingPermission") // areNotificationsEnabled() covers permission + settings
    fun show(context: Context, reminder: Reminder, habitTitle: String?, canComplete: Boolean = false) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val res = context.localized().resources

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
        val title = reminder.title.ifBlank { habitTitle ?: res.getString(R.string.reminder_default_title) }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(habitTitle?.takeIf { it != title })
            .setContentIntent(open)
            .setAutoCancel(true)
        // Actions are mirrored to the watch, so the done button works there as well.
        if (canComplete) {
            val done = PendingIntent.getBroadcast(
                context,
                (reminder.id + ReminderScheduler.NOTIFICATION_DONE_OFFSET).toInt(),
                Intent(context, ReminderReceiver::class.java)
                    .setAction(ReminderScheduler.ACTION_DONE)
                    .putExtra(ReminderScheduler.EXTRA_ID, reminder.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, res.getString(R.string.status_done), done)
        }
        builder.addAction(0, res.getString(R.string.snooze_action, reminder.snoozeMinutes), snooze)
        manager.notify(reminder.id.toInt(), builder.build())
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

            ReminderScheduler.ACTION_DONE -> {
                NotificationManagerCompat.from(app).cancel(id.toInt())
                val habitId = reminder.habitId ?: return
                container.repository.setLog(habitId, LocalDate.now(), LogStatus.COMPLETED)
                WatchSync.push(app)
            }

            ReminderScheduler.ACTION_FIRE, ReminderScheduler.ACTION_FIRE_SNOOZED -> {
                if (!reminder.isEnabled) return
                val habit = reminder.habitId?.let { container.repository.getHabit(it) }?.habit
                // Habit-bound reminders stay silent for archived habits.
                if (reminder.habitId == null || (habit != null && !habit.isArchived)) {
                    val canComplete = habit != null && habit.type == HabitType.BUILD &&
                        container.repository.statusOn(habit.id, LocalDate.now()) != LogStatus.COMPLETED
                    ReminderNotifier.show(app, reminder, habit?.title, canComplete)
                }
                if (action == ReminderScheduler.ACTION_FIRE) {
                    container.repository.registerFire(reminder)
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
                app.container.dailyScheduler.scheduleAll(app.container.settings.settings.first())
            } finally {
                pending.finish()
            }
        }
    }
}
