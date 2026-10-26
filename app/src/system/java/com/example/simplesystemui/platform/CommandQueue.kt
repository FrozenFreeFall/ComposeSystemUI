package com.example.simplesystemui.platform

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.os.UserHandle
import android.util.Log
import android.util.Pair
import android.view.Display
import android.view.accessibility.AccessibilityManager

/**
 * Android 16 IStatusBar endpoint for the bars/panels implemented by this demo.
 * Uses the ROM's transaction IDs and Parcelable creators, never copied AIDL ordinals.
 * See core/java/com/android/internal/statusbar/IStatusBar.aidl in android16-release.
 */
class CommandQueue(context: Context, callbacks: SystemBarCallbacks, onError: (Throwable) -> Unit) : Binder() {
    private var callbacks: SystemBarCallbacks? = callbacks
    private var onError: ((Throwable) -> Unit)? = onError
    private val handler = Handler(Looper.getMainLooper())
    private var service: IBinder? = null
    private var transientTypes = 0
    private val autoHideTimeout = context.getSystemService(AccessibilityManager::class.java)
        .getRecommendedTimeoutMillis(2250, AccessibilityManager.FLAG_CONTENT_CONTROLS).toLong()
    private val autoHide = object : Runnable {
        override fun run() {
            if (closed || transientTypes == 0) return
            if (callbacks?.transientAutoHideSuspended == true) {
                handler.postDelayed(this, autoHideTimeout)
                return
            }
            try {
                val wm = Class.forName("android.view.WindowManagerGlobal")
                    .getMethod("getWindowManagerService").invoke(null)
                Class.forName("android.view.IWindowManager")
                    .getMethod("hideTransientBars", Int::class.javaPrimitiveType).invoke(wm, Display.DEFAULT_DISPLAY)
                updateTransientBars(transientTypes, false)
            } catch (error: Exception) { reportError(error) }
        }
    }
    @Volatile private var closed = false
    private val transactions by lazy {
        Class.forName("com.android.internal.statusbar.IStatusBar\$Stub").declaredFields
            .filter { it.name.startsWith("TRANSACTION_") }
            .associate { field ->
                field.isAccessible = true
                field.getInt(null) to field.name.removePrefix("TRANSACTION_")
            }
    }
    private val deathRecipient = IBinder.DeathRecipient {
        reportError(IllegalStateException("系统 statusbar 服务已断开，请重新启动窗口服务"))
    }

    init { attachInterface(null, DESCRIPTOR) }

    fun start() {
        check(Looper.myLooper() == Looper.getMainLooper())
        check(!closed) { "CommandQueue 已关闭" }
        if (service != null) return
        transactions // Resolve the platform contract before registering our endpoint.
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java).invoke(null, "statusbar") as? IBinder
            ?: error("系统 statusbar 服务不可用")
        val barClass = Class.forName(DESCRIPTOR)
        val bar = Class.forName("$DESCRIPTOR\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, this)
        val serviceClass = Class.forName("com.android.internal.statusbar.IStatusBarService")
        val remote = Class.forName("${serviceClass.name}\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        service = binder
        binder.linkToDeath(deathRecipient, 0)
        try {
            val snapshot = requireNotNull(serviceClass.getMethod("registerStatusBar", barClass).invoke(remote, bar))
            // Registration runs on main. Binder callbacks only post, so the snapshot is applied first.
            applySnapshot(snapshot)
        } catch (error: Exception) {
            close()
            throw IllegalStateException("无法向系统注册状态栏：${error.cause?.message ?: error.message}", error)
        }
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == INTERFACE_TRANSACTION) {
            reply?.writeString(DESCRIPTOR)
            return true
        }
        val name = transactions[code] ?: return super.onTransact(code, data, reply, flags)
        data.enforceInterface(DESCRIPTOR)
        if (closed) return true
        try {
            val command = readCommand(name, data) ?: return super.onTransact(code, data, reply, flags)
            data.enforceNoDataAvail()
            // Never retain a Parcel: Binder recycles it as soon as onTransact returns.
            handler.post {
                if (!closed) try { callbacks?.let(command) } catch (error: Exception) { reportError(error) }
            }
        } catch (error: Exception) {
            reportError(IllegalStateException("无法处理系统状态栏命令 $name", error))
        }
        return true
    }

