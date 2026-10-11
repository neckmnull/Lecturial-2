package com.rassvet.lecturial.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.rassvet.lecturial.LecturialApplication
import com.rassvet.lecturial.MainActivity
import com.rassvet.lecturial.R
import com.rassvet.lecturial.data.local.ScheduleEventEntity
import com.rassvet.lecturial.model.WidgetStripePalette
import com.rassvet.lecturial.model.WidgetFontStyles
import com.rassvet.lecturial.model.renamedDiscipline
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class TodayScheduleWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            manager.updateAppWidget(id, widgetViews(context, id, 9, 0, 0, null))
        }
        manager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list)
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                updateWidgets(context, manager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAll(context: Context) {
            widgetScope.launch {
                val manager = AppWidgetManager.getInstance(context)
                val component = ComponentName(context, TodayScheduleWidget::class.java)
                updateWidgets(context, manager, manager.getAppWidgetIds(component))
            }
        }

        private suspend fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
        ) {
            if (ids.isEmpty()) return
            val app = context.applicationContext as LecturialApplication
            val outerPaddingDp = runCatching {
                app.preferences.widgetDisplayOptions.first().outerPaddingDp
            }.getOrDefault(9)
            val group = runCatching { app.preferences.selectedGroup.first() }.getOrNull()
            val lessons = if (group == null) {
                emptyList()
            } else {
                val teacher = runCatching { app.preferences.languageTeacher(group).first() }.getOrNull()
                runCatching {
                    app.database.scheduleDao().eventsForDay(group, LocalDate.now().toString())
                }.getOrDefault(emptyList()).filter {
                    com.rassvet.lecturial.model.matchesLanguageTeacher(
                        it.discipline,
                        it.teachers.split("\u001F"),
                        teacher,
                    )
                }
            }
            val activePlanCount = runCatching { app.database.studyPlanDao().activeCount() }.getOrDefault(0)
            val nearestPlan = runCatching { app.database.studyPlanDao().nearestActive() }.getOrNull()
            ids.forEach { id ->
                manager.updateAppWidget(
                    id,
                    widgetViews(
                        context = context,
                        appWidgetId = id,
                        outerPaddingDp = outerPaddingDp,
                        lessonCount = lessons.size,
                        activePlanCount = activePlanCount,
                        nearestPlan = nearestPlan?.let { plan ->
                            val dateLabel = plan.dueDate?.let { dueDateLabel(LocalDate.parse(it)) }
                            listOfNotNull(dateLabel, plan.title).joinToString(" · ")
                        },
                    ),
                )
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
        ): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_today_schedule).apply {
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
                setEmptyView(R.id.widget_list, R.id.widget_empty)
                setOnClickPendingIntent(R.id.widget_empty, openPendingIntent)
                setOnClickPendingIntent(R.id.widget_header, openPendingIntent)
                setTextViewText(
                    R.id.widget_date,
                    LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM, EEE", Locale.forLanguageTag("ru"))),
                )
                setTextViewText(R.id.widget_lesson_count, pluralCount(lessonCount, "пара", "пары", "пар"))
                setTextViewText(R.id.widget_plan_count, pluralCount(activePlanCount, "план", "плана", "планов"))
                setTextViewText(R.id.widget_nearest_plan_text, nearestPlan.orEmpty())
                setViewVisibility(
                    R.id.widget_nearest_plan,
                    if (nearestPlan.isNullOrBlank()) View.GONE else View.VISIBLE,
                )
                val paddingPx = context.dpToPx(outerPaddingDp)
                setViewPadding(R.id.widget_header, paddingPx, paddingPx, paddingPx, context.dpToPx(8))
                setViewPadding(R.id.widget_list, paddingPx, context.dpToPx(4), paddingPx, paddingPx)
                setViewPadding(R.id.widget_empty, paddingPx, paddingPx, paddingPx, paddingPx)
            }
    }
}

class ScheduleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ScheduleWidgetFactory(applicationContext)
}

