package com.example.ethos.watch

import android.content.Context
import android.util.Log
import com.example.ethos.EthosApp
import com.example.ethos.data.HabitType
import com.example.ethos.data.LogStatus
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Phone <-> watch protocol over the Wearable Data Layer. Keep in sync with the wear module.
 * Phone -> watch: data item [PATH_TODAY] with today's habits as JSON.
 * Watch -> phone: message [PATH_SET] (set today's status) and [PATH_REFRESH] (push again).
 */
object WatchProtocol {
    const val PATH_TODAY = "/ethos/today"
    const val PATH_SET = "/ethos/set"
    const val PATH_REFRESH = "/ethos/refresh"
    const val KEY_JSON = "json"
    const val KEY_STAMP = "stamp"
}

object WatchSync {
    private const val TAG = "WatchSync"

    /** Sends today's habits to the watch. Does nothing without Play Services or watch. */
    suspend fun push(context: Context) {
        val app = context.applicationContext as EthosApp
        val today = LocalDate.now()
        val items = JSONArray()
        app.container.repository.todayEntries(today).forEach { e ->
            items.put(
                JSONObject()
                    .put("id", e.habit.id)
                    .put("title", e.habit.title)
                    .put("type", e.habit.type.name)
                    .put("status", e.status?.name ?: JSONObject.NULL)
                    .put("streak", e.streak ?: JSONObject.NULL)
                    .put("weekDone", e.weekDone ?: JSONObject.NULL)
                    .put("weekTarget", e.weekTarget ?: JSONObject.NULL),
            )
        }
        val json = JSONObject().put("date", today.toString()).put("items", items).toString()
        try {
            val request = PutDataMapRequest.create(WatchProtocol.PATH_TODAY).apply {
                dataMap.putString(WatchProtocol.KEY_JSON, json)
                // A new stamp makes the watch get an update even if nothing changed (e.g. after a refresh).
                dataMap.putLong(WatchProtocol.KEY_STAMP, System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
        } catch (e: Exception) {
            Log.d(TAG, "No watch sync: ${e.message}")
        }
    }
}

/** Receives taps from the watch, even when the phone app is not running. */
class WatchListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        val app = applicationContext as EthosApp
        // Runs on a background thread of the listener service; blocking is fine here.
        runBlocking {
            when (event.path) {
                WatchProtocol.PATH_SET -> {
                    val o = JSONObject(String(event.data, Charsets.UTF_8))
                    val date = LocalDate.parse(o.getString("date"))
                    // Only today can be changed from the watch.
                    if (date == LocalDate.now()) {
                        val status = if (o.isNull("status")) null else LogStatus.valueOf(o.getString("status"))
                        val habit = app.container.repository.getHabit(o.getLong("id"))?.habit
                        if (habit != null && (status != LogStatus.COMPLETED || habit.type == HabitType.BUILD)) {
                            app.container.repository.setLog(habit.id, date, status)
                        }
                    }
                    WatchSync.push(app)
                }
                WatchProtocol.PATH_REFRESH -> WatchSync.push(app)
            }
        }
    }
}
