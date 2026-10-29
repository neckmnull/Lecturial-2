package com.rassvet.lecturial.ui

import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.rassvet.lecturial.model.WidgetStripePalette
import com.rassvet.lecturial.model.WidgetFontStyles
import com.rassvet.lecturial.model.AppDisplayOptions
import kotlin.math.roundToInt

private const val MaxSliderTickIntervals = 32

@Composable
internal fun FontStylePicker(
    selectedId: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = WidgetFontStyles.options.firstOrNull { it.id == selectedId }
        ?: WidgetFontStyles.options.first()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Шрифт и начертание", style = MaterialTheme.typography.titleMedium)
        Surface(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Выбрать шрифт, сейчас ${selected.name}" },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(selected.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Физика · 08:45",
                        modifier = Modifier.semantics { contentDescription = "Предпросмотр шрифта: ${selected.name}" },
                        style = fontPreviewStyle(MaterialTheme.typography.bodyLarge, selected.id),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(selected.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WidgetFontStyles.options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.name, style = MaterialTheme.typography.labelLarge)
                            Text(
                                "Физика · 08:45",
                                modifier = Modifier.semantics { contentDescription = "Предпросмотр шрифта: ${option.name}" },
                                style = fontPreviewStyle(MaterialTheme.typography.bodyLarge, option.id),
                            )
                        }
                    },
                    onClick = {
                        onSelected(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun fontPreviewStyle(
    base: androidx.compose.ui.text.TextStyle,
    fontStyleId: String,
): androidx.compose.ui.text.TextStyle = appTextStyle(
    base = base,
    sizeSp = 16,
    defaultSizeSp = 16,
    options = AppDisplayOptions(fontStyleId = fontStyleId),
)

@Composable
internal fun WidgetDimensionSlider(
    title: String,
    description: String,
    value: Int,
    range: IntRange,
    unit: String = "dp",
    showSteps: Boolean = true,
    onValueChanged: (Int) -> Unit,
) {
    // dp/sp/line ranges are discrete, so every value gets a tick mark. Only very dense
    // ranges (percentages, angles) stay continuous; their rounded value is shown above.
    val intervalCount = range.last - range.first
    val steps = if (showSteps && intervalCount in 2..MaxSliderTickIntervals) intervalCount - 1 else 0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "$value $unit".trim(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChanged(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps,
            modifier = Modifier.semantics { contentDescription = "$title: $value $unit".trim() },
        )
    }
}

@Composable
internal fun WidgetSettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // One touch target announced as a switch, like an M3 list item with a switch.
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Composable
internal fun StripeColorPicker(
    label: String,
    selectedColor: Int,
    onSelected: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            WidgetStripePalette.options.forEach { option ->
                val isSelected = option.argb == selectedColor
                Surface(
                    onClick = { onSelected(option.argb) },
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = "$label: ${option.name}${if (isSelected) ", выбран" else ""}"
                            selected = isSelected
                        },
                    shape = MaterialTheme.shapes.large,
                    color = Color.Transparent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(option.argb), MaterialTheme.shapes.small)
                                .then(
                                    Modifier.border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                        shape = MaterialTheme.shapes.small,
                                    ),
                                ),
                        )
                    }
                }
            }
        }
    }
}
