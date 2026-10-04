package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_tasks")
data class DeletedTaskEntity(
    @PrimaryKey val syncId: String,
    val deletedAt: Long
)
