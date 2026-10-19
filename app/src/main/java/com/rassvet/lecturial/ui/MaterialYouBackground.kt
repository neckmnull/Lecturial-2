package com.rassvet.lecturial.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.core.graphics.ColorUtils
import com.rassvet.lecturial.model.AppBackgroundStyle
import com.rassvet.lecturial.model.AppDisplayOptions
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Material You backdrop shared by every app destination.
 *
 * The soft tonal fields are derived from the active Material color scheme, so
 * built-in, light, dark, and user-created themes keep their intended contrast.
 */
@Composable
internal fun MaterialYouBackground(
    modifier: Modifier = Modifier,
    options: AppDisplayOptions = AppDisplayOptions(),
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f
    val intensity = options.backgroundIntensityPercent.coerceIn(25, 150) / 100f
    val primaryAlpha = (if (isDark) 0.22f else 0.16f) * intensity
    val secondaryAlpha = (if (isDark) 0.16f else 0.12f) * intensity
    val tertiaryAlpha = (if (isDark) 0.13f else 0.10f) * intensity
    val customGradientColors = options.backgroundGradientStartColor?.let { start ->
        options.backgroundGradientEndColor?.let { end ->
            listOf(
                Color(start),
                Color(options.backgroundGradientAccentColor ?: colors.primary.analogous().toArgb()),
                Color(end),
            )
        }
    }

    Surface(
        modifier = modifier.testTag("material-you-background"),
        color = colors.background,
        contentColor = colors.onBackground,
    ) {
        Box(Modifier.fillMaxSize()) {
            options.backgroundStyle.linePattern?.let { pattern ->
                LinePatternLayer(
                    pattern = pattern,
                    colors = customGradientColors ?: listOf(colors.primary, colors.primary.analogous(), colors.secondary),
                    angleDegrees = options.backgroundGradientAngleDegrees,
                    lineAlpha = ((if (isDark) 0.5f else 0.38f) * intensity).coerceAtMost(0.9f),
                    densityPercent = options.backgroundLineDensityPercent,
                    weight = options.backgroundLineWeight,
                    speedPercent = options.backgroundSpeedPercent,
                )
            }
            if (options.backgroundStyle == AppBackgroundStyle.Dynamic) {
                if (customGradientColors != null) {
                    CustomLinearGradient(
                        colors = customGradientColors,
                        angleDegrees = options.backgroundGradientAngleDegrees,
                        alpha = ((if (isDark) 0.30f else 0.22f) * intensity).coerceAtMost(0.55f),
                    )
                }
                DynamicTonalFields(
                    primary = (customGradientColors?.firstOrNull() ?: colors.primary)
                        .copy(alpha = primaryAlpha.coerceAtMost(1f)),
                    secondary = (customGradientColors?.lastOrNull() ?: colors.secondary)
                        .copy(alpha = secondaryAlpha.coerceAtMost(1f)),
                    tertiary = (customGradientColors?.getOrNull(1) ?: colors.primary.analogous())
                        .copy(alpha = tertiaryAlpha.coerceAtMost(1f)),
                    speedPercent = options.backgroundSpeedPercent,
                )
            }
            content()
        }
    }
}

/**
 * A neighbouring hue of this colour. The third tonal field used to take the theme's
 * tertiary colour, which is often complementary (amber on blue, orange on lime) and
 * blends into a muddy brown over a dark background; an analogous hue keeps the fields
 * in one family. Yellows turn towards orange rather than towards lime green.
 */
internal fun Color.analogous(degrees: Float = 28f): Color =
    Color(analogousArgb(toArgb(), degrees)).copy(alpha = alpha)

/** [analogous] for a packed ARGB colour; the widget draws with these. */
internal fun analogousArgb(argb: Int, degrees: Float = 28f): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    val shift = if (hsl[0] in 30f..90f) -degrees else degrees
    hsl[0] = (hsl[0] + shift + 360f) % 360f
    return ColorUtils.HSLToColor(hsl)
}

