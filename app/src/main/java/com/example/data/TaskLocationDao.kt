package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskLocationDao {

    @Query("SELECT * FROM task_locations ORDER BY isCompleted ASC, CASE priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 3 ELSE 4 END ASC, createdAt DESC")
    fun getAllTasksFlow(): Flow<List<TaskLocationEntity>>

    @Query("SELECT * FROM task_locations WHERE isCompleted = 0 ORDER BY CASE priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 3 ELSE 4 END ASC, createdAt DESC")
    fun getActiveTasksFlow(): Flow<List<TaskLocationEntity>>

    @Query("SELECT * FROM task_locations WHERE isCompleted = 0 ORDER BY CASE priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 3 ELSE 4 END ASC, createdAt DESC")
    suspend fun getActiveTasksList(): List<TaskLocationEntity>

    @Query("SELECT * FROM task_locations")
    suspend fun getAllTasksList(): List<TaskLocationEntity>

    @Query("SELECT * FROM task_locations WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskLocationEntity?

    @Query("SELECT * FROM task_locations WHERE syncId = :syncId LIMIT 1")
    suspend fun getTaskBySyncId(syncId: String): TaskLocationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskLocationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskLocationEntity>)

    @Update
    suspend fun updateTask(task: TaskLocationEntity)

    @Delete
    suspend fun deleteTask(task: TaskLocationEntity)

    @Query("DELETE FROM task_locations WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("UPDATE task_locations SET isNotificationTriggered = 1, lastNotifiedAt = :timestamp WHERE id = :id")
    suspend fun markAsNotified(id: Long, timestamp: Long)

    @Query("UPDATE task_locations SET isCompleted = :isCompleted, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setCompleted(id: Long, isCompleted: Boolean, updatedAt: Long)

    @Query("SELECT * FROM deleted_tasks")
    suspend fun getDeletedTasks(): List<DeletedTaskEntity>

    @Query("SELECT * FROM deleted_tasks WHERE syncId = :syncId LIMIT 1")
    suspend fun getDeletion(syncId: String): DeletedTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDeletion(deletion: DeletedTaskEntity)

    @Query("DELETE FROM deleted_tasks WHERE syncId = :syncId")
    suspend fun removeDeletion(syncId: String)

    @Query("DELETE FROM task_locations WHERE isCompleted = 1")
    suspend fun clearCompletedTasks()
}
