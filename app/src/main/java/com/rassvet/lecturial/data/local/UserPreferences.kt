package com.rassvet.lecturial.data.local

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rassvet.lecturial.model.WidgetStripeColors
import com.rassvet.lecturial.model.WidgetStripePalette
import com.rassvet.lecturial.model.WidgetDisplayOptions
import com.rassvet.lecturial.model.WidgetFontStyles
import com.rassvet.lecturial.model.AppTheme
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
    private val lessonAliasesKey = stringPreferencesKey("lesson_aliases")
    private val appThemeKey = stringPreferencesKey("app_theme")

    private val safePreferences = context.dataStore.data
        .catch { failure ->
            if (failure is IOException) emit(emptyPreferences()) else throw failure
        }

    val selectedGroup: Flow<String?> = safePreferences
        .map { preferences -> preferences[selectedGroupKey]?.trim()?.takeIf { it.isNotEmpty() } }

    val appTheme: Flow<AppTheme> = safePreferences
        .map { preferences -> AppTheme.fromStorageId(preferences[appThemeKey]) }

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

    suspend fun setAppTheme(theme: AppTheme) {
        context.dataStore.edit { it[appThemeKey] = theme.storageId }
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

    suspend fun setLessonAliases(aliases: Map<String, String>) {
        context.dataStore.edit { preferences ->
            val encoded = encodeLessonAliases(aliases)
            if (encoded == null) preferences.remove(lessonAliasesKey) else preferences[lessonAliasesKey] = encoded
        }
        TodayScheduleWidget.updateAll(context)
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
