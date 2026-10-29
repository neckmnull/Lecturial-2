package com.rassvet.lecturial.ui

import com.rassvet.lecturial.model.TeacherInfo
import androidx.compose.material.icons.outlined.AccountBox
import com.rassvet.lecturial.model.LessonAlertPause
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.MutableState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.rassvet.lecturial.model.ScheduleDay
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.ScheduleWeek
import com.rassvet.lecturial.model.uniqueDisciplines
import com.rassvet.lecturial.model.WidgetFontStyles
import com.rassvet.lecturial.model.AppTheme
import com.rassvet.lecturial.model.AppDisplayOptions
import com.rassvet.lecturial.model.AppUpdateState
import com.rassvet.lecturial.model.CustomAppTheme
import com.rassvet.lecturial.model.ThemePalette
import com.rassvet.lecturial.model.StudyPlan
import com.rassvet.lecturial.ui.theme.LecturialTheme
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

private val ru = Locale.forLanguageTag("ru")
private val LessonTimeColumnWidth = 68.dp
private val dayMonthFormatter = DateTimeFormatter.ofPattern("d MMMM", ru)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ScheduleScreen(
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
    onWidgetMatchAppChanged: (Boolean) -> Unit,
    onWidgetBackgroundOpacityChanged: (Int) -> Unit,
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
    openLesson: OpenLessonRequest? = null,
    onOpenLessonConsumed: (Long) -> Unit = {},
) {
    // Saved: rotating the phone recreates the activity, and a plain remember went back to
    // the schedule, even in the middle of a statistics run.
    var mainSection by rememberSaveable { mutableStateOf(MainSection.Schedule) }
    var settingsSection by rememberSaveable { mutableStateOf<SettingsDestination?>(null) }
    // The lesson the list scrolls to and briefly lights up.
    var focusedLesson by remember { mutableStateOf<String?>(null) }
    var focusShown by remember { mutableStateOf(false) }
    LaunchedEffect(openLesson?.key) {
        val request = openLesson ?: return@LaunchedEffect
        settingsSection = null
        mainSection = MainSection.Schedule
        if (request.date.with(DayOfWeek.MONDAY) == state.weekStart) onDateSelected(request.date) else onToday()
        focusedLesson = request.rowKey
        focusShown = false
        onOpenLessonConsumed(request.key)
    }
    // Its own effect: consuming the request above restarts that one, which would cancel
    // the wait. The highlight fades after a few seconds, or if the lesson never shows up.
    LaunchedEffect(focusedLesson, focusShown) {
        if (focusedLesson == null) return@LaunchedEffect
        // Lit for a few seconds once it is on screen: a cold start can take longer than that
        // to load the week. A lesson that never shows up is given up on later.
        delay(if (focusShown) FOCUS_VISIBLE_MILLIS else FOCUS_TIMEOUT_MILLIS)
        focusedLesson = null
        focusShown = false
    }
    val screenEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val predictiveBackProgress = remember { Animatable(0f) }
    LaunchedEffect(openPlanId, openPlanRequestKey) {
        if (openPlanId != null) mainSection = MainSection.Plans
    }
    // The update notification lands on Settings → About.
    LaunchedEffect(openUpdatesKey) {
        if (openUpdatesKey > 0L) {
            mainSection = MainSection.Settings
            settingsSection = SettingsDestination.About
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
    // Kept outside the tab transition: coming back to the schedule reuses the rows and the
    // scroll position instead of rebuilding the list and jumping to today a frame later.
    val scheduleRows = remember(
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
    val scheduleListState = rememberLazyListState()
    val scheduleScrolledFor = remember { mutableStateOf<String?>(null) }
    PredictiveBackHandler(enabled = mainSection == MainSection.Settings) { backEvents ->
        try {
            backEvents.collect { event -> predictiveBackProgress.snapTo(event.progress) }
            when (settingsSection) {
                SettingsDestination.Privacy -> settingsSection = SettingsDestination.About
                SettingsDestination.Widget -> settingsSection = SettingsDestination.Appearance
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
                        fun settingsDepth(section: SettingsDestination?): Int = when (section) {
                            null -> 1
                            SettingsDestination.Widget, SettingsDestination.Privacy -> 3
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
                    SettingsDestination.LanguageTeacher -> LanguageTeacherSelectionScreen(
                        state = state,
                        onSelected = { teacher ->
                            onLanguageTeacherSelected(teacher)
                            settingsSection = null
                        },
                        onRetry = onRetry,
                        onBack = { settingsSection = null },
                        backContentDescription = "Назад к настройкам",
                    )
                    SettingsDestination.Appearance -> AppearanceSettingsScreen(
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
                        onOpenWidgetSettings = { settingsSection = SettingsDestination.Widget },
                        onBack = { settingsSection = null },
                    )
                    SettingsDestination.Widget -> WidgetSettingsScreen(
                        modifier = nestedModifier,
                        state = state,
                        onBack = { settingsSection = SettingsDestination.Appearance },
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
                        onMatchAppChanged = onWidgetMatchAppChanged,
                        onBackgroundOpacityChanged = onWidgetBackgroundOpacityChanged,
                    )
                    SettingsDestination.Names -> LessonNamesScreen(
                        modifier = nestedModifier,
                        names = uniqueDisciplines(*state.loadedScheduleWeeks().toTypedArray()),
                        aliases = state.lessonAliases,
                        onAliasChanged = onLessonAliasChanged,
                        onBack = { settingsSection = null },
                    )
                    SettingsDestination.Testing -> NotificationTestingScreen(
                        modifier = nestedModifier,
                        onBack = { settingsSection = null },
                    )
                    SettingsDestination.About -> AboutScreen(
                        modifier = nestedModifier,
                        updateState = state.appUpdateState,
                        onBack = { settingsSection = null },
                        onOpenPrivacy = { settingsSection = SettingsDestination.Privacy },
                        onCheckForUpdates = onCheckForUpdates,
                        onDownloadUpdate = onDownloadUpdate,
                        onInstallUpdate = onInstallUpdate,
                    )
                    SettingsDestination.Privacy -> PrivacyScreen(
                        modifier = nestedModifier,
                        onBack = { settingsSection = SettingsDestination.About },
                    )
                    null -> AppSettingsScreen(
                        state = state,
                        onGroupSelected = onGroupSelected,
                        onTeacherSelected = onTeacherSelected,
                        onSearchModeChanged = onSearchModeChanged,
                        onGroupSearchQueryChanged = onGroupSearchQueryChanged,
                        onGroupsRetry = onGroupsRetry,
                        onOpenLanguageTeacher = { settingsSection = SettingsDestination.LanguageTeacher },
                        onOpenAppearance = { settingsSection = SettingsDestination.Appearance },
                        onOpenLessonNames = { settingsSection = SettingsDestination.Names },
                        onOpenTesting = { settingsSection = SettingsDestination.Testing },
                        onOpenAbout = { settingsSection = SettingsDestination.About },
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
                        rows = scheduleRows,
                        listState = scheduleListState,
                        scrolledForState = scheduleScrolledFor,
                        focusedLesson = focusedLesson,
                        onFocusShown = { focusShown = true },
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

private const val FOCUS_TIMEOUT_MILLIS = 15_000L
private const val FOCUS_VISIBLE_MILLIS = 4_000L
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScheduleContent(
    state: ScheduleUiState,
    rows: List<ScheduleRow>,
    listState: LazyListState,
    scrolledForState: MutableState<String?>,
    focusedLesson: String? = null,
    onFocusShown: () -> Unit = {},
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
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                val scrollSession = "${state.selectedGroup}|${state.weekStart}|${state.selectedDate}|${state.isLoading}"
                var scrolledFor by scrolledForState
                var scrolledToLesson by remember { mutableStateOf<String?>(null) }
                val listAlpha = remember { Animatable(1f) }
                val listScale = remember { Animatable(1f) }
                LaunchedEffect(rows, scrollSession, focusedLesson) {
                    if (state.isLoading) return@LaunchedEffect
                    // A lesson opened from the widget wins over the usual jump to the day.
                    val focusIndex = focusedLesson?.let { key -> rows.indexOfFirst { it.key == key } } ?: -1
                    if (focusIndex >= 0 && scrolledToLesson != focusedLesson) {
                        // Once per lesson, with one row of context above it.
                        scrolledToLesson = focusedLesson
                        listState.revealItem((focusIndex - 1).coerceAtLeast(0), listAlpha, listScale)
                        onFocusShown()
                        scrolledFor = scrollSession
                        return@LaunchedEffect
                    }
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
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("schedule-list")
                        .graphicsLayer {
                            alpha = listAlpha.value
                            scaleX = listScale.value
                            scaleY = listScale.value
                        },
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
                            is ScheduleRow.Lesson -> LessonTimelineItem(row, state.appDisplayOptions, highlighted = row.key == focusedLesson)
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
                            scope.launch { listState.revealItem(todayRowIndex, listAlpha, listScale) }
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
 * Brings row [index] into view the Material way. Rows within about a screen scroll there;
 * farther ones use the "fade through" pattern of the Material motion guide: the list fades
 * out, jumps while unseen and fades back in, growing slightly, instead of racing past
 * dozens of cards. [alpha] and [scale] drive the list's layer.
 */
private suspend fun LazyListState.revealItem(index: Int, alpha: Animatable<Float, *>, scale: Animatable<Float, *>) {
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return scrollToItem(index)
    val first = visible.first().index
    val last = visible.last().index
    val screen = (last - first).coerceAtLeast(1)
    if (index in (first - screen)..(last + screen)) return animateScrollToItem(index)
    try {
        alpha.animateTo(0f, tween(FADE_THROUGH_OUT_MILLIS, easing = EmphasizedAccelerate))
        scrollToItem(index)
        scale.snapTo(FADE_THROUGH_START_SCALE)
        coroutineScope {
            launch { alpha.animateTo(1f, tween(FADE_THROUGH_IN_MILLIS, easing = EmphasizedDecelerate)) }
            launch { scale.animateTo(1f, tween(FADE_THROUGH_IN_MILLIS, easing = EmphasizedDecelerate)) }
        }
    } finally {
        // A second tap mid-way cancels this one; the list must not stay faded.
        withContext(NonCancellable) {
            alpha.snapTo(1f)
            scale.snapTo(1f)
        }
    }
}

// Durations and curves of the Material 3 fade through and emphasized easing.
private const val FADE_THROUGH_OUT_MILLIS = 90
private const val FADE_THROUGH_IN_MILLIS = 210
private const val FADE_THROUGH_START_SCALE = 0.92f
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

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
        shape = MaterialTheme.shapes.largeIncreased,
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
            shape = MaterialTheme.shapes.largeIncreased,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LessonTimelineItem(lesson: ScheduleRow.Lesson, options: AppDisplayOptions, highlighted: Boolean = false) {
    // Lights up a lesson opened from the widget, then fades back.
    val highlight by animateFloatAsState(
        if (highlighted) 1f else 0f,
        MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "lesson-highlight",
    )
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
            .then(
                if (highlight > 0f) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f * highlight), tileShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = highlight), tileShape)
                } else {
                    Modifier
                },
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
                    // The wave moves every frame; a layer keeps that from redrawing the list.
                    .graphicsLayer()
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
internal fun LessonChip(text: String, container: Color, content: Color) {
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

internal fun appTextStyle(
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

@Composable
private fun EmptyDay(date: LocalDate) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Свободный день", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "На ${date.format(dayMonthFormatter)} занятий нет",
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
