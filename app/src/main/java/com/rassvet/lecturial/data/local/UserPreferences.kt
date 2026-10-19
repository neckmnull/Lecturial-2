package com.rassvet.lecturial.data.local

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rassvet.lecturial.model.WidgetStripeColors
import com.rassvet.lecturial.model.WidgetStripePalette
import com.rassvet.lecturial.model.WidgetDisplayOptions
import com.rassvet.lecturial.model.WidgetFontStyles
import com.rassvet.lecturial.model.AppTheme
import com.rassvet.lecturial.model.AppDisplayOptions
import com.rassvet.lecturial.model.AppBackgroundStyle
import com.rassvet.lecturial.model.CustomAppTheme
import com.rassvet.lecturial.model.BuiltInThemeOverride
import com.rassvet.lecturial.model.ThemePalette
import com.rassvet.lecturial.model.DeadlineReminder
import com.rassvet.lecturial.notify.DeadlineAlerts
import com.rassvet.lecturial.widget.TodayScheduleWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(
    name = "lecturial_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class UserPreferences(private val context: Context) {
    private val selectedGroupKey = stringPreferencesKey("selected_group")
    private val selectedTargetNameKey = stringPreferencesKey("selected_target_name")
    private val lectureStripeColorKey = intPreferencesKey("widget_lecture_stripe_color")
    private val practiceStripeColorKey = intPreferencesKey("widget_practice_stripe_color")
    private val showClassroomKey = booleanPreferencesKey("widget_show_classroom")
    private val showTimeKey = booleanPreferencesKey("widget_show_time")
    private val showStripeKey = booleanPreferencesKey("widget_show_stripe")
    private val showEndTimeKey = booleanPreferencesKey("widget_show_end_time")
    private val wrapTitleKey = booleanPreferencesKey("widget_wrap_title")
    private val outerPaddingKey = intPreferencesKey("widget_outer_padding_dp")
    private val horizontalPaddingKey = intPreferencesKey("widget_horizontal_padding_dp")
    private val verticalPaddingKey = intPreferencesKey("widget_vertical_padding_dp")
    private val itemSpacingKey = intPreferencesKey("widget_item_spacing_dp")
    private val titleTextSizeKey = intPreferencesKey("widget_title_text_size_sp")
    private val secondaryTextSizeKey = intPreferencesKey("widget_secondary_text_size_sp")
    private val fontStyleKey = stringPreferencesKey("widget_font_style")
    private val letterSpacingKey = intPreferencesKey("widget_letter_spacing_percent")
    private val titleMaxLinesKey = intPreferencesKey("widget_title_max_lines")
    private val widgetMatchAppKey = booleanPreferencesKey("widget_match_app")
    private val widgetBackgroundOpacityKey = intPreferencesKey("widget_background_opacity_percent")
    private val lessonAliasesKey = stringPreferencesKey("lesson_aliases")
    private val appThemeKey = stringPreferencesKey("app_theme")
    private val selectedCustomThemeIdKey = stringPreferencesKey("selected_custom_theme_id")
    private val customThemesKey = stringPreferencesKey("custom_app_themes")
    private val appDisplayOptionsKey = stringPreferencesKey("app_display_options")
    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val builtInThemeOverridesKey = stringPreferencesKey("built_in_theme_overrides")
    private val deadlineReminderDaysKey = stringPreferencesKey("deadline_reminder_days")
    private val lessonAlertsPausedUntilKey = longPreferencesKey("lesson_alerts_paused_until")
    private val scheduleWeeksBackKey = intPreferencesKey("schedule_weeks_back")
    private val scheduleWeeksForwardKey = intPreferencesKey("schedule_weeks_forward")

    private val safePreferences = context.dataStore.data
        .catch { failure ->
            if (failure is IOException) emit(emptyPreferences()) else throw failure
        }

    val selectedGroup: Flow<String?> = safePreferences
        .map { preferences -> preferences[selectedGroupKey]?.trim()?.takeIf { it.isNotEmpty() } }

    /** Display name for a teacher schedule key; groups are shown by their key. */
    val selectedTargetName: Flow<String?> = safePreferences
        .map { preferences -> preferences[selectedTargetNameKey]?.trim()?.takeIf { it.isNotEmpty() } }

    val appTheme: Flow<AppTheme> = safePreferences
        .map { preferences -> AppTheme.fromStorageId(preferences[appThemeKey]) }

    val darkTheme: Flow<Boolean> = safePreferences
        .map { preferences -> preferences[darkThemeKey] ?: true }

    val selectedCustomThemeId: Flow<String?> = safePreferences.map { preferences ->
        preferences[selectedCustomThemeIdKey]?.takeIf { it.isNotBlank() }
    }

    val customThemes: Flow<List<CustomAppTheme>> = safePreferences.map { preferences ->
        parseCustomThemes(preferences[customThemesKey])
    }

    val builtInThemeOverrides: Flow<List<BuiltInThemeOverride>> = safePreferences.map { preferences ->
        parseBuiltInThemeOverrides(preferences[builtInThemeOverridesKey])
    }

    val appDisplayOptions: Flow<AppDisplayOptions> = safePreferences.map { preferences ->
        parseAppDisplayOptions(preferences[appDisplayOptionsKey])
    }

    /** Epoch millis until which lesson alerts are muted; may lie in the past once it expires. */
    val lessonAlertsPausedUntil: Flow<Long?> = safePreferences.map { preferences ->
        preferences[lessonAlertsPausedUntilKey]
    }

    val deadlineReminderDays: Flow<Set<Int>> = safePreferences.map { preferences ->
        preferences[deadlineReminderDaysKey]
            ?.split(',')
            ?.mapNotNull(String::toIntOrNull)
            ?.filterTo(linkedSetOf()) { it in DeadlineReminder.supportedDays }
            ?: DeadlineReminder.default
    }

    val scheduleWeeksBack: Flow<Int> = safePreferences.map { preferences ->
        (preferences[scheduleWeeksBackKey] ?: DEFAULT_SCHEDULE_WEEKS_BACK).coerceIn(SCHEDULE_WEEKS_RANGE)
    }

    val scheduleWeeksForward: Flow<Int> = safePreferences.map { preferences ->
        (preferences[scheduleWeeksForwardKey] ?: DEFAULT_SCHEDULE_WEEKS_FORWARD).coerceIn(SCHEDULE_WEEKS_RANGE)
    }

    fun languageTeacher(group: String): Flow<String?> = safePreferences.map {
        it[stringPreferencesKey("language_teacher_$group")]
    }

    suspend fun setLanguageTeacher(group: String, teacher: String?) {
        context.dataStore.edit { it[stringPreferencesKey("language_teacher_$group")] = teacher.orEmpty() }
        TodayScheduleWidget.updateAll(context)
        com.rassvet.lecturial.notify.LessonAlerts.sync(context)
    }

    val widgetStripeColors: Flow<WidgetStripeColors> = safePreferences.map { preferences ->
        WidgetStripeColors(
            lecture = preferences[lectureStripeColorKey]
                ?.takeIf(WidgetStripePalette::isSupported)
                ?: WidgetStripePalette.defaultLecture,
            practice = preferences[practiceStripeColorKey]
                ?.takeIf(WidgetStripePalette::isSupported)
                ?: WidgetStripePalette.defaultPractice,
        )
    }

    val widgetDisplayOptions: Flow<WidgetDisplayOptions> = safePreferences.map { preferences ->
        WidgetDisplayOptions(
            showTime = preferences[showTimeKey] ?: true,
            showStripe = preferences[showStripeKey] ?: true,
            showClassroom = preferences[showClassroomKey] ?: true,
            showEndTime = preferences[showEndTimeKey] ?: true,
            wrapTitle = preferences[wrapTitleKey] ?: true,
            outerPaddingDp = (preferences[outerPaddingKey] ?: 9).coerceIn(0, 24),
            horizontalPaddingDp = (preferences[horizontalPaddingKey] ?: 12).coerceIn(0, 24),
            verticalPaddingDp = (preferences[verticalPaddingKey] ?: 6).coerceIn(0, 16),
            itemSpacingDp = (preferences[itemSpacingKey] ?: 3).coerceIn(0, 12),
            titleTextSizeSp = (preferences[titleTextSizeKey] ?: 12).coerceIn(9, 18),
            secondaryTextSizeSp = (preferences[secondaryTextSizeKey] ?: 9).coerceIn(7, 14),
            fontStyleId = preferences[fontStyleKey]
                ?.takeIf(WidgetFontStyles::isSupported)
                ?: WidgetFontStyles.defaultId,
            letterSpacingPercent = (preferences[letterSpacingKey] ?: 0).coerceIn(-5, 10),
            titleMaxLines = (preferences[titleMaxLinesKey] ?: 2).coerceIn(1, 4),
            matchApp = preferences[widgetMatchAppKey] ?: true,
            backgroundOpacityPercent = (preferences[widgetBackgroundOpacityKey] ?: 100).coerceIn(0, 100),
        )
    }

    val lessonAliases: Flow<Map<String, String>> = safePreferences.map { preferences ->
        parseLessonAliases(preferences[lessonAliasesKey])
    }

    suspend fun setSelectedGroup(group: String) {
        val normalized = group.trim()
        require(normalized.isNotEmpty())
        context.dataStore.edit { it[selectedGroupKey] = normalized }
    }

    suspend fun setSelectedTargetName(name: String?) {
        context.dataStore.edit { preferences ->
            val normalized = name?.trim()?.takeIf { it.isNotEmpty() }
            if (normalized == null) preferences.remove(selectedTargetNameKey)
            else preferences[selectedTargetNameKey] = normalized
        }
    }

    suspend fun setAppTheme(theme: AppTheme, customThemeId: String? = null) {
        context.dataStore.edit { preferences ->
            preferences[appThemeKey] = theme.storageId
            if (theme == AppTheme.Custom && !customThemeId.isNullOrBlank()) {
                preferences[selectedCustomThemeIdKey] = customThemeId
            } else if (theme != AppTheme.Custom) {
                preferences.remove(selectedCustomThemeIdKey)
            }
        }
        TodayScheduleWidget.updateAll(context)
    }

    suspend fun setDarkTheme(value: Boolean) {
        context.dataStore.edit { it[darkThemeKey] = value }
        TodayScheduleWidget.updateAll(context)
    }

    suspend fun setCustomThemes(themes: List<CustomAppTheme>) {
        context.dataStore.edit { preferences ->
            if (themes.isEmpty()) preferences.remove(customThemesKey)
            else preferences[customThemesKey] = encodeCustomThemes(themes)
        }
        TodayScheduleWidget.updateAll(context)
    }

    suspend fun setBuiltInThemeOverrides(overrides: List<BuiltInThemeOverride>) {
        context.dataStore.edit { preferences ->
            if (overrides.isEmpty()) preferences.remove(builtInThemeOverridesKey)
            else preferences[builtInThemeOverridesKey] = encodeBuiltInThemeOverrides(overrides)
        }
        TodayScheduleWidget.updateAll(context)
    }

    suspend fun setAppDisplayOptions(options: AppDisplayOptions) {
        context.dataStore.edit { it[appDisplayOptionsKey] = encodeAppDisplayOptions(options) }
        TodayScheduleWidget.updateAll(context)
    }

    suspend fun setLectureStripeColor(color: Int) {
        require(WidgetStripePalette.isSupported(color))
        context.dataStore.edit { it[lectureStripeColorKey] = color }
        TodayScheduleWidget.updateAll(context)
    }

    suspend fun setPracticeStripeColor(color: Int) {
        require(WidgetStripePalette.isSupported(color))
        context.dataStore.edit { it[practiceStripeColorKey] = color }
        TodayScheduleWidget.updateAll(context)
    }

    suspend fun setShowClassroom(value: Boolean) {
        context.dataStore.edit { it[showClassroomKey] = value }
        TodayScheduleWidget.updateAll(context)
    }

    suspend fun setShowTime(value: Boolean) = setWidgetBoolean(showTimeKey, value)

    suspend fun setShowStripe(value: Boolean) = setWidgetBoolean(showStripeKey, value)

    suspend fun setShowEndTime(value: Boolean) {
        context.dataStore.edit { it[showEndTimeKey] = value }
        TodayScheduleWidget.updateAll(context)
    }

    suspend fun setWrapTitle(value: Boolean) {
        context.dataStore.edit { it[wrapTitleKey] = value }
        TodayScheduleWidget.updateAll(context)
    }

    suspend fun setOuterPadding(value: Int) = setWidgetDimension(outerPaddingKey, value, 0..24)

    suspend fun setHorizontalPadding(value: Int) =
        setWidgetDimension(horizontalPaddingKey, value, 0..24)

    suspend fun setVerticalPadding(value: Int) =
        setWidgetDimension(verticalPaddingKey, value, 0..16)

    suspend fun setItemSpacing(value: Int) = setWidgetDimension(itemSpacingKey, value, 0..12)

    suspend fun setTitleTextSize(value: Int) = setWidgetDimension(titleTextSizeKey, value, 9..18)

    suspend fun setSecondaryTextSize(value: Int) =
        setWidgetDimension(secondaryTextSizeKey, value, 7..14)

    suspend fun setFontStyle(id: String) {
        require(WidgetFontStyles.isSupported(id))
        context.dataStore.edit { it[fontStyleKey] = id }
        TodayScheduleWidget.updateAll(context)
    }

    suspend fun setLetterSpacing(value: Int) = setWidgetDimension(letterSpacingKey, value, -5..10)

    suspend fun setTitleMaxLines(value: Int) = setWidgetDimension(titleMaxLinesKey, value, 1..4)

    suspend fun setWidgetMatchApp(value: Boolean) = setWidgetBoolean(widgetMatchAppKey, value)

    suspend fun setWidgetBackgroundOpacity(value: Int) = setWidgetDimension(widgetBackgroundOpacityKey, value, 0..100)

    suspend fun setLessonAliases(aliases: Map<String, String>) {
        context.dataStore.edit { preferences ->
            val encoded = encodeLessonAliases(aliases)
            if (encoded == null) preferences.remove(lessonAliasesKey) else preferences[lessonAliasesKey] = encoded
        }
        TodayScheduleWidget.updateAll(context)
    }

    suspend fun setLessonAlertsPausedUntil(untilMillis: Long?) {
        context.dataStore.edit { preferences ->
            if (untilMillis == null) preferences.remove(lessonAlertsPausedUntilKey)
            else preferences[lessonAlertsPausedUntilKey] = untilMillis
        }
        com.rassvet.lecturial.notify.LessonAlerts.sync(context)
    }

    suspend fun setDeadlineReminderDays(days: Set<Int>) {
        require(days.all { it in DeadlineReminder.supportedDays })
        context.dataStore.edit { preferences ->
            preferences[deadlineReminderDaysKey] = days.sortedDescending().joinToString(",")
        }
        DeadlineAlerts.sync(context)
    }

    suspend fun setScheduleWeeksBack(value: Int) {
        require(value in SCHEDULE_WEEKS_RANGE)
        context.dataStore.edit { it[scheduleWeeksBackKey] = value }
    }

    suspend fun setScheduleWeeksForward(value: Int) {
        require(value in SCHEDULE_WEEKS_RANGE)
        context.dataStore.edit { it[scheduleWeeksForwardKey] = value }
    }

    private suspend fun setWidgetBoolean(
        key: androidx.datastore.preferences.core.Preferences.Key<Boolean>,
        value: Boolean,
    ) {
        context.dataStore.edit { it[key] = value }
        TodayScheduleWidget.updateAll(context)
    }

    private suspend fun setWidgetDimension(
        key: androidx.datastore.preferences.core.Preferences.Key<Int>,
        value: Int,
        range: IntRange,
    ) {
        require(value in range)
        context.dataStore.edit { it[key] = value }
        TodayScheduleWidget.updateAll(context)
    }

    companion object {
        const val DEFAULT_SCHEDULE_WEEKS_BACK = 1
        const val DEFAULT_SCHEDULE_WEEKS_FORWARD = 1
        val SCHEDULE_WEEKS_RANGE = 0..2
    }
}

