package com.rassvet.lecturial.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rassvet.lecturial.data.ScheduleRepository
import com.rassvet.lecturial.data.StudyPlanRepository
import com.rassvet.lecturial.data.local.UserPreferences
import com.rassvet.lecturial.data.network.GitHubUpdateChecker
import com.rassvet.lecturial.BuildConfig
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.ScheduleWeek
import com.rassvet.lecturial.model.WidgetStripePalette
import com.rassvet.lecturial.model.WidgetFontStyles
import com.rassvet.lecturial.model.AppTheme
import com.rassvet.lecturial.model.AppDisplayOptions
import com.rassvet.lecturial.model.CustomAppTheme
import com.rassvet.lecturial.model.BuiltInThemeOverride
import com.rassvet.lecturial.model.ThemePalette
import com.rassvet.lecturial.model.StudyPlan
import com.rassvet.lecturial.model.DeadlineReminder
import com.rassvet.lecturial.model.AppUpdateState
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.ZoneId
import com.rassvet.lecturial.model.LessonAlertPause

data class ScheduleUiState(
    val appTheme: AppTheme = AppTheme.default,
    val darkTheme: Boolean = true,
    val customThemes: List<CustomAppTheme> = emptyList(),
    val builtInThemeOverrides: List<BuiltInThemeOverride> = emptyList(),
    val selectedCustomThemeId: String? = null,
    val appDisplayOptions: AppDisplayOptions = AppDisplayOptions(),
    val selectedGroup: String? = null,
    val languageTeacher: String? = null,
    val choosingLanguageTeacher: Boolean = false,
    val selectedDate: LocalDate = LocalDate.now(),
    val weekStart: LocalDate = LocalDate.now().with(DayOfWeek.MONDAY),
    val week: ScheduleWeek? = null,
    val nextWeek: ScheduleWeek? = null,
    val scheduleWeeks: List<ScheduleWeek> = emptyList(),
    val scheduleWeeksBack: Int = UserPreferences.DEFAULT_SCHEDULE_WEEKS_BACK,
    val scheduleWeeksForward: Int = UserPreferences.DEFAULT_SCHEDULE_WEEKS_FORWARD,
    val events: List<ScheduleEvent> = emptyList(),
    val availableGroups: List<String> = emptyList(),
    val groupSearchQuery: String = "",
    val isGroupsLoading: Boolean = false,
    val groupsError: String? = null,
    val isStarting: Boolean = true,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    /** True while a refresh the user should see is running: pull-to-refresh or entering the app. */
    val isManualRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val showingCachedData: Boolean = false,
    val lastUpdatedMillis: Long? = null,
    val lectureStripeColor: Int = WidgetStripePalette.defaultLecture,
    val practiceStripeColor: Int = WidgetStripePalette.defaultPractice,
    val widgetShowTime: Boolean = true,
    val widgetShowStripe: Boolean = true,
    val widgetShowClassroom: Boolean = true,
    val widgetShowEndTime: Boolean = true,
    val widgetWrapTitle: Boolean = true,
    val widgetOuterPaddingDp: Int = 9,
    val widgetHorizontalPaddingDp: Int = 12,
    val widgetVerticalPaddingDp: Int = 6,
    val widgetItemSpacingDp: Int = 3,
    val widgetTitleTextSizeSp: Int = 12,
    val widgetSecondaryTextSizeSp: Int = 9,
    val widgetFontStyleId: String = WidgetFontStyles.defaultId,
    val widgetLetterSpacingPercent: Int = 0,
    val widgetTitleMaxLines: Int = 2,
    val lessonAliases: Map<String, String> = emptyMap(),
    val studyPlans: List<StudyPlan> = emptyList(),
    val deadlineReminderDays: Set<Int> = DeadlineReminder.default,
    val lessonAlertsPausedUntil: Long? = null,
    val appUpdateState: AppUpdateState = AppUpdateState.Idle,
    val now: LocalDateTime = LocalDateTime.now(),
)

