package com.example.sync

import com.example.data.DeletedTaskEntity
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
        val userRef = firestore.collection("users").document(user.uid)
        val tasksRef = userRef.collection("tasks")
        val deletionsRef = userRef.collection("deletedTasks")

        var localTasks = repository.getAllTasksList()
        val localDeletions = repository.getDeletedTasks().associateBy { it.syncId }

        // Read cloud state first so Last-Write-Wins also applies to deletions.
        val taskSnapshot = tasksRef.get().await()
        val deletionSnapshot = deletionsRef.get().await()
        val remoteTasks = taskSnapshot.documents.mapNotNull { doc -> doc.toTask() }
        val remoteDeletions = deletionSnapshot.documents.mapNotNull { doc ->
            val syncId = doc.getString("syncId") ?: doc.id.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DeletedTaskEntity(syncId, doc.getLong("deletedAt") ?: return@mapNotNull null)
        }

        var downloaded = 0

        // Apply newer cloud tombstones locally. A newer local edit resurrects the task instead.
        remoteDeletions.forEach { deletion ->
            val localTask = localTasks.firstOrNull { it.syncId == deletion.syncId }
            val localDeletion = repository.getDeletion(deletion.syncId)
            if (localDeletion == null || deletion.deletedAt > localDeletion.deletedAt) {
                repository.saveDeletion(deletion)
            }
            if (localTask != null && deletion.deletedAt >= localTask.updatedAt) {
                repository.deleteTaskForSync(localTask)
                downloaded++
            }
        }

        localTasks = repository.getAllTasksList()
        val localBySyncId = localTasks.associateBy { it.syncId }
        val remoteDeletionBySyncId = remoteDeletions.associateBy { it.syncId }

        val tasksToDownload = remoteTasks.filter { remote ->
            val local = localBySyncId[remote.syncId]
            val deletion = localDeletions[remote.syncId]
            val remoteDeletion = remoteDeletionBySyncId[remote.syncId]
            val newestDeletion = listOfNotNull(deletion, remoteDeletion).maxByOrNull { it.deletedAt }
            (newestDeletion == null || remote.updatedAt > newestDeletion.deletedAt) &&
                (local == null || remote.updatedAt > local.updatedAt)
        }
        downloaded += repository.mergeRemoteTasks(tasksToDownload, preferIncoming = true)

        val currentLocalTasks = repository.getAllTasksList()
        val remoteBySyncId = remoteTasks.associateBy { it.syncId }
        val allLocalDeletions = repository.getDeletedTasks()

        // Upload local tombstones and remove stale cloud task documents.
        allLocalDeletions.forEach { deletion ->
            val remoteTask = remoteBySyncId[deletion.syncId]
            if (remoteTask == null || deletion.deletedAt >= remoteTask.updatedAt) {
                deletionsRef.document(deletion.syncId).set(
                    mapOf("syncId" to deletion.syncId, "deletedAt" to deletion.deletedAt)
                ).await()
                tasksRef.document(deletion.syncId).delete().await()
            }
        }

        val tasksToUpload = currentLocalTasks.filter { local ->
            val remote = remoteBySyncId[local.syncId]
            val remoteDeletion = remoteDeletionBySyncId[local.syncId]
            (remoteDeletion == null || local.updatedAt > remoteDeletion.deletedAt) &&
                (remote == null || local.updatedAt > remote.updatedAt)
        }
        tasksToUpload.forEach { task ->
            tasksRef.document(task.syncId).set(task.toCloudMap()).await()
            // A newer edit intentionally resurrects a previously deleted task.
            deletionsRef.document(task.syncId).delete().await()
            repository.removeDeletion(task.syncId)
        }

        CloudSyncResult(uploaded = tasksToUpload.size, downloaded = downloaded)
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