private fun encodeLessonAliases(aliases: Map<String, String>): String? {
    val cleaned = aliases.mapKeys { it.key.trim() }.mapValues { it.value.trim() }
        .filter { it.key.isNotEmpty() && it.value.isNotEmpty() && it.key != it.value }
    if (cleaned.isEmpty()) return null
    val obj = org.json.JSONObject()
    cleaned.forEach { (original, custom) -> obj.put(original, custom) }
    return obj.toString()
}

private fun parseLessonAliases(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching {
        val obj = org.json.JSONObject(raw)
        obj.keys().asSequence().associateWith { key -> obj.optString(key) }
            .filterValues { it.isNotBlank() }
    }.getOrDefault(emptyMap())
}

internal fun encodeCustomThemes(themes: List<CustomAppTheme>): String {
    val array = org.json.JSONArray()
    themes.forEach { theme ->
        array.put(
            org.json.JSONObject()
                .put("id", theme.id)
                .put("name", theme.name)
                .put("primary", theme.primary)
                .put("secondary", theme.secondary)
                .put("tertiary", theme.tertiary)
                .put("background", theme.background)
                .put("surface", theme.surface)
                .put("onSurface", theme.onSurface),
        )
    }
    return array.toString()
}

internal fun parseCustomThemes(raw: String?): List<CustomAppTheme> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = org.json.JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val name = item.optString("name").trim()
                if (id.isEmpty() || name.isEmpty()) continue
                val theme = runCatching {
                    CustomAppTheme(
                        id = id,
                        name = name,
                        primary = item.getInt("primary"),
                        secondary = item.getInt("secondary"),
                        tertiary = item.getInt("tertiary"),
                        background = item.getInt("background"),
                        surface = item.getInt("surface"),
                        onSurface = item.getInt("onSurface"),
                    )
                }.getOrNull() ?: continue
                add(theme)
            }
        }
    }.getOrDefault(emptyList())
}

