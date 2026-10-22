package com.rassvet.lecturial.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoRefreshTest {
    private val week = LocalDate.of(2026, 10, 19)
    private val starts = listOf(week.minusWeeks(1), week, week.plusWeeks(1))
    private val now = 10 * AUTO_REFRESH_MILLIS
    private val hour = 60 * 60 * 1000L

    @Test
    fun `weeks fetched within four hours aren't fetched again on entry`() {
        val fresh = starts.associateWith { now - 3 * hour }
        assertEquals(emptyList<LocalDate>(), weeksToFetch(starts, fresh, now, manual = false))
    }

    @Test
    fun `pull to refresh always fetches everything`() {
        val fresh = starts.associateWith { now - hour }
        assertEquals(starts, weeksToFetch(starts, fresh, now, manual = true))
    }

    @Test
    fun `old or missing weeks are fetched as one span`() {
        // The current week is fresh, the ones around it are old or never fetched.
        val fetchedAt = mapOf(starts[0] to now - 5 * hour, starts[1] to now - hour)
        assertEquals(starts, weeksToFetch(starts, fetchedAt, now, manual = false))
        assertEquals(listOf(starts[2]), weeksToFetch(starts, mapOf(starts[0] to now, starts[1] to now), now, manual = false))
        assertEquals(starts, weeksToFetch(starts, emptyMap(), now, manual = false))
    }

    @Test
    fun `exactly four hours counts as old`() {
        assertEquals(starts, weeksToFetch(starts, starts.associateWith { now - AUTO_REFRESH_MILLIS }, now, manual = false))
    }
}
