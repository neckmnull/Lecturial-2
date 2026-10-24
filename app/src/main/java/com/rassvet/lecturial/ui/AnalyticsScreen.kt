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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import com.rassvet.lecturial.model.RoomCategories
import com.rassvet.lecturial.model.RoomKind
import com.rassvet.lecturial.model.intradayRoomLoad
import com.rassvet.lecturial.model.naturalRoomKey
import com.rassvet.lecturial.model.RoomInfo
import com.rassvet.lecturial.model.SiriusPairSlots
import com.rassvet.lecturial.model.displayEventType
import com.rassvet.lecturial.model.displayAddress
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Shape
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
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
            .padding(horizontal = 16.dp, vertical = 24.dp),
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
        "Подбор пары, когда свободны все выбранные преподаватели и группы, — например, для встречи, консультации или пересдачи. «По парам» показывает, что у них идёт в выбранные пары дня.",
        listOf(
            "Добавьте участников: преподавателей по фамилии или группы по названию. Поиск после добавления не закрывается — можно добавить несколько подряд или все найденные сразу. Группы ИТ-специалитета (ИОП-ИТ) вашего курса добавляются сразу; фильтр «ИТ-специалитет» оставляет в поиске только их.",
            "Нажатие на участника в списке временно исключает его из подбора — он остаётся в списке, повторное нажатие возвращает. Крестик убирает участника совсем.",
            "Сравниваются пары с понедельника по субботу: с 1 пары (08:45) по 7 пару (18:15).",
            "Пара занята, если у кого-то из участников в это время есть любое занятие, даже не совпадающее с сеткой пар.",
            "«Лучшие варианты» — сначала пары, когда свободны все: в будни и в середине дня (2–5 пары). Если таких нет — пары с наименьшим числом занятых и их список.",
            "«Все общие окна» — все свободные для всех пары по дням. Прошедшие пары не показываются.",
            "«По парам» — выберите день и одну или несколько пар: для каждой пары видно, кто занят и кто свободен, а у занятых — предмет, тип занятия, время, аудитория с адресом, преподаватели (у преподавателя — группы), комментарий и ссылка на онлайн-занятие. Другие пары и дни той же недели после загрузки показываются сразу.",
        ),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.participants.forEach { participant ->
            ToggleableChip(
                label = participant.label,
                fullLabel = participant.label,
                included = participant.key !in state.excludedParticipants,
                onToggle = { viewModel.toggleParticipant(participant.key) },
                onRemove = { viewModel.removeParticipant(participant.key) },
            )
        }
    }
    if (state.participants.isNotEmpty()) ToggleHint()
    ParticipantPicker(
        state,
        viewModel,
        added = state.participants.mapTo(HashSet()) { it.key },
        onPick = viewModel::addParticipant,
    )
    ConnectedChoice(ParticipantsMode.entries, state.participantsMode, { it.title }, viewModel::setParticipantsMode)
    if (state.participantsMode == ParticipantsMode.Pairs) {
        PairsLookup(state, viewModel)
        return
    }
    ConnectedChoice(FreeTimeRange.entries, state.freeRange, { it.title }, viewModel::setFreeRange)
    Button(
        onClick = viewModel::findFreeTime,
        enabled = state.activeParticipants.isNotEmpty() && !state.isBusy,
        modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
    ) { Text("Найти общее время") }

    val result = state.freeResult ?: return
    key(result) { FreeTimeResults(result, state, viewModel) }
}

