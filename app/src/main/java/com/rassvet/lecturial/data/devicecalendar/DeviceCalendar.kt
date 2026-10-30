package com.rassvet.lecturial.data.devicecalendar

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import com.rassvet.lecturial.model.CalendarEntry
import com.rassvet.lecturial.model.SyncPlan
import com.rassvet.lecturial.model.SyncedEntry
import java.time.ZoneId
import java.time.ZoneOffset

/** A calendar on the phone that events can be written to, e.g. one of a Google account. */
data class DeviceCalendarInfo(
    val id: Long,
    val name: String,
    val account: String,
    val accountType: String,
    val color: Int,
) {
    /** Synced to Google by the phone's Google account, so it shows in Google Calendar everywhere. */
    val isGoogle: Boolean get() = accountType == GOOGLE_ACCOUNT_TYPE
}

internal const val GOOGLE_ACCOUNT_TYPE = "com.google"

/** Start, end and time zone of an event as the calendar provider stores them. */
internal data class ProviderTimes(val start: Long, val end: Long, val zone: String)

/**
 * [entry]'s times for the provider: timed events in [zone], all-day events as UTC midnights,
 * which is what the provider requires for them.
 */
internal fun providerTimes(entry: CalendarEntry, zone: ZoneId): ProviderTimes =
    if (entry.allDay) {
        val start = entry.start.toLocalDate()
        val end = entry.end.toLocalDate().takeIf { it.isAfter(start) } ?: start.plusDays(1)
        ProviderTimes(
            start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            end.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            "UTC",
        )
    } else {
        ProviderTimes(entry.start.atZone(zone).toInstant().toEpochMilli(), entry.end.atZone(zone).toInstant().toEpochMilli(), zone.id)
    }

data class DeviceApplyResult(
    val synced: List<SyncedEntry>,
    val created: Int,
    val updated: Int,
    val deleted: Int,
    val failed: Int,
)

/**
 * The phone's calendar provider: lists the calendars events can be written to and applies a
 * [SyncPlan] to one of them. A Google calendar's changes go to Google with the account's own sync.
 */
class DeviceCalendar(
    private val resolver: ContentResolver,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    /** Calendars the user may add events to, Google ones first. */
    fun writableCalendars(): List<DeviceCalendarInfo> {
        val projection = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE,
            Calendars.CALENDAR_COLOR,
        )
        val selection = "${Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${Calendars.SYNC_EVENTS} = 1"
        val calendars = mutableListOf<DeviceCalendarInfo>()
        resolver.query(Calendars.CONTENT_URI, projection, selection, arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), null)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    calendars += DeviceCalendarInfo(
                        id = cursor.getLong(0),
                        name = cursor.getString(1).orEmpty(),
                        account = cursor.getString(2).orEmpty(),
                        accountType = cursor.getString(3).orEmpty(),
                        color = cursor.getInt(4),
                    )
                }
            }
        return calendars.sortedWith(compareBy<DeviceCalendarInfo>({ !it.isGoogle }, { it.account }, { it.name }))
    }

    /**
     * The entries whose events are still in the calendar. One the user deleted is gone from
     * here too, so the next sync puts it back, as the calendar mirrors the schedule.
     */
    fun stillPresent(entries: List<SyncedEntry>): List<SyncedEntry> {
        val ids = entries.mapNotNull { it.remoteId.toLongOrNull() }
        val alive = mutableSetOf<Long>()
        ids.chunked(QUERY_CHUNK).forEach { chunk ->
            val selection = "${Events._ID} IN (${chunk.joinToString(",")}) AND ${Events.DELETED} = 0"
            resolver.query(Events.CONTENT_URI, arrayOf(Events._ID), selection, null, null)?.use { cursor ->
                while (cursor.moveToNext()) alive += cursor.getLong(0)
            }
        }
        return entries.filter { it.remoteId.toLongOrNull() in alive }
    }

    fun apply(calendarId: Long, plan: SyncPlan, before: List<SyncedEntry>): DeviceApplyResult {
        val kept = before.associateBy { it.key }.toMutableMap()
        var created = 0
        var updated = 0
        var deleted = 0
        var failed = 0
        plan.delete.forEach { entry ->
            runCatching { delete(entry.remoteId.toLong()) }
                .onSuccess { kept.remove(entry.key); deleted++ }
                .onFailure { failed++ }
        }
        plan.update.forEach { (old, entry) ->
            runCatching {
                val id = old.remoteId.toLong()
                if (resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), values(calendarId, entry), null, null) == 0) {
                    insert(calendarId, entry)
                } else {
                    setReminder(id, entry.reminderMinutes)
                    id
                }
            }
                .onSuccess { id -> kept[entry.key] = SyncedEntry(entry.key, id.toString(), entry.fingerprint, entry.date); updated++ }
                .onFailure { failed++ }
        }
        plan.create.forEach { entry ->
            runCatching { insert(calendarId, entry) }
                .onSuccess { id -> kept[entry.key] = SyncedEntry(entry.key, id.toString(), entry.fingerprint, entry.date); created++ }
                .onFailure { failed++ }
        }
        return DeviceApplyResult(kept.values.toList(), created, updated, deleted, failed)
    }

    /** Removes the given events; returns how many could not be removed. */
    fun deleteAll(entries: List<SyncedEntry>): Int =
        entries.count { entry -> runCatching { delete(entry.remoteId.toLong()) }.isFailure }

    private fun insert(calendarId: Long, entry: CalendarEntry): Long {
        val uri = resolver.insert(Events.CONTENT_URI, values(calendarId, entry)) ?: error("Календарь не принял событие")
        val id = ContentUris.parseId(uri)
        setReminder(id, entry.reminderMinutes)
        return id
    }

    private fun delete(id: Long) {
        resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null)
    }

    private fun setReminder(eventId: Long, minutes: Int?) {
        resolver.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()))
        if (minutes == null) return
        resolver.insert(
            Reminders.CONTENT_URI,
            ContentValues().apply {
                put(Reminders.EVENT_ID, eventId)
                put(Reminders.MINUTES, minutes)
                put(Reminders.METHOD, Reminders.METHOD_ALERT)
            },
        )
    }

    private fun values(calendarId: Long, entry: CalendarEntry): ContentValues {
        val times = providerTimes(entry, zone)
        return ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, entry.subject)
            put(Events.DESCRIPTION, entry.body)
            put(Events.EVENT_LOCATION, entry.location)
            put(Events.DTSTART, times.start)
            put(Events.DTEND, times.end)
            put(Events.EVENT_TIMEZONE, times.zone)
            put(Events.ALL_DAY, if (entry.allDay) 1 else 0)
            put(Events.HAS_ALARM, if (entry.reminderMinutes != null) 1 else 0)
            put(Events.AVAILABILITY, Events.AVAILABILITY_BUSY)
        }
    }

    private companion object {
        /** Well under SQLite's limit on the number of terms in one query. */
        const val QUERY_CHUNK = 400
    }
}
