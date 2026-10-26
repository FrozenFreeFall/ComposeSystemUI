package com.example.simplesystemui

import android.content.Context
import android.app.ActivityOptions
import android.app.PendingIntent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.simplesystemui.data.DeviceRepository
import com.example.simplesystemui.data.DeviceState
import com.example.simplesystemui.data.RecentTask
import com.example.simplesystemui.data.RecentTasksRepository
import com.example.simplesystemui.data.NotificationRepository
import com.example.simplesystemui.platform.SystemActionExecutor
import com.example.simplesystemui.platform.BarAppearanceRegion
import com.example.simplesystemui.platform.SystemBarCallbacks
import com.example.simplesystemui.platform.SystemBarState
import com.example.simplesystemui.platform.SystemStatusIcon
import java.util.concurrent.Executors

enum class InputTarget { APP, NAVIGATION, SHADE, RECENTS }
enum class NavigationAction { BACK, HOME, RECENTS }
enum class SettingsPage { WIFI, BLUETOOTH, DISPLAY, ALL, NOTIFICATIONS }

/** 按钮直接调用方法，Compose 直接读取属性，不需要 Flow、Store 或事件队列。 */
class SystemUiController(
    private val context: Context,
    val notifications: NotificationRepository = NotificationRepository(context),
    private val stopService: () -> Unit,
) : SystemBarCallbacks {
    var systemBars by mutableStateOf(SystemBarState())
        private set
    var statusIcons by mutableStateOf<Map<String, SystemStatusIcon>>(emptyMap())
        private set
    var device by mutableStateOf(DeviceState())
        private set
    var inputTarget by mutableStateOf(InputTarget.APP)
        private set
    var dark by mutableStateOf(true)
        private set
    var brightness by mutableIntStateOf(128)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var recentTasks by mutableStateOf<List<RecentTask>>(emptyList())
        private set
    var recentsLoading by mutableStateOf(false)
        private set
    var recentsError by mutableStateOf<String?>(null)
        private set
    val shadeVisible get() = inputTarget == InputTarget.SHADE
    val recentsVisible get() = inputTarget == InputTarget.RECENTS
    val panelVisible get() = shadeVisible || recentsVisible
    private var systemBarTouchActive = false
    override val transientAutoHideSuspended get() = systemBarTouchActive || inputTarget != InputTarget.APP

    var onWindowsChanged: () -> Unit = {}
    var onSystemBarInteraction: () -> Unit = {}
    fun setSystemBarTouchActive(active: Boolean) {
        systemBarTouchActive = active
        onSystemBarInteraction()
    }
    var runAfterInputReleased: ((() -> Unit) -> Unit) = { it() }
    private val devices = DeviceRepository(context)
    private val actions = SystemActionExecutor(context)
    private val recents = RecentTasksRepository(context)
    private val taskWorker = Executors.newSingleThreadExecutor { Thread(it, "RecentTasks") }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var shadeReturnTarget = InputTarget.APP
    private var recentsReturnTarget = InputTarget.APP
    @Volatile private var recentRequest = 0
    @Volatile
    private var closed = false

    fun start() {
        closed = false
        devices.start {
            device = it
            brightness = it.brightness.coerceIn(10, 255)
        }
    }

    fun openShade() {
        if (closed || !systemBars.shadeEnabled || shadeVisible) return
        notifications.refreshLockState()
        shadeReturnTarget = if (recentsVisible) recentsReturnTarget else inputTarget
        message = null
        changeTarget(InputTarget.SHADE)
    }

    fun closeShade() {
        if (shadeVisible) changeTarget(shadeReturnTarget)
    }

    fun openNotification(key: String) {
        if (closed) return
        notifications.refreshLockState()
        if (notifications.locked) { showMessage("请先解锁后查看通知"); return }
        val pending = notifications.contentIntent(key) ?: return
        afterReleasingInput {
            notifications.refreshLockState()
            if (notifications.locked || notifications.contentIntent(key) != pending) return@afterReleasingInput
            try {
                val options = if (Build.VERSION.SDK_INT >= 34) ActivityOptions.makeBasic().apply {
                    // A deliberate click may open an app after the overlay releases focus.
                    setPendingIntentBackgroundActivityStartMode(if (Build.VERSION.SDK_INT >= 36)
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                    else ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }.toBundle() else null
                pending.send(context, 0, null, null, null, null, options)
                if (notifications.shouldAutoCancel(key) && notifications.contentIntent(key) == pending) {
                    notifications.dismiss(key)
                }
            } catch (_: PendingIntent.CanceledException) {
                showMessage("通知已失效，请等待应用更新通知")
            }
        }
    }

    fun dismissNotification(key: String) { if (!closed) notifications.dismiss(key) }
    fun clearNotifications() { if (!closed) notifications.dismissAll() }
    fun openNotificationAccessSettings() {
        afterReleasingInput { actions.openSettings(SettingsPage.NOTIFICATIONS) }
    }

    fun focusNavigation() {
        if (!systemBars.navigationBarVisible) return
        changeTarget(InputTarget.NAVIGATION)
    }

    fun releaseFocus() {
        shadeReturnTarget = InputTarget.APP
        changeTarget(InputTarget.APP)
    }

    fun backPressed() {
        when {
            shadeVisible -> closeShade()
            recentsVisible -> closeRecents()
            else -> releaseFocus()
        }
    }

    fun edgeBack() {
        if (!systemBars.backEnabled) return
        if (inputTarget == InputTarget.APP) navigate(NavigationAction.BACK) else backPressed()
    }

    fun navigate(action: NavigationAction) {
        if (closed || !when (action) {
            NavigationAction.BACK -> systemBars.backEnabled
            NavigationAction.HOME -> systemBars.homeEnabled
            NavigationAction.RECENTS -> systemBars.recentsEnabled
        }) return
        if (action == NavigationAction.RECENTS && BuildConfig.SYSTEM_UI) {
            if (recentsVisible) closeRecents() else openRecents()
            return
        }
        afterReleasingInput { actions.navigate(action) }
    }

    fun openRecents() {
        if (!BuildConfig.SYSTEM_UI) {
            navigate(NavigationAction.RECENTS)
            return
        }
        if (closed || !systemBars.recentsEnabled || recentsVisible) return
        recentsReturnTarget = if (shadeVisible) shadeReturnTarget else inputTarget
        message = null
        changeTarget(InputTarget.RECENTS)
        refreshRecents()
    }

    fun closeRecents() {
        if (recentsVisible) changeTarget(recentsReturnTarget)
    }

    fun refreshRecents() {
        loadRecentTasks()
    }

    fun removeRecentTask(id: Int) {
        if (recentTasks.none { it.id == id }) return
        loadRecentTasks(removeId = id)
    }

    private fun loadRecentTasks(removeId: Int? = null) {
        if (closed || !recentsVisible || recentsLoading) return
        recentsLoading = true
        recentsError = null
        val request = ++recentRequest
        taskWorker.execute {
            if (closed || request != recentRequest) return@execute
            var removed = false
            val result = runCatching {
                if (removeId != null) {
                    recents.removeTask(removeId)
                    removed = true
                }
                recents.loadTasks()
            }
            mainHandler.post {
                if (closed || request != recentRequest || !recentsVisible) return@post
                recentsLoading = false
                result.onSuccess { recentTasks = it }.onFailure {
                    // A successful removal stays removed even if refreshing the list fails.
                    if (removed) recentTasks = recentTasks.filterNot { task -> task.id == removeId }
                    Log.e("SimpleSystemUI", "最近任务操作失败", it)
                    recentsError = it.cause?.message ?: it.message ?: "最近任务操作失败"
                }
            }
        }
    }

    fun openRecentTask(id: Int) {
        if (closed || !recentsVisible || recentsLoading || recentTasks.none { it.id == id }) return
        afterReleasingInput {
            val request = recentRequest
            taskWorker.execute {
                if (closed || request != recentRequest) return@execute
                runCatching { recents.openTask(id) }.onFailure {
                    Log.e("SimpleSystemUI", "切换任务失败", it)
                    mainHandler.post {
                        if (!closed && request == recentRequest) {
                            showMessage(it.cause?.message ?: it.message ?: "切换任务失败")
                        }
                    }
                }
            }
        }
    }

    fun toggleWifi() {
        if (!systemBars.quickSettingsEnabled) return
        if (!BuildConfig.SYSTEM_UI) {
            openSettings(SettingsPage.WIFI)
            return
        }
        safely {
            val enabled = device.wifiEnabled ?: error("设备没有可用的 Wi-Fi 服务")
            check(actions.setWifi(!enabled)) { "Wi-Fi 切换失败，请检查权限或设备策略" }
            message = "Wi-Fi 请求已提交，等待设备状态更新"
        }
    }

    fun toggleTheme() {
        dark = !dark
    }

    fun adjustBrightness(delta: Int) {
        if (!systemBars.quickSettingsEnabled) return
        safely {
            val value = (brightness + delta).coerceIn(10, 255)
            actions.setBrightness(value)
            brightness = value
        }
    }

    fun openSettings(page: SettingsPage) {
        if (!systemBars.quickSettingsEnabled) return
        afterReleasingInput { actions.openSettings(page) }
    }

    fun showImePicker() {
        if (!BuildConfig.SYSTEM_UI || !systemBars.imeVisible || !systemBars.showImeSwitcher) return
        afterReleasingInput { context.getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
    }

    override fun onDisable(disabled1: Int, disabled2: Int) {
        if (systemBars.disabled1 == disabled1 && systemBars.disabled2 == disabled2) return
        updateSystemBars(systemBars.copy(disabled1 = disabled1, disabled2 = disabled2), resetWindows = true)
    }

    override fun onSystemBarAttributes(
        appearance: Int, regions: List<BarAppearanceRegion>, behavior: Int, requestedVisibleTypes: Int,
    ) {
        updateSystemBars(systemBars.copy(appearance = appearance, appearanceRegions = regions,
            behavior = behavior, requestedVisibleTypes = requestedVisibleTypes))
    }

    override fun onBarWindowState(window: Int, state: Int) {
        val next = when (window) {
            1 -> systemBars.copy(statusWindowState = state)
            2 -> systemBars.copy(navigationWindowState = state)
            else -> return
        }
        updateSystemBars(next)
    }

    override fun onTransientBars(types: Int, show: Boolean) {
        updateSystemBars(systemBars.copy(transientTypes = if (show) systemBars.transientTypes or types
            else systemBars.transientTypes and types.inv()))
    }

    override fun onImeStatus(visible: Boolean, backDisposition: Int, showSwitcher: Boolean) {
        updateSystemBars(systemBars.copy(imeVisible = visible, imeBackDisposition = backDisposition,
            showImeSwitcher = visible && showSwitcher))
    }

    override fun onStatusIcon(slot: String, icon: SystemStatusIcon?) {
        if (closed) return
        statusIcons = statusIcons.toMutableMap().apply {
            if (icon == null) remove(slot) else put(slot, icon)
        }.toMap()
    }

    override fun onExpandPanel() = openShade()
    override fun onCollapsePanels() = releaseFocus()
    override fun onTogglePanel() { if (shadeVisible) closeShade() else openShade() }
    override fun onShowRecents() = openRecents()
    override fun onHideRecents() = closeRecents()
    override fun onToggleRecents() { if (recentsVisible) closeRecents() else openRecents() }

    private fun updateSystemBars(next: SystemBarState, resetWindows: Boolean = false) {
        if (closed) return
        val previous = systemBars
        systemBars = next
        if (!next.navigationBarVisible) {
            if (shadeReturnTarget == InputTarget.NAVIGATION) shadeReturnTarget = InputTarget.APP
            if (recentsReturnTarget == InputTarget.NAVIGATION) recentsReturnTarget = InputTarget.APP
        }
        if ((shadeVisible && !next.shadeEnabled) || (recentsVisible && !next.recentsEnabled) ||
            (inputTarget == InputTarget.NAVIGATION && !next.navigationBarVisible)) {
            releaseFocus()
        } else if (resetWindows || previous.statusBarVisible != next.statusBarVisible ||
            previous.navigationBarVisible != next.navigationBarVisible || previous.backEnabled != next.backEnabled) {
            onWindowsChanged()
        }
    }

    fun stop() {
        releaseFocus()
        stopService()
    }

    fun showMessage(text: String) {
        message = text
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    private fun changeTarget(target: InputTarget) {
        if (inputTarget != target) recentRequest++
        if (recentsVisible && target != InputTarget.RECENTS) {
            // Ignore results from a panel that has already closed.
            recentTasks = emptyList()
            recentsLoading = false
            recentsError = null
        }
        inputTarget = target
        // Also cancel a window's drag preview when releasing an already-released target.
        onWindowsChanged()
    }

    private fun afterReleasingInput(action: () -> Unit) {
        safely {
            releaseFocus()
            runAfterInputReleased { if (!closed) safely(action) }
        }
    }

    private fun safely(action: () -> Unit) {
        message = null
        try {
            action()
        } catch (error: Exception) {
            Log.e("SimpleSystemUI", "系统操作失败", error)
            showMessage(error.cause?.message ?: error.message ?: "系统操作失败")
        }
    }

    fun close() {
        closed = true
        systemBarTouchActive = false
        recentRequest++
        taskWorker.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        recentTasks = emptyList()
        statusIcons = emptyMap()
        recentsLoading = false
        devices.close()
        onWindowsChanged = {}
        onSystemBarInteraction = {}
        runAfterInputReleased = {}
    }
}