internal fun encodeBuiltInThemeOverrides(overrides: List<BuiltInThemeOverride>): String {
    val array = org.json.JSONArray()
    overrides.forEach { override ->
        array.put(
            org.json.JSONObject()
                .put("theme", override.theme.storageId)
                .put("isDark", override.isDark)
                .put("primary", override.palette.primary)
                .put("secondary", override.palette.secondary)
                .put("tertiary", override.palette.tertiary)
                .put("background", override.palette.background)
                .put("surface", override.palette.surface)
                .put("onSurface", override.palette.onSurface),
        )
    }
    return array.toString()
}

internal fun parseBuiltInThemeOverrides(raw: String?): List<BuiltInThemeOverride> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = org.json.JSONArray(raw)
        buildList {
            repeat(array.length()) { index ->
                val item = array.optJSONObject(index) ?: return@repeat
                val themeId = item.optString("theme")
                val theme = AppTheme.entries.firstOrNull { it.storageId == themeId } ?: return@repeat
                if (theme == AppTheme.Custom) return@repeat
                val override = runCatching {
                    BuiltInThemeOverride(
                        theme = theme,
                        isDark = item.optBoolean("isDark", true),
                        palette = ThemePalette(
                            primary = item.getInt("primary"),
                            secondary = item.getInt("secondary"),
                            tertiary = item.getInt("tertiary"),
                            background = item.getInt("background"),
                            surface = item.getInt("surface"),
                            onSurface = item.getInt("onSurface"),
                        ),
                    )
                }.getOrNull() ?: return@repeat
                add(override)
            }
        }
    }.getOrDefault(emptyList())
}

