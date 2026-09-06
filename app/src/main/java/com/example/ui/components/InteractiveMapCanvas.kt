package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TaskLocationEntity
import com.example.location.LocationHelper
import com.example.ui.localization.LocalizedStrings
import kotlin.math.roundToInt

@Composable
fun InteractiveMapCanvas(
    tasks: List<TaskLocationEntity>,
    selectedTask: TaskLocationEntity?,
    onTaskSelected: (TaskLocationEntity) -> Unit,
    onMapTappedCoordinates: ((Double, Double) -> Unit)? = null,
    onSimulateArrival: (TaskLocationEntity) -> Unit,
    currentUserLocation: Location? = null,
    proximityThresholdMeters: Int = 1000,
    nearbyTasks: List<TaskLocationEntity> = emptyList(),
    routeTargetTask: TaskLocationEntity? = null,
    showNearbyAlert: Boolean = true,
    onDismissNearbyAlert: () -> Unit = {},
    onSelectRouteTask: (TaskLocationEntity) -> Unit = {},
    strings: LocalizedStrings? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Effective current user coordinates (Istanbul Kadıköy default if GPS is not yet acquired)
    val userLat = currentUserLocation?.latitude ?: 40.9915
    val userLng = currentUserLocation?.longitude ?: 29.0275

    // Reference center coordinates (Istanbul / Region focal point)
    var centerLat by remember { mutableFloatStateOf(userLat.toFloat()) }
    var centerLng by remember { mutableFloatStateOf(userLng.toFloat()) }
    var zoomLevel by remember { mutableFloatStateOf(1.2f) }

    // Active task for routing and display
    val activeRouteTarget = routeTargetTask ?: nearbyTasks.firstOrNull() ?: selectedTask

    // Index of the active nearby task when cycling through multiple
    var activeNearbyIndex by remember { mutableIntStateOf(0) }

    // When activeRouteTarget changes, ensure activeNearbyIndex stays in bounds
    LaunchedEffect(nearbyTasks, activeRouteTarget?.id) {
        if (activeRouteTarget != null && nearbyTasks.isNotEmpty()) {
            val idx = nearbyTasks.indexOfFirst { it.id == activeRouteTarget.id }
            if (idx >= 0) {
                activeNearbyIndex = idx
            }
        }
    }

    // Auto-focus on route between user location and target task
    LaunchedEffect(activeRouteTarget?.id) {
        if (activeRouteTarget != null) {
            centerLat = ((userLat + activeRouteTarget.latitude) / 2.0).toFloat()
            centerLng = ((userLng + activeRouteTarget.longitude) / 2.0).toFloat()
            val dist = LocationHelper.calculateDistanceMeters(userLat, userLng, activeRouteTarget.latitude, activeRouteTarget.longitude)
            zoomLevel = when {
                dist < 500 -> 1.8f
                dist < 1500 -> 1.4f
                dist < 3000 -> 1.1f
                else -> 0.9f
            }
        }
    }

    // Pulse animation for geofences and radar
    val infiniteTransition = rememberInfiniteTransition(label = "mapAnimations")
    val pulseRatio by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseRadius"
    )

    // Animated dashed flow effect along the route line
    val routePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -32f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "routePhase"
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

                        var clickedTask: TaskLocationEntity? = null
                        for (task in tasks) {
                            val pinX = ((task.longitude.toFloat() - (centerLng - lngSpan / 2)) / lngSpan) * w
                            val pinY = (((centerLat + latSpan / 2) - task.latitude.toFloat()) / latSpan) * h
                            val dx = tapOffset.x - pinX
                            val dy = tapOffset.y - pinY
                            if (dx * dx + dy * dy <= 45f * 45f) {
                                clickedTask = task
                                break
                            }
                        }

                        if (clickedTask != null) {
                            onTaskSelected(clickedTask)
                            onSelectRouteTask(clickedTask)
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

            // 3. User Location Coordinates
            val userX = ((userLng.toFloat() - minLng) / lngSpan) * width
            val userY = ((maxLat - userLat.toFloat()) / latSpan) * height

            // 4. Draw Route between User Location and Active Target Task
            if (activeRouteTarget != null) {
                val targetX = ((activeRouteTarget.longitude.toFloat() - minLng) / lngSpan) * width
                val targetY = ((maxLat - activeRouteTarget.latitude.toFloat()) / latSpan) * height

                val routePath = Path().apply {
                    moveTo(userX, userY)
                    // Cubic bezier curve simulating natural road navigation
                    val dx = targetX - userX
                    val dy = targetY - userY
                    val cp1X = userX + dx * 0.40f
                    val cp1Y = userY + dy * 0.05f
                    val cp2X = userX + dx * 0.60f
                    val cp2Y = userY + dy * 0.95f
                    cubicTo(cp1X, cp1Y, cp2X, cp2Y, targetX, targetY)
                }

                // Outer route glow / shadow
                drawPath(
                    path = routePath,
                    color = Color(0xFF1E3A8A).copy(alpha = 0.40f),
                    style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Main route line (Primary Blue)
                drawPath(
                    path = routePath,
                    color = Color(0xFF2563EB),
                    style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Animated directional dashes flowing toward destination
                drawPath(
                    path = routePath,
                    color = Color(0xFFBAE6FD),
                    style = Stroke(
                        width = 3.5f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), routePhase)
                    )
                )

                // Route intermediate waypoint nodes
                val waypoints = listOf(0.25f, 0.50f, 0.75f)
                for (t in waypoints) {
                    val wpX = userX + (targetX - userX) * t
                    val wpY = userY + (targetY - userY) * t
                    drawCircle(color = Color.White, radius = 5f, center = Offset(wpX, wpY))
                    drawCircle(color = Color(0xFF1D4ED8), radius = 3.5f, center = Offset(wpX, wpY))
                }
            }

            // 5. Draw Geofence Radii and Pins for All Tasks
            for (task in tasks) {
                val pinX = ((task.longitude.toFloat() - minLng) / lngSpan) * width
                val pinY = ((maxLat - task.latitude.toFloat()) / latSpan) * height

                val isTarget = activeRouteTarget?.id == task.id
                val isSelected = selectedTask?.id == task.id || isTarget
                val radiusPx = (task.radiusMeters / 15f) * zoomLevel * 3.5f
                val pinColor = getCategoryColor(task.category)

                // Geofence Circle fill & border
                drawCircle(
                    color = pinColor.copy(alpha = if (isSelected) 0.25f else 0.10f),
                    radius = if (isSelected) radiusPx * pulseRatio else radiusPx,
                    center = Offset(pinX, pinY)
                )

                drawCircle(
                    color = pinColor.copy(alpha = if (isSelected) 0.9f else 0.4f),
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

            // 6. Draw User Current Location Marker
            // Outer pulsing radar ring
            drawCircle(
                color = Color(0xFF3B82F6).copy(alpha = 0.20f),
                radius = 36f * pulseRatio,
                center = Offset(userX, userY)
            )
            drawCircle(
                color = Color(0xFF2563EB).copy(alpha = 0.55f),
                radius = 36f * pulseRatio,
                center = Offset(userX, userY),
                style = Stroke(width = 2f)
            )
            // Center location dot
            drawCircle(
                color = Color.White,
                radius = 13f,
                center = Offset(userX, userY)
            )
            drawCircle(
                color = Color(0xFF2563EB),
                radius = 9f,
                center = Offset(userX, userY)
            )
            drawCircle(
                color = Color.White,
                radius = 3.5f,
                center = Offset(userX, userY)
            )
        }

        // Map Control Floating Buttons (Zoom In, Zoom Out, Center on Route)
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
                        if (activeRouteTarget != null) {
                            centerLat = ((userLat + activeRouteTarget.latitude) / 2.0).toFloat()
                            centerLng = ((userLng + activeRouteTarget.longitude) / 2.0).toFloat()
                            zoomLevel = 1.4f
                        } else {
                            centerLat = userLat.toFloat()
                            centerLng = userLng.toFloat()
                            zoomLevel = 1.3f
                        }
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Konum ve Rotaya Odakla")
                }
            }
        }

        // Map Top Status & Proximity Badge
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(
                            if (nearbyTasks.isNotEmpty()) Color(0xFFEF4444) else Color(0xFF10B981),
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "${tasks.size} Görev Aktif",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (nearbyTasks.isNotEmpty()) {
                        Text(
                            text = "🚨 ${nearbyTasks.size} Görev Yakında (${if (proximityThresholdMeters >= 1000) "${proximityThresholdMeters / 1000} km" else "$proximityThresholdMeters m"})",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            text = "Yakınlık eşiği: ${if (proximityThresholdMeters >= 1000) "${proximityThresholdMeters / 1000} km" else "$proximityThresholdMeters m"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Minimized Floating Route Pill (when user dismissed the main card)
        if (!showNearbyAlert && activeRouteTarget != null) {
            val dist = LocationHelper.calculateDistanceMeters(userLat, userLng, activeRouteTarget.latitude, activeRouteTarget.longitude)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .clickable { onSelectRouteTask(activeRouteTarget) },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Route,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🚨 ${activeRouteTarget.displayTitle} (${LocationHelper.formatDistance(dist)}) - Yolu Göster",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Prominent Nearby Task Alert & Route Navigation Card
        // ("Harita seçildiğinde 1 km yakınında yapmak istenilen bir şey varsa ekrana çıksın ve yolu göstersin")
        if (showNearbyAlert && activeRouteTarget != null) {
            val distanceMeters = LocationHelper.calculateDistanceMeters(
                userLat,
                userLng,
                activeRouteTarget.latitude,
                activeRouteTarget.longitude
            )
            val isWithinProximity = distanceMeters <= proximityThresholdMeters
            val estWalkMinutes = ((distanceMeters / 75f).roundToInt()).coerceAtLeast(1)

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("nearby_task_route_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    // Alert Header with Proximity Tag, Priority and Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isWithinProximity) Color(0xFFDC2626).copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isWithinProximity) Icons.Default.NearMe else Icons.Default.Route,
                                        contentDescription = null,
                                        tint = if (isWithinProximity) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isWithinProximity) {
                                            "🚨 ${LocationHelper.formatDistance(distanceMeters)} Yakınınızda Yapılacak İş Var"
                                        } else {
                                            "📍 ${LocationHelper.formatDistance(distanceMeters)} Mesafede"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isWithinProximity) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // If multiple nearby tasks exist, allow switching between them
                            if (nearbyTasks.size > 1) {
                                IconButton(
                                    onClick = {
                                        activeNearbyIndex = if (activeNearbyIndex > 0) activeNearbyIndex - 1 else nearbyTasks.size - 1
                                        onSelectRouteTask(nearbyTasks[activeNearbyIndex])
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Önceki")
                                }
                                Text(
                                    text = "${activeNearbyIndex + 1}/${nearbyTasks.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = {
                                        activeNearbyIndex = (activeNearbyIndex + 1) % nearbyTasks.size
                                        onSelectRouteTask(nearbyTasks[activeNearbyIndex])
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Sonraki")
                                }
                            }

                            IconButton(
                                onClick = onDismissNearbyAlert,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Kapat", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Task Title
                    Text(
                        text = activeRouteTarget.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // WHAT NEEDS TO BE DONE (Yapılmak İstenen Şey) - Prominent Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "📋 Yapılacak İş:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = activeRouteTarget.displayDescription,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Place Name & Distance / Walking Estimate Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = getCategoryColor(activeRouteTarget.category).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = activeRouteTarget.category,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = getCategoryColor(activeRouteTarget.category),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = activeRouteTarget.placeName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.DirectionsWalk,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "~$estWalkMinutes dk yürüme",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons (Focus Route, Google Maps Navigation, Test Arrival)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Center Route Button
                        FilledTonalButton(
                            onClick = {
                                centerLat = ((userLat + activeRouteTarget.latitude) / 2.0).toFloat()
                                centerLng = ((userLng + activeRouteTarget.longitude) / 2.0).toFloat()
                                zoomLevel = 1.4f
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Yolu Göster", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        // Google Maps Direct Navigation Button
                        Button(
                            onClick = {
                                openGoogleMapsRoute(
                                    context = context,
                                    originLat = userLat,
                                    originLng = userLng,
                                    destLat = activeRouteTarget.latitude,
                                    destLng = activeRouteTarget.longitude,
                                    destName = activeRouteTarget.placeName
                                )
                            },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Harita ile Git", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        // Test Arrival
                        IconButton(
                            onClick = { onSimulateArrival(activeRouteTarget) },
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = "Varışı Test Et",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Draws stylized vector map elements: Water channel, roads, park greenery
 */
private fun DrawScope.drawMapTerrain(
    w: Float,
    h: Float,
    centerLat: Float,
    centerLng: Float,
    zoom: Float
) {
    val waterColor = Color(0xFFBAE6FD)
    val landColor = Color(0xFFF1F5F9)
    val roadColor = Color(0xFFFFFFFF)
    val parkGreenColor = Color(0xFFDCFCE7)

    // Fill base land
    drawRect(color = landColor, size = Size(w, h))

    // Stylized Water Channel
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
        "İşyeri" -> Color(0xFF2563EB)
        "Park" -> Color(0xFF059669)
        "Tiyatro / Kültür" -> Color(0xFF7C3AED)
        "Market" -> Color(0xFFEA580C)
        "Kafe / Restoran" -> Color(0xFFD97706)
        else -> Color(0xFF0D9488)
    }
}

/**
 * Opens turn-by-turn navigation / route from current location to destination
 */
fun openGoogleMapsRoute(
    context: Context,
    originLat: Double,
    originLng: Double,
    destLat: Double,
    destLng: Double,
    destName: String = ""
) {
    try {
        val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=$originLat,$originLng&destination=$destLat,$destLng&travelmode=walking")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=$originLat,$originLng&destination=$destLat,$destLng&travelmode=walking")
        val browserIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(browserIntent)
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
            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${task.latitude},${task.longitude}")
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    } catch (e: Exception) {
        val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${task.latitude},${task.longitude}")
        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
    }
}
