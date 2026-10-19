package com.rassvet.lecturial.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** The line wallpapers. Each one is drawn in, line by line, in its own growth order. */
enum class LinePattern { Maze, Fingerprint, Topography, Waves }

/**
 * A scalar field sampled on a periodic n×n grid, with its gradient. Lines are drawn
 * where the field crosses `level` (or every `spacing` above it, for contour maps).
 */
class PatternField(
    val n: Int,
    val values: FloatArray,
    val level: Float,
    /** Distance between neighbouring contour levels; null draws a single contour. */
    val spacing: Float?,
    /** When each cell's line is drawn during the intro, 0 (first) to 1 (last). */
    val arrival: FloatArray,
) {
    internal val gradX = FloatArray(n * n)
    internal val gradY = FloatArray(n * n)

    init {
        for (y in 0 until n) for (x in 0 until n) {
            val i = y * n + x
            gradX[i] = (values[y * n + (x + 1) % n] - values[y * n + (x - 1 + n) % n]) / 2f
            gradY[i] = (values[((y + 1) % n) * n + x] - values[((y - 1 + n) % n) * n + x]) / 2f
        }
    }
}

object WallpaperPatterns {
    /**
     * Builds the field for [pattern]. [density] scales how many lines fit in one tile
     * (1.0 is the default look); [seed] makes the pattern reproducible.
     */
    fun field(pattern: LinePattern, density: Float, seed: Int): PatternField = when (pattern) {
        LinePattern.Maze -> maze(density, seed)
        LinePattern.Fingerprint -> fingerprint(density, seed)
        LinePattern.Topography -> topography(density, seed)
        LinePattern.Waves -> waves(density, seed)
    }

    /**
     * Alpha mask of the lines for a [sizePx]×[sizePx] tile: 0 is empty, 255 is a line.
     * Lines keep a constant [lineWidthPx] because the distance to the contour is
     * estimated from the field's gradient.
     */
    fun render(field: PatternField, sizePx: Int, lineWidthPx: Float): ByteArray {
        val out = ByteArray(sizePx * sizePx)
        val n = field.n
        val cellsPerPx = n.toFloat() / sizePx
        val halfWidth = lineWidthPx / 2f
        for (py in 0 until sizePx) {
            val gy = py * cellsPerPx
            val y0 = floor(gy).toInt()
            val ty = gy - y0
            val r0 = (y0 % n) * n
            val r1 = ((y0 + 1) % n) * n
            for (px in 0 until sizePx) {
                val gx = px * cellsPerPx
                val x0 = floor(gx).toInt()
                val tx = gx - x0
                val c0 = x0 % n
                val c1 = (x0 + 1) % n
                val value = bilinear(field.values, r0, r1, c0, c1, tx, ty)
                val dx = bilinear(field.gradX, r0, r1, c0, c1, tx, ty)
                val dy = bilinear(field.gradY, r0, r1, c0, c1, tx, ty)
                // Field change per pixel, to turn a value difference into a distance.
                val slope = max(hypot(dx, dy) * cellsPerPx, 1e-6f)
                val offset = value - field.level
                val gap = field.spacing?.let { spacing ->
                    val steps = offset / spacing
                    abs(steps - steps.roundToInt()) * spacing
                } ?: abs(offset)
                val distance = gap / slope
                val coverage = (halfWidth + 0.75f - distance).coerceIn(0f, 1.5f) / 1.5f
                out[py * sizePx + px] = (coverage * 255f).toInt().toByte()
            }
        }
        return out
    }

    /** Growth order of a [sizePx]×[sizePx] tile: 0 draws first, 255 last. */
    fun renderArrival(field: PatternField, sizePx: Int): ByteArray {
        val out = ByteArray(sizePx * sizePx)
        val n = field.n
        val cellsPerPx = n.toFloat() / sizePx
        for (py in 0 until sizePx) {
            val gy = py * cellsPerPx
            val y0 = floor(gy).toInt()
            val r0 = (y0 % n) * n
            val r1 = ((y0 + 1) % n) * n
            for (px in 0 until sizePx) {
                val gx = px * cellsPerPx
                val x0 = floor(gx).toInt()
                val value = bilinear(field.arrival, r0, r1, x0 % n, (x0 + 1) % n, gx - x0, gy - y0)
                out[py * sizePx + px] = (value.coerceIn(0f, 1f) * 255f).toInt().toByte()
            }
        }
        return out
    }

    private fun bilinear(a: FloatArray, r0: Int, r1: Int, c0: Int, c1: Int, tx: Float, ty: Float): Float {
        val top = a[r0 + c0] + (a[r0 + c1] - a[r0 + c0]) * tx
        val bottom = a[r1 + c0] + (a[r1 + c1] - a[r1 + c0]) * tx
        return top + (bottom - top) * ty
    }

