package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.TaskLocationEntity
import com.example.gemini.PlaceSearchResult
import com.example.location.LocationHelper
import com.example.location.MapIntentHelper
import java.util.Locale
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.NavigationTab
import com.example.ui.components.AddEditTaskSheet
import com.example.ui.components.ArchiveContent
import com.example.ui.components.EmptyTasksView
import com.example.ui.components.SettingsDialog
import com.example.ui.components.SyncSheet
import com.example.ui.components.TaskCard
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.LocalizationManager
import com.example.ui.localization.LocalizedStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val strings = remember(uiState.language) {
        LocalizationManager.getStrings(uiState.language)
    }

    // Permissions check
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            val neededPermissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(neededPermissions.toTypedArray())
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission && uiState.isLocationServiceRunning) {
            viewModel.stopLocationService()
        }
    }

    // Show simulated arrival message via Snackbar
    LaunchedEffect(uiState.simulatedArrivalMessage) {
        uiState.simulatedArrivalMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSimulatedMessage()
        }
    }

    // Show place search error message via Snackbar if place is not found
    LaunchedEffect(uiState.searchError) {
        uiState.searchError?.let { err ->
            snackbarHostState.showSnackbar(err)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTabletLayout = maxWidth >= 600.dp

        if (isTabletLayout) {
            // TABLET / EXPANDED DUAL PANE LAYOUT
            TabletDualPaneLayout(
                uiState = uiState,
                strings = strings,
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                hasLocationPermission = hasLocationPermission,
                onRequestPermissions = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )
        } else {
            // PHONE / COMPACT SINGLE PANE LAYOUT
            PhoneSinglePaneLayout(
                uiState = uiState,
                strings = strings,
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                hasLocationPermission = hasLocationPermission,
                onRequestPermissions = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )
        }
    }

    // Add / Edit Task Modal Sheet
    if (uiState.isAddEditSheetOpen) {
        AddEditTaskSheet(
            taskToEdit = uiState.taskToEdit,
            isSearchingPlace = uiState.isSearchingPlace,
            searchResults = uiState.searchResults,
            searchError = uiState.searchError,
            onClearSearchError = { viewModel.clearSearchError() },
            onSearchPlace = { query -> viewModel.searchPlaceWithMapsGrounding(query) },
            onSave = { title, desc, priority, placeName, category, lat, lng, addr, radius, info, isLocationExplicitlySet ->
                viewModel.saveTask(title, desc, priority, placeName, category, lat, lng, addr, radius, info, isLocationExplicitlySet)
            },
            onDismiss = { viewModel.closeAddEditSheet() },
            currentUserLat = uiState.currentUserLocation?.latitude,
            currentUserLng = uiState.currentUserLocation?.longitude,
            initialNewTaskPlace = uiState.initialNewTaskPlace
        )
    }

    // Multi-Device Sync Modal Sheet
    if (uiState.isSyncSheetOpen) {
        SyncSheet(
            syncState = uiState.syncState,
            onDeviceTypeChange = { viewModel.setDeviceType(it) },
            onDeviceNameChange = { viewModel.setDeviceName(it) },
            onSyncCodeChange = { viewModel.setSyncRoomCode(it) },
            onTriggerSync = { viewModel.triggerSync() },
            onExportData = { viewModel.exportSyncJson() },
            onImportData = { json, preferIncoming, cb -> viewModel.importSyncJson(json, preferIncoming, cb) },
            onDismiss = { viewModel.closeSyncSheet() }
        )
    }

    // Settings & Language Modal Sheet
    if (uiState.isSettingsOpen) {
        SettingsDialog(
            strings = strings,
            currentLanguage = uiState.language,
            onLanguageSelected = { viewModel.setLanguage(it) },
            currentThemeMode = uiState.themeMode,
            onThemeModeSelected = { viewModel.setThemeMode(it) },
            proximityThresholdMeters = uiState.proximityThresholdMeters,
            onProximityThresholdSelected = { viewModel.setProximityThreshold(it) },
            isLocationServiceRunning = uiState.isLocationServiceRunning,
            onToggleLocationService = { viewModel.toggleLocationService() },
            deviceRole = uiState.syncState.deviceType,
            roomCode = uiState.syncState.syncRoomCode,
            onOpenSyncSheet = { viewModel.openSyncSheet() },
            onDismiss = { viewModel.closeSettings() }
        )
    }
}

