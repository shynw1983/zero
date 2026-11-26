package com.zero.todo.feature.tasklist

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.nlp.ParsedQuickInput
import com.zero.todo.domain.model.AntiMisclickMode
import com.zero.todo.domain.model.TaskItem
import com.zero.todo.domain.model.TaskSortMode
import com.zero.todo.domain.model.TaskSwipeActions
import com.zero.todo.feature.folder.NavigationDestination
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
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-mdpi")
class TaskCardGestureTest {
    @Test
    fun `dragging a root between a parent and its first child in All requests nesting`() {
        val parent = TaskItem(id = "1", title = "First task")
        val child = TaskItem(id = "2", title = "Second task", parentTaskId = parent.id)
        val moving = TaskItem(id = "3", title = "Third task")
        val focusRequester = FocusRequester()
        var drop: Triple<String, String?, String?>? = null

        withContent({
            TaskListPane(
                currentDate = LocalDate.of(2026, 11, 26),
                title = "All",
                breadcrumbs = emptyList(),
                selectedDestination = NavigationDestination.All,
                includeSubfolders = false,
                isSearchOpen = false,
                searchQuery = "",
                taskSortMode = TaskSortMode.MANUAL,
                scheduledTodayMinutes = 0,
                dailyCapacityMinutes = 240,
                triageCount = 0,
                tasks = listOf(parent, moving),
                allActiveTasks = listOf(parent, child, moving),
                folderNameById = emptyMap(),
                selectedTaskId = null,
                antiMisclickMode = AntiMisclickMode.BOTH,
                taskSwipeActions = TaskSwipeActions(),
                quickInputText = "",
                parsedQuickInput = ParsedQuickInput("", ""),
                folderSuggestions = emptyList(),
                continuousEntryEnabled = false,
                undoBanner = null,
                quickInputFocusRequester = focusRequester,
                showDrawerButton = false,
                showTreeToggleButton = false,
                onOpenDrawer = {},
                onToggleTreePanel = {},
                onSelectBreadcrumb = {},
                onToggleIncludeSubfolders = {},
                onToggleSearch = {},
                onSearchQueryChange = {},
                onSetSortMode = {},
                onRescheduleOverdueToToday = {},
                onOpenTriage = {},
                onSelectTag = {},
                onRequestCreateSubfolder = {},
                onOpenFocusMode = {},
                onToggleTaskComplete = {},
                onDeleteTask = {},
                onIndentTask = { _, _ -> },
                onOutdentTask = {},
                onSelectTask = {},
                onReorderTask = { task, newParent, before -> drop = Triple(task.id, newParent, before) },
                onQuickInputEdit = {},
                onQuickInputSubmit = {},
                onToggleContinuousEntry = {},
                onVoiceInputRecognized = {},
                onUndo = {},
                onUndoTimeout = {}
            )
        }) { view ->
            val root = view.getChildAt(0) as ViewRootForTest
            val semantics = root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false)
            fun titleCenterY(task: TaskItem) = semantics.single { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == task.title } == true
            }.boundsInRoot.center.y

            val x = view.width / 2f
            val startY = titleCenterY(moving)
            val dropY = (titleCenterY(parent) + titleCenterY(child)) / 2f
            val downTime = SystemClock.uptimeMillis()
            view.touch(downTime, MotionEvent.ACTION_DOWN, x, startY)
            advance(650)
            view.touch(downTime, MotionEvent.ACTION_MOVE, x, dropY)
            view.touch(downTime, MotionEvent.ACTION_UP, x, dropY)

