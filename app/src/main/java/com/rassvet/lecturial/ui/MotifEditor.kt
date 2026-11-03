package com.rassvet.lecturial.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rassvet.lecturial.model.MotifPoint
import com.rassvet.lecturial.model.WallpaperMotif

/**
 * A square to draw one cell of the wallpaper with a finger. Each stroke is kept as soon as the
 * finger lifts, so the background behind the settings shows the pattern straight away.
 */
@Composable
internal fun MotifEditor(motif: WallpaperMotif, onMotifChanged: (WallpaperMotif) -> Unit) {
    var current by remember { mutableStateOf<List<MotifPoint>>(emptyList()) }
    // The gesture loop starts once; it reads the latest motif when a stroke ends.
    val latestMotif by rememberUpdatedState(motif)
    val latestOnChanged by rememberUpdatedState(onMotifChanged)
    val ink = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Caption(
            if (motif.isFull) {
                "Рисунок заполнен: отмените последние штрихи или очистите его."
            } else {
                "Нарисуйте узор пальцем. Он сам растянется на всю клетку, копии встанут вплотную " +
                    "и развернутся вертушкой по четыре. Линии появляются в том порядке, в каком вы их рисовали."
            },
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .semantics { contentDescription = "Поле для рисования узора обоев" }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        // Taken at once, so the page doesn't scroll while drawing.
                        down.consume()
                        val points = arrayListOf(down.position.toMotif(size.width.toFloat(), size.height.toFloat()))
                        current = points.toList()
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            points += change.position.toMotif(size.width.toFloat(), size.height.toFloat())
                            current = points.toList()
                        }
                        current = emptyList()
                        val updated = latestMotif.plusStroke(points)
                        if (updated != latestMotif) latestOnChanged(updated)
                    }
                },
        ) {
            // The edge of one copy: the copies repeat side by side with a little room between.
            val inset = size.minDimension * 0.04f
            drawRect(
                color = guide,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
            )
            motif.strokes.forEach { drawStroke(it, ink) }
            drawStroke(current, ink.copy(alpha = 0.7f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { onMotifChanged(motif.withoutLastStroke()) },
                enabled = motif.strokes.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Отменить")
            }
            OutlinedButton(
                onClick = { onMotifChanged(WallpaperMotif()) },
                enabled = motif.strokes.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Очистить")
            }
        }
    }
}

private fun Offset.toMotif(width: Float, height: Float) = MotifPoint(x / width, y / height)

private fun DrawScope.drawStroke(points: List<MotifPoint>, color: androidx.compose.ui.graphics.Color) {
    if (points.isEmpty()) return
    val path = Path().apply {
        moveTo(points[0].x * size.width, points[0].y * size.height)
        // A tap shows as a dot.
        if (points.size == 1) lineTo(points[0].x * size.width + 0.5f, points[0].y * size.height)
        points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
    }
    drawPath(path, color, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
