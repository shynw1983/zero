package com.zero.todo.data.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import com.zero.todo.core.common.ZeroDateFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

internal data class GoogleCalendarEvent(
    val eventId: Long,
    val title: String?,
    val calendarName: String?,
    val beginsAt: Long,
    val endsAt: Long,
    val isAllDay: Boolean
) {
    val occurrenceKey: String get() = "$eventId:$beginsAt"
}

internal sealed interface GoogleCalendarDay {
    data object PermissionRequired : GoogleCalendarDay
    data object Loading : GoogleCalendarDay
    data object NoCalendars : GoogleCalendarDay
    data object Unavailable : GoogleCalendarDay
    data class Ready(val events: List<GoogleCalendarEvent>) : GoogleCalendarDay
}

internal class CalendarDayWindow(date: LocalDate, zone: ZoneId) {
    private val localStart = ZeroDateFormatter.startOfDayMillis(date, zone)
    private val localEnd = ZeroDateFormatter.endOfDayExclusiveMillis(date, zone)
    // Android encodes all-day dates at UTC midnight, independent of the device time zone.
    private val utcStart = ZeroDateFormatter.startOfDayMillis(date, ZoneOffset.UTC)
    private val utcEnd = ZeroDateFormatter.endOfDayExclusiveMillis(date, ZoneOffset.UTC)

    val queryStart = minOf(localStart, utcStart)
    val queryEnd = maxOf(localEnd, utcEnd)

    fun overlaps(beginsAt: Long, endsAt: Long, isAllDay: Boolean): Boolean {
        val dayStart = if (isAllDay) utcStart else localStart
        val dayEnd = if (isAllDay) utcEnd else localEnd
        return if (endsAt <= beginsAt) {
            beginsAt in dayStart until dayEnd
        } else {
            beginsAt < dayEnd && endsAt > dayStart
        }
    }
}

internal class GoogleCalendarSource(private val resolver: ContentResolver) {
    fun observeDay(date: LocalDate, zone: ZoneId): Flow<GoogleCalendarDay> = callbackFlow<Unit> {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()
        .map {
            try {
                readDay(date, zone)
            } catch (error: CancellationException) {
                throw error
            } catch (_: SecurityException) {
                GoogleCalendarDay.PermissionRequired
            } catch (_: RuntimeException) {
                GoogleCalendarDay.Unavailable
            }
        }
        .catch { error ->
            when (error) {
                is CancellationException -> throw error
                is SecurityException -> emit(GoogleCalendarDay.PermissionRequired)
                is RuntimeException -> emit(GoogleCalendarDay.Unavailable)
                else -> throw error
            }
        }
        .flowOn(Dispatchers.IO)

    internal fun readDay(date: LocalDate, zone: ZoneId): GoogleCalendarDay {
        val calendars = resolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME),
            "${Calendars.ACCOUNT_TYPE} = ? AND ${Calendars.VISIBLE} = 1 AND ${Calendars.SYNC_EVENTS} = 1",
            arrayOf("com.google"),
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(Calendars._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_DISPLAY_NAME)
            buildMap<Long, String?> {
                while (cursor.moveToNext()) put(cursor.getLong(idColumn), cursor.getString(nameColumn))
            }
        } ?: return GoogleCalendarDay.Unavailable
        if (calendars.isEmpty()) return GoogleCalendarDay.NoCalendars

        val window = CalendarDayWindow(date, zone)
        val uri = Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, window.queryStart)
            ContentUris.appendId(this, window.queryEnd)
        }.build()
        val calendarIds = calendars.keys.map(Long::toString).toTypedArray()
        val calendarsSelection = "${Instances.CALENDAR_ID} IN (${calendarIds.joinToString { "?" }})"
        val events = resolver.query(
            uri,
            arrayOf(
                Instances.EVENT_ID, Instances.CALENDAR_ID, Instances.TITLE,
                Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.STATUS
            ),
            calendarsSelection,
            calendarIds,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(Instances.EVENT_ID)
            val calendarColumn = cursor.getColumnIndexOrThrow(Instances.CALENDAR_ID)
            val titleColumn = cursor.getColumnIndexOrThrow(Instances.TITLE)
            val beginColumn = cursor.getColumnIndexOrThrow(Instances.BEGIN)
            val endColumn = cursor.getColumnIndexOrThrow(Instances.END)
            val allDayColumn = cursor.getColumnIndexOrThrow(Instances.ALL_DAY)
            val statusColumn = cursor.getColumnIndexOrThrow(Instances.STATUS)
            buildList<GoogleCalendarEvent> {
                while (cursor.moveToNext()) {
                    val calendarId = cursor.getLong(calendarColumn)
                    if (calendarId !in calendars || cursor.getInt(statusColumn) == Events.STATUS_CANCELED) continue
                    val beginsAt = cursor.getLong(beginColumn)
                    val endsAt = if (cursor.isNull(endColumn)) beginsAt else cursor.getLong(endColumn)
                    val allDay = cursor.getInt(allDayColumn) != 0
                    if (window.overlaps(beginsAt, endsAt, allDay)) {
                        add(
                            GoogleCalendarEvent(
                                eventId = cursor.getLong(idColumn),
                                title = cursor.getString(titleColumn),
                                calendarName = calendars[calendarId],
                                beginsAt = beginsAt,
                                endsAt = endsAt,
                                isAllDay = allDay
                            )
                        )
                    }
                }
            }
        } ?: return GoogleCalendarDay.Unavailable

        return GoogleCalendarDay.Ready(events.sortedWith(
            compareBy<GoogleCalendarEvent> { !it.isAllDay }.thenBy { it.beginsAt }.thenBy { it.eventId }
        ))
    }
}
