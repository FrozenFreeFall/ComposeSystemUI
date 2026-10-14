package com.example.simplesystemui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.simplesystemui.data.DeviceRepository
import com.example.simplesystemui.data.DeviceState
import com.example.simplesystemui.data.RecentTask
import com.example.simplesystemui.data.RecentTasksRepository
import com.example.simplesystemui.platform.SystemActionExecutor
import java.util.concurrent.Executors

enum class InputTarget { APP, NAVIGATION, SHADE, RECENTS }
enum class NavigationAction { BACK, HOME, RECENTS }
enum class SettingsPage { WIFI, BLUETOOTH, DISPLAY, ALL }

/** 按钮直接调用方法，Compose 直接读取属性，不需要 Flow、Store 或事件队列。 */
class SystemUiController(private val context: Context, private val stopService: () -> Unit) {
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

    var onWindowsChanged: () -> Unit = {}
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
        if (shadeVisible) return
        shadeReturnTarget = if (recentsVisible) recentsReturnTarget else inputTarget
        message = null
        changeTarget(InputTarget.SHADE)
    }

    fun closeShade() {
        if (shadeVisible) changeTarget(shadeReturnTarget)
    }

    fun focusNavigation() {
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
        if (inputTarget == InputTarget.APP) navigate(NavigationAction.BACK) else backPressed()
    }

    fun navigate(action: NavigationAction) {
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
        if (closed || recentsVisible) return
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
        safely {
            val value = (brightness + delta).coerceIn(10, 255)
            actions.setBrightness(value)
            brightness = value
        }
    }

    fun openSettings(page: SettingsPage) {
        afterReleasingInput { actions.openSettings(page) }
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
        recentRequest++
        taskWorker.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        recentTasks = emptyList()
        recentsLoading = false
        devices.close()
        onWindowsChanged = {}
        runAfterInputReleased = {}
    }
}
