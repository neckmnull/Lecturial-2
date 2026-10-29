package com.rassvet.lecturial.ui

import com.rassvet.lecturial.model.TeacherInfo
import androidx.compose.material.icons.outlined.AccountBox
import com.rassvet.lecturial.model.LessonAlertPause
import java.time.ZoneId
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.graphics.Shape
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rassvet.lecturial.R
import com.rassvet.lecturial.BuildConfig
import com.rassvet.lecturial.data.local.UserPreferences
import com.rassvet.lecturial.notify.LessonAlerts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import android.content.Intent
import android.app.AlarmManager
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.rassvet.lecturial.model.AppUpdateState
import com.rassvet.lecturial.model.DeadlineReminder
import androidx.compose.material3.ButtonDefaults
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.core.net.toUri

private val ru = Locale.forLanguageTag("ru")
private val dayMonthFormatter = DateTimeFormatter.ofPattern("d MMMM", ru)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppSettingsScreen(
    state: ScheduleUiState,
    onGroupSelected: (String) -> Unit,
    onTeacherSelected: (TeacherInfo) -> Unit,
    onSearchModeChanged: (ScheduleSearchMode) -> Unit,
    onGroupSearchQueryChanged: (String) -> Unit,
    onGroupsRetry: () -> Unit,
    onOpenLanguageTeacher: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenLessonNames: () -> Unit,
    onOpenTesting: () -> Unit,
    onOpenAbout: () -> Unit,
    onDeadlineReminderDaysChanged: (Set<Int>) -> Unit,
    onLessonAlertsPause: (LessonAlertPause?) -> Unit,
    onScheduleWeeksBackChanged: (Int) -> Unit,
    onScheduleWeeksForwardChanged: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "Расписание, виджет и приложение",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsGroup(title = "Расписание") {
            item { shape ->
                SettingsListItem(
                    title = if (state.isTeacherMode) "Преподаватель" else "Учебная группа",
                    subtitle = state.scheduleLabel,
                    shape = shape,
                    icon = if (state.isTeacherMode) Icons.Rounded.Person else Icons.Rounded.AccountBox,
                    trailing = {
                        GroupDropdown(
                            state = state,
                            onSearchQueryChanged = onGroupSearchQueryChanged,
                            onRetry = onGroupsRetry,
                            onSelected = onGroupSelected,
                            onTeacherSelected = onTeacherSelected,
                            onSearchModeChanged = onSearchModeChanged,
                        )
                    },
                )
            }
            if (!state.isTeacherMode) item { shape ->
                SettingsListItem(
                    title = "Преподаватель иностранного языка",
                    subtitle = state.languageTeacher?.takeIf { it.isNotBlank() } ?: "Все преподаватели",
                    shape = shape,
                    icon = Icons.Rounded.Person,
                    onClick = onOpenLanguageTeacher,
                    accessibilityLabel = "Открыть: Преподаватель иностранного языка",
                )
            }
            item { shape ->
                SettingsListItem(
                    title = "Названия пар",
                    subtitle = "Сокращения предметов",
                    shape = shape,
                    icon = Icons.AutoMirrored.Rounded.List,
                    onClick = onOpenLessonNames,
                    accessibilityLabel = "Открыть: Названия пар",
                )
            }
            item { shape ->
                ScheduleRangeSettings(
                    weeksBack = state.scheduleWeeksBack,
                    weeksForward = state.scheduleWeeksForward,
                    onWeeksBackChanged = onScheduleWeeksBackChanged,
                    onWeeksForwardChanged = onScheduleWeeksForwardChanged,
                    shape = shape,
                )
            }
        }
        NotificationSettings(
            selectedDays = state.deadlineReminderDays,
            onSelectedDaysChanged = onDeadlineReminderDaysChanged,
            pausedUntil = state.lessonAlertsPausedUntil
                ?.takeIf { it > state.now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() },
            onPause = onLessonAlertsPause,
        )
        SettingsGroup(title = "Приложение") {
            item { shape ->
                SettingsListItem(
                    title = "Оформление",
                    subtitle = "Тема, карточки расписания и виджет",
                    shape = shape,
                    icon = Icons.Rounded.Create,
                    onClick = onOpenAppearance,
                    accessibilityLabel = "Открыть: Оформление",
                )
            }
            if (BuildConfig.DEBUG) {
                item { shape ->
                    SettingsListItem(
                        title = "Тестирование",
                        subtitle = "LiveChip, прогресс и уведомления",
                        shape = shape,
                        icon = Icons.Rounded.Build,
                        onClick = onOpenTesting,
                        accessibilityLabel = "Открыть: Тестирование",
                    )
                }
            }
            item { shape ->
                SettingsListItem(
                    title = "О приложении",
                    subtitle = "Источник и конфиденциальность",
                    shape = shape,
                    icon = Icons.Rounded.Info,
                    onClick = onOpenAbout,
                    accessibilityLabel = "Открыть: О приложении",
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsNavigationRow(title: String, subtitle: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f,
        tween(durationMillis = 100), label = "settings-press")
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Открыть: $title" }
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SettingsChevron()
        }
    }
}

@Composable
private fun ScheduleRangeSettings(
    weeksBack: Int,
    weeksForward: Int,
    onWeeksBackChanged: (Int) -> Unit,
    onWeeksForwardChanged: (Int) -> Unit,
    shape: Shape = MaterialTheme.shapes.extraLarge,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsLeadingIcon(Icons.Rounded.DateRange)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Диапазон расписания", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Какие недели загружать и показывать в общей ленте",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            WidgetDimensionSlider(
                title = "Недель назад",
                description = "Прошедшие недели до текущей",
                value = weeksBack,
                range = UserPreferences.SCHEDULE_WEEKS_RANGE,
                unit = weekUnit(weeksBack),
                onValueChanged = onWeeksBackChanged,
            )
            WidgetDimensionSlider(
                title = "Недель вперёд",
                description = "Будущие недели после текущей",
                value = weeksForward,
                range = UserPreferences.SCHEDULE_WEEKS_RANGE,
                unit = weekUnit(weeksForward),
                onValueChanged = onWeeksForwardChanged,
            )
        }
    }
}

