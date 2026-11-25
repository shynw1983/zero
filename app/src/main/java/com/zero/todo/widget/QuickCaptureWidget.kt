package com.zero.todo.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
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
import com.zero.todo.feature.folder.NavigationDestination

private val CapsuleObsidian = Color(0xFF09090B)
private val CapsuleSurface = Color(0xFF141417)
private val CapsuleAccent = Color(0xFF7B9CC2)
private val CapsuleText = Color(0xFFF4F4F5)

/**
 * 1x1 minimalist dark quick-capture capsule widget.
 * Launches ZERO directly into Inbox with the Quick Input Capsule focused (< 3s capture).
 */
class QuickCaptureWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ZeroWidgetIntents.EXTRA_DESTINATION_KEY, NavigationDestination.Inbox.toStorageKey())
            putExtra(ZeroWidgetIntents.EXTRA_LAUNCH_QUICK_INPUT, true)
        }

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(CapsuleObsidian)
                    .cornerRadius(24.dp)
                    .padding(4.dp)
                    .clickable(actionStartActivity(launchIntent)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(CapsuleSurface)
                        .cornerRadius(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(6.dp)
                            .background(CapsuleAccent)
                            .cornerRadius(3.dp)
                    ) {}
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = "+",
                        style = TextStyle(
                            color = ColorProvider(CapsuleText),
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
}
