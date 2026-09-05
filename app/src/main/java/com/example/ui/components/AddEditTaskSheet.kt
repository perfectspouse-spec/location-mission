package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
    onSearchPlace: (String) -> Unit,
    onSave: (
        placeName: String,
        category: String,
        latitude: Double,
        longitude: Double,
        address: String,
        taskDescription: String,
        radiusMeters: Int,
        geminiPlaceInfo: String?
    ) -> Unit,
    onDismiss: () -> Unit,
    currentUserLat: Double?,
    currentUserLng: Double?
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var searchQuery by remember { mutableStateOf("") }
    var placeName by remember { mutableStateOf(taskToEdit?.placeName ?: "") }
    var taskDescription by remember { mutableStateOf(taskToEdit?.taskDescription ?: "") }
    var category by remember { mutableStateOf(taskToEdit?.category ?: "İşyeri") }
    var address by remember { mutableStateOf(taskToEdit?.address ?: "") }
    var latitude by remember { mutableDoubleStateOf(taskToEdit?.latitude ?: (currentUserLat ?: 41.0082)) }
    var longitude by remember { mutableDoubleStateOf(taskToEdit?.longitude ?: (currentUserLng ?: 28.9784)) }
    var radiusMeters by remember { mutableIntStateOf(taskToEdit?.radiusMeters ?: 100) }
    var geminiPlaceInfo by remember { mutableStateOf(taskToEdit?.geminiPlaceInfo) }

    val categories = listOf("İşyeri", "Park", "Tiyatro / Kültür", "Market", "Kafe / Restoran", "Diğer")
    val radiusOptions = listOf(50, 100, 250, 500)

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
                        text = if (taskToEdit == null) "Yeni Konum Görevi" else "Görevi Düzenle",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Vardığınızda bildirim ile hatırlatılır",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI & Google Maps Grounding Search Box
            Surface(
                shape = RoundedCornerShape(16.dp),
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
                            text = "Google Maps Verileri ile Yeri Bul",
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
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Örn: Süreyya Operası, Emirgan Parkı, Zorlu...") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("place_search_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { onSearchPlace(searchQuery) },
                            enabled = searchQuery.isNotBlank() && !isSearchingPlace,
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

                    // Search Results with Maps Grounding data
                    if (searchResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        searchResults.forEach { result ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        placeName = result.placeName
                                        category = result.category
                                        address = result.address
                                        latitude = result.latitude
                                        longitude = result.longitude
                                        geminiPlaceInfo = result.summary
                                        if (result.suggestedTasks.isNotEmpty() && taskDescription.isBlank()) {
                                            taskDescription = result.suggestedTasks.first()
                                        }
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = result.placeName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = getCategoryColor(result.category).copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = result.category,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = getCategoryColor(result.category),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = result.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Text(
                                        text = result.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        fontSize = 11.sp
                                    )

                                    if (result.suggestedTasks.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "💡 Önerilen Görev: ${result.suggestedTasks.first()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Place Name Field
            OutlinedTextField(
                value = placeName,
                onValueChange = { placeName = it },
                label = { Text("Yer Adı (İşyeri, Park, Tiyatro vb.) *") },
                placeholder = { Text("Örn: Kadıköy Süreyya Tiyatrosu") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_place_name_input"),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Task Description (What user wants to do at this place)
            OutlinedTextField(
                value = taskDescription,
                onValueChange = { taskDescription = it },
                label = { Text("Bu Yerde Ne Yapmak İstiyorsunuz? *") },
                placeholder = { Text("Örn: Gişeden tiyatro biletlerini teslim al") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_description_input"),
                shape = RoundedCornerShape(12.dp),
                minLines = 2
            )

            Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(12.dp))

            // Geofence Detection Radius
            Text(
                text = "Bildirim Çapı (Geofence)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Seçilen mesafeye yaklaştığınızda bildirim tetiklenir",
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

            Spacer(modifier = Modifier.height(12.dp))

            // Coordinates & Current Location Option
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = String.format("%.4f", latitude),
                    onValueChange = { latitude = it.toDoubleOrNull() ?: latitude },
                    label = { Text("Enlem (Lat)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = String.format("%.4f", longitude),
                    onValueChange = { longitude = it.toDoubleOrNull() ?: longitude },
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
                        }
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Konumumu Kullan", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Save Button
            Button(
                onClick = {
                    if (placeName.isNotBlank() && taskDescription.isNotBlank()) {
                        onSave(
                            placeName.trim(),
                            category,
                            latitude,
                            longitude,
                            address.trim(),
                            taskDescription.trim(),
                            radiusMeters,
                            geminiPlaceInfo
                        )
                    }
                },
                enabled = placeName.isNotBlank() && taskDescription.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
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
                    text = if (taskToEdit == null) "Görevi Kaydet & Takibe Başla" else "Değişiklikleri Kaydet",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
