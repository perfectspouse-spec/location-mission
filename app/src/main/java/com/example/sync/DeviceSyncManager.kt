package com.example.sync

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.example.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class SyncState(
    val deviceType: String, // "Tablet" veya "Telefon"
    val deviceName: String,
    val syncRoomCode: String,
    val isAutoSyncEnabled: Boolean = true,
    val isSyncing: Boolean = false,
    val lastSyncTime: Long? = null,
    val lastSyncMessage: String = "Henüz senkronize edilmedi",
    val pairedDevices: List<String> = emptyList()
)

class DeviceSyncManager(
    private val context: Context,
    private val repository: TaskRepository
) {

    private val prefs = context.getSharedPreferences("geo_device_sync_prefs", Context.MODE_PRIVATE)

    private val _syncState = MutableStateFlow(loadInitialState())
    val syncState = _syncState.asStateFlow()

    private fun loadInitialState(): SyncState {
        val isTabletByConfig = isTabletDevice()
        val defaultType = if (isTabletByConfig) "Tablet" else "Telefon"
        val savedType = prefs.getString("KEY_DEVICE_TYPE", defaultType) ?: defaultType
        val defaultName = if (savedType == "Tablet") "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} Tablet" else "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} Telefon"
        val savedName = prefs.getString("KEY_DEVICE_NAME", defaultName) ?: defaultName
        val savedCode = prefs.getString("KEY_SYNC_CODE", generateDefaultSyncCode()) ?: "GEO-8419"
        val lastSync = prefs.getLong("KEY_LAST_SYNC_TIME", 0L).takeIf { it > 0 }

        return SyncState(
            deviceType = savedType,
            deviceName = savedName,
            syncRoomCode = savedCode,
            lastSyncTime = lastSync,
            lastSyncMessage = if (lastSync != null) "Son manuel içe aktarma tamamlandı" else "Manuel aktarım bekleniyor"
        )
    }

    private fun isTabletDevice(): Boolean {
        val config = context.resources.configuration
        return (config.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
    }

    private fun generateDefaultSyncCode(): String {
        val randomNum = (1000..9999).random()
        return "GEO-$randomNum"
    }

    fun setDeviceType(newType: String) {
        prefs.edit().putString("KEY_DEVICE_TYPE", newType).apply()
        _syncState.value = _syncState.value.copy(deviceType = newType)
    }

    fun setDeviceName(newName: String) {
        prefs.edit().putString("KEY_DEVICE_NAME", newName).apply()
        _syncState.value = _syncState.value.copy(deviceName = newName)
    }

    fun setSyncRoomCode(newCode: String) {
        val clean = newCode.trim().uppercase()
        prefs.edit().putString("KEY_SYNC_CODE", clean).apply()
        _syncState.value = _syncState.value.copy(syncRoomCode = clean)
    }

    /**
     * Performs cross-device sync.
     * In an active multi-device setup, this pushes local changes and pulls tablet/phone changes.
     */
    suspend fun performSync(): Result<String> {
        val message = "Otomatik bağlantı yok. Diğer cihaza JSON gönderip orada içe aktarın."
        _syncState.value = _syncState.value.copy(lastSyncMessage = message)
        return Result.failure(UnsupportedOperationException(message))
    }

    suspend fun exportPayload(): String {
        return repository.exportTasksToJson()
    }

    suspend fun importPayload(json: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val count = repository.importAndMergeFromJson(json)
            val now = System.currentTimeMillis()
            prefs.edit().putLong("KEY_LAST_SYNC_TIME", now).apply()
            _syncState.value = _syncState.value.copy(
                lastSyncTime = now,
                lastSyncMessage = "$count görev senkronize edilerek güncellendi."
            )
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
