package com.rassvet.lecturial.ui

import androidx.compose.material.icons.rounded.Place
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.SlotAvailability
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import com.rassvet.lecturial.model.LoadLevel
import com.rassvet.lecturial.model.pairsWord
import com.rassvet.lecturial.model.surnameWithInitials
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rassvet.lecturial.LecturialApplication
import com.rassvet.lecturial.model.AnalyticsPeriod
import com.rassvet.lecturial.model.OccupancyStats
import com.rassvet.lecturial.model.ScheduleTarget
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val ru = Locale.forLanguageTag("ru")
private val dayFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", ru)
private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", ru)

@Composable
internal fun AnalyticsScreen(onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as LecturialApplication
    val viewModel: AnalyticsViewModel = viewModel(
        factory = AnalyticsViewModel.Factory(app.analyticsRepository, app.preferences),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            onBack?.let { BackIconButton(it) }
            Text("Статистика", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        ConnectedChoice(
            options = AnalyticsTab.entries,
            selected = state.tab,
            title = { it.title },
            onSelected = viewModel::setTab,
        )
        // Keep the last progress so the card can animate out with its text.
        val lastProgress = remember { mutableStateOf<AnalyticsProgress?>(null) }
        state.progress?.let { lastProgress.value = it }
        AnimatedVisibility(
            visible = state.progress != null,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            lastProgress.value?.let { progress -> ProgressCard(progress, onCancel = viewModel::cancel) }
        }
        AnimatedVisibility(
            visible = state.error != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
        }
        val slide = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
        val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            targetState = state.tab,
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(slide) { direction * it / 4 } + fadeIn(fade)) togetherWith
                    (slideOutHorizontally(slide) { -direction * it / 4 } + fadeOut(fade)) using
                    // Animating the size of a long scrolling column re-measures it every frame.
                    null
            },
            label = "analytics-tab",
        ) { tab ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (tab) {
                    AnalyticsTab.FreeTime -> FreeTimeTab(state, viewModel)
                    AnalyticsTab.Load -> LoadTab(state, viewModel)
                    AnalyticsTab.Rooms -> RoomsTab(state, viewModel)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ------------------------------------------------------------------------------- free time

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FreeTimeTab(state: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    TabIntro(
        "Подбор пары, когда свободны все выбранные преподаватели и группы, — например, для встречи, консультации или пересдачи.",
        listOf(
            "Добавьте участников: преподавателей по фамилии или группы по названию. Группы специалитета (ИОП-ИТ) вашего курса добавляются сразу; фильтр «Только специалитет» оставляет в поиске только их.",
            "Сравниваются пары с понедельника по субботу: с 1 пары (08:45) по 7 пару (18:15).",
            "Пара занята, если у кого-то из участников в это время есть любое занятие, даже не совпадающее с сеткой пар.",
            "«Лучшие варианты» — сначала пары, когда свободны все: в будни и в середине дня (2–5 пары). Если таких нет — пары с наименьшим числом занятых и их список.",
            "«Все общие окна» — все свободные для всех пары по дням. Прошедшие пары не показываются.",
        ),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.participants.forEach { participant ->
            InputChip(
                selected = true,
                onClick = { viewModel.removeParticipant(participant.key) },
                label = { Text(participant.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = {
                    Icon(Icons.Rounded.Close, contentDescription = "Убрать ${participant.label}", Modifier.size(InputChipDefaults.IconSize))
                },
            )
        }
    }
    ParticipantPicker(state, viewModel, actionLabel = "Добавить", onPick = viewModel::addParticipant)
    ConnectedChoice(FreeTimeRange.entries, state.freeRange, { it.title }, viewModel::setFreeRange)
    Button(
        onClick = viewModel::findFreeTime,
        enabled = state.participants.isNotEmpty() && !state.isBusy,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Найти общее время") }

    val result = state.freeResult ?: return
    key(result) { FreeTimeResults(result, state, viewModel) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FreeTimeResults(result: FreeTimeResult, state: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    SectionTitle("Лучшие варианты", Modifier.revealOnAppear(0))
    if (result.recommended.isEmpty()) {
        Text("В выбранном периоде не осталось пар.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    result.recommended.forEachIndexed { index, slot ->
        SlotCard(
            slot = slot,
            schedules = result.schedules,
            expanded = state.expandedSlot == slot.key,
            suggestion = state.roomSuggestions[slot.key],
            shape = settingsGroupShape(index, result.recommended.size),
            modifier = Modifier.revealOnAppear(index + 1),
            onClick = { viewModel.toggleSlot(slot) },
        )
    }
    SectionTitle("Все общие окна", Modifier.revealOnAppear(result.recommended.size + 1))
    if (result.freeByDay.isEmpty()) {
        Text("Нет пары, когда свободны все. Посмотрите варианты выше.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    result.freeByDay.entries.forEachIndexed { index, (date, slots) ->
        Column(
            modifier = Modifier.revealOnAppear(result.recommended.size + 2 + index),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(date.format(dayFormatter).replaceFirstChar { it.titlecase(ru) }, style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                slots.forEach { slot ->
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            "${slot.number} · ${slot.label.substringAfter("· ")}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------- load

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoadTab(state: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    TabIntro(
        "Сводка для куратора: сколько пар у каждого преподавателя ваших групп и у самих групп за выбранный период.",
        listOf(
            "Курс — группы, которые вы курируете. Сначала подставляются группы, у которых вы сами ведёте пары; кнопка «Изменить» добавляет и убирает группы, а также преподавателей вручную.",
            "Преподаватели находятся автоматически: берутся все, кто ведёт пары у групп курса, и загружается их полное расписание.",
            "Пары и часы — все занятия преподавателя за период; одна пара — два академических часа. «В курсе» — сколько из них у ваших групп.",
            "«≈ в неделю» — среднее по неделям, в которых есть занятия. Нагрузка высокая — от 10 пар в неделю, средняя — от 4, низкая — меньше 4.",
            "Нажмите на карточку, чтобы увидеть нагрузку по неделям, дням недели и типам занятий.",
            "Расписание сохраняется в телефоне: повторный расчёт быстрее, прошедшие недели заново не загружаются.",
        ),
    )
    ResultCard(shape = RoundedCornerShape(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Курс", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { viewModel.setEditingCourse(!state.editingCourse) }) {
                Text(if (state.editingCourse) "Готово" else "Изменить")
            }
        }
        if (state.courseGroups.isEmpty() && state.extraTeachers.isEmpty()) {
            Text(
                "Добавьте группы курса — преподаватели подтянутся сами",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            (state.courseGroups.map { AnalyticsParticipant(it, it) } + state.extraTeachers).forEach { member ->
                InputChip(
                    selected = true,
                    onClick = { if (state.editingCourse) viewModel.removeFromCourse(member.key) },
                    label = { Text(surnameWithInitials(member.label).takeIf { ScheduleTarget.isTeacher(member.key) } ?: member.label) },
                    trailingIcon = if (state.editingCourse) {
                        { Icon(Icons.Rounded.Close, contentDescription = "Убрать ${member.label}", Modifier.size(InputChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
    if (state.editingCourse) {
        ParticipantPicker(state, viewModel, actionLabel = "Добавить", onPick = viewModel::addToCourse)
    }
    ConnectedChoice(AnalyticsPeriod.entries, state.loadPeriod, { it.title }, viewModel::setLoadPeriod)
    Button(
        onClick = viewModel::computeCourse,
        enabled = (state.courseGroups.isNotEmpty() || state.extraTeachers.isNotEmpty()) && !state.isBusy,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text(if (state.courseResult == null) "Показать нагрузку" else "Обновить") }

    val result = state.courseResult ?: return
    key(result) { CourseResults(state, viewModel, result) }
    state.detail?.let { row -> LoadDetailSheet(row, result.range, onDismiss = { viewModel.openDetail(null) }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseResults(state: AnalyticsUiState, viewModel: AnalyticsViewModel, result: CourseLoadResult) {
    SectionTitle(rangeText(result.range), Modifier.revealOnAppear(0))
    val totalPairs = result.teachers.sumOf { it.stats.pairs }
    val coursePairs = result.teachers.sumOf { it.coursePairs ?: 0 }
    val average = if (result.teachers.isEmpty()) 0.0 else result.teachers.sumOf { it.pairsPerWeek } / result.teachers.size
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
        SummaryTile("${result.teachers.size}", "преподавателей", Modifier.weight(1f).revealOnAppear(1))
        SummaryTile("$coursePairs", "${pairsWord(coursePairs)} в курсе", Modifier.weight(1f).revealOnAppear(2))
        SummaryTile(oneDecimal(average), "пар в неделю\nв среднем", Modifier.weight(1f).revealOnAppear(3))
    }
    if (totalPairs > coursePairs) {
        Text(
            "Всего у этих преподавателей $totalPairs ${pairsWord(totalPairs)}, включая другие группы.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Преподаватели", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
    ConnectedChoice(LoadSort.entries, state.loadSort, { it.title }, viewModel::setLoadSort)
    val teachers = state.sorted(result.teachers)
    val maxPairs = teachers.maxOfOrNull { it.stats.pairs }?.coerceAtLeast(1) ?: 1
    teachers.forEachIndexed { index, row ->
        LoadRowCard(row, maxPairs, settingsGroupShape(index, teachers.size), Modifier.revealOnAppear(index + 4)) { viewModel.openDetail(row) }
    }
    if (result.unresolved.isNotEmpty()) {
        Text(
            "Не нашли на сайте: ${result.unresolved.joinToString()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (result.groups.isNotEmpty()) {
        SectionTitle("Группы курса")
        val groups = state.sorted(result.groups)
        val maxGroup = groups.maxOfOrNull { it.stats.pairs }?.coerceAtLeast(1) ?: 1
        groups.forEachIndexed { index, row ->
            LoadRowCard(row, maxGroup, settingsGroupShape(index, groups.size), Modifier.revealOnAppear(teachers.size + index + 5)) { viewModel.openDetail(row) }
        }
    }
}

@Composable
private fun SummaryTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One teacher or group: name and level, pairs and hours, a bar against the busiest. */
@Composable
private fun LoadRowCard(
    row: LoadRow,
    maxPairs: Int,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val stats = row.stats
    val share = growingFraction(stats.pairs.toFloat() / maxPairs)
    Surface(onClick = onClick, shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.shortName,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LevelChip(row.level)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${stats.pairs}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    " ${pairsWord(stats.pairs)} · ${stats.academicHours} ч",
                    modifier = Modifier.padding(bottom = 3.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            LinearProgressIndicator(
                progress = share,
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = levelColor(row.level),
                drawStopIndicator = {},
            )
            Text(
                buildList {
                    add("≈ ${oneDecimal(row.pairsPerWeek)} в неделю")
                    add("${stats.activeDays} ${daysWord(stats.activeDays)}")
                    row.coursePairs?.let { add("в курсе $it") }
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LevelChip(level: LoadLevel) {
    val (container, content) = when (level) {
        LoadLevel.High -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        LoadLevel.Medium -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        LoadLevel.Low -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            level.title,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = content,
        )
    }
}

@Composable
private fun levelColor(level: LoadLevel) = when (level) {
    LoadLevel.High -> MaterialTheme.colorScheme.error
    LoadLevel.Medium -> MaterialTheme.colorScheme.primary
    LoadLevel.Low -> MaterialTheme.colorScheme.outline
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadDetailSheet(row: LoadRow, range: ClosedRange<LocalDate>, onDismiss: () -> Unit) {
    val stats = row.stats
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(row.participant.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "${rangeText(range)} · нагрузка ${row.level.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatGrid(stats)
            row.coursePairs?.let { course ->
                Text(
                    "В группах курса: $course ${pairsWord(course)} из ${stats.pairs}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (row.weekly.size > 1) {
                SectionTitle("По неделям")
                val maxWeek = row.weekly.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                row.weekly.forEach { (week, count) ->
                    BarRow(week.format(shortDateFormatter), count.toFloat() / maxWeek, "$count")
                }
            }
            SectionTitle("По дням недели")
            val maxDay = stats.pairsByWeekday.values.maxOrNull()?.coerceAtLeast(1) ?: 1
            stats.pairsByWeekday.forEach { (day, count) ->
                BarRow(day.getDisplayName(TextStyle.SHORT, ru).replaceFirstChar { it.titlecase(ru) }, count.toFloat() / maxDay, "$count")
            }
            if (stats.pairsByType.isNotEmpty()) {
                SectionTitle("По типам занятий")
                stats.pairsByType.forEach { (type, count) ->
                    BarRow(type, count.toFloat() / stats.pairs.coerceAtLeast(1), "$count")
                }
            }
        }
    }
}

private fun oneDecimal(value: Double): String = String.format(ru, "%.1f", value)

private fun daysWord(count: Int): String {
    val n100 = count % 100
    val n10 = count % 10
    return when {
        n100 in 11..14 -> "учебных дней"
        n10 == 1 -> "учебный день"
        n10 in 2..4 -> "учебных дня"
        else -> "учебных дней"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatGrid(stats: OccupancyStats) {
    val cells = listOf(
        "${stats.pairs}" to pairsWord(stats.pairs),
        "${stats.academicHours}" to "ак. часов",
        "${stats.activeDays} из ${stats.studyDays}" to "учебных дней",
        oneDecimal(stats.averagePerActiveDay) to "пар в учебный день",
        "${stats.maxPairsInDay}" to (stats.busiestDay?.let { "максимум за день · ${it.format(shortDateFormatter)}" } ?: "максимум за день"),
        "${stats.windows}" to "окон между парами",
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
        cells.forEach { (value, label) -> SummaryTile(value, label, Modifier.weight(1f)) }
    }
}

// ------------------------------------------------------------------------------- rooms

@Composable
private fun RoomsTab(state: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    TabIntro(
        "Насколько заняты аудитории университета: где почти всегда идут занятия, а где обычно свободно.",
        listOf(
            "Приложение находит все аудитории на сайте расписания и загружает их занятия за период: неделя — около минуты, семестр — 10–20 минут. Данные сохраняются.",
            "Процент — доля пар (1–7 пары, пн–сб), в которые в аудитории идёт занятие.",
            "«Самые свободные» — среди аудиторий, где за период было хотя бы одно занятие; аудитории совсем без занятий посчитаны отдельно.",
            "«Загрузка в течение дня» — какая доля используемых аудиторий занята в каждую пару: видно самые загруженные часы.",
        ),
    )
    ConnectedChoice(AnalyticsPeriod.entries, state.roomPeriod, { it.title }, viewModel::setRoomPeriod)
    Button(
        onClick = viewModel::computeRooms,
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Собрать статистику") }

    val result = state.roomsResult ?: return
    key(result) { RoomsResults(result) }
}

@Composable
private fun RoomsResults(result: RoomsResult) {
    var order = 0
    val used = result.loads.filter { it.busySlots > 0 }
    SectionTitle("${rangeText(result.range)} · ${result.loads.size} аудиторий", Modifier.revealOnAppear(order++))
    SectionTitle("Самые загруженные", Modifier.revealOnAppear(order++))
    used.take(TOP).forEach { BarRow(it.room, it.utilization.toFloat(), percent(it.utilization), Modifier.revealOnAppear(order++)) }
    SectionTitle("Самые свободные", Modifier.revealOnAppear(order++))
    used.takeLast(TOP).reversed().forEach {
        BarRow(it.room, it.utilization.toFloat(), percent(it.utilization), Modifier.revealOnAppear(order++))
    }
    val empty = result.loads.size - used.size
    if (empty > 0) {
        Text("Без занятий за период: $empty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SectionTitle("Загрузка в течение дня", Modifier.revealOnAppear(order++))
    Text(
        "Доля используемых аудиторий, занятых в каждую пару",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    result.intraday.forEach { (slot, share) ->
        BarRow("${slot.number} · ${slot.start}", share.toFloat(), percent(share), Modifier.revealOnAppear(order++))
    }
}

// ------------------------------------------------------------------------------- shared

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ParticipantPicker(
    state: AnalyticsUiState,
    viewModel: AnalyticsViewModel,
    actionLabel: String,
    onPick: (AnalyticsParticipant) -> Unit,
) {
    val teacherSearch = state.searchMode == ScheduleSearchMode.Teacher
    val focusManager = LocalFocusManager.current
    ConnectedChoice(
        options = ScheduleSearchMode.entries,
        selected = state.searchMode,
        title = { if (it == ScheduleSearchMode.Teacher) "Преподаватели" else "Группы" },
        onSelected = viewModel::setSearchMode,
    )
    OutlinedTextField(
        value = state.query,
        onValueChange = { viewModel.search(it) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(if (teacherSearch) "Фамилия преподавателя" else "Название группы") },
        singleLine = true,
    )
    if (!teacherSearch) {
        FilterChip(
            selected = state.specialistOnly,
            onClick = { viewModel.setSpecialistOnly(!state.specialistOnly) },
            label = { Text("Только специалитет") },
        )
    }
    when {
        state.isSearching -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        state.searchError != null -> Text(state.searchError, color = MaterialTheme.colorScheme.error)
    }
    val options: List<AnalyticsParticipant> = if (teacherSearch) {
        state.teacherResults.map { AnalyticsParticipant(ScheduleTarget.teacherKey(it.id), it.name) }
    } else {
        state.visibleGroups.map { AnalyticsParticipant(it, it) }
    }
    options.take(MAX_RESULTS).forEachIndexed { index, option ->
        Surface(
            onClick = {
                focusManager.clearFocus()
                onPick(option)
            },
            shape = settingsGroupShape(index, options.take(MAX_RESULTS).size),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(option.label, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(actionLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ConnectedChoice(
    options: List<T>,
    selected: T,
    title: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelected(option) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) { Text(title(option), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProgressCard(progress: AnalyticsProgress, onCancel: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (progress.total > 0) "${progress.text}: ${progress.done} из ${progress.total}" else progress.text,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onCancel) { Text("Отменить") }
            }
            if (progress.total > 0) {
                LinearWavyProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ResultCard(shape: RoundedCornerShape, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun BarRow(label: String, fraction: Float, value: String, modifier: Modifier = Modifier) {
    val grown = growingFraction(fraction.coerceIn(0f, 1f))
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, Modifier.width(120.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = grown,
            modifier = Modifier.weight(1f).height(10.dp),
            drawStopIndicator = {},
        )
        Text(value, Modifier.width(48.dp), style = MaterialTheme.typography.labelLarge)
    }
}

private fun percent(value: Double): String = "${(value * 100).roundToInt()} %"

private fun rangeText(range: ClosedRange<LocalDate>): String =
    "${range.start.format(shortDateFormatter)} — ${range.endInclusive.format(shortDateFormatter)}"

private const val TOP = 7
private const val MAX_RESULTS = 8

/**
 * Fades and lifts an item in when it first appears; [order] staggers neighbours so a list
 * of results builds up top to bottom instead of popping in at once.
 */
@Composable
private fun Modifier.revealOnAppear(order: Int): Modifier {
    val progress = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        delay(order.coerceAtMost(MAX_STAGGERED) * STAGGER_MILLIS)
        progress.animateTo(1f, spec)
    }
    return graphicsLayer {
        val value = progress.value
        alpha = value.coerceIn(0f, 1f)
        translationY = (1f - value) * 28.dp.toPx()
        val scale = 0.96f + 0.04f * value
        scaleX = scale
        scaleY = scale
    }
}

/** A bar value that grows from zero the first time it is shown and follows later changes. */
@Composable
private fun growingFraction(target: Float): () -> Float {
    val animated = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(target) { animated.animateTo(target, spec) }
    // Returned as a lambda so the progress bar reads it while drawing: no recomposition per frame.
    return remember(animated) { { animated.value.coerceIn(0f, 1f) } }
}

private const val STAGGER_MILLIS = 45L
private const val MAX_STAGGERED = 14

/** What a tab is for, plus an expandable "how it's counted" explanation. */
@Composable
private fun TabIntro(summary: String, points: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "how-arrow")
    Text(summary, style = MaterialTheme.typography.bodyLarge)
    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Как это считается", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Свернуть" else "Развернуть",
                    modifier = Modifier.graphicsLayer { rotationZ = rotation },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    points.forEach { point ->
                        Row {
                            Text("•", Modifier.width(16.dp), color = MaterialTheme.colorScheme.primary)
                            Text(point, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** A recommended meeting slot; tapping it shows everyone's day and a free room. */
@Composable
private fun SlotCard(
    slot: SlotAvailability,
    schedules: Map<String, List<ScheduleEvent>>,
    expanded: Boolean,
    suggestion: RoomSuggestion?,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "slot-arrow")
    Surface(onClick = onClick, shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(slot.date.format(dayFormatter).replaceFirstChar { it.titlecase(ru) }, style = MaterialTheme.typography.titleMedium)
                    Text(slot.slot.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (slot.isFreeForAll) "Свободны все" else "Заняты: ${slot.busy.joinToString()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (slot.isFreeForAll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Свернуть" else "Расписание и аудитория",
                    modifier = Modifier.graphicsLayer { rotationZ = rotation },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoomSuggestionRow(suggestion)
                    schedules.forEach { (label, lessons) ->
                        DaySchedule(label, lessons.filter { it.date == slot.date }, slot)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RoomSuggestionRow(suggestion: RoomSuggestion?) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Место", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                when {
                    suggestion == null || suggestion.isLoading -> Text(
                        "Ищем свободную аудиторию…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    suggestion.room != null -> {
                        Text(suggestion.room, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        suggestion.note?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    else -> Text(
                        suggestion.note.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            if (suggestion == null || suggestion.isLoading) LoadingIndicator(Modifier.size(32.dp))
        }
    }
}

/** One participant's lessons on the slot's day; a lesson clashing with the slot is marked. */
@Composable
private fun DaySchedule(label: String, lessons: List<ScheduleEvent>, slot: SlotAvailability) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (lessons.isEmpty()) {
            Text("Занятий нет", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        lessons.sortedBy { it.startTime }.distinctBy { Triple(it.startTime, it.endTime, it.discipline) }.forEach { lesson ->
            val clash = com.rassvet.lecturial.model.isBusy(listOf(lesson), slot.date, slot.slot)
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "${lesson.startTime}–${lesson.endTime}",
                    Modifier.width(96.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (clash) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    listOfNotNull(lesson.discipline, lesson.classroom).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (clash) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
