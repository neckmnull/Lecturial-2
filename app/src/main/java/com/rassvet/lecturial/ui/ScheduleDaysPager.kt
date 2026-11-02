package com.rassvet.lecturial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rassvet.lecturial.model.LksMark
import com.rassvet.lecturial.model.weekRangeLabel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * The schedule a day at a time: the week as a strip of days on top, the day's pairs below,
 * swiped sideways from day to day. The other layout to the long list, for those who think of
 * the schedule as days rather than a feed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleDaysPager(
    state: ScheduleUiState,
    rows: List<ScheduleRow>,
    lksMarks: List<LksMark>,
    focusedLesson: String?,
    onFocusShown: () -> Unit,
    onToday: () -> Unit,
    rowContent: @Composable (ScheduleRow) -> Unit,
) {
    val weeks = state.loadedScheduleWeeks()
    val dates = remember(weeks) { weeks.flatMap { week -> (0L..6L).map { week.weekStart.plusDays(it) } }.distinct().sorted() }
    val sections = remember(rows) { daySections(rows) }
    val today = state.now.toLocalDate()
    val banner = rows.firstOrNull { it is ScheduleRow.Banner } as ScheduleRow.Banner?
    if (dates.isEmpty()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            rows.forEach { rowContent(it) }
        }
        return
    }
    var plannerDate by remember { mutableStateOf<LocalDate?>(null) }
    // A new range of weeks starts the pager over at the chosen day.
    key(dates.first(), dates.size) {
        val start = dates.indexOf(state.selectedDate).takeIf { it >= 0 } ?: dates.indexOf(today).coerceAtLeast(0)
        val pager = rememberPagerState(initialPage = start) { dates.size }
        val scope = rememberCoroutineScope()
        val current = dates[pager.currentPage.coerceIn(dates.indices)]
        LaunchedEffect(focusedLesson, sections) {
            val target = focusedLesson?.let { lesson -> sections.entries.firstOrNull { (_, section) -> section.items.any { it.key == lesson } }?.key }
                ?: return@LaunchedEffect
            pager.animateScrollToPage(dates.indexOf(target))
            onFocusShown()
        }
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                val padding = state.appDisplayOptions.outerPaddingDp.dp
                Column(Modifier.padding(horizontal = padding).padding(top = 12.dp)) {
                    banner?.let { OfflineBanner(it.message, it.updatedAt) }
                    WeekStrip(
                        dates = dates,
                        current = current,
                        today = today,
                        busyDays = sections.filterValues { section -> section.items.any { it is ScheduleRow.Lesson } }.keys,
                        group = state.scheduleLabel,
                        onDay = { day -> scope.launch { pager.animateScrollToPage(dates.indexOf(day)) } },
                    )
                }
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize().testTag("schedule-days"),
                    beyondViewportPageCount = 1,
                    key = { dates[it].toString() },
                ) { page ->
                    val date = dates[page]
                    val section = sections[date]
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = padding, end = padding, top = 8.dp, bottom = 96.dp),
                    ) {
                        item(key = "heading") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    DayHeading(
                                        title = section?.day?.title ?: if (date == today) "Сегодня" else date.format(WeekdayFormat).replaceFirstChar { it.uppercase(Russian) },
                                        subtitle = section?.day?.subtitle ?: "${date.format(DayMonthFormat)} • нет пар",
                                        separated = false,
                                    )
                                }
                                if (!state.isTeacherMode && !date.isBefore(today)) {
                                    FilledTonalButton(onClick = { plannerDate = date }) { Text("План дня") }
                                }
                            }
                        }
                        if (section == null || section.items.isEmpty()) {
                            item(key = "empty") { EmptyDay(date) }
                        } else {
                            items(section.items, key = { it.key }, contentType = { it.contentType }) { rowContent(it) }
                        }
                    }
                }
            }
            val todayIndex = dates.indexOf(today)
            JumpToTodayButton(
                direction = if (todayIndex == pager.currentPage) null else 0,
                onClick = {
                    if (todayIndex >= 0) scope.launch { pager.animateScrollToPage(todayIndex) } else onToday()
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            )
        }
    }
    plannerDate?.let { date ->
        ModalBottomSheet(onDismissRequest = { plannerDate = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("План дня", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Caption("${date.format(DayMonthFormat)}: пары, домашние задания и свободное время")
                DayPlannerSection(weeks, lksMarks, state.studyPlans, state.now, fixedDate = date)
            }
        }
    }
}

/** The week of [current] as seven days to tap; a dot marks the days with pairs. */
@Composable
private fun WeekStrip(
    dates: List<LocalDate>,
    current: LocalDate,
    today: LocalDate,
    busyDays: Set<LocalDate>,
    group: String,
    onDay: (LocalDate) -> Unit,
) {
    val monday = current.with(DayOfWeek.MONDAY)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(group, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                Caption(weekRangeLabel(monday, monday.plusDays(6)))
            }
            val previous = current.minusWeeks(1).takeIf { it in dates } ?: monday.minusDays(1).takeIf { it in dates }
            val next = current.plusWeeks(1).takeIf { it in dates } ?: monday.plusWeeks(1).takeIf { it in dates }
            IconButton(onClick = { previous?.let(onDay) }, enabled = previous != null) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Предыдущая неделя")
            }
            IconButton(onClick = { next?.let(onDay) }, enabled = next != null) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Следующая неделя")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (0L..6L).map { monday.plusDays(it) }.forEach { day ->
                val selected = day == current
                val loaded = day in dates
                Column(
                    Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(enabled = loaded) { onDay(day) }
                        .semantics {
                            this.selected = selected
                            contentDescription = day.format(DayMonthFormat)
                        }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        day.format(ShortWeekday),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(
                                when {
                                    selected -> MaterialTheme.colorScheme.primary
                                    day == today -> MaterialTheme.colorScheme.primaryContainer
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                },
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${day.dayOfMonth}",
                            style = MaterialTheme.typography.titleMedium,
                            color = when {
                                selected -> MaterialTheme.colorScheme.onPrimary
                                day == today -> MaterialTheme.colorScheme.onPrimaryContainer
                                loaded -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.outline
                            },
                        )
                    }
                    Box(
                        Modifier
                            .size(5.dp)
                            .background(
                                if (day in busyDays) MaterialTheme.colorScheme.secondary else androidx.compose.ui.graphics.Color.Transparent,
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

private val Russian = Locale.forLanguageTag("ru")
private val ShortWeekday = DateTimeFormatter.ofPattern("EE", Russian)
private val WeekdayFormat = DateTimeFormatter.ofPattern("EEEE", Russian)
private val DayMonthFormat = DateTimeFormatter.ofPattern("d MMMM", Russian)
