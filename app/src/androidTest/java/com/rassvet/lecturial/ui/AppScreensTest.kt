package com.rassvet.lecturial.ui

import android.content.Context
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.rassvet.lecturial.BuildConfig
import com.rassvet.lecturial.data.lks.LksState
import com.rassvet.lecturial.model.AppUpdateState
import com.rassvet.lecturial.model.ExtracurricularChoice
import com.rassvet.lecturial.model.LksDiscipline
import com.rassvet.lecturial.model.LksMark
import com.rassvet.lecturial.model.LksNews
import com.rassvet.lecturial.model.LksNotification
import com.rassvet.lecturial.model.LksSemester
import com.rassvet.lecturial.model.LksSnapshot
import com.rassvet.lecturial.model.ScheduleTarget
import com.rassvet.lecturial.model.StudyPlan
import com.rassvet.lecturial.ui.theme.LecturialTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Every tab and settings page, the event choice, the Study tab and the whole first-run walk. */
class AppScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComposeTestActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val date = LocalDate.of(2026, 8, 31)
    private val state = ScheduleUiState(
        selectedGroup = "ИОП-ИТ-26/1",
        languageTeacher = "",
        selectedDate = date,
        weekStart = date,
        isStarting = false,
        now = LocalDateTime.of(date, LocalTime.of(12, 0)),
    )

    @Before
    fun skipFirstRunSetup() = FirstRun.markDone(context)

    private fun render(
        state: ScheduleUiState,
        onExtracurricularChoiceChanged: (ExtracurricularChoice) -> Unit = {},
        onStudyPlanSaved: (StudyPlan) -> Unit = {},
    ) {
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
                    onExtracurricularChoiceChanged = onExtracurricularChoiceChanged,
                    onStudyPlanSaved = onStudyPlanSaved,
                )
            }
        }
    }

    private fun hasNode(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    // ------------------------------------------------------------------ navigation

    @Test
    fun aTeacherHasStatisticsInsteadOfStudy() {
        render(state.copy(selectedGroup = ScheduleTarget.teacherKey("7"), scheduleTargetName = "Иванов И. И."))

        compose.onAllNodesWithText("Учёба").assertCountEquals(0)
        compose.onNodeWithText("Статистика").performClick()
        compose.waitForIdle()
        assertTrue("The Statistics tab", hasNode("Нагрузка"))
    }

    @Test
    fun everyStudentTabOpens() {
        render(state)

        compose.onAllNodesWithText("Статистика").assertCountEquals(0)
        compose.onNodeWithText("Учёба").performClick()
        compose.waitForIdle()
        assertTrue("The Study tab", compose.onAllNodesWithText("Учёба").fetchSemanticsNodes().size >= 2)

        compose.onNodeWithText("Планы").performClick()
        compose.onNodeWithContentDescription("Добавить план").assertIsDisplayed()

        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText(SETTINGS_ROOT).assertIsDisplayed()

        // Settings has a row with the same name.
        compose.onNode(hasText("Расписание") and hasAnyAncestor(hasTestTag("bottom-navigation"))).performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText(SETTINGS_ROOT).assertCountEquals(0)
    }

    @Test
    fun everySettingsPageOpensAndBackReturns() {
        render(state)
        compose.onNodeWithText("Настройки").performClick()

        val pages = buildList {
            addAll(listOf("Преподаватель иностранного языка", "Внеучебные мероприятия", "Названия пар", "Оформление", "Outlook", "Google Календарь", "О приложении"))
            if (BuildConfig.DEBUG) add("Тестирование")
        }
        pages.forEach { page ->
            compose.onNodeWithText(page).performScrollTo().performClick()
            compose.waitForIdle()
            compose.onAllNodesWithText(SETTINGS_ROOT).assertCountEquals(0)

            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
            assertTrue("Back from $page", hasNode(SETTINGS_ROOT))
        }
    }

    // ------------------------------------------------------------------ events outside the curriculum

    @Test
    fun theEventChoiceListsNamesAndForwardsEverySwitch() {
        var chosen: ExtracurricularChoice? = null
        render(
            state.copy(extracurricularChoice = ExtracurricularChoice(hiddenNames = setOf("Разговоры о важном"))),
            onExtracurricularChoiceChanged = { chosen = it },
        )
        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Скрыто: 1").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Внеучебные мероприятия").performScrollTo().performClick()

        // A hidden name stays on the list, so it can be brought back while no week shows it.
        val name = compose.onNode(isToggleable() and hasText("Разговоры о важном"))
        name.assertIsOff()
        name.performClick()
        compose.runOnIdle { assertEquals(emptySet<String>(), chosen?.hiddenNames) }

        compose.onNode(isToggleable() and hasText("Показывать мероприятия")).performClick()
        compose.runOnIdle { assertEquals(false, chosen?.show) }
    }

    @Test
    fun withEventsOffTheNamesAreAwayAndTheRowSaysSo() {
        render(state.copy(extracurricularChoice = ExtracurricularChoice(show = false, hiddenNames = setOf("Экскурсия"))))
        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Скрыты").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Внеучебные мероприятия").performScrollTo().performClick()

        compose.onNode(isToggleable() and hasText("Показывать мероприятия")).assertIsOff()
        compose.onAllNodesWithText("Какие показывать").assertCountEquals(0)
    }

    @Test
    fun withNoEventsLoadedTheChoiceSaysSo() {
        render(state.copy(selectedGroup = "Группа без мероприятий"))
        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Показываются").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Внеучебные мероприятия").performScrollTo().performClick()

        compose.onNodeWithText("В загруженных неделях мероприятий нет", substring = true).assertIsDisplayed()
    }

    @Test
    fun aTeacherHasNoEventChoice() {
        render(state.copy(selectedGroup = ScheduleTarget.teacherKey("7"), scheduleTargetName = "Иванов И. И."))
        compose.onNodeWithText("Настройки").performClick()
        compose.waitForIdle()

        compose.onAllNodesWithText("Внеучебные мероприятия").assertCountEquals(0)
        compose.onAllNodesWithText("Преподаватель иностранного языка").assertCountEquals(0)
    }

    // ------------------------------------------------------------------ plans and updates

    @Test
    fun aNewPlanNeedsATitleAndIsSavedWithIt() {
        var saved: StudyPlan? = null
        render(state, onStudyPlanSaved = { saved = it })
        compose.onNodeWithText("Планы").performClick()
        compose.onNodeWithContentDescription("Добавить план").performClick()

        compose.onNodeWithText("Сохранить").assertIsNotEnabled()
        compose.onNodeWithText("Название").performTextInput("  Курсовая  ")
        compose.onNodeWithText("Сохранить").assertIsEnabled().performClick()

        compose.runOnIdle { assertEquals("Курсовая", saved?.title) }
    }

    @Test
    fun aRequiredUpdateClosesOffTheApp() {
        render(state.copy(requiredUpdateVersion = "9.9.9", appUpdateState = AppUpdateState.UpToDate("1.0")))

        compose.onNodeWithText("Нужно обновить приложение").assertIsDisplayed()
        compose.onNodeWithText("Версия 9.9.9 обязательна", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Настройки").assertCountEquals(0)
    }

    // ------------------------------------------------------------------ the Study tab

    private val mark = LksMark("m1", "Физика", date, "13:30", "Лекции", 5, false, "Оптика", null)
    private val snapshot = LksSnapshot(
        marks = listOf(mark, mark.copy(id = "m2", grade = 4, absent = true)),
        semesters = listOf(LksSemester("3", "2026/2027", listOf(LksDiscipline("Физика", listOf("Экзамен"), 4.0, 144, emptyList(), null)))),
        notifications = listOf(LksNotification("n1", "Заголовок уведомления", "Текст", "02.11.2026")),
        unreadNotifications = 1,
        news = listOf(LksNews("15", "Объявление университета", "2026-11-01")),
        loadedAtMillis = 7L,
    )

    private fun renderStudy(state: LksState, onSignIn: () -> Unit = {}, onDisconnect: () -> Unit = {}) {
        compose.setContent {
            LecturialTheme {
                StudyContent(state, emptyList(), onSignIn = onSignIn, onRefresh = {}, onDisconnect = onDisconnect)
            }
        }
    }

    @Test
    fun signedOutTheStudyTabOffersTheSignIn() {
        var asked = 0
        renderStudy(LksState(), onSignIn = { asked++ })

        compose.onNodeWithText("Войти в личный кабинет").performScrollTo().performClick()

        compose.runOnIdle { assertEquals(1, asked) }
        compose.onAllNodesWithText("Выйти из личного кабинета").assertCountEquals(0)
    }

    @Test
    fun signedInTheStudyTabShowsEverySectionAndSigningOutIsForwarded() {
        var left = 0
        renderStudy(LksState(connected = true, snapshot = snapshot), onDisconnect = { left++ })

        // The average and the subject's row both say it.
        compose.onAllNodesWithText("4,5").onFirst().assertIsDisplayed()
        compose.onNodeWithText("50 %").assertIsDisplayed()

        compose.onNodeWithText("Сессия").performClick()
        compose.onNodeWithText("экзаменов").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Уведомления").performClick()
        compose.onNodeWithText("Заголовок уведомления").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Новости").performClick()
        compose.onNodeWithText("Объявление университета").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Выйти из личного кабинета").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, left) }
    }

    @Test
    fun afterTheSessionEndsTheSavedGradesStayAndTheRateLimitIsExplained() {
        renderStudy(LksState(connected = false, snapshot = snapshot, error = "Обновить можно будет через 30 сек."))

        compose.onNodeWithText("Обновить можно будет через 30 сек.").assertIsDisplayed()
        compose.onNodeWithText("Войти снова — показаны сохранённые данные").assertIsDisplayed()
        compose.onAllNodesWithText("4,5").onFirst().performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Выйти из личного кабинета").assertCountEquals(0)
    }

    // ------------------------------------------------------------------ sign-in screen and first run

    @Test
    fun theSignInPageCannotBeCapturedAndTheBanEndsWithIt() {
        fun secure() = compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        compose.setContent {
            var open by remember { mutableStateOf(true) }
            LecturialTheme {
                if (open) LksLoginScreen(onSignedIn = {}, onClose = { open = false })
            }
        }
        compose.waitForIdle()
        assertTrue(secure())

        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.waitForIdle()
        assertFalse(secure())
    }

    @Test
    fun theButtonOnTheGroupStepSaysWhatIsMissing() {
        render(ScheduleUiState(availableGroups = listOf("ИОП-ИТ-26/1"), isStarting = false))

        compose.onNodeWithText("Выбери группу").assertIsNotEnabled()
        compose.onNodeWithText("ИОП-ИТ-26/1").performClick()
        compose.onNodeWithText("Продолжить").assertIsEnabled()
    }

    @Test
    fun theFirstRunWalksAllTheWayToTheSchedule() {
        context.getSharedPreferences("first_run", Context.MODE_PRIVATE).edit().clear().commit()
        compose.setContent {
            var current by remember { mutableStateOf(ScheduleUiState(availableGroups = listOf("ИОП-ИТ-26/1"), isStarting = false)) }
            LecturialTheme {
                LecturialApp(
                    state = current,
                    onGroupSelected = { current = current.copy(selectedGroup = it, languageTeacher = "") },
                    onDateSelected = {},
                    onMoveWeek = {},
                    onToday = {},
                    onRefresh = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("Начать").performClick()
        compose.onNodeWithText("ИОП-ИТ-26/1").performClick()
        compose.onNodeWithText("Продолжить").performClick()
        compose.waitForIdle()
        // Optional steps can be skipped; the notifications step asks only when its button is pressed.
        listOf("Личный кабинет", "Уведомления", "Оформление").forEach { step ->
            compose.onNodeWithText(step).assertIsDisplayed()
            compose.onNodeWithText("Пропустить").performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithText("Пропустить").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Открыть расписание").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Настройки").assertIsDisplayed()
        assertTrue(FirstRun.isDone(context))
    }

    private companion object {
        const val SETTINGS_ROOT = "Расписание, виджет и приложение"
    }
}