@Composable
private fun CustomLinearGradient(
    colors: List<Color>,
    angleDegrees: Int,
    alpha: Float,
) {
    Canvas(Modifier.fillMaxSize().testTag("custom-wallpaper-gradient")) {
        val angle = Math.toRadians(angleDegrees.toDouble())
        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        val distance = abs(direction.x) * size.width + abs(direction.y) * size.height
        val halfVector = Offset(direction.x * distance / 2f, direction.y * distance / 2f)
        val center = Offset(size.width / 2f, size.height / 2f)
        drawRect(
            brush = Brush.linearGradient(
                colors = colors.map { it.copy(alpha = alpha) },
                start = center - halfVector,
                end = center + halfVector,
            ),
        )
    }
}

@Composable
private fun DynamicTonalFields(
    primary: Color,
    secondary: Color,
    tertiary: Color,
    speedPercent: Int,
) {
    if (speedPercent <= 0) {
        TonalFields(primary, secondary, tertiary) { StaticPhases }
        return
    }
    val baseDurationMillis = 80_000 - speedPercent.coerceIn(1, 100) * 500
    // The fields drift over tens of seconds, so ~20 redraws per second look identical to
    // 60 while keeping the whole screen from being repainted on every frame.
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val startedAt = withFrameMillis { it }
        while (true) {
            delay(BackgroundFrameMillis)
            withFrameMillis { elapsedMillis = it - startedAt }
        }
    }
    TonalFields(primary, secondary, tertiary) {
        fun phase(durationMultiplier: Float): Float {
            val duration = (baseDurationMillis * durationMultiplier).toLong()
            return (elapsedMillis % duration).toFloat() / duration * FullTurn
        }
        // Read only inside the draw phase: ticks invalidate drawing, not composition.
        floatArrayOf(phase(1f), phase(1.37f), phase(1.79f), phase(2.23f))
    }
}

private val StaticPhases = floatArrayOf(0f, 1.7f, 3.8f, 0.9f)
private const val BackgroundFrameMillis = 50L

@Composable
private fun TonalFields(
    primary: Color,
    secondary: Color,
    tertiary: Color,
    phases: () -> FloatArray,
) {
    Canvas(
        Modifier
            .fillMaxSize()
            .testTag("dynamic-tonal-fields")
            // Its own layer: each tick redraws only the fields. Without it the tick
            // re-recorded the whole screen above the background twenty times a second.
            .graphicsLayer(),
    ) {
        val (primaryPhase, secondaryPhase, tertiaryPhase, driftPhase) = phases()
        fun softField(color: Color, center: Offset, radius: Float) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(color, color.copy(alpha = 0f)),
                    center = center,
                    radius = radius,
                ),
            )
        }

        softField(
            color = primary,
            center = Offset(
                size.width * (1.04f + 0.13f * sin(primaryPhase) + 0.04f * cos(driftPhase)),
                size.height * (0.08f + 0.08f * cos(primaryPhase) + 0.03f * sin(driftPhase)),
            ),
            radius = size.maxDimension * (0.66f + 0.035f * sin(primaryPhase + driftPhase)),
        )
        softField(
            color = secondary,
            center = Offset(
                size.width * (-0.12f + 0.11f * cos(secondaryPhase) + 0.035f * sin(driftPhase)),
                size.height * (0.48f + 0.12f * sin(secondaryPhase) + 0.03f * cos(driftPhase)),
            ),
            radius = size.maxDimension * (0.58f + 0.04f * cos(secondaryPhase - driftPhase)),
        )
        softField(
            color = tertiary,
            center = Offset(
                size.width * (0.88f + 0.095f * sin(tertiaryPhase) + 0.03f * cos(driftPhase)),
                size.height * (0.96f + 0.08f * cos(tertiaryPhase) + 0.025f * sin(driftPhase)),
            ),
            radius = size.maxDimension * (0.62f + 0.035f * sin(tertiaryPhase + driftPhase)),
        )
    }
}

private val FullTurn = (2 * PI).toFloat()