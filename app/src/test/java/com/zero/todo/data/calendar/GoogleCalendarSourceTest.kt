package com.zero.todo.data.calendar

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleCalendarSourceTest {
    private val date = LocalDate.of(2026, 6, 1)
    private val zone = ZoneId.of("Asia/Tokyo")
    private val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver

    @Test
    fun `reads only Google calendar occurrences overlapping the selected day`() {
        val provider = TestCalendarProvider().apply {
            rows = listOf(
                event(1, "Recurring appointment", "2026-06-01T04:00:00Z", "2026-06-01T05:00:00Z"),
                event(1, "Recurring appointment", "2026-06-01T01:00:00Z", "2026-06-01T02:00:00Z"),
                event(2, "All day", "2026-06-01T00:00:00Z", "2026-06-02T00:00:00Z", allDay = true),
                event(3, "Overnight", "2026-05-31T14:00:00Z", "2026-05-31T16:00:00Z"),
                event(4, "Previous all day", "2026-05-31T00:00:00Z", "2026-06-01T00:00:00Z", allDay = true),
                event(5, "Next local day", "2026-06-01T15:00:00Z", "2026-06-01T16:00:00Z"),
                event(6, "Canceled", "2026-06-01T02:00:00Z", "2026-06-01T03:00:00Z", status = Events.STATUS_CANCELED),
                event(7, "Other calendar", "2026-06-01T02:00:00Z", "2026-06-01T03:00:00Z", calendarId = 9)
            )
        }
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)

        val day = GoogleCalendarSource(resolver).readDay(date, zone) as GoogleCalendarDay.Ready

        assertEquals(listOf("All day", "Overnight", "Recurring appointment", "Recurring appointment"), day.events.map { it.title })
        assertEquals(4, day.events.map { it.occurrenceKey }.distinct().size)
        assertTrue(day.events.all { it.calendarName == "Personal" })
        assertEquals(millis("2026-05-31T15:00:00Z"), provider.queryStart)
        assertEquals(millis("2026-06-02T00:00:00Z"), provider.queryEnd)
    }

    @Test
    fun `refreshes when the system calendar provider reports a change`() = runBlocking {
        val provider = TestCalendarProvider()
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)
        val updates = Channel<GoogleCalendarDay>(Channel.BUFFERED)
        val observer = launch { GoogleCalendarSource(resolver).observeDay(date, zone).collect { updates.send(it) } }
        try {
            withTimeout(5_000L) {
                assertEquals(GoogleCalendarDay.Ready(emptyList()), updates.receive())
                provider.rows = listOf(event(1, "New meeting", "2026-06-01T02:00:00Z", "2026-06-01T03:00:00Z"))
                resolver.notifyChange(Events.CONTENT_URI, null)
                val refreshed = updates.receive() as GoogleCalendarDay.Ready
                assertEquals("New meeting", refreshed.events.single().title)
            }
        } finally {
            observer.cancel()
        }
    }

    @Test
    fun `day boundaries use local midnight for timed events and UTC for all-day dates`() {
        val window = CalendarDayWindow(LocalDate.of(2026, 3, 8), ZoneId.of("America/Los_Angeles"))
        val localStart = millis("2026-03-08T08:00:00Z")
        val localEnd = millis("2026-03-09T07:00:00Z")
        val utcStart = millis("2026-03-08T00:00:00Z")
        val utcEnd = millis("2026-03-09T00:00:00Z")

        assertEquals(utcStart, window.queryStart)
        assertEquals(localEnd, window.queryEnd)
        assertTrue(window.overlaps(utcStart, utcEnd, isAllDay = true))
        assertFalse(window.overlaps(utcStart, localStart, isAllDay = false))
        assertTrue(window.overlaps(localStart, localStart, isAllDay = false))
        assertFalse(window.overlaps(localEnd, localEnd, isAllDay = false))
        assertTrue(window.overlaps(localEnd - 1, localEnd + 1, isAllDay = false))
    }

    private class TestCalendarProvider : ContentProvider() {
        @Volatile var rows = emptyList<Array<Any>>()
        var queryStart = 0L
        var queryEnd = 0L

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor = MatrixCursor(requireNotNull(projection)).apply {
            if (uri == Calendars.CONTENT_URI) {
                assertEquals(listOf("com.google"), selectionArgs?.toList())
                assertTrue(requireNotNull(selection).contains("${Calendars.VISIBLE} = 1"))
                addRow(arrayOf(4L, "Personal"))
            } else {
                assertEquals(Instances.CONTENT_URI.pathSegments, uri.pathSegments.dropLast(2))
                assertEquals(listOf("4"), selectionArgs?.toList())
                queryStart = uri.pathSegments[uri.pathSegments.lastIndex - 1].toLong()
                queryEnd = uri.pathSegments.last().toLong()
                rows.forEach(::addRow)
            }
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri = error("Read-only provider")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
            error("Read-only provider")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Read-only provider")
    }

    private fun event(
        id: Long,
        title: String,
        start: String,
        end: String,
        allDay: Boolean = false,
        status: Int = Events.STATUS_CONFIRMED,
        calendarId: Long = 4L
    ): Array<Any> = arrayOf(id, calendarId, title, millis(start), millis(end), if (allDay) 1 else 0, status)

    private fun millis(instant: String): Long = Instant.parse(instant).toEpochMilli()
}
