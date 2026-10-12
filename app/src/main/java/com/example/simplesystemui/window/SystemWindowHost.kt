package com.example.simplesystemui.window

import android.os.Build
import android.provider.Settings
import android.content.Context
import android.graphics.Insets
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.simplesystemui.R
import com.example.simplesystemui.mvi.*
import com.example.simplesystemui.platform.PlatformWindowApi
import com.example.simplesystemui.platform.SystemWindowTypes
import com.example.simplesystemui.ui.SystemWindowContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

enum class WindowKind { STATUS, NAVIGATION, SHADE }

/** Three application overlay windows owned by the foreground service. Main-thread only. */
class SystemWindowHost(private val context: Context, private val store: SystemUiStore) {
    private data class Entry(
        val kind: WindowKind,
        val wm: WindowManager,
        val root: FrameLayout,
        val compose: ComposeView,
        val params: WindowManager.LayoutParams,
        val owner: WindowLifecycleOwner,
        var backDispatcher: OnBackInvokedDispatcher? = null,
        val backCallback: OnBackInvokedCallback?,
    )
    private val entries = mutableListOf<Entry>()
    private var lastTarget: InputTarget? = null

    fun attach() {
        if (entries.isNotEmpty()) return
        check(Settings.canDrawOverlays(context)) { "未授予悬浮窗权限" }
        try {
            add(WindowKind.STATUS, SystemWindowTypes.TYPE_STATUS_BAR)
            add(WindowKind.NAVIGATION, SystemWindowTypes.TYPE_NAVIGATION_BAR)
            add(WindowKind.SHADE, SystemWindowTypes.TYPE_NOTIFICATION_SHADE)
            render(store.state.value)
        } catch (error: Exception) {
            error.printStackTrace()
            close() // Atomic rollback: never leave only one of the bars attached.
            throw IllegalStateException(
                "悬浮窗创建失败：请检查悬浮窗权限及系统是否允许显示。${error.message}", error,
            )
        }
    }

    private fun add(kind: WindowKind, type: Int) {
        val display = requireNotNull(context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY))
        val displayContext = context.createDisplayContext(display)
        val windowContext = if (Build.VERSION.SDK_INT >= 30) displayContext.createWindowContext(type, null) else displayContext
        val themed = ContextThemeWrapper(windowContext, R.style.Theme_SimpleSystemUI)
        val wm = windowContext.getSystemService(WindowManager::class.java)
        val owner = WindowLifecycleOwner()
        val height = when (kind) {
            WindowKind.STATUS -> dp(windowContext, 40)
            WindowKind.NAVIGATION -> dp(windowContext, 64)
            WindowKind.SHADE -> WindowManager.LayoutParams.MATCH_PARENT
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, height, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = if (kind == WindowKind.NAVIGATION) Gravity.BOTTOM else Gravity.TOP
            setTitle("SimpleSystemUI-${kind.name}")
            if (Build.VERSION.SDK_INT >= 30) {
                setFitInsetsTypes(0)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
//        if (kind == WindowKind.STATUS) {
//            PlatformWindowApi.provideBarInsets(params, WindowInsets.Type.statusBars(), Insets.of(0, height, 0, 0))
//        } else if (kind == WindowKind.NAVIGATION) {
//            PlatformWindowApi.provideBarInsets(params, WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, height))
//        }
        val root = object : FrameLayout(themed) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) store.dispatch(SystemUiIntent.BackPressed)
                    return true
                }
                if (event.keyCode == KeyEvent.KEYCODE_MENU) {
                    if (event.action == KeyEvent.ACTION_UP) store.dispatch(
                        if (store.state.value.shadeVisible) SystemUiIntent.CloseShade else SystemUiIntent.OpenShade,
                    )
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeViewModelStoreOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        val compose = ComposeView(themed).apply {
            id = View.generateViewId()
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { SystemWindowContent(kind, store) }
        }
        root.addView(compose, FrameLayout.LayoutParams(-1, -1))
        if (kind == WindowKind.SHADE) root.visibility = View.GONE
        try {
            wm.addView(root, params)
            val entry = Entry(kind, wm, root, compose, params, owner,
                backCallback = if (Build.VERSION.SDK_INT >= 33) OnBackInvokedCallback { store.dispatch(SystemUiIntent.BackPressed) } else null)
            entries += entry
            root.post {
                if (Build.VERSION.SDK_INT >= 33 && entries.contains(entry) && root.isAttachedToWindow) {
                    entry.backDispatcher = root.findOnBackInvokedDispatcher()
                    entry.backDispatcher?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, requireNotNull(entry.backCallback))
                }
            }
            owner.visible(kind != WindowKind.SHADE)
        } catch (e: Exception) {
            compose.disposeComposition()
            owner.destroy()
            throw e
        }
    }

    fun render(state: SystemUiState) {
        if (lastTarget == state.inputTarget) return
        // First release focus from all windows, then give it to exactly one target.
        entries.forEach { entry -> setFocusable(entry, false) }
        val shade = entries.firstOrNull { it.kind == WindowKind.SHADE }
        shade?.apply {
            root.visibility = if (state.shadeVisible) View.VISIBLE else View.GONE
            owner.visible(state.shadeVisible)
        }
        val target = when (state.inputTarget) {
            InputTarget.APP -> null
            InputTarget.NAVIGATION -> entries.firstOrNull { it.kind == WindowKind.NAVIGATION }
            InputTarget.SHADE -> shade
        }
        target?.let { setFocusable(it, true) }
        lastTarget = state.inputTarget
    }

    private fun setFocusable(entry: Entry, focused: Boolean) {
        val mask = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val newFlags = if (focused) entry.params.flags and mask.inv() else entry.params.flags or mask
        if (entry.params.flags != newFlags) {
            entry.params.flags = newFlags
            entry.wm.updateViewLayout(entry.root, entry.params)
        }
    }

    /** Wait for actual focus callbacks before injecting BACK, otherwise our bar could receive it. */
    suspend fun awaitInputReleased() {
        withTimeout(1_500) {
            while (entries.any { it.root.hasWindowFocus() }) delay(16)
        }
    }

    fun close() {
        entries.asReversed().forEach {
            if (Build.VERSION.SDK_INT >= 33) {
                it.backCallback?.let { callback -> it.backDispatcher?.unregisterOnBackInvokedCallback(callback) }
            }
            runCatching { it.wm.removeViewImmediate(it.root) }
            it.compose.disposeComposition()
            it.owner.destroy()
        }
        entries.clear()
        lastTarget = null
    }

    fun recreate() {
        close()
        attach()
    }
    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
