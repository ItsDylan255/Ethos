package com.example.ethos.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ethos.data.EthosRepository
import com.example.ethos.data.Reminder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class ReminderItem(val reminder: Reminder, val habitTitle: String?)

class RemindersViewModel(private val repo: EthosRepository) : ViewModel() {

    val items: StateFlow<List<ReminderItem>> = combine(
        repo.observeReminders(),
        repo.observeAllHabits(),
    ) { reminders, habits ->
        val titles = habits.associate { it.habit.id to it.habit.title }
        reminders.map { ReminderItem(it, it.habitId?.let(titles::get)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEnabled(reminder: Reminder, enabled: Boolean) {
        viewModelScope.launch { repo.setReminderEnabled(reminder, enabled) }
    }

    fun pauseUntilTomorrow(reminder: Reminder) {
        val until = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        viewModelScope.launch { repo.pauseReminder(reminder, until) }
    }

    fun resume(reminder: Reminder) {
        viewModelScope.launch { repo.pauseReminder(reminder, null) }
    }

    fun delete(reminder: Reminder) {
        viewModelScope.launch { repo.deleteReminder(reminder) }
    }
}
