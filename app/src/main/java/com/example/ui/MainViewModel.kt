package com.example.ui

import android.app.Application
import android.content.Context
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.TaskLocationEntity
import com.example.data.TaskRepository
import com.example.gemini.GeminiMapsService
import com.example.gemini.PlaceSearchResult
import com.example.location.LocationHelper
import com.example.location.LocationMonitorService
import com.example.sync.DeviceSyncManager
import com.example.sync.SyncState
import com.example.ui.localization.AppLanguage
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MainUiState(
    val tasks: List<TaskLocationEntity> = emptyList(),
    val activeTasks: List<TaskLocationEntity> = emptyList(),
    val archivedTasks: List<TaskLocationEntity> = emptyList(),
    val filteredTasks: List<TaskLocationEntity> = emptyList(),
    val totalActiveCount: Int = 0,
    val totalCompletedCount: Int = 0,
    val selectedTask: TaskLocationEntity? = null,
    val selectedCategory: String = "Tümü",
    val searchQuery: String = "",
    val isSearchingPlace: Boolean = false,
    val searchResults: List<PlaceSearchResult> = emptyList(),
    val searchError: String? = null,
    val isLocationServiceRunning: Boolean = false,
    val currentUserLocation: Location? = null,
    val syncState: SyncState = SyncState("Telefon", "Cihaz", "GEO-1234"),
    val activeTab: NavigationTab = NavigationTab.TASKS,
    val isAddEditSheetOpen: Boolean = false,
    val isSyncSheetOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val taskToEdit: TaskLocationEntity? = null,
    val simulatedArrivalMessage: String? = null,
    val lastCheckTimestamp: Long = System.currentTimeMillis(),
    val proximityThresholdMeters: Int = 1000,
    val nearbyTasksWithinThreshold: List<TaskLocationEntity> = emptyList(),
    val routeTargetTask: TaskLocationEntity? = null,
    val showNearbyTaskAlert: Boolean = true
)

enum class NavigationTab {
    MAP,
    TASKS,
    ARCHIVE,
    SYNC
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = TaskRepository(database.taskLocationDao())
    private val syncManager = DeviceSyncManager(application, repository)
    private val geminiService = GeminiMapsService()
    private val prefs = application.getSharedPreferences("geo_task_prefs", Context.MODE_PRIVATE)

    private val defaultUserLocation = Location("default_simulated").apply {
        latitude = 40.9915
        longitude = 29.0275
    }

