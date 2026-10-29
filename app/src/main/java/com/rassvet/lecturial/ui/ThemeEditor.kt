package com.rassvet.lecturial.ui

import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextField
import com.rassvet.lecturial.model.AppTheme
import com.rassvet.lecturial.model.AppDisplayOptions
import com.rassvet.lecturial.model.CustomAppTheme
import com.rassvet.lecturial.model.toPalette
import com.rassvet.lecturial.model.parseThemeColor
import com.rassvet.lecturial.model.themeColorHex
import com.rassvet.lecturial.ui.theme.LecturialTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private val AccentThemeColors = listOf(
    0xFF8AABFF, 0xFF62D5E8, 0xFF67D391, 0xFFFF897D,
    0xFFB69DF8, 0xFFFF8FB3, 0xFFE8B84A, 0xFFA8ABB4,
    0xFF4F7DFF, 0xFF00A7B5, 0xFF2E9B61, 0xFFE55D50,
)
private val BackgroundThemeColors = listOf(
    0xFF000000, 0xFF0A0D14, 0xFF10131C, 0xFF111827,
    0xFF18181B, 0xFF1C1917, 0xFFF8FAFC, 0xFFFFFBFE,
)
private val SurfaceThemeColors = listOf(
    0xFF0E0E10, 0xFF151922, 0xFF1E2430, 0xFF232936,
    0xFF27272A, 0xFF292524, 0xFFFFFFFF, 0xFFF1F5F9,
)
private val TextThemeColors = listOf(
    0xFFF4F4F5, 0xFFE2E8F0, 0xFFD1D5DB, 0xFFA1A1AA,
    0xFF71717A, 0xFF3F3F46, 0xFF18181B, 0xFF09090B,
)
@Composable
internal fun CustomThemeEditor(
    theme: CustomAppTheme?,
    title: String = if (theme == null) "Новая тема" else "Редактор темы",
    allowRename: Boolean = true,
    darkTheme: Boolean = true,
    previewAppTheme: AppTheme = AppTheme.Custom,
    backgroundOptions: AppDisplayOptions = AppDisplayOptions(),
    onSave: (CustomAppTheme) -> Unit,
    onDelete: (() -> Unit)? = null,
    onReset: (() -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(theme?.id) { mutableStateOf(theme?.name.orEmpty()) }
    var primary by remember(theme?.id) { mutableStateOf(theme?.let { themeColorHex(it.primary) } ?: "#8AABFF") }
    var secondary by remember(theme?.id) { mutableStateOf(theme?.let { themeColorHex(it.secondary) } ?: "#7EC8F2") }
    var tertiary by remember(theme?.id) { mutableStateOf(theme?.let { themeColorHex(it.tertiary) } ?: "#E8B84A") }
    var background by remember(theme?.id) { mutableStateOf(theme?.let { themeColorHex(it.background) } ?: "#000000") }
    var surface by remember(theme?.id) { mutableStateOf(theme?.let { themeColorHex(it.surface) } ?: "#0E0E10") }
    var onSurface by remember(theme?.id) { mutableStateOf(theme?.let { themeColorHex(it.onSurface) } ?: "#F4F4F5") }
    val parsed = listOf(primary, secondary, tertiary, background, surface, onSurface).map(::parseThemeColor)
    val canSave = name.isNotBlank() && parsed.all { it != null }
    val previewTheme = if (parsed.all { it != null }) {
        CustomAppTheme(
            id = theme?.id ?: "theme-preview",
            name = name.ifBlank { "Предпросмотр" },
            primary = parsed[0]!!,
            secondary = parsed[1]!!,
            tertiary = parsed[2]!!,
            background = parsed[3]!!,
            surface = parsed[4]!!,
            onSurface = parsed[5]!!,
        )
    } else null
    SettingsPage(title = title, onBack = onBack, modifier = modifier) {
        Text(
            "Выберите оттенки на hue-колесе, из готовой палитры или введите их в формате #RRGGBB. Предпросмотр обновляется сразу, тема сохранится только по кнопке ниже.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsSection(null) {
            if (allowRename) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Название темы") },
                    singleLine = true,
                )
            } else {
                Text(name, style = MaterialTheme.typography.titleLarge)
            }
            ThemeColorField("Основной цвет", primary, { primary = it }, parsed[0], AccentThemeColors)
            ThemeColorField("Дополнительный цвет", secondary, { secondary = it }, parsed[1], AccentThemeColors)
            ThemeColorField("Третичный цвет", tertiary, { tertiary = it }, parsed[2], AccentThemeColors)
            ThemeColorField("Фон", background, { background = it }, parsed[3], BackgroundThemeColors)
            ThemeColorField("Поверхности", surface, { surface = it }, parsed[4], SurfaceThemeColors)
            ThemeColorField("Текст", onSurface, { onSurface = it }, parsed[5], TextThemeColors)
        }
        if (previewTheme != null) {
            LecturialTheme(
                appTheme = previewAppTheme,
                customTheme = previewTheme.takeIf { previewAppTheme == AppTheme.Custom },
                darkTheme = darkTheme,
                builtInOverride = previewTheme.toPalette().takeIf { previewAppTheme != AppTheme.Custom },
            ) {
                AppThemePreview(options = backgroundOptions)
            }
        }
        Button(
            enabled = canSave,
            onClick = {
                onSave(
                    CustomAppTheme(
                        id = theme?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        primary = parsed[0]!!,
                        secondary = parsed[1]!!,
                        tertiary = parsed[2]!!,
                        background = parsed[3]!!,
                        surface = parsed[4]!!,
                        onSurface = parsed[5]!!,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Сохранить и применить") }
        if (onDelete != null) {
            OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Удалить тему") }
        }
        if (onReset != null) {
            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                Text("Сбросить изменения темы")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeColorField(
    label: String,
    value: String,
    onValueChanged: (String) -> Unit,
    parsed: Int?,
    palette: List<Long>,
) {
    var paletteExpanded by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChanged(it.take(9)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = { if (value.isNotBlank() && parsed == null) Text("Используйте формат #RRGGBB") },
        isError = value.isNotBlank() && parsed == null,
        singleLine = true,
        leadingIcon = {
            Box(
                Modifier.size(24.dp).background(parsed?.let(::Color) ?: Color.Transparent, MaterialTheme.shapes.small),
            )
        },
        trailingIcon = {
            TextButton(
                onClick = { paletteExpanded = !paletteExpanded },
                modifier = Modifier.semantics {
                    contentDescription = if (paletteExpanded) "Скрыть палитру: $label" else "Открыть палитру: $label"
                },
            ) {
                Text(if (paletteExpanded) "Скрыть" else "Палитра")
            }
        },
    )
    if (paletteExpanded) {
        HueColorPicker(
            label = label,
            color = parsed ?: palette.first().toInt(),
            onColorChanged = { onValueChanged(themeColorHex(it)) },
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            palette.forEach { paletteColor ->
                val color = paletteColor.toInt()
                val hex = themeColorHex(color)
                val selected = parsed == color
                Surface(
                    onClick = { onValueChanged(hex) },
                    modifier = Modifier
                        .size(42.dp)
                        .semantics {
                            contentDescription = "$label, цвет $hex"
                            this.selected = selected
                        },
                    shape = MaterialTheme.shapes.medium,
                    color = Color(color),
                    border = BorderStroke(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {}
            }
        }
    }
}

@Composable
private fun HueColorPicker(
    label: String,
    color: Int,
    onColorChanged: (Int) -> Unit,
) {
    val hsv = remember(color) { FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) } }
    val saturation = hsv[1].takeIf { it >= 0.15f } ?: 0.72f
    val brightness = hsv[2].let { if (it < 0.08f) 0.18f else it }
    fun selectHue(position: Offset, center: Offset) {
        val degrees = Math.toDegrees(
            atan2(position.y - center.y, position.x - center.x).toDouble(),
        ).toFloat()
        val hue = (degrees + 90f + 360f) % 360f
        onColorChanged(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Hue-колесо", style = MaterialTheme.typography.labelLarge)
        Canvas(
            modifier = Modifier
                .size(184.dp)
                .semantics { contentDescription = "Hue-колесо: $label" }
                .pointerInput(saturation, brightness) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        selectHue(down.position, center)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                selectHue(change.position, center)
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            val strokeWidth = 24.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            repeat(360) { degree ->
                drawArc(
                    color = Color(android.graphics.Color.HSVToColor(floatArrayOf(degree.toFloat(), 1f, 1f))),
                    startAngle = degree - 90f,
                    sweepAngle = 1.8f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(strokeWidth),
                )
            }
            val angle = Math.toRadians((hsv[0] - 90f).toDouble())
            val markerRadius = diameter / 2f
            val marker = Offset(
                x = center.x + cos(angle).toFloat() * markerRadius,
                y = center.y + sin(angle).toFloat() * markerRadius,
            )
            drawCircle(Color.Black.copy(alpha = 0.65f), radius = 9.dp.toPx(), center = marker)
            drawCircle(Color.White, radius = 6.dp.toPx(), center = marker)
            drawCircle(Color(color), radius = 4.dp.toPx(), center = marker)
            drawCircle(Color(color), radius = 42.dp.toPx(), center = center)
        }
        Text(
            "Проведите по кругу, чтобы выбрать оттенок",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
