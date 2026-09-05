package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.TaskLocationEntity
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.NavigationTab
import com.example.ui.components.AddEditTaskSheet
import com.example.ui.components.InteractiveMapCanvas
import com.example.ui.components.SyncSheet
import com.example.ui.components.TaskCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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

    // Show simulated arrival message via Snackbar
    LaunchedEffect(uiState.simulatedArrivalMessage) {
        uiState.simulatedArrivalMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSimulatedMessage()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTabletLayout = maxWidth >= 600.dp

        if (isTabletLayout) {
            // TABLET / EXPANDED DUAL PANE LAYOUT
            TabletDualPaneLayout(
                uiState = uiState,
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
            onSearchPlace = { query -> viewModel.searchPlaceWithMapsGrounding(query) },
            onSave = { placeName, category, lat, lng, addr, desc, radius, info ->
                viewModel.saveTask(placeName, category, lat, lng, addr, desc, radius, info)
            },
            onDismiss = { viewModel.closeAddEditSheet() },
            currentUserLat = uiState.currentUserLocation?.latitude,
            currentUserLng = uiState.currentUserLocation?.longitude
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
            onImportData = { json, cb -> viewModel.importSyncJson(json, cb) },
            onDismiss = { viewModel.closeSyncSheet() }
        )
    }
}

/**
 * Tablet Dual-Pane Master-Detail View:
 * Left: Search, Category filter, Task List, Sync & Background status
 * Right: Full interactive map with live pin tracking & quick controls
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabletDualPaneLayout(
    uiState: MainUiState,
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
                            text = "Konum Görev Yöneticisi",
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
                    // Sync Status Badge & Button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clickable { viewModel.openSyncSheet() }
                            .padding(end = 12.dp)
                            .testTag("tablet_sync_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Eşleşme: ${uiState.syncState.syncRoomCode}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Background Service Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Text(
                            text = if (uiState.isLocationServiceRunning) "Arka Plan Takibi: Açık" else "Arka Plan Takibi",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = uiState.isLocationServiceRunning,
                            onCheckedChange = { viewModel.toggleLocationService() },
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
            // LEFT PANE (Master: Tasks List, Filters, Search)
            Column(
                modifier = Modifier
                    .width(400.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Search Bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Görev veya yer ara...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tablet_search_bar"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Category Filter Chips
                CategoryFilterRow(
                    selectedCategory = uiState.selectedCategory,
                    onCategorySelected = { viewModel.selectCategory(it) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Action Bar: Add Task Button + Task Count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${uiState.filteredTasks.size} Görev",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    ExtendedFloatingActionButton(
                        onClick = { viewModel.openAddTask() },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("Yeni Konum Görevi") },
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("add_task_fab"),
                        shape = RoundedCornerShape(12.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Task List
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

            // RIGHT PANE (Detail: Interactive Map View)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(8.dp)
            ) {
                InteractiveMapCanvas(
                    tasks = uiState.filteredTasks,
                    selectedTask = uiState.selectedTask,
                    onTaskSelected = { viewModel.selectTask(it) },
                    onMapTappedCoordinates = { lat, lng ->
                        viewModel.openAddTask()
                    },
                    onSimulateArrival = { viewModel.simulateArrival(it) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Phone Single-Pane Navigation Layout:
 * Switch between Map View, Tasks List, and Sync Tabs
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneSinglePaneLayout(
    uiState: MainUiState,
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
                            text = "Konum Görevleri",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Eşleşme: ${uiState.syncState.syncRoomCode} (${uiState.syncState.deviceType})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    // Sync icon button
                    IconButton(
                        onClick = { viewModel.openSyncSheet() },
                        modifier = Modifier.testTag("sync_sheet_button")
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = "Senkronizasyon", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Background tracking toggle
                    Switch(
                        checked = uiState.isLocationServiceRunning,
                        onCheckedChange = { viewModel.toggleLocationService() },
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
                    selected = uiState.activeTab == NavigationTab.MAP,
                    onClick = { viewModel.setActiveTab(NavigationTab.MAP) },
                    icon = { Icon(Icons.Default.Map, contentDescription = "Harita") },
                    label = { Text("Harita") },
                    modifier = Modifier.testTag("tab_map")
                )
                NavigationBarItem(
                    selected = uiState.activeTab == NavigationTab.TASKS,
                    onClick = { viewModel.setActiveTab(NavigationTab.TASKS) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (uiState.filteredTasks.isNotEmpty()) {
                                    Badge { Text("${uiState.filteredTasks.size}") }
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = "Görevler")
                        }
                    },
                    label = { Text("Görevler") },
                    modifier = Modifier.testTag("tab_tasks")
                )
                NavigationBarItem(
                    selected = uiState.activeTab == NavigationTab.SYNC,
                    onClick = { viewModel.openSyncSheet() },
                    icon = { Icon(Icons.Outlined.Sync, contentDescription = "Senkron") },
                    label = { Text("Senkron") },
                    modifier = Modifier.testTag("tab_sync")
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddTask() },
                modifier = Modifier.testTag("add_task_fab"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Yeni Konum Görevi")
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
                            text = "Konum izni gerekli. Arka planda varış bildirimi için dokunun.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Screen Content Based on Active Tab
            when (uiState.activeTab) {
                NavigationTab.MAP -> {
                    InteractiveMapCanvas(
                        tasks = uiState.filteredTasks,
                        selectedTask = uiState.selectedTask,
                        onTaskSelected = { viewModel.selectTask(it) },
                        onMapTappedCoordinates = { lat, lng ->
                            viewModel.openAddTask()
                        },
                        onSimulateArrival = { viewModel.simulateArrival(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                NavigationTab.TASKS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Search bar
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Görev veya yer ara...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_tasks_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        CategoryFilterRow(
                            selectedCategory = uiState.selectedCategory,
                            onCategorySelected = { viewModel.selectCategory(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Tasks list
                        if (uiState.filteredTasks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 64.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Henüz bu kategoride görev yok",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "+ butonuna dokunarak yeni yer ekleyin",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
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

                NavigationTab.SYNC -> {
                    // Sync tab directly opens sync sheet
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf("Tümü", "İşyeri", "Park", "Tiyatro / Kültür", "Market", "Kafe / Restoran", "Diğer")

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { cat ->
            FilterChip(
                selected = selectedCategory == cat,
                onClick = { onCategorySelected(cat) },
                label = { Text(cat, fontSize = 12.sp) },
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}