private fun weekUnit(value: Int): String = when {
    value % 10 == 1 && value % 100 != 11 -> "неделя"
    value % 10 in 2..4 && value % 100 !in 12..14 -> "недели"
    else -> "недель"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationSettings(
    selectedDays: Set<Int>,
    onSelectedDaysChanged: (Set<Int>) -> Unit,
    pausedUntil: Long? = null,
    onPause: (LessonAlertPause?) -> Unit = {},
) {
    val context = LocalContext.current
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    fun exactAlarmMissing() = Build.VERSION.SDK_INT >= 31 && alarmManager?.canScheduleExactAlarms() == false
    // The permission is granted in system settings, so re-check whenever the user comes back.
    var needsExactAlarm by remember { mutableStateOf(exactAlarmMissing()) }
    LifecycleResumeEffect(Unit) {
        needsExactAlarm = exactAlarmMissing()
        onPauseOrDispose { }
    }
    SettingsGroup(title = "Уведомления") {
        item { shape ->
            SettingsListItem(
                title = "Системные настройки",
                subtitle = "Звук, вибрация и показ на экране блокировки",
                shape = shape,
                icon = Icons.Rounded.Notifications,
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                },
                accessibilityLabel = "Открыть: Уведомления",
            )
        }
        item { shape ->
            LessonAlertPauseItem(pausedUntil = pausedUntil, onPause = onPause, shape = shape)
        }
        if (needsExactAlarm) {
            item { shape ->
                SettingsListItem(
                    title = "Разрешить точные напоминания",
                    subtitle = "Без этого прогресс пары не запустится, пока приложение закрыто",
                    shape = shape,
                    icon = Icons.Rounded.Warning,
                    emphasizedIcon = true,
                    subtitleColor = MaterialTheme.colorScheme.error,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData("package:${context.packageName}".toUri()),
                            )
                        }
                    },
                )
            }
        }
        item { shape ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsLeadingIcon(Icons.Rounded.DateRange)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Напоминания о сроках", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Для активных задач, целей и дедлайнов с датой. Приходят в 09:00.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DeadlineReminder.entries.forEach { reminder ->
                            val selected = reminder.daysBefore in selectedDays
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    onSelectedDaysChanged(
                                        if (selected) selectedDays - reminder.daysBefore
                                        else selectedDays + reminder.daysBefore,
                                    )
                                },
                                label = { Text(reminder.title) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    if (selectedDays.isEmpty()) {
                        Text(
                            "Напоминания о сроках выключены",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** Mutes lesson reminders and the live notification while the user skips classes. */
@Composable
private fun LessonAlertPauseItem(
    pausedUntil: Long?,
    onPause: (LessonAlertPause?) -> Unit,
    shape: RoundedCornerShape,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val lastMutedDay = pausedUntil?.takeIf { it != LessonAlertPause.FOREVER_MILLIS }?.let {
        java.time.Instant.ofEpochMilli(it - 1).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    val subtitle = when {
        pausedUntil == LessonAlertPause.FOREVER_MILLIS -> "Выключены, пока не включишь снова"
        lastMutedDay == null -> "Отключить на время или навсегда"
        lastMutedDay == LocalDate.now() -> "Выключены до конца дня"
        else -> "Выключены по ${lastMutedDay.format(dayMonthFormatter)} включительно"
    }
    SettingsListItem(
        title = "Напоминания о парах",
        subtitle = subtitle,
        shape = shape,
        icon = if (pausedUntil != null) Icons.Rounded.Close else Icons.Rounded.DateRange,
        emphasizedIcon = pausedUntil != null,
        subtitleColor = if (pausedUntil != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        trailing = {
            if (pausedUntil != null) {
                TextButton(onClick = { onPause(null) }) { Text("Включить") }
            } else {
                Box {
                    TextButton(onClick = { menuOpen = true }) { Text("Выключить") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        LessonAlertPause.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.title) },
                                onClick = {
                                    menuOpen = false
                                    onPause(option)
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
internal fun NotificationTestingScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsPage(title = "Тестирование", onBack = onBack, modifier = modifier) {
        Text(
            "Запусти сценарий, сверни приложение и проверь карточку в шторке, LiveChip в строке состояния, таймер и линию прогресса.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Сценарии уведомлений", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Каждый новый сценарий заменяет предыдущее тестовое уведомление.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = { LessonAlerts.testUpcoming(context) },
            modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        ) { Text("До начала — 15 минут") }
        OutlinedButton(
            onClick = { LessonAlerts.testCurrentHourFifteen(context) },
            modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        ) { Text("Идёт пара — осталось 1 ч 15 мин") }
        SettingsNavigationRow("Системные Live Updates", "Разрешение на LiveChip") {
            LessonAlerts.openLiveUpdateSettings(context)
        }
        TextButton(
            onClick = { LessonAlerts.clearTest(context) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Убрать тестовое уведомление") }
        Text(
            "Этот раздел отображается только в тестовой сборке.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun AboutScreen(
    updateState: AppUpdateState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: (AppUpdateState.ReadyToInstall) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (updateState == AppUpdateState.Idle) onCheckForUpdates()
    }
    SettingsPage(title = "О приложении", onBack = onBack, modifier = modifier) {
        Text(
            stringResource(R.string.app_full_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Версия ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        UpdateStatus(
            state = updateState,
            onCheck = onCheckForUpdates,
            onDownload = onDownloadUpdate,
            onInstall = onInstallUpdate,
            onOpenRelease = { url ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                }
            },
        )
        SettingsNavigationRow("GitHub", "Исходный код и все релизы") {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, "https://github.com/neckmnull/Lecturial-2".toUri()),
                )
            }
        }
        SettingsNavigationRow("Telegram", "Официальный канал @lecturial_2") {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, "https://t.me/lecturial_2".toUri()),
                )
            }
        }
        SettingsNavigationRow("Источник расписания", "Университет «Сириус»") {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, "https://schedule.siriusuniversity.ru/".toUri()))
            }
        }
        SettingsNavigationRow("Политика конфиденциальности", "Данные и их хранение", onOpenPrivacy)
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Shown instead of the app while the installed version is older than the releases allow.
 * Only updating gets past it; the download and install work like in Settings → About.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RequiredUpdateScreen(
    version: String,
    updateState: AppUpdateState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: (AppUpdateState.ReadyToInstall) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    // Fetch the release straight away, so the download button is there without a tap.
    LaunchedEffect(updateState) {
        if (updateState == AppUpdateState.Idle) onCheck()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text("Нужно обновить приложение", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Версия $version обязательна: без неё Lecturial 2 может работать неправильно, поэтому " +
                "эта версия больше не открывается. Обновление займёт минуту, настройки и планы сохранятся.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        UpdateStatus(
            state = updateState,
            onCheck = onCheck,
            onDownload = onDownload,
            onInstall = onInstall,
            onOpenRelease = { uriHandler.openUri(it) },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateStatus(
    state: AppUpdateState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: (AppUpdateState.ReadyToInstall) -> Unit,
    onOpenRelease: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Обновления", style = MaterialTheme.typography.titleMedium)
            when (state) {
                AppUpdateState.Idle -> {
                    Text(
                        "Проверка стабильных релизов на GitHub.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onCheck) { Text("Проверить") }
                }
                AppUpdateState.Checking -> Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LoadingIndicator(modifier = Modifier.size(24.dp))
                    Text("Проверяем GitHub…", style = MaterialTheme.typography.bodyMedium)
                }
                is AppUpdateState.UpToDate -> Text(
                    "Установлена актуальная версия ${state.latestVersion}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is AppUpdateState.Available -> {
                    Text(
                        "Доступна версия ${state.version}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Button(onClick = onDownload) {
                        Text("Скачать и установить")
                    }
                    TextButton(onClick = { onOpenRelease(state.releaseUrl) }) { Text("Открыть релиз на GitHub") }
                }
                is AppUpdateState.Downloading -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LoadingIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            state.progressPercent?.let { "Скачиваем обновление… $it%" }
                                ?: "Скачиваем обновление…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                is AppUpdateState.ReadyToInstall -> {
                    Text(
                        "Версия ${state.version} скачана. Android попросит подтвердить установку.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Button(onClick = { onInstall(state) }) { Text("Установить") }
                }
                is AppUpdateState.DownloadError -> {
                    Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onDownload) { Text("Повторить скачивание") }
                    TextButton(onClick = { onOpenRelease(state.update.releaseUrl) }) { Text("Открыть релиз на GitHub") }
                }
                is AppUpdateState.InstallationError -> {
                    Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { onInstall(state.update) }) { Text("Повторить установку") }
                    TextButton(onClick = { onOpenRelease(state.update.releaseUrl) }) { Text("Открыть релиз на GitHub") }
                }
                is AppUpdateState.Error -> {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onCheck) { Text("Повторить") }
                }
            }
        }
    }
}

@Composable
internal fun PrivacyScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    SettingsPage(title = "Конфиденциальность", onBack = onBack, modifier = modifier, spacing = 16.dp) {
        Text(
            "Lecturial 2 не является официальным приложением Университета «Сириус».",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        PrivacySection(
            "Какие данные используются",
            "Приложение не создаёт аккаунты, не показывает рекламу и не использует аналитические SDK. Оно хранит выбранную учебную группу, настройки, сокращения названий предметов и кеш расписания.",
        )
        PrivacySection(
            "Где хранятся данные",
            "Настройки и кеш хранятся только на устройстве. Для загрузки расписания выбранная группа передаётся серверу schedule.siriusuniversity.ru по защищённому HTTPS-соединению.",
        )
        PrivacySection(
            "Удаление данных",
            "Все локальные данные можно удалить в системных настройках приложения или полностью удалив Lecturial 2 с устройства.",
        )
    }
}

@Composable
private fun PrivacySection(title: String, text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LessonNamesScreen(
    modifier: Modifier = Modifier,
    names: List<String>,
    aliases: Map<String, String>,
    onAliasChanged: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPage(title = "Названия пар", onBack = onBack, modifier = modifier, spacing = 16.dp) {
        Text(
            "Подставьте короткие имена — они появятся в расписании, виджете и уведомлениях.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (names.isEmpty()) {
            Text(
                "Сначала откройте расписание, чтобы подтянуть список предметов.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // Each subject on its own opaque tile, its full name in large type above the field.
            // As the field's floating label the name shrank to a cut-off caption, and the
            // transparent outlined fields let the wallpaper lines run through the text.
            Column(verticalArrangement = Arrangement.spacedBy(SettingsGroupGap)) {
                names.forEachIndexed { index, original ->
                    val alias = aliases[original]
                    var value by remember(original, alias) { mutableStateOf(alias ?: original) }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = settingsGroupShape(index, names.size),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = if (alias != null) 4.dp else 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(original, style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = value,
                                onValueChange = { next ->
                                    value = next
                                    onAliasChanged(original, next)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Короткое имя") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            )
                            if (alias != null) {
                                TextButton(
                                    onClick = {
                                        value = original
                                        onAliasChanged(original, original)
                                    },
                                    modifier = Modifier.align(Alignment.End),
                                ) {
                                    Text("Сбросить")
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
