package com.rassvet.lecturial.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.SemanticsActions
import com.rassvet.lecturial.model.AppTheme
import com.rassvet.lecturial.model.AppDisplayOptions
import com.rassvet.lecturial.model.AppUpdateState
import com.rassvet.lecturial.model.ScheduleDay
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.ScheduleWeek
import com.rassvet.lecturial.model.StudyPlan
import com.rassvet.lecturial.model.StudyPlanType
import com.rassvet.lecturial.ui.theme.LecturialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicInteger

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

        compose.onNodeWithText("На этой неделе занятий нет").assertIsDisplayed()
    }

    @Test
    fun emptyScheduleDisappearsWhenLessonsArrive() {
        val date = LocalDate.of(2026, 8, 31)
        val emptyWeek = ScheduleWeek("ИОП-ИТ-26/1", date, listOf(ScheduleDay(date, emptyList())))
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
        var state by mutableStateOf(
            ScheduleUiState(
                selectedGroup = "ИОП-ИТ-26/1",
                selectedDate = date,
                weekStart = date,
                week = emptyWeek,
                isStarting = false,
                now = LocalDateTime.of(date, LocalTime.of(12, 0)),
            ),
        )
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
                )
            }
        }

        compose.onNodeWithText("На этой неделе занятий нет").assertIsDisplayed()

        compose.runOnIdle {
            state = state.copy(
                week = ScheduleWeek("ИОП-ИТ-26/1", date, listOf(ScheduleDay(date, listOf(event)))),
                events = listOf(event),
            )
        }

        compose.onNodeWithText("На этой неделе занятий нет").assertDoesNotExist()
        compose.onNodeWithText("Физика").assertIsDisplayed()
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
        compose.onNodeWithText("Неон").performScrollTo().performClick()

        compose.runOnIdle { assertEquals(AppTheme.Neon, selectedTheme) }
    }

    @Test
    fun appearanceForwardsLightModeSelection() {
        var darkTheme: Boolean? = null
        render(
            state = ScheduleUiState(selectedGroup = "ИОП-ИТ-26/1", isStarting = false),
            onDarkThemeChanged = { darkTheme = it },
        )

        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Оформление").performClick()
        compose.onNodeWithText("Тёмный режим").performClick()

        compose.runOnIdle { assertEquals(false, darkTheme) }
    }

    @Test
    fun deadlineReminderSelectionIsShownAndForwarded() {
        var selectedDays: Set<Int>? = null
        render(
            state = ScheduleUiState(
                selectedGroup = "ИОП-ИТ-26/1",
                isStarting = false,
                deadlineReminderDays = setOf(7, 1, 0),
            ),
            onDeadlineReminderDaysChanged = { selectedDays = it },
        )

        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Напоминания о сроках").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Выбрано: за неделю, за день, в день срока")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("За неделю").performScrollTo().performClick()

        compose.runOnIdle { assertEquals(setOf(1, 0), selectedDays) }
    }

    @Test
    fun scheduleRangeSettingsShowValuesAndForwardChanges() {
        var weeksBack: Int? = null
        var weeksForward: Int? = null
        render(
            state = ScheduleUiState(
                selectedGroup = "ИОП-ИТ-26/1",
                isStarting = false,
                scheduleWeeksBack = 1,
                scheduleWeeksForward = 1,
            ),
            onScheduleWeeksBackChanged = { weeksBack = it },
            onScheduleWeeksForwardChanged = { weeksForward = it },
        )

        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Диапазон расписания").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Недель назад: 1 неделя")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(2f) }
        compose.onNodeWithContentDescription("Недель вперёд: 1 неделя")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(2f) }

        compose.runOnIdle {
            assertEquals(2, weeksBack)
            assertEquals(2, weeksForward)
        }
    }

    @Test
    fun builtInThemeCanBeOpenedForEditing() {
        render(ScheduleUiState(selectedGroup = "ИОП-ИТ-26/1", isStarting = false))

        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Оформление").performClick()
        compose.onAllNodesWithText("Изменить")[0].performClick()

        compose.onNodeWithText("Тема «Сириус»").assertIsDisplayed()
        compose.onNodeWithText("Сохранить и применить").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun appearanceForwardsScheduleSettingsReset() {
        var resetCalled = false
        render(
            state = ScheduleUiState(
                selectedGroup = "ИОП-ИТ-26/1",
                isStarting = false,
                appDisplayOptions = AppDisplayOptions(showTime = false),
            ),
            onAppDisplayOptionsReset = { resetCalled = true },
        )

        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Оформление").performClick()
        compose.onNodeWithText("Сбросить настройки расписания").performScrollTo().performClick()

        compose.runOnIdle { assertEquals(true, resetCalled) }
    }

    @Test
    fun aboutShowsAvailableGitHubUpdateAndChecksWhenIdle() {
        val checks = AtomicInteger()
        var state by mutableStateOf(
            ScheduleUiState(
                selectedGroup = "ИОП-ИТ-26/1",
                isStarting = false,
                appUpdateState = AppUpdateState.Idle,
            ),
        )
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
                    onCheckForUpdates = { checks.incrementAndGet() },
                )
            }
        }

        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("О приложении").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Версия 1.6.1-debug")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.waitUntil(timeoutMillis = 5_000) { checks.get() == 1 }
        compose.runOnIdle {
            assertEquals(1, checks.get())
            state = state.copy(
                appUpdateState = AppUpdateState.Available(
                    "1.7.0",
                    "https://github.com/neckmnull/Lecturial-2/releases/tag/v1.7.0",
                    "https://github.com/neckmnull/Lecturial-2/releases/download/v1.7.0/Lecturial-2-v1.7.0.apk",
                    "Lecturial-2-v1.7.0.apk",
                ),
            )
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Доступна версия 1.7.0.")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("Доступна версия 1.7.0.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Скачать и установить").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Открыть релиз на GitHub").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun appearanceOffersMoreThemesAndThemeCreator() {
        render(
            ScheduleUiState(
                selectedGroup = "ИОП-ИТ-26/1",
                isStarting = false,
            ),
        )

        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Оформление").performClick()
        compose.onNodeWithText("Аметист").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Бордовый").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Янтарь").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Графит").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Неон").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Фуксия").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Электрик").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Создать тему").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun customThemeEditorOffersColorPalettes() {
        render(
            ScheduleUiState(
                selectedGroup = "ИОП-ИТ-26/1",
                isStarting = false,
            ),
        )

        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Оформление").performClick()
        compose.onNodeWithText("Создать тему").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Открыть палитру: Основной цвет").performClick()
        compose.onNodeWithContentDescription("Hue-колесо: Основной цвет").assertIsDisplayed()
        compose.onNodeWithContentDescription("Основной цвет, цвет #62D5E8").performScrollTo().performClick()

        compose.onNodeWithText("#62D5E8").assertIsDisplayed()
        compose.onNodeWithContentDescription("Живой предпросмотр темы приложения")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun appearanceContainsAllVisualSettingsAndFontPreview() {
        render(
            ScheduleUiState(
                selectedGroup = "ИОП-ИТ-26/1",
                isStarting = false,
            ),
        )

        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Оформление").performClick()
        compose.onNodeWithText("Оформление расписания").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Живой предпросмотр расписания")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Показывать аудиторию").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Показывать время").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Показывать цветную полосу").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Время окончания").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Переносить название").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Предпросмотр шрифта: Системный Medium")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Выбрать шрифт, сейчас Системный Medium")
            .performScrollTo()
            .performClick()
        compose.onNodeWithContentDescription("Предпросмотр шрифта: Моноширинный")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithText("Оформление виджета").performScrollTo().assertIsDisplayed()
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

    @Test
    fun scheduleWeeksAreShownInOneScrollableFeed() {
        val currentStart = LocalDate.of(2026, 8, 31)
        fun week(offset: Long, discipline: String): ScheduleWeek {
            val start = currentStart.plusWeeks(offset)
            val event = ScheduleEvent(
                id = "shared-id",
                date = start,
                startTime = "08:45",
                endTime = "10:05",
                pairNumber = 1,
                discipline = discipline,
                type = "Лекция",
                classroom = null,
                address = null,
                teachers = emptyList(),
                comment = null,
                onlineUrl = null,
            )
            return ScheduleWeek("ИОП-ИТ-26/1", start, listOf(ScheduleDay(start, listOf(event))))
        }
        render(
            ScheduleUiState(
                selectedGroup = "ИОП-ИТ-26/1",
                selectedDate = currentStart,
                weekStart = currentStart,
                scheduleWeeks = listOf(
                    week(-1, "История"),
                    week(0, "Физика"),
                    week(1, "Алгоритмы"),
                ),
                isStarting = false,
                now = LocalDateTime.of(currentStart, LocalTime.NOON),
            ),
        )

        compose.onNodeWithTag("schedule-list").performScrollToNode(hasText("Предыдущая неделя"))
        compose.onNodeWithText("Предыдущая неделя").assertIsDisplayed()
        compose.onNodeWithTag("schedule-list").performScrollToNode(hasText("История"))
        compose.onNodeWithText("История").assertIsDisplayed()
        compose.onNodeWithTag("schedule-list").performScrollToNode(hasText("Следующая неделя"))
        compose.onNodeWithText("Следующая неделя").assertIsDisplayed()
        compose.onNodeWithTag("schedule-list").performScrollToNode(hasText("Алгоритмы"))
        compose.onNodeWithText("Алгоритмы").assertIsDisplayed()
    }

    @Test
    fun scheduleOpensAtHighlightedCurrentWeek() {
        val currentStart = LocalDate.of(2026, 8, 31)
        fun week(start: LocalDate, discipline: String) = ScheduleWeek(
            group = "ИОП-ИТ-26/1",
            weekStart = start,
            days = listOf(
                ScheduleDay(
                    date = start,
                    events = listOf(
                        ScheduleEvent(
                            id = discipline,
                            date = start,
                            startTime = "08:45",
                            endTime = "10:05",
                            pairNumber = 1,
                            discipline = discipline,
                            type = "Лекция",
                            classroom = null,
                            address = null,
                            teachers = emptyList(),
                            comment = null,
                            onlineUrl = null,
                        ),
                    ),
                ),
            ),
        )
        render(
            ScheduleUiState(
                selectedGroup = "ИОП-ИТ-26/1",
                selectedDate = currentStart,
                weekStart = currentStart,
                scheduleWeeks = listOf(
                    week(currentStart.minusWeeks(2), "Старая дисциплина"),
                    week(currentStart.minusWeeks(1), "История"),
                    week(currentStart, "Физика"),
                    week(currentStart.plusWeeks(1), "Алгоритмы"),
                ),
                isStarting = false,
                now = LocalDateTime.of(currentStart, LocalTime.NOON),
            ),
        )

        compose.waitForIdle()
        compose.onNodeWithTag("current-week").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithText("Текущая неделя").assertIsDisplayed()
    }

    @Test
    fun scheduleOpensAtTodayWhenTodayHasNoLessons() {
        val currentStart = LocalDate.of(2026, 8, 31)
        val sunday = currentStart.plusDays(6)
        val nextStart = currentStart.plusWeeks(1)
        val nextLesson = ScheduleEvent(
            id = "next-week",
            date = nextStart,
            startTime = "08:45",
            endTime = "10:05",
            pairNumber = 1,
            discipline = "Алгоритмы",
            type = "Лекция",
            classroom = null,
            address = null,
            teachers = emptyList(),
            comment = null,
            onlineUrl = null,
        )
        render(
            ScheduleUiState(
                selectedGroup = "ИОП-ИТ-26/1",
                selectedDate = sunday,
                weekStart = currentStart,
                scheduleWeeks = listOf(
                    ScheduleWeek("ИОП-ИТ-26/1", currentStart.minusWeeks(1), emptyList()),
                    ScheduleWeek("ИОП-ИТ-26/1", currentStart, emptyList()),
                    ScheduleWeek(
                        "ИОП-ИТ-26/1",
                        nextStart,
                        listOf(ScheduleDay(nextStart, listOf(nextLesson))),
                    ),
                ),
                isStarting = false,
                now = LocalDateTime.of(sunday, LocalTime.NOON),
            ),
        )

        compose.waitForIdle()
        compose.onNodeWithText("Сегодня").assertIsDisplayed()
        compose.onNodeWithText("6 сентября").assertIsDisplayed()
    }

    @Test
    fun plansTabShowsLocalPlanAndForwardsCompletion() {
        val date = LocalDate.of(2026, 11, 10)
        var completed: Pair<String, Boolean>? = null
        val state = ScheduleUiState(
            selectedGroup = "ИОП-ИТ-26/1",
            selectedDate = date,
            weekStart = date,
            isStarting = false,
            studyPlans = listOf(
                StudyPlan(
                    id = "deadline-1",
                    type = StudyPlanType.Deadline,
                    title = "Сдать лабораторную",
                    dueDate = LocalDate.now(),
                    createdAtMillis = 1L,
                ),
            ),
        )

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
                    onStudyPlanCompletedChanged = { id, value -> completed = id to value },
                )
            }
        }

        compose.onNodeWithText("Планы").performClick()
        compose.onNodeWithText("Сдать лабораторную").assertIsDisplayed()
        compose.onNodeWithContentDescription("Отметить выполненным").performClick()
        compose.runOnIdle { assertEquals("deadline-1" to true, completed) }
    }

    @Test
    fun datedPlanAppearsInScheduleWithTimeAndUndatedPlanDoesNot() {
        val date = LocalDate.of(2026, 11, 10)
        var completed: Pair<String, Boolean>? = null
        val timedPlan = StudyPlan(
            id = "timed-plan",
            type = StudyPlanType.Task,
            title = "Повторить конспект",
            dueDate = date,
            dueTime = LocalTime.of(14, 30),
            createdAtMillis = 1L,
        )
        val state = ScheduleUiState(
            selectedGroup = "ИОП-ИТ-26/1",
            selectedDate = date,
            weekStart = date.with(java.time.DayOfWeek.MONDAY),
            week = ScheduleWeek("ИОП-ИТ-26/1", date.with(java.time.DayOfWeek.MONDAY), emptyList()),
            isStarting = false,
            now = LocalDateTime.of(date, LocalTime.NOON),
            studyPlans = listOf(
                timedPlan,
                timedPlan.copy(id = "undated-plan", title = "План без даты", dueDate = null, dueTime = null),
            ),
        )

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
                    onStudyPlanCompletedChanged = { id, value -> completed = id to value },
                )
            }
        }

        compose.onNodeWithText("Повторить конспект").assertIsDisplayed()
        compose.onNodeWithText("14:30").assertIsDisplayed()
        compose.onNodeWithText("План без даты").assertDoesNotExist()
        compose.onNodeWithContentDescription("Отметить выполненным").performClick()
        compose.runOnIdle { assertEquals("timed-plan" to true, completed) }
    }

    private fun render(
        state: ScheduleUiState,
        onGroupSelected: (String) -> Unit = {},
        onGroupSearchQueryChanged: (String) -> Unit = {},
        onGroupsRetry: () -> Unit = {},
        onAppThemeSelected: (AppTheme) -> Unit = {},
        onDarkThemeChanged: (Boolean) -> Unit = {},
        onAppDisplayOptionsReset: () -> Unit = {},
        onDeadlineReminderDaysChanged: (Set<Int>) -> Unit = {},
        onScheduleWeeksBackChanged: (Int) -> Unit = {},
        onScheduleWeeksForwardChanged: (Int) -> Unit = {},
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
                    onDarkThemeChanged = onDarkThemeChanged,
                    onAppDisplayOptionsReset = onAppDisplayOptionsReset,
                    onDeadlineReminderDaysChanged = onDeadlineReminderDaysChanged,
                    onScheduleWeeksBackChanged = onScheduleWeeksBackChanged,
                    onScheduleWeeksForwardChanged = onScheduleWeeksForwardChanged,
                )
            }
        }
    }
}
