package com.example.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.TaskLocationEntity
import com.example.data.TaskRepository
import com.example.gemini.GeminiMapsService
import com.example.gemini.PlaceSearchResult
import com.example.location.LocationMonitorService
import com.example.sync.DeviceSyncManager
import com.example.sync.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val tasks: List<TaskLocationEntity> = emptyList(),
    val filteredTasks: List<TaskLocationEntity> = emptyList(),
    val selectedTask: TaskLocationEntity? = null,
    val selectedCategory: String = "Tümü",
    val searchQuery: String = "",
    val isSearchingPlace: Boolean = false,
    val searchResults: List<PlaceSearchResult> = emptyList(),
    val searchError: String? = null,
    val isLocationServiceRunning: Boolean = false,
    val currentUserLocation: Location? = null,
    val syncState: SyncState = SyncState("Telefon", "Cihaz", "GEO-1234"),
    val activeTab: NavigationTab = NavigationTab.MAP,
    val isAddEditSheetOpen: Boolean = false,
    val isSyncSheetOpen: Boolean = false,
    val taskToEdit: TaskLocationEntity? = null,
    val simulatedArrivalMessage: String? = null
)

enum class NavigationTab {
    MAP,
    TASKS,
    SYNC
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = TaskRepository(database.taskLocationDao())
    private val syncManager = DeviceSyncManager(application, repository)
    private val geminiService = GeminiMapsService()

