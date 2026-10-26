package com.example.simplesystemui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.example.simplesystemui.platform.PlatformWindowApi
import com.example.simplesystemui.platform.BackAccessibilityService
import com.example.simplesystemui.platform.CommandQueue
import com.example.simplesystemui.platform.NotificationAccess
import com.example.simplesystemui.window.SystemWindowHost

/** Service 负责窗口、设备监听和通知的生命周期。 */
class SystemUIService : Service() {
    private val app get() = application as SystemUIApplication
    private lateinit var controller: SystemUiController
    private lateinit var windows: SystemWindowHost
    private var commandQueue: CommandQueue? = null
    private var notificationAccess: NotificationAccess? = null
    private var ready = false
    private var failure: String? = null

    override fun onCreate() {
        super.onCreate()
        try {
            startForegroundNotification()
            if (BuildConfig.SYSTEM_UI) {
                PlatformWindowApi.requireSystemEnvironment(this)
            } else {
                check(Settings.canDrawOverlays(this)) { "请先在控制台授权悬浮窗" }
            }
            controller = SystemUiController(this, app.notifications) { stopSelf() }
            windows = SystemWindowHost(this, controller)
            controller.onWindowsChanged = {
                try { windows.render() } catch (error: Exception) { fail(error) }
            }
            controller.runAfterInputReleased = windows::afterInputReleased
            BackAccessibilityService.onConnectionChanged = {
                if (ready) controller.onWindowsChanged()
            }
            windows.attach()
            controller.start()
            app.notifications.start()
            notificationAccess = NotificationAccess(this, app.notifications).also { it.start() }
            commandQueue = CommandQueue(this, controller, ::fail)
            controller.onSystemBarInteraction = { commandQueue?.touchAutoHide() }
            commandQueue?.start()
            if (failure != null) return
            ready = true
            val mode = if (BuildConfig.SYSTEM_UI) "系统原生 SystemUI 窗口" else "普通应用悬浮窗"
            app.reportStatus(ServiceStatus(true, "$mode 已显示：状态栏 / 导航栏 / 快捷面板"))
        } catch (error: Exception) {
            fail(error)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!ready) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_OPEN_SHADE -> controller.openShade()
            ACTION_OPEN_RECENTS -> controller.openRecents()
            ACTION_FOCUS_NAVIGATION -> controller.focusNavigation()
            ACTION_RELEASE_FOCUS -> controller.releaseFocus()
        }
        // 窗口被设备原有 SystemUI 占用时，不自动反复重启。
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (ready) try { windows.recreate() } catch (error: Exception) { fail(error) }
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
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(100, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(100, notification)
        }
    }

    private fun fail(error: Throwable) {
        if (failure != null) return
        ready = false
        failure = error.message ?: error.javaClass.simpleName
        Log.e(TAG, "SystemUI startup/window failure", error)
        app.reportStatus(ServiceStatus(false, failure!!))
        Toast.makeText(this, failure, Toast.LENGTH_LONG).show()
        stopSelf()
    }

    override fun onDestroy() {
        ready = false
        BackAccessibilityService.onConnectionChanged = null
        commandQueue?.close()
        commandQueue = null
        notificationAccess?.close()
        notificationAccess = null
        if (::controller.isInitialized) controller.close()
        if (::windows.isInitialized) windows.close()
        app.notifications.stop()
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
        const val ACTION_OPEN_RECENTS = "com.example.simplesystemui.OPEN_RECENTS"
        const val ACTION_FOCUS_NAVIGATION = "com.example.simplesystemui.FOCUS_NAVIGATION"
        const val ACTION_RELEASE_FOCUS = "com.example.simplesystemui.RELEASE_FOCUS"
    }
}
