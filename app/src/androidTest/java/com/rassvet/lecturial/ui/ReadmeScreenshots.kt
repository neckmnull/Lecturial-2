package com.rassvet.lecturial.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.rassvet.lecturial.data.lks.LksState
import com.rassvet.lecturial.model.AppDisplayOptions
import com.rassvet.lecturial.model.LksMark
import com.rassvet.lecturial.model.LksSnapshot
import com.rassvet.lecturial.model.ScheduleDay
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.ScheduleLayout
import com.rassvet.lecturial.model.ScheduleWeek
import com.rassvet.lecturial.model.StudyPlan
import com.rassvet.lecturial.model.StudyPlanType
import com.rassvet.lecturial.ui.theme.LecturialTheme
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp

/**
 * The README's screenshots, drawn from made-up data so no real schedule or grades end up on
 * GitHub. Runs only when asked: `-e readmeScreenshots true`; the pictures land in the app's
 * cache under `readme/`.
 */
class ReadmeScreenshots {
    @get:Rule
    val compose = createAndroidComposeRule<ComposeTestActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val monday = LocalDate.of(2027, 3, 15)
    private val wednesday = monday.plusDays(2)
    private val group = "ИТ-26/1"

    private fun pair(day: LocalDate, number: Int, discipline: String, type: String, room: String, teacher: String): ScheduleEvent {
        val (start, end) = listOf("08:45" to "10:05", "10:20" to "11:40", "11:55" to "13:15", "13:30" to "14:50", "15:05" to "16:25")[number - 1]
        return ScheduleEvent(
            id = "$day-$number",
            date = day,
            startTime = start,
            endTime = end,
            pairNumber = number,
            discipline = discipline,
            type = type,
            classroom = room,
            address = null,
            teachers = listOf(teacher),
            comment = null,
            onlineUrl = null,
        )
    }

    private val days = listOf(
        ScheduleDay(
            monday,
            listOf(
                pair(monday, 1, "Математический анализ", "Лекция", "Альфа 5.1", "Смирнова Анна Сергеевна"),
                pair(monday, 2, "География", "Практика", "2.15К_1", "Орлов Павел Игоревич"),
                pair(monday, 4, "Иностранный язык", "Практика", "Альфа 5.8", "Белова Мария Андреевна"),
            ),
        ),
        ScheduleDay(
            monday.plusDays(1),
            listOf(
                pair(monday.plusDays(1), 2, "Физика", "Лекция", "Альфа 5.3", "Кузнецов Олег Петрович"),
                pair(monday.plusDays(1), 3, "Физика", "Практика", "1.32К_3", "Кузнецов Олег Петрович"),
            ),
        ),
        ScheduleDay(
            wednesday,
            listOf(
                pair(wednesday, 1, "Линейная алгебра и аналитическая геометрия", "Лекция", "Альфа 5.1", "Смирнова Анна Сергеевна"),
                pair(wednesday, 2, "Алгоритмы и структуры данных", "Практика", "Альфа 5.5", "Волков Дмитрий Олегович"),
                pair(wednesday, 3, "География", "Практика", "2.15К_1", "Орлов Павел Игоревич"),
                pair(wednesday, 5, "История России", "Лекция", "Альфа 5.3", "Никитина Елена Викторовна"),
            ),
        ),
        ScheduleDay(
            monday.plusDays(3),
            listOf(
                pair(monday.plusDays(3), 1, "Иностранный язык", "Практика", "Альфа 5.8", "Белова Мария Андреевна"),
                pair(monday.plusDays(3), 2, "Математический анализ", "Практика", "1.32К_3", "Смирнова Анна Сергеевна"),
            ),
        ),
        ScheduleDay(
            monday.plusDays(4),
            listOf(pair(monday.plusDays(4), 2, "Физическая культура и спорт", "Практика", "Спортзал", "Громов Илья Сергеевич")),
        ),
    )
    private val week = ScheduleWeek(group, monday, days)
    private val deadline = StudyPlan(
        id = "rgr",
        type = StudyPlanType.Deadline,
        title = "Сдать РГР по физике",
        dueDate = wednesday,
        dueTime = LocalTime.of(18, 0),
        createdAtMillis = 0,
    )
    private val state = ScheduleUiState(
        selectedGroup = group,
        selectedDate = wednesday,
        weekStart = monday,
        week = week,
        events = week.days.single { it.date == wednesday }.events,
        studyPlans = listOf(deadline),
        isStarting = false,
        now = wednesday.atTime(10, 52),
    )

