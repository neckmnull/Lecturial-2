package com.rassvet.lecturial

import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rassvet.lecturial.notify.LessonAlerts
import com.rassvet.lecturial.ui.LecturialApp
import com.rassvet.lecturial.ui.ScheduleViewModel
import com.rassvet.lecturial.ui.theme.LecturialTheme

class MainActivity : ComponentActivity() {
    private val splashStartedAt = SystemClock.elapsedRealtime()
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { LessonAlerts.sync(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 31 && ValueAnimator.areAnimatorsEnabled()) {
            window.decorView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (SystemClock.elapsedRealtime() - splashStartedAt < 200L) return false
                    window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            })
        }
        enableEdgeToEdge()
        val app = application as LecturialApplication
        setContent {
            val viewModel: ScheduleViewModel = viewModel(
                factory = ScheduleViewModel.Factory(
                    app.repository,
                    app.preferences,
                    app.studyPlanRepository,
                    app.updateChecker,
                ),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            LecturialTheme(
                appTheme = state.appTheme,
                customTheme = state.customThemes.firstOrNull { it.id == state.selectedCustomThemeId },
                darkTheme = state.darkTheme,
                builtInOverride = state.builtInThemeOverrides.firstOrNull {
                    it.theme == state.appTheme && it.isDark == state.darkTheme
                }?.palette,
            ) {
                LecturialApp(
                    state = state,
                    onGroupSelected = viewModel::selectGroup,
                    onGroupSearchQueryChanged = viewModel::setGroupSearchQuery,
                    onGroupsRetry = { viewModel.requestGroups() },
                    onLanguageTeacherSelected = viewModel::selectLanguageTeacher,
                    onDateSelected = viewModel::selectDate,
                    onMoveWeek = viewModel::moveWeek,
                    onToday = viewModel::goToToday,
                    onRefresh = viewModel::refresh,
                    onRetry = viewModel::retry,
                    onLectureStripeColorSelected = viewModel::setLectureStripeColor,
                    onPracticeStripeColorSelected = viewModel::setPracticeStripeColor,
                    onWidgetShowClassroomChanged = viewModel::setWidgetShowClassroom,
                    onWidgetShowTimeChanged = viewModel::setWidgetShowTime,
                    onWidgetShowStripeChanged = viewModel::setWidgetShowStripe,
                    onWidgetShowEndTimeChanged = viewModel::setWidgetShowEndTime,
                    onWidgetWrapTitleChanged = viewModel::setWidgetWrapTitle,
                    onWidgetOuterPaddingChanged = viewModel::setWidgetOuterPadding,
                    onWidgetHorizontalPaddingChanged = viewModel::setWidgetHorizontalPadding,
                    onWidgetVerticalPaddingChanged = viewModel::setWidgetVerticalPadding,
                    onWidgetItemSpacingChanged = viewModel::setWidgetItemSpacing,
                    onWidgetTitleTextSizeChanged = viewModel::setWidgetTitleTextSize,
                    onWidgetSecondaryTextSizeChanged = viewModel::setWidgetSecondaryTextSize,
                    onWidgetFontStyleChanged = viewModel::setWidgetFontStyle,
                    onWidgetLetterSpacingChanged = viewModel::setWidgetLetterSpacing,
                    onWidgetTitleMaxLinesChanged = viewModel::setWidgetTitleMaxLines,
                    onLessonAliasChanged = viewModel::setLessonAlias,
                    onAppThemeSelected = viewModel::setAppTheme,
                    onDarkThemeChanged = viewModel::setDarkTheme,
                    onBuiltInThemeSaved = viewModel::saveBuiltInThemeOverride,
                    onBuiltInThemeReset = viewModel::resetBuiltInThemeOverride,
                    onCustomThemeSelected = viewModel::selectCustomTheme,
                    onCustomThemeSaved = viewModel::saveCustomTheme,
                    onCustomThemeDeleted = viewModel::deleteCustomTheme,
                    onAppDisplayOptionsChanged = viewModel::setAppDisplayOptions,
                    onAppDisplayOptionsReset = viewModel::resetAppDisplayOptions,
                    onStudyPlanSaved = viewModel::saveStudyPlan,
                    onStudyPlanCompletedChanged = viewModel::setStudyPlanCompleted,
                    onStudyPlanDeleted = viewModel::deleteStudyPlan,
                    onDeadlineReminderDaysChanged = viewModel::setDeadlineReminderDays,
                    onCheckForUpdates = viewModel::checkForUpdates,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        requestLessonNotifications()
        LessonAlerts.sync(this)
    }

    private fun requestLessonNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val prefs = getSharedPreferences("lesson_alerts", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
