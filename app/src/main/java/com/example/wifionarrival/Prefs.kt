package com.example.wifionarrival

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("wifi_on_arrival", Context.MODE_PRIVATE)

    var targetLat: Double
        get() = Double.fromBits(sp.getLong("lat", 35.681236.toBits()))
        set(v) = sp.edit().putLong("lat", v.toBits()).apply()

    var targetLng: Double
        get() = Double.fromBits(sp.getLong("lng", 139.767125.toBits()))
        set(v) = sp.edit().putLong("lng", v.toBits()).apply()

    var radiusMeters: Float
        get() = sp.getFloat("radius", 50f)
        set(v) = sp.edit().putFloat("radius", v).apply()

    var intervalSec: Int
        get() = sp.getInt("interval", 30)
        set(v) = sp.edit().putInt("interval", v.coerceIn(5, 600)).apply()

    var startHour: Int
        get() = sp.getInt("start_h", 7)
        set(v) = sp.edit().putInt("start_h", v.coerceIn(0, 23)).apply()

    var startMin: Int
        get() = sp.getInt("start_m", 0)
        set(v) = sp.edit().putInt("start_m", v.coerceIn(0, 59)).apply()

    var endHour: Int
        get() = sp.getInt("end_h", 22)
        set(v) = sp.edit().putInt("end_h", v.coerceIn(0, 23)).apply()

    var endMin: Int
        get() = sp.getInt("end_m", 0)
        set(v) = sp.edit().putInt("end_m", v.coerceIn(0, 59)).apply()

    var monitoring: Boolean
        get() = sp.getBoolean("monitoring", false)
        set(v) = sp.edit().putBoolean("monitoring", v).apply()
}