            assertEquals(Triple(moving.id, parent.id, child.id), drop)
        }
    }

    @Test
    fun `handle continues delivering vertical touch events after the card enters dragging state`() {
        withCard { view, events ->
            val handleX = view.width - 24f
            val downTime = SystemClock.uptimeMillis()
            view.touch(downTime, MotionEvent.ACTION_DOWN, handleX, 106f)
            view.touch(downTime, MotionEvent.ACTION_MOVE, handleX, 140f)
            assertEquals(1, events.starts)
            advance(80)
            view.touch(downTime, MotionEvent.ACTION_MOVE, handleX, 188f)
            view.touch(downTime, MotionEvent.ACTION_UP, handleX, 188f)

            assertEquals(1, events.ends)
            assertEquals(0, events.cancels)
            assertTrue(events.draggedDistance > 48f)
            assertEquals(0, events.swipes)
        }
    }

    @Test
    fun `title tap opens details and title long press starts vertical drag`() {
        withCard { view, events ->
            val x = view.width / 2f
            val tapTime = SystemClock.uptimeMillis()
            view.touch(tapTime, MotionEvent.ACTION_DOWN, x, 106f)
            view.touch(tapTime, MotionEvent.ACTION_UP, x, 106f)
            assertEquals(1, events.opens)

            val downTime = SystemClock.uptimeMillis()
            view.touch(downTime, MotionEvent.ACTION_DOWN, x, 106f)
            advance(650)
            assertEquals(1, events.starts)
            view.touch(downTime, MotionEvent.ACTION_MOVE, x, 163f)
            advance(80)
            view.touch(downTime, MotionEvent.ACTION_UP, x, 163f)

            assertEquals(1, events.ends)
            assertEquals(0, events.cancels)
            assertTrue(events.draggedDistance > 40f)
            assertEquals(1, events.opens)
            assertEquals(0, events.swipes)
        }
    }

    private fun withCard(check: (ComposeView, GestureEvents) -> Unit) {
        val events = GestureEvents()
        val task = TaskItem(id = "task", title = "Task")
        var dragging by mutableStateOf(false)
        var dragY by mutableFloatStateOf(0f)
        withContent({
            Box(Modifier.fillMaxSize().padding(top = 80.dp)) {
                AntiMisclickTaskCard(
                    task = task,
                    isSelected = false,
                    isListScrolling = false,
                    isDragging = dragging,
                    dragOffsetY = if (dragging) dragY else 0f,
                    folderBadgeName = null,
                    swipeActions = TaskSwipeActions(),
                    onToggleComplete = { events.swipes++ },
                    onDeleteTask = { events.swipes++ },
                    onSelectTask = { events.opens++ },
                    reorderEnabled = true,
                    onReorderDragStart = {
                        events.starts++
                        dragging = true
                    },
                    onReorderDragDelta = {
                        events.draggedDistance += it
                        dragY += it
                    },
                    onReorderDragEnd = {
                        events.ends++
                        dragging = false
                    },
                    onReorderDragCancel = {
                        events.cancels++
                        dragging = false
                    }
                )
            }
        }) { view -> check(view, events) }
    }

    private fun withContent(content: @Composable () -> Unit, check: (ComposeView) -> Unit) {
        // Advance one simulated display frame at a time while the list's drag effect is running.
        ShadowChoreographer.setPaused(true)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        // Route gesture timeouts through the looper advanced by this test.
        val uiContext = Dispatchers.Main.immediate + AndroidUiDispatcher.CurrentThread[MonotonicFrameClock]!!
        val recomposer = Recomposer(uiContext)
        val recomposition = CoroutineScope(uiContext).launch { recomposer.runRecomposeAndApplyChanges() }
        try {
            val activity = controller.get()
            val view = ComposeView(activity).apply {
                setParentCompositionContext(recomposer)
                setContent { ZeroTheme(hapticsEnabled = false, content = content) }
            }
            activity.setContentView(view)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, 400, 600)
            advance(100)
            check(view)
        } finally {
            controller.pause().stop().destroy()
            recomposition.cancel()
            recomposer.cancel()
            ShadowChoreographer.setPaused(false)
        }
    }

    private fun ComposeView.touch(downTime: Long, action: Int, x: Float, y: Float) {
        advance(20)
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        try {
            dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun advance(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private class GestureEvents {
        var starts = 0
        var ends = 0
        var cancels = 0
        var draggedDistance = 0f
        var opens = 0
        var swipes = 0
    }
}
