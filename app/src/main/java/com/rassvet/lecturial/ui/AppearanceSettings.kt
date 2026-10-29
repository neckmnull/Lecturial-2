package com.rassvet.lecturial.ui

import com.rassvet.lecturial.model.GradientPreset
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import com.rassvet.lecturial.model.CardDensity
import com.rassvet.lecturial.model.CardTextSize
import com.rassvet.lecturial.model.CardCorners
import androidx.compose.material3.FilledTonalButton
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Create
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rassvet.lecturial.model.AppBackgroundStyle
import com.rassvet.lecturial.model.AppTheme
import com.rassvet.lecturial.model.AppDisplayOptions
import com.rassvet.lecturial.model.CustomAppTheme
import com.rassvet.lecturial.model.BuiltInThemeOverride
import com.rassvet.lecturial.model.ThemePalette
import com.rassvet.lecturial.model.toPalette
import com.rassvet.lecturial.ui.theme.defaultThemePalette
import com.rassvet.lecturial.ui.theme.dynamicThemePalette
import com.rassvet.lecturial.ui.theme.supportsDynamicTheme
import androidx.compose.material3.ButtonDefaults
import com.rassvet.lecturial.model.parseThemeColor
import com.rassvet.lecturial.model.themeColorHex
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val ru = Locale.forLanguageTag("ru")
private val WallpaperGradientColors = listOf(
    0xFF4F7DFF, 0xFF62D5E8, 0xFF67D391, 0xFFFF897D,
    0xFFB69DF8, 0xFFFF8FB3, 0xFFE8B84A, 0xFF172554,
    0xFF0F766E, 0xFF166534, 0xFF9F1239, 0xFF581C87,
)

