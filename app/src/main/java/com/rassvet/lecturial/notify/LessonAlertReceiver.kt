package com.rassvet.lecturial.notify

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.atomic.AtomicInteger

class LessonAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            LessonAlerts.ACTION_ALARM -> {
                val pendingResult = goAsync()
                LessonAlerts.onAlarm(context) { pendingResult.finish() }
            }
        }
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        UpdateNotifier.schedule(context)
        val pendingResult = goAsync()
        val remaining = AtomicInteger(2)
        val complete = { if (remaining.decrementAndGet() == 0) pendingResult.finish() }
        LessonAlerts.sync(context, complete)
        DeadlineAlerts.sync(context, complete)
    }
}

class ScheduleChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val supported = setOf(
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
        if (intent.action !in supported) return
        val pendingResult = goAsync()
        val remaining = AtomicInteger(2)
        val complete = { if (remaining.decrementAndGet() == 0) pendingResult.finish() }
        LessonAlerts.sync(context, complete)
        DeadlineAlerts.sync(context, complete)
    }
}
