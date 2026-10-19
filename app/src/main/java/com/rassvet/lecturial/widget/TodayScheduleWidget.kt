package com.rassvet.lecturial.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
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
import com.rassvet.lecturial.model.AppBackgroundStyle
import com.rassvet.lecturial.model.AppDisplayOptions
import com.rassvet.lecturial.model.AppTheme
import com.rassvet.lecturial.model.ThemePalette
import com.rassvet.lecturial.model.WidgetStripePalette
import com.rassvet.lecturial.model.WidgetFontStyles
import com.rassvet.lecturial.model.StudyPlan
import com.rassvet.lecturial.model.StudyPlanRecurrence
import com.rassvet.lecturial.model.StudyPlanType
import com.rassvet.lecturial.model.nextOccurrenceDate
import com.rassvet.lecturial.model.occursOn
import com.rassvet.lecturial.model.renamedDiscipline
import com.rassvet.lecturial.model.toPalette
import com.rassvet.lecturial.ui.theme.defaultThemePalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class TodayScheduleWidget : AppWidgetProvider() {
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
            val outerPaddingDp = runCatching {
                app.preferences.widgetDisplayOptions.first().outerPaddingDp
            }.getOrDefault(9)
            val group = app.preferences.selectedGroup.first()
            val lessons = if (group == null) {
                emptyList()
            } else {
                val teacher = app.preferences.languageTeacher(group).first()
                app.database.scheduleDao().eventsForDay(group, LocalDate.now().toString()).filter {
                    com.rassvet.lecturial.model.matchesLanguageTeacher(
                        it.discipline,
                        it.teachers.split("\u001F"),
                        teacher,
                    )
                }
            }
            val activePlans = app.database.studyPlanDao().all()
                .filterNot(StudyPlanEntity::isCompleted)
            val today = LocalDate.now()
            val todayPlanCount = activePlans.count { it.widgetOccursOn(today) }
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
                    )
                }
                runCatching {
                    val views = commonViews().apply {
                        setImageViewBitmap(
                            R.id.widget_wallpaper,
                            renderWidgetWallpaper(
                                appearance = appearance,
                                size = widgetBitmapSize(context, manager, id),
                            ),
                        )
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
                val listTemplate = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                setPendingIntentTemplate(R.id.widget_list, listTemplate)
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
                setTextViewText(R.id.widget_empty, context.getString(emptyMessageRes))
                appearance?.let { colors ->
                    setTextColor(R.id.widget_title, colors.palette.primary)
                    setTextColor(R.id.widget_date, colors.palette.onSurface)
                    setTextColor(R.id.widget_nearest_plan_text, colors.secondaryTextColor)
                    setTextColor(R.id.widget_empty, colors.secondaryTextColor)
                    setInt(R.id.widget_plan_dot, "setColorFilter", colors.palette.secondary)
                    setInt(R.id.widget_divider, "setBackgroundColor", colors.outlineColor)
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

        private const val TAG = "TodayScheduleWidget"
        private const val UPDATE_ALL_DEBOUNCE_MILLIS = 300L
    }
}

private data class WidgetAppearance(
    val palette: ThemePalette,
    val options: AppDisplayOptions,
    val isDark: Boolean,
) {
    val surfaceVariantColor: Int = compositeColors(
        foreground = withAlpha(palette.onSurface, if (isDark) 0.12f else 0.08f),
        background = palette.surface,
    )
    val secondaryTextColor: Int = withAlpha(palette.onSurface, 0.72f)
    val outlineColor: Int = withAlpha(palette.onSurface, 0.22f)
}

private data class WidgetBitmapSize(
    val width: Int,
    val height: Int,
    val cornerRadius: Float,
)

private fun defaultWidgetAppearance(@Suppress("UNUSED_PARAMETER") error: Throwable): WidgetAppearance =
    WidgetAppearance(
        palette = defaultThemePalette(AppTheme.default, false),
        options = AppDisplayOptions(),
        isDark = false,
    )

private suspend fun loadWidgetAppearance(app: LecturialApplication): WidgetAppearance {
    val preferences = app.preferences
    val theme = preferences.appTheme.first()
    val isDark = preferences.darkTheme.first()
    val selectedCustomThemeId = preferences.selectedCustomThemeId.first()
    val palette = if (theme == AppTheme.Custom) {
        preferences.customThemes.first()
            .firstOrNull { it.id == selectedCustomThemeId }
            ?.toPalette()
            ?: defaultThemePalette(AppTheme.default, isDark)
    } else {
        preferences.builtInThemeOverrides.first()
            .firstOrNull { it.theme == theme && it.isDark == isDark }
            ?.palette
            ?: defaultThemePalette(theme, isDark)
    }
    return WidgetAppearance(
        palette = palette,
        options = preferences.appDisplayOptions.first(),
        isDark = isDark,
    )
}

private fun widgetBitmapSize(
    context: Context,
    manager: AppWidgetManager,
    appWidgetId: Int,
): WidgetBitmapSize {
    val options = manager.getAppWidgetOptions(appWidgetId)
    val widthDp = max(
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180),
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 180),
    ).coerceAtLeast(1)
    val heightDp = max(
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110),
        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110),
    ).coerceAtLeast(1)
    val density = context.resources.displayMetrics.density
    val requestedWidth = max(1, (widthDp * density).roundToInt())
    val requestedHeight = max(1, (heightDp * density).roundToInt())
    val pixelBudget = 120_000f
    val downscale = min(1f, sqrt(pixelBudget / (requestedWidth.toFloat() * requestedHeight)))
    return WidgetBitmapSize(
        width = max(1, (requestedWidth * downscale).roundToInt()),
        height = max(1, (requestedHeight * downscale).roundToInt()),
        cornerRadius = 24f * density * downscale,
    )
}