/**
 * Tablet Dual-Pane Master-Detail View
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabletDualPaneLayout(
    uiState: MainUiState,
    strings: LocalizedStrings,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    hasLocationPermission: Boolean,
    onRequestPermissions: () -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = strings.appTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Tablet, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tablet Modu", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.openSyncSheet() },
                        modifier = Modifier.testTag("btn_sync_tablet")
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = "Manuel JSON senkronizasyonu")
                    }
                    // Language & Settings Button
                    IconButton(
                        onClick = { viewModel.openSettings() },
                        modifier = Modifier.testTag("btn_settings_tablet")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = strings.settings)
                    }

                    // Background 10s Tracking Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Text(
                            text = if (uiState.isLocationServiceRunning) {
                                if (uiState.language == AppLanguage.TURKISH) "10s Takip: Açık" else "10s Tracking: Active"
                            } else {
                                if (uiState.language == AppLanguage.TURKISH) "10s Takip: Kapalı" else "10s Tracking: Paused"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = uiState.isLocationServiceRunning,
                            onCheckedChange = {
                                if (it && !hasLocationPermission) {
                                    onRequestPermissions()
                                } else {
                                    viewModel.toggleLocationService(hasLocationPermission)
                                }
                            },
                            modifier = Modifier.testTag("service_toggle_tablet")
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // LEFT PANE (Master: Tasks List, Filters, Enter Key Banner)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Enter Key Banner at the beginning of the screen when a task is selected
                AnimatedVisibility(
                    visible = uiState.selectedTask != null,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    uiState.selectedTask?.let { selected ->
                        SelectedTaskEnterBanner(
                            selectedTask = selected,
                            strings = strings,
                            onNewEntry = { viewModel.openAddTask() },
                            onDeselect = { viewModel.clearSelectedTask() },
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                }

                UnifiedHomeSearch(uiState, viewModel)

                // Category Filter Dropdown (ComboBox)
                CategoryFilterDropdown(
                    selectedCategory = uiState.selectedCategory,
                    onCategorySelected = { viewModel.selectCategory(it) },
                    filterAllLabel = strings.filterAll
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Switch: Active vs Archive
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.activeTab != NavigationTab.ARCHIVE,
                        onClick = { viewModel.setActiveTab(NavigationTab.TASKS) },
                        label = { Text("${strings.tabTasks} (${uiState.totalActiveCount})") }
                    )
                    FilterChip(
                        selected = uiState.activeTab == NavigationTab.ARCHIVE,
                        onClick = { viewModel.setActiveTab(NavigationTab.ARCHIVE) },
                        label = { Text("${strings.tabArchive} (${uiState.totalCompletedCount})") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.activeTab == NavigationTab.ARCHIVE) {
                    ArchiveContent(
                        archivedTasks = uiState.archivedTasks,
                        strings = strings,
                        userLatitude = uiState.currentUserLocation?.latitude,
                        userLongitude = uiState.currentUserLocation?.longitude,
                        searchQuery = uiState.searchQuery,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        selectedCategory = uiState.selectedCategory,
                        onCategorySelected = { viewModel.selectCategory(it) },
                        onRestoreTask = { viewModel.restoreTask(it) },
                        onDeleteTask = { viewModel.deleteTask(it) },
                        onClearArchive = { viewModel.clearArchive() }
                    )
                } else {
                    // Action Bar: Add Task Button + Task Count
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${uiState.filteredTasks.size} ${strings.tasksCount}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (uiState.tasks.isNotEmpty()) {
                            ExtendedFloatingActionButton(
                                onClick = { viewModel.openAddTask() },
                                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                                text = { Text(strings.addNewTask) },
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag("add_task_fab"),
                                shape = RoundedCornerShape(12.dp),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Task List or Empty State
                    if (uiState.filteredTasks.isEmpty()) {
                        EmptyTasksView(
                            strings = strings,
                            onAddNewTask = { viewModel.openAddTask() }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(uiState.filteredTasks, key = { it.id }) { task ->
                                TaskCard(
                                    task = task,
                                    isSelected = task.id == uiState.selectedTask?.id,
                                    userLatitude = uiState.currentUserLocation?.latitude,
                                    userLongitude = uiState.currentUserLocation?.longitude,
                                    onSelect = { viewModel.selectTask(task) },
                                    onToggleComplete = { viewModel.toggleTaskComplete(task) },
                                    onEdit = { viewModel.openEditTask(task) },
                                    onDelete = { viewModel.deleteTask(task) },
                                    onSimulateArrival = { viewModel.simulateArrival(task) }
                                )
                            }
                        }
                    }
                }
            }


        }
    }
}

@Composable
private fun UnifiedHomeSearch(uiState: MainUiState, viewModel: MainViewModel) {
    var query by remember { mutableStateOf(uiState.searchQuery) }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it; viewModel.setSearchQuery(it) },
        modifier = Modifier.fillMaxWidth().testTag("unified_home_search"),
        placeholder = { Text("Görev veya adres ara") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { if (query.isNotBlank()) viewModel.searchPlaceOnMap(query) }) {
                Icon(Icons.Default.Place, contentDescription = "Adresi ara")
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            if (query.isNotBlank()) viewModel.searchPlaceOnMap(query)
        })
    )
    uiState.homeSearchedPlace?.let { place ->
        Text(text = place.placeName, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { viewModel.openAddTask(place) }) { Text("Bu adrese görev ekle") }
    }
}

/**
 * Detail and Overview Pane for Tablet dual-pane layout.
 * Replaces the map section with a polished, accessible task details / summary dashboard.
 */
