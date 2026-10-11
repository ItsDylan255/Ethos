package com.example.ethos

import android.app.Application
import android.content.Context
import com.example.ethos.data.EthosDatabase
import com.example.ethos.data.EthosRepository
import com.example.ethos.data.SettingsRepository
import com.example.ethos.reminders.ReminderNotifier
import com.example.ethos.reminders.ReminderScheduler

/** Manual dependency container; small enough that a DI framework is not needed. */
class AppContainer(context: Context) {
    val scheduler = ReminderScheduler(context)
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
    }
}
