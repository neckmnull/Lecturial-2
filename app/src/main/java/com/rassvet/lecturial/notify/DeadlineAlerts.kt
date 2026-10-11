package com.rassvet.lecturial.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rassvet.lecturial.LecturialApplication
import com.rassvet.lecturial.MainActivity
import com.rassvet.lecturial.R
import com.rassvet.lecturial.model.StudyPlan
import com.rassvet.lecturial.model.scheduledDeadlineReminders
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object DeadlineAlerts {
    const val ACTION_ALARM = "com.rassvet.lecturial.DEADLINE_ALARM"
    private const val CHANNEL_ID = "plan_deadlines"
    private const val PREFS = "deadline_alerts"
    private const val KEY_CODES = "codes"
    private const val EXTRA_PLAN_ID = "plan_id"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_TYPE = "type"
    private const val EXTRA_DAYS_BEFORE = "days_before"
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

    fun onAlarm(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf(String::isNotBlank) ?: return
        val type = intent.getStringExtra(EXTRA_TYPE).orEmpty()
        val daysBefore = intent.getIntExtra(EXTRA_DAYS_BEFORE, 0)
        val planId = intent.getStringExtra(EXTRA_PLAN_ID).orEmpty()
        ensureChannel(context)
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            val text = when (daysBefore) {
                0 -> "Срок сегодня"
                1 -> "До срока остался 1 день"
                else -> "До срока осталось $daysBefore дня"
            }
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_lecturial)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(type)
                .setColor(ACCENT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .build()
            NotificationManagerCompat.from(context).notify(notificationId(planId, daysBefore), notification)
        }
        sync(context)
    }

    private suspend fun refresh(app: LecturialApplication) {
        ensureChannel(app)
        cancelAlarms(app)
        val days = app.preferences.deadlineReminderDays.first()
        if (days.isEmpty()) return
        val reminders = scheduledDeadlineReminders(
            plans = app.studyPlanRepository.all(),
            reminderDays = days,
            now = LocalDateTime.now(),
            zoneId = ZoneId.systemDefault(),
        )
        val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return
        val exact = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
        val codes = linkedSetOf<Int>()
        reminders.forEach { reminder ->
            val code = requestCode(reminder.plan.id, reminder.daysBefore)
            val pending = PendingIntent.getBroadcast(
                app,
                code,
                alarmIntent(app, reminder.plan, reminder.daysBefore),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            if (exact) {
                runCatching {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pending)
                }.getOrElse {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pending)
                }
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pending)
            }
            codes += code
        }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY_CODES, codes.map(Int::toString).toSet())
            .apply()
    }

    private fun cancelAlarms(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEY_CODES, emptySet()).orEmpty().forEach { raw ->
            val code = raw.toIntOrNull() ?: return@forEach
            val pending = PendingIntent.getBroadcast(
                context,
                code,
                Intent(context, DeadlineAlertReceiver::class.java).setAction(ACTION_ALARM),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.cancel(pending)
        }
        prefs.edit().remove(KEY_CODES).apply()
    }

    private fun alarmIntent(context: Context, plan: StudyPlan, daysBefore: Int) =
        Intent(context, DeadlineAlertReceiver::class.java).apply {
            action = ACTION_ALARM
            setPackage(context.packageName)
            putExtra(EXTRA_PLAN_ID, plan.id)
            putExtra(EXTRA_TITLE, plan.title)
            putExtra(EXTRA_TYPE, plan.type.title)
            putExtra(EXTRA_DAYS_BEFORE, daysBefore)
        }

    private fun requestCode(planId: String, daysBefore: Int): Int =
        31 * planId.hashCode() + daysBefore

    private fun notificationId(planId: String, daysBefore: Int): Int =
        requestCode(planId, daysBefore) xor 0x4D31

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Сроки планов", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Выбранные напоминания о задачах, целях и дедлайнах"
                enableVibration(true)
            },
        )
    }
}

class DeadlineAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DeadlineAlerts.ACTION_ALARM) DeadlineAlerts.onAlarm(context, intent)
    }
}