package com.example.ethos.wear

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.time.LocalDate

/** Same paths as WatchProtocol in the phone app. */
object WatchProtocol {
    const val PATH_TODAY = "/ethos/today"
    const val PATH_SET = "/ethos/set"
    const val PATH_REFRESH = "/ethos/refresh"
    const val KEY_JSON = "json"
}

data class WatchHabit(
    val id: Long,
    val title: String,
    val avoid: Boolean,
    /** "COMPLETED", "FAILED" or null, like the phone's LogStatus. */
    val status: String?,
    val streak: Int?,
    val weekDone: Int?,
    val weekTarget: Int?,
) {
    /** Done for habits you build, still avoided for habits you break. */
    val success: Boolean get() = if (avoid) status != STATUS_FAILED else status == STATUS_COMPLETED

    /** Status after a tap: build toggles done/open, break toggles avoided/done anyway. */
    fun toggled(): String? = when {
        avoid -> if (status == STATUS_FAILED) null else STATUS_FAILED
        status == STATUS_COMPLETED -> null
        else -> STATUS_COMPLETED
    }

    companion object {
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
    }
}

data class WatchToday(val date: LocalDate, val items: List<WatchHabit>) {
    val done: Int get() = items.count { it.success }
    val isCurrent: Boolean get() = date == LocalDate.now()
}

/** Reads what the phone sent and sends taps back. The phone stays the only source of truth. */
class PhoneLink(context: Context) {
    private val dataClient = Wearable.getDataClient(context)
    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    suspend fun load(): WatchToday? {
        val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(WatchProtocol.PATH_TODAY).build()
        val buffer = dataClient.getDataItems(uri).await()
        return try {
            buffer.firstOrNull()?.let { parse(DataMapItem.fromDataItem(it).dataMap.getString(WatchProtocol.KEY_JSON)) }
        } finally {
            buffer.release()
        }
    }

    /** False if no phone is connected; nothing is queued then. */
    suspend fun setStatus(habitId: Long, date: LocalDate, status: String?): Boolean {
        val payload = JSONObject()
            .put("id", habitId)
            .put("date", date.toString())
            .put("status", status ?: JSONObject.NULL)
        return send(WatchProtocol.PATH_SET, payload.toString().toByteArray(Charsets.UTF_8))
    }

    suspend fun requestRefresh(): Boolean = send(WatchProtocol.PATH_REFRESH, ByteArray(0))

    private suspend fun send(path: String, data: ByteArray): Boolean = try {
        val nodes = nodeClient.connectedNodes.await()
        var sent = false
        for (node in nodes) {
            messageClient.sendMessage(node.id, path, data).await()
            sent = true
        }
        sent
    } catch (e: Exception) {
        false
    }

    private fun parse(json: String?): WatchToday? {
        if (json == null) return null
        val root = JSONObject(json)
        val arr = root.getJSONArray("items")
        val items = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            WatchHabit(
                id = o.getLong("id"),
                title = o.getString("title"),
                avoid = o.getString("type") == "AVOID",
                status = if (o.isNull("status")) null else o.getString("status"),
                streak = if (o.isNull("streak")) null else o.getInt("streak"),
                weekDone = if (o.isNull("weekDone")) null else o.getInt("weekDone"),
                weekTarget = if (o.isNull("weekTarget")) null else o.getInt("weekTarget"),
            )
        }
        return WatchToday(LocalDate.parse(root.getString("date")), items)
    }
}
