package com.rassvet.lecturial

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.rassvet.lecturial.data.cacheWeek
import com.rassvet.lecturial.model.ScheduleDay
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.ScheduleWeek
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/** The debug build's own application: its data is apart from the installed release app. */
internal val testApp: LecturialApplication get() = ApplicationProvider.getApplicationContext()

internal const val TEST_GROUP = "ТЕСТ-ГРУППА-1"

/** Waits for what the app does on its own coroutines. */
internal fun eventually(what: String, timeoutMillis: Long = 10_000L, check: () -> Boolean) {
    val deadline = SystemClock.elapsedRealtime() + timeoutMillis
    while (!check()) {
        if (SystemClock.elapsedRealtime() > deadline) throw AssertionError("Not in time: $what")
        Thread.sleep(50)
    }
}

internal fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

internal fun grantNotifications() {
    if (Build.VERSION.SDK_INT < 33) return
    InstrumentationRegistry.getInstrumentation().uiAutomation
        .grantRuntimePermission(testApp.packageName, Manifest.permission.POST_NOTIFICATIONS)
}

internal fun lesson(
    id: String,
    date: LocalDate,
    start: String,
    end: String,
    discipline: String,
    type: String = "Практика",
    teachers: List<String> = emptyList(),
) = ScheduleEvent(
    id = id,
    date = date,
    startTime = start,
    endTime = end,
    pairNumber = 1,
    discipline = discipline,
    type = type,
    classroom = null,
    address = null,
    teachers = teachers,
    comment = null,
    onlineUrl = null,
)

/** Runs with [TEST_GROUP] as the chosen schedule and [lessons] cached for it, then takes them out. */
internal suspend fun <T> withTestSchedule(lessons: List<ScheduleEvent>, block: suspend () -> T): T {
    val app = testApp
    val previous = app.preferences.selectedGroup.first()
    app.preferences.setSelectedGroup(TEST_GROUP)
    lessons.groupBy { it.date.with(DayOfWeek.MONDAY) }.forEach { (monday, inWeek) ->
        val days = (0L..6L).map { offset ->
            val date = monday.plusDays(offset)
            ScheduleDay(date, inWeek.filter { it.date == date })
        }
        app.database.cacheWeek(TEST_GROUP, ScheduleWeek(TEST_GROUP, monday, days), System.currentTimeMillis())
    }
    try {
        return block()
    } finally {
        app.database.openHelper.writableDatabase.run {
            execSQL("DELETE FROM schedule_events WHERE groupName = ?", arrayOf(TEST_GROUP))
            execSQL("DELETE FROM week_cache WHERE groupName = ?", arrayOf(TEST_GROUP))
        }
        previous?.let { app.preferences.setSelectedGroup(it) }
    }
}