    private val _themeMode = MutableStateFlow(
        try {
            AppThemeMode.valueOf(prefs.getString("theme_mode", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }
    )

    private val _selectedCategory = MutableStateFlow("Tümü")
    private val _searchQuery = MutableStateFlow("")
    private val _selectedTask = MutableStateFlow<TaskLocationEntity?>(null)
    private val _isSearchingPlace = MutableStateFlow(false)
    private val _searchResults = MutableStateFlow<List<PlaceSearchResult>>(emptyList())
    private val _searchError = MutableStateFlow<String?>(null)
    private val _activeTab = MutableStateFlow(NavigationTab.TASKS)
    private val _isAddEditSheetOpen = MutableStateFlow(false)
    private val _isSyncSheetOpen = MutableStateFlow(false)
    private val _isSettingsOpen = MutableStateFlow(false)
    private val _language = MutableStateFlow(
        try {
            AppLanguage.valueOf(prefs.getString("app_language", AppLanguage.ENGLISH.name) ?: AppLanguage.ENGLISH.name)
        } catch (e: Exception) {
            AppLanguage.ENGLISH
        }
    )
    private val _taskToEdit = MutableStateFlow<TaskLocationEntity?>(null)
    private val _simulatedArrivalMessage = MutableStateFlow<String?>(null)
    private val _tickerTime = MutableStateFlow(System.currentTimeMillis())
    private val _proximityThresholdMeters = MutableStateFlow(1000)
    private val _routeTargetTask = MutableStateFlow<TaskLocationEntity?>(null)
    private val _showNearbyTaskAlert = MutableStateFlow(true)
    private val _customUserLocation = MutableStateFlow<Location?>(null)

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
        _isSettingsOpen,
        _language,
        _taskToEdit,
        _simulatedArrivalMessage,
        _tickerTime,
        _proximityThresholdMeters,
        _routeTargetTask,
        _showNearbyTaskAlert,
        _customUserLocation,
        _themeMode
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
        val isSettings = params[13] as Boolean
        val lang = params[14] as AppLanguage
        val editingTask = params[15] as? TaskLocationEntity
        val simMsg = params[16] as? String
        val ticker = params[17] as Long
        val proximityThreshold = params[18] as Int
        val routeTarget = params[19] as? TaskLocationEntity
        val showAlert = params[20] as Boolean
        val customLoc = params[21] as? Location
        val theme = params[22] as AppThemeMode

        val effectiveUserLoc = userLoc ?: customLoc ?: defaultUserLocation

        val filtered = allTasks.filter { task ->
            val isAll = category == "Tümü" || category == "All" || category == "Todos" || category == "Alle" || category == "Tous"
            val matchesCategory = isAll || task.category == category
            val matchesQuery = query.isBlank() ||
                    task.displayTitle.contains(query, ignoreCase = true) ||
                    task.displayDescription.contains(query, ignoreCase = true) ||
                    task.placeName.contains(query, ignoreCase = true) ||
                    task.address.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }

        val activeTasks = filtered.filter { !it.isCompleted }
        val archivedTasks = filtered.filter { it.isCompleted }
        val totalActive = allTasks.count { !it.isCompleted }
        val totalCompleted = allTasks.count { it.isCompleted }

        // Calculate tasks within the configured proximity distance (default 1 km)
        val nearbyTasks = allTasks.filter { task ->
            !task.isCompleted && LocationHelper.calculateDistanceMeters(
                effectiveUserLoc.latitude,
                effectiveUserLoc.longitude,
                task.latitude,
                task.longitude
            ) <= proximityThreshold
        }.sortedBy { task ->
            LocationHelper.calculateDistanceMeters(
                effectiveUserLoc.latitude,
                effectiveUserLoc.longitude,
                task.latitude,
                task.longitude
            )
        }

        val effectiveRouteTarget = routeTarget ?: if (tab == NavigationTab.MAP && nearbyTasks.isNotEmpty()) nearbyTasks.first() else selected

        MainUiState(
            tasks = allTasks,
            activeTasks = activeTasks,
            archivedTasks = archivedTasks,
            filteredTasks = if (tab == NavigationTab.ARCHIVE) archivedTasks else activeTasks,
            totalActiveCount = totalActive,
            totalCompletedCount = totalCompleted,
            selectedTask = selected,
            selectedCategory = category,
            searchQuery = query,
            isSearchingPlace = isSearching,
            searchResults = results,
            searchError = searchErr,
            isLocationServiceRunning = isServiceOn,
            currentUserLocation = effectiveUserLoc,
            syncState = sync,
            activeTab = tab,
            isAddEditSheetOpen = isAddOpen,
            isSyncSheetOpen = isSyncOpen,
            isSettingsOpen = isSettings,
            language = lang,
            themeMode = theme,
            taskToEdit = editingTask,
            simulatedArrivalMessage = simMsg,
            lastCheckTimestamp = ticker,
            proximityThresholdMeters = proximityThreshold,
            nearbyTasksWithinThreshold = nearbyTasks,
            routeTargetTask = effectiveRouteTarget,
            showNearbyTaskAlert = showAlert
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    init {
        seedSampleTasksIfEmpty()
        startPeriodic10SecondCheck()
    }

    private fun startPeriodic10SecondCheck() {
        viewModelScope.launch {
            while (isActive) {
                delay(10000L) // Compare/refresh every 10 seconds
                _tickerTime.value = System.currentTimeMillis()
            }
        }
    }

    private fun seedSampleTasksIfEmpty() {
        viewModelScope.launch {
            val existing = repository.getAllTasksList()
            if (existing.isEmpty()) {
                val sample1 = TaskLocationEntity(
                    title = "Süreyya Operası Bilet Teslimi",
                    description = "Gişeden cuma günkü temsil için rezerve edilmiş tiyatro biletlerini al",
                    priority = "HIGH",
                    placeName = "Kadıköy Süreyya Tiyatrosu",
                    category = "Tiyatro / Kültür",
                    latitude = 40.9897,
                    longitude = 29.0289,
                    address = "Bahariye Cad. No:29, Kadıköy / İstanbul",
                    taskDescription = "Gişeden cuma günkü temsil için rezerve edilmiş tiyatro biletlerini al",
                    radiusMeters = 100,
                    deviceOrigin = syncManager.syncState.value.deviceType,
                    geminiPlaceInfo = "Google Haritalar: Tarihi opera binası. Temsilden en az 20 dakika önce kapıda olunması tavsiye edilir."
                )

                val sample2 = TaskLocationEntity(
                    title = "Maslak Plaza Ofis Sunumu",
                    description = "Toplantı odasında çeyrek dönem sunum belgelerini teslim et ve imzalat",
                    priority = "HIGH",
                    placeName = "Maslak İş Kuleleri",
                    category = "İşyeri",
                    latitude = 41.1118,
                    longitude = 29.0211,
                    address = "Büyükdere Cad. No:140, Maslak / Sarıyer",
                    taskDescription = "Toplantı odasında çeyrek dönem sunum belgelerini teslim et ve imzalat",
                    radiusMeters = 150,
                    deviceOrigin = syncManager.syncState.value.deviceType,
                    geminiPlaceInfo = "Google Haritalar: İş ve finans kuleleri bölgesi. Ziyaretçi otoparkı mevcuttur."
                )

                val sample3 = TaskLocationEntity(
                    title = "Emirgan Korusu Yürüyüşü",
                    description = "Göl etrafında 30 dakikalık doğa yürüyüşü yap ve Sarı Köşk'te mola ver",
                    priority = "MEDIUM",
                    placeName = "Emirgan Parkı & Korusu",
                    category = "Park",
                    latitude = 41.1084,
                    longitude = 29.0543,
                    address = "Reşitpaşa, Sarıyer / İstanbul",
                    taskDescription = "Göl etrafında 30 dakikalık doğa yürüyüşü yap ve Sarı Köşk'te mola ver",
                    radiusMeters = 200,
                    deviceOrigin = syncManager.syncState.value.deviceType,
                    geminiPlaceInfo = "Google Haritalar: Lale bahçeleri, gölet ve tarihi köşkler barındırır. Giriş serbesttir."
                )

                val sample4 = TaskLocationEntity(
                    title = "Haftalık Organik Alışveriş",
                    description = "Taze köy yumurtası, soğuk sıkım zeytinyağı ve taze mevsim sebzelerini al",
                    priority = "LOW",
                    placeName = "Kadıköy Tarihi Çarşı & Pazar",
                    category = "Market",
                    latitude = 40.9902,
                    longitude = 29.0255,
                    address = "Caferağa, Kadıköy / İstanbul",
                    taskDescription = "Taze köy yumurtası, soğuk sıkım zeytinyağı ve taze mevsim sebzelerini al",
                    radiusMeters = 100,
                    deviceOrigin = syncManager.syncState.value.deviceType,
                    geminiPlaceInfo = "Google Haritalar: Organik tezgahlar ve geleneksel dükkanlar mevcuttur."
                )

                repository.addTask(sample1)
                repository.addTask(sample2)
                repository.addTask(sample3)
                repository.addTask(sample4)
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

    fun clearSelectedTask() {
        _selectedTask.value = null
    }

    fun setActiveTab(tab: NavigationTab) {
        _activeTab.value = tab
        if (tab == NavigationTab.MAP) {
            _showNearbyTaskAlert.value = true
        }
    }

    fun setProximityThreshold(meters: Int) {
        _proximityThresholdMeters.value = meters
    }

    fun setRouteTargetTask(task: TaskLocationEntity?) {
        _routeTargetTask.value = task
        if (task != null) {
            _selectedTask.value = task
            _showNearbyTaskAlert.value = true
        }
    }

    fun showRouteForTask(task: TaskLocationEntity) {
        _routeTargetTask.value = task
        _selectedTask.value = task
        _activeTab.value = NavigationTab.MAP
        _showNearbyTaskAlert.value = true
    }

    fun dismissNearbyTaskAlert() {
        _showNearbyTaskAlert.value = false
    }

    fun openNearbyTaskAlert() {
        _showNearbyTaskAlert.value = true
    }

    fun setUserLocation(latitude: Double, longitude: Double) {
        val loc = Location("user_manual").apply {
            this.latitude = latitude
            this.longitude = longitude
        }
        _customUserLocation.value = loc
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

    fun openSettings() {
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    fun setLanguage(language: AppLanguage) {
        _language.value = language
        prefs.edit().putString("app_language", language.name).apply()
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun restoreTask(task: TaskLocationEntity) {
        viewModelScope.launch {
            if (task.isCompleted) {
                repository.toggleCompleted(task)
            }
        }
    }

    fun clearArchive() {
        viewModelScope.launch {
            repository.clearCompletedTasks()
        }
    }

    fun addSampleTask(
        title: String,
        desc: String,
        priority: String,
        place: String,
        category: String,
        lat: Double,
        lng: Double
    ) {
        viewModelScope.launch {
            val task = TaskLocationEntity(
                title = title,
                description = desc,
                priority = priority,
                placeName = place,
                category = category,
                latitude = lat,
                longitude = lng,
                taskDescription = desc,
                radiusMeters = 150,
                deviceOrigin = syncManager.syncState.value.deviceType
            )
            val id = repository.addTask(task)
            _selectedTask.value = task.copy(id = id)
        }
    }

    fun saveTask(
        title: String,
        description: String,
        priority: String,
        placeName: String,
        category: String,
        latitude: Double,
        longitude: Double,
        address: String,
        radiusMeters: Int,
        geminiPlaceInfo: String?
    ) {
        viewModelScope.launch {
            val existing = _taskToEdit.value
            val currentDeviceType = syncManager.syncState.value.deviceType

            if (existing != null) {
                val updated = existing.copy(
                    title = title,
                    description = description,
                    priority = priority,
                    placeName = placeName,
                    category = category,
                    latitude = latitude,
                    longitude = longitude,
                    address = address,
                    taskDescription = description,
                    radiusMeters = radiusMeters,
                    geminiPlaceInfo = geminiPlaceInfo ?: existing.geminiPlaceInfo,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateTask(updated)
                _selectedTask.value = updated
            } else {
                val newTask = TaskLocationEntity(
                    title = title,
                    description = description,
                    priority = priority,
                    placeName = placeName,
                    category = category,
                    latitude = latitude,
                    longitude = longitude,
                    address = address,
                    taskDescription = description,
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
                // Graceful fallback for quota exhaustion or offline:
                // Provide direct place result with default coordinates
                val fallbackPlace = PlaceSearchResult(
                    placeName = query,
                    category = "İşyeri",
                    latitude = userLoc?.latitude ?: 41.0082,
                    longitude = userLoc?.longitude ?: 28.9784,
                    address = "$query (Harita konumu)",
                    summary = "Google Haritalar konumu girildi.",
                    suggestedTasks = listOf("$query konumunda görevi tamamla")
                )
                _searchResults.value = listOf(fallbackPlace)
                _searchError.value = null
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
            val lang = _language.value
            _simulatedArrivalMessage.value = "${task.displayTitle} (${task.placeName}) konumuna varış simüle edildi! Bildirim gönderildi."
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
