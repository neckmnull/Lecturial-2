package com.rassvet.lecturial.ui

import androidx.compose.runtime.key
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import com.rassvet.lecturial.model.LoadLevel
import com.rassvet.lecturial.model.pairsWord
import com.rassvet.lecturial.model.surnameWithInitials
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rassvet.lecturial.model.AnalyticsPeriod
import com.rassvet.lecturial.model.OccupancyStats
import com.rassvet.lecturial.model.ScheduleTarget
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val ru = Locale.forLanguageTag("ru")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM", ru)
// ------------------------------------------------------------------------------- load

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LoadTab(state: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    TabIntro(
        "Сводка для преподавателя: сколько пар у каждого преподавателя ваших групп и у самих групп за выбранный период.",
        listOf(
            "Курс — группы, с которыми вы работаете. Сначала подставляются группы, у которых вы сами ведёте пары; кнопка «Изменить» добавляет и убирает группы, а также преподавателей вручную.",
            "Нажатие на группу или преподавателя курса выключает их из расчёта, не убирая из курса; выбор запоминается.",
            "Преподаватели находятся автоматически: берутся все, кто ведёт пары у групп курса, и загружается их полное расписание.",
            "Пары и часы — все занятия преподавателя за период; одна пара — два академических часа. «В курсе» — сколько из них у ваших групп.",
            "«≈ в неделю» — среднее по неделям, в которых есть занятия. Нагрузка высокая — от 10 пар в неделю, средняя — от 4, низкая — меньше 4.",
            "Нажмите на карточку, чтобы увидеть нагрузку по группам курса (и другим группам), дисциплинам, времени начала пар, неделям, дням недели и типам занятий.",
            "Расписание сохраняется в телефоне: повторный расчёт быстрее, прошедшие недели заново не загружаются.",
        ),
    )
    ResultCard(shape = MaterialTheme.shapes.extraLarge) {
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
                ToggleableChip(
                    label = surnameWithInitials(member.label).takeIf { ScheduleTarget.isTeacher(member.key) } ?: member.label,
                    fullLabel = member.label,
                    included = member.key !in state.excludedCourse,
                    onToggle = { viewModel.toggleCourseMember(member.key) },
                    onRemove = if (state.editingCourse) {
                        { viewModel.removeFromCourse(member.key) }
                    } else {
                        null
                    },
                )
            }
        }
        if (state.courseGroups.isNotEmpty() || state.extraTeachers.isNotEmpty()) ToggleHint()
    }
    if (state.editingCourse) {
        ParticipantPicker(
            state,
            viewModel,
            added = state.courseGroups.toHashSet() + state.extraTeachers.map { it.key },
            onPick = viewModel::addToCourse,
        )
    }
    ConnectedChoice(AnalyticsPeriod.entries, state.loadPeriod, { it.title }, viewModel::setLoadPeriod)
    Button(
        onClick = viewModel::computeCourse,
        enabled = (state.activeCourseGroups.isNotEmpty() || state.activeExtraTeachers.isNotEmpty()) && !state.isBusy,
        modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
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
    ConnectedChoice(LoadSort.entries, state.loadSort, { sortTitle(it, byName = "А–Я") }, viewModel::setLoadSort)
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
    Surface(modifier = modifier, shape = MaterialTheme.shapes.largeIncreased, color = MaterialTheme.colorScheme.surfaceContainer) {
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
                .padding(horizontal = 16.dp)
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
            val breakdown = row.breakdown
            val total = stats.pairs.coerceAtLeast(1)
            if (row.isTeacher && (breakdown.byCourseGroup.isNotEmpty() || breakdown.outsideCourse > 0)) {
                SectionTitle("По группам")
                breakdown.byCourseGroup.forEach { (group, count) ->
                    BarRow(group, count.toFloat() / total, "$count")
                }
                if (breakdown.outsideCourse > 0) {
                    BarRow("Другие группы", breakdown.outsideCourse.toFloat() / total, "${breakdown.outsideCourse}")
                }
                Caption("Лекция для нескольких групп курса считается у каждой из них.")
            }
            if (breakdown.byDiscipline.isNotEmpty()) {
                SectionTitle("По дисциплинам")
                val maxDiscipline = breakdown.byDiscipline.maxOf { it.pairs }.coerceAtLeast(1)
                breakdown.byDiscipline.forEach { discipline ->
                    NamedBarRow(
                        label = discipline.discipline,
                        fraction = discipline.pairs.toFloat() / maxDiscipline,
                        value = "${discipline.pairs}",
                        caption = when {
                            !row.isTeacher -> null
                            discipline.coursePairs == discipline.pairs -> "все в группах курса"
                            discipline.coursePairs == 0 -> "вне групп курса"
                            else -> "${discipline.coursePairs} из ${discipline.pairs} в группах курса"
                        },
                    )
                }
            }
            if (stats.pairs > 0) {
                SectionTitle("По времени начала")
                val maxSlot = breakdown.bySlot.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                breakdown.bySlot.forEach { (slot, count) ->
                    BarRow("${slot.number} пара · ${slot.start.format(timeFormatter)}", count.toFloat() / maxSlot, "$count")
                }
                if (breakdown.offGrid > 0) {
                    BarRow("Другое время", breakdown.offGrid.toFloat() / maxSlot, "${breakdown.offGrid}")
                }
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
                    NamedBarRow(type, count.toFloat() / stats.pairs.coerceAtLeast(1), "$count")
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
