package com.rassvet.lecturial

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rassvet.lecturial.model.UniversityZone
import com.rassvet.lecturial.notify.LessonAlerts
import com.rassvet.lecturial.ui.FirstRun
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The real activity with the real app behind it, through every way in: the launcher, notifications, the widget. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = testApp
    private val today: LocalDate = LocalDate.now(UniversityZone)
    private val tomorrow: LocalDate = today.plusDays(1)
    private val plain get() = Intent(app, MainActivity::class.java)

    @Before
    fun skipFirstRunSetup() = FirstRun.markDone(app)

    // Not ActivityScenario: it knows its activity by the intent, and the app clears what it has handled.
    private fun opens(intent: Intent, showing: String): Unit = runBlocking {
        withTestSchedule(
            listOf(
                lesson("today", today, "08:45", "10:05", "Физика"),
                lesson("tomorrow", tomorrow, "08:45", "10:05", "Завтрашняя пара"),
            ),
        ) {
            val activity = InstrumentationRegistry.getInstrumentation()
                .startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            try {
                compose.waitUntil(15_000L) { compose.onAllNodesWithText(showing).fetchSemanticsNodes().isNotEmpty() }
            } finally {
                onMain { activity.finish() }
                eventually("the activity to close") { activity.isDestroyed }
            }
        }
        // The activity set alarms for the test's lessons.
        LessonAlerts.refreshNow(app)
    }

    @Test
    fun theLauncherOpensTheSchedule() = opens(plain, showing = "Настройки")

    @Test
    fun theGradesNotificationOpensStudy() = opens(MainActivity.openStudyIntent(app), showing = "Войти в личный кабинет")

    @Test
    fun theMicrosoftSignInReturnsToOutlook() = opens(MainActivity.openOutlookIntent(app), showing = "Войти через Microsoft")

    @Test
    fun theUpdateNotificationOpensUpdates() = opens(MainActivity.openUpdatesIntent(app), showing = "Обновления")

    @Test
    fun aPlanThatNoLongerExistsDoesNoHarm() = opens(MainActivity.openPlanIntent(app, "no-such-plan"), showing = "Настройки")

    @Test
    fun aLessonTappedInTheWidgetOpensItsDay() = opens(
        plain.putExtra(MainActivity.EXTRA_LESSON_ID, "tomorrow").putExtra(MainActivity.EXTRA_LESSON_DATE, tomorrow.toString()),
        showing = "Завтрашняя пара",
    )

    @Test
    fun badDataFromOutsideIsIgnored() {
        opens(plain.putExtra(MainActivity.EXTRA_LESSON_ID, "lesson").putExtra(MainActivity.EXTRA_LESSON_DATE, "not a date"), showing = "Настройки")
        opens(plain.putExtra(MainActivity.EXTRA_PLAN_ID, "   "), showing = "Настройки")
    }
}
