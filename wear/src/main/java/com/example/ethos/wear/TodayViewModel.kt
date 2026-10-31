package com.example.ethos.wear

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WatchUiState(
    val today: WatchToday? = null,
    val loading: Boolean = true,
    val message: String? = null,
)

class TodayViewModel(app: Application) : AndroidViewModel(app), DataClient.OnDataChangedListener {
    private val link = PhoneLink(app)
    private val dataClient = Wearable.getDataClient(app)

    private val _state = MutableStateFlow(WatchUiState())
    val state: StateFlow<WatchUiState> = _state.asStateFlow()

    init {
        dataClient.addListener(this)
        viewModelScope.launch {
            reload()
            // Ask the phone for fresh data (new day, changes made while the watch was away).
            if (!link.requestRefresh()) {
                _state.update { it.copy(loading = false, message = app.getString(R.string.phone_not_connected)) }
            }
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val today = runCatching { link.load() }.getOrNull()
        _state.update {
            it.copy(today = today, loading = today == null || !today.isCurrent, message = null)
        }
    }

    fun toggle(habit: WatchHabit) {
        val today = _state.value.today ?: return
        val newStatus = habit.toggled()
        val updated = habit.copy(status = newStatus)
        // Show the change at once; the phone confirms by sending fresh data.
        _state.update { s -> s.copy(today = today.copy(items = today.items.map { if (it.id == habit.id) updated else it })) }
        if (!habit.avoid && updated.success) vibrate(getApplication())
        viewModelScope.launch {
            if (!link.setStatus(habit.id, today.date, newStatus)) {
                _state.update { s -> s.copy(today = today, message = getApplication<Application>().getString(R.string.phone_not_connected_unsaved)) }
            }
        }
    }

    override fun onCleared() {
        dataClient.removeListener(this)
    }
}

/** Short, strong buzz when a habit is ticked off. */
private fun vibrate(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
}
