package com.example.ethos.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ethos.EthosApp
import com.example.ethos.R
import com.example.ethos.data.AppLanguage
import com.example.ethos.data.AppLocale
import com.example.ethos.data.AppSettings
import com.example.ethos.data.BackupJson
import com.example.ethos.data.CsvExport
import com.example.ethos.data.ThemeMode
import com.example.ethos.data.localized
import com.example.ethos.reminders.DailyNotifier
import com.example.ethos.reminders.ReminderNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val app: EthosApp) : ViewModel() {
    private val container = app.container

    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { container.settings.setThemeMode(mode) } }
    fun setDynamicColor(enabled: Boolean) { viewModelScope.launch { container.settings.setDynamicColor(enabled) } }
    fun setMissedHint(enabled: Boolean) { viewModelScope.launch { container.settings.setMissedHint(enabled) } }

    /** The screen recreates itself afterwards; notification channels get their names in the new language. */
    fun setLanguage(language: AppLanguage) {
        AppLocale.set(app, language)
        ReminderNotifier.ensureChannel(app)
        DailyNotifier.ensureChannel(app)
    }

    private fun text(id: Int, vararg args: Any): String = app.localized().getString(id, *args)

    // Daily notifications: store the setting, then re-plan the alarms with the new values.
    fun setDailyReport(enabled: Boolean) = updateDaily { container.settings.setDailyReportEnabled(enabled) }
    fun setDailyReportTime(minutes: Int) = updateDaily { container.settings.setDailyReportMinutes(minutes) }
    fun setMotivation(enabled: Boolean) = updateDaily { container.settings.setMotivationEnabled(enabled) }
    fun setMotivationTime(minutes: Int) = updateDaily { container.settings.setMotivationMinutes(minutes) }

    private fun updateDaily(change: suspend () -> Unit) {
        viewModelScope.launch {
            change()
            container.dailyScheduler.scheduleAll(container.settings.settings.first())
        }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            _message.value = try {
                withContext(Dispatchers.IO) {
                    val json = BackupJson.encode(container.repository.snapshot())
                    val stream = app.contentResolver.openOutputStream(uri, "wt") ?: error(text(R.string.err_not_writable))
                    stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                text(R.string.msg_backup_saved)
            } catch (e: Exception) {
                text(R.string.msg_export_failed, e.message.orEmpty())
            }
        }
    }

    /** Table of all days for analysis (Excel, LibreOffice, pandas …); not importable. */
    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            _message.value = try {
                withContext(Dispatchers.IO) {
                    val csv = CsvExport.encode(container.repository.snapshot(), app.localized().resources)
                    val stream = app.contentResolver.openOutputStream(uri, "wt") ?: error(text(R.string.err_not_writable))
                    stream.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                }
                text(R.string.msg_table_saved)
            } catch (e: Exception) {
                text(R.string.msg_export_failed, e.message.orEmpty())
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            _message.value = try {
                val snapshot = withContext(Dispatchers.IO) {
                    val stream = app.contentResolver.openInputStream(uri) ?: error(text(R.string.err_not_readable))
                    BackupJson.decode(stream.use { it.readBytes().toString(Charsets.UTF_8) })
                }
                container.repository.restore(snapshot)
                text(R.string.msg_restored, snapshot.habits.size)
            } catch (e: Exception) {
                text(R.string.msg_import_failed, e.message.orEmpty())
            }
        }
    }
}
