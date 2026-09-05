package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TaskLocationEntity
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun InteractiveMapCanvas(
    tasks: List<TaskLocationEntity>,
    selectedTask: TaskLocationEntity?,
    onTaskSelected: (TaskLocationEntity) -> Unit,
    onMapTappedCoordinates: ((Double, Double) -> Unit)? = null,
    onSimulateArrival: (TaskLocationEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Reference center coordinates (Istanbul / Region focal point)
    var centerLat by remember { mutableFloatStateOf(41.04f) }
    var centerLng by remember { mutableFloatStateOf(29.02f) }
    var zoomLevel by remember { mutableFloatStateOf(1.0f) } // 0.5x to 3.0x

    // Pulse animation for selected geofence
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRatio by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseRadius"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFE5E7EB))
            .testTag("map_canvas_view")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        // Sensitivity scaled by zoom
                        val scale = 0.0003f / zoomLevel
                        centerLng -= dragAmount.x * scale
                        centerLat += dragAmount.y * scale
                    }
                }
                .pointerInput(tasks) {
                    detectTapGestures { tapOffset ->
                        val w = size.width
                        val h = size.height
                        val lngSpan = 0.20f / zoomLevel
                        val latSpan = 0.15f / zoomLevel

                        // Check if tapped near any task pin
                        var clickedTask: TaskLocationEntity? = null
                        for (task in tasks) {
                            val pinX = ((task.longitude.toFloat() - (centerLng - lngSpan / 2)) / lngSpan) * w
                            val pinY = (((centerLat + latSpan / 2) - task.latitude.toFloat()) / latSpan) * h
                            val dx = tapOffset.x - pinX
                            val dy = tapOffset.y - pinY
                            if (dx * dx + dy * dy <= 40f * 40f) {
                                clickedTask = task
                                break
                            }
                        }

                        if (clickedTask != null) {
                            onTaskSelected(clickedTask)
                        } else {
                            val tappedLng = (centerLng - lngSpan / 2) + (tapOffset.x / w) * lngSpan
                            val tappedLat = (centerLat + latSpan / 2) - (tapOffset.y / h) * latSpan
                            onMapTappedCoordinates?.invoke(tappedLat.toDouble(), tappedLng.toDouble())
                        }
                    }
                }
        ) {
            val width = size.width
            val height = size.height

            // 1. Draw stylized background map terrain
            drawMapTerrain(width, height, centerLat, centerLng, zoomLevel)

            // 2. Coordinate translation window
            val lngSpan = 0.20f / zoomLevel
            val latSpan = 0.15f / zoomLevel
            val minLng = centerLng - lngSpan / 2
            val maxLat = centerLat + latSpan / 2

            // 3. Draw geofence radius circle and pulse for tasks
            for (task in tasks) {
                val pinX = ((task.longitude.toFloat() - minLng) / lngSpan) * width
                val pinY = ((maxLat - task.latitude.toFloat()) / latSpan) * height

                val isSelected = task.id == selectedTask?.id
                val radiusPx = (task.radiusMeters / 15f) * zoomLevel * 3.5f

                val pinColor = getCategoryColor(task.category)

                // Geofence Circle
                drawCircle(
                    color = pinColor.copy(alpha = if (isSelected) 0.22f else 0.10f),
                    radius = if (isSelected) radiusPx * pulseRatio else radiusPx,
                    center = Offset(pinX, pinY)
                )

                drawCircle(
                    color = pinColor.copy(alpha = if (isSelected) 0.8f else 0.4f),
                    radius = if (isSelected) radiusPx * pulseRatio else radiusPx,
                    center = Offset(pinX, pinY),
                    style = Stroke(width = if (isSelected) 3.5f else 2f)
                )

                // Pin Marker Body
                val markerRadius = if (isSelected) 18f else 14f
                drawCircle(
                    color = Color.White,
                    radius = markerRadius + 3f,
                    center = Offset(pinX, pinY)
                )
                drawCircle(
                    color = pinColor,
                    radius = markerRadius,
                    center = Offset(pinX, pinY)
                )
                drawCircle(
                    color = Color.White,
                    radius = markerRadius * 0.4f,
                    center = Offset(pinX, pinY)
                )
            }
        }

        // Map Control Floating Buttons (Zoom In, Zoom Out, Center)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 4.dp
            ) {
                IconButton(
                    onClick = { zoomLevel = (zoomLevel * 1.25f).coerceAtMost(3.0f) },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Yakınlaştır")
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 4.dp
            ) {
                IconButton(
                    onClick = { zoomLevel = (zoomLevel / 1.25f).coerceAtMost(3.0f).coerceAtLeast(0.6f) },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Uzaklaştır")
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 4.dp
            ) {
                IconButton(
                    onClick = {
                        if (selectedTask != null) {
                            centerLat = selectedTask.latitude.toFloat()
                            centerLng = selectedTask.longitude.toFloat()
                            zoomLevel = 1.4f
                        } else if (tasks.isNotEmpty()) {
                            centerLat = tasks.first().latitude.toFloat()
                            centerLng = tasks.first().longitude.toFloat()
                        }
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Merkeze Odakla")
                }
            }
        }

        // Map Top Status Badge
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            shadowElevation = 3.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF10B981), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${tasks.size} Konum Görevi Aktif",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Bottom Selected Place Quick Action Card
        if (selectedTask != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = getCategoryColor(selectedTask.category).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = selectedTask.category,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = getCategoryColor(selectedTask.category),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "⭕ ${selectedTask.radiusMeters}m Çap",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (selectedTask.isNotificationTriggered) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = Color(0xFF059669),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Vardınız",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF059669),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = selectedTask.placeName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "📋 ${selectedTask.taskDescription}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (selectedTask.address.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "📍 ${selectedTask.address}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSimulateArrival(selectedTask) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Varışı Test Et", fontSize = 13.sp)
                        }

                        FilledTonalButton(
                            onClick = { openGoogleMaps(context, selectedTask) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Google Maps", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Draws stylized vector map elements: Bosphorus / Water channel, roads, park greenery
 */
private fun DrawScope.drawMapTerrain(
    w: Float,
    h: Float,
    centerLat: Float,
    centerLng: Float,
    zoom: Float
) {
    // Water background (Bosphorus or sea accent)
    val waterColor = Color(0xFFBAE6FD)
    val landColor = Color(0xFFF1F5F9)
    val roadColor = Color(0xFFFFFFFF)
    val parkGreenColor = Color(0xFFDCFCE7)

    // Fill base land
    drawRect(color = landColor, size = Size(w, h))

    // Stylized Water Channel (diagonal flow representing coastal/river area)
    val waterPath = Path().apply {
        moveTo(w * 0.45f, 0f)
        cubicTo(
            w * 0.55f, h * 0.35f,
            w * 0.35f, h * 0.70f,
            w * 0.60f, h
        )
        lineTo(w * 0.78f, h)
        cubicTo(
            w * 0.50f, h * 0.70f,
            w * 0.70f, h * 0.35f,
            w * 0.60f, 0f
        )
        close()
    }
    drawPath(path = waterPath, color = waterColor)

    // Green Park zones
    drawRoundRect(
        color = parkGreenColor,
        topLeft = Offset(w * 0.12f, h * 0.18f),
        size = Size(w * 0.28f, h * 0.22f),
        cornerRadius = CornerRadius(24f, 24f)
    )
    drawRoundRect(
        color = parkGreenColor,
        topLeft = Offset(w * 0.68f, h * 0.55f),
        size = Size(w * 0.25f, h * 0.25f),
        cornerRadius = CornerRadius(24f, 24f)
    )

    // Arterial Road network
    val roadStroke = 7f * zoom.coerceIn(0.8f, 1.8f)
    val secondaryRoadStroke = 3.5f * zoom.coerceIn(0.8f, 1.5f)

    // Major Highways
    drawLine(
        color = roadColor,
        start = Offset(0f, h * 0.38f),
        end = Offset(w, h * 0.42f),
        strokeWidth = roadStroke
    )
    drawLine(
        color = roadColor,
        start = Offset(0f, h * 0.72f),
        end = Offset(w, h * 0.68f),
        strokeWidth = roadStroke
    )
    drawLine(
        color = roadColor,
        start = Offset(w * 0.28f, 0f),
        end = Offset(w * 0.32f, h),
        strokeWidth = roadStroke
    )
    drawLine(
        color = roadColor,
        start = Offset(w * 0.82f, 0f),
        end = Offset(w * 0.85f, h),
        strokeWidth = roadStroke
    )

    // Secondary street grid
    for (i in 1..5) {
        drawLine(
            color = roadColor.copy(alpha = 0.75f),
            start = Offset(0f, h * (i * 0.16f)),
            end = Offset(w, h * (i * 0.16f)),
            strokeWidth = secondaryRoadStroke
        )
        drawLine(
            color = roadColor.copy(alpha = 0.75f),
            start = Offset(w * (i * 0.18f), 0f),
            end = Offset(w * (i * 0.18f), h),
            strokeWidth = secondaryRoadStroke
        )
    }
}

fun getCategoryColor(category: String): Color {
    return when (category) {
        "İşyeri" -> Color(0xFF2563EB) // Blue
        "Park" -> Color(0xFF059669) // Emerald
        "Tiyatro / Kültür" -> Color(0xFF7C3AED) // Violet
        "Market" -> Color(0xFFEA580C) // Orange
        "Kafe / Restoran" -> Color(0xFFD97706) // Amber
        else -> Color(0xFF0D9488) // Teal
    }
}

private fun openGoogleMaps(context: Context, task: TaskLocationEntity) {
    try {
        val uri = Uri.parse("geo:${task.latitude},${task.longitude}?q=${task.latitude},${task.longitude}(${Uri.encode(task.placeName)})")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            // Fallback to web browser
            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${task.latitude},${task.longitude}")
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    } catch (e: Exception) {
        val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${task.latitude},${task.longitude}")
        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
    }
}
