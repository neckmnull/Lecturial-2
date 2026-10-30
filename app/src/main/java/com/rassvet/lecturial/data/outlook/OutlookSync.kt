package com.rassvet.lecturial.data.outlook

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.rassvet.lecturial.BuildConfig
import com.rassvet.lecturial.LecturialApplication
import com.rassvet.lecturial.data.SyncedEntriesFile
import com.rassvet.lecturial.data.calendarEntries
import com.rassvet.lecturial.data.syncSummary
import com.rassvet.lecturial.model.CalendarSyncSettings
import com.rassvet.lecturial.model.planSync
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

/** What the Outlook settings screen shows. */
data class OutlookState(
    /** False in a build without a Microsoft client ID: sign-in can't work there. */
    val available: Boolean,
    val account: String? = null,
    val settings: CalendarSyncSettings = CalendarSyncSettings(),
    val signingIn: Boolean = false,
    val syncing: Boolean = false,
    val lastSyncMillis: Long? = null,
    val lastSummary: String? = null,
    val error: String? = null,
) {
    val signedIn: Boolean get() = account != null
}

/**
 * Keeps the "Lecturial 2" calendar in the user's Outlook in step with the schedule and plans:
 * after every schedule refresh or plan change it creates, updates and removes events so
 * the calendar matches. Past events are left as they were.
 */
