package com.rassvet.lecturial.data.devicecalendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.test.platform.app.InstrumentationRegistry
import com.rassvet.lecturial.model.CalendarEntry
import com.rassvet.lecturial.model.LESSON_MARK
import com.rassvet.lecturial.model.SyncPlan
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** What the sync deletes from a calendar is found by a query; it must find the app's events only. */
class DeviceCalendarTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private var calendarId = -1L

    @Before
    fun createLocalCalendar() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName, Manifest.permission.READ_CALENDAR)
        automation.grantRuntimePermission(context.packageName, Manifest.permission.WRITE_CALENDAR)
        val uri = resolver.insert(
            asSyncAdapter(Calendars.CONTENT_URI),
            ContentValues().apply {
                put(Calendars.ACCOUNT_NAME, ACCOUNT)
                put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(Calendars.NAME, ACCOUNT)
                put(Calendars.CALENDAR_DISPLAY_NAME, "Lecturial test")
                put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
                put(Calendars.OWNER_ACCOUNT, ACCOUNT)
                put(Calendars.SYNC_EVENTS, 1)
                put(Calendars.CALENDAR_TIME_ZONE, ZoneId.systemDefault().id)
            },
        )!!
        calendarId = ContentUris.parseId(uri)
    }

    @After
    fun removeLocalCalendar() {
        if (calendarId >= 0) resolver.delete(ContentUris.withAppendedId(asSyncAdapter(Calendars.CONTENT_URI), calendarId), null, null)
    }

    @Test
    fun markedEventsAreTheAppsOwnAndNothingElse() {
        val calendar = DeviceCalendar(resolver, appPackage = context.packageName)
        val day = LocalDate.now().plusDays(1)
        val entry = CalendarEntry(
            key = "lesson:test",
            subject = "Физика · Практика",
            date = day,
            start = day.atTime(10, 20),
            end = day.atTime(11, 40),
            allDay = false,
            location = null,
            body = "Тип: Практика\n$LESSON_MARK",
            reminderMinutes = null,
        )
        val written = calendar.apply(calendarId, SyncPlan(listOf(entry), emptyList(), emptyList()), emptyList()).synced.single()
        // Written by a version before the tags: only the mark at the end of the text.
        val older = insertEvent("Физика · Лекция", "Тип: Лекция\n$LESSON_MARK", day)
        val users = insertEvent("Встреча", "Своё событие, где упомянуто: $LESSON_MARK, но не в конце.", day)

        val marked = calendar.markedEvents(calendarId, fromMillis = null)

        assertEquals(setOf(written.remoteId, older.toString()), marked.map { it.remoteId }.toSet())
        assertTrue(marked.single { it.remoteId == older.toString() }.key == null)
        assertEquals(0, calendar.deleteIds(marked.map { it.remoteId }))
        assertTrue(calendar.markedEvents(calendarId, fromMillis = null).isEmpty())
        assertTrue(eventAlive(users))
    }

    private fun insertEvent(title: String, description: String, day: LocalDate): Long {
        val zone = ZoneId.systemDefault()
        val uri = resolver.insert(
            Events.CONTENT_URI,
            ContentValues().apply {
                put(Events.CALENDAR_ID, calendarId)
                put(Events.TITLE, title)
                put(Events.DESCRIPTION, description)
                put(Events.DTSTART, day.atTime(13, 30).atZone(zone).toInstant().toEpochMilli())
                put(Events.DTEND, day.atTime(14, 50).atZone(zone).toInstant().toEpochMilli())
                put(Events.EVENT_TIMEZONE, zone.id)
            },
        )!!
        return ContentUris.parseId(uri)
    }

    private fun eventAlive(id: Long): Boolean =
        resolver.query(Events.CONTENT_URI, arrayOf(Events._ID), "${Events._ID} = ? AND ${Events.DELETED} = 0", arrayOf(id.toString()), null)
            ?.use { it.count == 1 } == true

    private fun asSyncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    private companion object {
        const val ACCOUNT = "lecturial-test"
    }
}
