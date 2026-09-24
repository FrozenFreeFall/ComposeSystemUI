package com.example.simplesystemui.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DeviceState(
    val time: String = "--:--",
    val date: String = "",
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    val wifiEnabled: Boolean? = null,
    val brightness: Int = 128,
)

/** Application context only. Receivers live exactly as long as the Flow collection. */
class DeviceRepository(context: Context) {
    private val app = context.applicationContext
    private val wifi = app.getSystemService(WifiManager::class.java)

    fun observe(): Flow<DeviceState> = callbackFlow {
        var battery: Intent? = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        fun publish() {
            val now = LocalDateTime.now()
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val present = battery?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false) == true
            val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            trySend(DeviceState(
                time = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                date = now.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
                batteryPercent = if (present && level >= 0 && scale > 0) (100 * level / scale).coerceIn(0, 100) else null,
                charging = plugged != 0,
                wifiEnabled = runCatching { wifi?.isWifiEnabled }.getOrNull(),
                brightness = Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128),
            ))
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) battery = intent
                publish()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        // All actions above are protected system broadcasts; no custom exported actions.
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { publish() }
        }
        try {
            app.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, observer)
            publish()
            awaitClose { }
        } finally {
            app.unregisterReceiver(receiver)
            app.contentResolver.unregisterContentObserver(observer)
        }
    }
}