    /**
     * Gray–Scott reaction–diffusion in its labyrinth regime: the meandering lines with
     * dead ends from the reference image. Runs once per tile; the stripe width is about
     * ten cells, so the grid size sets how many stripes fit.
     */
    internal fun maze(density: Float, seed: Int, steps: Int = MAZE_STEPS): PatternField {
        val n = (MAZE_BASE_CELLS * density).roundToInt().coerceIn(48, 220)
        val size = n * n
        var u = FloatArray(size) { 1f }
        var v = FloatArray(size)
        var nextU = FloatArray(size)
        var nextV = FloatArray(size)
        val random = Random(seed)
        // A few seeds, so the labyrinth visibly grows out of them like coral.
        repeat(MAZE_SEEDS) {
            val cx = random.nextInt(n)
            val cy = random.nextInt(n)
            for (dy in -2..2) for (dx in -2..2) {
                val i = ((cy + dy + n) % n) * n + (cx + dx + n) % n
                u[i] = 0.5f
                v[i] = 0.25f + random.nextFloat() * 0.1f
            }
        }
        val arrival = FloatArray(size) { -1f }
        val left = IntArray(n) { (it - 1 + n) % n }
        val right = IntArray(n) { (it + 1) % n }
        repeat(steps) { step ->
            for (y in 0 until n) {
                val row = y * n
                val up = left[y] * n
                val down = right[y] * n
                for (x in 0 until n) {
                    val i = row + x
                    val l = row + left[x]
                    val r = row + right[x]
                    val lapU = u[l] + u[r] + u[up + x] + u[down + x] - 4f * u[i]
                    val lapV = v[l] + v[r] + v[up + x] + v[down + x] - 4f * v[i]
                    val uvv = u[i] * v[i] * v[i]
                    nextU[i] = u[i] + DU * lapU - uvv + FEED * (1f - u[i])
                    nextV[i] = v[i] + DV * lapV + uvv - (FEED + KILL) * v[i]
                    if (arrival[i] < 0f && nextV[i] > MAZE_ON) arrival[i] = step.toFloat()
                }
            }
            val swapU = u; u = nextU; nextU = swapU
            val swapV = v; v = nextV; nextV = swapV
        }
        var low = Float.MAX_VALUE
        var high = -Float.MAX_VALUE
        for (value in v) {
            low = min(low, value)
            high = max(high, value)
        }
        // Gaps between stripes never switch on; give them their neighbours' time so a
        // contour, which sits between the two, appears together with its stripe.
        repeat(MAZE_SPREAD_PASSES) {
            val previous = arrival.copyOf()
            for (y in 0 until n) for (x in 0 until n) {
                val i = y * n + x
                if (previous[i] >= 0f) continue
                var earliest = Float.MAX_VALUE
                for (j in intArrayOf(y * n + left[x], y * n + right[x], left[y] * n + x, right[y] * n + x)) {
                    if (previous[j] >= 0f) earliest = min(earliest, previous[j])
                }
                if (earliest != Float.MAX_VALUE) arrival[i] = earliest
            }
        }
        val last = arrival.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        for (i in arrival.indices) arrival[i] = if (arrival[i] < 0f) 1f else arrival[i] / last
        return PatternField(n, v, level = (low + high) / 2f, spacing = null, arrival = arrival)
    }

    /**
     * Zero contour of a random monochromatic wave: closed, gently bending lines like a
     * fingerprint. Wave vectors are integers, so the tile is seamless.
     */
    private fun fingerprint(density: Float, seed: Int): PatternField {
        val k = (FINGERPRINT_WAVES * density).coerceIn(6f, 30f)
        val random = Random(seed)
        val ring = ringVectors(k)
        val waves = List(min(ring.size, 28)) { ring[random.nextInt(ring.size)] to random.nextFloat() * 2f * PI.toFloat() }
        // Blooms out of a few centres.
        val centres = List(4) { random.nextFloat() to random.nextFloat() }
        return sampled(
            gridFor(k),
            level = 0f,
            spacing = null,
            arrival = { x, y -> centres.minOf { (cx, cy) -> torusDistance(x, y, cx, cy) } / MAX_TORUS_DISTANCE },
        ) { x, y ->
            waves.sumOf { (vector, phase) -> cos(2.0 * PI * (vector.first * x + vector.second * y) + phase) }.toFloat()
        }
    }