internal fun encodeAppDisplayOptions(options: AppDisplayOptions): String = org.json.JSONObject()
    .put("lectureStripeColor", options.lectureStripeColor)
    .put("practiceStripeColor", options.practiceStripeColor)
    .put("showTime", options.showTime)
    .put("showStripe", options.showStripe)
    .put("showClassroom", options.showClassroom)
    .put("showEndTime", options.showEndTime)
    .put("wrapTitle", options.wrapTitle)
    .put("outerPaddingDp", options.outerPaddingDp)
    .put("horizontalPaddingDp", options.horizontalPaddingDp)
    .put("verticalPaddingDp", options.verticalPaddingDp)
    .put("itemSpacingDp", options.itemSpacingDp)
    .put("titleTextSizeSp", options.titleTextSizeSp)
    .put("secondaryTextSizeSp", options.secondaryTextSizeSp)
    .put("fontStyleId", options.fontStyleId)
    .put("letterSpacingPercent", options.letterSpacingPercent)
    .put("titleMaxLines", options.titleMaxLines)
    .put("tileCornerRadiusDp", options.tileCornerRadiusDp)
    .put("tileOpacityPercent", options.tileOpacityPercent)
    .put("backgroundStyle", options.backgroundStyle.storageId)
    .put("backgroundIntensityPercent", options.backgroundIntensityPercent)
    .put("backgroundSpeedPercent", options.backgroundSpeedPercent)
    .put("backgroundGradientStartColor", options.backgroundGradientStartColor)
    .put("backgroundGradientEndColor", options.backgroundGradientEndColor)
    .put("backgroundGradientAccentColor", options.backgroundGradientAccentColor)
    .put("backgroundGradientAngleDegrees", options.backgroundGradientAngleDegrees)
    .put("backgroundLineDensityPercent", options.backgroundLineDensityPercent)
    .put("backgroundLineWeight", options.backgroundLineWeight)
    .toString()

