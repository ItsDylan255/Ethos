package com.example.ethos.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ethos.domain.DayState
import com.example.ethos.ui.AppViewModelProvider
import com.example.ethos.ui.WEEKDAY_SHORT
import com.example.ethos.ui.components.EmptyState
import com.example.ethos.ui.components.EthosCard
import com.example.ethos.ui.components.EthosTopBar
import com.example.ethos.ui.components.ProgressBar
import com.example.ethos.ui.displayTitle
import com.example.ethos.ui.theme.typeColor
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
    onOpenHabit: (Long) -> Unit,
    vm: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val items by vm.items.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { EthosTopBar(title = "Statistik", subtitle = "Diese Woche und die letzten 30 Tage") },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState("Noch keine Daten.", Modifier.padding(padding))
        } else {
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { OverviewCard(items) }
                items(items, key = { it.habit.id }) { item ->
                    StatCard(item, onClick = { onOpenHabit(item.habit.id) })
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(items: List<StatItem>) {
    val rates = items.mapNotNull { it.rate30 }
    val average = if (rates.isEmpty()) null else rates.average().roundToInt()
    EthosCard(spacing = 8.dp) {
        Text("Ø ERFOLGSQUOTE · 30 TAGE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(average?.let { "$it %" } ?: "–", style = MaterialTheme.typography.headlineMedium)
        ProgressBar((average ?: 0) / 100f, MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun StatCard(item: StatItem, onClick: () -> Unit) {
    val accent = typeColor(item.habit.type)
    EthosCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.habit.displayTitle(), style = MaterialTheme.typography.titleMedium)
                WeekStrip(item.week, accent)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    item.rate30?.let { "$it %" } ?: "–",
                    style = MaterialTheme.typography.headlineSmall,
                    color = accent,
                )
                val sub = when {
                    item.weekTarget != null -> "${item.weekDone ?: 0}/${item.weekTarget} Woche"
                    (item.streak ?: 0) >= 2 -> "${item.streak} Tage"
                    else -> "30 Tage"
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WeekStrip(week: List<DayState?>, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        week.forEachIndexed { index, state ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    WEEKDAY_SHORT[index].take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Dot(state, accent)
            }
        }
    }
}

@Composable
private fun Dot(state: DayState?, accent: Color) {
    val cs = MaterialTheme.colorScheme
    val (fill, ring) = when (state) {
        DayState.COMPLETED -> accent to accent
        DayState.AVOIDED -> accent.copy(alpha = 0.25f) to Color.Transparent
        DayState.FAILED -> cs.error to cs.error
        DayState.NOT_RECORDED -> Color.Transparent to cs.outline
        null -> Color.Transparent to cs.outlineVariant
    }
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, ring, CircleShape),
    )
}
