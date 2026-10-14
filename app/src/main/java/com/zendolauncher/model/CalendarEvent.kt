package com.zendolauncher.model

/** One device-calendar event, read-only. Times are wall-clock millis. */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean = false,
    val calendarName: String? = null,
    val location: String? = null,
)

data class DeviceCalendar(
    val id: Long,
    val name: String,
)
