package com.rassvet.lecturial.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
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
    /**
     * How much the field grows from the bottom edge of the tile to the top edge of the next
     * one. Waves rise by a whole number of lines down the tile; reading across the edge
     * without it, the jump crossed every level at once and drew a straight line there.
     */
    val yWrapStep: Float = 0f,
) {
    internal val gradX = FloatArray(n * n)
    internal val gradY = FloatArray(n * n)

    init {
        for (y in 0 until n) for (x in 0 until n) {
            val i = y * n + x
            gradX[i] = (values[y * n + (x + 1) % n] - values[y * n + (x - 1 + n) % n]) / 2f
            val below = values[((y + 1) % n) * n + x] + if (y == n - 1) yWrapStep else 0f
            val above = values[((y - 1 + n) % n) * n + x] - if (y == 0) yWrapStep else 0f
            gradY[i] = (below - above) / 2f
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
                // The last row reads the first one of the next tile, continued by its step.
                val wrap = if (y0 + 1 >= n) field.yWrapStep else 0f
                val top = field.values[r0 + c0] + (field.values[r0 + c1] - field.values[r0 + c0]) * tx
                val bottom = field.values[r1 + c0] + wrap + (field.values[r1 + c1] - field.values[r1 + c0]) * tx
                val value = top + (bottom - top) * ty
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

    /**
     * Growth order of a [sizePx]×[sizePx] tile: 0 draws first, 255 last. For [LinePattern.Waves]
     * it is when each line starts; the drawing adds [WAVE_SWEEP] times the screen position.
     */
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
        // Seeds the labyrinth grows out of like coral: one jittered seed per cell of a coarse
        // grid, so no part of a large tile is far from every seed and left empty.
        val cells = ceil(n / MAZE_SEED_SPACING).toInt().coerceAtLeast(2)
        for (gy in 0 until cells) for (gx in 0 until cells) {
            val cx = ((gx + 0.2f + random.nextFloat() * 0.6f) * n / cells).toInt()
            val cy = ((gy + 0.2f + random.nextFloat() * 0.6f) * n / cells).toInt()
            for (dy in -2..2) for (dx in -2..2) {
                val i = ((cy + dy + n) % n) * n + (cx + dx + n) % n
                u[i] = 0.5f
                v[i] = 0.25f + random.nextFloat() * 0.1f
            }
        }
        val arrival = FloatArray(size) { -1f }
        val left = IntArray(n) { (it - 1 + n) % n }
        val right = IntArray(n) { (it + 1) % n }
        // Runs at least [steps], and on until the growth has reached every part of the tile
        // and settled there; a region left untouched would stay an empty patch.
        var step = 0
        var settleUntil = -1
        while (step < steps * MAZE_MAX_STEPS_FACTOR) {
            if (step >= steps && step % MAZE_COVERAGE_CHECK == 0) {
                if (settleUntil < 0 && v.none { it < MAZE_UNTOUCHED }) settleUntil = step + MAZE_SETTLE_STEPS
                if (settleUntil in 0..step) break
            }
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
            step++
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
     * Sets of concentric circles, like the whorls of a fingerprint, packed across the tile:
     * the biggest sets first, smaller ones into the gaps. Every line is a true circle.
     *
     * Inside a set the field is -cos(pi * d / step), whose zero crossings are circles one
     * step apart; a set ends where the field reaches its top, 1, which is also its value
     * outside every set. So the field stays smooth across set edges and between sets, and
     * sets that touch continue each other's spacing. Distances wrap around the tile.
     */
    private fun fingerprint(density: Float, seed: Int): PatternField {
        val step = 1f / (FINGERPRINT_RINGS * density).coerceIn(6f, 30f)
        val whorls = packWhorls(Random(seed), step)
        fun whorlAt(x: Float, y: Float): Pair<Whorl, Float>? {
            for (whorl in whorls) {
                val d = hypot(wrapped(x - whorl.x), wrapped(y - whorl.y))
                if (d < whorl.radius) return whorl to d
            }
            return null
        }
        // Sixteen cells per step keep even the smallest circles round.
        return sampled(
            (16f / step).roundToInt().coerceIn(128, 320),
            level = 0f,
            spacing = null,
            // Each set blooms from its centre outwards, the sets starting a little apart.
            arrival = { x, y ->
                whorlAt(x, y)?.let { (whorl, d) -> whorl.delay + (1f - FINGERPRINT_MAX_DELAY) * d / whorl.radius } ?: 1f
            },
        ) { x, y ->
            whorlAt(x, y)?.let { (_, d) -> -cos(PI * d / step).toFloat() } ?: 1f
        }
    }

    private class Whorl(val x: Float, val y: Float, val radius: Float, val delay: Float)

    /**
     * Places sets of 2j+1 circles (radius (2j+1) steps) from the largest down: each size
     * gets a number of random tries and keeps the spots where it doesn't overlap a placed set.
     */
    private fun packWhorls(random: Random, step: Float): List<Whorl> {
        val whorls = mutableListOf<Whorl>()
        val largest = ((FINGERPRINT_MAX_RADIUS / step - 1f) / 2f).toInt().coerceAtLeast(0)
        for (j in largest downTo 0) {
            val radius = step * (2 * j + 1)
            repeat(FINGERPRINT_TRIES * (largest - j + 1)) {
                val x = random.nextFloat()
                val y = random.nextFloat()
                val fits = whorls.all { hypot(wrapped(x - it.x), wrapped(y - it.y)) >= it.radius + radius }
                if (fits) whorls += Whorl(x, y, radius, random.nextFloat() * FINGERPRINT_MAX_DELAY)
            }
        }
        return whorls
    }

    /** [value] moved by whole tiles into -0.5..0.5. */
    private fun wrapped(value: Float): Float = value - floor(value + 0.5f)

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
        // Each line is drawn left to right like a pen stroke across the whole screen. The tile
        // holds only when each line starts, a little apart; the sweep is added per screen
        // position when drawing (WAVE_SWEEP), since a tile-sized sweep restarted at every tile.
        // The line number wraps with the tile, so a line crossing its top edge keeps its start.
        return sampled(
            gridFor(lines),
            level = 0f,
            spacing = 1f,
            arrival = { x, y ->
                val line = floor(y * lines + 0.9f * evaluate(bends, x, y) + 0.5f)
                val index = ((line % lines) + lines) % lines
                ((index * 0.618f) % 1f) * WAVE_START_SPREAD
            },
            yWrapStep = lines,
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

    /** Enough cells to resolve [features] lines across the tile. */
    private fun gridFor(features: Float): Int = (features * 8f).roundToInt().coerceIn(64, 256)

    private fun sampled(
        n: Int,
        level: Float,
        spacing: Float?,
        arrival: (Float, Float) -> Float,
        yWrapStep: Float = 0f,
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
        return PatternField(n, values, level, spacing, order, yWrapStep)
    }

    private const val DU = 0.2097f
    private const val DV = 0.105f
    private const val FEED = 0.0545f
    private const val KILL = 0.062f
    private const val MAZE_BASE_CELLS = 150f
    internal const val MAZE_STEPS = 5000
    /** Distance between neighbouring seeds, in cells; a stripe is about ten cells wide. */
    private const val MAZE_SEED_SPACING = 70f
    /** The simulation runs at most this many times the requested steps. */
    private const val MAZE_MAX_STEPS_FACTOR = 4
    private const val MAZE_COVERAGE_CHECK = 250
    /** Steps after full coverage for the newest stripes to take their final shape. */
    private const val MAZE_SETTLE_STEPS = 1500
    /** Inhibitor level of a cell the growth hasn't reached at all. */
    private const val MAZE_UNTOUCHED = 1e-4f
    private const val MAZE_SPREAD_PASSES = 8
    /** Inhibitor level that marks a cell as part of a stripe. */
    private const val MAZE_ON = 0.12f
    /** Circle steps per tile width at 100 % density. */
    private const val FINGERPRINT_RINGS = 18f
    /** The largest set of circles, in tiles; under half a tile so a set can't meet itself. */
    private const val FINGERPRINT_MAX_RADIUS = 0.29f
    /** Random spots tried for the largest sets; smaller sizes get proportionally more. */
    private const val FINGERPRINT_TRIES = 400
    /** The latest a set starts drawing, as a share of the whole drawing time. */
    private const val FINGERPRINT_MAX_DELAY = 0.35f
    private const val TOPOGRAPHY_LEVELS = 9f
    private const val WAVE_LINES = 14f
    /** How much later the last wave starts than the first, as a share of the drawing. */
    private const val WAVE_START_SPREAD = 0.3f
    /** Share of the drawing a wave takes to cross the screen; with the spread it ends at 1. */
    const val WAVE_SWEEP = 1f - WAVE_START_SPREAD
}
