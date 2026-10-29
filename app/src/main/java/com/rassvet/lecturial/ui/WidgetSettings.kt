package com.rassvet.lecturial.ui

import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetSettingsScreen(
    modifier: Modifier = Modifier,
    state: ScheduleUiState,
    onBack: () -> Unit,
    onLectureColorSelected: (Int) -> Unit,
    onPracticeColorSelected: (Int) -> Unit,
    onShowTimeChanged: (Boolean) -> Unit,
    onShowStripeChanged: (Boolean) -> Unit,
    onShowClassroomChanged: (Boolean) -> Unit,
    onShowEndTimeChanged: (Boolean) -> Unit,
    onWrapTitleChanged: (Boolean) -> Unit,
    onOuterPaddingChanged: (Int) -> Unit,
    onHorizontalPaddingChanged: (Int) -> Unit,
    onVerticalPaddingChanged: (Int) -> Unit,
    onItemSpacingChanged: (Int) -> Unit,
    onTitleTextSizeChanged: (Int) -> Unit,
    onSecondaryTextSizeChanged: (Int) -> Unit,
    onFontStyleChanged: (String) -> Unit,
    onLetterSpacingChanged: (Int) -> Unit,
    onTitleMaxLinesChanged: (Int) -> Unit,
    onMatchAppChanged: (Boolean) -> Unit,
    onBackgroundOpacityChanged: (Int) -> Unit,
) {
    SettingsPage(title = "Виджет", onBack = onBack, modifier = modifier) {
            Text(
                "Виджет повторяет тему и фон приложения — динамический, линии или однотонный, с вашим градиентом.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsSection(null) {
                WidgetSettingSwitch(
                    title = "Как в приложении",
                    description = "Цветные полосы, шрифт, размер текста, отступы и что показывать — как в расписании",
                    checked = state.widgetMatchApp,
                    onCheckedChange = onMatchAppChanged,
                )
                WidgetDimensionSlider(
                    title = "Фон виджета",
                    description = "Насколько фон закрывает обои рабочего стола",
                    value = state.widgetBackgroundOpacityPercent,
                    range = 0..100,
                    unit = "%",
                    showSteps = false,
                    onValueChanged = onBackgroundOpacityChanged,
                )
                AnimatedVisibility(
                    visible = state.widgetMatchApp,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    Text(
                        "Меняйте оформление карточек в разделе «Оформление» — виджет подстроится сам. " +
                            "Выключите «Как в приложении», чтобы настроить виджет отдельно.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(
                visible = !state.widgetMatchApp,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsSection("Цветовые полосы") {
                        StripeColorPicker("Лекция", state.lectureStripeColor, onLectureColorSelected)
                        StripeColorPicker("Практика", state.practiceStripeColor, onPracticeColorSelected)
                    }
                    SettingsSection("Содержимое") {
                        WidgetSettingSwitch(
                            title = "Показывать аудиторию",
                            description = "Номер аудитории под названием предмета",
                            checked = state.widgetShowClassroom,
                            onCheckedChange = onShowClassroomChanged,
                        )
                        WidgetSettingSwitch(
                            title = "Показывать время",
                            description = "Время начала или полный диапазон занятия",
                            checked = state.widgetShowTime,
                            onCheckedChange = onShowTimeChanged,
                        )
                        WidgetSettingSwitch(
                            title = "Показывать цветную полосу",
                            description = "Акцент слева для различения лекций и практик",
                            checked = state.widgetShowStripe,
                            onCheckedChange = onShowStripeChanged,
                        )
                        WidgetSettingSwitch(
                            title = "Время окончания",
                            description = "Полный диапазон, например 08:45–10:05",
                            checked = state.widgetShowEndTime,
                            onCheckedChange = onShowEndTimeChanged,
                        )
                        WidgetSettingSwitch(
                            title = "Переносить название",
                            description = "До двух строк для длинного названия предмета",
                            checked = state.widgetWrapTitle,
                            onCheckedChange = onWrapTitleChanged,
                        )
                    }
                    SettingsSection("Размер текста") {
                        FontStylePicker(
                            selectedId = state.widgetFontStyleId,
                            onSelected = onFontStyleChanged,
                        )
                        WidgetDimensionSlider(
                            title = "Название предмета",
                            description = "Размер основного текста в виджете",
                            value = state.widgetTitleTextSizeSp,
                            range = 9..18,
                            unit = "sp",
                            onValueChanged = onTitleTextSizeChanged,
                        )
                        WidgetDimensionSlider(
                            title = "Время и аудитория",
                            description = "Размер вспомогательного текста",
                            value = state.widgetSecondaryTextSizeSp,
                            range = 7..14,
                            unit = "sp",
                            onValueChanged = onSecondaryTextSizeChanged,
                        )
                        WidgetDimensionSlider(
                            title = "Межбуквенный интервал",
                            description = "От плотного текста до более разреженного",
                            value = state.widgetLetterSpacingPercent,
                            range = -5..10,
                            unit = "%",
                            onValueChanged = onLetterSpacingChanged,
                        )
                        WidgetDimensionSlider(
                            title = "Максимум строк названия",
                            description = "Используется, когда включён перенос названия",
                            value = state.widgetTitleMaxLines,
                            range = 1..4,
                            unit = "стр.",
                            onValueChanged = onTitleMaxLinesChanged,
                        )
                    }
                    SettingsSection("Отступы и плотность") {
                        WidgetDimensionSlider(
                            title = "Отступ от краёв виджета",
                            description = "Не даёт строкам заходить на скруглённые края",
                            value = state.widgetOuterPaddingDp,
                            range = 0..24,
                            onValueChanged = onOuterPaddingChanged,
                        )
                        WidgetDimensionSlider(
                            title = "Отступ текста слева и справа",
                            description = "Расстояние от цветной полосы и правого края",
                            value = state.widgetHorizontalPaddingDp,
                            range = 0..24,
                            onValueChanged = onHorizontalPaddingChanged,
                        )
                        WidgetDimensionSlider(
                            title = "Вертикальный отступ строки",
                            description = "Высота свободного места внутри каждого занятия",
                            value = state.widgetVerticalPaddingDp,
                            range = 0..16,
                            onValueChanged = onVerticalPaddingChanged,
                        )
                        WidgetDimensionSlider(
                            title = "Расстояние между занятиями",
                            description = "Пустое место между соседними строками",
                            value = state.widgetItemSpacingDp,
                            range = 0..12,
                            onValueChanged = onItemSpacingChanged,
                        )
                    }
                }
            }
            Text(
                "Изменения применяются к виджету сразу.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
    }
}
