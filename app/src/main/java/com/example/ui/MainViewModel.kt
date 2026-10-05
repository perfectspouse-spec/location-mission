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
import com.example.sync.FirebaseTaskSyncManager
import com.example.sync.SyncState
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.LocalizationManager
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
import java.util.Locale

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
    val showNearbyTaskAlert: Boolean = true,
    val homeSearchedPlace: PlaceSearchResult? = null,
    val initialNewTaskPlace: PlaceSearchResult? = null
)

enum class NavigationTab {
    TASKS,
    ARCHIVE,
    SYNC
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = TaskRepository(database.taskLocationDao())
    private val syncManager = DeviceSyncManager(application, repository)
    private val cloudSyncManager = FirebaseTaskSyncManager(repository)
    private val geminiService = GeminiMapsService(application)
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
    private val _proximityThresholdMeters = MutableStateFlow(prefs.getInt("proximity_threshold_meters", 1000))
    private val _routeTargetTask = MutableStateFlow<TaskLocationEntity?>(null)
    private val _showNearbyTaskAlert = MutableStateFlow(true)
    private val _customUserLocation = MutableStateFlow<Location?>(null)
    private val _homeSearchedPlace = MutableStateFlow<PlaceSearchResult?>(null)
    private val _initialNewTaskPlace = MutableStateFlow<PlaceSearchResult?>(null)

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
        _themeMode,
        _homeSearchedPlace,
        _initialNewTaskPlace
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
        val homePlace = params[23] as? PlaceSearchResult
        val initNewPlace = params[24] as? PlaceSearchResult

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

