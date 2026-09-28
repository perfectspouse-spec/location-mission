package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TaskLocationEntity
import com.example.gemini.PlaceSearchResult

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditTaskSheet(
    taskToEdit: TaskLocationEntity?,
    isSearchingPlace: Boolean,
    searchResults: List<PlaceSearchResult>,
    searchError: String? = null,
    onClearSearchError: () -> Unit = {},
    onSearchPlace: (String) -> Unit,
    onSave: (
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
        isLocationExplicitlySet: Boolean
    ) -> Unit,
    onDismiss: () -> Unit,
    currentUserLat: Double?,
    currentUserLng: Double?
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(taskToEdit?.title ?: "") }
    var description by remember { mutableStateOf(taskToEdit?.displayDescription ?: "") }
    var priority by remember { mutableStateOf(taskToEdit?.priority ?: "MEDIUM") }
    var searchQuery by remember { mutableStateOf("") }
    var placeName by remember { mutableStateOf(taskToEdit?.placeName ?: "") }
    var category by remember { mutableStateOf(taskToEdit?.category ?: "İşyeri") }
    var address by remember { mutableStateOf(taskToEdit?.address ?: "") }
    var latitude by remember { mutableDoubleStateOf(taskToEdit?.latitude ?: (currentUserLat ?: 41.0082)) }
    var longitude by remember { mutableDoubleStateOf(taskToEdit?.longitude ?: (currentUserLng ?: 28.9784)) }
    var radiusMeters by remember { mutableIntStateOf(taskToEdit?.radiusMeters ?: 100) }
    var geminiPlaceInfo by remember { mutableStateOf(taskToEdit?.geminiPlaceInfo) }
    var isLocationExplicitlySet by remember { mutableStateOf(taskToEdit != null) }
    var lastResolvedPlaceName by remember { mutableStateOf(taskToEdit?.placeName ?: "") }

    // Auto-update coordinates and address when a place search succeeds
    LaunchedEffect(searchResults) {
        if (searchResults.isNotEmpty()) {
            val first = searchResults.first()
            latitude = first.latitude
            longitude = first.longitude
            address = first.address
            lastResolvedPlaceName = first.placeName
            isLocationExplicitlySet = true
            if (geminiPlaceInfo.isNullOrBlank()) {
                geminiPlaceInfo = first.summary
            }
            if (title.isBlank()) {
                title = first.placeName
            }
        }
    }

    LaunchedEffect(searchError) {
        if (!searchError.isNullOrBlank()) {
            isLocationExplicitlySet = false
        }
    }

    val categories = listOf("İşyeri", "Park", "Tiyatro / Kültür", "Market", "Kafe / Restoran", "Diğer")
    val radiusOptions = listOf(50, 100, 250, 500)
    val priorityOptions = listOf(
        Triple("HIGH", "🚨 Yüksek", Color(0xFFC62828)),
        Triple("MEDIUM", "🟡 Orta", Color(0xFFE65100)),
        Triple("LOW", "🟢 Düşük", Color(0xFF2E7D32))
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (taskToEdit == null) "Yeni Görev Ekle" else "Görevi Düzenle",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Konuma vardığınızda bildirim ile hatırlatılır",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.testTag("btn_close_sheet")) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Task Title (Başlık)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Görev Başlığı *") },
                placeholder = { Text("Örn: Süreyya Tiyatrosu Bilet Teslimatı") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_title_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Task Description (Açıklama / Ne Yapılacak)
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Görev Açıklaması / Ne Yapılacak? *") },
                placeholder = { Text("Örn: Gişeden cuma günkü rezerve biletleri teslim al") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_description_input"),
                shape = RoundedCornerShape(12.dp),
                minLines = 2
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Priority Selection (Öncelik Seçimi)
            Text(
                text = "Öncelik Seviyesi *",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                priorityOptions.forEach { (key, label, color) ->
                    val isSelected = priority.equals(key, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) color else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { priority = key }
                            .testTag("priority_chip_$key")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSelected) color else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Location Selection (Konum Seçimi)
            Text(
                text = "Konum / Yer Seçimi *",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Search with Google Maps Grounding
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Google Haritalar ile Otomatik Bul",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                onClearSearchError()
                            },
                            placeholder = { Text("Örn: Süreyya Operası, Emirgan Parkı...") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("place_search_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                val target = searchQuery.ifBlank { placeName }
                                if (target.isNotBlank()) {
                                    onSearchPlace(target)
                                }
                            },
                            enabled = (searchQuery.isNotBlank() || placeName.isNotBlank()) && !isSearchingPlace,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("search_place_button")
                        ) {
                            if (isSearchingPlace) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Search, contentDescription = "Ara")
                            }
                        }
                    }
                }
            }

            // Error Banner if place could not be found
            if (!searchError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("place_search_error_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = searchError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onClearSearchError, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Show Search Results from Gemini Maps Grounding / Geocoding if available
            if (searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    searchResults.forEach { result ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    placeName = result.placeName
                                    latitude = result.latitude
                                    longitude = result.longitude
                                    address = result.address
                                    isLocationExplicitlySet = true
                                    lastResolvedPlaceName = result.placeName
                                    onClearSearchError()
                                    category = when {
                                        result.category.contains("park", true) -> "Park"
                                        result.category.contains("tiyatro", true) || result.category.contains("kültür", true) -> "Tiyatro / Kültür"
                                        result.category.contains("market", true) -> "Market"
                                        result.category.contains("kafe", true) || result.category.contains("restoran", true) -> "Kafe / Restoran"
                                        result.category.contains("ofis", true) || result.category.contains("iş", true) -> "İşyeri"
                                        else -> result.category
                                    }
                                    if (description.isBlank() && result.suggestedTasks.isNotEmpty()) {
                                        description = result.suggestedTasks.first()
                                    }
                                    if (title.isBlank()) {
                                        title = result.placeName
                                    }
                                    geminiPlaceInfo = result.summary
                                }
                                .testTag("search_result_item"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = result.placeName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = result.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // Success badge when location is verified
            if (isLocationExplicitlySet && lastResolvedPlaceName.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Konum Doğrulandı: $lastResolvedPlaceName (${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Place Name manual input with search trigger
            OutlinedTextField(
                value = placeName,
                onValueChange = {
                    placeName = it
                    if (it != lastResolvedPlaceName) {
                        isLocationExplicitlySet = false
                    }
                    if (title.isBlank()) title = it
                    onClearSearchError()
                },
                label = { Text("Konum / Yer Adı *") },
                placeholder = { Text("Örn: Kadıköy Süreyya Tiyatrosu, Anıtkabir...") },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (isSearchingPlace) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (placeName.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    onSearchPlace(placeName)
                                },
                                modifier = Modifier.testTag("btn_find_place_location")
                            ) {
                                Icon(
                                    Icons.Default.LocationSearching,
                                    contentDescription = "Konumu Bul",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (placeName.isNotBlank() && !isSearchingPlace) {
                        onSearchPlace(placeName)
                    }
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("place_name_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Address field
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Açık Adres (Opsiyonel)") },
                placeholder = { Text("Örn: Bahariye Cad. No:29, Kadıköy") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Interactive Pin on Map Preview Box
            Text(
                text = "📍 Haritada Konum İğneleme (Dokunarak Seçin)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            // Translate tap on mini map into coordinate nudge
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()
                            val deltaLng = ((offset.x / width) - 0.5f) * 0.04f
                            val deltaLat = (0.5f - (offset.y / height)) * 0.04f
                            latitude += deltaLat
                            longitude += deltaLng
                            isLocationExplicitlySet = true
                        }
                    }
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = "İğne",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Seçili İğne: ${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Haritaya dokunarak iğneyi taşıyabilirsiniz",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Open in Google Maps Button
                    OutlinedButton(
                        onClick = {
                            val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(placeName.ifBlank { "Hedef Konum" })})")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Google Maps", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Coordinates & Current Location Option
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = String.format("%.4f", latitude),
                    onValueChange = {
                        latitude = it.toDoubleOrNull() ?: latitude
                        isLocationExplicitlySet = true
                    },
                    label = { Text("Enlem (Lat)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = String.format("%.4f", longitude),
                    onValueChange = {
                        longitude = it.toDoubleOrNull() ?: longitude
                        isLocationExplicitlySet = true
                    },
                    label = { Text("Boylam (Lng)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                if (currentUserLat != null && currentUserLng != null) {
                    IconButton(
                        onClick = {
                            latitude = currentUserLat
                            longitude = currentUserLng
                            address = "Mevcut konumunuz"
                            isLocationExplicitlySet = true
                        },
                        modifier = Modifier.testTag("btn_use_current_location")
                    ) {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = "Mevcut Konumu Kullan",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category Chips
            Text(
                text = "Kategori",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat, fontSize = 12.sp) },
                        leadingIcon = if (category == cat) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Geofence Detection Radius
            Text(
                text = "Varış Bildirim Yarıçapı (Geofence)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Bu mesafeye girdiğinizde bildirim tetiklenecektir",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                radiusOptions.forEach { r ->
                    FilterChip(
                        selected = radiusMeters == r,
                        onClick = { radiusMeters = r },
                        label = { Text("${r} m", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save & Cancel Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text("İptal")
                }

                Button(
                    onClick = {
                        val finalTitle = title.ifBlank { placeName.ifBlank { "Görev" } }
                        val finalPlace = placeName.ifBlank { title.ifBlank { "Konum" } }
                        if (description.isNotBlank()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSave(
                                finalTitle.trim(),
                                description.trim(),
                                priority,
                                finalPlace.trim(),
                                category,
                                latitude,
                                longitude,
                                address.trim(),
                                radiusMeters,
                                geminiPlaceInfo,
                                isLocationExplicitlySet
                            )
                        }
                    },
                    enabled = (title.isNotBlank() || placeName.isNotBlank()) && description.isNotBlank(),
                    modifier = Modifier
                        .weight(2f)
                        .height(52.dp)
                        .testTag("save_task_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (taskToEdit == null) "Görevi Kaydet" else "Değişiklikleri Kaydet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
