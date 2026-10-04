package com.example.data

import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class TaskRepository(private val dao: TaskLocationDao) {

    val allTasks: Flow<List<TaskLocationEntity>> = dao.getAllTasksFlow()
    val activeTasks: Flow<List<TaskLocationEntity>> = dao.getActiveTasksFlow()

    suspend fun getActiveTasksList(): List<TaskLocationEntity> = dao.getActiveTasksList()
    suspend fun getAllTasksList(): List<TaskLocationEntity> = dao.getAllTasksList()

    suspend fun addTask(task: TaskLocationEntity): Long {
        val timestamp = System.currentTimeMillis()
        val toSave = task.copy(
            createdAt = if (task.createdAt == 0L) timestamp else task.createdAt,
            updatedAt = timestamp
        )
        return dao.insertTask(toSave)
    }

    suspend fun updateTask(task: TaskLocationEntity) {
        val toSave = task.copy(updatedAt = System.currentTimeMillis())
        dao.updateTask(toSave)
    }

    suspend fun deleteTask(task: TaskLocationEntity) {
        dao.saveDeletion(DeletedTaskEntity(task.syncId, System.currentTimeMillis()))
        dao.deleteTask(task)
    }

    suspend fun deleteTaskById(id: Long) {
        dao.getTaskById(id)?.let { deleteTask(it) }
    }

    suspend fun toggleCompleted(task: TaskLocationEntity) {
        val newStatus = !task.isCompleted
        dao.setCompleted(task.id, newStatus, System.currentTimeMillis())
    }

    suspend fun clearCompletedTasks() {
        dao.getAllTasksList().filter { it.isCompleted }.forEach { deleteTask(it) }
    }

    suspend fun markAsNotified(id: Long) {
        dao.markAsNotified(id, System.currentTimeMillis())
    }

    suspend fun getTaskById(id: Long): TaskLocationEntity? {
        return dao.getTaskById(id)
    }

    /**
     * Merges a list of remote tasks synced from another device (e.g. Tablet or Phone).
     * Conflict resolution: Last-Write-Wins based on updatedAt timestamp.
     * Returns count of updated or added items.
     */
    suspend fun mergeRemoteTasks(remoteTasks: List<TaskLocationEntity>): Int {
        var modifiedCount = 0
        for (remote in remoteTasks) {
            val local = dao.getTaskBySyncId(remote.syncId)
            val deletion = dao.getDeletion(remote.syncId)
            if (deletion != null && deletion.deletedAt >= remote.updatedAt) continue
            if (deletion != null && remote.updatedAt > deletion.deletedAt) dao.removeDeletion(remote.syncId)
            if (local == null) {
                // Insert as new task
                dao.insertTask(remote.copy(id = 0))
                modifiedCount++
            } else if (remote.updatedAt > local.updatedAt) {
                // Update local task with newer remote changes
                val updated = remote.copy(id = local.id)
                dao.updateTask(updated)
                modifiedCount++
            }
        }
        return modifiedCount
    }

    /**
     * Serializes tasks to a JSON string for multi-device sync
     */
    suspend fun exportTasksToJson(): String {
        val tasks = dao.getAllTasksList()
        val array = JSONArray()
        for (t in tasks) {
            val obj = JSONObject()
            obj.put("syncId", t.syncId)
            obj.put("title", t.title)
            obj.put("description", t.description)
            obj.put("priority", t.priority)
            obj.put("placeName", t.placeName)
            obj.put("category", t.category)
            obj.put("latitude", t.latitude)
            obj.put("longitude", t.longitude)
            obj.put("address", t.address)
            obj.put("taskDescription", t.taskDescription)
            obj.put("radiusMeters", t.radiusMeters)
            obj.put("isCompleted", t.isCompleted)
            obj.put("isNotificationTriggered", t.isNotificationTriggered)
            obj.put("createdAt", t.createdAt)
            obj.put("updatedAt", t.updatedAt)
            obj.put("deviceOrigin", t.deviceOrigin)
            obj.put("geminiPlaceInfo", t.geminiPlaceInfo ?: "")
            array.put(obj)
        }
        val deletions = JSONArray()
        dao.getDeletedTasks().forEach { deletion ->
            deletions.put(JSONObject().put("syncId", deletion.syncId).put("deletedAt", deletion.deletedAt))
        }
        return JSONObject().put("version", 2).put("tasks", array).put("deletions", deletions).toString()
    }

    /**
     * Parses JSON string received from another device and merges it
     */
    suspend fun importAndMergeFromJson(jsonString: String): Int {
        val trimmed = jsonString.trim()
        val envelope = if (trimmed.startsWith("{")) JSONObject(trimmed) else null
        val array = envelope?.getJSONArray("tasks") ?: JSONArray(trimmed)
        val deletions = envelope?.optJSONArray("deletions") ?: JSONArray()
        var deletedCount = 0
        for (i in 0 until deletions.length()) {
            val item = deletions.getJSONObject(i)
            val syncId = item.getString("syncId")
            val deletedAt = item.getLong("deletedAt")
            val previous = dao.getDeletion(syncId)
            if (previous == null || deletedAt > previous.deletedAt) {
                dao.saveDeletion(DeletedTaskEntity(syncId, deletedAt))
                val local = dao.getTaskBySyncId(syncId)
                if (local != null && local.updatedAt <= deletedAt) {
                    dao.deleteTask(local)
                    deletedCount++
                }
            }
        }
        val list = mutableListOf<TaskLocationEntity>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                TaskLocationEntity(
                    id = 0,
                    syncId = obj.optString("syncId", java.util.UUID.randomUUID().toString()),
                    title = obj.optString("title", ""),
                    description = obj.optString("description", ""),
                    priority = obj.optString("priority", "MEDIUM"),
                    placeName = obj.optString("placeName", ""),
                    category = obj.optString("category", "Genel"),
                    latitude = obj.optDouble("latitude", 0.0),
                    longitude = obj.optDouble("longitude", 0.0),
                    address = obj.optString("address", ""),
                    taskDescription = obj.optString("taskDescription", ""),
                    radiusMeters = obj.optInt("radiusMeters", 100),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    isNotificationTriggered = obj.optBoolean("isNotificationTriggered", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                    deviceOrigin = obj.optString("deviceOrigin", "Tablet"),
                    geminiPlaceInfo = obj.optString("geminiPlaceInfo").takeIf { it.isNotEmpty() }
                )
            )
        }
        return deletedCount + mergeRemoteTasks(list)
    }
}
