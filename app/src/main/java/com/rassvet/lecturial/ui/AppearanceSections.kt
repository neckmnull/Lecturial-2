package com.rassvet.lecturial.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip

/** A theme in the swatch row: its name and three accent colours. */
internal data class ThemeSwatch(
    val key: String,
    val name: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * All themes as a single row of colour swatches instead of a long list; the last item
 * creates a new theme.
 */
@Composable
internal fun ThemeSwatchRow(swatches: List<ThemeSwatch>, onCreate: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        swatches.forEach { swatch -> ThemeSwatchItem(swatch) }
        Column(
            modifier = Modifier
                .width(SwatchItemWidth)
                .clip(MaterialTheme.shapes.medium)
                .clickable(role = Role.Button, onClick = onCreate),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(SwatchSize),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                "Создать тему",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ThemeSwatchItem(swatch: ThemeSwatch) {
    val scale by animateFloatAsState(
        if (swatch.selected) 1f else 0.86f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "swatch-scale",
    )
    val ring = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .width(SwatchItemWidth)
            .clip(MaterialTheme.shapes.medium)
            // The swatch and its name are one target: tapping the name used to do nothing.
            .selectable(selected = swatch.selected, role = Role.RadioButton, onClick = swatch.onClick)
            .semantics { contentDescription = "Тема: ${swatch.name}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(SwatchSize),
            shape = CircleShape,
            color = Color.Transparent,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    // Top half primary, bottom quarters secondary and tertiary, like the
                    // system wallpaper colour picker.
                    val inset = if (swatch.selected) 5.dp.toPx() else 0f
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = Offset(inset, inset)
                    drawArc(swatch.primary, 180f, 180f, useCenter = true, topLeft = topLeft, size = arcSize)
                    drawArc(swatch.tertiary, 0f, 90f, useCenter = true, topLeft = topLeft, size = arcSize)
                    drawArc(swatch.secondary, 90f, 90f, useCenter = true, topLeft = topLeft, size = arcSize)
                    if (swatch.selected) drawCircle(ring, style = Stroke(2.5.dp.toPx()))
                }
                if (swatch.selected) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(3.dp),
                        )
                    }
                }
            }
        }
        Text(
            swatch.name,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (swatch.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One-tap presets as a connected button group. [selected] is null when the options were
 * tuned by hand, so no button is lit.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> PresetChoice(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            if (selected == null) {
                Text("свои значения", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            options.forEachIndexed { index, option ->
                ToggleButton(
                    checked = option == selected,
                    onCheckedChange = { onSelected(option) },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    shapes = connectedButtonShapes(index, options.size),
                ) {
                    Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private val SwatchSize = 56.dp
private val SwatchItemWidth = 72.dp
