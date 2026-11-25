package com.zero.todo.feature.focus

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zero.todo.data.local.ZeroDatabase
import com.zero.todo.data.repository.FocusRepositoryImpl
import com.zero.todo.data.repository.TaskRepositoryImpl
import com.zero.todo.domain.model.FocusMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusTimerControllerTest {

    private lateinit var database: ZeroDatabase
    private lateinit var taskRepository: TaskRepositoryImpl
    private lateinit var focusRepository: FocusRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ZeroDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        taskRepository = TaskRepositoryImpl(database, database.taskDao(), database.folderDao())
        focusRepository = FocusRepositoryImpl(database.focusSessionDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `2-Minute Starter finishes with gentle prompt, persists session, and seamlessly transitions to Pomodoro`() = runBlocking {
        val task = taskRepository.createTask("Overcome procrastination")
        val controller = FocusTimerController(
            scope = this,
            focusRepository = focusRepository,
            twoMinSeconds = 2,
            pomodoroSeconds = 3,
            tickIntervalMs = 20L
        )

        // Start 2-Minute Starter
        controller.openFocus(task = task, initialMode = FocusMode.TWO_MIN_STARTER, autoStart = true)
        val afterTwoMin = withTimeout(5_000L) {
            controller.state.first { it.phase == FocusPhase.TWO_MIN_FINISHED_PROMPT }
        }
        assertEquals(FocusHapticKind.GENTLE_TWO_MIN_DONE, afterTwoMin.hapticKind)
        assertEquals(0, afterTwoMin.remainingSeconds)

        // Verify TWO_MIN_STARTER recorded in Room FocusSessionEntity
        val sessionsAfterStarter = withTimeout(5_000L) {
            focusRepository.observeSessionsForTask(task.id).first { it.size == 1 }
        }
        assertEquals(FocusMode.TWO_MIN_STARTER, sessionsAfterStarter.single().mode)
        assertEquals(2, sessionsAfterStarter.single().durationSeconds)

        // One-tap seamless continuation into 25-minute Pomodoro
        controller.continueToPomodoro()
        assertEquals(FocusMode.POMODORO, controller.state.value.mode)
        assertEquals(FocusPhase.RUNNING, controller.state.value.phase)

        val afterPomodoro = withTimeout(5_000L) {
            controller.state.first { it.phase == FocusPhase.POMODORO_COMPLETED }
        }
        assertEquals(FocusHapticKind.HEAVY_POMODORO_DONE, afterPomodoro.hapticKind)

        // Verify both sessions recorded in FocusSessionEntity
        val allSessions = withTimeout(5_000L) {
            focusRepository.observeSessionsForTask(task.id).first { it.size == 2 }
        }
        assertTrue(allSessions.any { it.mode == FocusMode.TWO_MIN_STARTER })
        assertTrue(allSessions.any { it.mode == FocusMode.POMODORO && it.durationSeconds == 3 })
    }
}
