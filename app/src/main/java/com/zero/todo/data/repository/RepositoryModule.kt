package com.zero.todo.data.repository

import com.zero.todo.core.designsystem.AndroidAppIconSwitcher
import com.zero.todo.core.designsystem.AppIconSwitcher
import com.zero.todo.core.geofence.AndroidGeofenceSyncController
import com.zero.todo.core.geofence.GeofenceSyncController
import com.zero.todo.core.notification.AlarmReminderScheduler
import com.zero.todo.core.notification.ReminderScheduler
import com.zero.todo.data.ai.AiEngine
import com.zero.todo.data.ai.ZeroAiEngine
import com.zero.todo.data.preferences.SharedPrefsUserPreferencesRepository
import com.zero.todo.data.preferences.UserPreferencesRepository
import com.zero.todo.widget.GlanceWidgetRefresher
import com.zero.todo.widget.WidgetRefresher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFolderRepository(impl: FolderRepositoryImpl): FolderRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: TagRepositoryImpl): TagRepository

    @Binds
    @Singleton
    abstract fun bindFocusRepository(impl: FocusRepositoryImpl): FocusRepository

    @Binds
    @Singleton
    abstract fun bindWidgetRefresher(impl: GlanceWidgetRefresher): WidgetRefresher

    @Binds
    @Singleton
    abstract fun bindAppIconSwitcher(impl: AndroidAppIconSwitcher): AppIconSwitcher

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: AlarmReminderScheduler): ReminderScheduler

    @Binds
    @Singleton
    abstract fun bindGeofenceSyncController(impl: AndroidGeofenceSyncController): GeofenceSyncController

    @Binds
    abstract fun bindAiEngine(impl: ZeroAiEngine): AiEngine

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        impl: SharedPrefsUserPreferencesRepository
    ): UserPreferencesRepository
}
