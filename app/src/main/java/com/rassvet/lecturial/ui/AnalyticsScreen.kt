package com.rassvet.lecturial.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
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
import com.rassvet.lecturial.model.ScheduleTarget
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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

/** Study days to choose from: today, tomorrow and the rest of the coming week. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DayChips(days: List<LocalDate>, selected: LocalDate, onSelected: (LocalDate) -> Unit) {
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

/**
 * A listed teacher or group. A tap switches it off for the calculation and back on, so
 * someone can be left out for a moment without searching for them again; the cross
 * takes it out of the list.
 */
@Composable
internal fun ToggleableChip(
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
internal fun ToggleHint() {
    Text(
        "Нажмите, чтобы временно не учитывать — участник останется в списке. Крестик убирает его совсем.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val weekdayFormatter = DateTimeFormatter.ofPattern("EE, d MMM", ru)

private fun dayChipLabel(date: LocalDate, today: LocalDate?): String = when (date) {
    today -> "Сегодня"
    today?.plusDays(1) -> "Завтра"
    else -> date.format(weekdayFormatter)
}

internal fun sortTitle(sort: LoadSort, byName: String): String = when (sort) {
    LoadSort.Busiest -> "Загруженные"
    LoadSort.Freest -> "Свободные"
    LoadSort.ByName -> byName
}

// ------------------------------------------------------------------------------- shared

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ParticipantPicker(
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
internal fun <T> ConnectedChoice(
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
                shapes = connectedButtonShapes(index, options.size),
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
internal fun ResultCard(shape: Shape, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
}

/** Bars on a tonal card, so their labels don't lie on the wallpaper. */
@Composable
internal fun BarCard(content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
internal fun BarRow(label: String, fraction: Float, value: String, modifier: Modifier = Modifier) {
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
internal fun NamedBarRow(label: String, fraction: Float, value: String, caption: String? = null, modifier: Modifier = Modifier) {
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
internal fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

internal fun percent(value: Double): String = "${(value * 100).roundToInt()} %"

internal fun rangeText(range: ClosedRange<LocalDate>): String =
    "${range.start.format(shortDateFormatter)} — ${range.endInclusive.format(shortDateFormatter)}"

private const val TOP = 7
private const val MAX_RESULTS = 8

/**
 * Fades and lifts an item in when it first appears; [order] staggers neighbours so a list
 * of results builds up top to bottom instead of popping in at once.
 */
@Composable
internal fun Modifier.revealOnAppear(order: Int): Modifier {
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
internal fun growingFraction(target: Float): () -> Float {
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
internal fun TabIntro(summary: String, points: List<String>) {
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