    private fun readCommand(name: String, data: Parcel): ((SystemBarCallbacks) -> Unit)? {
        return when (name) {
            "setIcon" -> {
                val slot = requireNotNull(data.readString())
                val icon = readObject(data, "com.android.internal.statusbar.StatusBarIcon")?.let(::statusIcon)
                return { callbacks -> callbacks.onStatusIcon(slot, icon) }
            }
            "removeIcon" -> {
                val slot = requireNotNull(data.readString())
                return { callbacks -> callbacks.onStatusIcon(slot, null) }
            }
            "disable" -> {
                val display = data.readInt()
                val first = data.readInt()
                val second = data.readInt()
                forDisplay(display) { it.onDisable(first, second) }
            }
            "disableForAllDisplays" -> {
                val states = requireNotNull(readObject(data, "com.android.internal.statusbar.DisableStates"))
                val displays = states.field("displaysWithStates") as Map<*, *>
                val disabled = displays[Display.DEFAULT_DISPLAY] as? Pair<*, *>
                return { callbacks ->
                    if (disabled != null) callbacks.onDisable(disabled.first as Int, disabled.second as Int)
                }
            }
            "animateExpandNotificationsPanel" -> { { it.onExpandPanel() } }
            "animateExpandSettingsPanel" -> {
                data.readString() // This demo has a single combined panel.
                return { callbacks -> callbacks.onExpandPanel() }
            }
            "animateCollapsePanels" -> { { it.onCollapsePanels() } }
            "toggleNotificationsPanel" -> { { it.onTogglePanel() } }
            "showRecentApps" -> {
                data.readBoolean()
                return { callbacks -> callbacks.onShowRecents() }
            }
            "hideRecentApps" -> {
                data.readBoolean()
                data.readBoolean()
                return { callbacks -> callbacks.onHideRecents() }
            }
            "toggleRecentApps" -> { { it.onToggleRecents() } }
            "setWindowState" -> {
                val display = data.readInt()
                val window = data.readInt()
                val state = data.readInt()
                forDisplay(display) {
                    if (state == 2 && (window == 1 || window == 2)) updateTransientBars(window, false)
                    it.onBarWindowState(window, state)
                }
            }
            "setImeWindowStatus" -> {
                val display = data.readInt()
                val visible = data.readInt() and 2 != 0 // InputMethodService.IME_VISIBLE
                val disposition = data.readInt()
                val switcher = data.readBoolean()
                forDisplay(display) { it.onImeStatus(visible, disposition, switcher) }
            }
            "onSystemBarAttributesChanged" -> {
                val display = data.readInt()
                val appearance = data.readInt()
                val regions = readArray(data, "com.android.internal.view.AppearanceRegion").map(::appearanceRegion)
                data.readBoolean() // navbarColorManagedByIme; no IME color sampling in this demo.
                val behavior = data.readInt()
                val requested = data.readInt()
                data.readString() // focused package
                readArray(data, "com.android.internal.statusbar.LetterboxDetails")
                forDisplay(display) { it.onSystemBarAttributes(appearance, regions, behavior, requested) }
            }
            "showTransient", "abortTransient" -> {
                val display = data.readInt()
                val types = data.readInt()
                val show = name == "showTransient"
                if (show) data.readBoolean()
                forDisplay(display) { updateTransientBars(types, show) }
            }
            else -> null // Biometric, power, toast etc. are outside this demo's implemented surface.
        }
    }

    private fun forDisplay(display: Int, action: (SystemBarCallbacks) -> Unit): (SystemBarCallbacks) -> Unit =
        if (display == Display.DEFAULT_DISPLAY) action else { _ -> }

    private fun applySnapshot(snapshot: Any) {
        val target = callbacks ?: return
        target.onDisable(snapshot.int("mDisabledFlags1"), snapshot.int("mDisabledFlags2"))
        val regions = (snapshot.field("mAppearanceRegions") as Array<*>).filterNotNull().map(::appearanceRegion)
        target.onSystemBarAttributes(snapshot.int("mAppearance"), regions,
            snapshot.int("mBehavior"), snapshot.int("mRequestedVisibleTypes"))
        updateTransientBars(snapshot.int("mTransientBarTypes"), true)
        target.onImeStatus(snapshot.int("mImeWindowVis") and 2 != 0,
            snapshot.int("mImeBackDisposition"), snapshot.field("mShowImeSwitcher") as Boolean)
        (snapshot.field("mIcons") as Map<*, *>).forEach { (slot, value) ->
            if (slot is String && value != null) target.onStatusIcon(slot, statusIcon(value))
        }
    }

    private fun appearanceRegion(value: Any): BarAppearanceRegion {
        val bounds = value.javaClass.getMethod("getBounds").invoke(value) as Rect
        val appearance = value.javaClass.getMethod("getAppearance").invoke(value) as Int
        return BarAppearanceRegion(appearance, bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    private fun statusIcon(value: Any): SystemStatusIcon {
        val user = value.field("user") as UserHandle
        val userId = UserHandle::class.java.getMethod("getIdentifier").invoke(user) as Int
        return SystemStatusIcon(value.field("icon") as Icon, value.field("visible") as Boolean,
            value.field("contentDescription")?.toString().orEmpty(), userId, value.int("iconLevel"))
    }

    @Suppress("UNCHECKED_CAST")
    private fun creator(name: String) = Class.forName(name).getField("CREATOR").get(null) as Parcelable.Creator<Parcelable>
    private fun readObject(data: Parcel, name: String): Any? = data.readTypedObject(creator(name))
    private fun readArray(data: Parcel, name: String): List<Any> =
        data.createTypedArray(creator(name))?.filterNotNull().orEmpty()
    private fun Any.field(name: String): Any? = javaClass.getField(name).get(this)
    private fun Any.int(name: String) = javaClass.getField(name).getInt(this)

    private fun reportError(error: Throwable) {
        Log.e("SimpleSystemUI", "CommandQueue failure", error)
        handler.post { if (!closed) onError?.invoke(error) }
    }

    private fun updateTransientBars(types: Int, show: Boolean) {
        val bars = types and 3
        transientTypes = if (show) transientTypes or bars else transientTypes and bars.inv()
        callbacks?.onTransientBars(bars, show)
        touchAutoHide()
    }

    fun touchAutoHide() {
        handler.removeCallbacks(autoHide)
        if (!closed && transientTypes != 0) handler.postDelayed(autoHide, autoHideTimeout)
    }

    fun close() {
        closed = true
        callbacks = null
        onError = null
        handler.removeCallbacksAndMessages(null)
        service?.let { runCatching { it.unlinkToDeath(deathRecipient, 0) } }
        service = null
        transientTypes = 0
        // IStatusBarService has no unregister API. Release callbacks; a later start re-registers.
    }

    companion object {
        private const val DESCRIPTOR = "com.android.internal.statusbar.IStatusBar"
    }
}
