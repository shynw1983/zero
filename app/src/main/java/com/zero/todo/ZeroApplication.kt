package com.zero.todo

import android.app.Application
import com.zero.todo.core.designsystem.AppIconSwitcher
import com.zero.todo.core.notification.ZeroNotificationHelper
import com.zero.todo.data.preferences.UserPreferencesRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ZeroApplication : Application() {

    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository
    @Inject lateinit var appIconSwitcher: AppIconSwitcher

    override fun onCreate() {
        super.onCreate()
        ZeroNotificationHelper.ensureChannels(this)
        // Keep launcher icon aligned with saved accent (no-op when already in sync)
        appIconSwitcher.syncAccentIcon(userPreferencesRepository.preferences.value.accent)
    }
}
