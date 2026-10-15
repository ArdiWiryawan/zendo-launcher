package com.zendolauncher.platform

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.zendolauncher.model.CalendarEvent
import com.zendolauncher.model.DeviceCalendar

/**
 * Read-only device calendar access. No network: queries the local provider.
 * Permission is requested only when the user opens a calendar feature.
 *
 * Primary source is the Instances table (expanded occurrences: recurring
 * series, modified/deleted instances, and multi-day spans come back the way
 * the provider resolves them). If Instances is unavailable on a device, it
 * falls back to the Events table rather than failing.
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
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.ACCOUNT_TYPE,
                    CalendarContract.Calendars.SYNC_EVENTS,
                ),
                "${CalendarContract.Calendars.VISIBLE}=1",
                null, null,
            )?.use { c ->
                val idCol = c.getColumnIndex(CalendarContract.Calendars._ID)
                val nameCol = c.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountCol = c.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val typeCol = c.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                val syncCol = c.getColumnIndex(CalendarContract.Calendars.SYNC_EVENTS)
                while (c.moveToNext()) {
                    out += DeviceCalendar(
                        id = if (idCol >= 0) c.getLong(idCol) else -1L,
                        name = if (nameCol >= 0) c.getString(nameCol) ?: "Calendar" else "Calendar",
                        accountName = if (accountCol >= 0) c.getString(accountCol) else null,
                        accountType = if (typeCol >= 0) c.getString(typeCol) else null,
                        syncEvents = if (syncCol >= 0) c.getInt(syncCol) != 0 else true,
                    )
                }
            }
        }
        return out
    }

    /**
     * Occurrences overlapping [startMillis, endMillis). Scoped to the displayed
     * period; callers pass weeks, not years. [calendarIds] null/empty = all.
     * De-duplicated by (event, start) so a provider that yields an instance
     * twice still renders once.
     */
    fun queryEvents(
        startMillis: Long,
        endMillis: Long,
        calendarIds: Set<Long>? = null,
    ): List<CalendarEvent> {
        if (!hasPermission()) return emptyList()
        return queryInstances(startMillis, endMillis, calendarIds)
            ?: queryEventMasters(startMillis, endMillis, calendarIds)
    }

    private fun queryInstances(
        startMillis: Long,
        endMillis: Long,
        calendarIds: Set<Long>?,
    ): List<CalendarEvent>? {
        return try {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(startMillis.toString())
                .appendPath(endMillis.toString())
                .build()
            val selection = StringBuilder("1=1")
            val args = ArrayList<String>()
            if (!calendarIds.isNullOrEmpty()) {
                selection.append(" AND ${CalendarContract.Instances.CALENDAR_ID} IN (")
                selection.append(calendarIds.joinToString(",") { "?" })
                selection.append(")")
                calendarIds.forEach { args += it.toString() }
            }
            val out = ArrayList<CalendarEvent>()
            context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.EVENT_LOCATION,
                ),
                selection.toString(),
                args.toArray(arrayOf()),
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                val idC = c.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleC = c.getColumnIndex(CalendarContract.Instances.TITLE)
                val startC = c.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endC = c.getColumnIndex(CalendarContract.Instances.END)
                val allDayC = c.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val locC = c.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val seen = HashSet<Pair<Long, Long>>()
                while (c.moveToNext()) {
                    val title = if (titleC >= 0) c.getString(titleC)?.trim().orEmpty() else ""
                    if (title.isEmpty()) continue
                    val id = if (idC >= 0) c.getLong(idC) else 0L
                    val start = if (startC >= 0) c.getLong(startC) else 0L
                    if (!seen.add(id to start)) continue
                    out += CalendarEvent(
                        id = id,
                        title = title,
                        startMillis = start,
                        endMillis = if (endC >= 0) c.getLong(endC) else start,
                        allDay = if (allDayC >= 0) c.getInt(allDayC) == 1 else false,
                        location = if (locC >= 0) c.getString(locC)?.trim()?.takeIf { it.isNotEmpty() } else null,
                    )
                }
            }
            out.sortedBy { it.startMillis }
        } catch (_: Exception) {
            null
        }
    }

    private fun queryEventMasters(
        startMillis: Long,
        endMillis: Long,
        calendarIds: Set<Long>?,
    ): List<CalendarEvent> {
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
                val seen = HashSet<Pair<Long, Long>>()
                while (c.moveToNext()) {
                    val title = if (titleC >= 0) c.getString(titleC)?.trim().orEmpty() else ""
                    if (title.isEmpty()) continue
                    val id = if (idC >= 0) c.getLong(idC) else 0L
                    val start = if (startC >= 0) c.getLong(startC) else 0L
                    if (!seen.add(id to start)) continue
                    out += CalendarEvent(
                        id = id,
                        title = title,
                        startMillis = start,
                        endMillis = if (endC >= 0) c.getLong(endC) else 0L,
                        allDay = if (allDayC >= 0) c.getInt(allDayC) == 1 else false,
                        location = if (locC >= 0) c.getString(locC)?.trim()?.takeIf { it.isNotEmpty() } else null,
                    )
                }
            }
        }
        return out.sortedBy { it.startMillis }
    }

    /** Deep-link URI for an event, for "open details" where a viewer exists. */
    fun eventUri(eventId: Long): android.net.Uri =
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
}
