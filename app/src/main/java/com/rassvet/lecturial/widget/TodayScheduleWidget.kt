package com.rassvet.lecturial.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.rassvet.lecturial.LecturialApplication
import com.rassvet.lecturial.MainActivity
import com.rassvet.lecturial.R
import com.rassvet.lecturial.data.local.ScheduleEventEntity
import com.rassvet.lecturial.data.local.StudyPlanEntity
import com.rassvet.lecturial.model.parseEventMinutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class TodayScheduleWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            // A lesson started or ended (or the day changed): refresh the current-lesson mark.
            val pendingResult = goAsync()
            widgetScope.launch {
                try {
                    updateAllNow(context)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        // The provider's initial layout is already a stable empty state. Preserve the
        // current content while refreshing so an interrupted process cannot replace it
        // with a transient loading screen.
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                updateWidgets(context, manager, appWidgetIds)
            } catch (error: Throwable) {
                Log.e(TAG, "Widget update broadcast failed", error)
                publishErrorWidgets(context, manager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
            } catch (error: Throwable) {
                Log.e(TAG, "Widget resize update failed for id=$appWidgetId", error)
                publishErrorWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val updateAllLock = Any()
        private var pendingUpdateAll: Job? = null

        fun updateAll(context: Context) {
            // Settings sliders persist every step; coalesce bursts so each step doesn't
            // re-render the widget bitmaps.
            synchronized(updateAllLock) {
                pendingUpdateAll?.cancel()
                pendingUpdateAll = widgetScope.launch {
                    delay(UPDATE_ALL_DEBOUNCE_MILLIS)
                    updateAllNow(context)
                }
            }
        }

        private suspend fun updateAllNow(context: Context) {
            run {
                runCatching {
                    val manager = AppWidgetManager.getInstance(context)
                    val component = ComponentName(context, TodayScheduleWidget::class.java)
                    val ids = manager.getAppWidgetIds(component)
                    updateWidgets(context, manager, ids)
                }.onFailure { error ->
                    Log.e(TAG, "Unable to update widgets", error)
                    runCatching {
                        val manager = AppWidgetManager.getInstance(context)
                        val component = ComponentName(context, TodayScheduleWidget::class.java)
                        publishErrorWidgets(context, manager, manager.getAppWidgetIds(component))
                    }.onFailure { fallbackError ->
                        Log.e(TAG, "Unable to publish updateAll error state", fallbackError)
                    }
                }
            }
        }

        private suspend fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
        ) {
            if (ids.isEmpty()) return
            val app = context.applicationContext as LecturialApplication
            val appearance = runCatching { loadWidgetAppearance(app) }
                .onFailure { error -> Log.e(TAG, "Unable to load widget appearance; using defaults", error) }
                .getOrElse(::defaultWidgetAppearance)
            val outerPaddingDp = appearance.look.outerPaddingDp
            val lessons = todayLessons(app)
            scheduleNextRefresh(context, lessons)
            val activePlans = app.database.studyPlanDao().all()
                .filterNot(StudyPlanEntity::isCompleted)
            val today = LocalDate.now()
            val todayPlanCount = activePlans.count { it.widgetOccursOn(today) }
            // The row of the current (or next) lesson, in the factory's time order, so the
            // list opens on it instead of on lessons that are already over.
            val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
            val currentRow = (
                lessons.map { "$today-${it.startTime}" to (parseEventMinutes(it.endTime) ?: Int.MAX_VALUE) } +
                    activePlans.filter { it.widgetOccursOn(today) }.map { "$today-${it.dueTime ?: "23:59"}" to Int.MAX_VALUE }
                )
                .sortedBy { it.first }
                .indexOfFirst { (_, end) -> end > nowMinutes }
            val nearestPlan = activePlans.minWithOrNull(
                compareBy<StudyPlanEntity> { it.widgetOccurrenceDate(today) ?: LocalDate.MAX }
                    .thenBy { it.dueTime ?: "23:59" }
                    .thenByDescending(StudyPlanEntity::createdAtMillis),
            )
            ids.forEach { id ->
                val nearestPlanLabel = nearestPlan?.let { plan ->
                    val dateLabel = plan.widgetOccurrenceDate(today)?.let { dueDateLabel(it, today) }
                    listOfNotNull(dateLabel, plan.title).joinToString(" · ")
                }
                val commonViews = {
                    widgetViews(
                        context = context,
                        appWidgetId = id,
                        outerPaddingDp = outerPaddingDp,
                        lessonCount = lessons.size,
                        activePlanCount = todayPlanCount,
                        nearestPlan = nearestPlanLabel,
                        nearestPlanId = nearestPlan?.id,
                        appearance = appearance,
                    ).apply {
                        if (currentRow > 0) setScrollPosition(R.id.widget_list, currentRow)
                    }
                }
                runCatching {
                    val views = commonViews().apply {
                        setImageViewBitmap(
                            R.id.widget_wallpaper,
                            renderWidgetWallpaper(
                                context = context,
                                appearance = appearance,
                                size = widgetBitmapSize(context, manager, id),
                            ),
                        )
                        // The rendered wallpaper replaces the XML background, which would
                        // otherwise show through a translucent widget; the clear shape keeps
                        // the rounded outline the widget is clipped to.
                        setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_outline)
                    }
                    manager.updateAppWidget(id, views)
                }.onFailure { error ->
                    // A launcher can reject large or unsupported RemoteViews. Keep the
                    // schedule usable with its XML background instead of leaving a
                    // permanently broken widget instance.
                    Log.e(TAG, "Themed widget update failed for id=$id; using fallback", error)
                    runCatching { manager.updateAppWidget(id, commonViews()) }
                        .onFailure { fallbackError ->
                            Log.e(TAG, "Fallback widget update failed for id=$id", fallbackError)
                        }
                }
            }
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }

        private fun widgetViews(
            context: Context,
            appWidgetId: Int,
            outerPaddingDp: Int,
            lessonCount: Int,
            activePlanCount: Int,
            nearestPlan: String?,
            nearestPlanId: String? = null,
            wallpaper: Bitmap? = null,
            appearance: WidgetAppearance? = null,
            emptyMessageRes: Int = R.string.widget_no_lessons,
        ): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_today_schedule).apply {
                wallpaper?.let { setImageViewBitmap(R.id.widget_wallpaper, it) }
                val openApp = Intent(context, MainActivity::class.java)
                val openPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    openApp,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val serviceIntent = Intent(context, ScheduleWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                setRemoteAdapter(R.id.widget_list, serviceIntent)
                // Single top: a running app gets the tapped lesson in onNewIntent. With clear top
                // alone Android finished and restarted it, so the tap seemed to just open the app.
                val listTemplate = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                setPendingIntentTemplate(R.id.widget_list, listTemplate)
                setEmptyView(R.id.widget_list, R.id.widget_empty)
                setOnClickPendingIntent(R.id.widget_empty, openPendingIntent)
                setOnClickPendingIntent(R.id.widget_header, openPendingIntent)
                setTextViewText(
                    R.id.widget_date,
                    LocalDate.now().format(HeaderDateFormatter),
                )
                setTextViewText(R.id.widget_lesson_count, pluralCount(lessonCount, "пара", "пары", "пар"))
                setTextViewText(R.id.widget_plan_count, pluralCount(activePlanCount, "план", "плана", "планов"))
                setTextViewText(R.id.widget_nearest_plan_text, nearestPlan.orEmpty())
                setTextViewText(R.id.widget_empty, context.getString(emptyMessageRes))
                appearance?.let { colors ->
                    setTextColor(R.id.widget_title, colors.headerTitleColor)
                    setTextColor(R.id.widget_date, colors.headerTextColor)
                    setTextColor(R.id.widget_nearest_plan_text, colors.headerSecondaryColor)
                    setTextColor(R.id.widget_empty, colors.headerSecondaryColor)
                    setInt(R.id.widget_plan_dot, "setColorFilter", colors.palette.secondary)
                    setInt(R.id.widget_divider, "setBackgroundColor", colors.outlineColor)
                    setInt(R.id.widget_lesson_count_background, "setColorFilter", colors.primaryChipColor)
                    setTextColor(R.id.widget_lesson_count, colors.primaryChipTextColor)
                    setInt(R.id.widget_plan_count_background, "setColorFilter", colors.secondaryChipColor)
                    setTextColor(R.id.widget_plan_count, colors.secondaryChipTextColor)
                }
                setViewVisibility(
                    R.id.widget_nearest_plan,
                    if (nearestPlan.isNullOrBlank()) View.GONE else View.VISIBLE,
                )
                if (nearestPlanId != null) {
                    setOnClickPendingIntent(
                        R.id.widget_nearest_plan,
                        PendingIntent.getActivity(
                            context,
                            nearestPlanId.hashCode(),
                            MainActivity.openPlanIntent(context, nearestPlanId),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                }
                val paddingPx = context.dpToPx(outerPaddingDp)
                setViewPadding(R.id.widget_header, paddingPx, paddingPx, paddingPx, context.dpToPx(8))
                setViewPadding(R.id.widget_list, paddingPx, context.dpToPx(4), paddingPx, paddingPx)
                setViewPadding(R.id.widget_empty, paddingPx, paddingPx, paddingPx, paddingPx)
            }

        private fun publishErrorWidgets(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
        ) {
            ids.forEach { id ->
                runCatching {
                    manager.updateAppWidget(
                        id,
                        widgetViews(
                            context = context,
                            appWidgetId = id,
                            outerPaddingDp = 9,
                            lessonCount = 0,
                            activePlanCount = 0,
                            nearestPlan = null,
                            emptyMessageRes = R.string.widget_load_error,
                        ),
                    )
                }.onFailure { error ->
                    Log.e(TAG, "Unable to publish widget error state for id=$id", error)
                }
            }
            runCatching { manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list) }
                .onFailure { error -> Log.e(TAG, "Unable to refresh widget error rows", error) }
        }

        /**
         * Wakes the widget at the next lesson start or end today, or just after midnight,
         * so the current lesson is marked and finished ones fade without opening the app.
         * Not exact and not waking the phone: a widget nobody looks at can wait.
         */
        private fun scheduleNextRefresh(context: Context, lessons: List<ScheduleEventEntity>) {
            val now = LocalDateTime.now()
            val nowMinutes = now.hour * 60 + now.minute
            val nextMinutes = lessons
                .flatMap { listOfNotNull(parseEventMinutes(it.startTime), parseEventMinutes(it.endTime)) }
                .filter { it > nowMinutes }
                .minOrNull()
            val at = if (nextMinutes != null) {
                now.toLocalDate().atStartOfDay().plusMinutes(nextMinutes.toLong())
            } else {
                now.toLocalDate().plusDays(1).atStartOfDay().plusMinutes(1)
            }
            val refresh = PendingIntent.getBroadcast(
                context,
                REFRESH_REQUEST_CODE,
                Intent(context, TodayScheduleWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            runCatching {
                context.getSystemService(AlarmManager::class.java).setWindow(
                    AlarmManager.RTC,
                    at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    REFRESH_WINDOW_MILLIS,
                    refresh,
                )
            }.onFailure { error -> Log.e(TAG, "Unable to schedule the next widget refresh", error) }
        }

        private const val TAG = "TodayScheduleWidget"
        private const val UPDATE_ALL_DEBOUNCE_MILLIS = 300L
        private const val ACTION_REFRESH = "com.rassvet.lecturial.widget.REFRESH"
        private const val REFRESH_REQUEST_CODE = 7301
        private const val REFRESH_WINDOW_MILLIS = 60_000L
    }
}

internal fun Context.dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()

/** Today's cached lessons of the chosen schedule, with the language-teacher filter applied. */
internal suspend fun todayLessons(app: LecturialApplication): List<ScheduleEventEntity> {
    val group = app.preferences.selectedGroup.first() ?: return emptyList()
    val teacher = app.preferences.languageTeacher(group).first()
    return app.database.scheduleDao().eventsForDay(group, LocalDate.now().toString()).filter {
        com.rassvet.lecturial.model.matchesLanguageTeacher(it.discipline, it.teachers.split("\u001F"), teacher)
    }
}

private val HeaderDateFormatter = DateTimeFormatter.ofPattern("d MMMM, EEE", Locale.forLanguageTag("ru"))

private fun pluralCount(value: Int, one: String, few: String, many: String): String {
    val form = when {
        value % 100 in 11..14 -> many
        value % 10 == 1 -> one
        value % 10 in 2..4 -> few
        else -> many
    }
    return "$value $form"
}