/** Pick a day and pairs, then see what each participant has in them. */
@Composable
private fun PairsLookup(state: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    DayChips(state.lookupDays, state.pairsDate, viewModel::setPairsDate)
    PairToggles(state.pairsSelected, viewModel::togglePairsSlot)
    Caption(
        SiriusPairSlots.filter { it.number in state.pairsSelected }
            .joinToString { "${it.number} пара ${it.start.format(timeFormatter)}–${it.end.format(timeFormatter)}" },
    )
    val result = state.pairsResult
    if (result == null) {
        Button(
            onClick = viewModel::showPairs,
            enabled = state.activeParticipants.isNotEmpty() && !state.isBusy,
            modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
        ) { Text("Показать пары") }
    } else {
        Text(
            result.date.format(dayFormatter).replaceFirstChar { it.titlecase(ru) },
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        result.pairs.forEach { pair ->
            SectionTitle(pair.slot.label)
            Caption(pairSummary(pair))
            pair.rows.forEachIndexed { index, row -> PairRowTile(row, settingsGroupShape(index, pair.rows.size)) }
        }
    }
}

/** Who is busy in a pair and who is free, in one line. */
private fun pairSummary(pair: PairLessons): String {
    val free = pair.rows.filter { it.lessons.isEmpty() }.map { it.participant.label }
    val busy = pair.rows.size - free.size
    return when {
        free.isEmpty() -> "Заняты все: ${pair.rows.size}"
        busy == 0 -> "Свободны все: ${pair.rows.size}"
        else -> "Заняты $busy из ${pair.rows.size} · свободны: ${free.joinToString()}"
    }
}

/** Pairs 1–7 as a connected group where several can be on at once. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PairToggles(selected: Set<Int>, onToggle: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        SiriusPairSlots.forEachIndexed { index, slot ->
            ToggleButton(
                checked = slot.number in selected,
                onCheckedChange = { onToggle(slot.number) },
                modifier = Modifier.weight(1f).semantics { contentDescription = "${slot.number} пара" },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    SiriusPairSlots.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) { Text("${slot.number}", maxLines = 1) }
        }
    }
}

/** One teacher or group in a pair: everything the schedule says about their lessons then. */
@Composable
private fun PairRowTile(row: PairRow, shape: RoundedCornerShape) {
    val forTeacher = ScheduleTarget.isTeacher(row.participant.key)
    val free = row.lessons.isEmpty()
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    row.participant.label,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (free) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = if (free) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Text(
                        if (free) "Свободно" else "Занятие",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            row.lessons.forEach { lesson -> PairLessonDetails(lesson, forTeacher) }
        }
    }
}

/** A lesson in full: subject, type and time, room and address, people, comment, online link. */
@Composable
private fun PairLessonDetails(lesson: ScheduleEvent, forTeacher: Boolean) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(lesson.discipline, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(
            listOfNotNull(displayEventType(lesson.type).ifBlank { null }, "${lesson.startTime}–${lesson.endTime}").joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        val place = listOfNotNull(lesson.classroom?.trim()?.ifBlank { null }, displayAddress(lesson.address)).joinToString(" · ")
        if (place.isNotEmpty()) DetailLine(Icons.Rounded.Place, place)
        // A group sees who teaches; a teacher sees which groups.
        val people = if (forTeacher) lesson.studentGroup?.trim().orEmpty() else lesson.teachers.joinToString()
        if (people.isNotEmpty()) DetailLine(Icons.Rounded.Person, people)
        lesson.comment?.trim()?.takeIf { it.isNotEmpty() }?.let { DetailLine(Icons.Rounded.Info, it) }
        lesson.onlineUrl?.takeIf { it.isNotBlank() }?.let { url ->
            TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text("Открыть онлайн-занятие") }
        }
    }
}

@Composable
private fun DetailLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Study days to choose from: today, tomorrow and the rest of the coming week. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayChips(days: List<LocalDate>, selected: LocalDate, onSelected: (LocalDate) -> Unit) {
    val today = days.firstOrNull()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { date ->
            FilterChip(
                selected = date == selected,
                onClick = { onSelected(date) },
                label = { Text(dayChipLabel(date, today)) },
            )
        }
    }
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

/**
 * A listed teacher or group. A tap switches it off for the calculation and back on, so
 * someone can be left out for a moment without searching for them again; the cross
 * takes it out of the list.
 */
@Composable
private fun ToggleableChip(
    label: String,
    fullLabel: String,
    included: Boolean,
    onToggle: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    InputChip(
        selected = included,
        onClick = onToggle,
        label = {
            Text(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (included) null else TextDecoration.LineThrough,
            )
        },
        modifier = Modifier.semantics { stateDescription = if (included) "учитывается" else "не учитывается" },
        leadingIcon = if (included) {
            { Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(InputChipDefaults.IconSize)) }
        } else {
            null
        },
        trailingIcon = onRemove?.let { remove ->
            {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(onClick = remove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Убрать $fullLabel", Modifier.size(InputChipDefaults.IconSize))
                }
            }
        },
    )
}

