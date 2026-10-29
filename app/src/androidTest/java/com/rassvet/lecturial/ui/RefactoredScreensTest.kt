package com.rassvet.lecturial.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.rassvet.lecturial.model.ScheduleDay
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.ScheduleWeek
import com.rassvet.lecturial.ui.theme.LecturialTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Screens that moved into their own files, and what was added to them since. */
class RefactoredScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComposeTestActivity>()

    private val date = LocalDate.of(2026, 8, 31)
    private val longName = "Математический анализ и дифференциальные уравнения"
    private val event = ScheduleEvent(
        id = "lesson",
        date = date,
        startTime = "08:45",
        endTime = "10:05",
        pairNumber = 1,
        discipline = longName,
        type = "Лекция",
        classroom = "5.7",
        address = null,
        teachers = emptyList(),
        comment = null,
        onlineUrl = null,
    )
    private val state = ScheduleUiState(
        selectedGroup = "ИОП-ИТ-26/1",
        selectedDate = date,
        weekStart = date,
        week = ScheduleWeek("ИОП-ИТ-26/1", date, listOf(ScheduleDay(date, listOf(event)))),
        events = listOf(event),
        isStarting = false,
        now = LocalDateTime.of(date, LocalTime.of(12, 0)),
    )

    @Test
    fun lessonNamesShowTheFullNameOnATileAndForwardTheShortName() {
        var alias: Pair<String, String>? = null
        compose.setContent {
            LecturialTheme {
                LecturialApp(
                    state = state,
                    onGroupSelected = {},
                    onDateSelected = {},
                    onMoveWeek = {},
                    onToday = {},
                    onRefresh = {},
                    onRetry = {},
                    onLessonAliasChanged = { original, custom -> alias = original to custom },
                )
            }
        }

        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Названия пар").performScrollTo().performClick()

        // The full name heads its tile; the field below holds it until a short name is typed.
        compose.onAllNodesWithText(longName)[0].assertIsDisplayed()
        compose.onNodeWithText("Короткое имя").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("Матан")

        compose.runOnIdle { assertEquals(longName to "Матан", alias) }
    }

    @Test
    fun widgetPageShowsItsOwnSettingsAndTheMatchRowIsOneSwitch() {
        var matchApp: Boolean? = null
        compose.setContent {
            LecturialTheme {
                LecturialApp(
                    state = state.copy(widgetMatchApp = false),
                    onGroupSelected = {},
                    onDateSelected = {},
                    onMoveWeek = {},
                    onToday = {},
                    onRefresh = {},
                    onRetry = {},
                    onWidgetMatchAppChanged = { matchApp = it },
                )
            }
        }

        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Оформление").performScrollTo().performClick()
        compose.onNodeWithText("Оформление виджета").performScrollTo().performClick()

        compose.onNodeWithText("Цветовые полосы").performScrollTo().assertIsDisplayed()
        // The whole row is the switch: one target, announced with the switch role.
        val matchRow = compose.onNode(isToggleable() and hasText("Как в приложении", substring = true))
        matchRow.assertIsOff()
        matchRow.performClick()

        compose.runOnIdle { assertEquals(true, matchApp) }
    }

    @Test
    fun theOpenTabAndSettingsPageSurviveRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            LecturialTheme {
                LecturialApp(
                    state = state,
                    onGroupSelected = {},
                    onDateSelected = {},
                    onMoveWeek = {},
                    onToday = {},
                    onRefresh = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("О приложении").performScrollTo().performClick()
        compose.onNodeWithText("Политика конфиденциальности").performScrollTo().assertIsDisplayed()

        // What a rotation does: the activity is recreated from its saved state.
        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Политика конфиденциальности").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aboutLeadsToThePrivacyPage() {
        compose.setContent {
            LecturialTheme {
                LecturialApp(
                    state = state,
                    onGroupSelected = {},
                    onDateSelected = {},
                    onMoveWeek = {},
                    onToday = {},
                    onRefresh = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("О приложении").performScrollTo().performClick()
        compose.onNodeWithText("Политика конфиденциальности").performScrollTo().performClick()

        compose.onNodeWithText("Какие данные используются").performScrollTo().assertIsDisplayed()
    }
}
