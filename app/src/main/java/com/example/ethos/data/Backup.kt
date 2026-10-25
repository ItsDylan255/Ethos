package com.example.ethos.data

import org.json.JSONArray
import org.json.JSONObject

data class BackupSnapshot(
    val habits: List<Habit>,
    val schedules: List<HabitSchedule>,
    val logs: List<HabitLog>,
    val reminders: List<Reminder>,
    val notes: List<HabitNote>,
    val exceptions: List<HabitException> = emptyList(),
)

/** Plain JSON backup. Ids are kept so that all relations survive a restore. */
object BackupJson {
    private const val APP = "ethos"
    private const val FORMAT_VERSION = 1

    fun encode(s: BackupSnapshot): String {
        val root = JSONObject()
        root.put("app", APP)
        root.put("formatVersion", FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("habits", array(s.habits) { h ->
            JSONObject()
                .put("id", h.id).put("title", h.title).put("description", h.description ?: JSONObject.NULL)
                .put("type", h.type.name).put("createdAt", h.createdAt).put("isArchived", h.isArchived)
                .put("sortOrder", h.sortOrder)
                .put("law1", h.law1).put("law2", h.law2).put("law3", h.law3).put("law4", h.law4)
                .put("motivation", h.motivation)
        })
        root.put("schedules", array(s.schedules) { x ->
            JSONObject()
                .put("id", x.id).put("habitId", x.habitId).put("recurrenceType", x.recurrenceType.name)
                .put("weekdays", x.weekdays).put("weeklyTarget", x.weeklyTarget ?: JSONObject.NULL)
                .put("startDate", x.startDate ?: JSONObject.NULL).put("endDate", x.endDate ?: JSONObject.NULL)
                .put("catchUp", x.catchUp)
        })
        root.put("logs", array(s.logs) { l ->
            JSONObject()
                .put("id", l.id).put("habitId", l.habitId).put("localDate", l.localDate)
                .put("status", l.status.name).put("createdAt", l.createdAt).put("updatedAt", l.updatedAt)
        })
        root.put("notes", array(s.notes) { n ->
            JSONObject()
                .put("id", n.id).put("habitId", n.habitId).put("localDate", n.localDate)
                .put("text", n.text).put("createdAt", n.createdAt).put("updatedAt", n.updatedAt)
        })
        root.put("exceptions", array(s.exceptions) { e ->
            JSONObject()
                .put("id", e.id).put("habitId", e.habitId).put("originDate", e.originDate)
                .put("kind", e.kind.name).put("targetDate", e.targetDate ?: JSONObject.NULL)
                .put("createdAt", e.createdAt)
        })
        root.put("reminders", array(s.reminders) { r ->
            JSONObject()
                .put("id", r.id).put("habitId", r.habitId ?: JSONObject.NULL).put("title", r.title)
                .put("mode", r.mode.name).put("isEnabled", r.isEnabled)
                .put("startTime", r.startTime).put("endTime", r.endTime).put("weekdays", r.weekdays)
                .put("minIntervalMinutes", r.minIntervalMinutes ?: JSONObject.NULL)
                .put("maxIntervalMinutes", r.maxIntervalMinutes ?: JSONObject.NULL)
                .put("fixedTimes", r.fixedTimes ?: JSONObject.NULL)
                .put("snoozeMinutes", r.snoozeMinutes)
                .put("pausedUntil", r.pausedUntil ?: JSONObject.NULL)
                .put("maxPerDay", r.maxPerDay ?: JSONObject.NULL)
        })
        return root.toString(2)
    }

    /** Throws IllegalArgumentException / JSONException for files that are not a valid backup. */
    fun decode(text: String): BackupSnapshot {
        val root = JSONObject(text)
        require(root.optString("app") == APP) { "Keine Ethos-Sicherung." }
        require(root.optInt("formatVersion", 0) <= FORMAT_VERSION) { "Sicherung stammt aus einer neueren Version." }
        return BackupSnapshot(
            habits = root.getJSONArray("habits").objects().map { o ->
                Habit(
                    id = o.getLong("id"), title = o.getString("title"), description = o.str("description"),
                    type = HabitType.valueOf(o.getString("type")), createdAt = o.getLong("createdAt"),
                    isArchived = o.getBoolean("isArchived"), sortOrder = o.getInt("sortOrder"),
                    law1 = o.optString("law1"), law2 = o.optString("law2"),
                    law3 = o.optString("law3"), law4 = o.optString("law4"),
                    motivation = o.optString("motivation"),
                )
            },
            schedules = root.getJSONArray("schedules").objects().map { o ->
                HabitSchedule(
                    id = o.getLong("id"), habitId = o.getLong("habitId"),
                    recurrenceType = RecurrenceType.valueOf(o.getString("recurrenceType")),
                    weekdays = o.getString("weekdays"), weeklyTarget = o.int("weeklyTarget"),
                    startDate = o.long("startDate"), endDate = o.long("endDate"),
                    catchUp = o.optBoolean("catchUp", false),
                )
            },
            logs = root.getJSONArray("logs").objects().map { o ->
                HabitLog(
                    id = o.getLong("id"), habitId = o.getLong("habitId"), localDate = o.getString("localDate"),
                    status = LogStatus.valueOf(o.getString("status")),
                    createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"),
                )
            },
            reminders = root.getJSONArray("reminders").objects().map { o ->
                Reminder(
                    id = o.getLong("id"), habitId = o.long("habitId"), title = o.getString("title"),
                    mode = ReminderMode.valueOf(o.getString("mode")), isEnabled = o.getBoolean("isEnabled"),
                    startTime = o.getString("startTime"), endTime = o.getString("endTime"),
                    weekdays = o.getString("weekdays"),
                    minIntervalMinutes = o.int("minIntervalMinutes"), maxIntervalMinutes = o.int("maxIntervalMinutes"),
                    fixedTimes = o.str("fixedTimes"), snoozeMinutes = o.getInt("snoozeMinutes"),
                    pausedUntil = o.long("pausedUntil"), nextTriggerAt = null,
                    maxPerDay = o.int("maxPerDay"),
                )
            },
            exceptions = root.optJSONArray("exceptions")?.objects().orEmpty().map { o ->
                HabitException(
                    id = o.getLong("id"), habitId = o.getLong("habitId"), originDate = o.getString("originDate"),
                    kind = ExceptionKind.valueOf(o.getString("kind")), targetDate = o.str("targetDate"),
                    createdAt = o.getLong("createdAt"),
                )
            },
            notes = root.optJSONArray("notes")?.objects().orEmpty().map { o ->
                HabitNote(
                    id = o.getLong("id"), habitId = o.getLong("habitId"), localDate = o.getString("localDate"),
                    text = o.getString("text"), createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"),
                )
            },
        )
    }

    private fun <T> array(items: List<T>, map: (T) -> JSONObject): JSONArray =
        JSONArray().also { arr -> items.forEach { arr.put(map(it)) } }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.long(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.int(key: String): Int? = if (isNull(key)) null else getInt(key)
}
