package com.rassvet.lecturial.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rassvet.lecturial.data.local.WallpaperFieldCache
import com.rassvet.lecturial.model.LinePattern
import com.rassvet.lecturial.model.WallpaperPatterns
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Line wallpaper that draws itself: lines appear in the pattern's growth order (the
 * labyrinth grows out of its seeds, waves are drawn left to right…), then stay still.
 * The drawing runs in a GPU shader on Android 13+; older versions show it finished.
 * Afterwards the gradient keeps flowing slowly along the lines. [speedPercent] sets how
 * fast both happen; 0 shows the pattern finished and still.
 */
@Composable
internal fun LinePatternLayer(
    pattern: LinePattern,
    colors: List<Color>,
    angleDegrees: Int,
    lineAlpha: Float,
    densityPercent: Int,
    weight: Int,
    speedPercent: Int,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // A multiple of 4 keeps the ALPHA_8 bitmap rows unpadded.
    val tilePx = with(density) { (TileSize.toPx() / 4).toInt().coerceAtLeast(16) * 4 }
    val lineWidthPx = with(density) { (weight.coerceIn(1, 5) * 0.75f).dp.toPx() }
    val tile by produceState<PatternTile?>(null, pattern, densityPercent, weight, tilePx) {
        value = WallpaperTiles.tile(context.applicationContext, pattern, densityPercent / 100f, lineWidthPx, tilePx)
    }
    val animates = Build.VERSION.SDK_INT >= 33 && speedPercent > 0
    val progress = remember(tile) { Animatable(if (animates) 0f else 1f) }
    LaunchedEffect(tile, animates) {
        if (tile == null) return@LaunchedEffect
        if (!animates) {
            progress.snapTo(1f)
        } else {
            val remaining = 1f - progress.value
            progress.animateTo(
                1f,
                tween((drawDurationMillis(speedPercent) * remaining).toInt(), easing = LinearEasing),
            )
        }
    }
    // Waves are drawn as one stroke across the screen rather than once per tile.
    val sweep = pattern == LinePattern.Waves
    val growShader = remember(tile, sweep) { tile?.let { growShader(it, sweep) } }

    // The gradient flows at about 25 frames a second: smooth enough for a slow drift and far
    // cheaper than redrawing the whole screen at the display's rate.
    val flows = speedPercent > 0
    var flowMillis by remember { mutableLongStateOf(0L) }
    if (flows) {
        LaunchedEffect(Unit) {
            val startedAt = withFrameMillis { it }
            while (true) {
                delay(FLOW_FRAME_MILLIS)
                withFrameMillis { flowMillis = it - startedAt }
            }
        }
    }
    val flowPeriod = flowPeriodMillis(speedPercent)

    Canvas(
        Modifier
            .fillMaxSize()
            .testTag("line-wallpaper")
            // Offscreen so the gradient can be clipped to the lines with SrcIn.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val current = tile ?: return@Canvas
        val shader = growShader
        if (shader != null && progress.value < 1f) {
            setProgress(shader, progress.value, size.width)
            drawRect(ShaderBrush(shader))
        } else {
            drawRect(ShaderBrush(current.lines))
        }
        val angle = Math.toRadians(angleDegrees.toDouble())
        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        val reach = abs(direction.x) * size.width + abs(direction.y) * size.height
        val half = Offset(direction.x * reach / 2f, direction.y * reach / 2f)
        val center = Offset(size.width / 2f, size.height / 2f)
        // Mirrored, the gradient repeats seamlessly, so shifting it by two lengths loops.
        val phase = if (flows) (flowMillis % flowPeriod).toFloat() / flowPeriod else 0f
        val shift = Offset(direction.x * reach * 2f * phase, direction.y * reach * 2f * phase)
        drawRect(
            brush = Brush.linearGradient(colors, start = center - half + shift, end = center + half + shift, tileMode = TileMode.Mirror),
            alpha = lineAlpha.coerceIn(0f, 1f),
            blendMode = BlendMode.SrcIn,
        )
    }
}

/** One full flow of the gradient: about 16 seconds at full speed, a minute at the slowest. */
internal fun flowPeriodMillis(speedPercent: Int): Long {
    val speed = speedPercent.coerceIn(1, 100) / 100f
    return (16_000 + (1f - speed) * 44_000).toLong()
}

