package com.rassvet.lecturial.data.local

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rassvet.lecturial.data.SyncedEntriesFile
import com.rassvet.lecturial.model.AppUpdateState
import com.rassvet.lecturial.model.DayPlannerSettings
import com.rassvet.lecturial.model.ExtracurricularChoice
import com.rassvet.lecturial.model.StudyTask
import com.rassvet.lecturial.model.SyncedEntry
import com.rassvet.lecturial.testApp
import java.io.File
import java.security.KeyStore
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** What the app keeps on the phone: encryption with the real Keystore, settings and small records. */
@RunWith(AndroidJUnit4::class)
class LocalStorageTest {
    private val app = testApp
    private val preferences = app.preferences
    private val today = LocalDate.of(2026, 11, 6)

    @Test
    fun aSecretBoxSealsAndOpensAndRefusesWhatIsNotItsOwn() {
        val box = SecretBox(TEST_KEY)
        val other = SecretBox("$TEST_KEY-other")
        try {
            val sealed = box.encrypt("сессия=секрет")

            assertEquals("сессия=секрет", box.decrypt(sealed))
            assertFalse(sealed.contains("секрет"))
            assertNotEquals("A fresh vector every time", sealed, box.encrypt("сессия=секрет"))
            assertNull("Another key", other.decrypt(sealed))
            assertNull("Not sealed at all", box.decrypt("garbage"))
            assertNull("Tampered with", box.decrypt(sealed.dropLast(4) + "AAAA"))
        } finally {
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                deleteEntry(TEST_KEY)
                deleteEntry("$TEST_KEY-other")
            }
        }
    }

    @Test
    fun aRequiredUpdateIsRememberedAndALaterReleaseCanLiftIt() = runBlocking {
        val before = preferences.requiredUpdateVersion.first()
        fun available(required: Boolean) = AppUpdateState.Available("9.9.9", "url", "apk", "name.apk", required = required)
        try {
            preferences.followUpdateCheck(available(required = true))
            assertEquals("9.9.9", preferences.requiredUpdateVersion.first())

            preferences.followUpdateCheck(AppUpdateState.Checking)
            assertEquals("Something else changes nothing", "9.9.9", preferences.requiredUpdateVersion.first())

            preferences.followUpdateCheck(available(required = false))
            assertNull("A release that requires nothing lifts it", preferences.requiredUpdateVersion.first())

            preferences.followUpdateCheck(available(required = true))
            preferences.followUpdateCheck(AppUpdateState.UpToDate("9.9.9"))
            assertNull(preferences.requiredUpdateVersion.first())
        } finally {
            preferences.followUpdateCheck(
                if (before != null) AppUpdateState.Available(before, "url", "apk", "name.apk", required = true) else AppUpdateState.UpToDate("0"),
            )
        }
    }

    @Test
    fun theEventChoiceAndOtherSettingsReadBackAsSaved() = runBlocking {
        val choice = preferences.extracurricularChoice.first()
        val days = preferences.deadlineReminderDays.first()
        val aliases = preferences.lessonAliases.first()
        try {
            val chosen = ExtracurricularChoice(show = false, hiddenNames = setOf("Разговоры о важном", "Встречи с группой"))
            preferences.setExtracurricularChoice(chosen)
            assertEquals(chosen, preferences.extracurricularChoice.first())

            preferences.setDeadlineReminderDays(setOf(3))
            assertEquals(setOf(3), preferences.deadlineReminderDays.first())

            val names = mapOf("Математический анализ \"углублённый\"" to "Матан, часть 1")
            preferences.setLessonAliases(names)
            assertEquals(names, preferences.lessonAliases.first())
        } finally {
            preferences.setExtracurricularChoice(choice)
            preferences.setDeadlineReminderDays(days)
            preferences.setLessonAliases(aliases)
        }
    }

    @Test
    fun allTeachersIsAChoiceSoTheQuestionIsNotAskedAgain() = runBlocking {
        val group = "test-group-${UUID.randomUUID()}"
        assertNull("Not chosen yet", preferences.languageTeacher(group).first())

        preferences.setLanguageTeacher(group, null)
        assertEquals("", preferences.languageTeacher(group).first())

        preferences.setLanguageTeacher(group, "Иванова Анна")
        assertEquals("Иванова Анна", preferences.languageTeacher(group).first())
    }

    @Test
    fun theDayPlanRemembersWhatWasDoneAcrossRestarts() {
        val prefs = app.getSharedPreferences("day_planner", Context.MODE_PRIVATE)
        val saved = prefs.getString("memory", null)
        try {
            prefs.edit().clear().commit()
            val task = StudyTask("hw:1", "Математический анализ (Математика)", "ДЗ", null, 45, 3.0)
            val store = DayPlannerStore(app)
            val day = LocalDate.now()

            store.setDone(day, task, studied = true)
            store.putOff(day, task.copy(key = "plan:2", discipline = null))
            store.updateSettings(DayPlannerSettings(dayEnd = LocalTime.of(20, 0), blockMinutes = 30, maxStudyMinutes = 120))

            val reopened = DayPlannerStore(app).memory.value
            assertEquals(setOf("hw:1", "plan:2"), reopened.doneOn(day))
            assertEquals("A put-off task isn't study", 45, reopened.minutesOn(day))
            assertEquals(day, reopened.lastStudied["математический анализ"])
            assertEquals(LocalTime.of(20, 0), reopened.settings.dayEnd)
            assertEquals(30, reopened.settings.blockMinutes)

            store.setDone(day, task, studied = false)
            assertFalse("hw:1" in DayPlannerStore(app).memory.value.doneOn(day))
        } finally {
            prefs.edit().clear().apply { saved?.let { putString("memory", it) } }.commit()
        }
    }

    @Test
    fun theRecordOfSyncedEventsSurvivesAndABrokenOneReadsAsEmpty() {
        val file = File(app.cacheDir, "test-synced-${UUID.randomUUID()}.json")
        try {
            val record = SyncedEntriesFile(file)
            assertTrue("No file yet", record.load().isEmpty())

            val entries = listOf(SyncedEntry("lesson:a", "r1", "fp", today), SyncedEntry("plan:b:$today", "r2", "fp2", today))
            record.save(entries)
            assertEquals(entries, record.load())
            assertFalse("Nothing left half-written", File(file.path + ".part").exists())

            file.writeText("{ broken")
            assertTrue(record.load().isEmpty())
        } finally {
            file.delete()
        }
    }

    private companion object {
        const val TEST_KEY = "lecturial_test_box"
    }
}
