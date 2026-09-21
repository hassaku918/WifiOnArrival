package com.example.wifionarrival

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.wifionarrival.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != LocationMonitorService.ACTION_STATUS) return
            val status = intent.getStringExtra(LocationMonitorService.EXTRA_STATUS) ?: return
            binding.textStatus.text = "状態: $status"
            if (intent.hasExtra(LocationMonitorService.EXTRA_LAT)) {
                val lat = intent.getDoubleExtra(LocationMonitorService.EXTRA_LAT, 0.0)
                val lng = intent.getDoubleExtra(LocationMonitorService.EXTRA_LNG, 0.0)
                val dist = intent.getFloatExtra(LocationMonitorService.EXTRA_DIST, Float.NaN)
                val distStr = if (!dist.isNaN()) " / ${"%.1f".format(dist)}m" else ""
                binding.textLastLocation.text =
                    "最終位置: ${"%.6f".format(lat)}, ${"%.6f".format(lng)}$distStr"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)
        loadPrefsToUi()

        binding.btnSave.setOnClickListener {
            if (saveUiToPrefs()) Toast.makeText(this, "保存しました", Toast.LENGTH_SHORT).show()
        }
        binding.btnStart.setOnClickListener {
            if (!saveUiToPrefs()) return@setOnClickListener
            if (!hasLocationPermission()) {
                requestLocationPermissions()
                return@setOnClickListener
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocationPermission()) {
                requestBackgroundLocation()
                return@setOnClickListener
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF
                )
                return@setOnClickListener
            }
            startMonitor()
        }
        binding.btnStop.setOnClickListener {
            stopService(Intent(this, LocationMonitorService::class.java))
            prefs.monitoring = false
            binding.textStatus.text = "状態: 停止中"
            Toast.makeText(this, "監視を停止しました", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(LocationMonitorService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(statusReceiver, filter)
        }
        if (prefs.monitoring) binding.textStatus.text = "状態: 監視中"
    }

    override fun onStop() {
        try { unregisterReceiver(statusReceiver) } catch (_: Exception) {}
        super.onStop()
    }

    private fun loadPrefsToUi() {
        binding.editLat.setText(prefs.targetLat.toString())
        binding.editLng.setText(prefs.targetLng.toString())
        binding.editRadius.setText(prefs.radiusMeters.toString())
        binding.editInterval.setText(prefs.intervalSec.toString())
        binding.editStartHour.setText(prefs.startHour.toString())
        binding.editStartMin.setText(prefs.startMin.toString())
        binding.editEndHour.setText(prefs.endHour.toString())
        binding.editEndMin.setText(prefs.endMin.toString())
    }

    private fun saveUiToPrefs(): Boolean {
        return try {
            val lat = binding.editLat.text.toString().toDouble()
            val lng = binding.editLng.text.toString().toDouble()
            val radius = binding.editRadius.text.toString().toFloat()
            val interval = binding.editInterval.text.toString().toInt()
            val sh = binding.editStartHour.text.toString().toInt()
            val sm = binding.editStartMin.text.toString().toInt()
            val eh = binding.editEndHour.text.toString().toInt()
            val em = binding.editEndMin.text.toString().toInt()
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) {
                Toast.makeText(this, "緯度・経度が範囲外です", Toast.LENGTH_SHORT).show()
                return false
            }
            if (radius <= 0f) {
                Toast.makeText(this, "半径は正の数にしてください", Toast.LENGTH_SHORT).show()
                return false
            }
            prefs.targetLat = lat
            prefs.targetLng = lng
            prefs.radiusMeters = radius
            prefs.intervalSec = interval
            prefs.startHour = sh
            prefs.startMin = sm
            prefs.endHour = eh
            prefs.endMin = em
            true
        } catch (_: NumberFormatException) {
            Toast.makeText(this, "数値の入力が正しくありません", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun startMonitor() {
        ContextCompat.startForegroundService(
            this, Intent(this, LocationMonitorService::class.java)
        )
        binding.textStatus.text = "状態: 監視中"
        Toast.makeText(this, "監視を開始しました", Toast.LENGTH_SHORT).show()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasBackgroundLocationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        else true

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            REQ_LOCATION
        )
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), REQ_BG_LOCATION
            )
            Toast.makeText(this, "「常に許可」を選択してください", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) requestBackgroundLocation()
                    else startMonitor()
                } else {
                    Toast.makeText(this, "位置情報の許可が必要です", Toast.LENGTH_LONG).show()
                    openAppSettings()
                }
            }
            REQ_BG_LOCATION -> {
                if (hasBackgroundLocationPermission()) startMonitor()
                else {
                    Toast.makeText(this, "バックグラウンド位置情報の許可が必要です", Toast.LENGTH_LONG).show()
                    openAppSettings()
                }
            }
            REQ_NOTIF -> startMonitor()
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        )
    }

    companion object {
        private const val REQ_LOCATION = 100
        private const val REQ_BG_LOCATION = 101
        private const val REQ_NOTIF = 102
    }
}
