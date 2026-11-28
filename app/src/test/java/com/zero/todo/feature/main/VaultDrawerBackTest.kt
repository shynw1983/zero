package com.zero.todo.feature.main

import android.os.Looper
import android.os.SystemClock
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.window.core.layout.WindowWidthSizeClass
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.WorkspaceMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
@Config(sdk = [34], qualifiers = "en-w400dp-h950dp-mdpi")
class VaultDrawerBackTest {
    private val frameClock = BroadcastFrameClock()

    @Test
    fun `compact drawer consumes back while opening and after it opens`() =
        withVault(WindowWidthSizeClass.COMPACT) { view, back, events ->
            view.openDrawer()
            advanceFrames(4)
            assertFalse(view.isDrawerOpen())
            back()
            advanceFrames()
            assertFalse(view.isDrawerOpen())
            assertEquals(0, events.fallbackBacks)

            view.openDrawer()
            advanceFrames()
            assertTrue(view.isDrawerOpen())
            back()
            advanceFrames()
            assertFalse(view.isDrawerOpen())
            assertEquals(0, events.fallbackBacks)

            back()
            assertEquals(1, events.fallbackBacks)
        }

    @Test
    @Config(qualifiers = "en-w720dp-h950dp-mdpi")
    fun `medium drawer closes before backing out of the selected task`() =
        withVault(WindowWidthSizeClass.MEDIUM, TaskItem(id = "task", title = "Task")) { view, back, events ->
            view.openDrawer()
            advanceFrames()
            assertTrue(view.isDrawerOpen())

            back()
            advanceFrames()
            assertFalse(view.isDrawerOpen())
            assertEquals("task", events.state.value.selectedTaskId)
            assertEquals(0, events.fallbackBacks)

            back()
            assertEquals(null, events.state.value.selectedTaskId)
            assertEquals(0, events.fallbackBacks)
        }

    private fun withVault(
        widthClass: WindowWidthSizeClass,
        selectedTask: TaskItem? = null,
        check: (ComposeView, () -> Unit, VaultEvents) -> Unit
    ) {
        ShadowChoreographer.setPaused(true)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val uiContext = Dispatchers.Main.immediate + frameClock
        val recomposer = Recomposer(uiContext)
        val recomposition = CoroutineScope(uiContext).launch { recomposer.runRecomposeAndApplyChanges() }
        try {
            val activity = controller.get()
            val events = VaultEvents(selectedTask)
            activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { events.fallbackBacks++ }
            })
            val view = ComposeView(activity).apply {
                setParentCompositionContext(recomposer)
                setContent {
                    ZeroTheme(hapticsEnabled = false) {
                        ZeroAdaptiveShell(
                            uiState = events.state.value,
                            callbacks = ZeroShellCallbacks(onSelectTask = { id ->
                                events.state.value = events.state.value.copy(
                                    selectedTaskId = id,
                                    selectedTask = selectedTask?.takeIf { it.id == id }
                                )
                            }),
                            windowWidthSizeClass = widthClass
                        )
                    }
                }
            }
            activity.setContentView(view)
            val width = if (widthClass == WindowWidthSizeClass.COMPACT) 400 else 720
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(950, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, width, 950)
            advanceFrames()
            check(view, activity.onBackPressedDispatcher::onBackPressed, events)
        } finally {
            controller.pause().stop().destroy()
            recomposition.cancel()
            recomposer.cancel()
            ShadowChoreographer.setPaused(false)
        }
    }

    private fun ComposeView.openDrawer() {
        val description = context.getString(R.string.cd_open_drawer)
        val button = semanticsNodes().first { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(description) == true
        }
        assertTrue(button.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true)
    }

    private fun ComposeView.isDrawerOpen(): Boolean = semanticsNodes().any {
        it.config.getOrNull(SemanticsActions.Dismiss) != null
    }

    private fun ComposeView.semanticsNodes() = (getChildAt(0) as ViewRootForTest)
        .semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)

    private fun advanceFrames(frames: Int = 50) {
        val looper = shadowOf(Looper.getMainLooper())
        repeat(frames) {
            looper.idleFor(Duration.ofMillis(16))
            frameClock.sendFrame(SystemClock.uptimeMillis() * 1_000_000)
            looper.idle()
        }
    }

    private class VaultEvents(task: TaskItem?) {
        val state = mutableStateOf(MainUiState(
            workspaceMode = WorkspaceMode.VAULT,
            selectedTaskId = task?.id,
            selectedTask = task
        ))
        var fallbackBacks = 0
    }
}
