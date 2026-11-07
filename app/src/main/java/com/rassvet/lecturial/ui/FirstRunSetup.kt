package com.rassvet.lecturial.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.rassvet.lecturial.LecturialApplication
import com.rassvet.lecturial.notify.LessonAlerts
import com.rassvet.lecturial.model.AppBackgroundStyle
import com.rassvet.lecturial.model.AppDisplayOptions
import com.rassvet.lecturial.model.AppTheme
import com.rassvet.lecturial.model.TeacherInfo
import com.rassvet.lecturial.ui.theme.defaultThemePalette
import com.rassvet.lecturial.ui.theme.dynamicThemePalette
import com.rassvet.lecturial.ui.theme.supportsDynamicTheme
import androidx.compose.runtime.mutableIntStateOf
import com.rassvet.lecturial.model.ScheduleLayout
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

private enum class SetupStep { Welcome, Group, Account, Notifications, Look, Layout, Done }

/** Whether the first-launch setup was finished, or skipped by someone who used the app before it. */
internal object FirstRun {
    private const val PREFERENCES = "first_run"
    private const val DONE = "done"

    fun isDone(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(DONE, false)

    fun markDone(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit { putBoolean(DONE, true) }
    }

    /** Bumped from Settings to walk through the setup once more. */
    var replays by mutableIntStateOf(0)
        private set

    fun replay() {
        replays++
    }
}

/**
 * The first launch: who the user is and their group, the personal account, notifications and
 * the look, one calm step at a time. Optional steps can be skipped; everything stays in Settings.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FirstRunSetup(
    state: ScheduleUiState,
    onGroupSearchQueryChanged: (String) -> Unit,
    onGroupsRetry: () -> Unit,
    onGroupSelected: (String) -> Unit,
    onTeacherSelected: (TeacherInfo) -> Unit,
    onSearchModeChanged: (ScheduleSearchMode) -> Unit,
    onLanguageTeacherSelected: (String?) -> Unit,
    onRetry: () -> Unit,
    onAppThemeSelected: (AppTheme) -> Unit,
    onDarkThemeChanged: (Boolean) -> Unit,
    onAppDisplayOptionsChanged: (AppDisplayOptions) -> Unit,
    onFinished: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(SetupStep.Welcome) }
    // Set when a group or teacher is picked here, so coming back to this step does not skip it.
    var picking by rememberSaveable { mutableStateOf(false) }
    var regroup by rememberSaveable { mutableStateOf(false) }
    val steps = SetupStep.entries.filter { it != SetupStep.Account || !state.isTeacherMode }
    val chosen = state.selectedGroup != null && !state.choosingLanguageTeacher
    fun next() {
        steps.getOrNull(steps.indexOf(step) + 1)?.let { step = it } ?: onFinished()
    }
    fun back() {
        if (step == SetupStep.Group && state.choosingLanguageTeacher && !regroup) {
            regroup = true
        } else {
            steps.getOrNull(steps.indexOf(step) - 1)?.let { step = it }
        }
    }
    BackHandler(enabled = step != SetupStep.Welcome, onBack = ::back)
    // The group step ends by itself once the schedule knows what to show.
    LaunchedEffect(picking, chosen) {
        if (picking && chosen && step == SetupStep.Group) {
            picking = false
            next()
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        val progress by animateFloatAsState(
            (steps.indexOf(step) + 1f) / steps.size,
            MaterialTheme.motionScheme.slowSpatialSpec(),
            label = "setup-progress",
        )
        if (step != SetupStep.Welcome) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = ::back) { Text("Назад") }
                LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                val optional = step == SetupStep.Account || step == SetupStep.Notifications || step == SetupStep.Look ||
                    step == SetupStep.Layout
                val forward = optional || (step == SetupStep.Group && chosen)
                TextButton(onClick = ::next, enabled = forward) {
                    Text(if (optional) "Пропустить" else "Далее", modifier = Modifier.alpha(if (forward) 1f else 0f))
                }
            }
        }
        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                (slideInHorizontally(screenSlideSpec()) { direction * it / 5 } + fadeIn(screenFadeSpec())) togetherWith
                    (slideOutHorizontally(screenSlideSpec()) { -direction * it / 8 } + fadeOut(screenFadeSpec()))
            },
            label = "setup-step",
        ) { current ->
            when (current) {
                SetupStep.Welcome -> WelcomeStep(onStart = ::next)
                SetupStep.Group -> if (state.choosingLanguageTeacher && state.selectedGroup != null && !regroup) {
                    LanguageTeacherSelectionScreen(
                        state = state,
                        onSelected = onLanguageTeacherSelected,
                        onRetry = onRetry,
                        onBack = { regroup = true },
                    )
                } else {
                    GroupSelectionScreen(
                        state = state,
                        onSearchQueryChanged = onGroupSearchQueryChanged,
                        onRetry = onGroupsRetry,
                        onGroupSelected = { group ->
                            picking = true
                            regroup = false
                            onGroupSelected(group)
                        },
                        onTeacherSelected = { teacher ->
                            picking = true
                            regroup = false
                            onTeacherSelected(teacher)
                        },
                        onSearchModeChanged = onSearchModeChanged,
                    )
                }
                SetupStep.Account -> AccountStep(onDone = ::next)
                SetupStep.Notifications -> NotificationsStep(onNext = ::next)
                SetupStep.Look -> LookStep(state, onAppThemeSelected, onDarkThemeChanged, onAppDisplayOptionsChanged, onNext = ::next)
                SetupStep.Layout -> LayoutStep(state, onAppDisplayOptionsChanged, onNext = ::next)
                SetupStep.Done -> DoneStep(teacher = state.isTeacherMode, onFinish = onFinished)
            }
        }
    }
}

@Composable
private fun StepPage(
    title: String,
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    content: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.revealOnAppear(0))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.revealOnAppear(1))
            content()
        }
        Button(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        ) { Text(actionLabel) }
    }
}

@Composable
private fun WelcomeStep(onStart: () -> Unit) {
    StepPage(
        title = "Lecturial 2",
        text = "Расписание Университета «Сириус» и всё, что вокруг него, в одном приложении. Настроим за минуту.",
        actionLabel = "Начать",
        onAction = onStart,
    ) {
        Spacer(Modifier.height(8.dp))
        FeatureRow(Icons.Rounded.DateRange, "Расписание и виджет", "Пары на неделю вперёд, офлайн, с напоминаниями и живым уведомлением", 2)
        FeatureRow(Icons.Rounded.Star, "Оценки из личного кабинета", "Баллы и посещаемость по каждой паре, сессия, уведомления и новости", 3)
        FeatureRow(Icons.Rounded.CheckCircle, "Планы и календари", "Задачи и дедлайны, синхронизация с Google Календарём и Outlook", 4)
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, text: String, order: Int) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().revealOnAppear(order)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AccountStep(onDone: () -> Unit) {
    val lks = (LocalContext.current.applicationContext as LecturialApplication).lks
    val lksState by lks.state.collectAsState()
    var signingIn by rememberSaveable { mutableStateOf(false) }
    if (signingIn) {
        LksLoginScreen(
            onSignedIn = { cookies ->
                lks.connect(cookies)
                signingIn = false
                onDone()
            },
            onClose = { signingIn = false },
        )
        return
    }
    StepPage(
        title = "Личный кабинет",
        text = "Подключите личный кабинет студента — оценки и посещаемость появятся прямо на парах, " +
            "а новые оценки и уведомления будут приходить пушами.",
        actionLabel = if (lksState.connected) "Далее" else "Войти в личный кабинет",
        onAction = { if (lksState.connected) onDone() else signingIn = true },
    ) {
        Caption("Вход на странице lks.siriusuniversity.ru: пароль видит только сайт университета. Подключить можно и позже — во вкладке «Учёба».")
    }
}

@Composable
private fun NotificationsStep(onNext: () -> Unit) {
    val context = LocalContext.current
    fun granted() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var allowed by rememberSaveable { mutableStateOf(granted()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = it
        LessonAlerts.sync(context)
    }
    StepPage(
        title = "Уведомления",
        text = "Напомним о паре заранее, покажем, сколько осталось до конца, и сообщим о новых оценках и обновлениях.",
        actionLabel = if (allowed || Build.VERSION.SDK_INT < 33) "Далее" else "Разрешить уведомления",
        onAction = {
            if (allowed || Build.VERSION.SDK_INT < 33) onNext() else permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
    ) {
        FeatureRow(Icons.Rounded.Notifications, if (allowed) "Уведомления разрешены" else "Уведомления выключены", "Что и когда приходит, настраивается в Настройках", 2)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookStep(
    state: ScheduleUiState,
    onAppThemeSelected: (AppTheme) -> Unit,
    onDarkThemeChanged: (Boolean) -> Unit,
    onAppDisplayOptionsChanged: (AppDisplayOptions) -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    StepPage(
        title = "Оформление",
        text = "Палитра, светлая или тёмная тема и фон. Свои цвета, живые обои и вид карточек — в Настройках → Оформление.",
        actionLabel = "Далее",
        onAction = onNext,
    ) {
        SettingsSection("Палитра") {
            ConnectedChoice(listOf(false, true), state.darkTheme, { if (it) "Тёмная" else "Светлая" }, onDarkThemeChanged)
            val themes = AppTheme.entries.filter { it != AppTheme.Custom && (it != AppTheme.Dynamic || supportsDynamicTheme) }
            ThemeSwatchRow(
                themes.map { theme ->
                    val palette = (if (theme == AppTheme.Dynamic) dynamicThemePalette(context, state.darkTheme) else null)
                        ?: defaultThemePalette(theme, state.darkTheme)
                    ThemeSwatch(
                        key = theme.storageId,
                        name = appThemeTitle(theme),
                        primary = Color(palette.primary),
                        secondary = Color(palette.secondary),
                        tertiary = Color(palette.tertiary),
                        selected = state.appTheme == theme,
                        onClick = { onAppThemeSelected(theme) },
                    )
                },
            )
        }
        SettingsSection("Фон") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Drawing a pattern needs the editor in Settings, so the setup offers the ready ones.
                AppBackgroundStyle.entries.filter { it != AppBackgroundStyle.Drawn }.forEach { style ->
                    FilterChip(
                        selected = state.appDisplayOptions.backgroundStyle == style,
                        onClick = { onAppDisplayOptionsChanged(state.appDisplayOptions.copy(backgroundStyle = style)) },
                        label = { Text(style.title) },
                    )
                }
            }
        }
    }
}

/** The two layouts side by side as small pictures of the schedule, so the difference is plain to see. */
@Composable
private fun LayoutStep(
    state: ScheduleUiState,
    onAppDisplayOptionsChanged: (AppDisplayOptions) -> Unit,
    onNext: () -> Unit,
) {
    val selected = state.appDisplayOptions.scheduleLayout
    StepPage(
        title = "Вид расписания",
        text = "Как удобнее смотреть пары? Поменять можно в любой момент: Настройки → Оформление → Вид расписания.",
        actionLabel = "Далее",
        onAction = onNext,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.revealOnAppear(2)) {
            ScheduleLayout.entries.forEach { layout ->
                LayoutOption(
                    layout = layout,
                    selected = layout == selected,
                    onClick = { onAppDisplayOptionsChanged(state.appDisplayOptions.copy(scheduleLayout = layout)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Caption(
            when (selected) {
                ScheduleLayout.Feed -> "Лентой: все дни одним длинным списком, неделя за неделей. Удобно пролистать вперёд и увидеть всё сразу."
                ScheduleLayout.Days -> "По дням: сверху полоса недели, под ней пары одного дня. Дни листаются свайпом влево и вправо, " +
                    "на каждом дне есть «План дня»."
            },
        )
    }
}

@Composable
private fun LayoutOption(layout: ScheduleLayout, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val border by animateDpAsState(if (selected) 2.dp else 1.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "layout-border")
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(border, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.semantics { this.selected = selected },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().height(220.dp)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (layout) {
                        ScheduleLayout.Feed -> {
                            MockLine(0.55f, MaterialTheme.colorScheme.primary)
                            MockLesson()
                            MockLesson()
                            MockLine(0.45f, MaterialTheme.colorScheme.primary)
                            MockLesson()
                            MockLesson()
                            MockLine(0.5f, MaterialTheme.colorScheme.primary)
                            MockLesson()
                        }
                        ScheduleLayout.Days -> {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                repeat(6) { index ->
                                    Box(
                                        Modifier
                                            .size(13.dp)
                                            .background(
                                                if (index == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                CircleShape,
                                            ),
                                    )
                                }
                            }
                            MockLine(0.5f, MaterialTheme.colorScheme.primary)
                            MockLesson()
                            MockLesson()
                            MockLesson()
                            Spacer(Modifier.weight(1f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                Text("←  свайп  →", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RadioButton(selected = selected, onClick = null)
                Text(layout.title, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** A heading or a line of text in the layout pictures. */
@Composable
private fun MockLine(width: Float, color: Color) {
    Box(Modifier.fillMaxWidth(width).height(8.dp).background(color.copy(alpha = 0.7f), RoundedCornerShape(4.dp)))
}

/** A lesson card in the layout pictures: the time, the colour stripe and two lines. */
@Composable
private fun MockLesson() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(18.dp).background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(2.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            MockLine(0.9f, MaterialTheme.colorScheme.onSurfaceVariant)
            MockLine(0.5f, MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun DoneStep(teacher: Boolean, onFinish: () -> Unit) {
    StepPage(
        title = "Всё готово",
        text = "Ещё несколько возможностей, которые стоит знать:",
        actionLabel = "Открыть расписание",
        onAction = onFinish,
    ) {
        FeatureRow(Icons.Rounded.DateRange, "Виджет «Сегодня»", "Добавьте его на рабочий стол — нажатие на пару откроет её в приложении", 2)
        if (teacher) {
            FeatureRow(Icons.Rounded.Star, "Статистика", "Нагрузка преподавателей и групп, общие окна и свободные аудитории", 3)
        } else {
            FeatureRow(Icons.Rounded.Star, "Учёба", "Оценки, сессия, уведомления и новости из личного кабинета", 3)
        }
        if (!teacher) {
            FeatureRow(Icons.Rounded.DateRange, "План дня", "Во вкладке «Планы»: домашка, дедлайны и свободное время между парами", 4)
        }
        FeatureRow(Icons.Rounded.CheckCircle, "Календари", "Настройки → Google Календарь или Outlook: пары и планы в вашем календаре", 5)
        Spacer(Modifier.width(1.dp))
    }
}
