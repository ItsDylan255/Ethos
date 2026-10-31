package com.example.ethos.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Checkbox
import androidx.wear.compose.material.CheckboxDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults

/** Same accents as the phone app: indigo for habits you build, orange for habits you break. */
private val BUILD_ACCENT = Color(0xFF8C95F0)
private val AVOID_ACCENT = Color(0xFFF0A04B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { TodayScreen() } }
    }
}

@Composable
private fun TodayScreen(vm: TodayViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val today = state.today?.takeIf { it.isCurrent }

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("Heute") } }
            when {
                today == null -> item {
                    Hint(state.message ?: if (state.loading) "Warte auf das Handy …" else "Öffne Ethos auf dem Handy")
                }
                today.items.isEmpty() -> item { Hint("Für heute ist nichts geplant.") }
                else -> {
                    item {
                        Text(
                            "${today.done} von ${today.items.size} geschafft",
                            style = MaterialTheme.typography.caption1,
                            color = MaterialTheme.colors.onSurfaceVariant,
                        )
                    }
                    items(today.items, key = { it.id }) { habit -> HabitChip(habit, onToggle = { vm.toggle(habit) }) }
                    state.message?.let { item { Hint(it) } }
                }
            }
        }
    }
}

@Composable
private fun HabitChip(habit: WatchHabit, onToggle: () -> Unit) {
    val accent = if (habit.avoid) AVOID_ACCENT else BUILD_ACCENT
    ToggleChip(
        checked = habit.success,
        onCheckedChange = { onToggle() },
        label = { Text(habit.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = { Text(subtitle(habit), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        toggleControl = {
            Checkbox(
                checked = habit.success,
                colors = CheckboxDefaults.colors(checkedBoxColor = accent, checkedCheckmarkColor = accent),
            )
        },
        colors = ToggleChipDefaults.toggleChipColors(
            checkedStartBackgroundColor = accent.copy(alpha = 0.32f),
            checkedEndBackgroundColor = accent.copy(alpha = 0.18f),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun subtitle(h: WatchHabit): String = when {
    h.avoid -> if (h.success) streakOr(h, "Vermieden") else "Doch gemacht"
    h.weekTarget != null -> "${h.weekDone ?: 0} von ${h.weekTarget} diese Woche"
    h.status == WatchHabit.STATUS_FAILED -> "Nicht gemacht"
    h.success -> streakOr(h, "Erledigt")
    else -> streakOr(h, "Offen")
}

private fun streakOr(h: WatchHabit, fallback: String): String =
    if ((h.streak ?: 0) >= 2) "${h.streak} Tage in Folge" else fallback

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.body2,
        color = MaterialTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
