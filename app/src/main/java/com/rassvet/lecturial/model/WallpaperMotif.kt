package com.rassvet.lecturial.model

import java.util.Locale
import kotlin.math.hypot

/** A point of a drawn stroke, in 0..1 of the square cell it was drawn in. */
data class MotifPoint(val x: Float, val y: Float)

/**
 * A pattern drawn by the user for the line wallpaper: strokes in the order they were drawn.
 * The wallpaper repeats the cell across the screen and draws the strokes in that order.
 */
data class WallpaperMotif(val strokes: List<List<MotifPoint>> = emptyList()) {
    val isEmpty: Boolean get() = strokes.none { it.size > 1 }

    val pointCount: Int get() = strokes.sumOf { it.size }

    /** Whether another stroke still fits; the motif is kept small enough to store and draw quickly. */
    val isFull: Boolean get() = strokes.size >= MAX_STROKES || pointCount >= MAX_POINTS

    /**
     * [points] added as a new stroke, thinned so that steps shorter than [MIN_STEP] go;
     * a tap becomes a dot. Nothing changes once the motif is full.
     */
    fun plusStroke(points: List<MotifPoint>): WallpaperMotif {
        if (points.isEmpty() || isFull) return this
        val clamped = points.map { MotifPoint(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) }
        val thinned = thin(clamped).let { stroke ->
            if (stroke.size > 1) stroke else listOf(stroke[0], MotifPoint((stroke[0].x + DOT).coerceAtMost(1f), stroke[0].y))
        }
        val stroke = thinned.take(MAX_POINTS - pointCount)
        return if (stroke.size < 2) this else copy(strokes = strokes + listOf(stroke))
    }

    fun withoutLastStroke(): WallpaperMotif = copy(strokes = strokes.dropLast(1))

    /** The drawing stretched evenly over the whole square and centred, so its copies sit close together. */
    fun fitted(): WallpaperMotif {
        val points = strokes.flatten()
        if (points.isEmpty()) return this
        val minX = points.minOf { it.x }
        val minY = points.minOf { it.y }
        val spanX = points.maxOf { it.x } - minX
        val spanY = points.maxOf { it.y } - minY
        val span = maxOf(spanX, spanY, MIN_SPAN)
        val padX = (span - spanX) / 2
        val padY = (span - spanY) / 2
        return copy(strokes = strokes.map { stroke -> stroke.map { MotifPoint((it.x - minX + padX) / span, (it.y - minY + padY) / span) } })
    }

    /** Drawn length of all strokes, in cell sizes. */
    val length: Float get() = strokes.sumOf { stroke -> stroke.zipWithNext { a, b -> segment(a, b).toDouble() }.sum() }.toFloat()

    companion object {
        const val MAX_STROKES = 60
        const val MAX_POINTS = 3000
        const val MIN_STEP = 0.008f
        private const val DOT = 0.002f
        /** A dot or a short dash is not blown up to the whole square. */
        private const val MIN_SPAN = 0.08f

        internal fun segment(a: MotifPoint, b: MotifPoint): Float = hypot(b.x - a.x, b.y - a.y)

        private fun thin(points: List<MotifPoint>): List<MotifPoint> {
            val kept = arrayListOf(points.first())
            points.drop(1).forEach { point -> if (segment(kept.last(), point) >= MIN_STEP) kept += point }
            // The finger lifts where the user meant the stroke to end.
            if (points.size > 1 && kept.last() != points.last()) kept += points.last()
            return kept
        }
    }
}

/** This point turned clockwise around the square's centre by [quarterTurns] × 90°. */
fun MotifPoint.turned(quarterTurns: Int): MotifPoint = when (Math.floorMod(quarterTurns, 4)) {
    0 -> this
    1 -> MotifPoint(1 - y, x)
    2 -> MotifPoint(1 - x, 1 - y)
    else -> MotifPoint(y, 1 - x)
}

/**
 * How many quarter turns the copy in [row], [column] gets: in every 2×2 block the copies turn
 * around the block's middle like a pinwheel, so the corners near it meet.
 */
fun pinwheelTurns(row: Int, column: Int): Int = when {
    row % 2 == 0 && column % 2 == 0 -> 0
    row % 2 == 0 -> 1
    column % 2 == 1 -> 2
    else -> 3
}

/** "x,y x,y;x,y x,y": strokes split by `;`, points by spaces, three decimals each. */
fun encodeMotif(motif: WallpaperMotif): String = motif.strokes.joinToString(";") { stroke ->
    stroke.joinToString(" ") { String.format(Locale.ROOT, "%.3f,%.3f", it.x, it.y) }
}

fun parseMotif(raw: String?): WallpaperMotif {
    if (raw.isNullOrBlank()) return WallpaperMotif()
    val strokes = raw.split(';').mapNotNull { stroke ->
        stroke.trim().split(' ').mapNotNull { pair ->
            val (x, y) = pair.split(',').takeIf { it.size == 2 } ?: return@mapNotNull null
            val px = x.toFloatOrNull() ?: return@mapNotNull null
            val py = y.toFloatOrNull() ?: return@mapNotNull null
            MotifPoint(px.coerceIn(0f, 1f), py.coerceIn(0f, 1f))
        }.takeIf { it.size > 1 }
    }
    return WallpaperMotif(strokes.take(WallpaperMotif.MAX_STROKES))
}
