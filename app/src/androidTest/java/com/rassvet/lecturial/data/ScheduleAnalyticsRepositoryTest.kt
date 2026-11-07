package com.rassvet.lecturial.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rassvet.lecturial.data.local.LecturialDatabase
import com.rassvet.lecturial.data.network.ScheduleRemoteDataSource
import com.rassvet.lecturial.model.ScheduleDay
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.ScheduleWeek
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleAnalyticsRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("analytics", Context.MODE_PRIVATE)
    private lateinit var database: LecturialDatabase

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, LecturialDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
        prefs.edit().clear().commit()
    }

    @Test
    fun lessonsComeFromTheCacheWhileTheyAreFresh() = runBlocking {
        val today = LocalDate.of(2026, 11, 4)
        val monday = LocalDate.of(2026, 11, 2)
        var clock = 1_000_000L
        var fetched = 0
        val remote = object : ScheduleRemoteDataSource {
            override suspend fun fetchWeek(group: String, weekStart: LocalDate): ScheduleWeek {
                fetched++
                val event = ScheduleEvent("$group-$weekStart", weekStart, "08:45", "10:05", 1, "Физика", "Лекция", null, null, emptyList(), null, null)
                return ScheduleWeek(group, weekStart, (0L..6L).map { ScheduleDay(weekStart.plusDays(it), if (it == 0L) listOf(event) else emptyList()) })
            }
        }
        val repository = ScheduleAnalyticsRepository(context, database, { remote }, nowMillis = { clock })
        suspend fun lessons() = repository.lessons("Группа", monday, monday.plusDays(6), today).map { it.discipline }

        assertEquals(listOf("Физика"), lessons())
        assertEquals(1, fetched)

        clock += 5 * 60 * 60 * 1000L
        lessons()
        assertEquals("Five hours are still fresh", 1, fetched)

        clock += 2 * 60 * 60 * 1000L
        lessons()
        assertEquals("Seven hours aren't", 2, fetched)
    }

    @Test
    fun aRoomListWithFailedQueriesIsNotKeptForTheWeek() = runBlocking {
        var failing = true
        var asked = 0
        val remote = object : ScheduleRemoteDataSource {
            override suspend fun fetchWeek(group: String, weekStart: LocalDate): ScheduleWeek = error("not used")

            override suspend fun fetchClassrooms(query: String): List<String> {
                asked++
                if (failing && query == "К") throw IOException("connection reset")
                return listOf("Альфа 5.8")
            }
        }
        val repository = ScheduleAnalyticsRepository(context, database, { remote })

        assertEquals(listOf("Альфа 5.8"), repository.allClassrooms())
        val firstRun = asked
        failing = false
        repository.allClassrooms()
        assertTrue("A list with a failed query is asked for again", asked > firstRun)

        val secondRun = asked
        repository.allClassrooms()
        assertEquals("A complete list is kept", secondRun, asked)
    }
}