    @Before
    fun onlyWhenAsked() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("readmeScreenshots") == "true")
        FirstRun.markDone(context)
    }

    @Test
    fun scheduleFeed() = shoot("1-schedule") { App(state) }

    @Test
    fun scheduleByDay() = shoot("2-by-day") {
        App(state.copy(appDisplayOptions = AppDisplayOptions(scheduleLayout = ScheduleLayout.Days)))
    }

    @Test
    fun studyGrades() = shoot("3-study") { Study() }

    @Test
    fun dayPlanner() = shoot("4-day-plan") { DayPlan() }

    /**
     * The README's demo clip: the feed with the current class, the day-by-day layout swiped
     * along, grades and the day plan, saved frame by frame under `cache/readme/frames/` at
     * [FRAME_MILLIS] steps of the test clock. Runs only with `-e readmeDemo true` as well.
     */
    @Test
    fun walkthrough() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("readmeDemo") == "true")
        var scene by mutableStateOf(0)
        var appState by mutableStateOf(state)
        // From the very first frame, so the cards' entrance is in the clip.
        compose.mainClock.autoAdvance = false
        frames = File(context.cacheDir, "readme/frames").apply {
            deleteRecursively()
            mkdirs()
        }
        compose.setContent {
            LecturialTheme {
                AnimatedContent(
                    targetState = scene,
                    transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                    label = "demo-scene",
                ) { current ->
                    when (current) {
                        0 -> App(appState)
                        1 -> Study()
                        else -> DayPlan()
                    }
                }
            }
        }
        hold(3000)
        compose.onNodeWithTag("schedule-list").performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.4f, durationMillis = 700) }
        hold(1800)
        compose.onNodeWithTag("schedule-list").performTouchInput { swipeDown(startY = bottom * 0.3f, endY = bottom * 0.8f, durationMillis = 700) }
        hold(1500)
        compose.runOnUiThread { appState = appState.copy(appDisplayOptions = AppDisplayOptions(scheduleLayout = ScheduleLayout.Days)) }
        hold(2200)
        repeat(2) {
            compose.onNodeWithTag("schedule-days").performTouchInput { swipeLeft(durationMillis = 350) }
            hold(1600)
        }
        compose.runOnUiThread { scene = 1 }
        hold(2600)
        compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.45f, durationMillis = 700) }
        hold(2000)
        compose.runOnUiThread { scene = 2 }
        hold(2600)
        compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.45f, durationMillis = 700) }
        hold(2500)
    }

    private var frames: File? = null
    private var frameCount = 0

    /** Lets [millis] of the test clock pass, saving a frame of the clip at every step. */
    private fun hold(millis: Long) {
        repeat((millis / FRAME_MILLIS).toInt()) {
            compose.mainClock.advanceTimeBy(FRAME_MILLIS)
            frames?.let { folder -> save(File(folder, "%04d.png".format(frameCount++)), CLIP_WIDTH) }
        }
    }

    private fun save(file: File, width: Int) {
        val shot = compose.onRoot().captureToImage().asAndroidBitmap()
        val scaled = Bitmap.createScaledBitmap(shot, width, shot.height * width / shot.width, true)
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Study() {
        MaterialYouBackground(Modifier.fillMaxSize()) {
            StudyContent(
                state = LksState(connected = true, snapshot = studySnapshot()),
                scheduleEvents = emptyList(),
                onSignIn = {},
                onRefresh = {},
                onDisconnect = {},
            )
        }
    }

    @Composable
    private fun DayPlan() {
        MaterialYouBackground(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("План дня", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Caption("Среда, 17 марта: пары, домашка и свободное время")
                DayPlannerSection(
                    weeks = listOf(week),
                    marks = listOf(
                        LksMark("hw", "География", monday, "10:20", "Практические занятия", 5, false, "Климат России", "Контурная карта, §12"),
                    ),
                    plans = listOf(deadline),
                    now = wednesday.atTime(7, 30),
                    fixedDate = wednesday,
                )
            }
        }
    }

    @Composable
    private fun App(state: ScheduleUiState) {
        LecturialApp(state = state, onGroupSelected = {}, onDateSelected = {}, onMoveWeek = {}, onToday = {}, onRefresh = {}, onRetry = {})
    }

    private fun studySnapshot(): LksSnapshot {
        val today = LocalDate.now()
        fun mark(id: String, discipline: String, daysAgo: Long, grade: Int?, absent: Boolean?, homework: String? = null) =
            LksMark(id, discipline, today.minusDays(daysAgo), "10:20", "Практические занятия", grade, absent, null, homework)
        return LksSnapshot(
            marks = listOf(
                mark("a1", "Математический анализ", 20, 5, false),
                mark("a2", "Математический анализ", 13, 4, false),
                mark("a3", "Математический анализ", 6, 5, false, "№ 412–418"),
                mark("b1", "Физика", 18, 4, false),
                mark("b2", "Физика", 11, null, true),
                mark("b3", "Физика", 4, 5, false, "Подготовить отчёт по лабораторной"),
                mark("c1", "Иностранный язык", 15, 5, false),
                mark("c2", "Иностранный язык", 8, 5, false),
                mark("d1", "География", 9, 3, false),
                mark("d2", "География", 2, 4, false, "Контурная карта, §12"),
            ),
            loadedAtMillis = System.currentTimeMillis(),
        )
    }

    private companion object {
        /** 20 frames a second. */
        const val FRAME_MILLIS = 50L
        const val CLIP_WIDTH = 480
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { LecturialTheme { content() } }
        compose.waitForIdle()
        val folder = File(context.cacheDir, "readme").apply { mkdirs() }
        save(File(folder, "$name.png"), 540)
    }
}
