package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.localization.LocalizedStrings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyTasksView(
    strings: LocalizedStrings,
    onAddNewTask: () -> Unit,
    onAddSampleTask: (title: String, desc: String, priority: String, place: String, category: String, lat: Double, lng: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("empty_state_view")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Friendly Animated Radar / Map Pin Graphic
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val tertiaryColor = MaterialTheme.colorScheme.tertiary

                Canvas(modifier = Modifier.size(130.dp)) {
                    val center = this.center
                    // Outer pulsing ring
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.15f),
                        radius = (size.minDimension / 2f) * pulseScale,
                        style = Stroke(width = 4.dp.toPx())
                    )
                    // Middle radar ring
                    drawCircle(
                        color = tertiaryColor.copy(alpha = 0.2f),
                        radius = (size.minDimension / 2.7f)
                    )
                    // Inner subtle ring
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.08f),
                        radius = (size.minDimension / 1.8f)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = strings.emptyTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = strings.emptySubtitle,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Add Button
            Button(
                onClick = onAddNewTask,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_empty_add_task")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.emptyAction,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Starter Suggestions
            Text(
                text = "Hızlı Başlangıç Önerileri",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SuggestionChip(
                    onClick = {
                        onAddSampleTask(
                            "Tiyatro Biletleri Teslimi",
                            "Gişeden rezerve tiyatro biletlerini teslim al",
                            "HIGH",
                            "Kadıköy Süreyya Tiyatrosu",
                            "Tiyatro / Kültür",
                            40.9897,
                            29.0289
                        )
                    },
                    label = { Text("🎭 Tiyatro Bileti (Yüksek)") },
                    shape = RoundedCornerShape(12.dp)
                )

                SuggestionChip(
                    onClick = {
                        onAddSampleTask(
                            "İş Toplantı Evrakları",
                            "Proje belgelerini danışmaya teslim et ve imzalat",
                            "HIGH",
                            "Maslak Ofis Kuleleri",
                            "İşyeri",
                            41.1118,
                            29.0211
                        )
                    },
                    label = { Text("🏢 İş Maslak (Yüksek)") },
                    shape = RoundedCornerShape(12.dp)
                )

                SuggestionChip(
                    onClick = {
                        onAddSampleTask(
                            "Park Yürüyüşü ve Mola",
                            "Göl kenarında 30 dk yürüyüş yap",
                            "MEDIUM",
                            "Emirgan Parkı & Korusu",
                            "Park",
                            41.1084,
                            29.0543
                        )
                    },
                    label = { Text("🌲 Emirgan Parkı (Orta)") },
                    shape = RoundedCornerShape(12.dp)
                )

                SuggestionChip(
                    onClick = {
                        onAddSampleTask(
                            "Haftalık Organik Pazar",
                            "Taze meyve, zeytin ve peynir al",
                            "LOW",
                            "Kadıköy Çarşı Pazarı",
                            "Market",
                            40.9902,
                            29.0255
                        )
                    },
                    label = { Text("🛒 Çarşı & Pazar (Düşük)") },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}
