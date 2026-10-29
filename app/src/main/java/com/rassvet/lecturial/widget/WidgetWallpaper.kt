package com.rassvet.lecturial.widget

import android.appwidget.AppWidgetManager
import android.content.Context
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
import android.os.Build
import android.util.SizeF
import android.content.res.Configuration
import com.rassvet.lecturial.model.widgetSizeDp
import android.util.Log
import androidx.core.graphics.ColorUtils
import com.rassvet.lecturial.LecturialApplication
import com.rassvet.lecturial.R
import com.rassvet.lecturial.data.local.WallpaperFieldCache
import com.rassvet.lecturial.model.AppBackgroundStyle
import com.rassvet.lecturial.model.WallpaperPatterns
import com.rassvet.lecturial.model.WidgetLook
import com.rassvet.lecturial.ui.analogousArgb
import com.rassvet.lecturial.model.AppDisplayOptions
import com.rassvet.lecturial.model.AppTheme
import com.rassvet.lecturial.model.ThemePalette
import com.rassvet.lecturial.model.toPalette
import com.rassvet.lecturial.ui.theme.defaultThemePalette
import com.rassvet.lecturial.ui.theme.dynamicThemePalette
import kotlinx.coroutines.flow.first
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.core.graphics.createBitmap

internal data class WidgetAppearance(
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

internal data class WidgetBitmapSize(
    val width: Int,
    val height: Int,
    val cornerRadius: Float,
    /** Bitmap pixels per dp: the bitmap is downscaled to stay within launcher limits. */
    val pxPerDp: Float,
)

internal fun defaultWidgetAppearance(@Suppress("UNUSED_PARAMETER") error: Throwable): WidgetAppearance =
    WidgetAppearance(
        palette = defaultThemePalette(AppTheme.default, false),
        options = AppDisplayOptions(),
        isDark = false,
        look = WidgetLook.fromApp(AppDisplayOptions()),
        backgroundOpacity = 1f,
    )

internal suspend fun loadWidgetAppearance(app: LecturialApplication): WidgetAppearance {
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

internal fun widgetBitmapSize(
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
internal fun renderWidgetWallpaper(
    context: Context,
    appearance: WidgetAppearance,
    size: WidgetBitmapSize,
): Bitmap {
    val bitmap = createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
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
    val mask = createBitmap(tilePx, tilePx, Bitmap.Config.ALPHA_8).apply {
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

internal fun tileShape(radiusDp: Int): Int = TileShapes[((radiusDp.coerceIn(0, 32) + 2) / 4).coerceIn(0, TileShapes.lastIndex)]

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

/** About 600×1000 px: thin lines stay crisp; a bitmap this size is far below launcher limits. */
private const val WALLPAPER_PIXEL_BUDGET = 600_000f

/** Height of the header area that gets a veil over the wallpaper. */
private const val HEADER_SCRIM_DP = 96f

/** Side of one line wallpaper tile, the same as in the app so the pattern keeps its scale. */
private const val WALLPAPER_TILE_DP = 360f
