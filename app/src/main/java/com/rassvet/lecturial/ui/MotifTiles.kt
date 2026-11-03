package com.rassvet.lecturial.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.core.graphics.createBitmap
import com.rassvet.lecturial.model.MotifPoint
import com.rassvet.lecturial.model.WallpaperMotif
import com.rassvet.lecturial.model.pinwheelTurns
import com.rassvet.lecturial.model.turned
import kotlin.math.roundToInt

/**
 * The user's motif repeated over a square tile, cell by cell, so the tile wraps without a seam.
 * The drawing fills its cell edge to edge, and the copies of each 2×2 block turn around its
 * middle like a pinwheel, so they fit tightly and their lines meet. Without [order] it is the
 * line mask; with it, each line pixel holds when the pen gets there, from early (dark) to late
 * (light), so the wallpaper draws the strokes in the order they were drawn. [density] sets how
 * many cells fit: 2, 4 or 6 across, an even number so every block is whole.
 */
internal fun renderMotifTile(motif: WallpaperMotif, density: Float, lineWidthPx: Float, sizePx: Int, order: Boolean): Bitmap {
    val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ALPHA_8)
    if (motif.isEmpty) return bitmap
    val fitted = motif.fitted()
    val canvas = Canvas(bitmap)
    val repeats = 2 * (1 + (density - 0.5f) * 2).roundToInt().coerceIn(1, 3)
    val cell = sizePx.toFloat() / repeats
    // Only half a line in from the edge: whole strokes, but copies right next to each other.
    val inset = lineWidthPx / 2 + 1f
    val scale = cell - 2 * inset
    val paint = Paint(if (order) 0 else Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        // The order covers the line's soft edge too, and later strokes win where lines cross.
        strokeWidth = if (order) lineWidthPx + 2f else lineWidthPx
        if (order) xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }
    val total = fitted.length.coerceAtLeast(1e-4f)
    for (row in 0 until repeats) for (column in 0 until repeats) {
        val turns = pinwheelTurns(row, column)
        val left = column * cell + inset
        val top = row * cell + inset
        fun x(point: MotifPoint) = left + point.turned(turns).x * scale
        fun y(point: MotifPoint) = top + point.turned(turns).y * scale
        if (!order) {
            fitted.strokes.filter { it.size > 1 }.forEach { stroke ->
                val path = Path().apply {
                    moveTo(x(stroke[0]), y(stroke[0]))
                    stroke.drop(1).forEach { lineTo(x(it), y(it)) }
                }
                canvas.drawPath(path, paint)
            }
        } else {
            var walked = 0f
            fitted.strokes.filter { it.size > 1 }.forEach { stroke ->
                stroke.zipWithNext { a, b ->
                    walked += WallpaperMotif.segment(a, b)
                    paint.alpha = ((ORDER_START + (1f - ORDER_START) * walked / total) * 255).roundToInt().coerceIn(1, 255)
                    canvas.drawLine(x(a), y(a), x(b), y(b), paint)
                }
            }
        }
    }
    return bitmap
}

/** Even the first stroke waits a moment, so the drawing starts from an empty screen. */
private const val ORDER_START = 0.02f
