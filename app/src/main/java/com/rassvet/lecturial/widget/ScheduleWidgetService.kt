package com.rassvet.lecturial.widget

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.rassvet.lecturial.LecturialApplication
import com.rassvet.lecturial.MainActivity
import com.rassvet.lecturial.R
import com.rassvet.lecturial.data.local.ScheduleEventEntity
import com.rassvet.lecturial.data.local.StudyPlanEntity
import com.rassvet.lecturial.model.WidgetLook
import com.rassvet.lecturial.model.parseEventMinutes
import com.rassvet.lecturial.model.AppDisplayOptions
import com.rassvet.lecturial.model.StudyPlan
import com.rassvet.lecturial.model.StudyPlanRecurrence
import com.rassvet.lecturial.model.StudyPlanType
import com.rassvet.lecturial.model.nextOccurrenceDate
import com.rassvet.lecturial.model.occursOn
import com.rassvet.lecturial.model.renamedDiscipline
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class ScheduleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ScheduleWidgetFactory(applicationContext)
}

private class ScheduleWidgetFactory(
    private val context: Context,
) : RemoteViewsService.RemoteViewsFactory {
    private sealed class Item {
        data class Lesson(val event: ScheduleEventEntity) : Item()
        data class Plan(val plan: StudyPlanEntity, val occurrenceDate: LocalDate?) : Item()
        data object Error : Item()
    }

    private var items: List<Item> = emptyList()
    private var look: WidgetLook = WidgetLook.fromApp(AppDisplayOptions())
    private var lessonAliases: Map<String, String> = emptyMap()
    private var widgetAppearance: WidgetAppearance? = null
    /** Minutes since midnight when the rows were built, for the current-lesson mark. */
    private var nowMinutes: Int = 0

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        items = runCatching {
            runBlocking {
                val app = context.applicationContext as LecturialApplication
                val appearance = runCatching { loadWidgetAppearance(app) }
                    .onFailure { error -> Log.e(SERVICE_TAG, "Unable to load widget appearance; using defaults", error) }
                    .getOrElse(::defaultWidgetAppearance)
                widgetAppearance = appearance
                look = appearance.look
                nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
                lessonAliases = runCatching { app.preferences.lessonAliases.first() }.getOrDefault(emptyMap())
                val lessons = todayLessons(app)
                val plans = app.database.studyPlanDao().all()
                    .filterNot(StudyPlanEntity::isCompleted)
                val today = LocalDate.now()
                buildList {
                    addAll(lessons.map(Item::Lesson))
                    addAll(plans.mapNotNull { plan ->
                        plan.takeIf { it.widgetOccursOn(today) }?.let { Item.Plan(it, today) }
                    })
                }.sortedBy { item ->
                    when (item) {
                        is Item.Lesson -> "$today-${item.event.startTime}"
                        is Item.Plan -> "${item.occurrenceDate ?: LocalDate.MAX}-${item.plan.dueTime ?: "23:59"}"
                        Item.Error -> LocalDate.MAX.toString()
                    }
                }
            }
        }.onFailure { error ->
            Log.e(SERVICE_TAG, "Unable to load widget collection", error)
            widgetAppearance = runCatching {
                defaultWidgetAppearance(error)
            }.getOrNull()
        }.getOrElse { listOf(Item.Error) }
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        val item = items.getOrNull(position) ?: return null
        if (item == Item.Error) return errorView()
        return runCatching { scheduleItemView(item) }
            .onFailure { error -> Log.e(SERVICE_TAG, "Unable to render widget row at position=$position", error) }
            .getOrElse { errorView() }
    }

    private fun scheduleItemView(item: Item): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_schedule_item).apply {
            val look = look
            listOf(
                R.id.widget_item_time,
                R.id.widget_item_title,
                R.id.widget_item_room,
            ).forEach { viewId ->
                setFloat(viewId, "setLetterSpacing", look.letterSpacingPercent / 100f)
            }
            val event = (item as? Item.Lesson)?.event
            val plan = (item as? Item.Plan)?.plan
            val planOccurrenceDate = (item as? Item.Plan)?.occurrenceDate
            val start = event?.let { parseEventMinutes(it.startTime) }
            val end = event?.let { parseEventMinutes(it.endTime) }
            val isCurrent = start != null && end != null && nowMinutes in start until end
            val isFinished = end != null && end <= nowMinutes
            val appearance = widgetAppearance
            val fallbackColor = context.getColor(R.color.widget_on_surface_variant)
            val titleColor = appearance?.rowTitleColor ?: fallbackColor
            val secondaryTextColor = appearance?.secondaryTextColor ?: fallbackColor
            val finishedColor = appearance?.finishedTextColor ?: fallbackColor
            // Finished lessons step back; the current one is marked in the accent colour.
            setTextColor(
                R.id.widget_item_time,
                when {
                    isCurrent && appearance != null -> appearance.currentTimeColor
                    isFinished -> finishedColor
                    else -> secondaryTextColor
                },
            )
            setTextColor(R.id.widget_item_title, if (isFinished) finishedColor else titleColor)
            setTextColor(R.id.widget_item_room, if (isFinished) finishedColor else secondaryTextColor)
            val fade = if (isFinished) FINISHED_ALPHA else 1f
            setInt(R.id.widget_item_stripe, "setImageAlpha", (fade * 255).roundToInt())
            if (appearance != null) {
                // The current lesson's tile takes the accent tint, as in the app.
                setImageViewResource(R.id.widget_item_background, tileShape(appearance.options.tileCornerRadiusDp))
                setInt(
                    R.id.widget_item_background,
                    "setColorFilter",
                    if (isCurrent) appearance.primaryChipColor else appearance.surfaceVariantColor,
                )
                val opacity = appearance.options.tileOpacityPercent.coerceIn(0, 100) / 100f
                setInt(R.id.widget_item_background, "setImageAlpha", (opacity * fade * 255).roundToInt())
            }
            val horizontalPaddingPx = context.dpToPx(look.horizontalPaddingDp)
            val verticalPaddingPx = context.dpToPx(look.verticalPaddingDp)
            val spacingPx = context.dpToPx(look.itemSpacingDp)
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
            val normalizedType = event?.type.orEmpty().lowercase()
            val isPractice = normalizedType.contains("практ")
            setInt(
                R.id.widget_item_stripe,
                "setColorFilter",
                plan?.color ?: if (isPractice) look.practiceStripeColor else look.lectureStripeColor,
            )
            setViewVisibility(
                R.id.widget_item_stripe,
                if (look.showStripe) View.VISIBLE else View.GONE,
            )
            setTextViewText(
                R.id.widget_item_time,
                styleWidgetText(
                    when {
                        plan?.isAllDay == true -> "Весь день"
                        plan != null -> listOfNotNull(
                            planOccurrenceDate?.let(::dueDateLabel),
                            plan.dueTime,
                        ).joinToString(" · ")
                        isCurrent && look.showEndTime -> "${NOW_LABEL} · до ${event.endTime}"
                        isCurrent -> NOW_LABEL
                        look.showEndTime -> "${event!!.startTime}–${event.endTime}"
                        else -> event!!.startTime
                    },
                    look.fontStyleId,
                ),
            )
            setViewVisibility(R.id.widget_item_time, if (look.showTime || isCurrent) View.VISIBLE else View.GONE)
            setTextViewTextSize(
                R.id.widget_item_time,
                TypedValue.COMPLEX_UNIT_SP,
                look.secondaryTextSizeSp.toFloat(),
            )
            setTextViewText(
                R.id.widget_item_title,
                styleWidgetText(plan?.title ?: renamedDiscipline(event!!.discipline, lessonAliases), look.fontStyleId),
            )
            setInt(R.id.widget_item_title, "setMaxLines", if (look.wrapTitle) look.titleMaxLines else 1)
            setTextViewTextSize(
                R.id.widget_item_title,
                TypedValue.COMPLEX_UNIT_SP,
                look.titleTextSizeSp.toFloat(),
            )
            val details = plan?.let {
                "${StudyPlanType.fromStorageId(it.type).title} · ${it.note}".trimEnd(' ', '·')
            }
                ?: event!!.classroom.orEmpty().trim()
            setTextViewText(R.id.widget_item_room, styleWidgetText(details, look.fontStyleId))
            setTextViewTextSize(
                R.id.widget_item_room,
                TypedValue.COMPLEX_UNIT_SP,
                look.secondaryTextSizeSp.toFloat(),
            )
            setViewVisibility(
                R.id.widget_item_room,
                if ((plan != null || look.showClassroom) && details.isNotEmpty()) View.VISIBLE else View.GONE,
            )
            // A plan opens in Plans; a lesson opens the schedule on that very lesson.
            setOnClickFillInIntent(
                R.id.widget_item_root,
                when {
                    plan != null -> Intent().putExtra(MainActivity.EXTRA_PLAN_ID, plan.id)
                    else -> Intent()
                        .putExtra(MainActivity.EXTRA_LESSON_DATE, event!!.date)
                        .putExtra(MainActivity.EXTRA_LESSON_ID, event.id)
                },
            )
        }

    private fun errorView(): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_schedule_status_item).apply {
            setTextViewText(R.id.widget_status_title, context.getString(R.string.widget_load_error))
            setTextViewText(R.id.widget_status_hint, context.getString(R.string.widget_load_error_hint))
            setOnClickFillInIntent(R.id.widget_status_root, Intent())
        }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 2
    override fun getItemId(position: Int): Long =
        when (val item = items.getOrNull(position)) {
            is Item.Lesson -> item.event.id.hashCode().toLong()
            is Item.Plan -> item.plan.id.hashCode().toLong()
            Item.Error -> Long.MIN_VALUE
            null -> position.toLong()
        }

    override fun hasStableIds(): Boolean = true

    private companion object {
        const val SERVICE_TAG = "ScheduleWidgetService"
        const val NOW_LABEL = "Сейчас"
        /** How visible a finished lesson's tile and stripe stay. */
        const val FINISHED_ALPHA = 0.5f
    }
}

internal fun StudyPlanEntity.widgetOccurrenceDate(today: LocalDate): LocalDate? {
    val start = dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    return StudyPlan(
        id = id,
        type = StudyPlanType.fromStorageId(type),
        title = title,
        dueDate = start,
        recurrence = StudyPlanRecurrence.fromStorageId(recurrence),
        createdAtMillis = createdAtMillis,
    ).nextOccurrenceDate(today)
}

internal fun StudyPlanEntity.widgetOccursOn(date: LocalDate): Boolean {
    val start = dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
    return StudyPlan(
        id = id,
        type = StudyPlanType.fromStorageId(type),
        title = title,
        dueDate = start,
        recurrence = StudyPlanRecurrence.fromStorageId(recurrence),
        createdAtMillis = createdAtMillis,
    ).occursOn(date)
}

private val ShortDateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru"))
internal fun dueDateLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when {
    date.isBefore(today) -> "Просрочено"
    date == today -> "Сегодня"
    date == today.plusDays(1) -> "Завтра"
    else -> date.format(ShortDateFormatter)
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