private fun renderWidgetWallpaper(
    appearance: WidgetAppearance,
    size: WidgetBitmapSize,
): Bitmap {
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.clipRoundedRect(size.width, size.height, size.cornerRadius)
    canvas.drawColor(appearance.palette.background)
    val options = appearance.options
    if (options.backgroundStyle != AppBackgroundStyle.Dynamic) return bitmap

    val intensity = options.backgroundIntensityPercent.coerceIn(25, 150) / 100f
    val customStart = options.backgroundGradientStartColor
    val customEnd = options.backgroundGradientEndColor
    val customAccent = options.backgroundGradientAccentColor ?: appearance.palette.tertiary
    if (customStart != null && customEnd != null) {
        val angle = Math.toRadians(options.backgroundGradientAngleDegrees.toDouble())
        val directionX = cos(angle).toFloat()
        val directionY = sin(angle).toFloat()
        val distance = abs(directionX) * size.width + abs(directionY) * size.height
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val alpha = ((if (appearance.isDark) 0.30f else 0.22f) * intensity).coerceAtMost(0.55f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                centerX - directionX * distance / 2f,
                centerY - directionY * distance / 2f,
                centerX + directionX * distance / 2f,
                centerY + directionY * distance / 2f,
                intArrayOf(
                    withAlpha(customStart, alpha),
                    withAlpha(customAccent, alpha),
                    withAlpha(customEnd, alpha),
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, size.width.toFloat(), size.height.toFloat(), paint)
    }

    val primary = customStart ?: appearance.palette.primary
    val secondary = customEnd ?: appearance.palette.secondary
    val tertiary = if (customStart != null && customEnd != null) customAccent else appearance.palette.tertiary
    val primaryAlpha = ((if (appearance.isDark) 0.22f else 0.16f) * intensity).coerceAtMost(1f)
    val secondaryAlpha = ((if (appearance.isDark) 0.16f else 0.12f) * intensity).coerceAtMost(1f)
    val tertiaryAlpha = ((if (appearance.isDark) 0.13f else 0.10f) * intensity).coerceAtMost(1f)
    val maxDimension = max(size.width, size.height).toFloat()
    drawSoftField(
        canvas,
        primary,
        primaryAlpha,
        size.width * (1.04f + 0.04f * cos(0.9f)),
        size.height * (0.16f + 0.03f * sin(0.9f)),
        maxDimension * (0.66f + 0.035f * sin(0.9f)),
    )
    drawSoftField(
        canvas,
        secondary,
        secondaryAlpha,
        size.width * (-0.12f + 0.11f * cos(1.7f) + 0.035f * sin(0.9f)),
        size.height * (0.48f + 0.12f * sin(1.7f) + 0.03f * cos(0.9f)),
        maxDimension * (0.58f + 0.04f * cos(0.8f)),
    )
    drawSoftField(
        canvas,
        tertiary,
        tertiaryAlpha,
        size.width * (0.88f + 0.095f * sin(3.8f) + 0.03f * cos(0.9f)),
        size.height * (0.96f + 0.08f * cos(3.8f) + 0.025f * sin(0.9f)),
        maxDimension * (0.62f + 0.035f * sin(4.7f)),
    )
    return bitmap
}

private fun renderWidgetTile(
    context: Context,
    appearance: WidgetAppearance,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val estimatedHeightDp = 54 + appearance.options.verticalPaddingDp * 2 +
        (appearance.options.titleMaxLines - 2).coerceAtLeast(0) * appearance.options.titleTextSizeSp
    val requestedWidth = max(1, (320 * density).roundToInt())
    val requestedHeight = max(1, (estimatedHeightDp * density).roundToInt())
    // Collection rows are transferred through Binder one by one. A compact bitmap
    // keeps rounded translucent tiles without risking launcher transaction limits.
    val pixelBudget = 20_000f
    val downscale = min(1f, sqrt(pixelBudget / (requestedWidth.toFloat() * requestedHeight)))
    val width = max(1, (requestedWidth * downscale).roundToInt())
    val height = max(1, (requestedHeight * downscale).roundToInt())
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = appearance.options.tileCornerRadiusDp.coerceIn(0, 32) * density * downscale
    canvas.clipRoundedRect(width, height, radius)
    canvas.drawColor(
        withAlpha(
            appearance.surfaceVariantColor,
            appearance.options.tileOpacityPercent.coerceIn(0, 100) / 100f,
        ),
    )
    return bitmap
}

private fun drawSoftField(
    canvas: Canvas,
    color: Int,
    alpha: Float,
    centerX: Float,
    centerY: Float,
    radius: Float,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            centerX,
            centerY,
            radius,
            withAlpha(color, alpha),
            withAlpha(color, 0f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
}

private fun Canvas.clipRoundedRect(width: Int, height: Int, radius: Float) {
    val clipPath = Path().apply {
        addRoundRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            radius,
            radius,
            Path.Direction.CW,
        )
    }
    clipPath(clipPath)
}

private fun withAlpha(color: Int, alpha: Float): Int = Color.argb(
    (alpha.coerceIn(0f, 1f) * 255).roundToInt(),
    Color.red(color),
    Color.green(color),
    Color.blue(color),
)

private fun compositeColors(foreground: Int, background: Int): Int {
    val foregroundAlpha = Color.alpha(foreground) / 255f
    val backgroundAlpha = Color.alpha(background) / 255f
    val outputAlpha = foregroundAlpha + backgroundAlpha * (1f - foregroundAlpha)
    if (outputAlpha <= 0f) return Color.TRANSPARENT

    fun component(foregroundComponent: Int, backgroundComponent: Int): Int =
        ((foregroundComponent * foregroundAlpha +
            backgroundComponent * backgroundAlpha * (1f - foregroundAlpha)) / outputAlpha)
            .roundToInt()
            .coerceIn(0, 255)

    return Color.argb(
        (outputAlpha * 255).roundToInt().coerceIn(0, 255),
        component(Color.red(foreground), Color.red(background)),
        component(Color.green(foreground), Color.green(background)),
        component(Color.blue(foreground), Color.blue(background)),
    )
}

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
    private var widgetAppearance: WidgetAppearance? = null
    private var widgetTile: Bitmap? = null

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        items = runCatching {
            runBlocking {
                val app = context.applicationContext as LecturialApplication
                widgetAppearance = runCatching { loadWidgetAppearance(app) }
                    .onFailure { error -> Log.e(SERVICE_TAG, "Unable to load widget appearance; using defaults", error) }
                    .getOrElse(::defaultWidgetAppearance)
                widgetTile = widgetAppearance?.let { renderWidgetTile(context, it) }
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
                val group = app.preferences.selectedGroup.first()
                val lessons = if (group == null) emptyList() else {
                    val teacher = app.preferences.languageTeacher(group).first()
                    app.database.scheduleDao().eventsForDay(group, LocalDate.now().toString()).filter {
                        com.rassvet.lecturial.model.matchesLanguageTeacher(
                            it.discipline,
                            it.teachers.split("\u001F"),
                            teacher,
                        )
                    }
                }
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
            widgetTile = null
        }.getOrElse { listOf(Item.Error) }
    }

    override fun onDestroy() {
        items = emptyList()
        widgetTile = null
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
            listOf(
                R.id.widget_item_time,
                R.id.widget_item_title,
                R.id.widget_item_room,
            ).forEach { viewId ->
                setFloat(viewId, "setLetterSpacing", letterSpacingPercent / 100f)
            }
            val appearance = widgetAppearance
            val secondaryTextColor = appearance?.secondaryTextColor
                ?: context.getColor(R.color.widget_on_surface_variant)
            setTextColor(R.id.widget_item_time, secondaryTextColor)
            setTextColor(R.id.widget_item_title, secondaryTextColor)
            setTextColor(R.id.widget_item_room, secondaryTextColor)
            widgetTile?.let { setImageViewBitmap(R.id.widget_item_background, it) }
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
            val event = (item as? Item.Lesson)?.event
            val plan = (item as? Item.Plan)?.plan
            val planOccurrenceDate = (item as? Item.Plan)?.occurrenceDate
            val normalizedType = event?.type.orEmpty().lowercase()
            val isPractice = normalizedType.contains("практ")
            setInt(
                R.id.widget_item_stripe,
                "setColorFilter",
                plan?.color ?: if (isPractice) practiceStripeColor else lectureStripeColor,
            )
            setViewVisibility(
                R.id.widget_item_stripe,
                if (showStripe) View.VISIBLE else View.GONE,
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
                        showEndTime -> "${event!!.startTime}–${event.endTime}"
                        else -> event!!.startTime
                    },
                    fontStyleId,
                ),
            )
            setViewVisibility(R.id.widget_item_time, if (showTime) View.VISIBLE else View.GONE)
            setTextViewTextSize(
                R.id.widget_item_time,
                TypedValue.COMPLEX_UNIT_SP,
                secondaryTextSizeSp.toFloat(),
            )
            setTextViewText(
                R.id.widget_item_title,
                styleWidgetText(plan?.title ?: renamedDiscipline(event!!.discipline, lessonAliases), fontStyleId),
            )
            setInt(R.id.widget_item_title, "setMaxLines", if (wrapTitle) titleMaxLines else 1)
            setTextViewTextSize(
                R.id.widget_item_title,
                TypedValue.COMPLEX_UNIT_SP,
                titleTextSizeSp.toFloat(),
            )
            val details = plan?.let {
                "${StudyPlanType.fromStorageId(it.type).title} · ${it.note}".trimEnd(' ', '·')
            }
                ?: event!!.classroom.orEmpty().trim()
            setTextViewText(R.id.widget_item_room, styleWidgetText(details, fontStyleId))
            setTextViewTextSize(
                R.id.widget_item_room,
                TypedValue.COMPLEX_UNIT_SP,
                secondaryTextSizeSp.toFloat(),
            )
            setViewVisibility(
                R.id.widget_item_room,
                if ((plan != null || showClassroom) && details.isNotEmpty()) View.VISIBLE else View.GONE,
            )
            setOnClickFillInIntent(
                R.id.widget_item_root,
                if (plan != null) Intent().putExtra(MainActivity.EXTRA_PLAN_ID, plan.id) else Intent(),
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
    }
}

private fun StudyPlanEntity.widgetOccurrenceDate(today: LocalDate): LocalDate? {
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

private fun StudyPlanEntity.widgetOccursOn(date: LocalDate): Boolean {
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
