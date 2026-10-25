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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object DailyNotifier {
    const val CHANNEL_ID = "daily"
    const val REPORT_ID = 2_000_001
    const val MOTIVATION_ID = 2_000_002

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Tägliche Hinweise", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission") // areNotificationsEnabled() covers permission + settings
    fun show(context: Context, id: Int, title: String, summary: String, lines: List<String>) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }
}

/** Fires the daily overview and the "never miss twice" motivation notification. */
class DailyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as EthosApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(app, intent.action)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(app: EthosApp, action: String?) {
        val container = app.container
        val settings = container.settings.settings.first()
        when (action) {
            DailyScheduler.ACTION_REPORT -> if (settings.dailyReportEnabled) {
                val habits = container.repository.habitsDueToday()
                if (habits.isNotEmpty()) {
                    DailyNotifier.show(
                        app,
                        DailyNotifier.REPORT_ID,
                        title = "Heute: ${count(habits.size)}",
                        summary = habits.joinToString(", ") { it.title },
                        lines = habits.map { "• ${it.title}" },
                    )
                }
            }

            DailyScheduler.ACTION_MOTIVATION -> if (settings.motivationEnabled) {
                val missed = container.repository.missedOnceToday()
                if (missed.isNotEmpty()) {
                    DailyNotifier.show(
                        app,
                        DailyNotifier.MOTIVATION_ID,
                        title = "Nie zweimal verpassen",
                        summary = missed.joinToString(", ") { it.title },
                        lines = missed.map { it.title + ": " + it.motivation.ifBlank { "Heute machst du einfach weiter." } },
                    )
                }
            }
        }
        // Plan the next day.
        container.dailyScheduler.scheduleAll(settings)
    }

    private fun count(n: Int) = if (n == 1) "1 Gewohnheit" else "$n Gewohnheiten"
}
