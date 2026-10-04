package com.example.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.TaskLocationEntity
import java.util.Locale

object NotificationHelper {

    const val CHANNEL_ID_REMINDERS = "location_task_reminders_v2"
    const val CHANNEL_ID_SERVICE = "location_monitor_service_v2"
    const val NOTIFICATION_ID_SERVICE = 1001

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // High-priority arrival reminder channel (heads-up popup, sound, vibration)
            val reminderChannel = NotificationChannel(
                CHANNEL_ID_REMINDERS,
                "Konum Görev Hatırlatıcıları (Öncelikli)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Hedef konuma yaklaşıldığında veya varıldığında tetiklenen yüksek öncelikli görev uyarıları"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 350, 150, 350)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            // High-priority foreground tracking service channel (ensures persistent visibility & background accuracy)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "Konum Takip Servisi (Sessiz)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Arka planda kesintisiz konum izleme ve görev yakınlık kontrolü"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(reminderChannel)
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * High-priority heads-up notification triggered when the user enters the task's geofence radius.
     */
    fun showArrivalNotification(
        context: Context,
        task: TaskLocationEntity,
        distanceMeters: Int? = null
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to open task in app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_TASK_ID", task.id)
            putExtra("EXTRA_SHOW_MAP", true)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            task.id.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Complete task directly from notification
        val completeIntent = Intent(context, LocationMonitorService::class.java).apply {
            action = LocationMonitorService.ACTION_COMPLETE_TASK
            putExtra(LocationMonitorService.EXTRA_TASK_ID, task.id)
        }
        val completePendingIntent = PendingIntent.getService(
            context,
            (3000 + task.id).toInt(),
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priorityText = when (task.priority.uppercase(Locale.ROOT)) {
            "HIGH" -> "🚨 Yüksek Öncelik"
            "LOW" -> "🟢 Düşük Öncelik"
            else -> "🟡 Orta Öncelik"
        }

        val distanceText = if (distanceMeters != null) " (~${distanceMeters}m)" else ""

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("📍 ${task.displayTitle} konumuna vardınız!$distanceText")
            .setContentText("$priorityText • ${task.displayDescription}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "$priorityText\n" +
                        "📋 Görev: ${task.displayDescription}\n" +
                        "📍 Konum: ${task.placeName}\n" +
                        "🏷️ Kategori: ${task.category}\n" +
                        "📌 Adres: ${task.address.ifBlank { "Harita koordinatı (${task.latitude}, ${task.longitude})" }}\n" +
                        "⭕ Yarıçap: ${task.radiusMeters} metre"
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.checkbox_on_background,
                "Görevi Tamamla",
                completePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_mapmode,
                "Haritada Gör",
                openPendingIntent
            )
            .build()

        notificationManager.notify((2000 + task.id).toInt(), notification)
    }

    /**
     * High-priority persistent foreground notification showing live tracking status,
     * monitored task count, current coordinates, nearest task, and a Stop action button.
     */
    fun buildForegroundNotification(
        context: Context,
        activeTaskCount: Int,
        currentLocation: Location? = null,
        nearestTaskName: String? = null,
        nearestTaskDistanceMeters: Int? = null
    ): android.app.Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Stop Service
        val stopIntent = Intent(context, LocationMonitorService::class.java).apply {
            action = LocationMonitorService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "📍 Konum Takibi Aktif (Yüksek Öncelik)"
        val summaryText = when {
            nearestTaskName != null && nearestTaskDistanceMeters != null ->
                "$activeTaskCount aktif görev • En yakın: $nearestTaskName (${nearestTaskDistanceMeters}m)"
            activeTaskCount > 0 ->
                "$activeTaskCount konum görevi izleniyor • Tetikleme hazır"
            else ->
                "Arka planda konum izleniyor • Yeni görevler bekleniyor"
        }

        val bigText = buildString {
            append("Arka Planda Yüksek Öncelikli Konum Servisi Çalışıyor\n")
            append("• İzlenen Görev: $activeTaskCount adet aktif konum\n")
            if (currentLocation != null) {
                append("• Koordinat: ${String.format(Locale.US, "%.4f", currentLocation.latitude)}, ${String.format(Locale.US, "%.4f", currentLocation.longitude)}\n")
            }
            if (nearestTaskName != null && nearestTaskDistanceMeters != null) {
                append("• En Yakın Görev: $nearestTaskName (~${nearestTaskDistanceMeters}m)\n")
            }
            append("• Mod: Gerçek zamanlı tetikleme (Yüksek Doğruluk)")
        }

        return NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(summaryText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Durdur",
                stopPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_compass,
                "Uygulamayı Aç",
                openPendingIntent
            )
            .build()
    }
}
