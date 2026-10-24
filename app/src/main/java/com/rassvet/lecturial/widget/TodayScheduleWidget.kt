package com.rassvet.lecturial.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.content.res.Configuration
import com.rassvet.lecturial.model.widgetSizeDp
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.ColorUtils
import com.rassvet.lecturial.LecturialApplication
import com.rassvet.lecturial.MainActivity
import com.rassvet.lecturial.R
import com.rassvet.lecturial.data.local.ScheduleEventEntity
import com.rassvet.lecturial.data.local.StudyPlanEntity
import com.rassvet.lecturial.data.local.WallpaperFieldCache
import com.rassvet.lecturial.model.AppBackgroundStyle
import com.rassvet.lecturial.model.WallpaperPatterns
import com.rassvet.lecturial.model.WidgetLook
import com.rassvet.lecturial.model.parseEventMinutes
import com.rassvet.lecturial.ui.analogousArgb
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
import com.rassvet.lecturial.ui.theme.dynamicThemePalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
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

private data class WidgetAppearance(
    val palette: ThemePalette,
    /** The app's appearance: background, card corners and transparency. */
    val options: AppDisplayOptions,
    val isDark: Boolean,
    /** Row settings, either the widget's own or the app's scaled down. */
    val look: WidgetLook,
    val backgroundOpacity: Float,
) {
    val surfaceVariantColor: Int = compositeColors(
        foreground = withAlpha(palette.onSurface, if (isDark) 0.12f else 0.08f),
        background = palette.surface,
    )
    val outlineColor: Int = withAlpha(palette.onSurface, 0.22f)
    val primaryChipColor: Int = compositeColors(withAlpha(palette.primary, if (isDark) 0.32f else 0.22f), palette.surface)
    val secondaryChipColor: Int = compositeColors(withAlpha(palette.secondary, if (isDark) 0.28f else 0.18f), palette.surface)

    /** What the lesson rows sit on: the tile colour over the widget background. */
    private val tileColor: Int = compositeColors(
        withAlpha(surfaceVariantColor, options.tileOpacityPercent.coerceIn(0, 100) / 100f),
        palette.background,
    )

    // Every text colour is checked against what it sits on (WCAG 4.5:1 for text), so
    // accents and secondary text never melt into the background or the tiles.
    val headerTitleColor: Int = readable(palette.primary, palette.background, palette.onSurface)
    val headerTextColor: Int = readable(palette.onSurface, palette.background, palette.onSurface)
    val headerSecondaryColor: Int = readable(withAlpha(palette.onSurface, 0.78f), palette.background, palette.onSurface)
    val primaryChipTextColor: Int = readable(palette.onSurface, primaryChipColor, palette.onSurface)
    val secondaryChipTextColor: Int = readable(palette.onSurface, secondaryChipColor, palette.onSurface)
    val rowTitleColor: Int = readable(palette.onSurface, tileColor, palette.onSurface)
    val secondaryTextColor: Int = readable(withAlpha(palette.onSurface, 0.78f), tileColor, palette.onSurface)
    val currentTimeColor: Int = readable(palette.primary, tileColor, palette.onSurface)
    /** Finished lessons step back but stay legible (3:1, the large-text minimum). */
    val finishedTextColor: Int = readable(withAlpha(palette.onSurface, 0.55f), tileColor, palette.onSurface, minRatio = 3.0)
}

/**
 * [color] if it reads well enough on [background], otherwise moved towards [fallback]
 * (the theme's text colour) and, if even that isn't enough, towards black or white.
 */
private fun readable(color: Int, background: Int, fallback: Int, minRatio: Double = 4.5): Int {
    val opaqueBackground = if (Color.alpha(background) == 255) background else compositeColors(background, Color.WHITE)
    fun contrast(candidate: Int) = ColorUtils.calculateContrast(compositeColors(candidate, opaqueBackground), opaqueBackground)
    if (contrast(color) >= minRatio) return color
    val extreme = if (ColorUtils.calculateLuminance(opaqueBackground) > 0.5) Color.BLACK else Color.WHITE
    for (target in listOf(fallback, extreme)) {
        for (step in 1..10) {
            val candidate = ColorUtils.blendARGB(color, target, step / 10f)
            if (contrast(candidate) >= minRatio) return candidate
        }
    }
    return extreme
}

