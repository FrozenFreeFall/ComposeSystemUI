package com.example.simplesystemui.window

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.content.Context
import android.graphics.Insets
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.view.animation.DecelerateInterpolator
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.simplesystemui.R
import com.example.simplesystemui.BuildConfig
import com.example.simplesystemui.InputTarget
import com.example.simplesystemui.SystemUiController
import com.example.simplesystemui.platform.PlatformWindowApi
import com.example.simplesystemui.platform.BackAccessibilityService
import com.example.simplesystemui.platform.SystemWindowTypes
import com.example.simplesystemui.ui.SystemWindowContent

enum class WindowKind { STATUS, NAVIGATION, SHADE }

/** System bars, notification shade and left-edge back windows. All methods run on the main thread. */
class SystemWindowHost(private val context: Context, private val controller: SystemUiController) {
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
    private val handler = Handler(Looper.getMainLooper())
    private val edgeBack = EdgeBackWindow(context, controller::edgeBack)
    private var statusBarSwipe: VerticalSwipeHandler? = null
    private var shadeSwipe: VerticalSwipeHandler? = null
    private val shadeScrollState = ScrollState(0)
    private var shadeAnimator: ValueAnimator? = null
    private var shadePreview = false
    private var shadeHeight = 0f
    private var dragStartHeight = 0f
    private var dragStartedExpanded = false

    fun attach() {
        if (entries.isNotEmpty()) return
        try {
            add(WindowKind.STATUS, SystemWindowTypes.TYPE_STATUS_BAR)
            add(WindowKind.NAVIGATION, SystemWindowTypes.TYPE_NAVIGATION_BAR)
            add(WindowKind.SHADE, SystemWindowTypes.TYPE_NOTIFICATION_SHADE)
            edgeBack.attach()
            render()
        } catch (error: Exception) {
            error.printStackTrace()
            close() // Atomic rollback: never leave only one of the bars attached.
            throw IllegalStateException(
                if (BuildConfig.SYSTEM_UI) {
                    "系统窗口创建失败：请检查平台签名、系统权限，以及原有 SystemUI 是否已释放窗口。${error.message}"
                } else {
                    "悬浮窗创建失败：请检查悬浮窗权限及系统是否允许显示。${error.message}"
                }, error,
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
        if (BuildConfig.SYSTEM_UI && Build.VERSION.SDK_INT >= 36) {
            if (kind == WindowKind.STATUS) {
                PlatformWindowApi.provideBarInsets(params, WindowInsets.Type.statusBars(), Insets.of(0, height, 0, 0))
            } else if (kind == WindowKind.NAVIGATION) {
                PlatformWindowApi.provideBarInsets(params, WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, height))
            }
        }
        var shadeSwipeAllowed = false
        val swipe = when (kind) {
            WindowKind.STATUS -> VerticalSwipeHandler(
                themed, SwipeDirection.DOWN,
                canStart = { !controller.panelVisible && entries.any { it.kind == WindowKind.SHADE } },
                onStart = ::startShadeDrag,
                onDrag = { distance -> updateShadeHeight(dragStartHeight + distance) },
                onEnd = ::finishShadeDrag,
            ).also { statusBarSwipe = it }
            WindowKind.SHADE -> VerticalSwipeHandler(
                themed, SwipeDirection.UP,
                canStart = { controller.shadeVisible && shadeSwipeAllowed },
                onStart = ::startShadeDrag,
                onDrag = { distance -> updateShadeHeight(dragStartHeight + distance) },
                onEnd = ::finishShadeDrag,
            ).also { shadeSwipe = it }
            WindowKind.NAVIGATION -> null
        }
        val root = object : FrameLayout(themed) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (kind == WindowKind.SHADE && event.actionMasked == MotionEvent.ACTION_DOWN) {
                    // Choose scrolling or closing once per gesture. A later gesture can close
                    // after content reaches its end; areas outside the scrolling column always can.
                    val scrollWidth = this.width.coerceAtMost(dp(themed, 900))
                    val sideMargin = (this.width - scrollWidth) / 2f
                    shadeSwipeAllowed = shadePreview || !shadeScrollState.canScrollForward ||
                        event.y < dp(themed, 40) || event.y >= this.height - dp(themed, 112) ||
                        event.x < sideMargin || event.x >= this.width - sideMargin
                }
                return super.dispatchTouchEvent(event)
            }

            override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
                val intercepted = swipe?.intercept(event) == true
                return intercepted || (kind == WindowKind.SHADE && shadePreview) ||
                    super.onInterceptTouchEvent(event)
            }

