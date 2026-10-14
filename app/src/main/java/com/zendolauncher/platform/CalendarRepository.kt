package com.zendolauncher.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.zendolauncher.model.CalendarEvent
import com.zendolauncher.model.DeviceCalendar

/**
 * Read-only device calendar access. No network: queries the local provider.
 * Permission is requested only when the user opens a calendar feature.
 */
class CalendarRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun listCalendars(): List<DeviceCalendar> {
        if (!hasPermission()) return emptyList()
        val out = ArrayList<DeviceCalendar>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
                "${CalendarContract.Calendars.VISIBLE}=1",
                null, null,
            )?.use { c ->
                val idCol = c.getColumnIndex(CalendarContract.Calendars._ID)
                val nameCol = c.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                while (c.moveToNext()) {
                    out += DeviceCalendar(
                        id = if (idCol >= 0) c.getLong(idCol) else -1L,
                        name = if (nameCol >= 0) c.getString(nameCol) ?: "Calendar" else "Calendar",
                    )
                }
            }
        }
        return out
    }

    /**
     * Events overlapping [startMillis, endMillis). Scoped to the displayed period;
     * callers pass a week, not a year. [calendarIds] null/empty = all.
     */
    fun queryEvents(
        startMillis: Long,
        endMillis: Long,
        calendarIds: Set<Long>? = null,
    ): List<CalendarEvent> {
        if (!hasPermission()) return emptyList()
        val out = ArrayList<CalendarEvent>()
        runCatching {
            val selection = StringBuilder()
                .append("${CalendarContract.Events.DTSTART}<?")
                .append(" AND ${CalendarContract.Events.DTEND}>?")
                .append(" AND ${CalendarContract.Events.STATUS}!=${CalendarContract.Events.STATUS_CANCELED}")
            val args = ArrayList<String>()
            args += endMillis.toString()
            args += startMillis.toString()
            if (!calendarIds.isNullOrEmpty()) {
                selection.append(" AND ${CalendarContract.Events.CALENDAR_ID} IN (")
                selection.append(calendarIds.joinToString(",") { "?" })
                selection.append(")")
                calendarIds.forEach { args += it.toString() }
            }
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.ALL_DAY,
                    CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.CALENDAR_ID,
                ),
                selection.toString(),
                args.toArray(arrayOf()),
                "${CalendarContract.Events.DTSTART} ASC",
            )?.use { c ->
                val idC = c.getColumnIndex(CalendarContract.Events._ID)
                val titleC = c.getColumnIndex(CalendarContract.Events.TITLE)
                val startC = c.getColumnIndex(CalendarContract.Events.DTSTART)
                val endC = c.getColumnIndex(CalendarContract.Events.DTEND)
                val allDayC = c.getColumnIndex(CalendarContract.Events.ALL_DAY)
                val locC = c.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                while (c.moveToNext()) {
                    val title = if (titleC >= 0) c.getString(titleC)?.trim().orEmpty() else ""
                    if (title.isEmpty()) continue
                    out += CalendarEvent(
                        id = if (idC >= 0) c.getLong(idC) else 0L,
                        title = title,
                        startMillis = if (startC >= 0) c.getLong(startC) else 0L,
                        endMillis = if (endC >= 0) c.getLong(endC) else 0L,
                        allDay = if (allDayC >= 0) c.getInt(allDayC) == 1 else false,
                        location = if (locC >= 0) c.getString(locC)?.trim()?.takeIf { it.isNotEmpty() } else null,
                    )
                }
            }
        }
        return out.sortedBy { it.startMillis }
    }
}
