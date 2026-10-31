package com.example.ethos.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ethos.R
import com.example.ethos.ui.components.HairlineDivider
import com.example.ethos.ui.habits.HabitEditScreen
import com.example.ethos.ui.habits.HabitsScreen
import com.example.ethos.ui.reminders.ReminderEditScreen
import com.example.ethos.ui.reminders.RemindersScreen
import com.example.ethos.ui.settings.SettingsScreen
import com.example.ethos.ui.stats.HabitDetailScreen
import com.example.ethos.ui.stats.StatsScreen
import com.example.ethos.ui.today.TodayScreen

object Routes {
    const val TODAY = "today"
    const val HABITS = "habits"
    const val STATS = "stats"
    const val REMINDERS = "reminders"
    const val HABIT_EDIT = "habit_edit/{id}"
    const val HABIT_DETAIL = "habit_detail/{id}"
    const val REMINDER_EDIT = "reminder_edit/{id}"
    const val SETTINGS = "settings"

    fun habitEdit(id: Long = -1L) = "habit_edit/$id"
    fun habitDetail(id: Long) = "habit_detail/$id"
    fun reminderEdit(id: Long = -1L) = "reminder_edit/$id"
}

private data class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, R.string.tab_today, Icons.Default.CheckCircle),
    Tab(Routes.HABITS, R.string.tab_habits, Icons.AutoMirrored.Filled.List),
    Tab(Routes.STATS, R.string.tab_stats, Icons.Default.DateRange),
    Tab(Routes.REMINDERS, R.string.tab_reminders, Icons.Default.Notifications),
)

private val idArg = listOf(navArgument("id") { type = NavType.LongType })

@Composable
fun EthosRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (tabs.any { it.route == current }) {
                Column {
                    HairlineDivider()
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp,
                    ) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = current == tab.route,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = stringResource(tab.label)) },
                                label = { Text(stringResource(tab.label)) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.TODAY,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.TODAY) {
                TodayScreen(
                    onAddHabit = { nav.navigate(Routes.habitEdit()) },
                    onOpenHabit = { nav.navigate(Routes.habitDetail(it)) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.HABITS) {
                HabitsScreen(
                    onAddHabit = { nav.navigate(Routes.habitEdit()) },
                    onEditHabit = { nav.navigate(Routes.habitEdit(it)) },
                )
            }
            composable(Routes.STATS) {
                StatsScreen(onOpenHabit = { nav.navigate(Routes.habitDetail(it)) })
            }
            composable(Routes.REMINDERS) {
                RemindersScreen(
                    onAddReminder = { nav.navigate(Routes.reminderEdit()) },
                    onEditReminder = { nav.navigate(Routes.reminderEdit(it)) },
                )
            }
            composable(Routes.HABIT_EDIT, arguments = idArg) {
                HabitEditScreen(
                    onBack = { nav.popBackStack() },
                    onDeleted = {
                        nav.popBackStack()
                        // Came from the habit's detail page? That page is gone too.
                        if (nav.currentDestination?.route == Routes.HABIT_DETAIL) nav.popBackStack()
                    },
                )
            }
            composable(Routes.HABIT_DETAIL, arguments = idArg) {
                HabitDetailScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.habitEdit(it)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.REMINDER_EDIT, arguments = idArg) {
                ReminderEditScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
