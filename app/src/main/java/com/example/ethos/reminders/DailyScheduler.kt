package com.example.ethos.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.ethos.data.AppSettings
import com.example.ethos.domain.nextDailyOccurrence
import java.time.Instant

/**
 * One repeating-by-rescheduling alarm each for the daily overview and the motivation notification.
 * Re-run [scheduleAll] after settings change, after reboot and after every delivery.
 */
class DailyScheduler(private val context: Context) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    fun scheduleAll(settings: AppSettings, now: Instant = Instant.now()) {
        schedule(ACTION_REPORT, REPORT_CODE, settings.dailyReportEnabled, settings.dailyReportMinutes, now)
        schedule(ACTION_MOTIVATION, MOTIVATION_CODE, settings.motivationEnabled, settings.motivationMinutes, now)
    }

    private fun schedule(action: String, code: Int, enabled: Boolean, minutes: Int, now: Instant) {
        val pending = pendingIntent(action, code)
        if (!enabled) {
            alarmManager.cancel(pending)
            return
        }
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextDailyOccurrence(minutes, now).toEpochMilli(),
            pending,
        )
    }

    private fun pendingIntent(action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        code,
        Intent(context, DailyReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_REPORT = "com.example.ethos.DAILY_REPORT"
        const val ACTION_MOTIVATION = "com.example.ethos.DAILY_MOTIVATION"
        private const val REPORT_CODE = 3_000_001
        private const val MOTIVATION_CODE = 3_000_002
    }
}
