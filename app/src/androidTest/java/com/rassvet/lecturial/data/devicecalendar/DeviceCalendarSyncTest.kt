package com.rassvet.lecturial.data.devicecalendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rassvet.lecturial.eventually
import com.rassvet.lecturial.lesson
import com.rassvet.lecturial.model.UniversityZone
import com.rassvet.lecturial.testApp
import com.rassvet.lecturial.withTestSchedule
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The whole sync to the phone's calendar, in a local calendar made for the test and removed after it. */
@RunWith(AndroidJUnit4::class)
class DeviceCalendarSyncTest {
    private val app = testApp
    private val resolver = app.contentResolver
    private val prefs = app.getSharedPreferences("device_calendar_sync", Context.MODE_PRIVATE)
    private val record = File(app.filesDir, "device_calendar_synced.json")
    private val day: LocalDate = LocalDate.now(UniversityZone).plusDays(1)
    private val lessons = listOf(
        lesson("a", day, "08:45", "10:05", "Физика"),
        lesson("b", day, "10:20", "11:40", "Физика"),
    )
    private var calendarId = -1L

    private fun forgetSync() {
        prefs.edit().clear().commit()
        record.delete()
    }

    @Before
    fun setUp() {
        // Nothing selected, so the sync can't take events out of any calendar but the test's.
        forgetSync()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(app.packageName, Manifest.permission.READ_CALENDAR)
        automation.grantRuntimePermission(app.packageName, Manifest.permission.WRITE_CALENDAR)
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
    fun tearDown() {
        forgetSync()
        if (calendarId >= 0) resolver.delete(ContentUris.withAppendedId(asSyncAdapter(Calendars.CONTENT_URI), calendarId), null, null)
    }

    @Test
    fun lessonsGoInOnceAndComeOutOnDisconnectAndTheUsersEventsStay() = runBlocking {
        withTestSchedule(lessons) {
            val users = insertUsersEvent()
            val sync = DeviceCalendarSync(app)

            sync.select(DeviceCalendarInfo(calendarId, "Lecturial test", ACCOUNT, CalendarContract.ACCOUNT_TYPE_LOCAL, 0))
            eventually("the first sync") { lessonEvents() == 2 }

            sync.syncNow()
            assertEquals("A second sync adds nothing", 2, lessonEvents())

            // Lost app data: a new install finds its events in the calendar and takes them over.
            record.delete()
            DeviceCalendarSync(app).syncNow()
            assertEquals("Taken over, not doubled", 2, lessonEvents())

            val reopened = DeviceCalendarSync(app)
            reopened.disconnect()
            eventually("the disconnect") { lessonEvents() == 0 }
            assertEquals(1, eventsWithId(users))
        }
    }

    @Test
    fun aLessonDeletedInTheCalendarComesBackAsTheCalendarMirrorsTheSchedule() = runBlocking {
        withTestSchedule(lessons) {
            val sync = DeviceCalendarSync(app)
            sync.select(DeviceCalendarInfo(calendarId, "Lecturial test", ACCOUNT, CalendarContract.ACCOUNT_TYPE_LOCAL, 0))
            eventually("the first sync") { lessonEvents() == 2 }

            resolver.delete(
                Events.CONTENT_URI,
                "${Events.CALENDAR_ID} = ? AND ${Events.TITLE} LIKE ? AND ${Events.DTSTART} = (SELECT MIN(${Events.DTSTART}) FROM view_events WHERE ${Events.CALENDAR_ID} = ?)",
                arrayOf(calendarId.toString(), "Физика%", calendarId.toString()),
            ).takeIf { it > 0 } ?: deleteOneLesson()
            eventually("the deletion") { lessonEvents() == 1 }

            sync.syncNow()

            assertEquals(2, lessonEvents())
        }
    }

    private fun deleteOneLesson() {
        val id = resolver.query(
            Events.CONTENT_URI,
            arrayOf(Events._ID),
            "${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0 AND ${Events.TITLE} LIKE ?",
            arrayOf(calendarId.toString(), "Физика%"),
            null,
        )!!.use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
        resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null)
    }

    private fun lessonEvents(): Int = resolver.query(
        Events.CONTENT_URI,
        arrayOf(Events._ID),
        "${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0 AND ${Events.TITLE} LIKE ?",
        arrayOf(calendarId.toString(), "Физика%"),
        null,
    )!!.use { it.count }

    private fun eventsWithId(id: Long): Int = resolver.query(
        Events.CONTENT_URI,
        arrayOf(Events._ID),
        "${Events._ID} = ? AND ${Events.DELETED} = 0",
        arrayOf(id.toString()),
        null,
    )!!.use { it.count }

    private fun insertUsersEvent(): Long {
        val zone = ZoneId.systemDefault()
        val uri = resolver.insert(
            Events.CONTENT_URI,
            ContentValues().apply {
                put(Events.CALENDAR_ID, calendarId)
                put(Events.TITLE, "Встреча")
                put(Events.DESCRIPTION, "Своё событие")
                put(Events.DTSTART, day.atTime(13, 30).atZone(zone).toInstant().toEpochMilli())
                put(Events.DTEND, day.atTime(14, 50).atZone(zone).toInstant().toEpochMilli())
                put(Events.EVENT_TIMEZONE, zone.id)
            },
        )!!
        return ContentUris.parseId(uri)
    }

    private fun asSyncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    private companion object {
        const val ACCOUNT = "lecturial-test"
    }
}