    private val _selectedCategory = MutableStateFlow("Tümü")
    private val _searchQuery = MutableStateFlow("")
    private val _selectedTask = MutableStateFlow<TaskLocationEntity?>(null)
    private val _isSearchingPlace = MutableStateFlow(false)
    private val _searchResults = MutableStateFlow<List<PlaceSearchResult>>(emptyList())
    private val _searchError = MutableStateFlow<String?>(null)
    private val _activeTab = MutableStateFlow(NavigationTab.MAP)
    private val _isAddEditSheetOpen = MutableStateFlow(false)
    private val _isSyncSheetOpen = MutableStateFlow(false)
    private val _taskToEdit = MutableStateFlow<TaskLocationEntity?>(null)
    private val _simulatedArrivalMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MainUiState> = combine(
        repository.allTasks,
        _selectedCategory,
        _searchQuery,
        _selectedTask,
        _isSearchingPlace,
        _searchResults,
        _searchError,
        LocationMonitorService.isServiceRunning,
        LocationMonitorService.currentLocation,
        syncManager.syncState,
        _activeTab,
        _isAddEditSheetOpen,
        _isSyncSheetOpen,
        _taskToEdit,
        _simulatedArrivalMessage
    ) { params ->
        @Suppress("UNCHECKED_CAST")
        val allTasks = params[0] as List<TaskLocationEntity>
        val category = params[1] as String
        val query = params[2] as String
        val selected = params[3] as? TaskLocationEntity
        val isSearching = params[4] as Boolean
        val results = params[5] as List<PlaceSearchResult>
        val searchErr = params[6] as? String
        val isServiceOn = params[7] as Boolean
        val userLoc = params[8] as? Location
        val sync = params[9] as SyncState
        val tab = params[10] as NavigationTab
        val isAddOpen = params[11] as Boolean
        val isSyncOpen = params[12] as Boolean
        val editingTask = params[13] as? TaskLocationEntity
        val simMsg = params[14] as? String

        val filtered = allTasks.filter { task ->
            val matchesCategory = (category == "Tümü" || task.category == category)
            val matchesQuery = query.isBlank() ||
                    task.placeName.contains(query, ignoreCase = true) ||
                    task.taskDescription.contains(query, ignoreCase = true) ||
                    task.address.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }

        MainUiState(
            tasks = allTasks,
            filteredTasks = filtered,
            selectedTask = selected ?: allTasks.firstOrNull(),
            selectedCategory = category,
            searchQuery = query,
            isSearchingPlace = isSearching,
            searchResults = results,
            searchError = searchErr,
            isLocationServiceRunning = isServiceOn,
            currentUserLocation = userLoc,
            syncState = sync,
            activeTab = tab,
            isAddEditSheetOpen = isAddOpen,
            isSyncSheetOpen = isSyncOpen,
            taskToEdit = editingTask,
            simulatedArrivalMessage = simMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    init {
        seedSampleTasksIfEmpty()
    }

    private fun seedSampleTasksIfEmpty() {
        viewModelScope.launch {
            val existing = repository.getAllTasksList()
            if (existing.isEmpty()) {
                val sample1 = TaskLocationEntity(
                    placeName = "Kadıköy Süreyya Tiyatrosu",
                    category = "Tiyatro / Kültür",
                    latitude = 40.9897,
                    longitude = 29.0289,
                    address = "Bahariye Cad. No:29, Kadıköy / İstanbul",
                    taskDescription = "Gişeden rezerve biletleri teslim al ve etkinlik programını incele",
                    radiusMeters = 100,
                    deviceOrigin = syncManager.syncState.value.deviceType,
                    geminiPlaceInfo = "Google Haritalar: Tarihi opera binası. Temsilden en az 20 dakika önce kapıda olunması tavsiye edilir."
                )

                val sample2 = TaskLocationEntity(
                    placeName = "Emirgan Parkı & Korusu",
                    category = "Park",
                    latitude = 41.1084,
                    longitude = 29.0543,
                    address = "Reşitpaşa, Sarıyer / İstanbul",
                    taskDescription = "Göl etrafında 30 dakikalık yürüyüş yap ve Sarı Köşk'te mola ver",
                    radiusMeters = 200,
                    deviceOrigin = syncManager.syncState.value.deviceType,
                    geminiPlaceInfo = "Google Haritalar: Lale bahçeleri, gölet ve tarihi köşkler barındırır. Giriş serbesttir."
                )

                val sample3 = TaskLocationEntity(
                    placeName = "Maslak Plaza Ofisi",
                    category = "İşyeri",
                    latitude = 41.1118,
                    longitude = 29.0211,
                    address = "Büyükdere Cad. No:140, Maslak / Sarıyer",
                    taskDescription = "Proje teslim evraklarını danışmaya bırak ve yönetimle toplantıya katıl",
                    radiusMeters = 150,
                    deviceOrigin = syncManager.syncState.value.deviceType,
                    geminiPlaceInfo = "Google Haritalar: İş ve finans kuleleri bölgesi. Ziyaretçi otoparkı mevcuttur."
                )

                repository.addTask(sample1)
                repository.addTask(sample2)
                repository.addTask(sample3)
            }
        }
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectTask(task: TaskLocationEntity) {
        _selectedTask.value = task
    }

    fun setActiveTab(tab: NavigationTab) {
        _activeTab.value = tab
    }

    fun openAddTask() {
        _taskToEdit.value = null
        _searchResults.value = emptyList()
        _searchError.value = null
        _isAddEditSheetOpen.value = true
    }

    fun openEditTask(task: TaskLocationEntity) {
        _taskToEdit.value = task
        _searchResults.value = emptyList()
        _searchError.value = null
        _isAddEditSheetOpen.value = true
    }

    fun closeAddEditSheet() {
        _isAddEditSheetOpen.value = false
        _taskToEdit.value = null
    }

    fun openSyncSheet() {
        _isSyncSheetOpen.value = true
    }

    fun closeSyncSheet() {
        _isSyncSheetOpen.value = false
    }

    fun saveTask(
        placeName: String,
        category: String,
        latitude: Double,
        longitude: Double,
        address: String,
        taskDescription: String,
        radiusMeters: Int,
        geminiPlaceInfo: String?
    ) {
        viewModelScope.launch {
            val existing = _taskToEdit.value
            val currentDeviceType = syncManager.syncState.value.deviceType

            if (existing != null) {
                val updated = existing.copy(
                    placeName = placeName,
                    category = category,
                    latitude = latitude,
                    longitude = longitude,
                    address = address,
                    taskDescription = taskDescription,
                    radiusMeters = radiusMeters,
                    geminiPlaceInfo = geminiPlaceInfo ?: existing.geminiPlaceInfo,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateTask(updated)
                _selectedTask.value = updated
            } else {
                val newTask = TaskLocationEntity(
                    placeName = placeName,
                    category = category,
                    latitude = latitude,
                    longitude = longitude,
                    address = address,
                    taskDescription = taskDescription,
                    radiusMeters = radiusMeters,
                    deviceOrigin = currentDeviceType,
                    geminiPlaceInfo = geminiPlaceInfo
                )
                val id = repository.addTask(newTask)
                _selectedTask.value = newTask.copy(id = id)
            }
            closeAddEditSheet()
        }
    }

    fun deleteTask(task: TaskLocationEntity) {
        viewModelScope.launch {
            repository.deleteTask(task)
            if (_selectedTask.value?.id == task.id) {
                _selectedTask.value = null
            }
        }
    }

    fun toggleTaskComplete(task: TaskLocationEntity) {
        viewModelScope.launch {
            repository.toggleCompleted(task)
        }
    }

    /**
     * Searches places via Gemini 2.5 Flash with Google Maps Grounding
     */
    fun searchPlaceWithMapsGrounding(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isSearchingPlace.value = true
            _searchError.value = null
            val userLoc = uiState.value.currentUserLocation
            val result = geminiService.searchPlaceWithMapsGrounding(
                query = query,
                userLatitude = userLoc?.latitude,
                userLongitude = userLoc?.longitude
            )
            result.onSuccess { place ->
                _searchResults.value = listOf(place)
                _isSearchingPlace.value = false
            }.onFailure { err ->
                _searchError.value = "Arama tamamlanamadı: ${err.localizedMessage}"
                _isSearchingPlace.value = false
            }
        }
    }

    /**
     * Immediate simulation of arriving at the task location (for testing in emulator / user check)
     */
    fun simulateArrival(task: TaskLocationEntity) {
        viewModelScope.launch {
            LocationMonitorService.simulateArrival(getApplication(), task.id)
            _simulatedArrivalMessage.value = "${task.placeName} konumuna varış simüle edildi! Bildirim gönderildi."
        }
    }

    fun clearSimulatedMessage() {
        _simulatedArrivalMessage.value = null
    }

    fun toggleLocationService() {
        val app = getApplication<Application>()
        if (LocationMonitorService.isServiceRunning.value) {
            LocationMonitorService.stopService(app)
        } else {
            LocationMonitorService.startService(app)
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            syncManager.performSync()
        }
    }

    fun setDeviceType(type: String) {
        syncManager.setDeviceType(type)
    }

    fun setDeviceName(name: String) {
        syncManager.setDeviceName(name)
    }

    fun setSyncRoomCode(code: String) {
        syncManager.setSyncRoomCode(code)
    }

    fun importSyncJson(json: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = syncManager.importPayload(json)
            res.onSuccess { count ->
                onComplete(true, "$count adet görev başarıyla içe aktarıldı ve eşitlendi.")
            }.onFailure { e ->
                onComplete(false, "Hata: ${e.localizedMessage}")
            }
        }
    }

    suspend fun exportSyncJson(): String {
        return syncManager.exportPayload()
    }
}
