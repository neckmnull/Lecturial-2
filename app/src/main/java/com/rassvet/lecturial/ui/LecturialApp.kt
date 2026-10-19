package com.rassvet.lecturial.ui

import androidx.compose.material.icons.rounded.Search
import com.rassvet.lecturial.model.TeacherInfo
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import com.rassvet.lecturial.model.LessonAlertPause
import java.time.ZoneId
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.RowScope
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rassvet.lecturial.R
import com.rassvet.lecturial.BuildConfig
import com.rassvet.lecturial.data.local.UserPreferences
import com.rassvet.lecturial.notify.LessonAlerts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.foundation.Image
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.app.AlarmManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rassvet.lecturial.model.LessonLiveState
import com.rassvet.lecturial.model.AppBackgroundStyle
import com.rassvet.lecturial.model.ScheduleDay
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.ScheduleWeek
import com.rassvet.lecturial.model.displayAddress
import com.rassvet.lecturial.model.displayComment
import com.rassvet.lecturial.model.displayEventType
import com.rassvet.lecturial.model.liveLessonState
import com.rassvet.lecturial.model.shortenTeachers
import com.rassvet.lecturial.model.renamedDiscipline
import com.rassvet.lecturial.model.uniqueDisciplines
import com.rassvet.lecturial.model.weekRangeLabel
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.rassvet.lecturial.model.forLanguageTeacher
import com.rassvet.lecturial.model.isForeignLanguage
import com.rassvet.lecturial.model.WidgetStripePalette
import com.rassvet.lecturial.model.WidgetFontStyles
import com.rassvet.lecturial.model.AppTheme
import com.rassvet.lecturial.model.AppDisplayOptions
import com.rassvet.lecturial.model.AppUpdateState
import com.rassvet.lecturial.model.CustomAppTheme
import com.rassvet.lecturial.model.BuiltInThemeOverride
import com.rassvet.lecturial.model.ThemePalette
import com.rassvet.lecturial.model.StudyPlan
import com.rassvet.lecturial.model.DeadlineReminder
import com.rassvet.lecturial.model.occursOn
import com.rassvet.lecturial.model.occurrenceOn
import com.rassvet.lecturial.model.toPalette
import com.rassvet.lecturial.ui.theme.defaultThemePalette
import com.rassvet.lecturial.model.parseThemeColor
import com.rassvet.lecturial.model.themeColorHex
import com.rassvet.lecturial.ui.theme.LecturialTheme
import java.text.DateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

private val ru = Locale.forLanguageTag("ru")
private val fullWeekdayFormatter = DateTimeFormatter.ofPattern("EEEE", ru)
private val dayMonthFormatter = DateTimeFormatter.ofPattern("d MMMM", ru)
private val LessonTimeColumnWidth = 68.dp
private const val RestGapMinutes = 40
private const val MaxSliderTickIntervals = 32

// Expressive spatial/effects springs; slightly more damped for full-screen slides.
private fun <T> screenSlideSpec() = spring<T>(dampingRatio = 0.9f, stiffness = 380f)

private fun <T> screenFadeSpec() = spring<T>(dampingRatio = 1f, stiffness = 1600f)

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
private val WallpaperGradientColors = listOf(
    0xFF4F7DFF, 0xFF62D5E8, 0xFF67D391, 0xFFFF897D,
    0xFFB69DF8, 0xFFFF8FB3, 0xFFE8B84A, 0xFF172554,
    0xFF0F766E, 0xFF166534, 0xFF9F1239, 0xFF581C87,
)

private enum class SettingsSection { LanguageTeacher, Appearance, Widget, Names, Testing, About, Privacy }
private enum class MainSection { Schedule, Plans, Statistics, Settings }
private enum class AppearanceSection { Settings, BuiltInEditor, CustomEditor }
private enum class PreviewLessonType { Lecture, Practice }

