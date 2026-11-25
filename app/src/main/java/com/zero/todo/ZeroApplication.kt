package com.zero.todo

import android.app.Application
import com.zero.todo.core.notification.ZeroNotificationHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ZeroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ZeroNotificationHelper.ensureChannels(this)
    }
}
