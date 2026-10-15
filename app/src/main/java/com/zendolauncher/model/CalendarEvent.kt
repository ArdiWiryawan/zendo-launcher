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
    /** Account email where the provider exposes it, or null (local calendars). */
    val accountName: String? = null,
    /** Account type, e.g. com.google for Google calendars. Null when undisclosed. */
    val accountType: String? = null,
) {
    /** True only when the provider identifies a Google account behind this calendar. */
    val isGoogle: Boolean get() = accountType == "com.google"
}