        val effectiveRouteTarget = routeTarget ?: if (nearbyTasks.isNotEmpty()) nearbyTasks.first() else selected

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
            showNearbyTaskAlert = showAlert,
            homeSearchedPlace = homePlace,
            initialNewTaskPlace = initNewPlace
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    init {
        clearAnySampleTasks()
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

    private fun clearAnySampleTasks() {
        viewModelScope.launch {
            val sampleTitles = setOf(
                "Süreyya Operası Bilet Teslimi",
                "Maslak Plaza Ofis Sunumu",
                "Emirgan Korusu Yürüyüşü",
                "Haftalık Organik Alışveriş",
                "Tiyatro Biletleri Teslimi",
                "İş Toplantı Evrakları",
                "Park Yürüyüşü ve Mola",
                "Haftalık Organik Pazar"
            )
            val existing = repository.getAllTasksList()
            existing.filter { it.title in sampleTitles }.forEach { task ->
                repository.deleteTask(task)
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

    fun selectTaskById(taskId: Long, showMap: Boolean = false) {
        viewModelScope.launch {
            val task = repository.getTaskById(taskId)
            if (task != null) {
                _selectedTask.value = task
                _routeTargetTask.value = task
            }
        }
    }

    fun clearSelectedTask() {
        _selectedTask.value = null
    }

    fun setActiveTab(tab: NavigationTab) {
        _activeTab.value = tab
    }

    fun setProximityThreshold(meters: Int) {
        prefs.edit().putInt("proximity_threshold_meters", meters).apply()
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

    fun openAddTask(initialPlace: PlaceSearchResult? = null) {
        _taskToEdit.value = null
        _searchResults.value = emptyList()
        _searchError.value = null
        _initialNewTaskPlace.value = initialPlace
        _isAddEditSheetOpen.value = true
    }

    fun openEditTask(task: TaskLocationEntity) {
        _taskToEdit.value = task
        _searchResults.value = emptyList()
        _searchError.value = null
        _initialNewTaskPlace.value = null
        _isAddEditSheetOpen.value = true
    }

    fun closeAddEditSheet() {
        _isAddEditSheetOpen.value = false
        _taskToEdit.value = null
        _initialNewTaskPlace.value = null
    }

    fun searchPlaceOnMap(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _isSearchingPlace.value = true
            _searchError.value = null
            val userLoc = uiState.value.currentUserLocation
            val searchResult = geminiService.searchPlaceWithMapsGrounding(
                query = clean,
                userLatitude = userLoc?.latitude,
                userLongitude = userLoc?.longitude
            )
            if (searchResult.isSuccess) {
                val place = searchResult.getOrThrow()
                _homeSearchedPlace.value = place
                _searchError.value = null
            } else {
                _homeSearchedPlace.value = null
                val strings = LocalizationManager.getStrings(_language.value)
                _searchError.value = strings.placeNotFoundMessage(clean)
            }
            _isSearchingPlace.value = false
        }
    }

    fun clearHomeSearchedPlace() {
        _homeSearchedPlace.value = null
        _searchError.value = null
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

    fun clearSearchError() {
        _searchError.value = null
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
        geminiPlaceInfo: String?,
        isLocationExplicitlySet: Boolean = false
    ) {
        viewModelScope.launch {
            val existing = _taskToEdit.value
            val currentDeviceType = syncManager.syncState.value.deviceType

            var finalLat = latitude
            var finalLng = longitude
            var finalAddress = address
            var finalPlaceInfo = geminiPlaceInfo

            // If location has NOT been explicitly verified/chosen on map, or place name changed:
            // Attempt to resolve the real-world coordinates of the entered place name first!
            val shouldResolvePlace = (!isLocationExplicitlySet && placeName.isNotBlank()) ||
                    (existing != null && placeName.isNotBlank() && placeName != existing.placeName && !isLocationExplicitlySet)

            if (shouldResolvePlace) {
                val searchResult = geminiService.searchPlaceWithMapsGrounding(
                    query = placeName,
                    userLatitude = uiState.value.currentUserLocation?.latitude,
                    userLongitude = uiState.value.currentUserLocation?.longitude
                )

                if (searchResult.isSuccess) {
                    val place = searchResult.getOrThrow()
                    finalLat = place.latitude
                    finalLng = place.longitude
                    if (finalAddress.isBlank() || finalAddress == "Harita konumu") {
                        finalAddress = place.address
                    }
                    if (finalPlaceInfo.isNullOrBlank()) {
                        finalPlaceInfo = place.summary
                    }
                } else {
                    // CRITICAL: The place could NOT be resolved to real coordinates!
                    // Notify user with error message, do NOT silently record the user's current location!
                    val strings = LocalizationManager.getStrings(_language.value)
                    _searchError.value = strings.placeNotFoundMessage(placeName)
                    return@launch
                }
            }

            if (existing != null) {
                val updated = existing.copy(
                    title = title,
                    description = description,
                    priority = priority,
                    placeName = placeName,
                    category = category,
                    latitude = finalLat,
                    longitude = finalLng,
                    address = finalAddress,
                    taskDescription = description,
                    radiusMeters = radiusMeters,
                    geminiPlaceInfo = finalPlaceInfo ?: existing.geminiPlaceInfo,
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
                    latitude = finalLat,
                    longitude = finalLng,
                    address = finalAddress,
                    taskDescription = description,
                    radiusMeters = radiusMeters,
                    deviceOrigin = currentDeviceType,
                    geminiPlaceInfo = finalPlaceInfo
                )
                val id = repository.addTask(newTask)
                _selectedTask.value = newTask.copy(id = id)
            }
            _searchError.value = null
            closeAddEditSheet()
        }
    }

    /**
     * Directly adds a task from the Home Screen when place information is entered.
     * Resolves the real-world coordinates of the place.
     * If found, sets the found place's real coordinates in the data.
     * If NOT found, provides an error message and NEVER sets the user's current location.
     */
    fun addPlaceFromHomeScreen(
        placeQuery: String,
        optionalTitle: String? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        val clean = placeQuery.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _isSearchingPlace.value = true
            _searchError.value = null
            val userLoc = uiState.value.currentUserLocation
            val searchResult = geminiService.searchPlaceWithMapsGrounding(
                query = clean,
                userLatitude = userLoc?.latitude,
                userLongitude = userLoc?.longitude
            )

            if (searchResult.isSuccess) {
                val place = searchResult.getOrThrow()
                val currentDeviceType = syncManager.syncState.value.deviceType
                val taskTitle = optionalTitle?.ifBlank { place.placeName } ?: place.placeName
                val taskDesc = place.suggestedTasks.firstOrNull() ?: "${place.placeName} konumundaki görevi tamamla"

                val newTask = TaskLocationEntity(
                    title = taskTitle,
                    description = taskDesc,
                    priority = "HIGH",
                    placeName = place.placeName,
                    category = place.category,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    address = place.address,
                    taskDescription = taskDesc,
                    radiusMeters = 150,
                    deviceOrigin = currentDeviceType,
                    geminiPlaceInfo = place.summary
                )

                val id = repository.addTask(newTask)
                val savedTask = newTask.copy(id = id)
                _selectedTask.value = savedTask
                _routeTargetTask.value = savedTask
                _showNearbyTaskAlert.value = true
                val strings = LocalizationManager.getStrings(_language.value)
                _simulatedArrivalMessage.value = "✅ ${place.placeName} konumu eklendi! (${String.format(Locale.US, "%.4f", place.latitude)}, ${String.format(Locale.US, "%.4f", place.longitude)})"
                _searchError.value = null
                _isSearchingPlace.value = false
                onSuccess?.invoke()
            } else {
                // If not found, show error message to the user! Never substitute current user location!
                val strings = LocalizationManager.getStrings(_language.value)
                _searchError.value = strings.placeNotFoundMessage(clean)
                _isSearchingPlace.value = false
            }
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
     * Searches places via Gemini 2.5 Flash with Google Maps Grounding and Geocoding Service.
     * If no place is found, reports an error message and NEVER substitutes current user location.
     */
    fun searchPlaceWithMapsGrounding(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _isSearchingPlace.value = true
            _searchError.value = null
            val userLoc = uiState.value.currentUserLocation
            val result = geminiService.searchMultiplePlaces(
                query = clean,
                userLatitude = userLoc?.latitude,
                userLongitude = userLoc?.longitude
            )
            result.onSuccess { places ->
                if (places.isNotEmpty()) {
                    _searchResults.value = places
                    _searchError.value = null
                } else {
                    _searchResults.value = emptyList()
                    val strings = LocalizationManager.getStrings(_language.value)
                    _searchError.value = strings.placeNotFoundMessage(clean)
                }
                _isSearchingPlace.value = false
            }.onFailure { err ->
                _searchResults.value = emptyList()
                val strings = LocalizationManager.getStrings(_language.value)
                _searchError.value = strings.placeNotFoundMessage(clean)
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

    fun stopLocationService() {
        val app = getApplication<Application>()
        LocationMonitorService.stopService(app)
    }

    fun toggleLocationService(hasPermission: Boolean = true) {
        val app = getApplication<Application>()
        if (LocationMonitorService.isServiceRunning.value) {
            LocationMonitorService.stopService(app)
        } else {
            if (hasPermission) {
                LocationMonitorService.startService(app)
            } else {
                LocationMonitorService.stopService(app)
            }
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            cloudSyncManager.syncNow()
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

    fun importSyncJson(json: String, preferIncoming: Boolean, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = syncManager.importPayload(json, preferIncoming)
            res.onSuccess { count ->
                _searchQuery.value = ""
                _selectedCategory.value = "Tümü"
                _activeTab.value = NavigationTab.TASKS
                _homeSearchedPlace.value = null
                onComplete(true, if (count > 0) "$count kayıt aktarıldı. Görev listesi ve filtreler yenilendi." else "Aktarım tamamlandı ancak değişen kayıt yok. Görev zaten mevcut olabilir.")
            }.onFailure { e ->
                onComplete(false, "Hata: ${e.localizedMessage}")
            }
        }
    }

    suspend fun exportSyncJson(): String {
        return syncManager.exportPayload()
    }
}
