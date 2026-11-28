package com.zero.todo.feature.folder

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import com.zero.todo.core.designsystem.ZeroAccent
import com.zero.todo.core.designsystem.ZeroHaptics
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.data.repository.TagWithCount
import com.zero.todo.domain.model.Folder
import com.zero.todo.domain.model.FolderTreeBuilder
import com.zero.todo.domain.model.TagItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class SidebarTreeGestureTest {
    private val frameClock = BroadcastFrameClock()

    @Test
    fun `both sidebar trees accept direct dragging and cancel other section drops`() {
        val folders = listOf(Folder("a", name = "Project A"), Folder("b", name = "Project B"))
        val tags = listOf(
            TagWithCount(TagItem("c", name = "Label C"), 0),
            TagWithCount(TagItem("d", name = "Label D"), 0)
        )
        val drops = mutableListOf<Drop>()
        val feedback = mutableListOf<HapticFeedbackType>()
        var menuOpened = 0
        withSidebar(
            folders, tags, onOpenMenu = { menuOpened++ }, onDrop = { drops += it }, onHapticFeedback = { feedback += it }
        ) { view ->
            val folderY = view.titleBounds("Project A").center.y
            val secondFolderY = view.titleBounds("Project B").center.y
            val tagY = view.titleBounds("Label C").center.y
            val secondTagY = view.titleBounds("Label D").center.y
            val gripX = view.width - 36f
            val downTime = SystemClock.uptimeMillis()
            view.touch(downTime, MotionEvent.ACTION_DOWN, gripX, folderY)
            view.touch(downTime, MotionEvent.ACTION_UP, gripX, folderY)
            assertEquals(1, menuOpened)

            view.dragFromGrip(gripX, folderY, secondFolderY)
            view.dragFromGrip(gripX, secondTagY, tagY - 18f)
            view.dragFromGrip(gripX, folderY, tagY)
            view.dragFromGrip(gripX, folderY, secondFolderY, endAction = MotionEvent.ACTION_CANCEL)

            assertEquals(
                listOf(
                    Drop(SidebarTree.FOLDERS, "a", "b", null, null),
                    Drop(SidebarTree.TAGS, "d", null, null, "c")
                ),
                drops
            )
            assertEquals(1, menuOpened)
            assertEquals(emptyList<HapticFeedbackType>(), feedback)
        }
    }

    @Test
    fun `drag pickup and a new valid landing position each give one light tick`() {
        val folders = listOf(Folder("a", name = "Project A"), Folder("b", name = "Project B"))
        val feedback = mutableListOf<HapticFeedbackType>()
        val tick = HapticFeedbackType.TextHandleMove
        withSidebar(folders, onDrop = {}, hapticsEnabled = true, onHapticFeedback = { feedback += it }) { view ->
            val x = view.width - 36f
            val startY = view.titleBounds("Project A").center.y
            val targetY = view.titleBounds("Project B").center.y
            val downTime = SystemClock.uptimeMillis()
            view.touch(downTime, MotionEvent.ACTION_DOWN, x, startY)
            assertEquals(emptyList<HapticFeedbackType>(), feedback)
            view.touch(downTime, MotionEvent.ACTION_MOVE, x, startY + 18f)
            assertEquals(listOf(tick), feedback)

            advance(80)
            view.touch(downTime, MotionEvent.ACTION_MOVE, x, targetY)
            advance(20)
            assertEquals(listOf(tick, tick), feedback)

            advance(80)
            view.touch(downTime, MotionEvent.ACTION_MOVE, x, targetY + 2f)
            advance(20)
            view.touch(downTime, MotionEvent.ACTION_MOVE, x, startY)
            advance(20)
            view.touch(downTime, MotionEvent.ACTION_CANCEL, x, startY)
            assertEquals(listOf(tick, tick), feedback)
        }
    }

    @Test
    fun `dragging near the list edge scrolls while the source is off screen`() {
        val folders = List(20) { index -> Folder("$index", name = "Folder $index", sortOrder = index.toDouble()) }
        val drops = mutableListOf<Drop>()
        withSidebar(folders, onDrop = { drops += it }) { view ->
            val x = view.width - 36f
            val startY = view.titleBounds("Folder 0").center.y
            val edgeY = view.titleBounds("Trash").center.y - 42f
            val downTime = SystemClock.uptimeMillis()
            view.touch(downTime, MotionEvent.ACTION_DOWN, x, startY)
            view.touch(downTime, MotionEvent.ACTION_MOVE, x, startY + 28f)
            view.touch(downTime, MotionEvent.ACTION_MOVE, x, edgeY)
            repeat(120) { advance(20) }

            val source = view.textBounds("Folder 0").firstOrNull { it.width > 0f && it.height > 0f && it.left < x / 2 }
            assertNull("The original row should have scrolled out of view", source)
            val targetY = view.titleBounds("Folder 19").center.y
            view.touch(downTime, MotionEvent.ACTION_MOVE, x, targetY)
            view.touch(downTime, MotionEvent.ACTION_UP, x, targetY)

            assertEquals(listOf(Drop(SidebarTree.FOLDERS, "0", "19", null, null)), drops)
        }
    }

    private fun withSidebar(
        folders: List<Folder>,
        tags: List<TagWithCount> = emptyList(),
        onOpenMenu: () -> Unit = {},
        onDrop: (Drop) -> Unit,
        hapticsEnabled: Boolean = false,
        onHapticFeedback: (HapticFeedbackType) -> Unit = {},
        check: (ComposeView) -> Unit
    ) {
        val folderTree = FolderTreeBuilder.build(folders)
        ShadowChoreographer.setPaused(true)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val uiContext = Dispatchers.Main.immediate + frameClock
        val recomposer = Recomposer(uiContext)
        val recomposition = CoroutineScope(uiContext).launch { recomposer.runRecomposeAndApplyChanges() }
        try {
            val activity = controller.get()
            ZeroHaptics.resolveVibrator(activity.applicationContext)?.let { shadowOf(it).setHasVibrator(false) }
            val hapticFeedback = object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                    onHapticFeedback(hapticFeedbackType)
                }
            }
            val view = ComposeView(activity).apply {
                setParentCompositionContext(recomposer)
                setContent {
                    ZeroTheme(hapticsEnabled = hapticsEnabled) {
                        CompositionLocalProvider(LocalHapticFeedback provides hapticFeedback) {
                            FolderTreePanel(
                                folderTree = folderTree,
                                tagsWithCounts = tags,
                                selectedDestination = NavigationDestination.Inbox,
                                smartViewCounts = SmartViewCounts(),
                                triageCount = 0,
                                includeSubfolders = false,
                                isDarkTheme = true,
                                activeAccent = ZeroAccent.SlateBlue,
                                onSelectDestination = {},
                                onOpenTriage = {},
                                onToggleFolderExpanded = { _, _ -> },
                                onRequestCreateFolder = {},
                                onRequestFolderMenu = { onOpenMenu() },
                                onRequestTagMenu = { onOpenMenu() },
                                onPlaceFolder = { id, parent, preceding, following ->
                                    onDrop(Drop(SidebarTree.FOLDERS, id, parent, preceding, following))
                                },
                                onPlaceTag = { id, parent, preceding, following ->
                                    onDrop(Drop(SidebarTree.TAGS, id, parent, preceding, following))
                                },
                                onToggleIncludeSubfolders = {},
                                onToggleTheme = {},
                                onToggleAccent = {},
                                onOpenSettings = {}
                            )
                        }
                    }
                }
            }
            activity.setContentView(view)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(950, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, 400, 950)
            advance(100)
            check(view)
        } finally {
            controller.pause().stop().destroy()
            recomposition.cancel()
            recomposer.cancel()
            ShadowChoreographer.setPaused(false)
        }
    }

    private fun ComposeView.titleBounds(title: String): Rect = textBounds(title).first()

    private fun ComposeView.textBounds(title: String): List<Rect> {
        val root = getChildAt(0) as ViewRootForTest
        return root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false).filter { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == title } == true
        }.map { it.boundsInRoot }
    }

    private fun ComposeView.dragFromGrip(x: Float, startY: Float, endY: Float, endAction: Int = MotionEvent.ACTION_UP) {
        val downTime = SystemClock.uptimeMillis()
        touch(downTime, MotionEvent.ACTION_DOWN, x, startY)
        touch(downTime, MotionEvent.ACTION_MOVE, x, (startY + endY) / 2f)
        touch(downTime, MotionEvent.ACTION_MOVE, x, endY)
        touch(downTime, endAction, x, endY)
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

    private fun advance(millis: Long) {
        val looper = shadowOf(Looper.getMainLooper())
        looper.idleFor(Duration.ofMillis(millis))
        // A held drag continues scrolling on display frames between touch events.
        frameClock.sendFrame(SystemClock.uptimeMillis() * 1_000_000)
        looper.idle()
    }

    private data class Drop(
        val tree: SidebarTree,
        val sourceId: String,
        val parentId: String?,
        val precedingId: String?,
        val followingId: String?
    )
}
