package com.rassvet.lecturial.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rassvet.lecturial.data.local.LecturialDatabase
import com.rassvet.lecturial.data.network.ScheduleRemoteDataSource
import com.rassvet.lecturial.model.ExtracurricularChoice
import com.rassvet.lecturial.model.ScheduleDay
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.ScheduleTarget
import com.rassvet.lecturial.model.ScheduleWeek
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    fun extracurricularEventsFollowTheChoiceWithoutANewLoad() = runBlocking {
        val start = LocalDate.of(2026, 8, 31)
        fun event(id: String, name: String, type: String) =
            ScheduleEvent(id, start, "08:45", "10:05", 1, name, type, null, null, emptyList(), null, null)
        val remote = object : ScheduleRemoteDataSource {
            override suspend fun fetchWeek(group: String, weekStart: LocalDate) = ScheduleWeek(
                group,
                start,
                (0L..6L).map { offset ->
                    ScheduleDay(start.plusDays(offset), if (offset == 0L) listOf(event("a", "Физика", "Лекции"), event("b", "Тьюторский час", "Внеучебное")) else emptyList())
                },
            )
        }
        var choice = ExtracurricularChoice()
        val repository = ScheduleRepository(database, remote, extracurricularChoice = { choice })
        val group = "ИОП-ИТ-26/1"
        val teacher = ScheduleTarget.teacherKey("7")
        repository.refreshWeek(group, start)
        repository.refreshWeek(teacher, start)
        suspend fun shown(key: String) = repository.cachedWeek(key, start)!!.week.days.first().events.map { it.discipline }
        suspend fun between(key: String) = repository.eventsBetween(key, start, start).map { it.discipline }

        assertEquals(listOf("Физика", "Тьюторский час"), shown(group).sorted().reversed())
        assertEquals(2, between(group).size)

        choice = ExtracurricularChoice(show = false)
        assertEquals(listOf("Физика"), shown(group))
        assertEquals(listOf("Физика"), between(group))
        assertEquals("A teacher's schedule keeps everything", 2, shown(teacher).size)
        assertEquals("The choice still lists what is hidden", listOf("Тьюторский час"), repository.extracurricularNames(group, start))

        choice = ExtracurricularChoice(hiddenNames = setOf("Тьюторский час"))
        assertEquals(listOf("Физика"), shown(group))
        assertEquals(listOf("Физика"), between(group))

        choice = ExtracurricularChoice(hiddenNames = setOf("Разговоры о важном"))
        assertEquals(2, shown(group).size)
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

    @Test
    fun aRefreshGivenUpOnDoesNotAnswerTheNextOne() = runBlocking {
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

        val givenUp = launch { repository.refreshWeek("ИОП-ИТ-26/1", start) }
        delay(20)
        givenUp.cancelAndJoin()
        delay(300)
        repository.refreshWeek("ИОП-ИТ-26/1", start)

        assertEquals(2, calls.get())
    }

    @Test
    fun oldWeeksOfOtherSchedulesLeaveTheCache() = runBlocking {
        val old = LocalDate.of(2026, 8, 31)
        val recent = old.plusWeeks(20)
        val remote = object : ScheduleRemoteDataSource {
            override suspend fun fetchWeek(group: String, weekStart: LocalDate): ScheduleWeek {
                val event = ScheduleEvent("$group-$weekStart", weekStart, "08:45", "10:05", 1, group, "Лекции", null, null, emptyList(), null, null)
                return ScheduleWeek(group, weekStart, (0L..6L).map { ScheduleDay(weekStart.plusDays(it), if (it == 0L) listOf(event) else emptyList()) })
            }
        }
        val repository = ScheduleRepository(database, remote)
        for (group in listOf("ИОП-ИТ-26/1", "Чужая группа")) {
            for (week in listOf(old, recent)) repository.refreshWeek(group, week)
        }

        repository.trimCache(keep = "ИОП-ИТ-26/1", before = recent)

        assertNotNull("Your own schedule stays", repository.cachedWeek("ИОП-ИТ-26/1", old))
        assertEquals(null, repository.cachedWeek("Чужая группа", old))
        assertEquals(0, database.scheduleDao().eventsForWeek("Чужая группа", old.toString(), old.plusDays(6).toString()).size)
        assertEquals(1, repository.cachedWeek("Чужая группа", recent)?.week?.days?.first()?.events?.size)
    }

    @Test
    fun severalWeeksLoadInOneSessionAndMissedWeeksAreRetried() = runBlocking {
        val start = LocalDate.of(2026, 8, 31)
        val sessions = AtomicInteger()
        val single = AtomicInteger()
        fun week(group: String, weekStart: LocalDate) =
            ScheduleWeek(group, weekStart, (0L..6L).map { ScheduleDay(weekStart.plusDays(it), emptyList()) })
        val remote = object : ScheduleRemoteDataSource {
            override suspend fun fetchWeek(group: String, weekStart: LocalDate): ScheduleWeek {
                single.incrementAndGet()
                return week(group, weekStart)
            }

            override suspend fun fetchWeeks(
                group: String,
                firstWeekStart: LocalDate,
                count: Int,
                onWeek: suspend (ScheduleWeek) -> Unit,
            ) {
                sessions.incrementAndGet()
                // The session breaks off after two of the three weeks.
                onWeek(week(group, firstWeekStart))
                onWeek(week(group, firstWeekStart.plusWeeks(1)))
                throw java.io.IOException("connection reset")
            }
        }
        val repository = ScheduleRepository(database, remote)
        val delivered = mutableListOf<LocalDate>()

        repository.refreshWeeks("ИОП-ИТ-26/1", listOf(start, start.plusWeeks(1), start.plusWeeks(2))) {
            delivered += it.week.weekStart
        }

        assertEquals(1, sessions.get())
        assertEquals(1, single.get())
        assertEquals(listOf(start, start.plusWeeks(1), start.plusWeeks(2)), delivered)
        assertNotNull(repository.cachedWeek("ИОП-ИТ-26/1", start.plusWeeks(2)))
    }
}
