package com.example.simplesystemui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.simplesystemui.data.NotificationRepository

data class ServiceStatus(val running: Boolean = false, val message: String = "服务尚未启动")

class SystemUIApplication : Application() {
    val notifications by lazy { NotificationRepository(this) }
    var serviceStatus by mutableStateOf(ServiceStatus())
        private set

    fun reportStatus(status: ServiceStatus) {
        serviceStatus = status
    }
}
