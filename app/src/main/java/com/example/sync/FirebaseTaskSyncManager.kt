package com.example.sync

import com.example.data.TaskLocationEntity
import com.example.data.TaskRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class CloudSyncResult(val uploaded: Int, val downloaded: Int)

class FirebaseTaskSyncManager(
    private val repository: TaskRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun syncNow(): Result<CloudSyncResult> = runCatching {
        val user = auth.currentUser ?: error("Google hesabıyla giriş yapmanız gerekiyor.")
        val tasksRef = firestore.collection("users").document(user.uid).collection("tasks")
        val localTasks = repository.getAllTasksList()

        // First safe migration: upload existing Room tasks without deleting anything.
        localTasks.forEach { task ->
            tasksRef.document(task.syncId).set(task.toCloudMap()).await()
        }

        val snapshot = tasksRef.get().await()
        val remoteTasks = snapshot.documents.mapNotNull { doc -> doc.toTask() }
        val downloaded = repository.mergeRemoteTasks(remoteTasks, preferIncoming = true)
        CloudSyncResult(uploaded = localTasks.size, downloaded = downloaded)
    }
}

private fun TaskLocationEntity.toCloudMap(): Map<String, Any?> = mapOf(
    "syncId" to syncId,
    "title" to title,
    "description" to description,
    "priority" to priority,
    "placeName" to placeName,
    "category" to category,
    "latitude" to latitude,
    "longitude" to longitude,
    "address" to address,
    "taskDescription" to taskDescription,
    "radiusMeters" to radiusMeters,
    "isCompleted" to isCompleted,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "deviceOrigin" to deviceOrigin,
    "geminiPlaceInfo" to geminiPlaceInfo
)

private fun com.google.firebase.firestore.DocumentSnapshot.toTask(): TaskLocationEntity? {
    val sid = getString("syncId") ?: id.takeIf { it.isNotBlank() } ?: return null
    return TaskLocationEntity(
        id = 0,
        syncId = sid,
        title = getString("title").orEmpty(),
        description = getString("description").orEmpty(),
        priority = getString("priority") ?: "MEDIUM",
        placeName = getString("placeName").orEmpty(),
        category = getString("category") ?: "Genel",
        latitude = getDouble("latitude") ?: 0.0,
        longitude = getDouble("longitude") ?: 0.0,
        address = getString("address").orEmpty(),
        taskDescription = getString("taskDescription").orEmpty(),
        radiusMeters = getLong("radiusMeters")?.toInt() ?: 100,
        isCompleted = getBoolean("isCompleted") ?: false,
        // Notification state is intentionally local to each device.
        isNotificationTriggered = false,
        lastNotifiedAt = null,
        createdAt = getLong("createdAt") ?: System.currentTimeMillis(),
        updatedAt = getLong("updatedAt") ?: System.currentTimeMillis(),
        deviceOrigin = getString("deviceOrigin") ?: "Bulut",
        geminiPlaceInfo = getString("geminiPlaceInfo")
    )
}
