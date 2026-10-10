package com.rassvet.lecturial.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.rassvet.lecturial.model.AppTheme
import com.rassvet.lecturial.model.ScheduleDay
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.ScheduleWeek
import com.rassvet.lecturial.ui.theme.LecturialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class LecturialAppTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun firstLaunchOffersDynamicGroups() {
        var selected: String? = null
        render(
            ScheduleUiState(
                availableGroups = listOf("ИОП-ИТ-26/1", "Биоинженерия 24"),
                isStarting = false,
            ),
            onGroupSelected = { selected = it },
        )

        compose.onNodeWithText("ИОП-ИТ-26/1").assertIsDisplayed()
        compose.onNodeWithText("Биоинженерия 24").assertIsDisplayed()
        compose.onNodeWithText("Биоинженерия 24").performClick()
        compose.onNodeWithText("Продолжить").performClick()
        assertEquals("Биоинженерия 24", selected)
    }

    @Test
    fun groupSearchForwardsEnteredQuery() {
        var state by mutableStateOf(ScheduleUiState(isStarting = false))
        compose.setContent {
            LecturialTheme(appTheme = state.appTheme) {
                LecturialApp(
                    state = state,
                    onGroupSelected = {},
                    onDateSelected = {},
                    onMoveWeek = {},
                    onToday = {},
                    onRefresh = {},
                    onRetry = {},
                    onGroupSearchQueryChanged = { query ->
                        state = state.copy(groupSearchQuery = query)
                    },
                )
            }
        }

        compose.onNodeWithText("Поиск группы").performTextInput("Био")
        compose.runOnIdle { assertEquals("Био", state.groupSearchQuery) }
    }

    @Test
    fun groupSearchShowsEmptyResult() {
        render(ScheduleUiState(isStarting = false))

        compose.onNodeWithText("Группы не найдены").assertIsDisplayed()
    }

    @Test
    fun groupSearchErrorOffersRetry() {
        var retries = 0
        render(
            ScheduleUiState(isStarting = false, groupsError = "Нет сети"),
            onGroupsRetry = { retries++ },
        )

        compose.onNodeWithText("Нет сети").assertIsDisplayed()
        compose.onNodeWithText("Повторить").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun groupSearchShowsLoadingState() {
        render(ScheduleUiState(isStarting = false, isGroupsLoading = true))

        compose.onNodeWithContentDescription("Загрузка групп").assertIsDisplayed()
    }

    @Test
    fun emptyDayAndNetworkErrorHaveDedicatedStates() {
        val date = LocalDate.of(2026, 8, 31)
        render(
            state = ScheduleUiState(
                selectedGroup = "ИОП-ИТ-26/1",
                selectedDate = date,
                weekStart = date,
                week = ScheduleWeek("ИОП-ИТ-26/1", date, listOf(ScheduleDay(date, emptyList()))),
                isStarting = false,
                now = LocalDateTime.of(date, LocalTime.of(12, 0)),
            ),
        )

        compose.onNodeWithText("ИОП-ИТ-26/1").assertIsDisplayed()
        compose.onNodeWithText("сегодня • нет пар").assertIsDisplayed()

        compose.onNodeWithText("На ближайшие две недели занятий нет").assertIsDisplayed()
    }

    @Test
    fun errorWithoutCacheOffersRetry() {
        render(
            ScheduleUiState(
                selectedGroup = "ИОП-ИТ-26/1",
                isStarting = false,
                errorMessage = "Нет сети",
            ),
        )

        compose.onNodeWithText("Расписание недоступно").assertIsDisplayed()
        compose.onNodeWithText("Повторить").assertIsDisplayed()
    }

    @Test
    fun lessonCardShowsEveryAvailableFieldAndGroupCanChange() {
        val date = LocalDate.of(2026, 8, 31)
        val event = ScheduleEvent(
            id = "lesson",
            date = date,
            startTime = "08:45",
            endTime = "10:05",
            pairNumber = 1,
            discipline = "Физика",
            type = "Лекция",
            classroom = "5.7",
            address = "Главный корпус",
            teachers = listOf("Иванов Иван Иванович", "Петрова Анна Олеговна"),
            comment = "Принести ноутбук",
            onlineUrl = null,
        )
        var selected: String? = null
        render(
            state = ScheduleUiState(
                selectedGroup = "ИОП-ИТ-26/1",
                selectedDate = date,
                weekStart = date,
                week = ScheduleWeek("ИОП-ИТ-26/1", date, listOf(ScheduleDay(date, listOf(event)))),
                events = listOf(event),
                availableGroups = listOf("ИОП-ИТ-26/1", "Биоинженерия 24"),
                isStarting = false,
                now = LocalDateTime.of(date, LocalTime.of(12, 0)),
            ),
            onGroupSelected = { selected = it },
        )

        listOf(
            "08:45",
            "10:05",
            "Лекция",
            "Физика",
            "5.7 · Главный корпус",
            "И. Иван Иванович, П. Анна Олеговна",
            "Принести ноутбук",
            "сегодня • 1 пара",
        ).forEach { compose.onNodeWithText(it).assertIsDisplayed() }

        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithContentDescription("Выбрать группу, сейчас ИОП-ИТ-26/1").performClick()
        compose.onNodeWithText("Биоинженерия 24").performClick()
        assertEquals("Биоинженерия 24", selected)
    }

    @Test
    fun appearanceScreenForwardsSelectedTheme() {
        var selectedTheme: AppTheme? = null
        render(
            state = ScheduleUiState(
                selectedGroup = "ИОП-ИТ-26/1",
                isStarting = false,
            ),
            onAppThemeSelected = { selectedTheme = it },
        )

        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Оформление").performClick()
        compose.onNodeWithText("Океан").performClick()

        compose.runOnIdle { assertEquals(AppTheme.Ocean, selectedTheme) }
    }

    @Test
    fun longLessonTextStaysInsideTheScreen() {
        val date = LocalDate.of(2026, 8, 31)
        val discipline = "Проектирование распределённых информационных систем с очень длинным названием дисциплины"
        val comment = "Очень длинный комментарий к занятию, который не должен накладываться на соседние элементы или выходить за границы карточки"
        val event = ScheduleEvent(
            id = "long-lesson",
            date = date,
            startTime = "08:45",
            endTime = "10:05",
            pairNumber = 1,
            discipline = discipline,
            type = "Лекция",
            classroom = "Очень длинное название аудитории и корпуса Университета «Сириус»",
            address = null,
            teachers = listOf("Преподаватель С Очень Длинным Составным Именем"),
            comment = comment,
            onlineUrl = null,
        )
        render(
            ScheduleUiState(
                selectedGroup = "ИОП-ИТ-26/1",
                selectedDate = date,
                weekStart = date,
                week = ScheduleWeek("ИОП-ИТ-26/1", date, listOf(ScheduleDay(date, listOf(event)))),
                events = listOf(event),
                isStarting = false,
                now = LocalDateTime.of(date, LocalTime.of(12, 0)),
            ),
        )

        val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        listOf(discipline, comment).forEach { text ->
            val textBounds = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
            check(textBounds.left >= rootBounds.left) { "$text выходит за левую границу экрана" }
            check(textBounds.right <= rootBounds.right) { "$text выходит за правую границу экрана" }
        }
    }

    @Test
    fun lectureBadgeIsSingularAndGenericCampusAddressIsHidden() {
        val date = LocalDate.of(2026, 8, 31)
        val event = ScheduleEvent(
            id = "lesson",
            date = date,
            startTime = "08:45",
            endTime = "10:05",
            pairNumber = 1,
            discipline = "Физика",
            type = "Лекции",
            classroom = "5.7",
            address = "Основной",
            teachers = emptyList(),
            comment = null,
            onlineUrl = null,
        )
        render(
            ScheduleUiState(
                selectedGroup = "ИОП-ИТ-26/1",
                selectedDate = date,
                weekStart = date,
                week = ScheduleWeek("ИОП-ИТ-26/1", date, listOf(ScheduleDay(date, listOf(event)))),
                events = listOf(event),
                isStarting = false,
                now = LocalDateTime.of(date, LocalTime.of(12, 0)),
            ),
        )

        compose.onNodeWithText("Лекция").assertIsDisplayed()
        compose.onNodeWithText("Лекции").assertDoesNotExist()
        compose.onNodeWithText("5.7").assertIsDisplayed()
        compose.onNodeWithText("Основной").assertDoesNotExist()
    }

    @Test
    fun restHintAppearsBetweenLessonsWithAGap() {
        val date = LocalDate.of(2026, 8, 31)
        val first = ScheduleEvent(
            id = "first",
            date = date,
            startTime = "08:45",
            endTime = "10:05",
            pairNumber = 1,
            discipline = "Физика",
            type = "Лекция",
            classroom = "5.7",
            address = null,
            teachers = emptyList(),
            comment = null,
            onlineUrl = null,
        )
        val later = first.copy(
            id = "later",
            startTime = "13:00",
            endTime = "14:20",
            pairNumber = 3,
            discipline = "Математика",
            type = "Практика",
        )
        render(
            ScheduleUiState(
                selectedGroup = "ИОП-ИТ-26/1",
                selectedDate = date,
                weekStart = date,
                week = ScheduleWeek("ИОП-ИТ-26/1", date, listOf(ScheduleDay(date, listOf(first, later)))),
                events = listOf(first, later),
                isStarting = false,
                now = LocalDateTime.of(date, LocalTime.of(12, 0)),
            ),
        )

        compose.onNodeWithText("можно отдохнуть с 10:05 до 13:00", substring = true).assertIsDisplayed()
    }

    @Test
    fun currentLessonShowsNowCapsule() {
        val date = LocalDate.of(2026, 8, 31)
        val event = ScheduleEvent(
            id = "lesson",
            date = date,
            startTime = "08:45",
            endTime = "10:05",
            pairNumber = 1,
            discipline = "Физика",
            type = "Лекция",
            classroom = "5.7",
            address = null,
            teachers = emptyList(),
            comment = null,
            onlineUrl = null,
        )
        render(
            ScheduleUiState(
                selectedGroup = "ИОП-ИТ-26/1",
                selectedDate = date,
                weekStart = date,
                week = ScheduleWeek("ИОП-ИТ-26/1", date, listOf(ScheduleDay(date, listOf(event)))),
                events = listOf(event),
                isStarting = false,
                now = LocalDateTime.of(date, LocalTime.of(9, 0)),
            ),
        )

        compose.onNodeWithText("ещё 1 ч 5 мин").assertIsDisplayed()
    }

    @Test
    fun nextWeekIsSeparatedFromCurrentWeek() {
        val currentStart = LocalDate.of(2026, 8, 31)
        val nextStart = LocalDate.of(2026, 9, 7)
        val current = ScheduleEvent(
            id = "now",
            date = currentStart,
            startTime = "08:45",
            endTime = "10:05",
            pairNumber = 1,
            discipline = "Физика",
            type = "Лекция",
            classroom = null,
            address = null,
            teachers = emptyList(),
            comment = null,
            onlineUrl = null,
        )
        val upcoming = current.copy(id = "next", date = nextStart, discipline = "Алгоритмы")
        render(
            ScheduleUiState(
                selectedGroup = "ИОП-ИТ-26/1",
                selectedDate = currentStart,
                weekStart = currentStart,
                week = ScheduleWeek("ИОП-ИТ-26/1", currentStart, listOf(ScheduleDay(currentStart, listOf(current)))),
                nextWeek = ScheduleWeek("ИОП-ИТ-26/1", nextStart, listOf(ScheduleDay(nextStart, listOf(upcoming)))),
                events = listOf(current),
                isStarting = false,
                now = LocalDateTime.of(currentStart, LocalTime.of(12, 0)),
            ),
        )

        compose.onNodeWithText("Сегодня").assertIsDisplayed()
        compose.onNodeWithText("Следующая неделя").assertIsDisplayed()
        compose.onNodeWithText("7–13 сентября").assertIsDisplayed()
        compose.onNodeWithText("Алгоритмы").assertIsDisplayed()
    }

    private fun render(
        state: ScheduleUiState,
        onGroupSelected: (String) -> Unit = {},
        onGroupSearchQueryChanged: (String) -> Unit = {},
        onGroupsRetry: () -> Unit = {},
        onAppThemeSelected: (AppTheme) -> Unit = {},
    ) {
        compose.setContent {
            LecturialTheme(appTheme = state.appTheme) {
                LecturialApp(
                    state = state,
                    onGroupSelected = onGroupSelected,
                    onGroupSearchQueryChanged = onGroupSearchQueryChanged,
                    onGroupsRetry = onGroupsRetry,
                    onDateSelected = {},
                    onMoveWeek = {},
                    onToday = {},
                    onRefresh = {},
                    onRetry = {},
                    onAppThemeSelected = onAppThemeSelected,
                )
            }
        }
    }
}
