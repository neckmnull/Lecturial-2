package com.rassvet.lecturial.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.rassvet.lecturial.LecturialApplication
import com.rassvet.lecturial.MainActivity
import com.rassvet.lecturial.R
import com.rassvet.lecturial.model.LessonAlarmKind
import com.rassvet.lecturial.model.LessonLivePhase
import com.rassvet.lecturial.model.LessonRemindMinutes
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.VisibleLessonLive
import com.rassvet.lecturial.model.displayEventType
import com.rassvet.lecturial.model.formatCountdownMillis
import com.rassvet.lecturial.model.formatLiveChip
import com.rassvet.lecturial.model.lessonProgress
import com.rassvet.lecturial.model.lessonsOutsidePause
import com.rassvet.lecturial.model.renamedDiscipline
import com.rassvet.lecturial.model.scheduledLessonAlarms
import com.rassvet.lecturial.model.visibleLessonLive
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlin.math.sin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object LessonAlerts {
    const val CHANNEL_ID = "lesson_live"
    const val NOTIFICATION_ID = 16
    const val ACTION_ALARM = "com.rassvet.lecturial.LESSON_ALARM"

    private const val EXTRA_KIND = "kind"
    private const val EXTRA_DATE = "date"
    private const val EXTRA_ID = "id"
    private const val EXTRA_DISCIPLINE = "discipline"
    private const val EXTRA_TYPE = "type"
    private const val EXTRA_START = "start"
    private const val EXTRA_END = "end"
    private const val EXTRA_CLASSROOM = "classroom"
    private const val PREFS = "lesson_alerts"
    private const val KEY_CODES = "codes"
    private const val KEY_ALERTED = "alerted_event"
    private const val ACCENT = 0xFF8AABFF.toInt()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    fun sync(context: Context, onComplete: (() -> Unit)? = null) {
        val app = context.applicationContext as? LecturialApplication ?: run {
            onComplete?.invoke()
            return
        }
        scope.launch {
            try {
                mutex.withLock { runCatching { refresh(app) } }
            } finally {
                onComplete?.invoke()
            }
        }
    }

    fun testUpcoming(context: Context) {
        val now = System.currentTimeMillis()
        val event = testEvent()
        ensureChannel(context)
        post(
            context,
            event,
            alert = true,
            now = LocalDateTime.now(),
            live = VisibleLessonLive(event, LessonLivePhase.UPCOMING, now + 15 * 60_000L, now + 105 * 60_000L, 15),
            isTest = true,
        )
    }

    fun testCurrentHourFifteen(context: Context) {
        val now = System.currentTimeMillis()
        val event = testEvent()
        ensureChannel(context)
        post(
            context,
            event,
            alert = false,
            now = LocalDateTime.now(),
            live = VisibleLessonLive(event, LessonLivePhase.CURRENT, now - 15 * 60_000L, now + 75 * 60_000L, 75),
            isTest = true,
        )
    }

    fun clearTest(context: Context) = stopLive(context)

    fun openLiveUpdateSettings(context: Context) {
        if (Build.VERSION.SDK_INT < 36) return
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
    }

    private fun testEvent() = ScheduleEvent(
        id = "notification-test",
        date = LocalDate.now(),
        startTime = "12:00",
        endTime = "13:30",
        pairNumber = 3,
        discipline = "Тест Live-уведомления",
        type = "Лекция",
        classroom = "Тестовая аудитория",
        address = null,
        teachers = emptyList(),
        comment = null,
        onlineUrl = null,
    )

    private fun stopLive(context: Context) {
        LessonLiveService.stop(context)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun publish(context: Context, notification: Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        if (runCatching {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            }.isFailure
        ) {
            return
        }
    }


    /**
     * Every alarm just re-runs [refresh]: it sees the whole day, so a reminder for the next
     * pair can't replace the countdown of the pair that is still going on.
     */
    fun onAlarm(context: Context, onComplete: (() -> Unit)? = null) = sync(context, onComplete)

    internal suspend fun refreshNow(context: Context) {
        val app = context.applicationContext as? LecturialApplication ?: return
        mutex.withLock { runCatching { refresh(app) } }
    }

    private suspend fun refresh(app: LecturialApplication) {
        ensureChannel(app)
        if (LessonLiveService.isTestActive(app)) return
        val group = app.preferences.selectedGroup.first()
        if (group == null) {
            cancelAlarms(app)
            NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
            return
        }
        val today = LocalDate.now()
        val teacher = app.preferences.languageTeacher(group).first()
        val pausedUntil = app.preferences.lessonAlertsPausedUntil.first()
            ?.takeIf { it > System.currentTimeMillis() }
        val events = app.repository.eventsBetween(group, today, today.plusDays(13)).filter {
            com.rassvet.lecturial.model.matchesLanguageTeacher(it.discipline, it.teachers, teacher)
        }.let { lessonsOutsidePause(it, pausedUntil) }
        val now = LocalDateTime.now()
        scheduleAlarms(app, events, now)
        val live = visibleLessonLive(events, now)
        if (live == null) {
            stopLive(app)
        } else {
            post(app, live.event, alert = claimAlert(app, live), now = now, live = live)
        }
    }

    /** Alerts once per upcoming pair, however many times the live notification is refreshed. */
    private fun claimAlert(context: Context, live: VisibleLessonLive): Boolean {
        if (live.phase != LessonLivePhase.UPCOMING) return false
        val key = "${live.event.date}-${live.event.id}"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_ALERTED, null) == key) return false
        prefs.edit().putString(KEY_ALERTED, key).apply()
        return true
    }

    private fun post(
        context: Context,
        event: ScheduleEvent,
        alert: Boolean,
        now: LocalDateTime,
        live: VisibleLessonLive? = visibleLessonLive(listOf(event), now),
        isTest: Boolean = false,
    ) {
        val notification = buildNotification(context, event, alert, live ?: visibleLessonLive(listOf(event), now)) ?: return
        publish(context, notification)
        LessonLiveService.start(
            context,
            event,
            live ?: visibleLessonLive(listOf(event), now) ?: return,
            notification,
            isTest,
        )
    }

    internal fun refreshFromService(
        context: Context,
        event: ScheduleEvent,
        visible: VisibleLessonLive,
    ): Notification? {
        val notification = buildNotification(context, event, alert = false, visible = visible) ?: return null
        publish(context, notification)
        return notification
    }

    private fun buildNotification(
        context: Context,
        event: ScheduleEvent,
        alert: Boolean,
        visible: VisibleLessonLive?,
    ): Notification? {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        visible ?: return null
        val title = displayTitle(context, event.discipline)
        val remainingMillis = (visible.targetMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val type = displayEventType(event.type)
        val whenLabel = formatCountdownMillis(
            remainingMillis,
            upcoming = visible.phase == LessonLivePhase.UPCOMING,
        )
        val place = event.classroom?.takeIf { it.isNotBlank() }
        val text = buildString {
            append(whenLabel)
            if (type.isNotBlank()) append(" · ").append(type)
            if (place != null) append(" · ").append(place)
        }
        val progress = lessonProgress(visible, System.currentTimeMillis())
        val chipText = formatLiveChip(
            ((remainingMillis + 59_999L) / 60_000L).toInt().coerceAtLeast(1),
        )
        return if (Build.VERSION.SDK_INT >= 36) {
            buildStatusChipNotification(
                context, event, visible, text, chipText, progress.max, progress.current, alert, title,
            )
        } else {
            buildCompatNotification(
                context, event, visible, text, chipText, progress.max, progress.current, alert, title,
            )
        }
    }

    @RequiresApi(36)
    private fun buildStatusChipNotification(
        context: Context,
        event: ScheduleEvent,
        visible: VisibleLessonLive,
        text: String,
        chipText: String,
        total: Int,
        elapsed: Int,
        alert: Boolean,
        title: String,
    ): Notification {
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(elapsed)
            .setProgressSegments(
                listOf(Notification.ProgressStyle.Segment(total.coerceAtLeast(1)).setColor(ACCENT)),
            )
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lecturial)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(if (visible.phase == LessonLivePhase.CURRENT) "Идёт пара" else "Скоро пара")
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(!alert)
            .setCategory(Notification.CATEGORY_EVENT)
            .setColor(ACCENT)
            .setProgress(total.coerceAtLeast(1), elapsed.coerceIn(0, total.coerceAtLeast(1)), false)
            .setStyle(style)
            // Keep a readable value available when Pixel expands the promoted chip.
            .setShortCriticalText(chipText)
            .setWhen(visible.targetMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(3 * 60 * 60 * 1000L)
            .addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
        if (alert) builder.setDefaults(Notification.DEFAULT_ALL)
        return builder.build()
    }

    private fun buildCompatNotification(
        context: Context,
        event: ScheduleEvent,
        visible: VisibleLessonLive,
        text: String,
        chipText: String,
        total: Int,
        elapsed: Int,
        alert: Boolean,
        title: String,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lecturial)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(if (visible.phase == LessonLivePhase.CURRENT) "Идёт пара" else "Скоро пара")
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(!alert)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(chipText)
            .setWhen(visible.targetMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setPriority(if (alert) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setProgress(total.coerceAtLeast(1), elapsed.coerceIn(0, total.coerceAtLeast(1)), false)
            .setStyle(compatProgressStyle(total, elapsed))
        if (alert) builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        return builder.build()
    }

    private fun displayTitle(context: Context, original: String): String {
        val app = context.applicationContext as? LecturialApplication ?: return original
        val aliases = runCatching {
            kotlinx.coroutines.runBlocking { app.preferences.lessonAliases.first() }
        }.getOrDefault(emptyMap())
        return renamedDiscipline(original, aliases)
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun compatProgressStyle(total: Int, elapsed: Int): NotificationCompat.ProgressStyle {
        val length = total.coerceAtLeast(1)
        return NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(compatWavySegments(length))
            .setProgress(elapsed.coerceIn(0, length))
    }

    @RequiresApi(36)
    private fun platformWavySegments(total: Int): List<Notification.ProgressStyle.Segment> {
        val lengths = wavyLengths(total)
        val colors = wavyColors(lengths.size)
        return lengths.mapIndexed { index, length ->
            Notification.ProgressStyle.Segment(length).setColor(colors[index])
        }
    }

    private fun compatWavySegments(total: Int): List<NotificationCompat.ProgressStyle.Segment> {
        val lengths = wavyLengths(total)
        val colors = wavyColors(lengths.size)
        return lengths.mapIndexed { index, length ->
            NotificationCompat.ProgressStyle.Segment(length).setColor(colors[index])
        }
    }

    private fun wavyLengths(total: Int): IntArray {
        val size = total.coerceAtLeast(1)
        val waves = minOf(18, size)
        val lengths = IntArray(waves)
        var assigned = 0
        for (index in 0 until waves) {
            val leftParts = waves - index
            val left = size - assigned
            val weight = 1.0 + 0.65 * sin(index * 0.85)
            val weightLeft = (index until waves).sumOf { 1.0 + 0.65 * sin(it * 0.85) }
            lengths[index] = if (leftParts == 1) {
                left
            } else {
                ((left * weight / weightLeft).toInt()).coerceIn(1, left - (leftParts - 1))
            }
            assigned += lengths[index]
        }
        return lengths
    }

    private fun wavyColors(count: Int): IntArray {
        val peak = 0xFFD7E3FF.toInt()
        return IntArray(count) { index ->
            val wave = ((sin(index * 0.9) + 1.0) / 2.0).toFloat()
            ColorUtils.blendARGB(ACCENT, peak, wave)
        }
    }

    private fun scheduleAlarms(app: LecturialApplication, events: List<ScheduleEvent>, now: LocalDateTime) {
        cancelAlarms(app)
        val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return
        val exact = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
        val codes = LinkedHashSet<Int>()
        scheduledLessonAlarms(events, now, ZoneId.systemDefault()).forEach { alarm ->
            val code = requestCode(alarm.kind, alarm.event)
            val pending = PendingIntent.getBroadcast(
                app,
                code,
                alarmIntent(app, alarm.kind, alarm.event),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            if (exact) {
                runCatching {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAtMillis, pending)
                }.getOrElse {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAtMillis, pending)
                }
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAtMillis, pending)
            }
            codes += code
        }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_CODES, codes.map(Int::toString).toSet())
            .apply()
    }

    private fun cancelAlarms(app: Context) {
        val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val codes = prefs.getStringSet(KEY_CODES, emptySet()).orEmpty()
        codes.forEach { raw ->
            val code = raw.toIntOrNull() ?: return@forEach
            val pending = PendingIntent.getBroadcast(
                app,
                code,
                Intent(app, LessonAlertReceiver::class.java).setAction(ACTION_ALARM).setPackage(app.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.cancel(pending)
        }
        prefs.edit().remove(KEY_CODES).apply()
    }

    private fun alarmIntent(context: Context, kind: LessonAlarmKind, event: ScheduleEvent): Intent =
        Intent(context, LessonAlertReceiver::class.java).apply {
            action = ACTION_ALARM
            setPackage(context.packageName)
            putExtra(EXTRA_KIND, kind.name)
            putExtra(EXTRA_DATE, event.date.toString())
            putExtra(EXTRA_ID, event.id)
            putExtra(EXTRA_DISCIPLINE, event.discipline)
            putExtra(EXTRA_TYPE, event.type)
            putExtra(EXTRA_START, event.startTime)
            putExtra(EXTRA_END, event.endTime)
            putExtra(EXTRA_CLASSROOM, event.classroom)
        }

    private fun requestCode(kind: LessonAlarmKind, event: ScheduleEvent): Int =
        31 * 31 * kind.name.hashCode() + 31 * event.date.toString().hashCode() + event.id.hashCode()

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Пары",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Напоминание за 15 минут и ход текущей пары"
                enableVibration(true)
            },
        )
    }
}
