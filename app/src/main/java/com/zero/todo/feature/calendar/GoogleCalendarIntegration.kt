package com.zero.todo.feature.calendar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.zero.todo.R
import com.zero.todo.core.designsystem.ZeroSmallShape
import com.zero.todo.core.designsystem.ZeroTheme
import com.zero.todo.core.designsystem.hairlineBorder
import com.zero.todo.data.calendar.GoogleCalendarDay
import com.zero.todo.data.calendar.GoogleCalendarEvent
import com.zero.todo.data.calendar.GoogleCalendarSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class GoogleCalendarConnection(
    val day: GoogleCalendarDay,
    val permissionDenied: Boolean,
    val onConnect: () -> Unit,
    val onRetry: () -> Unit
) {
    val events: List<GoogleCalendarEvent> get() = (day as? GoogleCalendarDay.Ready)?.events.orEmpty()
}

@Composable
internal fun rememberGoogleCalendarConnection(date: LocalDate, zone: ZoneId): GoogleCalendarConnection {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val source = remember(context) { GoogleCalendarSource(context.contentResolver) }
    var permissionGranted by remember(context) { mutableStateOf(context.canReadCalendar()) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var retryCount by remember { mutableIntStateOf(0) }
    var day by remember(date, zone, permissionGranted, retryCount) {
        mutableStateOf<GoogleCalendarDay>(GoogleCalendarDay.Loading)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        permissionDenied = !granted
        if (granted) retryCount++
    }

    LaunchedEffect(date, zone, lifecycle, source, permissionGranted, retryCount) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val canRead = context.canReadCalendar()
            if (permissionGranted != canRead) {
                permissionGranted = canRead
                if (canRead) permissionDenied = false
            } else if (canRead) {
                source.observeDay(date, zone).collect { day = it }
            }
        }
    }

    return GoogleCalendarConnection(
        day = if (permissionGranted) day else GoogleCalendarDay.PermissionRequired,
        permissionDenied = permissionDenied,
        onConnect = {
            if (permissionDenied) {
                context.startActivity(Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                launcher.launch(Manifest.permission.READ_CALENDAR)
            }
        },
        onRetry = { retryCount++ }
    )
}

private fun Context.canReadCalendar(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

@Composable
internal fun GoogleCalendarStatus(connection: GoogleCalendarConnection) {
    val message = when (connection.day) {
        GoogleCalendarDay.PermissionRequired -> if (connection.permissionDenied) {
            R.string.cal_google_permission_denied
        } else {
            R.string.cal_google_connect_description
        }
        GoogleCalendarDay.Loading -> R.string.cal_google_loading
        GoogleCalendarDay.NoCalendars -> R.string.cal_google_no_calendars
        GoogleCalendarDay.Unavailable -> R.string.cal_google_unavailable
        is GoogleCalendarDay.Ready -> return
    }

    val colors = ZeroTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZeroSmallShape)
            .background(colors.surface)
            .hairlineBorder(shape = ZeroSmallShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.cal_google_title),
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary
        )
        Text(
            text = stringResource(message),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary
        )
        when (connection.day) {
            GoogleCalendarDay.PermissionRequired -> TextButton(
                onClick = connection.onConnect,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(if (connection.permissionDenied) R.string.cal_google_open_settings else R.string.cal_google_connect))
            }
            GoogleCalendarDay.Unavailable, GoogleCalendarDay.NoCalendars -> TextButton(
                onClick = connection.onRetry,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.cal_google_retry))
            }
            else -> Unit
        }
    }
}

@Composable
internal fun GoogleCalendarEventCard(event: GoogleCalendarEvent, date: LocalDate, zone: ZoneId) {
    val colors = ZeroTheme.colors
    val tabular = ZeroTheme.tabularTypography
    val locale = LocalConfiguration.current.locales[0]
    val time = if (event.isAllDay) {
        stringResource(R.string.cal_google_all_day)
    } else {
        val starts = Instant.ofEpochMilli(event.beginsAt).atZone(zone)
        val ends = Instant.ofEpochMilli(event.endsAt).atZone(zone)
        val timeFormatter = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
        val dateTimeFormatter = remember(locale) { DateTimeFormatter.ofPattern("MM/dd HH:mm", locale) }
        val startLabel = starts.format(if (starts.toLocalDate() == date) timeFormatter else dateTimeFormatter)
        val endLabel = ends.format(if (ends.toLocalDate() == date) timeFormatter else dateTimeFormatter)
        if (event.endsAt <= event.beginsAt) startLabel
        else stringResource(R.string.cal_google_time_range, startLabel, endLabel)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZeroSmallShape)
            .background(colors.surfaceElevated)
            .hairlineBorder(shape = ZeroSmallShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Rounded.Event,
            contentDescription = stringResource(R.string.cal_google_title),
            tint = colors.accent,
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = event.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.cal_google_untitled),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary
            )
            Text(text = time, style = tabular.dateCompact, color = colors.accent)
            event.calendarName?.takeIf { it.isNotBlank() }?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