private class ScheduleWidgetFactory(
    private val context: Context,
) : RemoteViewsService.RemoteViewsFactory {
    private var events: List<ScheduleEventEntity> = emptyList()
    private var lectureStripeColor: Int = WidgetStripePalette.defaultLecture
    private var practiceStripeColor: Int = WidgetStripePalette.defaultPractice
    private var showClassroom: Boolean = true
    private var showTime: Boolean = true
    private var showStripe: Boolean = true
    private var showEndTime: Boolean = true
    private var wrapTitle: Boolean = true
    private var horizontalPaddingDp: Int = 12
    private var verticalPaddingDp: Int = 6
    private var itemSpacingDp: Int = 3
    private var titleTextSizeSp: Int = 12
    private var secondaryTextSizeSp: Int = 9
    private var fontStyleId: String = WidgetFontStyles.defaultId
    private var letterSpacingPercent: Int = 0
    private var titleMaxLines: Int = 2
    private var lessonAliases: Map<String, String> = emptyMap()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        events = runBlocking {
            val app = context as LecturialApplication
            val stripeColors = runCatching { app.preferences.widgetStripeColors.first() }.getOrNull()
            lectureStripeColor = stripeColors?.lecture ?: WidgetStripePalette.defaultLecture
            practiceStripeColor = stripeColors?.practice ?: WidgetStripePalette.defaultPractice
            val displayOptions = runCatching { app.preferences.widgetDisplayOptions.first() }.getOrNull()
            showTime = displayOptions?.showTime ?: true
            showStripe = displayOptions?.showStripe ?: true
            showClassroom = displayOptions?.showClassroom ?: true
            showEndTime = displayOptions?.showEndTime ?: true
            wrapTitle = displayOptions?.wrapTitle ?: true
            horizontalPaddingDp = displayOptions?.horizontalPaddingDp ?: 12
            verticalPaddingDp = displayOptions?.verticalPaddingDp ?: 6
            itemSpacingDp = displayOptions?.itemSpacingDp ?: 3
            titleTextSizeSp = displayOptions?.titleTextSizeSp ?: 12
            secondaryTextSizeSp = displayOptions?.secondaryTextSizeSp ?: 9
            fontStyleId = displayOptions?.fontStyleId ?: WidgetFontStyles.defaultId
            letterSpacingPercent = displayOptions?.letterSpacingPercent ?: 0
            titleMaxLines = displayOptions?.titleMaxLines ?: 2
            lessonAliases = runCatching { app.preferences.lessonAliases.first() }.getOrDefault(emptyMap())
            val group = runCatching { app.preferences.selectedGroup.first() }.getOrNull()
                ?: return@runBlocking emptyList()
            val teacher = app.preferences.languageTeacher(group).first()
            runCatching {
                app.database.scheduleDao().eventsForDay(group, LocalDate.now().toString())
            }.getOrDefault(emptyList()).filter {
                com.rassvet.lecturial.model.matchesLanguageTeacher(it.discipline, it.teachers.split("\u001F"), teacher)
            }
        }
    }

    override fun onDestroy() {
        events = emptyList()
    }

    override fun getCount(): Int = events.size

    override fun getViewAt(position: Int): RemoteViews? {
        val event = events.getOrNull(position) ?: return null
        return RemoteViews(context.packageName, R.layout.widget_schedule_item).apply {
            listOf(
                R.id.widget_item_time,
                R.id.widget_item_title,
                R.id.widget_item_room,
            ).forEach { viewId ->
                setFloat(viewId, "setLetterSpacing", letterSpacingPercent / 100f)
            }
            setTextColor(R.id.widget_item_time, context.getColor(R.color.widget_on_background))
            setTextColor(R.id.widget_item_title, context.getColor(R.color.widget_on_background))
            setTextColor(R.id.widget_item_room, context.getColor(R.color.widget_on_surface_variant))
            val horizontalPaddingPx = context.dpToPx(horizontalPaddingDp)
            val verticalPaddingPx = context.dpToPx(verticalPaddingDp)
            val spacingPx = context.dpToPx(itemSpacingDp)
            setViewPadding(
                R.id.widget_item_root,
                0,
                spacingPx / 2,
                0,
                spacingPx - spacingPx / 2,
            )
            setViewPadding(
                R.id.widget_item_content,
                horizontalPaddingPx,
                verticalPaddingPx,
                horizontalPaddingPx,
                verticalPaddingPx,
            )
            val normalizedType = event.type.lowercase()
            val isPractice = normalizedType.contains("практ")
            setInt(
                R.id.widget_item_stripe,
                "setBackgroundColor",
                if (isPractice) practiceStripeColor else lectureStripeColor,
            )
            setViewVisibility(
                R.id.widget_item_stripe,
                if (showStripe) View.VISIBLE else View.GONE,
            )
            setTextViewText(
                R.id.widget_item_time,
                styleWidgetText(
                    if (showEndTime) "${event.startTime}–${event.endTime}" else event.startTime,
                    fontStyleId,
                ),
            )
            setViewVisibility(R.id.widget_item_time, if (showTime) View.VISIBLE else View.GONE)
            setTextViewTextSize(
                R.id.widget_item_time,
                TypedValue.COMPLEX_UNIT_SP,
                secondaryTextSizeSp.toFloat(),
            )
            setTextViewText(R.id.widget_item_title, styleWidgetText(renamedDiscipline(event.discipline, lessonAliases), fontStyleId))
            setInt(R.id.widget_item_title, "setMaxLines", if (wrapTitle) titleMaxLines else 1)
            setTextViewTextSize(
                R.id.widget_item_title,
                TypedValue.COMPLEX_UNIT_SP,
                titleTextSizeSp.toFloat(),
            )
            val classroom = event.classroom.orEmpty().trim()
            setTextViewText(R.id.widget_item_room, styleWidgetText(classroom, fontStyleId))
            setTextViewTextSize(
                R.id.widget_item_room,
                TypedValue.COMPLEX_UNIT_SP,
                secondaryTextSizeSp.toFloat(),
            )
            setViewVisibility(
                R.id.widget_item_room,
                if (showClassroom && classroom.isNotEmpty()) View.VISIBLE else View.GONE,
            )
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long =
        events.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}

private fun Context.dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()

private fun dueDateLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when {
    date.isBefore(today) -> "Просрочено"
    date == today -> "Сегодня"
    date == today.plusDays(1) -> "Завтра"
    else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru")))
}

private fun pluralCount(value: Int, one: String, few: String, many: String): String {
    val form = when {
        value % 100 in 11..14 -> many
        value % 10 == 1 -> one
        value % 10 in 2..4 -> few
        else -> many
    }
    return "$value $form"
}

private fun styleWidgetText(text: String, styleId: String): CharSequence {
    if (text.isEmpty()) return text
    val family = when (styleId) {
        "system_regular", "system_bold", "system_italic" -> "sans-serif"
        "condensed" -> "sans-serif-condensed"
        "serif", "serif_bold" -> "serif"
        "monospace" -> "monospace"
        else -> "sans-serif-medium"
    }
    return SpannableString(text).apply {
        setSpan(TypefaceSpan(family), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val typefaceStyle = when (styleId) {
            "system_bold", "serif_bold" -> Typeface.BOLD
            "system_italic" -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        if (typefaceStyle != Typeface.NORMAL) {
            setSpan(StyleSpan(typefaceStyle), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
