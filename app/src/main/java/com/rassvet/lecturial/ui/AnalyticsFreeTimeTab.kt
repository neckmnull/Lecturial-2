package com.rassvet.lecturial.ui

import androidx.compose.material.icons.rounded.Place
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.SlotAvailability
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.rassvet.lecturial.model.SiriusPairSlots
import com.rassvet.lecturial.model.displayEventType
import com.rassvet.lecturial.model.displayAddress
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rassvet.lecturial.model.ScheduleTarget
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ru = Locale.forLanguageTag("ru")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private val dayFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", ru)
// ------------------------------------------------------------------------------- free time

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FreeTimeTab(state: AnalyticsUiState, viewModel: AnalyticsViewModel) {
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
                shapes = connectedButtonShapes(index, SiriusPairSlots.size),
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
