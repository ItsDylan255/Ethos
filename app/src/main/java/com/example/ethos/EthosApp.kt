package com.example.ethos

import android.app.Application
import android.content.Context
import com.example.ethos.data.EthosDatabase
import com.example.ethos.data.EthosRepository
import com.example.ethos.data.SettingsRepository
import com.example.ethos.reminders.DailyNotifier
import com.example.ethos.reminders.DailyScheduler
import com.example.ethos.reminders.ReminderNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.ethos.domain.todayTicker
import com.example.ethos.reminders.ReminderScheduler
import com.example.ethos.watch.WatchSync

/** Manual dependency container; small enough that a DI framework is not needed. */
class AppContainer(context: Context) {
    val scheduler = ReminderScheduler(context)
    val dailyScheduler = DailyScheduler(context)
    val database = EthosDatabase.create(context)
    val repository = EthosRepository(database, scheduler)
    val settings = SettingsRepository(context)
}

class EthosApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderNotifier.ensureChannel(this)
        DailyNotifier.ensureChannel(this)
        // Make sure the daily alarms exist after install/update.
        CoroutineScope(Dispatchers.IO).launch {
            container.dailyScheduler.scheduleAll(container.settings.settings.first())
        }
        // Keep the watch up to date while the app process lives; changes from the watch push on their own.
        CoroutineScope(Dispatchers.IO).launch {
            val repo = container.repository
            combine(repo.observeAllHabits(), repo.observeAllLogs(), repo.observeAllExceptions(), todayTicker()) { _, _, _, _ -> }
                .collectLatest {
                    delay(300) // batch quick successive edits
                    WatchSync.push(this@EthosApp)
                }
        }
    }
}