@Composable
private fun TabletTaskDetailPane(
    selectedTask: TaskLocationEntity?,
    uiState: MainUiState,
    strings: LocalizedStrings,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    if (selectedTask != null) {
        // Detailed View of Selected Task
        Card(
            modifier = modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedTask.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedTask.isCompleted) Color(0xFF2E7D32).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = if (selectedTask.isCompleted) "✓ Tamamlandı" else "● Aktif Görev",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedTask.isCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = selectedTask.category,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { viewModel.clearSelectedTask() },
                        modifier = Modifier.testTag("btn_close_detail_pane")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                    }
                }

                // Description Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Görev Açıklaması",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = selectedTask.displayDescription,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Location Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Konum Bilgileri",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedTask.placeName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (selectedTask.address.isNotBlank()) {
                            Text(
                                text = selectedTask.address,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Koordinatlar: ${String.format(Locale.US, "%.5f", selectedTask.latitude)}, ${String.format(Locale.US, "%.5f", selectedTask.longitude)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "Varış Bildirim Yarıçapı: ${selectedTask.radiusMeters} metre",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )

                        // Distance to user if location available
                        uiState.currentUserLocation?.let { loc ->
                            val distance = LocationHelper.calculateDistanceMeters(
                                loc.latitude, loc.longitude,
                                selectedTask.latitude, selectedTask.longitude
                            )
                            val isNear = distance <= selectedTask.radiusMeters
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isNear) Color(0xFF2E7D32).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = if (isNear) "Menzil içindesiniz! (${distance.toInt()} m)" else "Uzaklık: ${if (distance >= 1000) String.format(Locale.US, "%.1f km", distance / 1000) else "${distance.toInt()} m"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isNear) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Place notes if available
                if (!selectedTask.geminiPlaceInfo.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Konum Notu",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = selectedTask.geminiPlaceInfo,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.toggleTaskComplete(selectedTask) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (selectedTask.isCompleted) "Geri Al" else "Tamamla")
                    }

                    OutlinedButton(
                        onClick = { viewModel.openEditTask(selectedTask) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Düzenle")
                    }

                    OutlinedButton(
                        onClick = { viewModel.simulateArrival(selectedTask) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Varışı Test Et")
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.deleteTask(selectedTask)
                            viewModel.clearSelectedTask()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Sil", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    } else {
        // No Task Selected: Overview & Stats Dashboard
        Card(
            modifier = modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Görev ve Konum Özeti",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Soldaki listeden bir görev seçerek detaylarını inceleyebilir veya yeni görev ekleyebilirsiniz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Metric Cards Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Toplam Görev", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${uiState.tasks.size}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Aktif Görevler", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${uiState.totalActiveCount}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Arşivlenmiş", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${uiState.totalCompletedCount}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                    }
                }

                // Device Sync Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Çoklu Cihaz Senkronizasyonu", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Oda Kodu: ${uiState.syncState.syncRoomCode} • Cihaz: ${uiState.syncState.deviceType}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { viewModel.openSyncSheet() },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Yönet")
                        }
                    }
                }

                // Quick Add Task Button
                Button(
                    onClick = { viewModel.openAddTask() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Yeni Görev Ekle", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Phone Single-Pane Navigation Layout
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneSinglePaneLayout(
    uiState: MainUiState,
    strings: LocalizedStrings,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    hasLocationPermission: Boolean,
    onRequestPermissions: () -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = strings.appTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (uiState.language == AppLanguage.TURKISH) {
                                "10s Kontrol • ${uiState.syncState.syncRoomCode} (${uiState.syncState.deviceType})"
                            } else {
                                "10s Interval • ${uiState.syncState.syncRoomCode} (${uiState.syncState.deviceType})"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    // Settings & Language Button
                    IconButton(
                        onClick = { viewModel.openSettings() },
                        modifier = Modifier.testTag("btn_settings_phone")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = strings.settings)
                    }

                    // Background tracking toggle
                    Switch(
                        checked = uiState.isLocationServiceRunning,
                        onCheckedChange = {
                            if (it && !hasLocationPermission) {
                                onRequestPermissions()
                            } else {
                                viewModel.toggleLocationService(hasLocationPermission)
                            }
                        },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("service_toggle")
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("bottom_nav_bar"),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = uiState.activeTab == NavigationTab.TASKS,
                    onClick = { viewModel.setActiveTab(NavigationTab.TASKS) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (uiState.totalActiveCount > 0) {
                                    Badge { Text("${uiState.totalActiveCount}") }
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = strings.tabTasks)
                        }
                    },
                    label = { Text(strings.tabTasks) },
                    modifier = Modifier.testTag("tab_tasks")
                )
                NavigationBarItem(
                    selected = uiState.activeTab == NavigationTab.ARCHIVE,
                    onClick = { viewModel.setActiveTab(NavigationTab.ARCHIVE) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (uiState.totalCompletedCount > 0) {
                                    Badge { Text("${uiState.totalCompletedCount}") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Archive, contentDescription = strings.tabArchive)
                        }
                    },
                    label = { Text(strings.tabArchive) },
                    modifier = Modifier.testTag("tab_archive")
                )
                NavigationBarItem(
                    selected = uiState.activeTab == NavigationTab.SYNC,
                    onClick = { viewModel.openSyncSheet() },
                    icon = { Icon(Icons.Outlined.Sync, contentDescription = strings.tabSync) },
                    label = { Text(strings.tabSync) },
                    modifier = Modifier.testTag("tab_sync")
                )
            }
        },
        floatingActionButton = {
            if (uiState.activeTab != NavigationTab.ARCHIVE && uiState.tasks.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { viewModel.openAddTask() },
                    modifier = Modifier.testTag("add_task_fab"),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.addNewTask)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Permission Notice Banner if location permission is not granted
            if (!hasLocationPermission) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRequestPermissions() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.permissionSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Screen Content Based on Active Tab
            when (uiState.activeTab) {
                NavigationTab.TASKS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        // Enter Key Action at the beginning of the screen when a task is selected
                        AnimatedVisibility(
                            visible = uiState.selectedTask != null,
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut() + slideOutVertically()
                        ) {
                            uiState.selectedTask?.let { selected ->
                                SelectedTaskEnterBanner(
                                    selectedTask = selected,
                                    strings = strings,
                                    onNewEntry = { viewModel.openAddTask() },
                                    onDeselect = { viewModel.clearSelectedTask() },
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }

                        UnifiedHomeSearch(uiState, viewModel)

                // Category Filter Dropdown (ComboBox)
                        CategoryFilterDropdown(
                            selectedCategory = uiState.selectedCategory,
                            onCategorySelected = { viewModel.selectCategory(it) },
                            filterAllLabel = strings.filterAll
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Tasks list or Empty State
                        if (uiState.filteredTasks.isEmpty()) {
                            EmptyTasksView(
                                strings = strings,
                                onAddNewTask = { viewModel.openAddTask() }
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                items(uiState.filteredTasks, key = { it.id }) { task ->
                                    TaskCard(
                                        task = task,
                                        isSelected = task.id == uiState.selectedTask?.id,
                                        userLatitude = uiState.currentUserLocation?.latitude,
                                        userLongitude = uiState.currentUserLocation?.longitude,
                                        onSelect = { viewModel.selectTask(task) },
                                        onToggleComplete = { viewModel.toggleTaskComplete(task) },
                                        onEdit = { viewModel.openEditTask(task) },
                                        onDelete = { viewModel.deleteTask(task) },
                                        onSimulateArrival = { viewModel.simulateArrival(task) }
                                    )
                                }
                            }
                        }
                    }
                }

                NavigationTab.ARCHIVE -> {
                    ArchiveContent(
                        archivedTasks = uiState.archivedTasks,
                        strings = strings,
                        userLatitude = uiState.currentUserLocation?.latitude,
                        userLongitude = uiState.currentUserLocation?.longitude,
                        searchQuery = uiState.searchQuery,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        selectedCategory = uiState.selectedCategory,
                        onCategorySelected = { viewModel.selectCategory(it) },
                        onRestoreTask = { viewModel.restoreTask(it) },
                        onDeleteTask = { viewModel.deleteTask(it) },
                        onClearArchive = { viewModel.clearArchive() }
                    )
                }

                NavigationTab.SYNC -> {
                    LaunchedEffect(Unit) {
                        viewModel.openSyncSheet()
                        viewModel.setActiveTab(NavigationTab.TASKS)
                    }
                }
            }
        }
    }
}