private data class WidgetBitmapSize(
    val width: Int,
    val height: Int,
    val cornerRadius: Float,
    /** Bitmap pixels per dp: the bitmap is downscaled to stay within launcher limits. */
    val pxPerDp: Float,
)

private fun defaultWidgetAppearance(@Suppress("UNUSED_PARAMETER") error: Throwable): WidgetAppearance =
    WidgetAppearance(
        palette = defaultThemePalette(AppTheme.default, false),
        options = AppDisplayOptions(),
        isDark = false,
        look = WidgetLook.fromApp(AppDisplayOptions()),
        backgroundOpacity = 1f,
    )

private suspend fun loadWidgetAppearance(app: LecturialApplication): WidgetAppearance {
    val preferences = app.preferences
    val theme = preferences.appTheme.first()
    val isDark = preferences.darkTheme.first()
    val selectedCustomThemeId = preferences.selectedCustomThemeId.first()
    val palette = if (theme == AppTheme.Dynamic) {
        dynamicThemePalette(app, isDark) ?: defaultThemePalette(AppTheme.default, isDark)
    } else if (theme == AppTheme.Custom) {
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
    val appOptions = preferences.appDisplayOptions.first()
    val widgetOptions = preferences.widgetDisplayOptions.first()
    return WidgetAppearance(
        palette = palette,
        options = appOptions,
        isDark = isDark,
        look = WidgetLook.resolve(widgetOptions, preferences.widgetStripeColors.first(), appOptions),
        backgroundOpacity = widgetOptions.backgroundOpacityPercent / 100f,
    )
}

private fun widgetBitmapSize(
    context: Context,
    manager: AppWidgetManager,
    appWidgetId: Int,
): WidgetBitmapSize {
    val options = manager.getAppWidgetOptions(appWidgetId)
    val exactSizes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        @Suppress("DEPRECATION")
        options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            ?.map { it.width to it.height }
            .orEmpty()
    } else {
        emptyList()
    }
    val (widthDp, heightDp) = widgetSizeDp(
        portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE,
        exactSizes = exactSizes,
        minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0),
        maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0),
        minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0),
        maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0),
    )
    val density = context.resources.displayMetrics.density
    val requestedWidth = max(1, (widthDp * density).roundToInt())
    val requestedHeight = max(1, (heightDp * density).roundToInt())
    // Sharp enough for thin lines; well under the launcher's limit for widget bitmaps.
    val pixelBudget = WALLPAPER_PIXEL_BUDGET
    val downscale = min(1f, sqrt(pixelBudget / (requestedWidth.toFloat() * requestedHeight)))
    return WidgetBitmapSize(
        width = max(1, (requestedWidth * downscale).roundToInt()),
        height = max(1, (requestedHeight * downscale).roundToInt()),
        cornerRadius = 24f * density * downscale,
        pxPerDp = density * downscale,
    )
}

/** The app's background — plain, tonal fields or a line wallpaper — at widget size. */
private fun renderWidgetWallpaper(
    context: Context,
    appearance: WidgetAppearance,
    size: WidgetBitmapSize,
): Bitmap {
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.clipRoundedRect(size.width, size.height, size.cornerRadius)
    // The whole background fades together, so a translucent widget keeps its colours.
    val layer = canvas.saveLayerAlpha(
        0f,
        0f,
        size.width.toFloat(),
        size.height.toFloat(),
        (appearance.backgroundOpacity.coerceIn(0f, 1f) * 255).roundToInt(),
    )
    canvas.drawColor(appearance.palette.background)
    val options = appearance.options
    when {
        options.backgroundStyle == AppBackgroundStyle.Dynamic -> drawTonalFields(canvas, appearance, size)
        options.backgroundStyle.linePattern != null -> runCatching { drawLineWallpaper(canvas, context, appearance, size) }
            .onFailure { error -> Log.e("TodayScheduleWidget", "Unable to draw the line wallpaper", error) }
    }
    canvas.restoreToCount(layer)
    drawHeaderScrim(canvas, appearance, size)
    return bitmap
}

