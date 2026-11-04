package com.rassvet.lecturial.data.lks

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.rassvet.lecturial.LecturialApplication
import com.rassvet.lecturial.MainActivity
import com.rassvet.lecturial.R
import com.rassvet.lecturial.data.local.SecretBox
import com.rassvet.lecturial.model.LksDiscipline
import com.rassvet.lecturial.model.LksMark
import com.rassvet.lecturial.model.LksNews
import com.rassvet.lecturial.model.LksNotification
import com.rassvet.lecturial.model.LksSemester
import com.rassvet.lecturial.model.LksSnapshot
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** What the Study tab shows. */
data class LksState(
    val connected: Boolean = false,
    val snapshot: LksSnapshot? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
)

/**
 * The personal account in the app: keeps the sign-in's cookies encrypted, fetches the journal,
 * the trajectory, notifications and news, caches them for offline use and tells about new
 * notifications and grades. Refreshes when the app opens and every few hours in the background.
 */
class LksRepository(private val app: LecturialApplication) {
    private val prefs = app.getSharedPreferences("lks", Context.MODE_PRIVATE)
    private val secrets = SecretBox("lecturial_lks_session")
    private val cacheFile = File(app.filesDir, "lks/snapshot.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(LksState(connected = cookies() != null, snapshot = loadCache()))
    val state: StateFlow<LksState> = _state.asStateFlow()

    /** Called by the sign-in screen with the WebView's cookies for the site. */
    fun connect(cookieHeader: String) {
        prefs.edit { putString(KEY_COOKIES, secrets.encrypt(cookieHeader)) }
        // The WebView keeps its cookies in a plain database; the encrypted copy above is the only one needed.
        forgetWebSession()
        _state.update { it.copy(connected = true, error = null) }
        schedule(app)
        refresh(force = true)
    }

    /** Forgets the session here and in the WebView; the cache goes too. */
    fun disconnect() {
        prefs.edit { clear() }
        cacheFile.delete()
        forgetWebSession()
        cancel(app)
        _state.value = LksState()
    }

    private fun forgetWebSession() {
        runCatching {
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }
            WebStorage.getInstance().deleteAllData()
        }
    }

    /** A refresh unless the data is fresher than [maxAgeMillis]. */
    fun refreshIfStale(maxAgeMillis: Long = STALE_MILLIS) {
        val loaded = _state.value.snapshot?.loadedAtMillis ?: 0L
        if (System.currentTimeMillis() - loaded >= maxAgeMillis) refresh(force = false)
    }

    fun refresh(force: Boolean = true) {
        scope.launch { refreshNow(notify = !force) }
    }

    /** Loads everything; with [notify], new notifications and grades become system notifications. */
    suspend fun refreshNow(notify: Boolean) = mutex.withLock {
        val header = cookies() ?: return@withLock
        _state.update { it.copy(refreshing = true, error = null) }
        try {
            val client = LksClient(header)
            val previous = _state.value.snapshot
            val fresh = client.snapshot()
            prefs.edit { putString(KEY_COOKIES, secrets.encrypt(client.cookieHeader)) }
            saveCache(fresh)
            _state.update { it.copy(snapshot = fresh, refreshing = false, connected = true) }
            if (notify && previous != null) announce(previous, fresh)
        } catch (cancelled: CancellationException) {
            _state.update { it.copy(refreshing = false) }
            throw cancelled
        } catch (signedOut: LksSignedOutException) {
            prefs.edit { remove(KEY_COOKIES) }
            _state.update { it.copy(refreshing = false, connected = false, error = signedOut.message) }
        } catch (failure: Throwable) {
            Log.w(TAG, "LKS refresh failed", failure)
            _state.update { it.copy(refreshing = false, error = failure.message ?: "Не удалось обновить данные личного кабинета") }
        }
    }

    private fun cookies(): String? = prefs.getString(KEY_COOKIES, null)?.let(secrets::decrypt)?.takeIf { it.isNotBlank() }

    // ------------------------------------------------------------------ notifications

    private fun announce(previous: LksSnapshot, fresh: LksSnapshot) {
        val manager = NotificationManagerCompat.from(app)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel()
        val known = previous.notifications.map { it.id }.toSet()
        fresh.notifications.filter { it.id !in known }.take(MAX_NOTIFICATIONS).forEach { item ->
            notify(manager, item.id.hashCode(), item.title.ifBlank { "Личный кабинет" }, item.text)
        }
        val before = previous.marks.associateBy { it.id }
        val graded = fresh.marks.filter { it.grade != null && before[it.id]?.grade != it.grade }
        if (graded.isNotEmpty()) {
            val title = if (graded.size == 1) "Новая оценка: ${graded.single().grade}" else "Новые оценки: ${graded.size}"
            val text = graded.take(5).joinToString("\n") { "${it.grade} · ${it.discipline}" }
            notify(manager, GRADES_NOTIFICATION_ID, title, text)
        }
    }