internal fun parseAppDisplayOptions(raw: String?): AppDisplayOptions {
    if (raw.isNullOrBlank()) return AppDisplayOptions()
    return runCatching {
        val item = org.json.JSONObject(raw)
        AppDisplayOptions(
            lectureStripeColor = item.optInt("lectureStripeColor", WidgetStripePalette.defaultLecture)
                .takeIf(WidgetStripePalette::isSupported)
                ?: WidgetStripePalette.defaultLecture,
            practiceStripeColor = item.optInt("practiceStripeColor", WidgetStripePalette.defaultPractice)
                .takeIf(WidgetStripePalette::isSupported)
                ?: WidgetStripePalette.defaultPractice,
            showTime = item.optBoolean("showTime", true),
            showStripe = item.optBoolean("showStripe", true),
            showClassroom = item.optBoolean("showClassroom", true),
            showEndTime = item.optBoolean("showEndTime", true),
            wrapTitle = item.optBoolean("wrapTitle", true),
            outerPaddingDp = item.optInt("outerPaddingDp", 22).coerceIn(0, 32),
            horizontalPaddingDp = item.optInt("horizontalPaddingDp", 12).coerceIn(0, 24),
            verticalPaddingDp = item.optInt("verticalPaddingDp", 0).coerceIn(0, 16),
            itemSpacingDp = item.optInt("itemSpacingDp", 20).coerceIn(0, 24),
            titleTextSizeSp = item.optInt("titleTextSizeSp", 16).coerceIn(12, 24),
            secondaryTextSizeSp = item.optInt("secondaryTextSizeSp", 14).coerceIn(10, 20),
            fontStyleId = item.optString("fontStyleId")
                .takeIf(WidgetFontStyles::isSupported)
                ?: WidgetFontStyles.defaultId,
            letterSpacingPercent = item.optInt("letterSpacingPercent", 0).coerceIn(-5, 10),
            titleMaxLines = item.optInt("titleMaxLines", 3).coerceIn(1, 5),
            tileCornerRadiusDp = item.optInt("tileCornerRadiusDp", 24).coerceIn(0, 32),
            tileOpacityPercent = item.optInt("tileOpacityPercent", 78).coerceIn(0, 100),
            backgroundStyle = AppBackgroundStyle.fromStorageId(item.optString("backgroundStyle")),
            backgroundIntensityPercent = item.optInt("backgroundIntensityPercent", 100).coerceIn(25, 150),
            backgroundSpeedPercent = item.optInt("backgroundSpeedPercent", 50).coerceIn(0, 100),
            backgroundGradientStartColor = item.optNullableInt("backgroundGradientStartColor"),
            backgroundGradientEndColor = item.optNullableInt("backgroundGradientEndColor"),
            backgroundGradientAccentColor = item.optNullableInt("backgroundGradientAccentColor"),
            backgroundGradientAngleDegrees = item.optInt("backgroundGradientAngleDegrees", 135).coerceIn(0, 360),
            backgroundLineDensityPercent = item.optInt("backgroundLineDensityPercent", 100).coerceIn(50, 150),
            backgroundLineWeight = item.optInt("backgroundLineWeight", 2).coerceIn(1, 5),
        )
    }.getOrDefault(AppDisplayOptions())
}

private fun org.json.JSONObject.optNullableInt(key: String): Int? =
    (opt(key) as? Number)?.toInt()