private fun ScheduleUiState.loadedScheduleWeeks(): List<ScheduleWeek> =
    scheduleWeeks.ifEmpty { listOfNotNull(week, nextWeek) }
        .distinctBy(ScheduleWeek::weekStart)
        .sortedBy(ScheduleWeek::weekStart)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LecturialApp(
    state: ScheduleUiState,
    openPlanId: String? = null,
    openPlanRequestKey: Long = 0L,
    openUpdatesKey: Long = 0L,
    onOpenPlanConsumed: (Long) -> Unit = {},
    onGroupSelected: (String) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onMoveWeek: (Long) -> Unit,
    onToday: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLectureStripeColorSelected: (Int) -> Unit = {},
    onPracticeStripeColorSelected: (Int) -> Unit = {},
    onWidgetShowTimeChanged: (Boolean) -> Unit = {},
    onWidgetShowStripeChanged: (Boolean) -> Unit = {},
    onWidgetShowClassroomChanged: (Boolean) -> Unit = {},
    onWidgetShowEndTimeChanged: (Boolean) -> Unit = {},
    onWidgetWrapTitleChanged: (Boolean) -> Unit = {},
    onWidgetOuterPaddingChanged: (Int) -> Unit = {},
    onWidgetHorizontalPaddingChanged: (Int) -> Unit = {},
    onWidgetVerticalPaddingChanged: (Int) -> Unit = {},
    onWidgetItemSpacingChanged: (Int) -> Unit = {},
    onWidgetTitleTextSizeChanged: (Int) -> Unit = {},
    onWidgetSecondaryTextSizeChanged: (Int) -> Unit = {},
    onWidgetFontStyleChanged: (String) -> Unit = {},
    onWidgetLetterSpacingChanged: (Int) -> Unit = {},
    onWidgetTitleMaxLinesChanged: (Int) -> Unit = {},
    onLessonAliasChanged: (String, String) -> Unit = { _, _ -> },
    onLanguageTeacherSelected: (String?) -> Unit = {},
    onGroupSearchQueryChanged: (String) -> Unit = {},
    onGroupsRetry: () -> Unit = {},
    onAppThemeSelected: (AppTheme) -> Unit = {},
    onDarkThemeChanged: (Boolean) -> Unit = {},
    onBuiltInThemeSaved: (AppTheme, ThemePalette) -> Unit = { _, _ -> },
    onBuiltInThemeReset: (AppTheme) -> Unit = {},
    onCustomThemeSelected: (String) -> Unit = {},
    onCustomThemeSaved: (CustomAppTheme) -> Unit = {},
    onCustomThemeDeleted: (String) -> Unit = {},
    onAppDisplayOptionsChanged: (AppDisplayOptions) -> Unit = {},
    onAppDisplayOptionsReset: () -> Unit = {},
    onStudyPlanSaved: (StudyPlan) -> Unit = {},
    onStudyPlanCompletedChanged: (String, Boolean) -> Unit = { _, _ -> },
    onStudyPlanDeleted: (String) -> Unit = {},
    onDeadlineReminderDaysChanged: (Set<Int>) -> Unit = {},
    onLessonAlertsPause: (LessonAlertPause?) -> Unit = {},
    onScheduleWeeksBackChanged: (Int) -> Unit = {},
    onScheduleWeeksForwardChanged: (Int) -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onInstallUpdate: (AppUpdateState.ReadyToInstall) -> Unit = {},
    onTeacherSelected: (TeacherInfo) -> Unit = {},
    onSearchModeChanged: (ScheduleSearchMode) -> Unit = {},
) {
    var returnToGroups by rememberSaveable { mutableStateOf(false) }
    val stage = when {
        state.isStarting -> 0
        state.selectedGroup == null || returnToGroups -> 1
        state.choosingLanguageTeacher -> 2
        else -> 3
    }
    MaterialYouBackground(
        modifier = Modifier.fillMaxSize(),
        options = state.appDisplayOptions,
    ) {
        AnimatedContent(
            modifier = Modifier.fillMaxSize().clipToBounds(),
            targetState = stage,
            transitionSpec = {
                val direction = if (targetState >= initialState) 1 else -1
                (slideInHorizontally(screenSlideSpec()) { direction * it / 5 } + fadeIn(screenFadeSpec())) togetherWith
                    (slideOutHorizontally(screenSlideSpec()) { -direction * it / 8 } + fadeOut(screenFadeSpec()))
            },
            label = "app-state",
        ) { screen ->
            when (screen) {
                0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                1 -> GroupSelectionScreen(
                    state = state,
                    onSearchQueryChanged = onGroupSearchQueryChanged,
                    onRetry = onGroupsRetry,
                    onGroupSelected = { group ->
                        returnToGroups = false
                        onGroupSelected(group)
                    },
                    onTeacherSelected = { teacher ->
                        returnToGroups = false
                        onTeacherSelected(teacher)
                    },
                    onSearchModeChanged = onSearchModeChanged,
                )
                2 -> LanguageTeacherSelectionScreen(
                    state = state,
                    onSelected = onLanguageTeacherSelected,
                    onRetry = onRetry,
                    onBack = { returnToGroups = true },
                )
                else -> ScheduleScreen(
                    state.copy(week = state.week?.forLanguageTeacher(state.languageTeacher),
                        nextWeek = state.nextWeek?.forLanguageTeacher(state.languageTeacher),
                        scheduleWeeks = state.scheduleWeeks.map { it.forLanguageTeacher(state.languageTeacher) },
                        events = state.events.filter { com.rassvet.lecturial.model.matchesLanguageTeacher(it.discipline, it.teachers, state.languageTeacher) }),
                    openPlanId,
                    openPlanRequestKey,
                    onOpenPlanConsumed,
                    onGroupSelected,
                    onGroupSearchQueryChanged,
                    onGroupsRetry,
                    onDateSelected,
                    onMoveWeek,
                    onToday,
                    onRefresh,
                    onRetry,
                    onLectureStripeColorSelected,
                    onPracticeStripeColorSelected,
                    onWidgetShowTimeChanged,
                    onWidgetShowStripeChanged,
                    onWidgetShowClassroomChanged,
                    onWidgetShowEndTimeChanged,
                    onWidgetWrapTitleChanged,
                    onWidgetOuterPaddingChanged,
                    onWidgetHorizontalPaddingChanged,
                    onWidgetVerticalPaddingChanged,
                    onWidgetItemSpacingChanged,
                    onWidgetTitleTextSizeChanged,
                    onWidgetSecondaryTextSizeChanged,
                    onWidgetFontStyleChanged,
                    onWidgetLetterSpacingChanged,
                    onWidgetTitleMaxLinesChanged,
                    onLessonAliasChanged,
                    onLanguageTeacherSelected,
                    onAppThemeSelected,
                    onDarkThemeChanged,
                    onBuiltInThemeSaved,
                    onBuiltInThemeReset,
                    onCustomThemeSelected,
                    onCustomThemeSaved,
                    onCustomThemeDeleted,
                    onAppDisplayOptionsChanged,
                    onAppDisplayOptionsReset,
                    onStudyPlanSaved,
                    onStudyPlanCompletedChanged,
                    onStudyPlanDeleted,
                    onDeadlineReminderDaysChanged,
                    onLessonAlertsPause,
                    onScheduleWeeksBackChanged,
                    onScheduleWeeksForwardChanged,
                    onCheckForUpdates,
                    onDownloadUpdate,
                    onInstallUpdate,
                    onTeacherSelected = onTeacherSelected,
                    onSearchModeChanged = onSearchModeChanged,
                    openUpdatesKey = openUpdatesKey,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LanguageTeacherSelectionScreen(
    state: ScheduleUiState,
    onSelected: (String?) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    backContentDescription: String = "Назад к выбору группы",
) {
    var selectedTeacher by rememberSaveable(state.selectedGroup) { mutableStateOf(state.languageTeacher) }
    val teachers = state.loadedScheduleWeeks().flatMap { it.days }
        .flatMap { it.events }.filter { isForeignLanguage(it.discipline) }
        .flatMap { it.teachers }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    val loading = state.isLoading || state.isRefreshing || (state.loadedScheduleWeeks().isEmpty() && state.errorMessage == null)
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        androidx.compose.material3.FilledTonalButton(
            onClick = onBack,
            modifier = Modifier.height(48.dp).semantics { contentDescription = backContentDescription },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Назад", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Шаг 2 из 2 · ${state.selectedGroup.orEmpty()}", color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge)
            Text("Преподаватель иностранного языка", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold)
            Text("Выберите своего преподавателя, чтобы в расписании остались только ваши занятия иностранного языка.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LoadingIndicator(Modifier.size(40.dp))
                        Text("Загружаем преподавателей…")
                    }
                }
                teachers.forEach { teacher ->
                    TeacherChoiceCard(teacher, selectedTeacher == teacher) { selectedTeacher = teacher }
                }
                if (!loading && teachers.isEmpty()) {
                    Text(state.errorMessage ?: "В выбранном диапазоне расписания пока нет преподавателей иностранного языка.")
                }
                if (state.errorMessage != null) {
                    TextButton(onClick = onRetry) { Text("Повторить загрузку") }
                }
                TeacherChoiceCard("Все преподаватели", selectedTeacher == "") { selectedTeacher = "" }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSelected(selectedTeacher?.takeIf { it.isNotBlank() }) },
            enabled = selectedTeacher != null,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Продолжить", style = MaterialTheme.typography.titleMedium) }
        Text("Выбор можно изменить в настройках.", Modifier.fillMaxWidth().padding(top = 10.dp),
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TeacherChoiceCard(title: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f,
        tween(durationMillis = 100), label = "teacher-scale")
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        tween(durationMillis = 140), label = "teacher-color")
    Surface(onClick = onClick, interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(14.dp), color = color,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.titleMedium)
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GroupSelectionScreen(
    state: ScheduleUiState,
    onSearchQueryChanged: (String) -> Unit,
    onRetry: () -> Unit,
    onGroupSelected: (String) -> Unit,
    onTeacherSelected: (TeacherInfo) -> Unit = {},
    onSearchModeChanged: (ScheduleSearchMode) -> Unit = {},
) {
    val teacherSearch = state.searchMode == ScheduleSearchMode.Teacher
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var selectedTeacher by remember { mutableStateOf<TeacherInfo?>(null) }
    LaunchedEffect(state.availableGroups) {
        if (selectedGroup !in state.availableGroups) selectedGroup = null
    }
    LaunchedEffect(state.availableTeachers) {
        if (selectedTeacher !in state.availableTeachers) selectedTeacher = null
    }
    val hasSelection = if (teacherSearch) selectedTeacher != null else selectedGroup != null
    val nothingLoaded = if (teacherSearch) state.availableTeachers.isEmpty() else state.availableGroups.isEmpty()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
        Image(
            painter = painterResource(R.drawable.onboarding_sirius_logo),
            contentDescription = "Логотип Университета Сириус",
            modifier = Modifier
                .width(190.dp)
                .height(70.dp)
                .offset(x = (-6).dp),
        )
        Spacer(Modifier.height(2.dp))
        Text("Привет!", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            if (teacherSearch) "Найдите себя в списке" else "Из какой ты группы?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (teacherSearch) {
                "Покажем ваши пары во всех группах и напомним о них"
            } else {
                "Найди свою группу — дальше выбери преподавателя иностранного языка"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        ScheduleSearchModeButtons(mode = state.searchMode, onModeChanged = onSearchModeChanged)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.groupSearchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (teacherSearch) "Поиск преподавателя" else "Поиск группы") },
            placeholder = { Text(if (teacherSearch) "Например, Иванов" else "Например, ИОП-ИТ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        Spacer(Modifier.height(12.dp))
        when {
            state.isGroupsLoading && nothingLoaded ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .semantics { contentDescription = "Загрузка групп" },
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }
            state.groupsError != null && nothingLoaded -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.groupsError, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                TextButton(onClick = onRetry) { Text("Повторить") }
            }
            nothingLoaded -> Text(
                if (teacherSearch) "Преподаватели не найдены" else "Группы не найдены",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            teacherSearch -> state.availableTeachers.forEach { teacher ->
                val selected = selectedTeacher == teacher
                Surface(
                    onClick = { selectedTeacher = teacher },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .semantics { this.selected = selected },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(teacher.name, style = MaterialTheme.typography.titleMedium)
                        teacher.department?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            else -> state.availableGroups.forEach { group ->
                val selected = selectedGroup == group
                Surface(
                    onClick = { selectedGroup = group },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .semantics { this.selected = selected },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                ) {
                    Text(group, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = {
                if (teacherSearch) selectedTeacher?.let(onTeacherSelected) else selectedGroup?.let(onGroupSelected)
            },
            enabled = hasSelection,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            AnimatedContent(
                targetState = hasSelection,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "continue-button-label",
            ) { selectionMade ->
                Text(
                    when {
                        selectionMade -> "Продолжить"
                        teacherSearch -> "Выберите себя в списке"
                        else -> "Выбери группу"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Выбор можно изменить позже в настройках.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScheduleScreen(
    state: ScheduleUiState,
    openPlanId: String?,
    openPlanRequestKey: Long,
    onOpenPlanConsumed: (Long) -> Unit,
    onGroupSelected: (String) -> Unit,
    onGroupSearchQueryChanged: (String) -> Unit,
    onGroupsRetry: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onMoveWeek: (Long) -> Unit,
    onToday: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLectureStripeColorSelected: (Int) -> Unit,
    onPracticeStripeColorSelected: (Int) -> Unit,
    onWidgetShowTimeChanged: (Boolean) -> Unit,
    onWidgetShowStripeChanged: (Boolean) -> Unit,
    onWidgetShowClassroomChanged: (Boolean) -> Unit,
    onWidgetShowEndTimeChanged: (Boolean) -> Unit,
    onWidgetWrapTitleChanged: (Boolean) -> Unit,
    onWidgetOuterPaddingChanged: (Int) -> Unit,
    onWidgetHorizontalPaddingChanged: (Int) -> Unit,
    onWidgetVerticalPaddingChanged: (Int) -> Unit,
    onWidgetItemSpacingChanged: (Int) -> Unit,
    onWidgetTitleTextSizeChanged: (Int) -> Unit,
    onWidgetSecondaryTextSizeChanged: (Int) -> Unit,
    onWidgetFontStyleChanged: (String) -> Unit,
    onWidgetLetterSpacingChanged: (Int) -> Unit,
    onWidgetTitleMaxLinesChanged: (Int) -> Unit,
    onLessonAliasChanged: (String, String) -> Unit,
    onLanguageTeacherSelected: (String?) -> Unit,
    onAppThemeSelected: (AppTheme) -> Unit,
    onDarkThemeChanged: (Boolean) -> Unit,
    onBuiltInThemeSaved: (AppTheme, ThemePalette) -> Unit,
    onBuiltInThemeReset: (AppTheme) -> Unit,
    onCustomThemeSelected: (String) -> Unit,
    onCustomThemeSaved: (CustomAppTheme) -> Unit,
    onCustomThemeDeleted: (String) -> Unit,
    onAppDisplayOptionsChanged: (AppDisplayOptions) -> Unit,
    onAppDisplayOptionsReset: () -> Unit,
    onStudyPlanSaved: (StudyPlan) -> Unit,
    onStudyPlanCompletedChanged: (String, Boolean) -> Unit,
    onStudyPlanDeleted: (String) -> Unit,
    onDeadlineReminderDaysChanged: (Set<Int>) -> Unit,
    onLessonAlertsPause: (LessonAlertPause?) -> Unit,
    onScheduleWeeksBackChanged: (Int) -> Unit,
    onScheduleWeeksForwardChanged: (Int) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: (AppUpdateState.ReadyToInstall) -> Unit,
    onTeacherSelected: (TeacherInfo) -> Unit = {},
    onSearchModeChanged: (ScheduleSearchMode) -> Unit = {},
    openUpdatesKey: Long = 0L,
) {
    var mainSection by remember { mutableStateOf(MainSection.Schedule) }
    var settingsSection by remember { mutableStateOf<SettingsSection?>(null) }
    val screenEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val predictiveBackProgress = remember { Animatable(0f) }
    LaunchedEffect(openPlanId, openPlanRequestKey) {
        if (openPlanId != null) mainSection = MainSection.Plans
    }
    // The update notification lands on Settings → About.
    LaunchedEffect(openUpdatesKey) {
        if (openUpdatesKey > 0L) {
            mainSection = MainSection.Settings
            settingsSection = SettingsSection.About
        }
    }
    // Statistics is a teacher tool; students switching back from teacher mode leave it.
    LaunchedEffect(state.isTeacherMode) {
        if (!state.isTeacherMode && mainSection == MainSection.Statistics) mainSection = MainSection.Schedule
    }
    LaunchedEffect(mainSection) {
        if (mainSection != MainSection.Settings) settingsSection = null
    }
    LaunchedEffect(mainSection, settingsSection) {
        predictiveBackProgress.snapTo(0f)
    }
    BackHandler(enabled = mainSection == MainSection.Plans || mainSection == MainSection.Statistics) {
        mainSection = MainSection.Schedule
    }
    PredictiveBackHandler(enabled = mainSection == MainSection.Settings) { backEvents ->
        try {
            backEvents.collect { event -> predictiveBackProgress.snapTo(event.progress) }
            when (settingsSection) {
                SettingsSection.Privacy -> settingsSection = SettingsSection.About
                SettingsSection.Widget -> settingsSection = SettingsSection.Appearance
                null -> mainSection = MainSection.Schedule
                else -> settingsSection = null
            }
        } catch (cancelled: CancellationException) {
            predictiveBackProgress.animateTo(0f, screenEffectsSpec)
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth().statusBarsPadding().clipToBounds()) {
            AnimatedContent(
                targetState = mainSection to settingsSection,
                transitionSpec = {
                    if (initialState.first != targetState.first) {
                        val direction = if (targetState.first.ordinal >= initialState.first.ordinal) 1 else -1
                        (slideInHorizontally(screenSlideSpec()) { direction * it / 5 } + fadeIn(screenFadeSpec())) togetherWith
                            (slideOutHorizontally(screenSlideSpec()) { -direction * it / 5 } + fadeOut(screenFadeSpec()))
                    } else {
                        fun settingsDepth(section: SettingsSection?): Int = when (section) {
                            null -> 1
                            SettingsSection.Widget, SettingsSection.Privacy -> 3
                            else -> 2
                        }
                        val initialDepth = if (initialState.first != MainSection.Settings) 0 else settingsDepth(initialState.second)
                        val targetDepth = if (targetState.first != MainSection.Settings) 0 else settingsDepth(targetState.second)
                        if (targetDepth > initialDepth) {
                            (slideInHorizontally(screenSlideSpec()) { it / 5 } + fadeIn(screenFadeSpec())) togetherWith
                                (slideOutHorizontally(screenSlideSpec()) { -it / 10 } + fadeOut(screenFadeSpec()))
                        } else {
                            (slideInHorizontally(screenSlideSpec()) { -it / 10 } + fadeIn(screenFadeSpec())) togetherWith
                                (slideOutHorizontally(screenSlideSpec()) { it / 5 } + fadeOut(screenFadeSpec()))
                        }
                    }
                },
                label = "screen-navigation",
            ) { (selectedSection, section) ->
            if (selectedSection == MainSection.Settings) {
                val nestedModifier = Modifier.graphicsLayer {
                    val progress = predictiveBackProgress.value
                    translationX = size.width * 0.18f * progress
                    scaleX = 1f - 0.04f * progress
                    scaleY = 1f - 0.04f * progress
                    alpha = 1f - 0.12f * progress
                }
                when (section) {
                    SettingsSection.LanguageTeacher -> LanguageTeacherSelectionScreen(
                        state = state,
                        onSelected = { teacher ->
                            onLanguageTeacherSelected(teacher)
                            settingsSection = null
                        },
                        onRetry = onRetry,
                        onBack = { settingsSection = null },
                        backContentDescription = "Назад к настройкам",
                    )
                    SettingsSection.Appearance -> AppearanceSettingsScreen(
                        modifier = nestedModifier,
                        selectedTheme = state.appTheme,
                        darkTheme = state.darkTheme,
                        builtInThemeOverrides = state.builtInThemeOverrides,
                        customThemes = state.customThemes,
                        selectedCustomThemeId = state.selectedCustomThemeId,
                        onThemeSelected = onAppThemeSelected,
                        onDarkThemeChanged = onDarkThemeChanged,
                        onBuiltInThemeSaved = onBuiltInThemeSaved,
                        onBuiltInThemeReset = onBuiltInThemeReset,
                        onCustomThemeSelected = onCustomThemeSelected,
                        onCustomThemeSaved = onCustomThemeSaved,
                        onCustomThemeDeleted = onCustomThemeDeleted,
                        appDisplayOptions = state.appDisplayOptions,
                        onAppDisplayOptionsChanged = onAppDisplayOptionsChanged,
                        onAppDisplayOptionsReset = onAppDisplayOptionsReset,
                        onOpenWidgetSettings = { settingsSection = SettingsSection.Widget },
                        onBack = { settingsSection = null },
                    )
                    SettingsSection.Widget -> WidgetSettingsScreen(
                        modifier = nestedModifier,
                        state = state,
                        onBack = { settingsSection = SettingsSection.Appearance },
                        onLectureColorSelected = onLectureStripeColorSelected,
                        onPracticeColorSelected = onPracticeStripeColorSelected,
                        onShowTimeChanged = onWidgetShowTimeChanged,
                        onShowStripeChanged = onWidgetShowStripeChanged,
                        onShowClassroomChanged = onWidgetShowClassroomChanged,
                        onShowEndTimeChanged = onWidgetShowEndTimeChanged,
                        onWrapTitleChanged = onWidgetWrapTitleChanged,
                        onOuterPaddingChanged = onWidgetOuterPaddingChanged,
                        onHorizontalPaddingChanged = onWidgetHorizontalPaddingChanged,
                        onVerticalPaddingChanged = onWidgetVerticalPaddingChanged,
                        onItemSpacingChanged = onWidgetItemSpacingChanged,
                        onTitleTextSizeChanged = onWidgetTitleTextSizeChanged,
                        onSecondaryTextSizeChanged = onWidgetSecondaryTextSizeChanged,
                        onFontStyleChanged = onWidgetFontStyleChanged,
                        onLetterSpacingChanged = onWidgetLetterSpacingChanged,
                        onTitleMaxLinesChanged = onWidgetTitleMaxLinesChanged,
                    )
                    SettingsSection.Names -> LessonNamesScreen(
                        modifier = nestedModifier,
                        names = uniqueDisciplines(*state.loadedScheduleWeeks().toTypedArray()),
                        aliases = state.lessonAliases,
                        onAliasChanged = onLessonAliasChanged,
                        onBack = { settingsSection = null },
                    )
                    SettingsSection.Testing -> NotificationTestingScreen(
                        modifier = nestedModifier,
                        onBack = { settingsSection = null },
                    )
                    SettingsSection.About -> AboutScreen(
                        modifier = nestedModifier,
                        updateState = state.appUpdateState,
                        onBack = { settingsSection = null },
                        onOpenPrivacy = { settingsSection = SettingsSection.Privacy },
                        onCheckForUpdates = onCheckForUpdates,
                        onDownloadUpdate = onDownloadUpdate,
                        onInstallUpdate = onInstallUpdate,
                    )
                    SettingsSection.Privacy -> PrivacyScreen(
                        modifier = nestedModifier,
                        onBack = { settingsSection = SettingsSection.About },
                    )
                    null -> AppSettingsScreen(
                        state = state,
                        onGroupSelected = onGroupSelected,
                        onTeacherSelected = onTeacherSelected,
                        onSearchModeChanged = onSearchModeChanged,
                        onGroupSearchQueryChanged = onGroupSearchQueryChanged,
                        onGroupsRetry = onGroupsRetry,
                        onOpenLanguageTeacher = { settingsSection = SettingsSection.LanguageTeacher },
                        onOpenAppearance = { settingsSection = SettingsSection.Appearance },
                        onOpenLessonNames = { settingsSection = SettingsSection.Names },
                        onOpenTesting = { settingsSection = SettingsSection.Testing },
                        onOpenAbout = { settingsSection = SettingsSection.About },
                        onDeadlineReminderDaysChanged = onDeadlineReminderDaysChanged,
                        onLessonAlertsPause = onLessonAlertsPause,
                        onScheduleWeeksBackChanged = onScheduleWeeksBackChanged,
                        onScheduleWeeksForwardChanged = onScheduleWeeksForwardChanged,
                    )
                }
            } else if (selectedSection == MainSection.Statistics) {
                AnalyticsScreen(onBack = null)
            } else if (selectedSection == MainSection.Plans) {
                StudyPlansScreen(
                    plans = state.studyPlans,
                    displayOptions = state.appDisplayOptions,
                    requestedPlanId = openPlanId,
                    requestedPlanKey = openPlanRequestKey,
                    onRequestedPlanOpened = onOpenPlanConsumed,
                    onSave = onStudyPlanSaved,
                    onCompletedChanged = onStudyPlanCompletedChanged,
                    onDelete = onStudyPlanDeleted,
                )
            } else {
                val pullState = rememberPullToRefreshState()
                PullToRefreshBox(
                    // Shown for pull-to-refresh and for the refresh on entering the app;
                    // other reloads (range changes etc.) keep cached lessons quietly.
                    isRefreshing = state.isManualRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                    state = pullState,
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullState,
                            isRefreshing = state.isManualRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    },
                ) {
                    ScheduleContent(
                        state = state,
                        onRetry = onRetry,
                        onToday = onToday,
                        onStudyPlanSaved = onStudyPlanSaved,
                        onStudyPlanCompletedChanged = onStudyPlanCompletedChanged,
                        onStudyPlanDeleted = onStudyPlanDeleted,
                    )
                }
            }
            }
        }
        LecturialBottomBar(
            selectedSection = mainSection,
            onHome = {
                settingsSection = null
                mainSection = MainSection.Schedule
            },
            onPlans = { mainSection = MainSection.Plans },
            onStatistics = { mainSection = MainSection.Statistics },
            showStatistics = state.isTeacherMode,
            onSettings = { mainSection = MainSection.Settings },
        )
    }
}

@Composable
private fun LecturialBottomBar(
    selectedSection: MainSection,
    onHome: () -> Unit,
    onPlans: () -> Unit,
    onStatistics: () -> Unit,
    onSettings: () -> Unit,
    showStatistics: Boolean,
) {
    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bottom-navigation"),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        BottomBarItem(
            selected = selectedSection == MainSection.Schedule,
            onClick = onHome,
            selectedIcon = Icons.Filled.DateRange,
            icon = Icons.Outlined.DateRange,
            label = "Расписание",
        )
        BottomBarItem(
            selected = selectedSection == MainSection.Plans,
            onClick = onPlans,
            selectedIcon = Icons.Filled.CheckCircle,
            icon = Icons.Outlined.CheckCircle,
            label = "Планы",
        )
        if (showStatistics) {
            BottomBarItem(
                selected = selectedSection == MainSection.Statistics,
                onClick = onStatistics,
                selectedIcon = StatisticsIconFilled,
                icon = StatisticsIconOutlined,
                label = "Статистика",
            )
        }
        BottomBarItem(
            selected = selectedSection == MainSection.Settings,
            onClick = onSettings,
            selectedIcon = Icons.Filled.Settings,
            icon = Icons.Outlined.Settings,
            label = "Настройки",
        )
    }
}

@Composable
private fun RowScope.BottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    selectedIcon: ImageVector,
    icon: ImageVector,
    label: String,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(if (selected) selectedIcon else icon, contentDescription = null) },
        label = { Text(label) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSettingsScreen(
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
            .padding(horizontal = 20.dp, vertical = 24.dp),
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
private fun SettingsNavigationRow(title: String, subtitle: String, onClick: () -> Unit) {
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
        shape = RoundedCornerShape(20.dp),
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
    shape: Shape = RoundedCornerShape(24.dp),
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

@Composable
private fun AppearanceSettingsScreen(
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
    var editedBuiltInTheme by remember { mutableStateOf<AppTheme?>(null) }
    var creatingTheme by remember { mutableStateOf(false) }
    var previewOptions by remember { mutableStateOf(appDisplayOptions) }
    var previewRequestKey by remember { mutableStateOf(0) }
    var previewVisible by remember { mutableStateOf(false) }
    var previewLessonType by remember { mutableStateOf(PreviewLessonType.Lecture) }
    LaunchedEffect(appDisplayOptions) {
        previewOptions = appDisplayOptions
    }
    LaunchedEffect(previewRequestKey) {
        if (previewRequestKey == 0) return@LaunchedEffect
        previewVisible = true
        delay(2_400)
        previewVisible = false
    }
    fun updateDisplayOptions(updated: AppDisplayOptions) {
        val lectureChanged = updated.lectureStripeColor != appDisplayOptions.lectureStripeColor
        val practiceChanged = updated.practiceStripeColor != appDisplayOptions.practiceStripeColor
        if (lectureChanged || practiceChanged) {
            previewOptions = updated
            if (lectureChanged) {
                previewLessonType = PreviewLessonType.Lecture
            } else if (practiceChanged) {
                previewLessonType = PreviewLessonType.Practice
            }
            previewRequestKey++
        } else {
            previewVisible = false
        }
        onAppDisplayOptionsChanged(updated)
    }
    fun updateBackgroundOptions(updated: AppDisplayOptions) {
        previewVisible = false
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackIconButton(onBack)
            Text("Оформление", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        Text(
            "Общее оформление приложения и расписания. Все сохранённые изменения применяются сразу.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppThemePreview(options = appDisplayOptions)
        WidgetSettingSwitch(
            title = "Тёмный режим",
            description = if (darkTheme) "Тёмная палитра включена" else "Используется светлая палитра",
            checked = darkTheme,
            onCheckedChange = onDarkThemeChanged,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AppBackgroundSettings(
            options = appDisplayOptions,
            onOptionsChanged = ::updateBackgroundOptions,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Тема приложения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        AppTheme.entries.filterNot { it == AppTheme.Custom }.forEach { theme ->
            val selected = theme == selectedTheme
            Surface(
                onClick = { onThemeSelected(theme) },
                modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
                shape = RoundedCornerShape(20.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(appThemePreviewColor(theme), RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(appThemeTitle(theme), style = MaterialTheme.typography.titleMedium)
                        Text(
                            appThemeDescription(theme),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { editedBuiltInTheme = theme }) { Text("Изменить") }
                    ThemeSelectionIndicator(selected)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Мои темы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        customThemes.forEach { theme ->
            val selected = selectedTheme == AppTheme.Custom && selectedCustomThemeId == theme.id
            Surface(
                onClick = { onCustomThemeSelected(theme.id) },
                modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
                shape = RoundedCornerShape(20.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        listOf(theme.primary, theme.secondary, theme.tertiary).forEach { color ->
                            Box(Modifier.size(12.dp, 36.dp).background(Color(color), RoundedCornerShape(6.dp)))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(theme.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { editedTheme = theme }) { Text("Изменить") }
                    ThemeSelectionIndicator(selected)
                }
            }
        }
        Button(onClick = { creatingTheme = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Создать тему")
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Оформление расписания", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Настройте карточки занятий и сразу оцените результат.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppDisplaySettings(
            options = appDisplayOptions,
            onOptionsChanged = ::updateDisplayOptions,
        )
        val defaultScheduleOptions = AppDisplayOptions(
            backgroundStyle = appDisplayOptions.backgroundStyle,
            backgroundIntensityPercent = appDisplayOptions.backgroundIntensityPercent,
            backgroundSpeedPercent = appDisplayOptions.backgroundSpeedPercent,
            backgroundGradientStartColor = appDisplayOptions.backgroundGradientStartColor,
            backgroundGradientEndColor = appDisplayOptions.backgroundGradientEndColor,
            backgroundGradientAngleDegrees = appDisplayOptions.backgroundGradientAngleDegrees,
        )
        OutlinedButton(
            onClick = {
                previewOptions = defaultScheduleOptions
                previewRequestKey++
                onAppDisplayOptionsReset()
            },
            enabled = appDisplayOptions != defaultScheduleOptions,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Сбросить настройки расписания") }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Виджет", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        SettingsNavigationRow(
            title = "Оформление виджета",
            subtitle = "Цвета, содержимое, шрифт, размеры и отступы",
            onClick = onOpenWidgetSettings,
        )
        Spacer(Modifier.height(12.dp))
                }
                AnimatedVisibility(
                    visible = previewVisible,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    enter = fadeIn(tween(160)),
                    exit = fadeOut(tween(180)),
                ) {
                    AppDisplayPreview(
                        options = previewOptions,
                        lessonType = previewLessonType,
                        onDismiss = { previewVisible = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppBackgroundSettings(
    options: AppDisplayOptions,
    onOptionsChanged: (AppDisplayOptions) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val usesCustomGradient = options.backgroundGradientStartColor != null &&
        options.backgroundGradientEndColor != null
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Фон приложения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Выберите однотонный классический фон или плавные цветовые поля активной темы.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BackgroundStyleCard(
                title = "Динамический",
                selected = options.backgroundStyle == AppBackgroundStyle.Dynamic,
                options = options.copy(
                    backgroundStyle = AppBackgroundStyle.Dynamic,
                    backgroundSpeedPercent = 0,
                ),
                modifier = Modifier.weight(1f),
                onClick = { onOptionsChanged(options.copy(backgroundStyle = AppBackgroundStyle.Dynamic)) },
            )
            BackgroundStyleCard(
                title = "Классический",
                selected = options.backgroundStyle == AppBackgroundStyle.Classic,
                options = options.copy(backgroundStyle = AppBackgroundStyle.Classic),
                modifier = Modifier.weight(1f),
                onClick = { onOptionsChanged(options.copy(backgroundStyle = AppBackgroundStyle.Classic)) },
            )
        }
        if (options.backgroundStyle == AppBackgroundStyle.Dynamic) {
            WidgetSettingSwitch(
                title = "Свои цвета градиента",
                description = "Иначе используются цвета выбранной темы",
                checked = usesCustomGradient,
            ) { enabled ->
                onOptionsChanged(
                    options.copy(
                        backgroundGradientStartColor = colors.primary.toArgb().takeIf { enabled },
                        backgroundGradientEndColor = colors.secondary.toArgb().takeIf { enabled },
                        backgroundGradientAccentColor = colors.tertiary.toArgb().takeIf { enabled },
                    ),
                )
            }
            if (usesCustomGradient) {
                WallpaperColorField(
                    label = "Начало градиента",
                    color = options.backgroundGradientStartColor!!,
                ) { onOptionsChanged(options.copy(backgroundGradientStartColor = it)) }
                WallpaperColorField(
                    label = "Середина градиента",
                    color = options.backgroundGradientAccentColor ?: colors.tertiary.toArgb(),
                ) { onOptionsChanged(options.copy(backgroundGradientAccentColor = it)) }
                WallpaperColorField(
                    label = "Конец градиента",
                    color = options.backgroundGradientEndColor!!,
                ) { onOptionsChanged(options.copy(backgroundGradientEndColor = it)) }
                WidgetDimensionSlider(
                    title = "Направление градиента",
                    description = "Угол перехода между цветами",
                    value = options.backgroundGradientAngleDegrees,
                    range = 0..360,
                    unit = "°",
                    showSteps = false,
                ) { onOptionsChanged(options.copy(backgroundGradientAngleDegrees = it)) }
            }
            BackgroundMotionSlider(
                title = "Интенсивность фона",
                description = "Насыщенность цветовых полей",
                value = options.backgroundIntensityPercent,
                range = 25..150,
            ) { onOptionsChanged(options.copy(backgroundIntensityPercent = it)) }
            BackgroundMotionSlider(
                title = "Скорость фона",
                description = "0 % полностью останавливает движение",
                value = options.backgroundSpeedPercent,
                range = 0..100,
            ) { onOptionsChanged(options.copy(backgroundSpeedPercent = it)) }
        } else {
            Text(
                "Однотонный фон темы без градиентов и анимации, как до появления динамического фона.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        shape = RoundedCornerShape(20.dp),
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
                    shape = RoundedCornerShape(10.dp),
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
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
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
                    .clip(RoundedCornerShape(12.dp)),
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
private fun CustomThemeEditor(
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
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsScreenHeader(title, onBack)
        Text(
            "Выберите оттенки на hue-колесе, из готовой палитры или введите их в формате #RRGGBB. Предпросмотр обновляется сразу, тема сохранится только по кнопке ниже.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
private fun ThemeColorField(
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
                Modifier.size(24.dp).background(parsed?.let(::Color) ?: Color.Transparent, RoundedCornerShape(8.dp)),
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
                    shape = RoundedCornerShape(13.dp),
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

@Composable
private fun AppThemePreview(options: AppDisplayOptions) {
    MaterialYouBackground(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .semantics { contentDescription = "Живой предпросмотр темы приложения" },
        options = options,
    ) {
        Surface(
            color = Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(24.dp),
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
                    shape = RoundedCornerShape(18.dp),
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

@Composable
private fun SettingsScreenHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BackIconButton(onBack)
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    }
}

private fun appThemeTitle(theme: AppTheme): String = when (theme) {
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
    AppTheme.Custom -> Color(0xFF8AABFF)
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
                                    .setData(Uri.parse("package:${context.packageName}")),
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
private fun NotificationTestingScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackIconButton(onBack)
            Text("Тестирование", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        Text(
            "Запусти сценарий, сверни приложение и проверь карточку в шторке, LiveChip в строке состояния, таймер и линию прогресса.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
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
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text("До начала — 15 минут") }
        OutlinedButton(
            onClick = { LessonAlerts.testCurrentHourFifteen(context) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
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
private fun AboutScreen(
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackIconButton(onBack)
            Text("О приложении", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
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
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            },
        )
        SettingsNavigationRow("GitHub", "Исходный код и все релизы") {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/neckmnull/Lecturial-2")),
                )
            }
        }
        SettingsNavigationRow("Telegram", "Официальный канал @lecturial_2") {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/lecturial_2")),
                )
            }
        }
        SettingsNavigationRow("Источник расписания", "Университет «Сириус»") {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://schedule.siriusuniversity.ru/")))
            }
        }
        SettingsNavigationRow("Политика конфиденциальности", "Данные и их хранение", onOpenPrivacy)
        Spacer(Modifier.height(12.dp))
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
        shape = RoundedCornerShape(20.dp),
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
private fun PrivacyScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackIconButton(onBack)
            Text(
                "Конфиденциальность",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
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
        shape = RoundedCornerShape(16.dp),
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
private fun LessonNamesScreen(
    modifier: Modifier = Modifier,
    names: List<String>,
    aliases: Map<String, String>,
    onAliasChanged: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackIconButton(onBack)
            Text("Названия пар", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
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
            names.forEach { original ->
                val alias = aliases[original]
                var value by remember(original, alias) { mutableStateOf(alias ?: original) }
                OutlinedTextField(
                    value = value,
                    onValueChange = { next ->
                        value = next
                        onAliasChanged(original, next)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(original, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                if (alias != null) {
                    TextButton(onClick = {
                        value = original
                        onAliasChanged(original, original)
                    }) {
                        Text("Сбросить")
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AppDisplaySettings(
    options: AppDisplayOptions,
    onOptionsChanged: (AppDisplayOptions) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Цветовые полосы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        StripeColorPicker("Лекция", options.lectureStripeColor) {
            onOptionsChanged(options.copy(lectureStripeColor = it))
        }
        StripeColorPicker("Практика", options.practiceStripeColor) {
            onOptionsChanged(options.copy(practiceStripeColor = it))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Text("Содержимое", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        WidgetSettingSwitch("Показывать аудиторию", "Аудитория и адрес занятия", options.showClassroom) {
            onOptionsChanged(options.copy(showClassroom = it))
        }
        WidgetSettingSwitch("Показывать время", "Время слева от названия предмета", options.showTime) {
            onOptionsChanged(options.copy(showTime = it))
        }
        WidgetSettingSwitch("Показывать цветную полосу", "Акцент у текущего занятия", options.showStripe) {
            onOptionsChanged(options.copy(showStripe = it))
        }
        WidgetSettingSwitch("Время окончания", "Показывать окончание под временем начала", options.showEndTime) {
            onOptionsChanged(options.copy(showEndTime = it))
        }
        WidgetSettingSwitch("Переносить название", "Разрешить несколько строк для предмета", options.wrapTitle) {
            onOptionsChanged(options.copy(wrapTitle = it))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Text("Размер текста", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        FontStylePicker(options.fontStyleId) { onOptionsChanged(options.copy(fontStyleId = it)) }
        WidgetDimensionSlider("Название предмета", "Размер основного текста", options.titleTextSizeSp, 12..24, "sp") {
            onOptionsChanged(options.copy(titleTextSizeSp = it))
        }
        WidgetDimensionSlider("Время и детали", "Размер вспомогательного текста", options.secondaryTextSizeSp, 10..20, "sp") {
            onOptionsChanged(options.copy(secondaryTextSizeSp = it))
        }
        WidgetDimensionSlider("Межбуквенный интервал", "Плотность букв", options.letterSpacingPercent, -5..10, "%") {
            onOptionsChanged(options.copy(letterSpacingPercent = it))
        }
        WidgetDimensionSlider("Максимум строк названия", "Используется при включённом переносе", options.titleMaxLines, 1..5, "стр.") {
            onOptionsChanged(options.copy(titleMaxLines = it))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Text("Стиль тайлов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        WidgetDimensionSlider("Скругление", "Радиус углов карточки занятия", options.tileCornerRadiusDp, 0..32) {
            onOptionsChanged(options.copy(tileCornerRadiusDp = it))
        }
        WidgetDimensionSlider("Прозрачность", "Плотность поверхности поверх обоев", options.tileOpacityPercent, 0..100, "%") {
            onOptionsChanged(options.copy(tileOpacityPercent = it))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Text("Отступы и плотность", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        WidgetDimensionSlider("Отступ от краёв", "Поля списка расписания", options.outerPaddingDp, 0..32) {
            onOptionsChanged(options.copy(outerPaddingDp = it))
        }
        WidgetDimensionSlider("Отступ текста", "Расстояние между временем и предметом", options.horizontalPaddingDp, 0..24) {
            onOptionsChanged(options.copy(horizontalPaddingDp = it))
        }
        WidgetDimensionSlider("Вертикальный отступ", "Свободное место внутри занятия", options.verticalPaddingDp, 0..16) {
            onOptionsChanged(options.copy(verticalPaddingDp = it))
        }
        WidgetDimensionSlider("Расстояние между занятиями", "Пустое место между карточками", options.itemSpacingDp, 0..24) {
            onOptionsChanged(options.copy(itemSpacingDp = it))
        }
        Text("Изменения применяются сразу.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppDisplayPreview(
    options: AppDisplayOptions,
    lessonType: PreviewLessonType,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .semantics { contentDescription = "Живой предпросмотр расписания" },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Живой предпросмотр", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Изменения видны сразу",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) { Text("Скрыть") }
            }
            Column(
                modifier = Modifier.padding(
                    start = options.outerPaddingDp.dp,
                    end = options.outerPaddingDp.dp,
                ),
            ) {
                LessonTimelineItem(
                    lesson = ScheduleRow.Lesson(
                        eventId = "appearance-preview",
                        typeLabel = if (lessonType == PreviewLessonType.Lecture) "Лекция" else "Практика",
                        isLecture = lessonType == PreviewLessonType.Lecture,
                        startTime = "08:45",
                        endTime = "10:05",
                        discipline = if (lessonType == PreviewLessonType.Lecture) {
                            "Основы программирования"
                        } else {
                            "Практические занятия"
                        },
                        location = "5.7 · Главный корпус",
                        teachers = "И. Иван Иванович",
                        comment = null,
                        liveLabel = null,
                    ),
                    options = options,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetSettingsScreen(
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
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackIconButton(onBack)
                Text("Виджет", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                "Эти параметры влияют только на виджет на рабочем столе.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                "Цветовые полосы",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            StripeColorPicker("Лекция", state.lectureStripeColor, onLectureColorSelected)
            StripeColorPicker("Практика", state.practiceStripeColor, onPracticeColorSelected)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text(
                "Содержимое",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text(
                "Размер текста",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text(
                "Отступы и плотность",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
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
            Text(
                "Изменения применяются к виджету сразу.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun FontStylePicker(
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
            shape = RoundedCornerShape(16.dp),
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
private fun WidgetDimensionSlider(
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
                "$value $unit",
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
            modifier = Modifier.semantics { contentDescription = "$title: $value $unit" },
        )
    }
}

@Composable
private fun WidgetSettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
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
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun StripeColorPicker(
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
                    shape = RoundedCornerShape(16.dp),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GroupDropdown(
    state: ScheduleUiState,
    onSearchQueryChanged: (String) -> Unit,
    onRetry: () -> Unit,
    onSelected: (String) -> Unit,
    onTeacherSelected: (TeacherInfo) -> Unit = {},
    onSearchModeChanged: (ScheduleSearchMode) -> Unit = {},
) {
    val teacherSearch = state.searchMode == ScheduleSearchMode.Teacher
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = {
                expanded = true
                onRetry()
            },
            modifier = Modifier.semantics {
                contentDescription = "Выбрать группу, сейчас ${state.scheduleLabel}"
            },
        ) {
            Text(
                "Изменить",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            val arrowRotation by animateFloatAsState(
                if (expanded) 180f else 0f,
                MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "group-arrow",
            )
            Icon(
                Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.graphicsLayer { rotationZ = arrowRotation },
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ScheduleSearchModeButtons(
                mode = state.searchMode,
                onModeChanged = onSearchModeChanged,
                modifier = Modifier.width(300.dp).padding(horizontal = 8.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = state.groupSearchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier.width(300.dp).padding(horizontal = 8.dp),
                label = { Text(if (teacherSearch) "Поиск преподавателя" else "Поиск группы") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            val nothingLoaded = if (teacherSearch) state.availableTeachers.isEmpty() else state.availableGroups.isEmpty()
            when {
                state.isGroupsLoading && nothingLoaded -> Box(
                    Modifier
                        .width(300.dp)
                        .height(64.dp)
                        .semantics { contentDescription = "Загрузка групп" },
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }
                state.groupsError != null && nothingLoaded -> Column(
                    modifier = Modifier.width(300.dp).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.groupsError, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    TextButton(onClick = onRetry) { Text("Повторить") }
                }
                nothingLoaded -> Text(
                    if (teacherSearch) "Преподаватели не найдены" else "Группы не найдены",
                    modifier = Modifier.width(300.dp).padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
            if (teacherSearch) {
                state.availableTeachers.forEach { teacher ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(teacher.name)
                                teacher.department?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        onClick = { expanded = false; onTeacherSelected(teacher) },
                    )
                }
            } else {
                state.availableGroups.forEach { group ->
                    DropdownMenuItem(
                        text = { Text(group) },
                        onClick = { expanded = false; onSelected(group) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScheduleContent(
    state: ScheduleUiState,
    onRetry: () -> Unit,
    onToday: () -> Unit,
    onStudyPlanSaved: (StudyPlan) -> Unit,
    onStudyPlanCompletedChanged: (String, Boolean) -> Unit,
    onStudyPlanDeleted: (String) -> Unit,
) {
    var editingPlan by remember { mutableStateOf<StudyPlan?>(null) }
    when {
        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        state.scheduleWeeks.isEmpty() && state.week == null && state.nextWeek == null && state.errorMessage != null ->
            FullError(state.errorMessage, onRetry)
        else -> {
            val rows = remember(
                state.scheduleWeeks,
                state.week,
                state.nextWeek,
                state.errorMessage,
                state.lastUpdatedMillis,
                state.selectedGroup,
                state.weekStart,
                state.selectedDate,
                state.now,
                state.lessonAliases,
                state.studyPlans,
            ) {
                buildScheduleRows(
                    scheduleWeeks = state.scheduleWeeks,
                    week = state.week,
                    nextWeek = state.nextWeek,
                    currentWeekStart = state.weekStart,
                    errorMessage = state.errorMessage,
                    lastUpdatedMillis = state.lastUpdatedMillis,
                    group = state.scheduleLabel,
                    teacherMode = state.isTeacherMode,
                    selectedDate = state.selectedDate,
                    now = state.now,
                    aliases = state.lessonAliases,
                    plans = state.studyPlans,
                )
            }
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                val listState = rememberLazyListState()
                val scrollSession = "${state.selectedGroup}|${state.weekStart}|${state.selectedDate}|${state.isLoading}"
                var scrolledFor by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(rows, scrollSession) {
                    if (state.isLoading) return@LaunchedEffect
                    if (scrolledFor == scrollSession) return@LaunchedEffect
                    val index = scheduleInitialRowIndex(
                        rowKeys = rows.map(ScheduleRow::key),
                        selectedDate = state.selectedDate,
                        currentWeekStart = state.weekStart,
                    )
                    if (index < 0) return@LaunchedEffect
                    listState.scrollToItem(index)
                    scrolledFor = scrollSession
                }
                val today = state.now.toLocalDate()
                val todayRowIndex = remember(rows, today) {
                    rows.indexOfFirst { it.key == "day-$today" }
                }
                // Visible whenever today's heading is scrolled away (or not loaded at all,
                // e.g. after moving to another week).
                val todayDirection by remember(todayRowIndex) {
                    derivedStateOf {
                        val visible = listState.layoutInfo.visibleItemsInfo
                        when {
                            todayRowIndex < 0 -> 0
                            visible.isEmpty() -> null
                            todayRowIndex < visible.first().index -> -1
                            todayRowIndex > visible.last().index -> 1
                            else -> null
                        }
                    }
                }
                val scope = rememberCoroutineScope()
                Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("schedule-list"),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = state.appDisplayOptions.outerPaddingDp.dp,
                        end = state.appDisplayOptions.outerPaddingDp.dp,
                        top = 12.dp,
                        // Room for the "Сегодня" button over the last row.
                        bottom = 96.dp,
                    ),
                ) {
                    items(
                        items = rows,
                        key = { it.key },
                        contentType = { it.contentType },
                    ) { row ->
                        when (row) {
                            is ScheduleRow.Header -> ScheduleHeader(row.group, row.subtitle)
                            is ScheduleRow.Banner -> OfflineBanner(row.message, row.updatedAt)
                            is ScheduleRow.Empty -> EmptyScheduleState()
                            is ScheduleRow.WeekBreak -> WeekDivider(row.title, row.range, row.isCurrent)
                            is ScheduleRow.EmptyWeek -> EmptyWeekState()
                            is ScheduleRow.Day -> DayHeading(row.title, row.subtitle, row.separated)
                            is ScheduleRow.Lesson -> LessonTimelineItem(row, state.appDisplayOptions)
                            is ScheduleRow.Plan -> StudyPlanTimelineItem(
                                plan = row.plan,
                                options = state.appDisplayOptions,
                                onToggle = {
                                    onStudyPlanCompletedChanged(row.sourcePlan.id, !row.sourcePlan.isCompleted)
                                },
                                onEdit = { editingPlan = row.sourcePlan },
                            )
                            is ScheduleRow.Rest -> BreakHint(row.text)
                        }
                    }
                }
                JumpToTodayButton(
                    direction = todayDirection,
                    onClick = {
                        if (todayRowIndex >= 0) {
                            scope.launch { listState.animateScrollToItem(todayRowIndex) }
                        } else {
                            onToday()
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                )
                }
            }
        }
    }
    editingPlan?.let { plan ->
        StudyPlanEditor(
            plan = plan,
            onDismiss = { editingPlan = null },
            onSave = {
                onStudyPlanSaved(it)
                editingPlan = null
            },
            onDelete = {
                onStudyPlanDeleted(plan.id)
                editingPlan = null
            },
        )
    }
}

/**
 * Returns to today's heading. [direction] is -1 when today is above the viewport,
 * 1 when below, 0 when it isn't in the loaded range, and null to hide the button.
 */
@Composable
private fun JumpToTodayButton(direction: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Keep the last arrow while the button animates out instead of flipping to the calendar icon.
    val lastDirection = remember { intArrayOf(0) }.also { if (direction != null) it[0] = direction }[0]
    AnimatedVisibility(
        visible = direction != null,
        modifier = modifier,
        enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        ExtendedFloatingActionButton(
            onClick = onClick,
            modifier = Modifier.testTag("jump-to-today"),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = {
                Icon(
                    when (lastDirection) {
                        -1 -> Icons.Rounded.KeyboardArrowUp
                        1 -> Icons.Rounded.KeyboardArrowDown
                        else -> Icons.Rounded.DateRange
                    },
                    contentDescription = null,
                )
            },
            text = { Text("Сегодня") },
        )
    }
}

/** Student / teacher switch as an Expressive connected button group. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScheduleSearchModeButtons(
    mode: ScheduleSearchMode,
    onModeChanged: (ScheduleSearchMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        listOf(ScheduleSearchMode.Group to "Студент", ScheduleSearchMode.Teacher to "Преподаватель")
            .forEachIndexed { index, (option, title) ->
                ToggleButton(
                    checked = mode == option,
                    onCheckedChange = { onModeChanged(option) },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    shapes = if (index == 0) {
                        ButtonGroupDefaults.connectedLeadingButtonShapes()
                    } else {
                        ButtonGroupDefaults.connectedTrailingButtonShapes()
                    },
                ) { Text(title, maxLines = 1) }
            }
    }
}

@Composable
private fun ScheduleHeader(group: String, subtitle: String) {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)) {
        Text(
            group,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            subtitle,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyScheduleState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.DateRange,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Занятий нет",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                "В выбранном диапазоне занятий нет",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EmptyWeekState() {
    Text(
        "На этой неделе занятий нет",
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun WeekDivider(title: String, range: String, isCurrent: Boolean) {
    if (isCurrent) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 16.dp)
                .testTag("current-week")
                .semantics { selected = true },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        range,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    )
                }
            }
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
                title,
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primaryFixed,
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        Text(
            range,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayHeading(title: String, subtitle: String, separated: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (separated) 30.dp else 4.dp, bottom = 16.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primaryFixed,
        )
        Text(
            subtitle,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun pairCountText(count: Int): String {
    val ending = when {
        count % 10 == 1 && count % 100 != 11 -> "пара"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "пары"
        else -> "пар"
    }
    return "$count $ending"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LessonTimelineItem(lesson: ScheduleRow.Lesson, options: AppDisplayOptions) {
    val container = if (lesson.isLecture) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val onContainer = if (lesson.isLecture) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
    val live = lesson.liveLabel != null
    val ongoing = lesson.liveProgress != null
    val stripeColor = when {
        live -> MaterialTheme.colorScheme.primary
        lesson.isLecture -> Color(options.lectureStripeColor)
        else -> Color(options.practiceStripeColor)
    }
    val tileShape = RoundedCornerShape(options.tileCornerRadiusDp.dp)
    val tileColor = if (ongoing) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            .compositeOver(MaterialTheme.colorScheme.surface.copy(alpha = options.tileOpacityPercent / 100f))
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = options.tileOpacityPercent / 100f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = options.itemSpacingDp.dp)
            .background(color = tileColor, shape = tileShape)
            .then(
                if (ongoing) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), tileShape)
                else Modifier,
            )
            .drawBehind {
                if (!options.showStripe) return@drawBehind
                val thickness = 3.dp.toPx()
                val verticalInset = 12.dp.toPx().coerceAtMost(size.height / 3f)
                drawRoundRect(
                    color = stripeColor,
                    topLeft = Offset(6.dp.toPx(), verticalInset),
                    size = Size(thickness, (size.height - verticalInset * 2f).coerceAtLeast(thickness)),
                    cornerRadius = CornerRadius(thickness / 2f),
                )
            }
            .padding(
                start = (options.horizontalPaddingDp + 6).dp,
                end = options.horizontalPaddingDp.dp,
                top = (options.verticalPaddingDp + 8).dp,
                bottom = (options.verticalPaddingDp + 8).dp,
            ),
    ) {
        if (lesson.typeLabel.isNotBlank() || lesson.liveLabel != null) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (options.showTime) LessonTimeColumnWidth else 0.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (lesson.typeLabel.isNotBlank()) {
                    LessonChip(lesson.typeLabel, container, onContainer)
                }
                if (lesson.liveLabel != null) {
                    LessonChip(
                        lesson.liveLabel,
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            if (options.showTime) {
                Column(modifier = Modifier.width(LessonTimeColumnWidth)) {
                    Text(
                        lesson.startTime,
                        style = appTextStyle(MaterialTheme.typography.titleLarge, options.secondaryTextSizeSp, 14, options),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (options.showEndTime) {
                        Text(
                            lesson.endTime,
                            style = appTextStyle(MaterialTheme.typography.bodyLarge, options.secondaryTextSizeSp, 14, options),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    lesson.discipline,
                    modifier = Modifier.fillMaxWidth(),
                    style = appTextStyle(MaterialTheme.typography.titleMedium, options.titleTextSizeSp, 16, options),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = if (options.wrapTitle) options.titleMaxLines else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (options.showClassroom && !lesson.location.isNullOrBlank()) {
                    LessonDetail(Icons.Outlined.Place, lesson.location, options)
                }
                if (!lesson.teachers.isNullOrBlank()) {
                    LessonDetail(
                        if (lesson.teachersIsGroup) Icons.Outlined.AccountBox else Icons.Outlined.Person,
                        lesson.teachers,
                        options,
                    )
                }
                if (!lesson.comment.isNullOrBlank()) {
                    LessonDetail(Icons.Outlined.Info, lesson.comment, options)
                }
            }
        }
        lesson.liveProgress?.let { progress ->
            val animated by animateFloatAsState(
                progress,
                MaterialTheme.motionScheme.slowEffectsSpec(),
                label = "lesson-progress",
            )
            LinearWavyProgressIndicator(
                progress = { animated },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (options.showTime) LessonTimeColumnWidth else 0.dp, top = 12.dp)
                    .semantics { contentDescription = "Прошло ${(progress * 100).roundToInt()}% пары" },
            )
        }
    }
}

@Composable
private fun StudyPlanTimelineItem(
    plan: StudyPlan,
    options: AppDisplayOptions,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    val accent = when (plan.type) {
        com.rassvet.lecturial.model.StudyPlanType.Task -> MaterialTheme.colorScheme.primary
        com.rassvet.lecturial.model.StudyPlanType.Deadline -> MaterialTheme.colorScheme.error
        com.rassvet.lecturial.model.StudyPlanType.Goal -> MaterialTheme.colorScheme.tertiary
        com.rassvet.lecturial.model.StudyPlanType.Event -> Color(plan.color)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = options.itemSpacingDp.dp)
            .alpha(if (plan.isCompleted) 0.58f else 1f)
            .clip(RoundedCornerShape(options.tileCornerRadiusDp.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = options.tileOpacityPercent / 100f))
            .clickable(onClick = onEdit)
            .drawBehind {
                if (!options.showStripe) return@drawBehind
                val thickness = 3.dp.toPx()
                val verticalInset = 12.dp.toPx().coerceAtMost(size.height / 3f)
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(6.dp.toPx(), verticalInset),
                    size = Size(thickness, (size.height - verticalInset * 2f).coerceAtLeast(thickness)),
                    cornerRadius = CornerRadius(thickness / 2f),
                )
            }
            .padding(
                start = (options.horizontalPaddingDp + 6).dp,
                end = options.horizontalPaddingDp.dp,
                top = (options.verticalPaddingDp + 8).dp,
                bottom = (options.verticalPaddingDp + 8).dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (options.showTime) {
            Text(
                if (plan.isAllDay) "Весь день" else plan.dueTime?.format(planTimeFormatter) ?: "—",
                modifier = Modifier.width(LessonTimeColumnWidth),
                style = appTextStyle(MaterialTheme.typography.titleLarge, options.secondaryTextSizeSp, 14, options),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Surface(
            onClick = onToggle,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(30.dp)
                .semantics {
                    contentDescription = if (plan.isCompleted) "Вернуть в активные" else "Отметить выполненным"
                },
            shape = CircleShape,
            color = if (plan.isCompleted) accent else Color.Transparent,
            border = BorderStroke(2.dp, accent),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (plan.isCompleted) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.surface,
                    )
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                "${plan.type.symbol} ${plan.type.title.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
            )
            Text(
                plan.title,
                style = appTextStyle(MaterialTheme.typography.titleMedium, options.titleTextSizeSp, 16, options),
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (plan.isCompleted) TextDecoration.LineThrough else null,
                maxLines = if (options.wrapTitle) options.titleMaxLines else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (plan.note.isNotBlank()) {
                Text(
                    plan.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LessonChip(text: String, container: Color, content: Color) {
    Text(
        text,
        modifier = Modifier
            .wrapContentWidth()
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun LessonDetail(icon: ImageVector, value: String, options: AppDisplayOptions) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = appTextStyle(MaterialTheme.typography.bodyMedium, options.secondaryTextSizeSp, 14, options),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun appTextStyle(
    base: androidx.compose.ui.text.TextStyle,
    sizeSp: Int,
    defaultSizeSp: Int,
    options: AppDisplayOptions,
): androidx.compose.ui.text.TextStyle {
    val family = when (options.fontStyleId) {
        "serif", "serif_bold" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        WidgetFontStyles.defaultId -> base.fontFamily ?: FontFamily.Default
        else -> FontFamily.SansSerif
    }
    val weight = when (options.fontStyleId) {
        "system_bold", "serif_bold" -> FontWeight.Bold
        "system_medium", "condensed" -> FontWeight.Medium
        else -> FontWeight.Normal
    }
    val style = if (options.fontStyleId == "system_italic") FontStyle.Italic else FontStyle.Normal
    val scaledSize = base.fontSize.value * sizeSp / defaultSizeSp
    return base.copy(
        fontFamily = family,
        fontWeight = weight,
        fontStyle = style,
        fontSize = scaledSize.sp,
        letterSpacing = (scaledSize * options.letterSpacingPercent / 100f).sp,
    )
}

@Composable
private fun BreakHint(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.width(20.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private sealed class ScheduleRow {
    abstract val key: String
    abstract val contentType: String

    data class Header(val group: String, val subtitle: String) : ScheduleRow() {
        override val key = "header"
        override val contentType = "header"
    }

    data class Banner(val message: String, val updatedAt: Long?) : ScheduleRow() {
        override val key = "banner"
        override val contentType = "banner"
    }

    data object Empty : ScheduleRow() {
        override val key = "empty"
        override val contentType = "empty"
    }

    data class WeekBreak(
        val weekStart: LocalDate,
        val title: String,
        val range: String,
        val isCurrent: Boolean,
    ) : ScheduleRow() {
        override val key = "week-break-$weekStart"
        override val contentType = "week-break"
    }

    data class EmptyWeek(val weekStart: LocalDate) : ScheduleRow() {
        override val key = "empty-week-$weekStart"
        override val contentType = "empty-week"
    }

    data class Day(
        val title: String,
        val subtitle: String,
        val separated: Boolean,
        val dateKey: String,
    ) : ScheduleRow() {
        override val key = "day-$dateKey"
        override val contentType = "day"
    }

    data class Lesson(
        val eventId: String,
        val typeLabel: String,
        val isLecture: Boolean,
        val startTime: String,
        val endTime: String,
        val discipline: String,
        val location: String?,
        val teachers: String?,
        val comment: String?,
        val liveLabel: String?,
        val liveProgress: Float? = null,
        val teachersIsGroup: Boolean = false,
    ) : ScheduleRow() {
        override val key = "lesson-$eventId"
        override val contentType = "lesson"
    }

    data class Plan(val plan: StudyPlan, val sourcePlan: StudyPlan = plan) : ScheduleRow() {
        override val key = "plan-${plan.id}-${plan.dueDate}"
        override val contentType = "plan"
    }

    data class Rest(val eventId: String, val text: String) : ScheduleRow() {
        override val key = "rest-$eventId"
        override val contentType = "rest"
    }
}

private fun buildScheduleRows(
    scheduleWeeks: List<ScheduleWeek>,
    week: ScheduleWeek?,
    nextWeek: ScheduleWeek?,
    currentWeekStart: LocalDate,
    errorMessage: String?,
    lastUpdatedMillis: Long?,
    group: String,
    selectedDate: LocalDate,
    now: LocalDateTime,
    aliases: Map<String, String>,
    plans: List<StudyPlan>,
    teacherMode: Boolean = false,
): List<ScheduleRow> {
    val rows = ArrayList<ScheduleRow>(96)
    if (errorMessage != null) rows += ScheduleRow.Banner(errorMessage, lastUpdatedMillis)
    val today = now.toLocalDate()
    val weeks = (scheduleWeeks.ifEmpty { listOfNotNull(week, nextWeek) })
        .distinctBy(ScheduleWeek::weekStart)
        .sortedBy(ScheduleWeek::weekStart)
    val todayCount = weeks.firstOrNull { today in it.weekStart..it.weekStart.plusDays(6) }
        ?.eventsFor(today)?.size ?: 0
    val todaySubtitle = if (todayCount == 0) "сегодня • нет пар" else "сегодня • ${pairCountText(todayCount)}"
    rows += ScheduleRow.Header(group, todaySubtitle)
    val sections = weeks.map { scheduleWeek ->
        val start = scheduleWeek.weekStart
        val weekPlans = plans.filter { plan ->
            (0L..6L).any { offset -> plan.occursOn(start.plusDays(offset)) }
        }
        Triple(
            scheduleWeek,
            weekPlans,
            scheduleDays(scheduleWeek, weekPlans, includedDate = selectedDate),
        )
    }
    if (sections.isEmpty()) {
        rows += ScheduleRow.Empty
        return rows
    }
    val live = liveLessonState(
        weeks.flatMap { it.days }.flatMap { it.events },
        today,
        now.toLocalTime(),
    )
    sections.forEach { (scheduleWeek, weekPlans, days) ->
        val start = scheduleWeek.weekStart
        rows += ScheduleRow.WeekBreak(
            weekStart = start,
            title = weekSectionTitle(start, currentWeekStart),
            range = weekRangeLabel(start, start.plusDays(6)),
            isCurrent = start == currentWeekStart,
        )
        if (days.isEmpty()) {
            rows += ScheduleRow.EmptyWeek(start)
        } else {
            rows.addDays(days, weekPlans, today, now, live, startSeparated = false, aliases = aliases, teacherMode = teacherMode)
            if (scheduleWeek.days.all { it.events.isEmpty() } && weekPlans.isEmpty()) {
                rows += ScheduleRow.EmptyWeek(start)
            }
        }
    }
    return rows
}

private fun weekSectionTitle(weekStart: LocalDate, currentWeekStart: LocalDate): String = when (weekStart) {
    currentWeekStart.minusWeeks(1) -> "Предыдущая неделя"
    currentWeekStart -> "Текущая неделя"
    currentWeekStart.plusWeeks(1) -> "Следующая неделя"
    else -> "Неделя"
}

internal fun scheduleInitialRowIndex(
    rowKeys: List<String>,
    selectedDate: LocalDate,
    currentWeekStart: LocalDate,
): Int {
    val exactDay = rowKeys.indexOf("day-$selectedDate")
    if (exactDay >= 0) return exactDay
    val datedRow = rowKeys.indexOfFirst { key ->
        val date = when {
            key.startsWith("day-") -> key.removePrefix("day-").toLocalDateOrNull()
            key.startsWith("week-break-") -> key.removePrefix("week-break-").toLocalDateOrNull()
            else -> null
        }
        date != null && !date.isBefore(selectedDate)
    }
    if (datedRow >= 0) return datedRow
    return rowKeys.indexOf("week-break-$currentWeekStart")
}

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

private fun scheduleDays(
    week: ScheduleWeek?,
    plans: List<StudyPlan>,
    notBefore: LocalDate? = null,
    includedDate: LocalDate? = null,
): List<ScheduleDay> {
    val eventsByDate = week?.days.orEmpty().associateBy(ScheduleDay::date)
    val weekStart = week?.weekStart
    val dateToInclude = includedDate?.takeIf { date ->
        weekStart != null && date in weekStart..weekStart.plusDays(6)
    }
    val recurringDates = if (weekStart == null) emptyList() else (0L..6L)
        .map { weekStart.plusDays(it) }
        .filter { date -> plans.any { it.occursOn(date) } }
    return (eventsByDate.keys + recurringDates + listOfNotNull(dateToInclude))
        .asSequence()
        .filter { notBefore == null || !it.isBefore(notBefore) }
        .map { date -> eventsByDate[date] ?: ScheduleDay(date, emptyList()) }
        .filter { day ->
            day.date == dateToInclude || day.events.isNotEmpty() || plans.any { it.occursOn(day.date) }
        }
        .sortedBy(ScheduleDay::date)
        .toList()
}

private sealed class DayScheduleItem {
    abstract val minuteOfDay: Int

    data class Lesson(val event: ScheduleEvent) : DayScheduleItem() {
        override val minuteOfDay = parseMinutes(event.startTime) ?: (24 * 60 - 1)
    }

    data class Plan(val source: StudyPlan, val occurrence: StudyPlan) : DayScheduleItem() {
        override val minuteOfDay = occurrence.dueTime?.let { it.hour * 60 + it.minute } ?: (24 * 60)
    }
}

/** Share of the pair already passed, or null if it is not going on at [now]. */
internal fun lessonProgressFraction(event: ScheduleEvent, now: LocalDateTime): Float? {
    if (event.date != now.toLocalDate()) return null
    val start = parseMinutes(event.startTime) ?: return null
    val end = parseMinutes(event.endTime) ?: return null
    if (end <= start) return null
    val current = now.hour * 60 + now.minute + now.second / 60f
    if (current < start || current >= end) return null
    return ((current - start) / (end - start)).coerceIn(0f, 1f)
}

private fun ArrayList<ScheduleRow>.addDays(
    days: List<ScheduleDay>,
    plans: List<StudyPlan>,
    today: LocalDate,
    now: LocalDateTime,
    live: LessonLiveState?,
    startSeparated: Boolean,
    aliases: Map<String, String>,
    teacherMode: Boolean = false,
) {
    days.forEachIndexed { dayIndex, day ->
        val isToday = day.date == today
        add(
            ScheduleRow.Day(
                title = if (isToday) {
                    "Сегодня"
                } else {
                    day.date.format(fullWeekdayFormatter).replaceFirstChar { it.uppercase(ru) }
                },
                subtitle = buildString {
                    append(day.date.format(dayMonthFormatter))
                    if (day.events.isNotEmpty()) append(" • ${pairCountText(day.events.size)}")
                    val planCount = plans.count { it.occursOn(day.date) }
                    if (planCount > 0) append(" • ${planCountText(planCount)}")
                },
                separated = startSeparated || dayIndex > 0,
                dateKey = day.date.toString(),
            ),
        )
        val timeline = buildList {
            day.events.forEach { add(DayScheduleItem.Lesson(it)) }
            plans.forEach { plan ->
                plan.occurrenceOn(day.date)?.let { add(DayScheduleItem.Plan(plan, it)) }
            }
        }.sortedWith(compareBy<DayScheduleItem> { it.minuteOfDay }.thenBy { it is DayScheduleItem.Plan })
        timeline.forEachIndexed { index, item ->
            if (item is DayScheduleItem.Plan) {
                add(ScheduleRow.Plan(item.occurrence, item.source))
                return@forEachIndexed
            }
            val event = (item as DayScheduleItem.Lesson).event
            val typeLabel = displayEventType(event.type)
            val location = listOfNotNull(event.classroom, displayAddress(event.address))
                .joinToString(" · ")
                .ifBlank { null }
            val eventKey = "${day.date}-${event.id}"
            add(
                ScheduleRow.Lesson(
                    eventId = eventKey,
                    typeLabel = typeLabel,
                    isLecture = typeLabel == "Лекция",
                    startTime = event.startTime,
                    endTime = event.endTime,
                    discipline = renamedDiscipline(event.discipline, aliases),
                    location = location,
                    // A teacher already knows who teaches; the group is what matters.
                    teachers = if (teacherMode) event.studentGroup else shortenTeachers(event.teachers),
                    teachersIsGroup = teacherMode,
                    comment = displayComment(event.comment),
                    liveLabel = live?.takeIf { it.eventKey == eventKey }?.label,
                    liveProgress = live?.takeIf { it.eventKey == eventKey }
                        ?.let { lessonProgressFraction(event, now) },
                ),
            )
            val next = (timeline.getOrNull(index + 1) as? DayScheduleItem.Lesson)?.event
            if (next != null && shouldShowRestHint(event, next)) {
                add(
                    ScheduleRow.Rest(
                        eventId = eventKey,
                        text = "Перерыв ${event.endTime} – ${next.startTime}",
                    ),
                )
            }
        }
    }
}

private fun planCountText(count: Int): String {
    val ending = when {
        count % 10 == 1 && count % 100 != 11 -> "план"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "плана"
        else -> "планов"
    }
    return "$count $ending"
}

internal fun shouldShowRestHint(event: ScheduleEvent, next: ScheduleEvent): Boolean {
    val currentPair = event.pairNumber
    val nextPair = next.pairNumber
    if (currentPair != null && nextPair != null && nextPair - currentPair > 1) return true
    val gap = minutesBetween(event.endTime, next.startTime) ?: return false
    return gap >= RestGapMinutes
}

internal fun minutesBetween(from: String, to: String): Int? {
    val start = parseMinutes(from) ?: return null
    val end = parseMinutes(to) ?: return null
    return end - start
}

private fun parseMinutes(time: String): Int? {
    val parts = time.split(':')
    if (parts.size < 2) return null
    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    return hours * 60 + minutes
}

@Composable
private fun EmptyDay(date: LocalDate) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Свободный день", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "На ${date.format(DateTimeFormatter.ofPattern("d MMMM", ru))} занятий нет",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OfflineBanner(message: String, updatedAt: Long?) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium).padding(14.dp),
    ) {
        Text("Показаны сохранённые данные", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        if (updatedAt != null) {
            Text(
                "Обновлено: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, ru).format(Date(updatedAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun FullError(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Расписание недоступно", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onRetry) { Text("Повторить") }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 390, heightDp = 844)
@Composable
private fun ScheduleScreenPreview() {
    val date = LocalDate.of(2026, 9, 2)
    val events = listOf(
        ScheduleEvent(
            id = "1",
            date = date,
            startTime = "13:00",
            endTime = "13:45",
            pairNumber = 3,
            discipline = "Этично договариваться",
            type = "Практика",
            classroom = "3.7",
            address = "Научный парк",
            teachers = emptyList(),
            comment = null,
            onlineUrl = null,
        ),
        ScheduleEvent(
            id = "2",
            date = date,
            startTime = "15:00",
            endTime = "16:30",
            pairNumber = 5,
            discipline = "Введение в математический анализ",
            type = "Лекция",
            classroom = "2.6",
            address = "Главный корпус",
            teachers = listOf("Иванов Иван Иванович"),
            comment = null,
            onlineUrl = null,
        ),
        ScheduleEvent(
            id = "3",
            date = date,
            startTime = "16:40",
            endTime = "18:10",
            pairNumber = 6,
            discipline = "Алгоритмы и структуры данных",
            type = "Практика",
            classroom = "1.12",
            address = "Главный корпус",
            teachers = listOf("Петрова Анна Олеговна"),
            comment = null,
            onlineUrl = null,
        ),
    )
    val nextStart = LocalDate.of(2026, 9, 7)
    val nextEvents = listOf(
        events.first().copy(
            id = "next",
            date = nextStart,
            startTime = "09:00",
            endTime = "10:30",
            pairNumber = 1,
            discipline = "Проектный практикум",
            type = "Практика",
        ),
    )
    LecturialTheme {
        LecturialApp(
            state = ScheduleUiState(
                selectedGroup = "ИОП-ИТ-26/1",
                selectedDate = date,
                weekStart = date.with(java.time.DayOfWeek.MONDAY),
                week = ScheduleWeek("ИОП-ИТ-26/1", date.with(java.time.DayOfWeek.MONDAY), listOf(ScheduleDay(date, events))),
                nextWeek = ScheduleWeek("ИОП-ИТ-26/1", nextStart, listOf(ScheduleDay(nextStart, nextEvents))),
                events = events,
                isStarting = false,
                now = LocalDateTime.of(date, LocalTime.of(13, 10)),
            ),
            onGroupSelected = {},
            onDateSelected = {},
            onMoveWeek = {},
            onToday = {},
            onRefresh = {},
            onRetry = {},
        )
    }
}