@Composable
internal fun AppearanceSettingsScreen(
    selectedTheme: AppTheme,
    darkTheme: Boolean,
    builtInThemeOverrides: List<BuiltInThemeOverride>,
    customThemes: List<CustomAppTheme>,
    selectedCustomThemeId: String?,
    onThemeSelected: (AppTheme) -> Unit,
    onDarkThemeChanged: (Boolean) -> Unit,
    onBuiltInThemeSaved: (AppTheme, ThemePalette) -> Unit,
    onBuiltInThemeReset: (AppTheme) -> Unit,
    onCustomThemeSelected: (String) -> Unit,
    onCustomThemeSaved: (CustomAppTheme) -> Unit,
    onCustomThemeDeleted: (String) -> Unit,
    appDisplayOptions: AppDisplayOptions,
    onAppDisplayOptionsChanged: (AppDisplayOptions) -> Unit,
    onAppDisplayOptionsReset: () -> Unit,
    onOpenWidgetSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editedTheme by remember { mutableStateOf<CustomAppTheme?>(null) }
    var editedBuiltInTheme by rememberSaveable { mutableStateOf<AppTheme?>(null) }
    var creatingTheme by rememberSaveable { mutableStateOf(false) }
    // Live schedule preview: floats in on any change to the lesson cards, stays while the
    // user keeps adjusting, and leaves a few seconds after the last change unless pinned.
    var previewOptions by remember { mutableStateOf(appDisplayOptions) }
    var previewRequestKey by remember { mutableIntStateOf(0) }
    var previewVisible by rememberSaveable { mutableStateOf(false) }
    var previewPinned by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(appDisplayOptions) {
        previewOptions = appDisplayOptions
    }
    LaunchedEffect(previewRequestKey, previewPinned) {
        if (previewRequestKey == 0) return@LaunchedEffect
        previewVisible = true
        if (previewPinned) return@LaunchedEffect
        delay(PREVIEW_HIDE_DELAY_MILLIS)
        previewVisible = false
    }
    fun updateDisplayOptions(updated: AppDisplayOptions) {
        previewOptions = updated
        previewRequestKey++
        onAppDisplayOptionsChanged(updated)
    }
    fun updateBackgroundOptions(updated: AppDisplayOptions) {
        // The whole screen already shows the background, so the card would only get in the way.
        if (!previewPinned) previewVisible = false
        onAppDisplayOptionsChanged(updated)
    }
    val appearanceSection = when {
        editedBuiltInTheme != null -> AppearanceSection.BuiltInEditor
        creatingTheme || editedTheme != null -> AppearanceSection.CustomEditor
        else -> AppearanceSection.Settings
    }
    fun closeEditor() {
        editedBuiltInTheme = null
        creatingTheme = false
        editedTheme = null
    }
    BackHandler(enabled = appearanceSection != AppearanceSection.Settings, onBack = ::closeEditor)
    AnimatedContent(
        targetState = appearanceSection,
        modifier = modifier.fillMaxSize().clipToBounds(),
        transitionSpec = {
            if (targetState == AppearanceSection.Settings) {
                (slideInHorizontally(screenSlideSpec()) { -it / 10 } + fadeIn(screenFadeSpec())) togetherWith
                    (slideOutHorizontally(screenSlideSpec()) { it / 5 } + fadeOut(screenFadeSpec()))
            } else {
                (slideInHorizontally(screenSlideSpec()) { it / 5 } + fadeIn(screenFadeSpec())) togetherWith
                    (slideOutHorizontally(screenSlideSpec()) { -it / 10 } + fadeOut(screenFadeSpec()))
            }
        },
        label = "appearance-navigation",
    ) { section ->
        when (section) {
            AppearanceSection.BuiltInEditor -> {
                val builtInTheme = remember { editedBuiltInTheme } ?: return@AnimatedContent
                val savedOverride = builtInThemeOverrides.firstOrNull {
                    it.theme == builtInTheme && it.isDark == darkTheme
                }
                val palette = savedOverride?.palette ?: defaultThemePalette(builtInTheme, darkTheme)
                CustomThemeEditor(
                    theme = CustomAppTheme(
                        id = "built-in-${builtInTheme.storageId}-$darkTheme",
                        name = appThemeTitle(builtInTheme),
                        primary = palette.primary,
                        secondary = palette.secondary,
                        tertiary = palette.tertiary,
                        background = palette.background,
                        surface = palette.surface,
                        onSurface = palette.onSurface,
                    ),
                    title = "Тема «${appThemeTitle(builtInTheme)}»",
                    allowRename = false,
                    darkTheme = darkTheme,
                    previewAppTheme = builtInTheme,
                    backgroundOptions = appDisplayOptions,
                    onSave = {
                        onBuiltInThemeSaved(builtInTheme, it.toPalette())
                        closeEditor()
                    },
                    onReset = if (savedOverride != null) {
                        {
                            onBuiltInThemeReset(builtInTheme)
                            closeEditor()
                        }
                    } else null,
                    onBack = ::closeEditor,
                )
            }
            AppearanceSection.CustomEditor -> {
                val theme = remember { editedTheme }
                CustomThemeEditor(
                    theme = theme,
                    darkTheme = darkTheme,
                    backgroundOptions = appDisplayOptions,
                    onSave = {
                        onCustomThemeSaved(it)
                        closeEditor()
                    },
                    onDelete = theme?.let {
                        {
                            onCustomThemeDeleted(it.id)
                            closeEditor()
                        }
                    },
                    onBack = ::closeEditor,
                )
            }
            AppearanceSection.Settings -> Box(Modifier.fillMaxSize()) {
                SettingsPage(title = "Оформление", onBack = onBack) {
        Text(
            "Общее оформление приложения и расписания. Все сохранённые изменения применяются сразу.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppThemePreview(options = appDisplayOptions)
        SettingsSection("Тема приложения") {
            WidgetSettingSwitch(
                title = "Тёмный режим",
                description = if (darkTheme) "Тёмная палитра включена" else "Используется светлая палитра",
                checked = darkTheme,
                onCheckedChange = onDarkThemeChanged,
            )
            val selectedCustomTheme = customThemes.firstOrNull {
                selectedTheme == AppTheme.Custom && it.id == selectedCustomThemeId
            }
            val context = LocalContext.current
            val builtInThemes = AppTheme.entries.filter { theme ->
                theme != AppTheme.Custom && (theme != AppTheme.Dynamic || supportsDynamicTheme)
            }
            val swatches = builtInThemes.map { theme ->
                val palette = (if (theme == AppTheme.Dynamic) dynamicThemePalette(context, darkTheme) else null)
                    ?: builtInThemeOverrides.firstOrNull { it.theme == theme && it.isDark == darkTheme }?.palette
                    ?: defaultThemePalette(theme, darkTheme)
                ThemeSwatch(
                    key = theme.storageId,
                    name = appThemeTitle(theme),
                    primary = Color(palette.primary),
                    secondary = Color(palette.secondary),
                    tertiary = Color(palette.tertiary),
                    selected = selectedTheme == theme,
                    onClick = { onThemeSelected(theme) },
                )
            } + customThemes.map { theme ->
                ThemeSwatch(
                    key = theme.id,
                    name = theme.name,
                    primary = Color(theme.primary),
                    secondary = Color(theme.secondary),
                    tertiary = Color(theme.tertiary),
                    selected = theme == selectedCustomTheme,
                    onClick = { onCustomThemeSelected(theme.id) },
                )
            }
            ThemeSwatchRow(swatches, onCreate = { creatingTheme = true })
            Text(
                selectedCustomTheme?.let { "Своя тема «${it.name}»" } ?: appThemeDescription(selectedTheme),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Wallpaper colours come from the system, so there is nothing to edit there.
            if (selectedTheme != AppTheme.Dynamic) FilledTonalButton(
                onClick = {
                    if (selectedCustomTheme != null) editedTheme = selectedCustomTheme
                    else editedBuiltInTheme = selectedTheme.takeUnless { it == AppTheme.Custom } ?: AppTheme.default
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Create, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Изменить цвета")
            }
        }
        SettingsSection(
            "Фон приложения",
            description = "Однотонный фон, плавные цветовые поля или живые линии — лабиринт, отпечаток, топография и волны.",
        ) {
            AppBackgroundSettings(
                options = appDisplayOptions,
                onOptionsChanged = ::updateBackgroundOptions,
            )
        }
        val defaultScheduleOptions = appDisplayOptions.withDefaultCards()
        AppDisplaySettings(
            options = appDisplayOptions,
            onOptionsChanged = ::updateDisplayOptions,
        ) {
            OutlinedButton(
                onClick = {
                    previewOptions = defaultScheduleOptions
                    previewRequestKey++
                    onAppDisplayOptionsReset()
                },
                enabled = appDisplayOptions != defaultScheduleOptions,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сбросить настройки расписания") }
        }
        SettingsNavigationRow(
            title = "Оформление виджета",
            subtitle = "Цвета, содержимое, шрифт, размеры и отступы",
            onClick = onOpenWidgetSettings,
        )
        // Room to scroll the last controls above the floating preview.
        val previewSpace by animateDpAsState(
            if (previewVisible) PREVIEW_RESERVED_HEIGHT else 12.dp,
            MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "preview-space",
        )
        Spacer(Modifier.height(previewSpace))
                }
                AnimatedVisibility(
                    visible = previewVisible,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 2 } +
                        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 2 } +
                        fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    AppDisplayPreview(
                        options = previewOptions,
                        pinned = previewPinned,
                        onPinnedChange = { previewPinned = it },
                        onDismiss = {
                            previewPinned = false
                            previewVisible = false
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppBackgroundSettings(
    options: AppDisplayOptions,
    onOptionsChanged: (AppDisplayOptions) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val style = options.backgroundStyle
    val usesCustomGradient = options.backgroundGradientStartColor != null &&
        options.backgroundGradientEndColor != null
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 3,
        ) {
            AppBackgroundStyle.entries.forEach { option ->
                BackgroundStyleCard(
                    title = option.title,
                    selected = style == option,
                    options = options.copy(backgroundStyle = option, backgroundSpeedPercent = 0),
                    modifier = Modifier.weight(1f),
                    onClick = { onOptionsChanged(options.copy(backgroundStyle = option)) },
                )
            }
        }
        if (!style.isColorful) {
            Text(
                "Однотонный фон темы без градиентов и анимации, как до появления динамического фона.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Text("Цвета", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GradientPill(
                title = "Цвета темы",
                colors = listOf(colors.primary, colors.tertiary, colors.secondary),
                selected = !usesCustomGradient,
            ) {
                onOptionsChanged(
                    options.copy(
                        backgroundGradientStartColor = null,
                        backgroundGradientEndColor = null,
                        backgroundGradientAccentColor = null,
                    ),
                )
            }
            val activePreset = GradientPreset.of(options)
            GradientPreset.entries.forEach { preset ->
                GradientPill(
                    title = preset.title,
                    colors = listOf(Color(preset.start), Color(preset.middle), Color(preset.end)),
                    selected = preset == activePreset,
                ) { onOptionsChanged(preset.applyTo(options)) }
            }
            GradientPill(
                title = "Свои",
                colors = listOf(colors.surfaceContainerHighest, colors.outline, colors.surfaceContainerHighest),
                selected = usesCustomGradient && activePreset == null,
            ) {
                if (!usesCustomGradient) {
                    onOptionsChanged(
                        options.copy(
                            backgroundGradientStartColor = colors.primary.toArgb(),
                            backgroundGradientEndColor = colors.secondary.toArgb(),
                            backgroundGradientAccentColor = colors.tertiary.toArgb(),
                        ),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = usesCustomGradient,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WallpaperColorField(
                    label = "Начало градиента",
                    color = options.backgroundGradientStartColor ?: colors.primary.toArgb(),
                ) { onOptionsChanged(options.copy(backgroundGradientStartColor = it)) }
                WallpaperColorField(
                    label = "Середина градиента",
                    color = options.backgroundGradientAccentColor ?: colors.tertiary.toArgb(),
                ) { onOptionsChanged(options.copy(backgroundGradientAccentColor = it)) }
                WallpaperColorField(
                    label = "Конец градиента",
                    color = options.backgroundGradientEndColor ?: colors.secondary.toArgb(),
                ) { onOptionsChanged(options.copy(backgroundGradientEndColor = it)) }
            }
        }
        WidgetDimensionSlider(
            title = "Направление градиента",
            description = "Угол перехода между цветами",
            value = options.backgroundGradientAngleDegrees,
            range = 0..360,
            unit = "°",
            showSteps = false,
        ) { onOptionsChanged(options.copy(backgroundGradientAngleDegrees = it)) }
        if (style.linePattern != null) {
            BackgroundMotionSlider(
                title = "Плотность линий",
                description = "Сколько линий помещается на экране",
                value = options.backgroundLineDensityPercent,
                range = 50..150,
            ) { onOptionsChanged(options.copy(backgroundLineDensityPercent = it)) }
            WidgetDimensionSlider(
                title = "Толщина линий",
                description = "От тонких штрихов до плотных линий",
                value = options.backgroundLineWeight,
                range = 1..5,
                unit = "",
            ) { onOptionsChanged(options.copy(backgroundLineWeight = it)) }
        }
        BackgroundMotionSlider(
            title = "Интенсивность фона",
            description = if (style.linePattern != null) "Яркость линий" else "Насыщенность цветовых полей",
            value = options.backgroundIntensityPercent,
            range = 25..150,
        ) { onOptionsChanged(options.copy(backgroundIntensityPercent = it)) }
        BackgroundMotionSlider(
            title = if (style.linePattern != null) "Скорость линий" else "Скорость фона",
            description = if (style.linePattern != null) {
                "Как быстро линии прорисовываются и переливаются; 0 % — готовый неподвижный узор"
            } else {
                "0 % полностью останавливает движение"
            },
            value = options.backgroundSpeedPercent,
            range = 0..100,
        ) { onOptionsChanged(options.copy(backgroundSpeedPercent = it)) }
    }
}

/** A gradient swatch with its name; the selected one gets a ring. */
@Composable
private fun GradientPill(title: String, colors: List<Color>, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .semantics {
                this.selected = selected
                contentDescription = "Градиент: $title"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(50),
            border = BorderStroke(
                if (selected) 2.5.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
            color = Color.Transparent,
            modifier = Modifier.width(64.dp).height(36.dp),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(if (selected) 3.dp else 0.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(colors)),
            )
        }
        Text(
            title,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BackgroundMotionSlider(
    title: String,
    description: String,
    value: Int,
    range: IntRange,
    onValueChanged: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        "$value %",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
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
                steps = 0,
                modifier = Modifier.semantics { contentDescription = "$title: $value %" },
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${range.first} %", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${range.last} %", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WallpaperColorField(
    label: String,
    color: Int,
    onColorChanged: (Int) -> Unit,
) {
    var value by remember(color) { mutableStateOf(themeColorHex(color)) }
    val parsed = parseThemeColor(value)
    ThemeColorField(
        label = label,
        value = value,
        onValueChanged = { newValue ->
            value = newValue
            parseThemeColor(newValue)?.let(onColorChanged)
        },
        parsed = parsed,
        palette = WallpaperGradientColors,
    )
}

@Composable
private fun BackgroundStyleCard(
    title: String,
    selected: Boolean,
    options: AppDisplayOptions,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = "Фон: ${title.lowercase(ru)}"
            this.selected = selected
        },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MaterialYouBackground(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(MaterialTheme.shapes.medium),
                options = options,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                    Box(Modifier.padding(8.dp)) { ThemeSelectionIndicator(selected) }
                }
            }
            Text(
                title,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ThemeSelectionIndicator(selected: Boolean) {
    val outlineColor = MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                CircleShape,
            )
            .drawBehind {
                if (!selected) {
                    drawCircle(
                        color = outlineColor,
                        style = Stroke(1.dp.toPx()),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (selected) 9.dp else 6.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape,
                ),
        )
    }
}

@Composable
internal fun AppThemePreview(options: AppDisplayOptions) {
    MaterialYouBackground(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .semantics { contentDescription = "Живой предпросмотр темы приложения" },
        options = options,
    ) {
        Surface(
            color = Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Предпросмотр приложения", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Расписание на сегодня",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LessonChip(
                                "Лекция",
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            LessonChip(
                                "Сейчас",
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Text("Основы программирования", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "08:45–10:05 · аудитория 5.7",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Основное действие") }
            }
        }
    }
}

private fun appThemeTitle(theme: AppTheme): String = when (theme) {
    AppTheme.Dynamic -> "Обои"
    AppTheme.Sirius -> "Сириус"
    AppTheme.Ocean -> "Океан"
    AppTheme.Forest -> "Лес"
    AppTheme.Sunset -> "Закат"
    AppTheme.Amethyst -> "Аметист"
    AppTheme.Burgundy -> "Бордовый"
    AppTheme.Amber -> "Янтарь"
    AppTheme.Graphite -> "Графит"
    AppTheme.Neon -> "Неон"
    AppTheme.Fuchsia -> "Фуксия"
    AppTheme.Electric -> "Электрик"
    AppTheme.Custom -> "Своя тема"
}

private fun appThemeDescription(theme: AppTheme): String = when (theme) {
    AppTheme.Dynamic -> "Material You: цвета подбираются под обои телефона"
    AppTheme.Sirius -> "Фирменный синий"
    AppTheme.Ocean -> "Бирюзовый и лазурный"
    AppTheme.Forest -> "Зелёный и природный"
    AppTheme.Sunset -> "Коралловый и тёплый"
    AppTheme.Amethyst -> "Фиолетовый и выразительный"
    AppTheme.Burgundy -> "Глубокий бордовый"
    AppTheme.Amber -> "Золотистый и контрастный"
    AppTheme.Graphite -> "Нейтральный монохром"
    AppTheme.Neon -> "Кислотный зелёный"
    AppTheme.Fuchsia -> "Яркий розовый"
    AppTheme.Electric -> "Насыщенный синий"
    AppTheme.Custom -> "Пользовательская палитра"
}

private fun appThemePreviewColor(theme: AppTheme): Color = when (theme) {
    AppTheme.Sirius -> Color(0xFF8AABFF)
    AppTheme.Ocean -> Color(0xFF62D5E8)
    AppTheme.Forest -> Color(0xFF8ED29A)
    AppTheme.Sunset -> Color(0xFFFFB59D)
    AppTheme.Amethyst -> Color(0xFFD0BCFF)
    AppTheme.Burgundy -> Color(0xFFFFB0C8)
    AppTheme.Amber -> Color(0xFFFFC107)
    AppTheme.Graphite -> Color(0xFFC5C7CE)
    AppTheme.Neon -> Color(0xFFB6FF00)
    AppTheme.Fuchsia -> Color(0xFFFF3CAC)
    AppTheme.Electric -> Color(0xFF2979FF)
    AppTheme.Custom, AppTheme.Dynamic -> Color(0xFF8AABFF)
}

@Composable
private fun AppDisplaySettings(
    options: AppDisplayOptions,
    onOptionsChanged: (AppDisplayOptions) -> Unit,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    var fineTuning by rememberSaveable { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        if (fineTuning) 180f else 0f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "fine-tuning-arrow",
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SettingsSection("Карточки расписания", description = "Настройте карточки занятий и сразу оцените результат.") {
            PresetChoice("Плотность", CardDensity.entries, CardDensity.of(options), { it.title }) {
                onOptionsChanged(it.applyTo(options))
            }
            PresetChoice("Размер текста", CardTextSize.entries, CardTextSize.of(options), { it.title }) {
                onOptionsChanged(it.applyTo(options))
            }
            PresetChoice("Углы карточек", CardCorners.entries, CardCorners.of(options), { it.title }) {
                onOptionsChanged(it.applyTo(options))
            }
            WidgetDimensionSlider("Прозрачность", "Насколько карточки закрывают фон", options.tileOpacityPercent, 0..100, "%") {
                onOptionsChanged(options.copy(tileOpacityPercent = it))
            }
            FontStylePicker(options.fontStyleId) { onOptionsChanged(options.copy(fontStyleId = it)) }
        }
        SettingsSection("Цветные полосы") {
            StripeColorPicker("Лекция", options.lectureStripeColor) {
                onOptionsChanged(options.copy(lectureStripeColor = it))
            }
            StripeColorPicker("Практика", options.practiceStripeColor) {
                onOptionsChanged(options.copy(practiceStripeColor = it))
            }
        }
        SettingsSection("Что показывать") {
            WidgetSettingSwitch("Показывать аудиторию", "Аудитория и адрес занятия", options.showClassroom) {
                onOptionsChanged(options.copy(showClassroom = it))
            }
            WidgetSettingSwitch("Показывать время", "Время слева от названия предмета", options.showTime) {
                onOptionsChanged(options.copy(showTime = it))
            }
            WidgetSettingSwitch("Время окончания", "Окончание пары под временем начала", options.showEndTime) {
                onOptionsChanged(options.copy(showEndTime = it))
            }
            WidgetSettingSwitch("Показывать цветную полосу", "Полоса цвета лекции или практики слева у каждой пары", options.showStripe) {
                onOptionsChanged(options.copy(showStripe = it))
            }
            WidgetSettingSwitch("Переносить название", "Длинное название предмета в несколько строк", options.wrapTitle) {
                onOptionsChanged(options.copy(wrapTitle = it))
            }
        }
        SettingsSection(null) {
            Surface(
                onClick = { fineTuning = !fineTuning },
                shape = MaterialTheme.shapes.largeIncreased,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Тонкая настройка", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Точные размеры текста, отступы и скругление",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (fineTuning) "Свернуть" else "Развернуть",
                        modifier = Modifier.graphicsLayer { rotationZ = arrowRotation },
                    )
                }
            }
            AnimatedVisibility(
                visible = fineTuning,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WidgetDimensionSlider("Название предмета", "Размер основного текста", options.titleTextSizeSp, 12..24, "") {
                        onOptionsChanged(options.copy(titleTextSizeSp = it))
                    }
                    WidgetDimensionSlider("Время и детали", "Размер вспомогательного текста", options.secondaryTextSizeSp, 10..20, "") {
                        onOptionsChanged(options.copy(secondaryTextSizeSp = it))
                    }
                    WidgetDimensionSlider("Максимум строк названия", "Когда включён перенос", options.titleMaxLines, 1..5, "") {
                        onOptionsChanged(options.copy(titleMaxLines = it))
                    }
                    WidgetDimensionSlider("Скругление", "Радиус углов карточки", options.tileCornerRadiusDp, 0..32, "") {
                        onOptionsChanged(options.copy(tileCornerRadiusDp = it))
                    }
                    WidgetDimensionSlider("Отступ от краёв", "Поля слева и справа от расписания", options.outerPaddingDp, 0..32, "") {
                        onOptionsChanged(options.copy(outerPaddingDp = it))
                    }
                    WidgetDimensionSlider("Отступ текста", "Между временем и предметом", options.horizontalPaddingDp, 0..24, "") {
                        onOptionsChanged(options.copy(horizontalPaddingDp = it))
                    }
                    WidgetDimensionSlider("Высота карточки", "Свободное место внутри пары", options.verticalPaddingDp, 0..16, "") {
                        onOptionsChanged(options.copy(verticalPaddingDp = it))
                    }
                    WidgetDimensionSlider("Расстояние между парами", "Пустое место между карточками", options.itemSpacingDp, 0..24, "") {
                        onOptionsChanged(options.copy(itemSpacingDp = it))
                    }
                    WidgetDimensionSlider("Межбуквенный интервал", "Плотность букв", options.letterSpacingPercent, -5..10, "%") {
                        onOptionsChanged(options.copy(letterSpacingPercent = it))
                    }
                }
            }
            footer()
        }
    }
}

/**
 * Floating live preview of the schedule cards: a lecture and a practice, so both stripe
 * colours and the gap between lessons are visible, drawn over the app background so
 * opacity and corners look as they will in the schedule.
 */
@Composable
private fun AppDisplayPreview(
    options: AppDisplayOptions,
    pinned: Boolean,
    onPinnedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dynamicBackground = options.backgroundStyle == AppBackgroundStyle.Dynamic
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Живой предпросмотр расписания" },
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(top = 10.dp, bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Живой предпросмотр", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (pinned) "Закреплён — обновляется вместе с настройками" else "Так карточки будут выглядеть в расписании",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onPinnedChange(!pinned) }) { Text(if (pinned) "Открепить" else "Закрепить") }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Скрыть предпросмотр")
                }
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .padding(top = 8.dp)
                    .clip(MaterialTheme.shapes.largeIncreased)
                    .background(colors.background)
                    .drawBehind {
                        // A still hint of the dynamic background behind the cards.
                        if (!dynamicBackground) return@drawBehind
                        val strength = options.backgroundIntensityPercent.coerceIn(25, 150) / 100f
                        drawRect(
                            Brush.radialGradient(
                                listOf(colors.primary.copy(alpha = 0.22f * strength), Color.Transparent),
                                center = Offset(size.width * 0.9f, 0f),
                                radius = size.maxDimension * 0.8f,
                            ),
                        )
                        drawRect(
                            Brush.radialGradient(
                                listOf(colors.secondary.copy(alpha = 0.16f * strength), Color.Transparent),
                                center = Offset(0f, size.height),
                                radius = size.maxDimension * 0.7f,
                            ),
                        )
                    }
                    .padding(horizontal = options.outerPaddingDp.coerceAtMost(24).dp, vertical = 10.dp),
            ) {
                Column {
                    PreviewLessons.forEach { lesson -> LessonTimelineItem(lesson = lesson, options = options) }
                }
            }
        }
    }
}

private val PreviewLessons = listOf(
    ScheduleRow.Lesson(
        eventId = "appearance-preview-lecture",
        typeLabel = "Лекция",
        isLecture = true,
        startTime = "08:45",
        endTime = "10:05",
        discipline = "Основы программирования",
        location = "5.7 · Главный корпус",
        teachers = "И. Иван Иванович",
        comment = null,
        liveLabel = null,
    ),
    ScheduleRow.Lesson(
        eventId = "appearance-preview-practice",
        typeLabel = "Практика",
        isLecture = false,
        startTime = "10:20",
        endTime = "11:40",
        discipline = "Практические занятия",
        location = "Альфа 5.8",
        teachers = "П. Анна Олеговна",
        comment = null,
        liveLabel = null,
    ),
)

private const val PREVIEW_HIDE_DELAY_MILLIS = 4_000L
private val PREVIEW_RESERVED_HEIGHT = 380.dp
