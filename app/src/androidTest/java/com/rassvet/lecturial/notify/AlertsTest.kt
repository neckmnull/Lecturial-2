package com.rassvet.lecturial.notify

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rassvet.lecturial.R
import com.rassvet.lecturial.eventually
import com.rassvet.lecturial.grantNotifications
import com.rassvet.lecturial.lesson
import com.rassvet.lecturial.model.ExtracurricularChoice
import com.rassvet.lecturial.model.LessonAlertPause
import com.rassvet.lecturial.model.LessonLivePhase
import com.rassvet.lecturial.model.UniversityZone
import com.rassvet.lecturial.model.VisibleLessonLive
import com.rassvet.lecturial.testApp
import com.rassvet.lecturial.withTestSchedule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Alarms and notifications on the phone itself. Notifications appear for a moment and are taken away. */
@RunWith(AndroidJUnit4::class)
class AlertsTest {
    private val app = testApp
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val alarms get() = app.getSharedPreferences("lesson_alerts", Context.MODE_PRIVATE).getStringSet("codes", emptySet()).orEmpty()

    @Test
    fun alarmsFollowTheScheduleTheEventChoiceAndThePause() = runBlocking {
        val day = LocalDate.now(UniversityZone).plusDays(1)
        val pause = app.preferences.lessonAlertsPausedUntil.first()
        val choice = app.preferences.extracurricularChoice.first()
        try {
            app.preferences.setLessonAlertsPausedUntil(null)
            app.preferences.setExtracurricularChoice(ExtracurricularChoice())
            withTestSchedule(
                listOf(
                    lesson("a", day, "08:45", "10:05", "Физика"),
                    lesson("b", day, "10:20", "11:40", "Тьюторский час", type = "Внеучебное"),
                ),
            ) {
                LessonAlerts.refreshNow(app)
                assertEquals("A reminder, the start and the end of each", 6, alarms.size)

                app.preferences.setExtracurricularChoice(ExtracurricularChoice(hiddenNames = setOf("Тьюторский час")))
                LessonAlerts.refreshNow(app)
                assertEquals("A hidden event leaves no alarms", 3, alarms.size)

                app.preferences.setLessonAlertsPausedUntil(LessonAlertPause.FOREVER_MILLIS)
                LessonAlerts.refreshNow(app)
                assertEquals(0, alarms.size)
            }
        } finally {
            app.preferences.setLessonAlertsPausedUntil(pause)
            app.preferences.setExtracurricularChoice(choice)
            LessonAlerts.refreshNow(app)
        }
    }

    @Test
    fun aLessonUnderWayShowsInTheLiveNotification() = runBlocking {
        grantNotifications()
        assumeTrue(NotificationManagerCompat.from(app).areNotificationsEnabled())
        val now = LocalDateTime.now(UniversityZone)
        // Times of a day: no lesson can run across midnight.
        assumeTrue(now.hour in 1..22)
        val time = DateTimeFormatter.ofPattern("HH:mm")
        val pause = app.preferences.lessonAlertsPausedUntil.first()
        try {
            app.preferences.setLessonAlertsPausedUntil(null)
            withTestSchedule(
                listOf(lesson("a", now.toLocalDate(), now.minusMinutes(10).format(time), now.plusMinutes(10).format(time), "Физика")),
            ) {
                LessonAlerts.refreshNow(app)

                eventually("the notification") { manager.activeNotifications.any { it.id == LessonAlerts.NOTIFICATION_ID } }
                val notification = manager.activeNotifications.first { it.id == LessonAlerts.NOTIFICATION_ID }.notification
                assertEquals("Физика", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
                assertEquals("Идёт пара", notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString())
            }
        } finally {
            LessonAlerts.clearTest(app)
            app.preferences.setLessonAlertsPausedUntil(pause)
            LessonAlerts.refreshNow(app)
        }
    }

    @Test
    fun theLiveNotificationCanBeStoppedTheMomentItStarts() = runBlocking {
        grantNotifications()
        assumeTrue(NotificationManagerCompat.from(app).areNotificationsEnabled())
        // Creates the channel the service posts to.
        LessonAlerts.refreshNow(app)
        val now = System.currentTimeMillis()
        val event = lesson("live", LocalDate.now(UniversityZone), "08:45", "10:05", "Физика")
        val live = VisibleLessonLive(event, LessonLivePhase.CURRENT, now - 60_000L, now + 600_000L, 10)
        val notification = NotificationCompat.Builder(app, LessonAlerts.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lecturial)
            .setContentTitle("Физика")
            .build()
        try {
            // Stopped before it went foreground, the service used to take the whole app down with it.
            repeat(5) {
                LessonLiveService.start(app, event, live, notification, isTest = true)
                LessonLiveService.stop(app)
            }
            Thread.sleep(3_000)
            assertFalse(manager.activeNotifications.any { it.id == LessonAlerts.NOTIFICATION_ID })
        } finally {
            LessonAlerts.clearTest(app)
        }
    }

    @Test
    fun deadlineRemindersCountTheDaysInProperRussian() {
        grantNotifications()
        assumeTrue(NotificationManagerCompat.from(app).areNotificationsEnabled())
        listOf(
            7 to "До срока осталось 7 дней",
            3 to "До срока осталось 3 дня",
            1 to "До срока остался 1 день",
            0 to "Срок сегодня",
        ).forEach { (days, expected) ->
            val title = "Тест срока $days"
            DeadlineAlerts.onAlarm(
                app,
                Intent(DeadlineAlerts.ACTION_ALARM)
                    .putExtra("plan_id", "test-plan-$days")
                    .putExtra("title", title)
                    .putExtra("type", "Дедлайн")
                    .putExtra("days_before", days),
            )
            eventually("the reminder") { active(title) != null }
            assertEquals(expected, active(title)!!.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            manager.cancel(active(title)!!.id)
        }
    }

    private fun active(title: String) = manager.activeNotifications.firstOrNull {
        it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == title
    }
}
