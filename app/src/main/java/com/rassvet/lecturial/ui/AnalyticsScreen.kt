package com.rassvet.lecturial.ui

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
internal fun AnalyticsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
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
            BackIconButton(onBack)
            Text("Аналитика", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        ConnectedChoice(
            options = AnalyticsTab.entries,
            selected = state.tab,
            title = { it.title },
            onSelected = viewModel::setTab,
        )
        state.progress?.let { progress -> ProgressCard(progress, onCancel = viewModel::cancel) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when (state.tab) {
            AnalyticsTab.FreeTime -> FreeTimeTab(state, viewModel)
            AnalyticsTab.Load -> LoadTab(state, viewModel)
            AnalyticsTab.Rooms -> RoomsTab(state, viewModel)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ------------------------------------------------------------------------------- free time

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FreeTimeTab(state: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    Text(
        "Добавьте преподавателей или группы — подберём пары, когда свободны все.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    SectionTitle("Лучшие варианты")
    if (result.recommended.isEmpty()) {
        Text("В выбранном периоде не осталось пар.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    result.recommended.forEachIndexed { index, slot ->
        ResultCard(shape = settingsGroupShape(index, result.recommended.size)) {
            Text(slot.date.format(dayFormatter).replaceFirstChar { it.titlecase(ru) }, style = MaterialTheme.typography.titleMedium)
            Text(slot.slot.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (slot.isFreeForAll) "Свободны все" else "Заняты: ${slot.busy.joinToString()}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (slot.isFreeForAll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
    SectionTitle("Все общие окна")
    if (result.freeByDay.isEmpty()) {
        Text("Нет пары, когда свободны все. Посмотрите варианты выше.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    result.freeByDay.forEach { (date, slots) ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

@Composable
private fun LoadTab(state: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    Text(
        "Занятость преподавателя или группы за неделю, месяц или семестр.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ResultCard(shape = RoundedCornerShape(24.dp)) {
        Text("Для кого", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(state.loadTarget?.label ?: "Выберите преподавателя или группу", style = MaterialTheme.typography.titleMedium)
    }
    ParticipantPicker(state, viewModel, actionLabel = "Выбрать", onPick = viewModel::setLoadTarget)
    ConnectedChoice(AnalyticsPeriod.entries, state.loadPeriod, { it.title }, viewModel::setLoadPeriod)
    Button(
        onClick = viewModel::computeLoad,
        enabled = state.loadTarget != null && !state.isBusy,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Посчитать") }

    val result = state.loadResult ?: return
    val stats = result.stats
    SectionTitle("${result.target.label} · ${rangeText(result.range)}")
    StatGrid(stats)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatGrid(stats: OccupancyStats) {
    val cells = listOf(
        "${stats.pairs}" to "пар",
        "${stats.academicHours}" to "ак. часов",
        "${stats.activeDays} из ${stats.studyDays}" to "учебных дней",
        String.format(ru, "%.1f", stats.averagePerActiveDay) to "пар в учебный день",
        "${stats.maxPairsInDay}" to (stats.busiestDay?.let { "макс. · ${it.format(shortDateFormatter)}" } ?: "макс. в день"),
        "${stats.windows}" to "окон между парами",
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
        cells.forEach { (value, label) ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------- rooms

@Composable
private fun RoomsTab(state: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    Text(
        "Загрузка всех аудиторий: самые занятые и самые свободные, и как она меняется в течение дня. " +
            "Сбор за семестр занимает 10–20 минут; загруженные недели сохраняются, повторный расчёт быстрее.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ConnectedChoice(AnalyticsPeriod.entries, state.roomPeriod, { it.title }, viewModel::setRoomPeriod)
    Button(
        onClick = viewModel::computeRooms,
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Собрать статистику") }

    val result = state.roomsResult ?: return
    val used = result.loads.filter { it.busySlots > 0 }
    SectionTitle("${rangeText(result.range)} · ${result.loads.size} аудиторий")
    SectionTitle("Самые загруженные")
    used.take(TOP).forEach { BarRow(it.room, it.utilization.toFloat(), percent(it.utilization)) }
    SectionTitle("Самые свободные")
    used.takeLast(TOP).reversed().forEach { BarRow(it.room, it.utilization.toFloat(), percent(it.utilization)) }
    val empty = result.loads.size - used.size
    if (empty > 0) {
        Text("Без занятий за период: $empty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SectionTitle("Загрузка в течение дня")
    Text(
        "Доля используемых аудиторий, занятых в каждую пару",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    result.intraday.forEach { (slot, share) ->
        BarRow("${slot.number} · ${slot.start}", share.toFloat(), percent(share))
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
private fun ResultCard(shape: RoundedCornerShape, content: @Composable () -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun BarRow(label: String, fraction: Float, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, Modifier.width(120.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
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
