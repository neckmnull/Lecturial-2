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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ScheduleRepositoryTest {
    private lateinit var database: LecturialDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            LecturialDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun refreshedWeekIsAvailableOfflineAndSeparatedByGroup() = runBlocking {
        val start = LocalDate.of(2026, 8, 31)
        val remote = object : ScheduleRemoteDataSource {
            override suspend fun fetchWeek(group: String, weekStart: LocalDate): ScheduleWeek {
                val event = ScheduleEvent("$group-id", start, "08:45", "10:05", 1, group, "Лекции", null, null, emptyList(), null, null)
                return ScheduleWeek(group, start, (0L..6L).map { ScheduleDay(start.plusDays(it), if (it == 0L) listOf(event) else emptyList()) })
            }
        }
        val repository = ScheduleRepository(database, remote) { 1234L }

        repository.refreshWeek("ИОП-ИТ-26/1", start)

        assertNotNull(repository.cachedWeek("ИОП-ИТ-26/1", start))
        assertEquals(1, repository.cachedWeek("ИОП-ИТ-26/1", start)?.week?.days?.first()?.events?.size)
        assertEquals(null, repository.cachedWeek("ИОП-ИТ-26/2", start))
    }

    @Test
    fun simultaneousRefreshesForSameWeekShareOneNetworkRequest() = runBlocking {
        val start = LocalDate.of(2026, 8, 31)
        val calls = AtomicInteger()
        val remote = object : ScheduleRemoteDataSource {
            override suspend fun fetchWeek(group: String, weekStart: LocalDate): ScheduleWeek {
                calls.incrementAndGet()
                delay(100)
                return ScheduleWeek(group, start, (0L..6L).map { ScheduleDay(start.plusDays(it), emptyList()) })
            }
        }
        val repository = ScheduleRepository(database, remote)

        val first = async { repository.refreshWeek("ИОП-ИТ-26/1", start) }
        val second = async { repository.refreshWeek("ИОП-ИТ-26/1", start) }
        first.await()
        second.await()

        assertEquals(1, calls.get())
    }
}