/**
 * Enter Key banner placed at the beginning of the screen when a task is selected
 */
@Composable
private fun SelectedTaskEnterBanner(
    selectedTask: TaskLocationEntity,
    strings: LocalizedStrings,
    onNewEntry: () -> Unit,
    onDeselect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("selected_task_enter_banner")
            .onKeyEvent { event ->
                if (event.key == Key.Enter) {
                    onNewEntry()
                    true
                } else false
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "📍 ${strings.selectedTaskBanner}:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (selectedTask.priority.uppercase()) {
                            "HIGH" -> androidx.compose.ui.graphics.Color(0xFFFFCDD2)
                            "LOW" -> androidx.compose.ui.graphics.Color(0xFFC8E6C9)
                            else -> androidx.compose.ui.graphics.Color(0xFFFFE0B2)
                        }
                    ) {
                        Text(
                            text = selectedTask.priority,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (selectedTask.priority.uppercase()) {
                                "HIGH" -> androidx.compose.ui.graphics.Color(0xFFB71C1C)
                                "LOW" -> androidx.compose.ui.graphics.Color(0xFF1B5E20)
                                else -> androidx.compose.ui.graphics.Color(0xFFE65100)
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = selectedTask.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = selectedTask.placeName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Enter Key Action Button
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onNewEntry,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("btn_enter_new_entry")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardReturn,
                        contentDescription = strings.enterKeyNewEntry,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = strings.enterKeyNewEntry,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                IconButton(
                    onClick = onDeselect,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_clear_selected_task")
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = strings.clearSelection,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFilterDropdown(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    filterAllLabel: String = "Tümü",
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val categories = listOf(filterAllLabel, "İşyeri", "Park", "Tiyatro / Kültür", "Market", "Kafe / Restoran", "Diğer")
    val displayCategory = if (selectedCategory.isBlank() || selectedCategory == "All") filterAllLabel else selectedCategory

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = displayCategory,
            onValueChange = {},
            readOnly = true,
            label = { Text("Kategori Filtresi") },
            leadingIcon = {
                Icon(
                    Icons.Default.FilterAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag("category_filter_combo_box"),
            shape = RoundedCornerShape(12.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { category ->
                val isSelected = selectedCategory == category || (category == filterAllLabel && (selectedCategory == "Tümü" || selectedCategory == "All"))
                DropdownMenuItem(
                    text = {
                        Text(
                            text = category,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingIcon = {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    onClick = {
                        onCategorySelected(category)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
fun CategoryFilterRow(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    filterAllLabel: String = "Tümü",
    modifier: Modifier = Modifier
) {
    val categories = listOf(filterAllLabel, "İşyeri", "Park", "Tiyatro / Kültür", "Market", "Kafe / Restoran", "Diğer")

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { cat ->
            val isSelected = selectedCategory == cat || (cat == filterAllLabel && (selectedCategory == "Tümü" || selectedCategory == "All"))
            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelected(cat) },
                label = { Text(cat, fontSize = 12.sp) },
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

/**
 * HomeScreenPlaceInputSection removed as map/search options are removed from HomeScreen.
 */
@Composable
private fun HomeScreenPlaceInputSection(
    modifier: Modifier = Modifier
) {
}

