package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "task_locations")
data class TaskLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val syncId: String = UUID.randomUUID().toString(),
    val placeName: String,
    val category: String = "Genel", // İşyeri, Park, Tiyatro / Kültür, Market, Kafe, Diğer
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val taskDescription: String,
    val radiusMeters: Int = 100,
    val isCompleted: Boolean = false,
    val isNotificationTriggered: Boolean = false,
    val lastNotifiedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deviceOrigin: String = "Cihaz",
    val geminiPlaceInfo: String? = null
)