/**
 * Softly covers the wallpaper behind the header so the date and counters don't melt into
 * the lines or tonal fields. A translucent widget keeps part of the veil, enough for the
 * header to read over any home screen wallpaper.
 */
private fun drawHeaderScrim(canvas: Canvas, appearance: WidgetAppearance, size: WidgetBitmapSize) {
    if (appearance.options.backgroundStyle == AppBackgroundStyle.Classic && appearance.backgroundOpacity >= 1f) return
    val height = (HEADER_SCRIM_DP * size.pxPerDp).coerceAtMost(size.height.toFloat())
    val alpha = 0.85f * max(appearance.backgroundOpacity, 0.5f)
    val color = appearance.palette.background
    canvas.drawRect(0f, 0f, size.width.toFloat(), height, Paint().apply {
        shader = LinearGradient(
            0f,
            0f,
            0f,
            height,
            intArrayOf(withAlpha(color, alpha), withAlpha(color, alpha), withAlpha(color, 0f)),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
    })
}

/** Gradient colours of colourful backgrounds: the user's own or the theme's, start to end. */
private fun WidgetAppearance.gradientColors(): IntArray {
    val start = options.backgroundGradientStartColor
    val end = options.backgroundGradientEndColor
    return if (start != null && end != null) {
        intArrayOf(start, options.backgroundGradientAccentColor ?: analogousArgb(palette.primary), end)
    } else {
        intArrayOf(palette.primary, analogousArgb(palette.primary), palette.secondary)
    }
}

private fun linearGradient(size: WidgetBitmapSize, angleDegrees: Int, colors: IntArray): LinearGradient {
    val angle = Math.toRadians(angleDegrees.toDouble())
    val directionX = cos(angle).toFloat()
    val directionY = sin(angle).toFloat()
    val distance = abs(directionX) * size.width + abs(directionY) * size.height
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    return LinearGradient(
        centerX - directionX * distance / 2f,
        centerY - directionY * distance / 2f,
        centerX + directionX * distance / 2f,
        centerY + directionY * distance / 2f,
        colors,
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
}

/**
 * The finished line wallpaper, at the same scale as in the app: the pattern's mask is
 * tiled with mirroring and coloured with the gradient through it.
 */
private fun drawLineWallpaper(canvas: Canvas, context: Context, appearance: WidgetAppearance, size: WidgetBitmapSize) {
    val options = appearance.options
    val pattern = options.backgroundStyle.linePattern ?: return
    val field = WallpaperFieldCache.field(context, pattern, options.backgroundLineDensityPercent / 100f)
    // A multiple of 4 keeps the ALPHA_8 rows unpadded.
    val tilePx = ((WALLPAPER_TILE_DP * size.pxPerDp) / 4).roundToInt().coerceAtLeast(16) * 4
    val lineWidthPx = (options.backgroundLineWeight.coerceIn(1, 5) * 0.75f * size.pxPerDp).coerceAtLeast(1f)
    val mask = Bitmap.createBitmap(tilePx, tilePx, Bitmap.Config.ALPHA_8).apply {
        copyPixelsFromBuffer(ByteBuffer.wrap(WallpaperPatterns.render(field, tilePx, lineWidthPx)))
    }
    val intensity = options.backgroundIntensityPercent.coerceIn(25, 150) / 100f
    val lineAlpha = ((if (appearance.isDark) 0.5f else 0.38f) * intensity).coerceAtMost(0.9f)
    val width = size.width.toFloat()
    val height = size.height.toFloat()
    val lines = canvas.saveLayer(0f, 0f, width, height, null)
    canvas.drawRect(0f, 0f, width, height, Paint().apply {
        // The pattern wraps around its tile, so a plain repeat has no seam.
        shader = BitmapShader(mask, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    })
    canvas.drawRect(0f, 0f, width, height, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = linearGradient(size, options.backgroundGradientAngleDegrees, appearance.gradientColors())
        alpha = (lineAlpha * 255).roundToInt()
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    })
    canvas.restoreToCount(lines)
    mask.recycle()
}

private fun drawTonalFields(canvas: Canvas, appearance: WidgetAppearance, size: WidgetBitmapSize) {
    val options = appearance.options
    val intensity = options.backgroundIntensityPercent.coerceIn(25, 150) / 100f
    val customStart = options.backgroundGradientStartColor
    val customEnd = options.backgroundGradientEndColor
    val customAccent = options.backgroundGradientAccentColor ?: analogousArgb(appearance.palette.primary)
    if (customStart != null && customEnd != null) {
        val alpha = ((if (appearance.isDark) 0.30f else 0.22f) * intensity).coerceAtMost(0.55f)
        val colors = intArrayOf(withAlpha(customStart, alpha), withAlpha(customAccent, alpha), withAlpha(customEnd, alpha))
        canvas.drawRect(0f, 0f, size.width.toFloat(), size.height.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = linearGradient(size, options.backgroundGradientAngleDegrees, colors)
        })
    }

    val primary = customStart ?: appearance.palette.primary
    val secondary = customEnd ?: appearance.palette.secondary
    val tertiary = customAccent
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
}

/**
 * Lesson tiles are tinted white shapes, one per corner radius, not bitmaps: on recent
 * Android versions a bitmap inside a widget list row could be swapped for the widget's own
 * wallpaper bitmap, which showed squeezed inside every row.
 */
private val TileShapes = listOf(
    R.drawable.widget_tile_r0, R.drawable.widget_tile_r4, R.drawable.widget_tile_r8,
    R.drawable.widget_tile_r12, R.drawable.widget_tile_r16, R.drawable.widget_tile_r20,
    R.drawable.widget_tile_r24, R.drawable.widget_tile_r28, R.drawable.widget_tile_r32,
)

private fun tileShape(radiusDp: Int): Int = TileShapes[((radiusDp.coerceIn(0, 32) + 2) / 4).coerceIn(0, TileShapes.lastIndex)]

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
                        isCurrent && look.showEndTime -> "${NOW_LABEL} · до ${event!!.endTime}"
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

/** About 600×1000 px: thin lines stay crisp; a bitmap this size is far below launcher limits. */
private const val WALLPAPER_PIXEL_BUDGET = 600_000f

/** Height of the header area that gets a veil over the wallpaper. */
private const val HEADER_SCRIM_DP = 96f

/** Side of one line wallpaper tile, the same as in the app so the pattern keeps its scale. */
private const val WALLPAPER_TILE_DP = 360f

/** Today's cached lessons of the chosen schedule, with the language-teacher filter applied. */
private suspend fun todayLessons(app: LecturialApplication): List<ScheduleEventEntity> {
    val group = app.preferences.selectedGroup.first() ?: return emptyList()
    val teacher = app.preferences.languageTeacher(group).first()
    return app.database.scheduleDao().eventsForDay(group, LocalDate.now().toString()).filter {
        com.rassvet.lecturial.model.matchesLanguageTeacher(it.discipline, it.teachers.split("\u001F"), teacher)
    }
}

private val ShortDateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru"))
private val HeaderDateFormatter = DateTimeFormatter.ofPattern("d MMMM, EEE", Locale.forLanguageTag("ru"))

private fun dueDateLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when {
    date.isBefore(today) -> "Просрочено"
    date == today -> "Сегодня"
    date == today.plusDays(1) -> "Завтра"
    else -> date.format(ShortDateFormatter)
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