            // This container only handles dragging; Compose buttons provide accessible actions.
            @SuppressLint("ClickableViewAccessibility")
            override fun onTouchEvent(event: MotionEvent): Boolean {
                return swipe?.touch(event) == true || (kind == WindowKind.SHADE && shadePreview) ||
                    super.onTouchEvent(event)
            }

            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) controller.backPressed()
                    return true
                }
                if (event.keyCode == KeyEvent.KEYCODE_MENU) {
                    if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                        if (controller.shadeVisible) controller.closeShade() else controller.openShade()
                    }
                    return true
                }
                if (event.keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
                    if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                        if (controller.recentsVisible) controller.closeRecents() else controller.openRecents()
                    }
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        val compose = ComposeView(themed).apply {
            id = View.generateViewId()
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { SystemWindowContent(kind, controller, shadeScrollState) }
        }
        root.addView(compose, FrameLayout.LayoutParams(-1, -1))
        if (kind == WindowKind.SHADE) {
            root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                if (shadePreview) updateShadeHeight(shadeHeight)
            }
        }
        if (kind == WindowKind.SHADE) root.visibility = View.GONE
        try {
            wm.addView(root, params)
            val entry = Entry(kind, wm, root, compose, params, owner,
                backCallback = if (Build.VERSION.SDK_INT >= 33) OnBackInvokedCallback { controller.backPressed() } else null)
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

    fun render() {
        edgeBack.reset()
        statusBarSwipe?.reset()
        shadeSwipe?.reset()
        cancelShadeAnimation()
        shadePreview = false
        // First release focus from all windows, then give it to exactly one target.
        entries.forEach { entry -> setFocusable(entry, false) }
        val shade = entries.firstOrNull { it.kind == WindowKind.SHADE }
        shade?.apply {
            setTouchable(this, true)
            root.clipBounds = null
            shadeHeight = if (controller.panelVisible) maxShadeHeight(this) else 0f
            root.visibility = if (controller.panelVisible) View.VISIBLE else View.GONE
            owner.visible(controller.panelVisible)
        }
        val target = when (controller.inputTarget) {
            InputTarget.APP -> null
            InputTarget.NAVIGATION -> entries.firstOrNull { it.kind == WindowKind.NAVIGATION }
            InputTarget.SHADE -> shade
            InputTarget.RECENTS -> shade
        }
        target?.let { setFocusable(it, true) }
        edgeBack.setEnabled(BuildConfig.SYSTEM_UI || BackAccessibilityService.connected ||
            controller.inputTarget != InputTarget.APP)
    }

    private fun startShadeDrag() {
        val shade = entries.firstOrNull { it.kind == WindowKind.SHADE } ?: return
        cancelShadeAnimation()
        dragStartHeight = shadeHeight
        dragStartedExpanded = controller.shadeVisible
        shadePreview = true
        // Opening stays with the status bar. Closing stays with the already-focused shade.
        if (!dragStartedExpanded) {
            setFocusable(shade, false)
            setTouchable(shade, false)
        }
        updateShadeHeight(shadeHeight)
        shade.root.visibility = View.VISIBLE
        shade.owner.visible(true)
    }

    private fun updateShadeHeight(height: Float) {
        val shade = entries.firstOrNull { it.kind == WindowKind.SHADE } ?: return
        shadeHeight = height.coerceIn(0f, maxShadeHeight(shade))
        val width = shade.root.width.takeIf { it > 0 } ?: shade.root.resources.displayMetrics.widthPixels
        // Keep the Compose layout stable and reveal it from the top as the finger moves.
        shade.root.clipBounds = Rect(0, 0, width, shadeHeight.toInt())
    }

    private fun finishShadeDrag(cancelled: Boolean, velocityY: Float) {
        val shade = entries.firstOrNull { it.kind == WindowKind.SHADE } ?: return
        if (!shadePreview) return
        val maxHeight = maxShadeHeight(shade)
        val minVelocity = ViewConfiguration.get(shade.root.context).scaledMinimumFlingVelocity
        val expand = when {
            cancelled -> dragStartedExpanded
            velocityY > minVelocity -> true
            velocityY < -minVelocity -> false
            else -> shadeHeight >= maxHeight / 2f
        }
        val target = if (expand) maxHeight else 0f
        val animator = ValueAnimator.ofFloat(shadeHeight, target)
        shadeAnimator = animator
        animator.duration = 220
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { updateShadeHeight(it.animatedValue as Float) }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (shadeAnimator !== animation) return
                shadeAnimator = null
                when {
                    expand == controller.shadeVisible -> render()
                    expand -> controller.openShade()
                    else -> controller.closeShade()
                }
            }
        })
        animator.start()
    }

    private fun maxShadeHeight(shade: Entry): Float {
        return when {
            shade.root.height > 0 -> shade.root.height.toFloat()
            Build.VERSION.SDK_INT >= 30 -> shade.wm.currentWindowMetrics.bounds.height().toFloat()
            else -> shade.root.resources.displayMetrics.heightPixels.toFloat()
        }
    }

    private fun cancelShadeAnimation() {
        shadeAnimator?.apply {
            removeAllListeners()
            removeAllUpdateListeners()
            cancel()
        }
        shadeAnimator = null
    }

    private fun setTouchable(entry: Entry, touchable: Boolean) {
        val mask = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val newFlags = if (touchable) entry.params.flags and mask.inv() else entry.params.flags or mask
        if (entry.params.flags != newFlags) {
            entry.params.flags = newFlags
            entry.wm.updateViewLayout(entry.root, entry.params)
        }
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
    fun afterInputReleased(action: () -> Unit) {
        handler.removeCallbacksAndMessages(null)
        val deadline = SystemClock.uptimeMillis() + 1_500
        val checkFocus = object : Runnable {
            override fun run() {
                if (entries.isEmpty() || controller.inputTarget != InputTarget.APP) return
                if (entries.none { it.root.hasWindowFocus() }) {
                    action()
                } else if (SystemClock.uptimeMillis() < deadline) {
                    handler.postDelayed(this, 16)
                } else {
                    controller.showMessage("窗口尚未释放按键焦点，请重试")
                }
            }
        }
        handler.post(checkFocus)
    }

    fun close() {
        edgeBack.close()
        statusBarSwipe?.reset()
        statusBarSwipe = null
        shadeSwipe?.reset()
        shadeSwipe = null
        cancelShadeAnimation()
        shadePreview = false
        shadeHeight = 0f
        handler.removeCallbacksAndMessages(null)
        entries.asReversed().forEach {
            if (Build.VERSION.SDK_INT >= 33) {
                it.backCallback?.let { callback -> it.backDispatcher?.unregisterOnBackInvokedCallback(callback) }
            }
            runCatching { it.wm.removeViewImmediate(it.root) }
            it.compose.disposeComposition()
            it.owner.destroy()
        }
        entries.clear()
    }

    fun recreate() {
        close()
        attach()
    }
    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
