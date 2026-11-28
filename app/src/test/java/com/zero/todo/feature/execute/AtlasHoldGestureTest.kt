package com.zero.todo.feature.execute

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-mdpi")
class AtlasHoldGestureTest {
    @Test
    fun `holding for 300 ms completes once while the finger is still down`() = withControl { view, events ->
        val downTime = SystemClock.uptimeMillis()
        view.touch(downTime, MotionEvent.ACTION_DOWN)
        advance(299)
        assertEquals(0, events.completed)
        assertTrue(events.progress.last() in 0.5f..<1f)

        advance(1)
        assertEquals(1, events.completed)
        assertEquals(1f, events.progress.last())
        advance(500)
        view.touch(downTime, MotionEvent.ACTION_UP)
        assertEquals(1, events.completed)
        assertEquals(0f, events.progress.last())
    }

    @Test
    fun `early release and cancellation reset progress without completion`() = withControl { view, events ->
        for (endAction in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL)) {
            val downTime = SystemClock.uptimeMillis()
            view.touch(downTime, MotionEvent.ACTION_DOWN)
            advance(80)
            assertTrue(events.progress.last() > 0f)

            view.touch(downTime, endAction)
            advance(400)
            assertEquals(0, events.completed)
            assertEquals(0f, events.progress.last())
        }
    }

    @Test
    fun `changing place or task cancels an active hold`() = withControl { view, events ->
        for (target in listOf(events.placeId, events.taskId)) {
            val downTime = SystemClock.uptimeMillis()
            view.touch(downTime, MotionEvent.ACTION_DOWN)
            advance(80)
            assertTrue(events.progress.last() > 0f)

            target.value += "-next"
            advance(400)
            assertEquals(0, events.completed)
            assertEquals(0f, events.progress.last())
            view.touch(downTime, MotionEvent.ACTION_UP)
        }
    }

    private fun withControl(check: (ComposeView, HoldEvents) -> Unit) {
        ShadowChoreographer.setPaused(true)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val uiContext = Dispatchers.Main.immediate + AndroidUiDispatcher.CurrentThread[MonotonicFrameClock]!!
        val recomposer = Recomposer(uiContext)
        val recomposition = CoroutineScope(uiContext).launch { recomposer.runRecomposeAndApplyChanges() }
        try {
            val events = HoldEvents()
            val view = ComposeView(controller.get()).apply {
                setParentCompositionContext(recomposer)
                setContent {
                    Box(
                        Modifier
                            .size(100.dp)
                            .atlasHoldToComplete(
                                placeId = events.placeId.value,
                                taskId = events.taskId.value,
                                onPress = {},
                                onProgress = { events.progress.add(it) },
                                onComplete = { events.completed++ }
                            )
                    )
                }
            }
            controller.get().setContentView(view)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, 400, 600)
            advance(100)
            check(view, events)
        } finally {
            controller.pause().stop().destroy()
            recomposition.cancel()
            recomposer.cancel()
            ShadowChoreographer.setPaused(false)
        }
    }

    private fun ComposeView.touch(downTime: Long, action: Int) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 40f, 40f, 0)
        try {
            dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun advance(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private class HoldEvents {
        val placeId = mutableStateOf("place")
        val taskId = mutableStateOf("task")
        val progress = mutableListOf<Float>()
        var completed = 0
    }
}
