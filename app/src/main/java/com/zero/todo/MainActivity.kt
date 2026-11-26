package com.zero.todo

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zero.todo.data.preferences.SharedPrefsUserPreferencesRepository
import com.zero.todo.domain.model.AppLanguage
import com.zero.todo.feature.main.WidgetLaunchArgs
import com.zero.todo.feature.main.ZeroAppRoot
import com.zero.todo.widget.ZeroWidgetIntents
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var widgetLaunchArgs by mutableStateOf<WidgetLaunchArgs?>(null)

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences(
            SharedPrefsUserPreferencesRepository.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val savedLanguage = AppLanguage.fromStorage(
            prefs.getString("app_language", AppLanguage.SYSTEM.name)
        )
        val tag = savedLanguage.languageTag
        val wrapped = if (tag != null) {
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration).apply {
                setLocale(locale)
                setLocales(LocaleList(locale))
            }
            newBase.createConfigurationContext(config)
        } else {
            newBase
        }
        super.attachBaseContext(wrapped)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        handleIntent(intent)
        setContent {
            ZeroAppRoot(widgetLaunchArgs = widgetLaunchArgs)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val destKey = intent.getStringExtra(ZeroWidgetIntents.EXTRA_DESTINATION_KEY)
        val taskId = intent.getStringExtra(ZeroWidgetIntents.EXTRA_TASK_ID)
        val quickInput = intent.getBooleanExtra(ZeroWidgetIntents.EXTRA_LAUNCH_QUICK_INPUT, false)
        if (destKey != null || taskId != null || quickInput) {
            widgetLaunchArgs = WidgetLaunchArgs(
                id = System.nanoTime(),
                destinationKey = destKey,
                taskId = taskId,
                launchQuickInput = quickInput
            )
        }
    }
}
