package com.rassvet.lecturial.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rassvet.lecturial.data.local.LecturialDatabase
import com.rassvet.lecturial.model.StudyPlan
import com.rassvet.lecturial.model.StudyPlanType
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyPlanRepositoryTest {
    private lateinit var database: LecturialDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            LecturialDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun plansAreStoredCompletedAndDeletedLocally() = runBlocking {
        var updates = 0
        val repository = StudyPlanRepository(database) { updates++ }
        val later = plan("later", LocalDate.of(2026, 11, 12), createdAt = 1L)
        val earlier = plan("earlier", LocalDate.of(2026, 11, 11), createdAt = 2L, dueTime = LocalTime.of(14, 25))

        repository.save(later)
        repository.save(earlier)
        assertEquals(listOf("earlier", "later"), repository.plans.first().map { it.id })
        assertEquals(LocalTime.of(14, 25), repository.plans.first().first().dueTime)
        assertEquals(2, repository.activeCount())

        repository.setCompleted("earlier", true)
        assertEquals(listOf("later", "earlier"), repository.plans.first().map { it.id })
        assertEquals(1, repository.activeCount())

        repository.delete("later")
        assertEquals(listOf("earlier"), repository.plans.first().map { it.id })
        assertEquals(4, updates)
    }

    private fun plan(id: String, dueDate: LocalDate, createdAt: Long, dueTime: LocalTime? = null) = StudyPlan(
        id = id,
        type = StudyPlanType.Deadline,
        title = "План $id",
        dueDate = dueDate,
        dueTime = dueTime,
        createdAtMillis = createdAt,
    )
}