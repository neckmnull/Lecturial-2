package com.rassvet.lecturial.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import com.rassvet.lecturial.model.AppBackgroundStyle
import com.rassvet.lecturial.model.AppDisplayOptions
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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

    Surface(
        modifier = modifier.testTag("material-you-background"),
        color = colors.background,
        contentColor = colors.onBackground,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (options.backgroundStyle == AppBackgroundStyle.Dynamic) {
                DynamicTonalFields(
                    primary = colors.primary.copy(alpha = primaryAlpha.coerceAtMost(1f)),
                    secondary = colors.secondary.copy(alpha = secondaryAlpha.coerceAtMost(1f)),
                    tertiary = colors.tertiary.copy(alpha = tertiaryAlpha.coerceAtMost(1f)),
                    speedPercent = options.backgroundSpeedPercent,
                )
            }
            content()
        }
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
        TonalFields(
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
            primaryPhase = 0f,
            secondaryPhase = 1.7f,
            tertiaryPhase = 3.8f,
            driftPhase = 0.9f,
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "dynamic-background")
    val baseDurationMillis = 80_000 - speedPercent.coerceIn(1, 100) * 500
    @Composable
    fun phase(durationMultiplier: Float, label: String): Float = transition.animateFloat(
        initialValue = 0f,
        targetValue = FullTurn,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (baseDurationMillis * durationMultiplier).toInt(),
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = label,
    ).value
    TonalFields(
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        primaryPhase = phase(1f, "primary-field-phase"),
        secondaryPhase = phase(1.37f, "secondary-field-phase"),
        tertiaryPhase = phase(1.79f, "tertiary-field-phase"),
        driftPhase = phase(2.23f, "background-drift-phase"),
    )
}

@Composable
private fun TonalFields(
    primary: Color,
    secondary: Color,
    tertiary: Color,
    primaryPhase: Float,
    secondaryPhase: Float,
    tertiaryPhase: Float,
    driftPhase: Float,
) {
    Canvas(
        Modifier
            .fillMaxSize()
            .testTag("dynamic-tonal-fields"),
    ) {
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