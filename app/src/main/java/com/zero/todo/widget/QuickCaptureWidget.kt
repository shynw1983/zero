package com.zero.todo.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.zero.todo.MainActivity

/**
 * 1x1 minimalist configurable quick-capture capsule widget.
 * Supports configurable theme + accent + target capture destination (Inbox / Today / Folder).
 */
class QuickCaptureWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val manager = GlanceAppWidgetManager(context)
        val appWidgetId = runCatching { manager.getAppWidgetId(id) }
            .getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)

        val config = WidgetConfigStore.load(context, appWidgetId, defaultScope = WidgetFilterScope.INBOX)
        val (appDark, appAccent) = readAppThemePrefs(context)
        val palette = resolveWidgetPalette(config, appDark, appAccent)
        val destinationKey = WidgetTaskQueryEngine.destinationStorageKey(config)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("zero://widget-quick/$appWidgetId")
            putExtra(ZeroWidgetIntents.EXTRA_DESTINATION_KEY, destinationKey)
            putExtra(ZeroWidgetIntents.EXTRA_LAUNCH_QUICK_INPUT, true)
        }

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(palette.background)
                    .cornerRadius(24.dp)
                    .padding(4.dp)
                    .clickable(actionStartActivity(launchIntent)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(palette.cardSurface)
                        .cornerRadius(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(6.dp)
                            .background(palette.accent)
                            .cornerRadius(3.dp)
                    ) {}
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = "+",
                        style = TextStyle(
                            color = ColorProvider(palette.textPrimary),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}

class QuickCaptureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickCaptureWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetConfigStore.delete(context, appWidgetIds)
    }
}
