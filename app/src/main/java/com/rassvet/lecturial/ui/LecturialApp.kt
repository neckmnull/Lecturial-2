package com.rassvet.lecturial.ui

import com.rassvet.lecturial.model.TeacherInfo
import com.rassvet.lecturial.model.LessonAlertPause
import androidx.compose.foundation.layout.RowScope
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.provider.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rassvet.lecturial.model.forLanguageTeacher
import com.rassvet.lecturial.model.AppTheme
import com.rassvet.lecturial.model.AppDisplayOptions
import com.rassvet.lecturial.model.AppUpdateState
import com.rassvet.lecturial.model.CustomAppTheme
import com.rassvet.lecturial.model.ThemePalette
import com.rassvet.lecturial.model.StudyPlan
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ru = Locale.forLanguageTag("ru")
private val fullWeekdayFormatter = DateTimeFormatter.ofPattern("EEEE", ru)
private val dayMonthFormatter = DateTimeFormatter.ofPattern("d MMMM", ru)
private val LessonTimeColumnWidth = 68.dp
private const val RestGapMinutes = 40
// Expressive spatial/effects springs; slightly more damped for full-screen slides.
internal fun <T> screenSlideSpec() = spring<T>(dampingRatio = 0.9f, stiffness = 380f)

internal fun <T> screenFadeSpec() = spring<T>(dampingRatio = 1f, stiffness = 1600f)

/** A lesson to open the schedule on, e.g. one tapped in the widget; [key] tells requests apart. */
data class OpenLessonRequest(val key: Long, val date: LocalDate, val eventId: String) {
    /** The schedule row of this lesson. */
    val rowKey: String get() = "lesson-$date-$eventId"
}

internal enum class SettingsDestination { LanguageTeacher, Appearance, Widget, Names, Outlook, GoogleCalendar, Testing, About, Privacy }
internal enum class MainSection { Schedule, Plans, Statistics, Settings }
internal enum class AppearanceSection { Settings, BuiltInEditor, CustomEditor }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LecturialApp(
    state: ScheduleUiState,
    openPlanId: String? = null,
    openPlanRequestKey: Long = 0L,
    openUpdatesKey: Long = 0L,
    openOutlookKey: Long = 0L,
    onOpenPlanConsumed: (Long) -> Unit = {},
    openLesson: OpenLessonRequest? = null,
    onOpenLessonConsumed: (Long) -> Unit = {},
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
    onWidgetMatchAppChanged: (Boolean) -> Unit = {},
    onWidgetBackgroundOpacityChanged: (Int) -> Unit = {},
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
        // A required update closes the app off until it is installed; Back still leaves the app.
        state.requiredUpdateVersion?.let { version ->
            RequiredUpdateScreen(
                version = version,
                updateState = state.appUpdateState,
                onCheck = onCheckForUpdates,
                onDownload = onDownloadUpdate,
                onInstall = onInstallUpdate,
            )
            return@MaterialYouBackground
        }
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
                else -> {
                    // Filtered once per schedule change: settings sliders and the minute tick
                    // copy the state without touching the weeks, so these keys stay the same.
                    val teacher = state.languageTeacher
                    val week = remember(state.week, teacher) { state.week?.forLanguageTeacher(teacher) }
                    val nextWeek = remember(state.nextWeek, teacher) { state.nextWeek?.forLanguageTeacher(teacher) }
                    val scheduleWeeks = remember(state.scheduleWeeks, teacher) {
                        state.scheduleWeeks.map { it.forLanguageTeacher(teacher) }
                    }
                    val events = remember(state.events, teacher) {
                        state.events.filter { com.rassvet.lecturial.model.matchesLanguageTeacher(it.discipline, it.teachers, teacher) }
                    }
                    ScheduleScreen(
                        state.copy(week = week, nextWeek = nextWeek, scheduleWeeks = scheduleWeeks, events = events),
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
                        onWidgetMatchAppChanged,
                        onWidgetBackgroundOpacityChanged,
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
                        openOutlookKey = openOutlookKey,
                        openLesson = openLesson,
                        onOpenLessonConsumed = onOpenLessonConsumed,
                    )
                }
            }
        }
    }
}

@Composable
internal fun LecturialBottomBar(
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
