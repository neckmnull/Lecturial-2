package com.rassvet.lecturial.notify

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.rassvet.lecturial.model.LessonLivePhase
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.VisibleLessonLive
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class LessonLiveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val states = MutableStateFlow<State?>(null)
    private var updateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = intent?.toState() ?: run {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val initial = pendingNotification ?: LessonAlerts.refreshFromService(this, state.event, state.live)
        if (initial == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        pendingNotification = null
        ServiceCompat.startForeground(
            this,
            LessonAlerts.NOTIFICATION_ID,
            initial,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        states.value = state
        if (updateJob?.isActive != true) updateJob = scope.launch { runUpdates() }
        return START_REDELIVER_INTENT
    }

    private suspend fun CoroutineScope.runUpdates() {
        while (isActive) {
            val current = states.value ?: break
            val now = System.currentTimeMillis()
            if (now >= current.live.endMillis) {
                // Pick the next lesson while this service is still in the foreground: a
                // fresh foreground-service start from the background is refused on Android 12+.
                LessonAlerts.refreshNow(applicationContext)
                if (states.value === current) break
                continue
            }
            if (current.live.phase == LessonLivePhase.UPCOMING && now >= current.live.startMillis) {
                states.compareAndSet(current, current.copy(live = current.live.copy(phase = LessonLivePhase.CURRENT)))
                continue
            }
            LessonAlerts.refreshFromService(this@LessonLiveService, current.event, current.live)
            val wait = minOf(UPDATE_INTERVAL_MILLIS, current.live.targetMillis - now).coerceAtLeast(1L)
            withTimeoutOrNull(wait) { states.first { it !== current } }
        }
        if (isActive) {
            clearTestState(applicationContext)
            ServiceCompat.stopForeground(this@LessonLiveService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (running === this) running = null
        updateJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private data class State(val event: ScheduleEvent, val live: VisibleLessonLive)

    private fun Intent.toState(): State? {
        val date = getStringExtra(EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val phase = getStringExtra(EXTRA_PHASE)?.let { runCatching { LessonLivePhase.valueOf(it) }.getOrNull() } ?: return null
        val event = ScheduleEvent(
            id = getStringExtra(EXTRA_ID) ?: return null,
            date = date,
            startTime = getStringExtra(EXTRA_START_TIME).orEmpty(),
            endTime = getStringExtra(EXTRA_END_TIME).orEmpty(),
            pairNumber = null,
            discipline = getStringExtra(EXTRA_DISCIPLINE).orEmpty(),
            type = getStringExtra(EXTRA_TYPE).orEmpty(),
            classroom = getStringExtra(EXTRA_CLASSROOM),
            address = null,
            teachers = emptyList(),
            comment = null,
            onlineUrl = null,
        )
        val start = getLongExtra(EXTRA_START_MILLIS, 0L)
        val end = getLongExtra(EXTRA_END_MILLIS, 0L)
        if (start <= 0L || end <= start) return null
        val target = if (phase == LessonLivePhase.UPCOMING) start else end
        val minutes = ((target - System.currentTimeMillis() + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
        return State(event, VisibleLessonLive(event, phase, start, end, minutes))
    }

    companion object {
        private const val TAG = "LessonLiveService"
        private const val UPDATE_INTERVAL_MILLIS = 10_000L
        private const val EXTRA_DATE = "date"
        private const val EXTRA_ID = "id"
        private const val EXTRA_DISCIPLINE = "discipline"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_START_TIME = "start_time"
        private const val EXTRA_END_TIME = "end_time"
        private const val EXTRA_CLASSROOM = "classroom"
        private const val EXTRA_PHASE = "phase"
        private const val EXTRA_START_MILLIS = "start_millis"
        private const val EXTRA_END_MILLIS = "end_millis"
        private const val TEST_PREFS = "lesson_live_test"
        private const val KEY_TEST_TARGET = "target"

        @Volatile private var pendingNotification: Notification? = null
        @Volatile private var running: LessonLiveService? = null

        fun start(
            context: Context,
            event: ScheduleEvent,
            live: VisibleLessonLive,
            notification: Notification,
            isTest: Boolean,
        ) {
            if (isTest) {
                context.getSharedPreferences(TEST_PREFS, Context.MODE_PRIVATE)
                    .edit().putLong(KEY_TEST_TARGET, live.endMillis).apply()
            } else {
                clearTestState(context)
            }
            running?.let { service ->
                service.states.value = State(event, live)
                return
            }
            pendingNotification = notification
            val intent = Intent(context, LessonLiveService::class.java).apply {
                putExtra(EXTRA_DATE, event.date.toString())
                putExtra(EXTRA_ID, event.id)
                putExtra(EXTRA_DISCIPLINE, event.discipline)
                putExtra(EXTRA_TYPE, event.type)
                putExtra(EXTRA_START_TIME, event.startTime)
                putExtra(EXTRA_END_TIME, event.endTime)
                putExtra(EXTRA_CLASSROOM, event.classroom)
                putExtra(EXTRA_PHASE, live.phase.name)
                putExtra(EXTRA_START_MILLIS, live.startMillis)
                putExtra(EXTRA_END_MILLIS, live.endMillis)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (refused: IllegalStateException) {
                // Android 12+ refuses background starts without an exact-alarm exemption.
                // The notification is already posted; it just won't tick until the app opens.
                pendingNotification = null
                Log.w(TAG, "Live lesson service start refused", refused)
            }
        }

        fun stop(context: Context) {
            pendingNotification = null
            clearTestState(context)
            context.stopService(Intent(context, LessonLiveService::class.java))
        }

        fun isTestActive(context: Context): Boolean {
            val prefs = context.getSharedPreferences(TEST_PREFS, Context.MODE_PRIVATE)
            val target = prefs.getLong(KEY_TEST_TARGET, 0L)
            val notificationActive = context.getSystemService(NotificationManager::class.java)
                ?.activeNotifications
                ?.any { it.id == LessonAlerts.NOTIFICATION_ID } == true
            return if (target > System.currentTimeMillis() && notificationActive) {
                true
            } else {
                prefs.edit().remove(KEY_TEST_TARGET).apply()
                false
            }
        }

        private fun clearTestState(context: Context) {
            context.getSharedPreferences(TEST_PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_TEST_TARGET).apply()
        }
    }
}
