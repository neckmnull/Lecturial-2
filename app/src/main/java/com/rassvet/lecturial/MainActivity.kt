package com.rassvet.lecturial

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rassvet.lecturial.notify.LessonAlerts
import com.rassvet.lecturial.model.AppUpdateState
import com.rassvet.lecturial.ui.LecturialApp
import com.rassvet.lecturial.ui.ScheduleViewModel
import com.rassvet.lecturial.ui.theme.LecturialTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val splashStartedAt = SystemClock.elapsedRealtime()
    private var pendingUpdate: AppUpdateState.ReadyToInstall? = null
    private var installationErrorHandler: ((AppUpdateState.ReadyToInstall, String) -> Unit)? = null
    private var openPlanRequest by mutableStateOf<OpenPlanRequest?>(null)
    private var openPlanSequence = 0L
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { LessonAlerts.sync(this) }
    private val unknownSourcesSettings = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val update = pendingUpdate ?: return@registerForActivityResult
        if (packageManager.canRequestPackageInstalls()) {
            openPackageInstaller(update)
        } else {
            reportInstallationError(update, "Разрешите Lecturial 2 устанавливать неизвестные приложения")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNavigationIntent(intent)
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
                    app.updatesDirectory,
                ),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            // Every return to the app pulls a fresh schedule.
            LifecycleStartEffect(viewModel) {
                viewModel.refreshOnEntry()
                onStopOrDispose { }
            }
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
                    openPlanId = openPlanRequest?.planId,
                    openPlanRequestKey = openPlanRequest?.key ?: 0L,
                    onOpenPlanConsumed = { key ->
                        if (openPlanRequest?.key == key) openPlanRequest = null
                    },
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
                    onLessonAlertsPause = viewModel::pauseLessonAlerts,
                    onScheduleWeeksBackChanged = viewModel::setScheduleWeeksBack,
                    onScheduleWeeksForwardChanged = viewModel::setScheduleWeeksForward,
                    onCheckForUpdates = viewModel::checkForUpdates,
                    onDownloadUpdate = viewModel::downloadUpdate,
                    onInstallUpdate = { update ->
                        requestUpdateInstallation(update, viewModel::reportInstallationError)
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        val planId = intent?.getStringExtra(EXTRA_PLAN_ID)?.takeIf(String::isNotBlank) ?: return
        openPlanSequence += 1
        openPlanRequest = OpenPlanRequest(openPlanSequence, planId)
        intent.removeExtra(EXTRA_PLAN_ID)
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

    private fun requestUpdateInstallation(
        update: AppUpdateState.ReadyToInstall,
        onError: (AppUpdateState.ReadyToInstall, String) -> Unit,
    ) {
        pendingUpdate = update
        installationErrorHandler = onError
        if (packageManager.canRequestPackageInstalls()) {
            openPackageInstaller(update)
            return
        }
        runCatching {
            unknownSourcesSettings.launch(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName"),
                ),
            )
        }.onFailure {
            reportInstallationError(update, "Не удалось открыть разрешение на установку приложений")
        }
    }

    private fun openPackageInstaller(update: AppUpdateState.ReadyToInstall) {
        runCatching {
            val updatesDirectory = (application as LecturialApplication).updatesDirectory.canonicalFile
            val apk = File(update.apkPath).canonicalFile
            require(apk.isFile && apk.parentFile == updatesDirectory) { "APK-файл обновления недоступен" }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }.onFailure { failure ->
            reportInstallationError(
                update,
                failure.message ?: "Не удалось открыть системный установщик",
            )
        }
    }

    private fun reportInstallationError(update: AppUpdateState.ReadyToInstall, message: String) {
        pendingUpdate = null
        installationErrorHandler?.invoke(update, message)
    }

    private data class OpenPlanRequest(val key: Long, val planId: String)

    companion object {
        const val EXTRA_PLAN_ID = "com.rassvet.lecturial.extra.PLAN_ID"

        fun openPlanIntent(context: Context, planId: String): Intent =
            Intent(context, MainActivity::class.java)
                .setAction("com.rassvet.lecturial.action.OPEN_PLAN")
                .putExtra(EXTRA_PLAN_ID, planId)
    }
}
