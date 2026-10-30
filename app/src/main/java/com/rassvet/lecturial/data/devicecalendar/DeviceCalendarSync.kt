package com.rassvet.lecturial.data.devicecalendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.rassvet.lecturial.LecturialApplication
import com.rassvet.lecturial.data.SyncedEntriesFile
import com.rassvet.lecturial.data.calendarEntries
import com.rassvet.lecturial.data.syncSummary
import com.rassvet.lecturial.model.CalendarSyncSettings
import com.rassvet.lecturial.model.planSync
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the Google Calendar settings screen shows. */
data class DeviceCalendarState(
    val hasPermission: Boolean,
    val calendars: List<DeviceCalendarInfo> = emptyList(),
    val selectedId: Long? = null,
    val settings: CalendarSyncSettings = CalendarSyncSettings(),
    val syncing: Boolean = false,
    val lastSyncMillis: Long? = null,
    val lastSummary: String? = null,
    val error: String? = null,
) {
    val selected: DeviceCalendarInfo? get() = calendars.firstOrNull { it.id == selectedId }
    val active: Boolean get() = hasPermission && selectedId != null
}

/**
 * Keeps a calendar on the phone, usually one of the Google account, in step with the schedule
 * and plans. The phone's Google account sync takes the events on to Google Calendar, so no
 * Google sign-in or API access is needed. Past events are left as they were.
 */
class DeviceCalendarSync(private val app: LecturialApplication) {
    private val calendar = DeviceCalendar(app.contentResolver)
    private val prefs = app.getSharedPreferences("device_calendar_sync", Context.MODE_PRIVATE)
    private val synced = SyncedEntriesFile(File(app.filesDir, "device_calendar_synced.json"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var pending: Job? = null

    private val _state = MutableStateFlow(
        DeviceCalendarState(
            hasPermission = hasPermission(),
            selectedId = prefs.getLong(KEY_CALENDAR, -1L).takeIf { it >= 0 },
            settings = CalendarSyncSettings(
                lessons = prefs.getBoolean(KEY_LESSONS, true),
                plans = prefs.getBoolean(KEY_PLANS, true),
                reminderMinutes = prefs.getInt(KEY_REMINDER, -1).takeIf { it >= 0 },
            ),
            lastSyncMillis = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 },
            lastSummary = prefs.getString(KEY_SUMMARY, null),
        ),
    )
    val state: StateFlow<DeviceCalendarState> = _state.asStateFlow()

    /** Checks the permission again and reloads the calendars, e.g. after a new Google calendar synced in. */
    fun refresh() {
        scope.launch {
            val permitted = hasPermission()
            val calendars = if (permitted) runCatching { calendar.writableCalendars() }.getOrDefault(emptyList()) else emptyList()
            _state.update { it.copy(hasPermission = permitted, calendars = calendars) }
        }
    }

    /** Sends everything to [chosen]; what was sent to another calendar before is taken out of it. */
    fun select(chosen: DeviceCalendarInfo) {
        if (chosen.id == _state.value.selectedId) return
        pending?.cancel()
        scope.launch {
            mutex.withLock {
                calendar.deleteAll(synced.load())
                synced.save(emptyList())
                prefs.edit { putLong(KEY_CALENDAR, chosen.id) }
                _state.update { it.copy(selectedId = chosen.id, error = null) }
            }
            syncNow()
        }
    }

    /** Stops syncing and removes the events it added, past ones included. */
    fun disconnect() {
        pending?.cancel()
        scope.launch {
            mutex.withLock {
                val left = runCatching { calendar.deleteAll(synced.load()) }.getOrDefault(0)
                synced.delete()
                prefs.edit {
                    remove(KEY_CALENDAR)
                    remove(KEY_LAST_SYNC)
                    remove(KEY_SUMMARY)
                }
                _state.update {
                    it.copy(
                        selectedId = null,
                        lastSyncMillis = null,
                        lastSummary = null,
                        error = if (left > 0) "Не удалось убрать событий: $left — удалите их в календаре" else null,
                    )
                }
            }
        }
    }

    fun updateSettings(settings: CalendarSyncSettings) {
        prefs.edit {
            putBoolean(KEY_LESSONS, settings.lessons)
            putBoolean(KEY_PLANS, settings.plans)
            putInt(KEY_REMINDER, settings.reminderMinutes ?: -1)
        }
        _state.update { it.copy(settings = settings) }
        requestSync()
    }

    /** A sync a moment from now; bursts (a refresh touching several weeks) become one. */
    fun requestSync() {
        if (!_state.value.active) return
        pending?.cancel()
        pending = scope.launch {
            delay(DEBOUNCE_MILLIS)
            syncNow()
        }
    }

    /** A sync started from the settings screen. */
    fun syncInBackground() {
        scope.launch { syncNow() }
    }

    suspend fun syncNow() = mutex.withLock {
        // The permission can be taken away in the system settings at any time.
        if (!hasPermission()) {
            _state.update { it.copy(hasPermission = false) }
            return@withLock
        }
        val calendarId = _state.value.selectedId ?: return@withLock
        _state.update { it.copy(syncing = true, error = null) }
        try {
            val today = LocalDate.now()
            val windowStart = today.minusDays(PAST_DAYS)
            val entries = app.calendarEntries(_state.value.settings, windowStart, today.plusDays(FUTURE_DAYS))
            val before = calendar.stillPresent(synced.load())
            val result = calendar.apply(calendarId, planSync(entries, before, windowStart), before)
            synced.save(result.synced)
            val summary = syncSummary(
                inCalendar = result.synced.count { !it.date.isBefore(windowStart) },
                created = result.created,
                updated = result.updated,
                deleted = result.deleted,
                failed = result.failed,
            )
            val now = System.currentTimeMillis()
            prefs.edit {
                putLong(KEY_LAST_SYNC, now)
                putString(KEY_SUMMARY, summary)
            }
            _state.update { it.copy(syncing = false, lastSyncMillis = now, lastSummary = summary) }
        } catch (cancelled: CancellationException) {
            _state.update { it.copy(syncing = false) }
            throw cancelled
        } catch (failure: Throwable) {
            Log.e(TAG, "Calendar sync failed", failure)
            _state.update { it.copy(syncing = false, error = failure.message ?: "Синхронизация не удалась") }
        }
    }

    private fun hasPermission(): Boolean = CALENDAR_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        val CALENDAR_PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

        private const val TAG = "DeviceCalendarSync"
        private const val DEBOUNCE_MILLIS = 3_000L
        /** A week back: lessons moved or cancelled after the fact are corrected too. */
        private const val PAST_DAYS = 7L
        private const val FUTURE_DAYS = 120L
        private const val KEY_CALENDAR = "calendar_id"
        private const val KEY_LESSONS = "lessons"
        private const val KEY_PLANS = "plans"
        private const val KEY_REMINDER = "reminder_minutes"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_SUMMARY = "summary"
    }
}
