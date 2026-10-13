package com.example.simplesystemui.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
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

/** Service 创建时监听，Service 销毁时取消；系统回调都在主线程执行。 */
class DeviceRepository(context: Context) {
    private val app = context.applicationContext
    private val wifi = app.getSystemService(WifiManager::class.java)
    private var onChanged: ((DeviceState) -> Unit)? = null
    private var battery: Intent? = null
    private var receiverRegistered = false
    private var observerRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) battery = intent
            publish()
        }
    }
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            publish()
        }
    }

    fun start(onChanged: (DeviceState) -> Unit) {
        this.onChanged = onChanged
        if (receiverRegistered) {
            publish()
            return
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        try {
            // 这里只接收受保护的系统广播，没有应用自定义广播。
            battery = ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            receiverRegistered = true
            app.contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, observer,
            )
            observerRegistered = true
            publish()
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    private fun publish() {
        val callback = onChanged ?: return
        val now = LocalDateTime.now()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val present = battery?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false) == true
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        callback(DeviceState(
            time = now.format(DateTimeFormatter.ofPattern("HH:mm")),
            date = now.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
            batteryPercent = if (present && level >= 0 && scale > 0) (100 * level / scale).coerceIn(0, 100) else null,
            charging = plugged != 0,
            wifiEnabled = runCatching { wifi?.isWifiEnabled }.getOrNull(),
            brightness = Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128),
        ))
    }

    fun close() {
        onChanged = null
        if (receiverRegistered) runCatching { app.unregisterReceiver(receiver) }
        if (observerRegistered) runCatching { app.contentResolver.unregisterContentObserver(observer) }
        receiverRegistered = false
        observerRegistered = false
        battery = null
    }
}