    /** Several contour levels of smooth hills: a topographic map. */
    private fun topography(density: Float, seed: Int): PatternField {
        val random = Random(seed)
        val waves = smoothWaves(random, maxFrequency = 4)
        val levels = TOPOGRAPHY_LEVELS * density
        // Rises from the valleys to the peaks: lower contours are drawn first.
        return sampled(
            gridFor(levels * 1.6f),
            level = 0f,
            spacing = 2f / levels,
            arrival = { x, y -> (evaluate(waves, x, y) + 2f) / 4f },
        ) { x, y -> evaluate(waves, x, y) }
    }

    /** Parallel lines bent by smooth hills, like wind on sand. */
    private fun waves(density: Float, seed: Int): PatternField {
        val random = Random(seed)
        val bends = smoothWaves(random, maxFrequency = 3)
        val lines = (WAVE_LINES * density).roundToInt().coerceAtLeast(4).toFloat()
        // y·lines grows by a whole number across the tile, so the stripes wrap seamlessly.
        // Each line is drawn left to right like a pen stroke, lines starting a little apart.
        return sampled(
            gridFor(lines),
            level = 0f,
            spacing = 1f,
            arrival = { x, y ->
                val line = floor(y * lines + 0.9f * evaluate(bends, x, y) + 0.5f)
                val start = ((line * 0.618f) % 1f + 1f) % 1f * 0.3f
                (x * 0.7f + start).coerceIn(0f, 1f)
            },
        ) { x, y -> y * lines + 0.9f * evaluate(bends, x, y) }
    }

    private class Wave(val fx: Int, val fy: Int, val amplitude: Float, val phase: Float)

    private fun smoothWaves(random: Random, maxFrequency: Int): List<Wave> = buildList {
        for (fx in -maxFrequency..maxFrequency) for (fy in 0..maxFrequency) {
            if (fy == 0 && fx <= 0) continue
            val length = hypot(fx.toFloat(), fy.toFloat())
            if (length > maxFrequency) continue
            add(Wave(fx, fy, (random.nextFloat() + 0.3f) / length, random.nextFloat() * 2f * PI.toFloat()))
        }
    }.let { list ->
        val norm = list.sumOf { it.amplitude.toDouble() }.toFloat().coerceAtLeast(1e-3f)
        list.map { Wave(it.fx, it.fy, it.amplitude / norm * 2f, it.phase) }
    }

    private fun evaluate(waves: List<Wave>, x: Float, y: Float): Float =
        waves.sumOf { it.amplitude * cos(2.0 * PI * (it.fx * x + it.fy * y) + it.phase) }.toFloat()

    /** Integer wave vectors of length about [k], one per direction pair. */
    private fun ringVectors(k: Float): List<Pair<Int, Int>> {
        val radius = k.roundToInt()
        return buildList {
            for (fx in -radius - 1..radius + 1) for (fy in 0..radius + 1) {
                if (fy == 0 && fx <= 0) continue
                if (abs(hypot(fx.toFloat(), fy.toFloat()) - k) <= 0.6f) add(fx to fy)
            }
        }.ifEmpty { listOf(radius to 0) }
    }

    /** Enough cells to resolve [features] lines across the tile. */
    private fun gridFor(features: Float): Int = (features * 8f).roundToInt().coerceIn(64, 256)

    private fun sampled(
        n: Int,
        level: Float,
        spacing: Float?,
        arrival: (Float, Float) -> Float,
        f: (Float, Float) -> Float,
    ): PatternField {
        val values = FloatArray(n * n)
        val order = FloatArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val fx = x.toFloat() / n
            val fy = y.toFloat() / n
            values[y * n + x] = f(fx, fy)
            order[y * n + x] = arrival(fx, fy).coerceIn(0f, 1f)
        }
        return PatternField(n, values, level, spacing, order)
    }

    /** Distance on the unit torus, so blooms near an edge continue across it. */
    private fun torusDistance(x: Float, y: Float, cx: Float, cy: Float): Float {
        val dx = abs(x - cx).let { min(it, 1f - it) }
        val dy = abs(y - cy).let { min(it, 1f - it) }
        return hypot(dx, dy)
    }

    private const val DU = 0.2097f
    private const val DV = 0.105f
    private const val FEED = 0.0545f
    private const val KILL = 0.062f
    private const val MAZE_BASE_CELLS = 150f
    internal const val MAZE_STEPS = 5000
    private const val MAZE_SEEDS = 5
    private const val MAZE_SPREAD_PASSES = 8
    /** Inhibitor level that marks a cell as part of a stripe. */
    private const val MAZE_ON = 0.12f
    private const val MAX_TORUS_DISTANCE = 0.7072f
    private const val FINGERPRINT_WAVES = 13f
    private const val TOPOGRAPHY_LEVELS = 9f
    private const val WAVE_LINES = 14f
}
