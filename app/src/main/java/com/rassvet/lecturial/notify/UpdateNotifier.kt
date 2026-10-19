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
import com.rassvet.lecturial.BuildConfig
import com.rassvet.lecturial.LecturialApplication
import com.rassvet.lecturial.MainActivity
import com.rassvet.lecturial.R
import com.rassvet.lecturial.model.AppUpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tells the user about a new GitHub release with a notification, once per version.
 * Checks run on app start and twice a day in the background, at most every 12 hours.
 */
object UpdateNotifier {
    const val ACTION_CHECK = "com.rassvet.lecturial.CHECK_UPDATE"
    private const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 27
    private const val PREFS = "update_notifier"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_NOTIFIED_VERSION = "notified_version"
    private const val MIN_INTERVAL = 12 * 60 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** Background check about twice a day; inexact, so it costs no exact-alarm permission. */
    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.setInexactRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR,
            AlarmManager.INTERVAL_HALF_DAY,
            checkIntent(context),
        )
    }

    fun check(context: Context, force: Boolean = false, onComplete: (() -> Unit)? = null) {
        val app = context.applicationContext as? LecturialApplication ?: run {
            onComplete?.invoke()
            return
        }
        scope.launch {
            try {
                mutex.withLock { runCatching { checkNow(app, force) } }
            } finally {
                onComplete?.invoke()
            }
        }
    }

    private suspend fun checkNow(app: LecturialApplication, force: Boolean) {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_CHECK, 0L) < MIN_INTERVAL) return
        val result = app.updateChecker.check(BuildConfig.VERSION_NAME)
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        val update = result as? AppUpdateState.Available ?: return
        if (prefs.getString(KEY_NOTIFIED_VERSION, null) == update.version) return
        if (notify(app, update)) prefs.edit().putString(KEY_NOTIFIED_VERSION, update.version).apply()
    }

    private fun notify(context: Context, update: AppUpdateState.Available): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return false
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            MainActivity.openUpdatesIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lecturial)
            .setContentTitle("Доступно обновление ${update.version}")
            .setContentText("Нажмите, чтобы посмотреть, что нового, и установить")
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        return runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }.isSuccess
    }

    private fun checkIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        NOTIFICATION_ID,
        Intent(context, UpdateCheckReceiver::class.java).setAction(ACTION_CHECK).setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Обновления приложения", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Сообщает о выходе новой версии Lecturial 2"
            },
        )
    }
}

class UpdateCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UpdateNotifier.ACTION_CHECK) return
        val pendingResult = goAsync()
        UpdateNotifier.check(context) { pendingResult.finish() }
    }
}