/** Fast speeds draw in a few seconds, slow ones take almost half a minute. */
internal fun drawDurationMillis(speedPercent: Int): Int {
    val speed = speedPercent.coerceIn(1, 100) / 100f
    return (4_000 + (1f - speed) * 24_000).toInt()
}

/** Line mask and growth order of one tile, ready for the GPU. */
private class PatternTile(lineMask: Bitmap, orderMask: Bitmap, val sizePx: Int) {
    // Every pattern wraps around its tile, so a plain repeat joins without a seam; mirroring
    // folded the lines back at each edge into a visible fold.
    val lines: Shader = BitmapShader(lineMask, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    val order: Shader = BitmapShader(orderMask, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
}

private fun growShader(tile: PatternTile, sweep: Boolean): Shader? =
    if (Build.VERSION.SDK_INT >= 33) GrowShader.create(tile, sweep) else null

private fun setProgress(shader: Shader, progress: Float, width: Float) {
    if (Build.VERSION.SDK_INT >= 33) GrowShader.setProgress(shader, progress, width)
}

@RequiresApi(33)
private object GrowShader {
    /**
     * Shows a line pixel once the drawing has reached its growth order, with a short
     * soft edge so the tips look inked in. Each tile copy starts a little later than its
     * neighbours so the repeats don't grow in lockstep. With `sweep` (the waves) the order
     * holds only when each line starts, and the screen position adds the pen's progress
     * from the left edge to the right one.
     */
    private const val SOURCE = """
        uniform shader lines;
        uniform shader order;
        uniform float progress;
        uniform float tileSize;
        uniform float sweep;
        uniform float width;

        float hash(float2 cell) {
            return fract(sin(dot(cell, float2(127.1, 311.7))) * 43758.5453);
        }

        half4 main(float2 position) {
            float alpha = float(lines.eval(position).a);
            float arrival = float(order.eval(position).a);
            float local;
            if (sweep > 0.5) {
                arrival = clamp(arrival + position.x / width * SWEEP_SHARE, 0.0, 1.0);
                local = progress;
            } else {
                float delay = hash(floor(position / tileSize)) * 0.2;
                local = (progress - delay) / 0.8;
            }
            float shown = smoothstep(arrival - 0.04, arrival, local);
            return half4(0.0, 0.0, 0.0, half(alpha * shown));
        }
    """

    fun create(tile: PatternTile, sweep: Boolean): Shader =
        RuntimeShader(SOURCE.replace("SWEEP_SHARE", WallpaperPatterns.WAVE_SWEEP.toString())).apply {
            setInputShader("lines", tile.lines)
            setInputShader("order", tile.order)
            setFloatUniform("tileSize", tile.sizePx.toFloat())
            setFloatUniform("sweep", if (sweep) 1f else 0f)
            setFloatUniform("width", 1f)
            setFloatUniform("progress", 0f)
        }

    fun setProgress(shader: Shader, progress: Float, width: Float) {
        (shader as RuntimeShader).setFloatUniform("progress", progress)
        shader.setFloatUniform("width", width.coerceAtLeast(1f))
    }
}

/** Generated tiles, kept in memory; the slow labyrinth field is also kept on disk. */
private object WallpaperTiles {
    // Room for the four style previews plus the active background.
    private val tiles = LruCache<String, PatternTile>(6)
    private val mutex = Mutex()

    suspend fun tile(context: Context, pattern: LinePattern, density: Float, lineWidthPx: Float, sizePx: Int): PatternTile {
        val key = "$pattern/$density/$lineWidthPx/$sizePx"
        tiles.get(key)?.let { return it }
        return mutex.withLock {
            tiles.get(key) ?: withContext(Dispatchers.Default) {
                val field = WallpaperFieldCache.field(context, pattern, density)
                PatternTile(
                    lineMask = alphaBitmap(WallpaperPatterns.render(field, sizePx, lineWidthPx), sizePx),
                    orderMask = alphaBitmap(WallpaperPatterns.renderArrival(field, sizePx), sizePx),
                    sizePx = sizePx,
                ).also { tiles.put(key, it) }
            }
        }
    }

    private fun alphaBitmap(pixels: ByteArray, sizePx: Int): Bitmap =
        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ALPHA_8).apply {
            copyPixelsFromBuffer(ByteBuffer.wrap(pixels))
        }

}

private val TileSize = 360.dp
private const val FLOW_FRAME_MILLIS = 40L
