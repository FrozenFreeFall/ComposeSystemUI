package com.example.simplesystemui.platform

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Insets
import android.os.Binder
import android.os.Process
import android.view.WindowManager
import androidx.annotation.RequiresApi
import java.lang.reflect.Array as ReflectArray

/** All non-SDK window access is isolated here. Intended ONLY for platform-authorized system UID. */
object PlatformWindowApi {
    fun requireSystemEnvironment(context: Context) {
        check(Process.myUid() % 100000 == Process.SYSTEM_UID) {
            "需要 android.uid.system，当前 uid=${Process.myUid()}；请使用设备平台签名并正确部署。"
        }
        listOf(
            "android.permission.STATUS_BAR_SERVICE",
            "android.permission.INTERNAL_SYSTEM_WINDOW",
            "android.permission.MONITOR_INPUT",
        ).forEach {
            check(context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED) { "缺少系统权限：$it" }
        }
    }

    /** Provide actual WMS insets, so apps which respect insets lay out around our bars. */
    @RequiresApi(36)
    fun provideBarInsets(params: WindowManager.LayoutParams, insetsType: Int, size: Insets) {
        val clazz = Class.forName("android.view.InsetsFrameProvider")
        val provider = clazz.getConstructor(Any::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .newInstance(Binder(), 0, insetsType)
        val frameSource = clazz.getField("SOURCE_FRAME").getInt(null)
        clazz.getMethod("setSource", Int::class.javaPrimitiveType).invoke(provider, frameSource)
        clazz.getMethod("setInsetsSize", Insets::class.java).invoke(provider, size)
        val array = ReflectArray.newInstance(clazz, 1)
        ReflectArray.set(array, 0, provider)
        WindowManager.LayoutParams::class.java.getField("providedInsets").set(params, array)
    }

}
