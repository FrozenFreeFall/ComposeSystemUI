package com.example.simplesystemui

import android.app.Application
import com.example.simplesystemui.data.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ServiceStatus(val running: Boolean = false, val message: String = "服务尚未启动")

/** Process dependencies only; the Service owns the MVI Store and the windows. */
class SystemUIApplication : Application() {
    val deviceRepository by lazy { DeviceRepository(this) }
    private val mutableStatus = MutableStateFlow(ServiceStatus())
    val serviceStatus = mutableStatus.asStateFlow()
    fun reportStatus(status: ServiceStatus) { mutableStatus.value = status }
}
