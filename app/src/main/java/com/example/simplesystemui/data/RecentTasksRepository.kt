package com.example.simplesystemui.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.drawable.Drawable
import android.hardware.HardwareBuffer
import android.os.Bundle
import android.os.Looper
import android.os.UserHandle
import androidx.annotation.WorkerThread
import com.example.simplesystemui.BuildConfig
import java.lang.reflect.InvocationTargetException

data class RecentTask(
    val id: Int,
    val title: String,
    val icon: Drawable?,
    val thumbnail: Bitmap?,
)

/** Android 36 系统版桥接。同步操作由控制器放到后台线程，普通版不调用隐藏 API。 */
@Suppress("NewApi")
class RecentTasksRepository(context: Context) {
    private val app = context.applicationContext
    private val taskServiceClass by lazy { Class.forName("android.app.IActivityTaskManager") }
    private val taskService by lazy {
        Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
    }

    @WorkerThread
    fun loadTasks(): List<RecentTask> {
        requireBackgroundSystemCall()
        val userId = ActivityManager::class.java.getMethod("getCurrentUser").invoke(null) as Int
        // 明确指定前台用户；系统 UID 的 Service 可能运行在 user 0。
        val slice = call("getRecentTasks", arrayOf(Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!), 40, 2, userId)
            ?: return emptyList()
        val tasks = Class.forName("android.content.pm.ParceledListSlice")
            .getMethod("getList").invoke(slice) as List<*>
        val packageManagers = mutableMapOf<Int, PackageManager>()
        return tasks.filterIsInstance<ActivityManager.RecentTaskInfo>().mapNotNull { task ->
            val component = task.baseActivity ?: task.baseIntent.component ?: task.topActivity
            val activityType = activityType(task)
            if (task.taskId < 0 || activityType == 2 || activityType == 3 ||
                task.baseIntent.hasCategory(Intent.CATEGORY_HOME) ||
                component?.packageName == app.packageName ||
                task.topActivity?.packageName == app.packageName) return@mapNotNull null

            // 卸载、图标或截图读取失败时仍保留该任务卡片。
            val packages = runCatching {
                // 列表也可能包含工作资料；图标和名称使用任务所属用户的资源。
                val taskUserId = task.javaClass.getField("userId").getInt(task)
                packageManagers.getOrPut(taskUserId) { userContext(taskUserId).packageManager }
            }.getOrDefault(app.packageManager)
            val activity = component?.let { runCatching { packages.getActivityInfo(it, 0) }.getOrNull() }
            val title = task.taskDescription?.label?.takeIf { it.isNotBlank() }
                ?: runCatching { activity?.loadLabel(packages)?.toString() }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                ?: component?.packageName ?: "任务 ${task.taskId}"
            val icon = runCatching { activity?.loadIcon(packages) }.getOrNull()
            RecentTask(task.taskId, title, icon, runCatching { thumbnail(task.taskId) }.getOrNull())
        }
    }

    @WorkerThread
    fun openTask(id: Int) {
        requireBackgroundSystemCall()
        val result = call("startActivityFromRecents", arrayOf(Int::class.javaPrimitiveType!!,
            Bundle::class.java), id, null) as Int
        // 100 及以上也可能表示取消/拒绝，Android 的成功区间是 0..99。
        check(result in 0..99) { "无法打开任务：$id（$result）" }
    }

    @WorkerThread
    fun removeTask(id: Int) {
        requireBackgroundSystemCall()
        check(call("removeTask", arrayOf(Int::class.javaPrimitiveType!!), id) == true) {
            "任务已不存在或无法移除：$id"
        }
    }

    private fun requireBackgroundSystemCall() {
        check(BuildConfig.SYSTEM_UI) { "普通应用演示不支持读取系统最近任务" }
        check(Looper.myLooper() != Looper.getMainLooper()) { "最近任务操作需要在后台线程执行" }
    }

    private fun userContext(userId: Int): Context {
        val user = UserHandle::class.java.getMethod("of", Int::class.javaPrimitiveType)
            .invoke(null, userId)
        return Context::class.java.getMethod("createContextAsUser", UserHandle::class.java,
            Int::class.javaPrimitiveType).invoke(app, user, 0) as Context
    }

    private fun activityType(task: ActivityManager.RecentTaskInfo): Int {
        val configuration = task.javaClass.getField("configuration").get(task)
        val windowConfiguration = configuration.javaClass.getField("windowConfiguration").get(configuration)
        return windowConfiguration.javaClass.getMethod("getActivityType").invoke(windowConfiguration) as Int
    }

    private fun thumbnail(id: Int): Bitmap? {
        // 只取已有低分辨率快照；安全窗口或无快照时显示占位卡片。
        val snapshot = call("getTaskSnapshot", arrayOf(Int::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!), id, true)
            ?: return null
        val snapshotClass = Class.forName("android.window.TaskSnapshot")
        val buffer = snapshotClass.getMethod("getHardwareBuffer").invoke(snapshot) as? HardwareBuffer
            ?: return null
        return buffer.use {
            val colorSpace = snapshotClass.getMethod("getColorSpace").invoke(snapshot) as? ColorSpace
            val hardwareBitmap = Bitmap.wrapHardwareBuffer(it, colorSpace) ?: return@use null
            try {
                // 返回独立的软件 Bitmap，及时释放快照持有的硬件缓冲区。
                hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
            } finally {
                hardwareBitmap.recycle()
            }
        }
    }

    private fun call(name: String, types: Array<Class<*>>, vararg arguments: Any?): Any? {
        try {
            return taskServiceClass.getMethod(name, *types).invoke(taskService, *arguments)
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }
}