    private fun notify(manager: NotificationManagerCompat, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            app,
            id,
            MainActivity.openStudyIntent(app),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lecturial)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            // Grades stay off the lock screen; it only says that something new arrived.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(app, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_lecturial)
                    .setContentTitle("Личный кабинет")
                    .setContentText("Есть новые данные")
                    .build(),
            )
            .build()
        runCatching { manager.notify(id, notification) }
    }

    private fun ensureChannel() {
        app.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Личный кабинет", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Новые уведомления и оценки из личного кабинета студента"
            },
        )
    }

    // ------------------------------------------------------------------ cache

    /** Sealed with the session's key: grades and attendance are no easier to read off the phone than the session. */
    private fun saveCache(snapshot: LksSnapshot) {
        cacheFile.parentFile?.mkdirs()
        val partial = File(cacheFile.path + ".part")
        partial.writeText(secrets.encrypt(snapshot.toJson().toString()))
        partial.renameTo(cacheFile)
    }

    private fun loadCache(): LksSnapshot? = runCatching {
        val stored = cacheFile.readText()
        // Up to 1.13.1 the cache was plain JSON: read it once more and seal it.
        val legacy = stored.startsWith("{")
        val snapshot = JSONObject(if (legacy) stored else secrets.decrypt(stored) ?: return null).toSnapshot()
        if (legacy) saveCache(snapshot)
        snapshot
    }.getOrNull()

    companion object {
        private const val TAG = "LksRepository"
        private const val KEY_COOKIES = "cookies"
        private const val CHANNEL_ID = "lks"
        private const val GRADES_NOTIFICATION_ID = 0x4C4B53
        private const val MAX_NOTIFICATIONS = 3
        private const val STALE_MILLIS = 30 * 60 * 1000L
        private const val INTERVAL_MILLIS = 3 * 60 * 60 * 1000L

        /** Every three hours, at a time that suits the system, while the account is connected. */
        fun schedule(context: Context) {
            context.getSystemService(AlarmManager::class.java)?.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + INTERVAL_MILLIS,
                INTERVAL_MILLIS,
                refreshIntent(context),
            )
        }

        fun cancel(context: Context) {
            context.getSystemService(AlarmManager::class.java)?.cancel(refreshIntent(context))
        }

        private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, LksRefreshReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** The background refresh: new notifications and grades arrive even with the app closed. */
class LksRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as LecturialApplication
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.lks.refreshNow(notify = true)
            } finally {
                pending.finish()
            }
        }
    }
}

// ---------------------------------------------------------------------- JSON for the cache

private fun LksSnapshot.toJson(): JSONObject = JSONObject()
    .put("loadedAt", loadedAtMillis)
    .put("unread", unreadNotifications)
    .put("marks", JSONArray(marks.map { mark ->
        JSONObject().put("id", mark.id).put("discipline", mark.discipline).put("date", mark.date.toString())
            .put("start", mark.startTime).put("type", mark.type).put("grade", mark.grade)
            .put("absent", mark.absent).put("topic", mark.topic).put("homework", mark.homework)
    }))
    .put("semesters", JSONArray(semesters.map { semester ->
        JSONObject().put("key", semester.key).put("title", semester.title).put("disciplines", JSONArray(semester.disciplines.map { discipline ->
            JSONObject().put("name", discipline.name).put("forms", JSONArray(discipline.controlForms))
                .put("credits", discipline.credits).put("hours", discipline.hours)
                .put("results", JSONArray(discipline.results)).put("passed", discipline.passed)
        }))
    }))
    .put("notifications", JSONArray(notifications.map { JSONObject().put("id", it.id).put("title", it.title).put("text", it.text).put("time", it.time) }))
    .put("news", JSONArray(news.map { JSONObject().put("id", it.id).put("title", it.title).put("date", it.date) }))

private fun JSONObject.toSnapshot(): LksSnapshot = LksSnapshot(
    loadedAtMillis = optLong("loadedAt"),
    unreadNotifications = optInt("unread"),
    marks = optJSONArray("marks").objects().map { item ->
        LksMark(
            id = item.getString("id"),
            discipline = item.optString("discipline"),
            date = LocalDate.parse(item.getString("date")),
            startTime = item.textOrNull("start"),
            type = item.optString("type"),
            grade = if (item.isNull("grade")) null else item.optInt("grade"),
            absent = if (item.isNull("absent")) null else item.optBoolean("absent"),
            topic = item.textOrNull("topic"),
            homework = item.textOrNull("homework"),
        )
    },
    semesters = optJSONArray("semesters").objects().map { semester ->
        LksSemester(
            key = semester.optString("key"),
            title = semester.optString("title"),
            disciplines = semester.optJSONArray("disciplines").objects().map { discipline ->
                LksDiscipline(
                    name = discipline.optString("name"),
                    controlForms = discipline.optJSONArray("forms").strings(),
                    credits = if (discipline.isNull("credits")) null else discipline.optDouble("credits"),
                    hours = if (discipline.isNull("hours")) null else discipline.optInt("hours"),
                    results = discipline.optJSONArray("results").strings(),
                    passed = if (discipline.isNull("passed")) null else discipline.optBoolean("passed"),
                )
            },
        )
    },
    notifications = optJSONArray("notifications").objects().map {
        LksNotification(it.optString("id"), it.optString("title"), it.optString("text"), it.optString("time"))
    },
    news = optJSONArray("news").objects().map { LksNews(it.optString("id"), it.optString("title"), it.optString("date")) },
)

private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }

private fun JSONObject.textOrNull(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