class OutlookSync(private val app: LecturialApplication) {
    private val http = OkHttpClient()
    private val auth = MicrosoftAuth(BuildConfig.MS_CLIENT_ID, http)
    private val vault = TokenVault(app)
    private val prefs = app.getSharedPreferences("outlook_sync", Context.MODE_PRIVATE)
    private val synced = SyncedEntriesFile(File(app.filesDir, "outlook_synced.json"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var pending: Job? = null
    private val graph = GraphCalendar(http, ::accessToken)

    private val _state = MutableStateFlow(
        OutlookState(
            available = BuildConfig.MS_CLIENT_ID.isNotBlank(),
            account = prefs.getString(KEY_ACCOUNT, null)?.takeIf { vault.load() != null },
            settings = CalendarSyncSettings(
                lessons = prefs.getBoolean(KEY_LESSONS, true),
                plans = prefs.getBoolean(KEY_PLANS, true),
                reminderMinutes = prefs.getInt(KEY_REMINDER, -1).takeIf { it >= 0 },
            ),
            lastSyncMillis = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 },
            lastSummary = prefs.getString(KEY_SUMMARY, null),
        ),
    )
    val state: StateFlow<OutlookState> = _state.asStateFlow()

    /** Opens the Microsoft sign-in page; the browser comes back to [completeSignIn]. */
    fun startSignIn(context: Context) {
        if (!_state.value.available) return
        val verifier = Pkce.verifier()
        val stateToken = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_VERIFIER, verifier).putString(KEY_STATE, stateToken).apply()
        _state.update { it.copy(signingIn = true, error = null) }
        val url = auth.authorizeUrl(MicrosoftAuth.redirectUri(app), stateToken, Pkce.challenge(verifier))
        runCatching { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url)) }
            .onFailure { failure ->
                _state.update { it.copy(signingIn = false, error = "Не удалось открыть браузер для входа") }
                Log.e(TAG, "Unable to open the sign-in page", failure)
            }
    }

    /** The browser's redirect back: trades the code for tokens and runs the first sync. */
    fun completeSignIn(redirect: Uri) {
        scope.launch {
            try {
                redirect.getQueryParameter("error")?.let { error ->
                    throw MicrosoftAuthException(error, redirect.getQueryParameter("error_description").orEmpty())
                }
                val code = redirect.getQueryParameter("code") ?: throw IllegalStateException("Microsoft не вернул код входа")
                check(redirect.getQueryParameter("state") == prefs.getString(KEY_STATE, null)) { "Вход не совпал с запросом, попробуйте ещё раз" }
                val verifier = prefs.getString(KEY_VERIFIER, null) ?: throw IllegalStateException("Вход устарел, попробуйте ещё раз")
                vault.save(auth.exchangeCode(code, MicrosoftAuth.redirectUri(app), verifier))
                val account = graph.account()
                val label = account.email.ifBlank { account.name }
                prefs.edit().remove(KEY_VERIFIER).remove(KEY_STATE).putString(KEY_ACCOUNT, label).remove(KEY_CALENDAR).apply()
                // A new account starts with an empty calendar of its own.
                synced.save(emptyList())
                _state.update { it.copy(account = label, signingIn = false, error = null) }
                syncNow()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                Log.e(TAG, "Sign-in failed", failure)
                _state.update { it.copy(signingIn = false, error = failure.message ?: "Не удалось войти") }
            }
        }
    }

    /** Forgets the account here; the events already in Outlook stay there. */
    fun signOut() {
        pending?.cancel()
        vault.clear()
        prefs.edit().remove(KEY_ACCOUNT).remove(KEY_CALENDAR).remove(KEY_LAST_SYNC).remove(KEY_SUMMARY).apply()
        synced.delete()
        _state.update { it.copy(account = null, syncing = false, lastSyncMillis = null, lastSummary = null, error = null) }
    }

    fun updateSettings(settings: CalendarSyncSettings) {
        prefs.edit()
            .putBoolean(KEY_LESSONS, settings.lessons)
            .putBoolean(KEY_PLANS, settings.plans)
            .putInt(KEY_REMINDER, settings.reminderMinutes ?: -1)
            .apply()
        _state.update { it.copy(settings = settings) }
        requestSync()
    }

    /** A sync a moment from now; bursts (a refresh touching several weeks) become one. */
    fun requestSync() {
        if (!_state.value.signedIn) return
        pending?.cancel()
        pending = scope.launch {
            delay(DEBOUNCE_MILLIS)
            syncNow()
        }
    }

    suspend fun syncNow() = mutex.withLock {
        if (!_state.value.signedIn) return@withLock
        _state.update { it.copy(syncing = true, error = null) }
        try {
            val settings = _state.value.settings
            val today = LocalDate.now()
            val windowStart = today.minusDays(PAST_DAYS)
            val windowEnd = today.plusDays(FUTURE_DAYS)
            val entries = app.calendarEntries(settings, windowStart, windowEnd)
            val calendar = prefs.getString(KEY_CALENDAR, null) ?: graph.calendarId(CALENDAR_NAME).also {
                prefs.edit().putString(KEY_CALENDAR, it).apply()
            }
            val before = synced.load()
            val plan = planSync(entries, before, windowStart)
            val result = graph.apply(calendar, plan, before)
            synced.save(result.synced)
            val summary = syncSummary(
                inCalendar = result.synced.count { !it.date.isBefore(windowStart) },
                created = result.created,
                updated = result.updated,
                deleted = result.deleted,
                failed = result.failed,
            )
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_SYNC, now).putString(KEY_SUMMARY, summary).apply()
            _state.update { it.copy(syncing = false, lastSyncMillis = now, lastSummary = summary) }
        } catch (cancelled: CancellationException) {
            _state.update { it.copy(syncing = false) }
            throw cancelled
        } catch (failure: Throwable) {
            Log.e(TAG, "Outlook sync failed", failure)
            // The app's calendar may have been deleted in Outlook: find or create it next time.
            if (failure.message?.contains("not found", ignoreCase = true) == true) prefs.edit().remove(KEY_CALENDAR).apply()
            _state.update { it.copy(syncing = false, error = failure.message ?: "Синхронизация не удалась") }
        }
    }

    /** A sync started from the settings screen. */
    fun syncInBackground() {
        scope.launch { syncNow() }
    }

    private suspend fun accessToken(forceRefresh: Boolean): String {
        val tokens = vault.load() ?: throw IllegalStateException("Войдите в аккаунт Microsoft")
        if (!forceRefresh && tokens.expiresAtMillis > System.currentTimeMillis()) return tokens.accessToken
        val renewed = try {
            auth.refresh(tokens.refreshToken)
        } catch (rejected: MicrosoftAuthException) {
            // The account revoked access or the sign-in expired: ask to sign in again.
            vault.clear()
            prefs.edit().remove(KEY_ACCOUNT).apply()
            _state.update { it.copy(account = null) }
            throw IllegalStateException("Вход в Microsoft истёк. Войдите заново", rejected)
        }
        vault.save(renewed)
        return renewed.accessToken
    }

    private companion object {
        const val TAG = "OutlookSync"
        const val CALENDAR_NAME = "Lecturial 2"
        const val DEBOUNCE_MILLIS = 3_000L
        /** A week back: lessons moved or cancelled after the fact are corrected too. */
        const val PAST_DAYS = 7L
        const val FUTURE_DAYS = 120L
        const val KEY_ACCOUNT = "account"
        const val KEY_CALENDAR = "calendar_id"
        const val KEY_LESSONS = "lessons"
        const val KEY_PLANS = "plans"
        const val KEY_REMINDER = "reminder_minutes"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_SUMMARY = "summary"
        const val KEY_VERIFIER = "pkce_verifier"
        const val KEY_STATE = "pkce_state"
    }
}
