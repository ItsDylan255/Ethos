package com.example.ethos.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ethos.EthosApp
import com.example.ethos.ui.habits.HabitEditViewModel
import com.example.ethos.ui.habits.HabitsViewModel
import com.example.ethos.ui.reminders.ReminderEditViewModel
import com.example.ethos.ui.reminders.RemindersViewModel
import com.example.ethos.ui.stats.HabitDetailViewModel
import com.example.ethos.ui.stats.StatsViewModel
import com.example.ethos.ui.today.TodayViewModel

private fun CreationExtras.repository() = (this[APPLICATION_KEY] as EthosApp).container.repository

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { TodayViewModel(repository()) }
        initializer { HabitsViewModel(repository()) }
        initializer { HabitEditViewModel(repository(), createSavedStateHandle()) }
        initializer { StatsViewModel(repository()) }
        initializer { HabitDetailViewModel(repository(), createSavedStateHandle()) }
        initializer { RemindersViewModel(repository()) }
        initializer { ReminderEditViewModel(repository(), createSavedStateHandle()) }
    }
}
