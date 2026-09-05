package com.example.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.TaskLocationEntity

object NotificationHelper {

    const val CHANNEL_ID_REMINDERS = "location_task_reminders"
    const val CHANNEL_ID_SERVICE = "location_monitor_service"
    const val NOTIFICATION_ID_SERVICE = 1001

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Heads-up reminder channel
            val reminderChannel = NotificationChannel(
                CHANNEL_ID_REMINDERS,
                "Konum Görev Hatırlatıcıları",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Konuma vardığınızda tetiklenen görev hatırlatmaları"
                enableVibration(true)
                setShowBadge(true)
            }

            // Foreground service channel
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "Konum Takip Servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Arka planda konum takip durumu"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(reminderChannel)
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    fun showArrivalNotification(context: Context, task: TaskLocationEntity) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_TASK_ID", task.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("📍 ${task.placeName} konumuna vardınız!")
            .setContentText("Görev: ${task.taskDescription}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Görev: ${task.taskDescription}\nAdres: ${task.address.ifBlank { "Google Maps konumu" }}\nKategori: ${task.category}")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify((2000 + task.id).toInt(), notification)
    }

    fun buildForegroundNotification(context: Context, activeTaskCount: Int): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Konum Takibi Aktif")
            .setContentText(
                if (activeTaskCount > 0)
                    "$activeTaskCount konum için varış bildirimi bekleniyor"
                else
                    "Arka planda konum izleniyor"
            )
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
