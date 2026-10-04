package com.example.location

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.data.AppDatabase
import com.example.data.TaskRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LocationMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var repository: TaskRepository
    private var locationCallback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val db = AppDatabase.getInstance(this)
        repository = TaskRepository(db.taskLocationDao())
        NotificationHelper.createNotificationChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        // A sticky service restart must never re-enable tracking after the user disabled it.
        if (action == ACTION_START && !getSharedPreferences("location_tracking", MODE_PRIVATE).getBoolean("enabled", false)) {
            stopTracking()
            stopSelf()
            _isServiceRunning.value = false
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_STOP -> {
                stopTracking()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                _isServiceRunning.value = false
                return START_NOT_STICKY
            }
            ACTION_COMPLETE_TASK -> {
                val taskId = intent?.getLongExtra(EXTRA_TASK_ID, -1L) ?: -1L
                if (taskId != -1L) {
                    serviceScope.launch {
                        val task = repository.getTaskById(taskId)
                        if (task != null && !task.isCompleted) {
                            repository.toggleCompleted(task)
                            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                            notificationManager.cancel((2000 + taskId).toInt())

                            // Update foreground notification status with new active tasks count
                            _currentLocation.value?.let { loc ->
                                checkTasksProximity(loc)
                            }
                        }
                    }
                }
            }
            ACTION_SIMULATE_ARRIVAL -> {
                val taskId = intent?.getLongExtra(EXTRA_TASK_ID, -1L) ?: -1L
                if (taskId != -1L) {
                    serviceScope.launch {
                        val task = repository.getTaskById(taskId)
                        if (task != null) {
                            NotificationHelper.showArrivalNotification(this@LocationMonitorService, task, 0)
                            repository.markAsNotified(task.id)
                        }
                    }
                }
            }
            ACTION_START -> {
                val notification = NotificationHelper.buildForegroundNotification(this, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        NotificationHelper.NOTIFICATION_ID_SERVICE,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                } else {
                    startForeground(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
                }
                _isServiceRunning.value = true
                startTracking()
            }
        }

        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        val hasFine = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            Log.w(TAG, "Location permissions not granted for background monitoring")
            _isServiceRunning.value = false
            stopTracking()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(3000L)
            .setMinUpdateDistanceMeters(5f)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                _currentLocation.value = location
                checkTasksProximity(location)
            }
        }

        try {
            locationCallback?.let {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    it,
                    Looper.getMainLooper()
                )
            }

            // Continuous proximity check loop every 10 seconds to ensure prompt evaluation
            serviceScope.launch {
                while (isActive) {
                    delay(10000L)
                    _currentLocation.value?.let { loc ->
                        checkTasksProximity(loc)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request location updates", e)
        }
    }

    private fun checkTasksProximity(userLocation: Location) {
        serviceScope.launch {
            try {
                val activeTasks = repository.getActiveTasksList()
                var nearestTaskName: String? = null
                var nearestDistance: Float? = null

                for (task in activeTasks) {
                    val distance = LocationHelper.calculateDistanceMeters(
                        userLocation.latitude,
                        userLocation.longitude,
                        task.latitude,
                        task.longitude
                    )

                    if (nearestDistance == null || distance < nearestDistance) {
                        nearestDistance = distance
                        nearestTaskName = task.displayTitle
                    }

                    // If user is within task's geofence radius
                    if (distance <= task.radiusMeters) {
                        val currentTime = System.currentTimeMillis()
                        // Avoid duplicate spam: allow re-notification after 5 minutes
                        val isRecent = task.lastNotifiedAt?.let { (currentTime - it) < 300_000L } ?: false

                        if (!task.isNotificationTriggered || !isRecent) {
                            NotificationHelper.showArrivalNotification(
                                context = this@LocationMonitorService,
                                task = task,
                                distanceMeters = distance.toInt()
                            )
                            repository.markAsNotified(task.id)
                        }
                    }
                }

                // Update the high-priority foreground notification with live coordinates and nearest task info
                val updatedNotification = NotificationHelper.buildForegroundNotification(
                    context = this@LocationMonitorService,
                    activeTaskCount = activeTasks.size,
                    currentLocation = userLocation,
                    nearestTaskName = nearestTaskName,
                    nearestTaskDistanceMeters = nearestDistance?.toInt()
                )
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NotificationHelper.NOTIFICATION_ID_SERVICE, updatedNotification)

            } catch (e: Exception) {
                Log.e(TAG, "Error checking task proximity", e)
            }
        }
    }

    private fun stopTracking() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
    }

    override fun onDestroy() {
        stopTracking()
        serviceScope.cancel()
        _isServiceRunning.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LocationMonitorService"
        const val ACTION_START = "com.example.action.START_MONITORING"
        const val ACTION_STOP = "com.example.action.STOP_MONITORING"
        const val ACTION_SIMULATE_ARRIVAL = "com.example.action.SIMULATE_ARRIVAL"
        const val ACTION_COMPLETE_TASK = "com.example.action.COMPLETE_TASK"
        const val EXTRA_TASK_ID = "EXTRA_TASK_ID"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        private val _currentLocation = MutableStateFlow<Location?>(null)
        val currentLocation = _currentLocation.asStateFlow()

        fun startService(context: Context) {
            context.getSharedPreferences("location_tracking", Context.MODE_PRIVATE).edit().putBoolean("enabled", true).apply()
            val intent = Intent(context, LocationMonitorService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            context.getSharedPreferences("location_tracking", Context.MODE_PRIVATE).edit().putBoolean("enabled", false).commit()
            val intent = Intent(context, LocationMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun simulateArrival(context: Context, taskId: Long) {
            val intent = Intent(context, LocationMonitorService::class.java).apply {
                action = ACTION_SIMULATE_ARRIVAL
                putExtra(EXTRA_TASK_ID, taskId)
            }
            context.startService(intent)
        }
    }
}