class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val preferences: UserPreferences,
    private val studyPlanRepository: StudyPlanRepository,
    private val updateChecker: GitHubUpdateChecker,
    private val updatesDirectory: File,
    private val today: () -> LocalDate = LocalDate::now,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {
    private val _state = MutableStateFlow(ScheduleUiState())
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var selectionJob: Job? = null
    private var groupsJob: Job? = null
    private var updateJob: Job? = null
    private var entryRefreshPending = false

    init {
        viewModelScope.launch {
            studyPlanRepository.plans.collectLatest { plans ->
                _state.update { it.copy(studyPlans = plans) }
            }
        }
        viewModelScope.launch {
            val group = preferences.selectedGroup.first()
            val savedTeacher = if (group == null) null else preferences.languageTeacher(group).first()
            val stripeColors = preferences.widgetStripeColors.first()
            val displayOptions = preferences.widgetDisplayOptions.first()
            val aliases = preferences.lessonAliases.first()
            val appTheme = preferences.appTheme.first()
            val darkTheme = preferences.darkTheme.first()
            val customThemes = preferences.customThemes.first()
            val builtInThemeOverrides = preferences.builtInThemeOverrides.first()
            val selectedCustomThemeId = preferences.selectedCustomThemeId.first()
            val appDisplayOptions = preferences.appDisplayOptions.first()
            val deadlineReminderDays = preferences.deadlineReminderDays.first()
            val lessonAlertsPausedUntil = preferences.lessonAlertsPausedUntil.first()
            val scheduleWeeksBack = preferences.scheduleWeeksBack.first()
            val scheduleWeeksForward = preferences.scheduleWeeksForward.first()
            val validCustomThemeId = selectedCustomThemeId?.takeIf { id -> customThemes.any { it.id == id } }
            val date = today()
            _state.update {
                it.copy(
                    appTheme = if (appTheme == AppTheme.Custom && validCustomThemeId == null) AppTheme.default else appTheme,
                    darkTheme = darkTheme,
                    customThemes = customThemes,
                    builtInThemeOverrides = builtInThemeOverrides,
                    selectedCustomThemeId = validCustomThemeId,
                    appDisplayOptions = appDisplayOptions,
                    deadlineReminderDays = deadlineReminderDays,
                    lessonAlertsPausedUntil = lessonAlertsPausedUntil,
                    scheduleWeeksBack = scheduleWeeksBack,
                    scheduleWeeksForward = scheduleWeeksForward,
                    selectedGroup = group,
                    languageTeacher = savedTeacher,
                    choosingLanguageTeacher = group != null && savedTeacher == null,
                    selectedDate = date,
                    weekStart = date.with(DayOfWeek.MONDAY),
                    isStarting = false,
                    now = now(),
                    lectureStripeColor = stripeColors.lecture,
                    practiceStripeColor = stripeColors.practice,
                    widgetShowTime = displayOptions.showTime,
                    widgetShowStripe = displayOptions.showStripe,
                    widgetShowClassroom = displayOptions.showClassroom,
                    widgetShowEndTime = displayOptions.showEndTime,
                    widgetWrapTitle = displayOptions.wrapTitle,
                    widgetOuterPaddingDp = displayOptions.outerPaddingDp,
                    widgetHorizontalPaddingDp = displayOptions.horizontalPaddingDp,
                    widgetVerticalPaddingDp = displayOptions.verticalPaddingDp,
                    widgetItemSpacingDp = displayOptions.itemSpacingDp,
                    widgetTitleTextSizeSp = displayOptions.titleTextSizeSp,
                    widgetSecondaryTextSizeSp = displayOptions.secondaryTextSizeSp,
                    widgetFontStyleId = displayOptions.fontStyleId,
                    widgetLetterSpacingPercent = displayOptions.letterSpacingPercent,
                    widgetTitleMaxLines = displayOptions.titleMaxLines,
                    lessonAliases = aliases,
                )
            }
            if (group != null) {
                loadCurrentWeek(showIndicator = entryRefreshPending)
                entryRefreshPending = false
            } else {
                requestGroups()
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                // The schedule only shows minutes; skipping same-minute ticks avoids
                // rebuilding every row and recomposing the whole screen four times a minute.
                val minute = now().truncatedTo(ChronoUnit.MINUTES)
                _state.update { if (it.now.truncatedTo(ChronoUnit.MINUTES) == minute) it else it.copy(now = minute) }
            }
        }
    }

    fun selectGroup(group: String) {
        val normalizedGroup = group.trim()
        require(normalizedGroup.isNotEmpty())
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch { preferences.setSelectedGroup(normalizedGroup) }
        val date = today()
        _state.update {
            it.copy(
                selectedGroup = normalizedGroup,
                languageTeacher = null,
                choosingLanguageTeacher = true,
                selectedDate = date,
                weekStart = date.with(DayOfWeek.MONDAY),
                week = null,
                nextWeek = null,
                scheduleWeeks = emptyList(),
                events = emptyList(),
                errorMessage = null,
                showingCachedData = false,
                lastUpdatedMillis = null,
            )
        }
        loadCurrentWeek()
    }

    fun checkForUpdates() {
        if (_state.value.appUpdateState is AppUpdateState.Checking ||
            _state.value.appUpdateState is AppUpdateState.Downloading
        ) return
        updateJob?.cancel()
        _state.update { it.copy(appUpdateState = AppUpdateState.Checking) }
        updateJob = viewModelScope.launch {
            try {
                val result = updateChecker.check(BuildConfig.VERSION_NAME)
                _state.update { it.copy(appUpdateState = result) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _state.update {
                    it.copy(
                        appUpdateState = AppUpdateState.Error(
                            failure.message ?: "Не удалось проверить обновления",
                        ),
                    )
                }
            }
        }
    }

    fun downloadUpdate() {
        val update = when (val current = _state.value.appUpdateState) {
            is AppUpdateState.Available -> current
            is AppUpdateState.DownloadError -> current.update
            else -> return
        }
        updateJob?.cancel()
        _state.update { it.copy(appUpdateState = AppUpdateState.Downloading(update, 0)) }
        updateJob = viewModelScope.launch {
            try {
                val safeVersion = update.version.replace(Regex("[^0-9A-Za-z._-]"), "_")
                val destination = File(updatesDirectory, "Lecturial-2-$safeVersion.apk")
                val apk = updateChecker.downloadApk(update, destination) { progress ->
                    _state.update { current ->
                        val downloading = current.appUpdateState as? AppUpdateState.Downloading
                        if (downloading?.update != update) current else current.copy(
                            appUpdateState = downloading.copy(progressPercent = progress),
                        )
                    }
                }
                _state.update {
                    it.copy(
                        appUpdateState = AppUpdateState.ReadyToInstall(
                            version = update.version,
                            releaseUrl = update.releaseUrl,
                            apkPath = apk.absolutePath,
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _state.update {
                    it.copy(
                        appUpdateState = AppUpdateState.DownloadError(
                            update = update,
                            message = failure.message ?: "Не удалось скачать обновление",
                        ),
                    )
                }
            }
        }
    }

    fun reportInstallationError(update: AppUpdateState.ReadyToInstall, message: String) {
        _state.update {
            it.copy(appUpdateState = AppUpdateState.InstallationError(update, message))
        }
    }

    fun setGroupSearchQuery(query: String) {
        if (_state.value.groupSearchQuery == query) return
        _state.update {
            it.copy(
                groupSearchQuery = query,
                availableGroups = emptyList(),
                groupsError = null,
            )
        }
        requestGroups(debounce = true)
    }

    fun requestGroups(debounce: Boolean = false) {
        val query = _state.value.groupSearchQuery
        groupsJob?.cancel()
        groupsJob = viewModelScope.launch {
            if (debounce) delay(350)
            _state.update { current ->
                if (current.groupSearchQuery != query) current else current.copy(
                    isGroupsLoading = true,
                    groupsError = null,
                )
            }
            try {
                val groups = repository.groups(query)
                _state.update { current ->
                    if (current.groupSearchQuery != query) current else current.copy(
                        availableGroups = groups,
                        isGroupsLoading = false,
                        groupsError = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _state.update { current ->
                    if (current.groupSearchQuery != query) current else current.copy(
                        isGroupsLoading = false,
                        groupsError = failure.message ?: "Не удалось загрузить группы",
                    )
                }
            }
        }
    }

    fun selectLanguageTeacher(teacher: String?) {
        val group = _state.value.selectedGroup ?: return
        _state.update { it.copy(languageTeacher = teacher.orEmpty(), choosingLanguageTeacher = false) }
        viewModelScope.launch {
            selectionJob?.join()
            preferences.setLanguageTeacher(group, teacher)
        }
    }

    fun selectDate(date: LocalDate) {
        val current = _state.value
        if (date.with(DayOfWeek.MONDAY) != current.weekStart) return
        _state.update { it.copy(selectedDate = date, events = it.week?.eventsFor(date).orEmpty()) }
    }

    fun moveWeek(offset: Long) {
        val current = _state.value
        val weekdayOffset = current.weekStart.until(current.selectedDate).days.coerceIn(0, 6).toLong()
        val nextStart = current.weekStart.plusWeeks(offset)
        _state.update {
            it.copy(
                weekStart = nextStart,
                selectedDate = nextStart.plusDays(weekdayOffset),
                week = null,
                nextWeek = null,
                scheduleWeeks = emptyList(),
                events = emptyList(),
                errorMessage = null,
            )
        }
        loadCurrentWeek()
    }

    fun goToToday() = goToToday(showIndicator = false)

    private fun goToToday(showIndicator: Boolean) {
        val date = today()
        _state.update {
            it.copy(
                selectedDate = date,
                weekStart = date.with(DayOfWeek.MONDAY),
                week = null,
                nextWeek = null,
                scheduleWeeks = emptyList(),
                events = emptyList(),
                errorMessage = null,
            )
        }
        loadCurrentWeek(showIndicator = showIndicator)
    }

    fun refresh() = loadCurrentWeek(isManualRefresh = true)

    /**
     * Called every time the app comes to the foreground: refreshes the schedule with a
     * visible indicator and jumps to the new day if the app was left open overnight.
     */
    fun refreshOnEntry() {
        val current = _state.value
        if (current.isStarting) {
            // Preferences are still loading; the initial load picks this up.
            entryRefreshPending = true
            return
        }
        if (current.selectedGroup == null) return
        val date = today()
        _state.update { it.copy(now = now().truncatedTo(ChronoUnit.MINUTES)) }
        if (current.now.toLocalDate() != date && current.selectedDate == current.now.toLocalDate()) {
            goToToday(showIndicator = true)
        } else {
            loadCurrentWeek(showIndicator = true)
        }
    }

    fun retry() = loadCurrentWeek()

    fun saveStudyPlan(plan: StudyPlan) {
        if (plan.title.isBlank()) return
        viewModelScope.launch { studyPlanRepository.save(plan) }
    }

    fun setStudyPlanCompleted(id: String, completed: Boolean) {
        viewModelScope.launch { studyPlanRepository.setCompleted(id, completed) }
    }

    fun deleteStudyPlan(id: String) {
        viewModelScope.launch { studyPlanRepository.delete(id) }
    }

    /** Mutes lesson alerts for [pause], or resumes them when it is null. */
    fun pauseLessonAlerts(pause: LessonAlertPause?) {
        val until = pause?.until(now(), ZoneId.systemDefault())
        _state.update { it.copy(lessonAlertsPausedUntil = until) }
        viewModelScope.launch { preferences.setLessonAlertsPausedUntil(until) }
    }

    fun setDeadlineReminderDays(days: Set<Int>) {
        if (_state.value.deadlineReminderDays == days) return
        _state.update { it.copy(deadlineReminderDays = days) }
        viewModelScope.launch { preferences.setDeadlineReminderDays(days) }
    }

    fun setScheduleWeeksBack(value: Int) = setScheduleRange(value, _state.value.scheduleWeeksForward)

    fun setScheduleWeeksForward(value: Int) = setScheduleRange(_state.value.scheduleWeeksBack, value)

    private fun setScheduleRange(weeksBack: Int, weeksForward: Int) {
        if (weeksBack !in UserPreferences.SCHEDULE_WEEKS_RANGE ||
            weeksForward !in UserPreferences.SCHEDULE_WEEKS_RANGE
        ) return
        val current = _state.value
        if (current.scheduleWeeksBack == weeksBack && current.scheduleWeeksForward == weeksForward) return
        _state.update {
            it.copy(
                scheduleWeeksBack = weeksBack,
                scheduleWeeksForward = weeksForward,
                scheduleWeeks = emptyList(),
                week = null,
                nextWeek = null,
                events = emptyList(),
            )
        }
        viewModelScope.launch {
            if (current.scheduleWeeksBack != weeksBack) preferences.setScheduleWeeksBack(weeksBack)
            if (current.scheduleWeeksForward != weeksForward) preferences.setScheduleWeeksForward(weeksForward)
        }
        loadCurrentWeek()
    }

    fun setAppTheme(theme: AppTheme) {
        if (theme == AppTheme.Custom) return
        if (_state.value.appTheme == theme) return
        _state.update { it.copy(appTheme = theme, selectedCustomThemeId = null) }
        viewModelScope.launch { preferences.setAppTheme(theme) }
    }

    fun setDarkTheme(value: Boolean) {
        if (_state.value.darkTheme == value) return
        _state.update { it.copy(darkTheme = value) }
        viewModelScope.launch { preferences.setDarkTheme(value) }
    }

    fun saveBuiltInThemeOverride(theme: AppTheme, palette: ThemePalette) {
        if (theme == AppTheme.Custom) return
        val current = _state.value
        val nextOverride = BuiltInThemeOverride(theme, current.darkTheme, palette)
        val overrides = current.builtInThemeOverrides
            .filterNot { it.theme == theme && it.isDark == current.darkTheme } + nextOverride
        _state.update { it.copy(appTheme = theme, selectedCustomThemeId = null, builtInThemeOverrides = overrides) }
        viewModelScope.launch {
            preferences.setBuiltInThemeOverrides(overrides)
            preferences.setAppTheme(theme)
        }
    }

    fun resetBuiltInThemeOverride(theme: AppTheme) {
        if (theme == AppTheme.Custom) return
        val current = _state.value
        val overrides = current.builtInThemeOverrides
            .filterNot { it.theme == theme && it.isDark == current.darkTheme }
        if (overrides.size == current.builtInThemeOverrides.size) return
        _state.update { it.copy(builtInThemeOverrides = overrides) }
        viewModelScope.launch { preferences.setBuiltInThemeOverrides(overrides) }
    }

    fun selectCustomTheme(id: String) {
        if (_state.value.customThemes.none { it.id == id }) return
        _state.update { it.copy(appTheme = AppTheme.Custom, selectedCustomThemeId = id) }
        viewModelScope.launch { preferences.setAppTheme(AppTheme.Custom, id) }
    }

    fun saveCustomTheme(theme: CustomAppTheme) {
        val normalized = theme.copy(name = theme.name.trim())
        if (normalized.name.isEmpty()) return
        val themes = _state.value.customThemes.toMutableList()
        val index = themes.indexOfFirst { it.id == normalized.id }
        if (index >= 0) themes[index] = normalized else themes += normalized
        _state.update {
            it.copy(
                customThemes = themes,
                appTheme = AppTheme.Custom,
                selectedCustomThemeId = normalized.id,
            )
        }
        viewModelScope.launch {
            preferences.setCustomThemes(themes)
            preferences.setAppTheme(AppTheme.Custom, normalized.id)
        }
    }

    fun deleteCustomTheme(id: String) {
        val themes = _state.value.customThemes.filterNot { it.id == id }
        if (themes.size == _state.value.customThemes.size) return
        val wasSelected = _state.value.appTheme == AppTheme.Custom && _state.value.selectedCustomThemeId == id
        _state.update {
            it.copy(
                customThemes = themes,
                appTheme = if (wasSelected) AppTheme.default else it.appTheme,
                selectedCustomThemeId = if (wasSelected) null else it.selectedCustomThemeId,
            )
        }
        viewModelScope.launch {
            preferences.setCustomThemes(themes)
            if (wasSelected) preferences.setAppTheme(AppTheme.default)
        }
    }

    fun setAppDisplayOptions(options: AppDisplayOptions) {
        if (_state.value.appDisplayOptions == options) return
        _state.update { it.copy(appDisplayOptions = options) }
        viewModelScope.launch { preferences.setAppDisplayOptions(options) }
    }

    fun resetAppDisplayOptions() {
        val current = _state.value.appDisplayOptions
        setAppDisplayOptions(
            AppDisplayOptions(
                backgroundStyle = current.backgroundStyle,
                backgroundIntensityPercent = current.backgroundIntensityPercent,
                backgroundSpeedPercent = current.backgroundSpeedPercent,
                backgroundGradientStartColor = current.backgroundGradientStartColor,
                backgroundGradientEndColor = current.backgroundGradientEndColor,
                backgroundGradientAccentColor = current.backgroundGradientAccentColor,
                backgroundGradientAngleDegrees = current.backgroundGradientAngleDegrees,
            ),
        )
    }

    fun setLectureStripeColor(color: Int) {
        if (!WidgetStripePalette.isSupported(color)) return
        _state.update { it.copy(lectureStripeColor = color) }
        viewModelScope.launch { preferences.setLectureStripeColor(color) }
    }

    fun setPracticeStripeColor(color: Int) {
        if (!WidgetStripePalette.isSupported(color)) return
        _state.update { it.copy(practiceStripeColor = color) }
        viewModelScope.launch { preferences.setPracticeStripeColor(color) }
    }

    fun setWidgetShowClassroom(value: Boolean) {
        _state.update { it.copy(widgetShowClassroom = value) }
        viewModelScope.launch { preferences.setShowClassroom(value) }
    }

    fun setWidgetShowTime(value: Boolean) {
        _state.update { it.copy(widgetShowTime = value) }
        viewModelScope.launch { preferences.setShowTime(value) }
    }

    fun setWidgetShowStripe(value: Boolean) {
        _state.update { it.copy(widgetShowStripe = value) }
        viewModelScope.launch { preferences.setShowStripe(value) }
    }

    fun setWidgetShowEndTime(value: Boolean) {
        _state.update { it.copy(widgetShowEndTime = value) }
        viewModelScope.launch { preferences.setShowEndTime(value) }
    }

    fun setWidgetWrapTitle(value: Boolean) {
        _state.update { it.copy(widgetWrapTitle = value) }
        viewModelScope.launch { preferences.setWrapTitle(value) }
    }

    fun setWidgetOuterPadding(value: Int) = updateWidgetDimension(
        current = { it.widgetOuterPaddingDp },
        update = { copy(widgetOuterPaddingDp = value) },
        persist = { preferences.setOuterPadding(it) },
        value = value,
        range = 0..24,
    )

    fun setWidgetHorizontalPadding(value: Int) = updateWidgetDimension(
        current = { it.widgetHorizontalPaddingDp },
        update = { copy(widgetHorizontalPaddingDp = value) },
        persist = { preferences.setHorizontalPadding(it) },
        value = value,
        range = 0..24,
    )

    fun setWidgetVerticalPadding(value: Int) = updateWidgetDimension(
        current = { it.widgetVerticalPaddingDp },
        update = { copy(widgetVerticalPaddingDp = value) },
        persist = { preferences.setVerticalPadding(it) },
        value = value,
        range = 0..16,
    )

    fun setWidgetItemSpacing(value: Int) = updateWidgetDimension(
        current = { it.widgetItemSpacingDp },
        update = { copy(widgetItemSpacingDp = value) },
        persist = { preferences.setItemSpacing(it) },
        value = value,
        range = 0..12,
    )

    fun setWidgetTitleTextSize(value: Int) = updateWidgetDimension(
        current = { it.widgetTitleTextSizeSp },
        update = { copy(widgetTitleTextSizeSp = value) },
        persist = { preferences.setTitleTextSize(it) },
        value = value,
        range = 9..18,
    )

    fun setWidgetSecondaryTextSize(value: Int) = updateWidgetDimension(
        current = { it.widgetSecondaryTextSizeSp },
        update = { copy(widgetSecondaryTextSizeSp = value) },
        persist = { preferences.setSecondaryTextSize(it) },
        value = value,
        range = 7..14,
    )

    fun setWidgetFontStyle(id: String) {
        if (!WidgetFontStyles.isSupported(id) || _state.value.widgetFontStyleId == id) return
        _state.update { it.copy(widgetFontStyleId = id) }
        viewModelScope.launch { preferences.setFontStyle(id) }
    }

    fun setWidgetLetterSpacing(value: Int) = updateWidgetDimension(
        current = { it.widgetLetterSpacingPercent },
        update = { copy(widgetLetterSpacingPercent = value) },
        persist = { preferences.setLetterSpacing(it) },
        value = value,
        range = -5..10,
    )

    fun setWidgetTitleMaxLines(value: Int) = updateWidgetDimension(
        current = { it.widgetTitleMaxLines },
        update = { copy(widgetTitleMaxLines = value) },
        persist = { preferences.setTitleMaxLines(it) },
        value = value,
        range = 1..4,
    )

    fun setLessonAlias(original: String, custom: String) {
        val key = original.trim()
        if (key.isEmpty()) return
        val trimmed = custom.trim()
        val current = _state.value.lessonAliases
        val next = if (trimmed.isEmpty() || trimmed == key) current - key else current + (key to trimmed)
        if (next == current) return
        _state.update { it.copy(lessonAliases = next) }
        viewModelScope.launch { preferences.setLessonAliases(next) }
    }

    private fun updateWidgetDimension(
        current: (ScheduleUiState) -> Int,
        update: ScheduleUiState.() -> ScheduleUiState,
        persist: suspend (Int) -> Unit,
        value: Int,
        range: IntRange,
    ) {
        if (value !in range || current(_state.value) == value) return
        _state.update { it.update() }
        viewModelScope.launch { persist(value) }
    }

    private fun loadCurrentWeek(isManualRefresh: Boolean = false, showIndicator: Boolean = isManualRefresh) {
        val group = _state.value.selectedGroup ?: return
        val weekStart = _state.value.weekStart
        val weeksBack = _state.value.scheduleWeeksBack
        val weeksForward = _state.value.scheduleWeeksForward
        val starts = scheduleWeekStarts(weekStart, weeksBack, weeksForward)
        fun ScheduleUiState.matchesRequest(): Boolean =
            selectedGroup == group &&
                this.weekStart == weekStart &&
                scheduleWeeksBack == weeksBack &&
                scheduleWeeksForward == weeksForward
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val cached = starts.mapNotNull { start ->
                runCatching { repository.cachedWeek(group, start) }.getOrNull()
            }
            if (cached.isNotEmpty()) {
                val cachedWeeks = cached.map { it.week }.sortedBy { it.weekStart }
                _state.update {
                    if (!it.matchesRequest()) it else it.copy(
                        scheduleWeeks = cachedWeeks,
                        week = cachedWeeks.firstOrNull { item -> item.weekStart == weekStart } ?: it.week,
                        nextWeek = cachedWeeks.firstOrNull { item -> item.weekStart == weekStart.plusWeeks(1) } ?: it.nextWeek,
                        events = cachedWeeks.firstOrNull { item -> item.weekStart == weekStart }
                            ?.eventsFor(it.selectedDate).orEmpty(),
                        lastUpdatedMillis = cached.maxOfOrNull { item -> item.updatedAtMillis } ?: it.lastUpdatedMillis,
                        showingCachedData = true,
                    )
                }
            }
            _state.update {
                if (!it.matchesRequest()) it else it.copy(
                    isLoading = cached.isEmpty() && !isManualRefresh,
                    isRefreshing = isManualRefresh || cached.isNotEmpty(),
                    isManualRefreshing = showIndicator && (isManualRefresh || cached.isNotEmpty()),
                    errorMessage = null,
                )
            }
            var firstFailure: Throwable? = null
            starts.forEach { start ->
                try {
                    val fresh = repository.refreshWeek(group, start)
                    _state.update {
                        if (!it.matchesRequest()) it else {
                            val updatedWeeks = (it.scheduleWeeks.filterNot { item -> item.weekStart == start } + fresh.week)
                                .sortedBy { item -> item.weekStart }
                            it.copy(
                                scheduleWeeks = updatedWeeks,
                                week = updatedWeeks.firstOrNull { item -> item.weekStart == weekStart },
                                nextWeek = updatedWeeks.firstOrNull { item -> item.weekStart == weekStart.plusWeeks(1) },
                                events = updatedWeeks.firstOrNull { item -> item.weekStart == weekStart }
                                    ?.eventsFor(it.selectedDate).orEmpty(),
                                isLoading = false,
                                isRefreshing = true,
                                errorMessage = null,
                                showingCachedData = false,
                                lastUpdatedMillis = fresh.updatedAtMillis,
                            )
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    if (firstFailure == null) firstFailure = failure
                }
            }
            _state.update {
                if (!it.matchesRequest()) it else it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isManualRefreshing = false,
                    errorMessage = firstFailure?.let { failure ->
                        failure.message ?: "Не удалось обновить часть расписания"
                    },
                    showingCachedData = firstFailure != null && it.scheduleWeeks.isNotEmpty(),
                )
            }
        }
    }

    class Factory(
        private val repository: ScheduleRepository,
        private val preferences: UserPreferences,
        private val studyPlanRepository: StudyPlanRepository,
        private val updateChecker: GitHubUpdateChecker,
        private val updatesDirectory: File,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ScheduleViewModel(repository, preferences, studyPlanRepository, updateChecker, updatesDirectory) as T
    }
}

internal fun scheduleWeekStarts(
    currentWeekStart: LocalDate,
    weeksBack: Int,
    weeksForward: Int,
): List<LocalDate> {
    require(weeksBack in UserPreferences.SCHEDULE_WEEKS_RANGE)
    require(weeksForward in UserPreferences.SCHEDULE_WEEKS_RANGE)
    return (-weeksBack..weeksForward).map { offset -> currentWeekStart.plusWeeks(offset.toLong()) }
}
