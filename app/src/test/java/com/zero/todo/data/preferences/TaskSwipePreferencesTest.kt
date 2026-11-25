package com.zero.todo.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zero.todo.domain.model.AntiMisclickMode
import com.zero.todo.domain.model.TaskSwipeAction
import com.zero.todo.domain.model.TaskSwipeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskSwipePreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `existing preferences default to completion in both directions`() {
        val actions = SharedPrefsUserPreferencesRepository(context).preferences.value.taskSwipeActions

        assertEquals(TaskSwipeAction.COMPLETE, actions.actionForOffset(-1f))
        assertEquals(TaskSwipeAction.COMPLETE, actions.actionForOffset(1f))
        assertEquals(TaskSwipeAction.NONE, actions.actionForOffset(0f))
    }

    @Test
    fun `each direction survives reload and can be disabled independently`() {
        val repository = SharedPrefsUserPreferencesRepository(context)
        repository.setTaskSwipeAction(TaskSwipeDirection.LEFT, TaskSwipeAction.DELETE)
        repository.setTaskSwipeAction(TaskSwipeDirection.RIGHT, TaskSwipeAction.NONE)
        repository.setAntiMisclickMode(AntiMisclickMode.HOLD_CONFIRM)

        val restored = SharedPrefsUserPreferencesRepository(context)
        assertFalse(restored.preferences.value.antiMisclickMode.allowSwipe)
        restored.setAntiMisclickMode(AntiMisclickMode.BOTH)
        val actions = restored.preferences.value.taskSwipeActions

        assertEquals(TaskSwipeAction.DELETE, actions.actionForOffset(-1f))
        assertEquals(TaskSwipeAction.NONE, actions.actionForOffset(1f))
    }
}