@Composable
private fun ToggleHint() {
    Text(
        "Нажмите, чтобы временно не учитывать — участник останется в списке. Крестик убирает его совсем.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ------------------------------------------------------------------------------- load

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoadTab(state: AnalyticsUiState, viewModel: AnalyticsViewModel) {
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

// ------------------------------------------------------------------------------- rooms

@Composable
private fun RoomsTab(state: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    TabIntro(
        "Насколько заняты аудитории университета: где почти всегда идут занятия, а где обычно свободно.",
        listOf(
            "Приложение находит все аудитории на сайте расписания и загружает их занятия за период: неделя — около минуты, семестр — 10–20 минут. Данные сохраняются.",
            "Процент — доля пар (1–7 пары, пн–сб), в которые в аудитории идёт занятие.",
            "«Самые свободные» — среди аудиторий, где за период было хотя бы одно занятие; аудитории совсем без занятий посчитаны отдельно.",
            "Названия приведены к одному виду: «Альфа 4.1», «К_9, ауд. 1.26». Под названием — только то, что указано на сайте расписания: пометка «комп», примечания и код аудитории. Одна аудитория, записанная на сайте дважды, считается один раз.",
            "«Загрузка в течение дня» — какая доля используемых аудиторий занята в каждую пару: видно самые загруженные часы.",
            "«Свободные аудитории» — выберите день и пару: приложение загрузит неделю расписания всех аудиторий и покажет, где в это время нет занятий и до какого времени аудитория остаётся свободной. Другие пары той же недели после этого показываются сразу.",
        ),
    )
    FreeRoomsLookup(state, viewModel)
    SectionTitle("Загруженность за период")
    ConnectedChoice(AnalyticsPeriod.entries, state.roomPeriod, { it.title }, viewModel::setRoomPeriod)
    Button(
        onClick = viewModel::computeRooms,
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
    ) { Text("Собрать статистику") }

    val result = state.roomsResult ?: return
    key(result) { RoomsResults(result, state.roomSort, viewModel::setRoomSort) }
}

@Composable
private fun RoomsResults(result: RoomsResult, sort: LoadSort, onSort: (LoadSort) -> Unit) {
    // Meeting rooms, halls and polygons would skew the rankings, so only teaching rooms by default.
    var teachingOnly by rememberSaveable { mutableStateOf(true) }
    val shown = remember(result, teachingOnly) { result.rooms.filter { !teachingOnly || it.info.isTeaching } }
    val used = remember(shown) { shown.filter { it.load.busySlots > 0 } }
    val intraday = remember(used) { intradayRoomLoad(used.map { it.load }) }
    var order = 0
    SectionTitle("${rangeText(result.range)} · ${shown.size} ${roomsWord(shown.size)}", Modifier.revealOnAppear(order++))
    FilterChip(
        selected = teachingOnly,
        onClick = { teachingOnly = !teachingOnly },
        label = { Text("Только учебные аудитории") },
        leadingIcon = if (teachingOnly) {
            { Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
    Caption(
        if (teachingOnly) {
            "Аудитории корпусов и «К_» — без переговорных, залов и других помещений."
        } else {
            "Все помещения из расписания." + if (result.notRooms > 0) " Ссылки и «Дистанционно» не считаются: ${result.notRooms}." else ""
        },
    )
    SectionTitle("Самые загруженные", Modifier.revealOnAppear(order++))
    BarCard { used.take(TOP).forEach { RoomBar(it, Modifier.revealOnAppear(order++)) } }
    SectionTitle("Самые свободные", Modifier.revealOnAppear(order++))
    BarCard { used.takeLast(TOP).reversed().forEach { RoomBar(it, Modifier.revealOnAppear(order++)) } }
    val empty = shown.size - used.size
    if (empty > 0) {
        Text("Без занятий за период: $empty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SectionTitle("Все аудитории по корпусам", Modifier.revealOnAppear(order++))
    ConnectedChoice(LoadSort.entries, sort, { sortTitle(it, byName = "По номеру") }, onSort)
    // Buildings are ordered like the rooms inside them: by their share of busy pairs.
    val categories = remember(shown, sort) {
        val groups = shown.groupBy { it.info.category }
        fun share(rooms: List<RoomStat>) = rooms.sumOf { it.load.busySlots }.toDouble() / rooms.sumOf { it.load.totalSlots }.coerceAtLeast(1)
        val ordered = when (sort) {
            LoadSort.Busiest -> groups.entries.sortedByDescending { share(it.value) }
            LoadSort.Freest -> groups.entries.sortedBy { share(it.value) }
            LoadSort.ByName -> groups.entries.sortedBy { RoomCategories.order.indexOf(it.key) }
        }
        ordered.map { (category, rooms) -> category to sortedRooms(rooms, sort) }
    }
    categories.forEach { (category, rooms) -> RoomCategorySection(category, rooms, Modifier.revealOnAppear(order++)) }
    SectionTitle("Загрузка в течение дня", Modifier.revealOnAppear(order++))
    Caption("Доля используемых аудиторий, занятых в каждую пару")
    BarCard {
        intraday.forEach { (slot, share) ->
            BarRow("${slot.number} · ${slot.start}", share.toFloat(), percent(share), Modifier.revealOnAppear(order++))
        }
    }
}

/** Pick a day and a pair, then see which rooms have nothing on then. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FreeRoomsLookup(state: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    SectionTitle("Свободные аудитории")
    DayChips(state.lookupDays, state.freeRoomDate, viewModel::setFreeRoomDate)
    ConnectedChoice(SiriusPairSlots.map { it.number }, state.freeRoomPair, { "$it" }, viewModel::setFreeRoomPair)
    SiriusPairSlots.firstOrNull { it.number == state.freeRoomPair }?.let { Caption(it.label) }
    val result = state.freeRoomsResult
    if (result == null) {
        Button(
            onClick = viewModel::findFreeRooms,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
        ) { Text("Найти свободные аудитории") }
    } else {
        // No key(): unfolded buildings and the filter stay as they are while pairs are switched.
        FreeRoomsResults(result)
    }
}

@Composable
private fun FreeRoomsResults(result: FreeRoomsResult) {
    var teachingOnly by rememberSaveable { mutableStateOf(true) }
    val shown = remember(result, teachingOnly) { result.rooms.filter { !teachingOnly || it.info.isTeaching } }
    // Rooms without a single lesson all week may be closed or not for classes: listed apart.
    val (used, idle) = remember(shown) { shown.partition { it.usedThisWeek } }
    val categories = remember(used) {
        used.groupBy { it.info.category }.entries
            .sortedBy { RoomCategories.order.indexOf(it.key).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
            .map { (category, rooms) -> category to rooms.sortedBy { naturalRoomKey(it.info.name) } }
    }
    var order = 0
    Text(
        "Свободно ${used.size} ${roomsWord(used.size)}",
        Modifier.revealOnAppear(order++),
        style = MaterialTheme.typography.titleSmall,
    )
    FilterChip(
        selected = teachingOnly,
        onClick = { teachingOnly = !teachingOnly },
        label = { Text("Только учебные аудитории") },
        leadingIcon = if (teachingOnly) {
            { Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
    if (result.unchecked > 0) {
        Caption("Не удалось загрузить расписание ${result.unchecked} ${roomsWord(result.unchecked)} — их здесь нет.")
    }
    categories.forEach { (category, rooms) ->
        key(category) {
            FoldableRoomSection(category, rooms.size, Modifier.revealOnAppear(order++)) {
                rooms.forEach { FreeRoomRow(it) }
            }
        }
    }
    if (idle.isNotEmpty()) {
        FoldableRoomSection("Без занятий всю неделю", idle.size, Modifier.revealOnAppear(order++)) {
            idle.sortedBy { naturalRoomKey(it.info.name) }.forEach { FreeRoomRow(it) }
        }
    }
}

@Composable
private fun FreeRoomRow(room: FreeRoomStat) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(room.info.name, style = MaterialTheme.typography.bodyLarge)
            roomCaption(room.info)?.let { Caption(it) }
        }
        Text(
            room.freeUntil?.let { "до ${it.format(timeFormatter)}" } ?: "до конца дня",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private val weekdayFormatter = DateTimeFormatter.ofPattern("EE, d MMM", ru)

private fun dayChipLabel(date: LocalDate, today: LocalDate?): String = when (date) {
    today -> "Сегодня"
    today?.plusDays(1) -> "Завтра"
    else -> date.format(weekdayFormatter)
}

/**
 * What the schedule site itself says about a room — its "(комп)" mark, its notes and its
 * code — nothing guessed from the name.
 */
private fun roomCaption(info: RoomInfo): String? = listOfNotNull(
    "компьютерный класс".takeIf { info.kind == RoomKind.Computer },
    info.note,
    info.code?.let { "код $it" },
).joinToString(" · ").ifEmpty { null }

/** A room's load under its tidy name, with [roomCaption] below it. */
@Composable
private fun RoomBar(stat: RoomStat, modifier: Modifier = Modifier) {
    NamedBarRow(
        label = stat.info.name,
        fraction = stat.load.utilization.toFloat(),
        value = percent(stat.load.utilization),
        caption = roomCaption(stat.info),
        modifier = modifier,
    )
}

/** One building or group of rooms, folded until tapped. */
@Composable
private fun RoomCategorySection(title: String, rooms: List<RoomStat>, modifier: Modifier = Modifier) {
    FoldableRoomSection(title, rooms.size, modifier) {
        rooms.forEach { RoomBar(it) }
    }
}

/** A titled card with a count that unfolds its rooms when tapped. */
@Composable
private fun FoldableRoomSection(title: String, count: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "room-category")
    Surface(
        onClick = { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text("$count", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.rotate(arrow))
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    content()
                }
            }
        }
    }
}

private fun sortTitle(sort: LoadSort, byName: String): String = when (sort) {
    LoadSort.Busiest -> "Загруженные"
    LoadSort.Freest -> "Свободные"
    LoadSort.ByName -> byName
}

private fun sortedRooms(rooms: List<RoomStat>, sort: LoadSort): List<RoomStat> = when (sort) {
    LoadSort.Busiest -> rooms.sortedWith(compareByDescending<RoomStat> { it.load.utilization }.thenBy { naturalRoomKey(it.info.name) })
    LoadSort.Freest -> rooms.sortedWith(compareBy<RoomStat> { it.load.utilization }.thenBy { naturalRoomKey(it.info.name) })
    LoadSort.ByName -> rooms.sortedBy { naturalRoomKey(it.info.name) }
}

private fun roomsWord(count: Int): String {
    val n100 = count % 100
    val n10 = count % 10
    return when {
        n100 in 11..14 -> "аудиторий"
        n10 == 1 -> "аудитория"
        n10 in 2..4 -> "аудитории"
        else -> "аудиторий"
    }
}

// ------------------------------------------------------------------------------- shared

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ParticipantPicker(
    state: AnalyticsUiState,
    viewModel: AnalyticsViewModel,
    /** Keys already in the list: shown as added rather than offered again. */
    added: Set<String>,
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
            label = { Text("ИТ-специалитет") },
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
    val shown = options.take(MAX_RESULTS)
    val notAdded = shown.filterNot { it.key in added }
    if (notAdded.size > 1) {
        FilledTonalButton(
            onClick = {
                focusManager.clearFocus()
                notAdded.forEach(onPick)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Добавить все найденные: ${notAdded.size}") }
    }
    shown.forEachIndexed { index, option ->
        val isAdded = option.key in added
        Surface(
            onClick = {
                focusManager.clearFocus()
                onPick(option)
            },
            enabled = !isAdded,
            shape = settingsGroupShape(index, shown.size),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(option.label, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (isAdded) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Добавлено", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                } else {
                    Text("Добавить", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
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
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
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
private fun ResultCard(shape: Shape, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
}

/** Bars on a tonal card, so their labels don't lie on the wallpaper. */
@Composable
private fun BarCard(content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
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

/** A bar with its name above it, for names too long for [BarRow]'s label column. */
@Composable
private fun NamedBarRow(label: String, fraction: Float, value: String, caption: String? = null, modifier: Modifier = Modifier) {
    val grown = growingFraction(fraction.coerceIn(0f, 1f))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LinearProgressIndicator(
                progress = grown,
                modifier = Modifier.weight(1f).height(10.dp),
                drawStopIndicator = {},
            )
            Text(value, style = MaterialTheme.typography.labelLarge)
        }
        caption?.let { Caption(it) }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        shape = MaterialTheme.shapes.largeIncreased,
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
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
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
