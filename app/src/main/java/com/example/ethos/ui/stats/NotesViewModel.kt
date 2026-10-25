package com.example.ethos.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ethos.data.EthosRepository
import com.example.ethos.data.Habit
import com.example.ethos.data.HabitNote
import com.example.ethos.domain.DayState
import com.example.ethos.domain.HabitLogic
import com.example.ethos.domain.toIndex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class NoteFilter { ALL, MISSED, DONE }

data class NoteItem(val note: HabitNote, val habit: Habit, val date: LocalDate, val state: DayState)
data class NoteGroup(val month: YearMonth, val items: List<NoteItem>)

data class NotesUiState(
    val groups: List<NoteGroup> = emptyList(),
    /** Habits that have at least one note, for the filter chips. */
    val habits: List<Habit> = emptyList(),
    val query: String = "",
    val habitId: Long? = null,
    val filter: NoteFilter = NoteFilter.ALL,
    val total: Int = 0,
    val shown: Int = 0,
)

class NotesViewModel(private val repo: EthosRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val habitFilter = MutableStateFlow<Long?>(null)
    private val statusFilter = MutableStateFlow(NoteFilter.ALL)

    private val data = combine(repo.observeAllNotes(), repo.observeAllHabits(), repo.observeAllLogs()) { n, h, l ->
        Triple(n, h, l)
    }
    private val filters = combine(query, habitFilter, statusFilter) { q, h, s -> Triple(q, h, s) }

    val uiState: StateFlow<NotesUiState> = combine(data, filters) { (notes, habits, logs), (q, habitId, status) ->
        val habitMap = habits.associate { it.habit.id to it.habit }
        val index = logs.toIndex()
        val all = notes.mapNotNull { n ->
            val habit = habitMap[n.habitId] ?: return@mapNotNull null
            val date = LocalDate.parse(n.localDate)
            val state = HabitLogic.dayState(habit.type, index[habit.id]?.get(date))
            NoteItem(n, habit, date, state)
        }
        val needle = q.trim()
        val shown = all.filter { item ->
            (habitId == null || item.habit.id == habitId) &&
                when (status) {
                    NoteFilter.ALL -> true
                    NoteFilter.MISSED -> item.state == DayState.FAILED
                    NoteFilter.DONE -> HabitLogic.isSuccess(item.state)
                } &&
                (needle.isEmpty() ||
                    item.note.text.contains(needle, ignoreCase = true) ||
                    item.habit.title.contains(needle, ignoreCase = true))
        }.sortedWith(compareByDescending<NoteItem> { it.date.toEpochDay() }.thenByDescending { it.note.updatedAt })

        NotesUiState(
            groups = shown.groupBy { YearMonth.from(it.date) }.map { (m, list) -> NoteGroup(m, list) },
            habits = all.map { it.habit }.distinctBy { it.id },
            query = q,
            habitId = habitId,
            filter = status,
            total = all.size,
            shown = shown.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun setQuery(value: String) { query.value = value }
    fun setHabit(id: Long?) { habitFilter.value = id }
    fun setFilter(value: NoteFilter) { statusFilter.value = value }

    fun saveNote(habitId: Long, date: LocalDate, text: String) {
        viewModelScope.launch { repo.setNote(habitId, date, text) }
    }
}
