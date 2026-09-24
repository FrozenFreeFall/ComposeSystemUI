package com.example.simplesystemui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.example.simplesystemui.mvi.*
import com.example.simplesystemui.platform.SystemActionExecutor
import com.example.simplesystemui.platform.PlatformWindowApi
import com.example.simplesystemui.window.SystemWindowHost
import kotlinx.coroutines.*

/** SystemUI entry point. Owns the lifetime of the MVI Store and all WMS windows. */
class SystemUIService : Service() {
    private val app get() = application as SystemUIApplication
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, error -> fail(error) })
    private lateinit var store: SystemUiStore
    private lateinit var windows: SystemWindowHost
    private lateinit var actions: SystemActionExecutor
    private var ready = false
    private var failure: String? = null

    override fun onCreate() {
        super.onCreate()
        try {
            startForegroundNotification()
            PlatformWindowApi.requireSystemEnvironment(this)
            store = SystemUiStore(scope)
            actions = SystemActionExecutor(this)
            windows = SystemWindowHost(this, store)
            windows.attach()
            ready = true
            app.reportStatus(ServiceStatus(true, "系统窗口已挂载：2000 / 2019 / 2040"))
            scope.launch { store.state.collect { windows.render(it) } }
            scope.launch { app.deviceRepository.observe().collect { store.dispatch(SystemUiIntent.DeviceChanged(it)) } }
            scope.launch {
                store.effects.collect { effect ->
                    try {
                        // Commit focus/window changes before executing any platform action.
                        windows.render(store.state.value)
                        when (effect) {
                            is SystemUiEffect.Navigate -> {
                                windows.awaitInputReleased()
                                actions.navigate(effect.action)
                            }
                            is SystemUiEffect.OpenSettings -> {
                                windows.awaitInputReleased()
                                actions.openSettings(effect.page)
                            }
                            is SystemUiEffect.SetWifi -> {
                                val success = withTimeout(10_000) { actions.setWifi(effect.enabled) }
                                store.dispatch(SystemUiIntent.WifiFinished(success))
                            }
                            is SystemUiEffect.SetBrightness -> actions.setBrightness(effect.value)
                            SystemUiEffect.StopService -> stopSelf()
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException && e !is TimeoutCancellationException) throw e
                        if (effect is SystemUiEffect.SetWifi) store.dispatch(SystemUiIntent.WifiFinished(false))
                        if (effect is SystemUiEffect.SetBrightness) {
                            val actual = android.provider.Settings.System.getInt(contentResolver,
                                android.provider.Settings.System.SCREEN_BRIGHTNESS, 128)
                            store.dispatch(SystemUiIntent.BrightnessChanged(actual))
                        }
                        val message = e.cause?.message ?: e.message ?: "系统操作失败"
                        store.dispatch(SystemUiIntent.ReportMessage(message))
                        Toast.makeText(this@SystemUIService, message, Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Effect failed: $effect", e)
                    }
                }
            }
        } catch (e: Exception) { fail(e) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!ready) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_OPEN_SHADE -> store.dispatch(SystemUiIntent.OpenShade)
            ACTION_FOCUS_NAVIGATION -> store.dispatch(SystemUiIntent.FocusNavigation)
            ACTION_RELEASE_FOCUS -> store.dispatch(SystemUiIntent.ReleaseFocus)
        }
        // Do not crash-loop if a vendor SystemUI later reclaims its singleton windows.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (ready) try { windows.recreate() } catch (e: Exception) { fail(e) }
    }

    private fun startForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "SystemUI 窗口服务", NotificationManager.IMPORTANCE_LOW))
        val launcher = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, SystemUIService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher).setContentTitle("SystemUI 窗口服务")
            .setContentText("状态栏 / 导航栏 / 快捷面板正在运行")
            .setContentIntent(launcher).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "停止", stop).build()).build()
        startForeground(100, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun fail(error: Throwable) {
        if (failure != null) return
        failure = error.message ?: error.javaClass.simpleName
        Log.e(TAG, "SystemUI startup/window failure", error)
        app.reportStatus(ServiceStatus(false, failure!!))
        Toast.makeText(this, failure, Toast.LENGTH_LONG).show()
        stopSelf()
    }

    override fun onDestroy() {
        ready = false
        serviceJob.cancel()
        if (::windows.isInitialized) windows.close()
        if (::store.isInitialized) store.close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        app.reportStatus(ServiceStatus(false, failure ?: "服务已停止，三个窗口已释放"))
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SimpleSystemUI"
        private const val CHANNEL = "systemui-windows"
        const val ACTION_START = "com.example.simplesystemui.START"
        const val ACTION_STOP = "com.example.simplesystemui.STOP"
        const val ACTION_OPEN_SHADE = "com.example.simplesystemui.OPEN_SHADE"
        const val ACTION_FOCUS_NAVIGATION = "com.example.simplesystemui.FOCUS_NAVIGATION"
        const val ACTION_RELEASE_FOCUS = "com.example.simplesystemui.RELEASE_FOCUS"
    }
}
