package com.zero.todo.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface WidgetRefresher {
    suspend fun refreshAll()
}

@Singleton
class GlanceWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context
) : WidgetRefresher {
    override suspend fun refreshAll() {
        ZeroWidgetUpdater.refreshAll(context)
    }
}

object NoOpWidgetRefresher : WidgetRefresher {
    override suspend fun refreshAll() = Unit
}
