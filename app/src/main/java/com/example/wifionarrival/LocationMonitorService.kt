package com.example.wifionarrival

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Calendar

class LocationMonitorService : Service(), LocationListener {

    companion object {
        const val CHANNEL_ID = "location_monitor"
        const val NOTIF_ID = 1001
        const val ACTION_STATUS = "com.example.wifionarrival.STATUS"
        const val EXTRA_STATUS = "status"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        const val EXTRA_DIST = "dist"
        private const val TAG = "WifiOnArrival"
    }

    private lateinit var prefs: Prefs
    private var locationManager: LocationManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var wifiTriggered = false

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!prefs.monitoring) return
            if (isInActiveTimeWindow()) {
                requestOneLocation()
            } else {
                updateNotification("時間外のためGPS停止中")
                broadcastStatus("時間外")
            }
            handler.postDelayed(this, prefs.intervalSec * 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        createChannel()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        prefs.monitoring = true
        wifiTriggered = false
        val notification = buildNotification("監視開始…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        handler.removeCallbacks(checkRunnable)
        handler.post(checkRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        prefs.monitoring = false
        handler.removeCallbacks(checkRunnable)
        try {
            locationManager?.removeUpdates(this)
        } catch (_: SecurityException) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun isInActiveTimeWindow(): Boolean {
        val cal = Calendar.getInstance()
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = prefs.startHour * 60 + prefs.startMin
        val end = prefs.endHour * 60 + prefs.endMin
        return if (start <= end) now in start until end else (now >= start || now < end)
    }

    private fun requestOneLocation() {
        try {
            val lm = locationManager ?: return
            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                updateNotification("位置情報プロバイダが無効")
                broadcastStatus("GPS無効")
                return
            }
            lm.requestSingleUpdate(provider, this, Looper.getMainLooper())
            updateNotification("位置取得中…")
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing", e)
            updateNotification("位置情報の権限がありません")
            broadcastStatus("権限なし")
        }
    }

    override fun onLocationChanged(location: Location) {
        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude, location.longitude,
            prefs.targetLat, prefs.targetLng, results
        )
        val dist = results[0]
        val msg = "距離: ${"%.1f".format(dist)} m"
        updateNotification(msg)
        broadcastStatus(msg, location.latitude, location.longitude, dist)

        if (dist <= prefs.radiusMeters) {
            if (!wifiTriggered) {
                wifiTriggered = true
                enableWifi()
                updateNotification("到着！WiFi ON 試行 (${"%.0f".format(dist)}m)")
            }
        } else if (dist > prefs.radiusMeters * 1.5f) {
            wifiTriggered = false
        }
    }

    @Deprecated("Deprecated in API")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    private fun enableWifi() {
        try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (wifi.isWifiEnabled) return
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                wifi.setWifiEnabled(true)
            } else {
                // Android 10+ ではアプリからWiFiを直接ONにできないため設定パネルを開く
                try {
                    startActivity(
                        Intent(Settings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                    startActivity(
                        Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable WiFi", e)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.channel_desc) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_running))
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(content: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(content))
    }

    private fun broadcastStatus(
        status: String,
        lat: Double = Double.NaN,
        lng: Double = Double.NaN,
        dist: Float = Float.NaN
    ) {
        sendBroadcast(Intent(ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATUS, status)
            if (!lat.isNaN()) putExtra(EXTRA_LAT, lat)
            if (!lng.isNaN()) putExtra(EXTRA_LNG, lng)
            if (!dist.isNaN()) putExtra(EXTRA_DIST, dist)
        })
    }
}
