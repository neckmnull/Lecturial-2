package com.rassvet.lecturial.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rassvet.lecturial.LecturialApplication
import com.rassvet.lecturial.model.DayBlock
import com.rassvet.lecturial.model.DayBlockKind
import com.rassvet.lecturial.model.LksMark
import com.rassvet.lecturial.model.ScheduleWeek
import com.rassvet.lecturial.model.StudyPlan
import com.rassvet.lecturial.model.planDay
import com.rassvet.lecturial.model.studyTasks
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The day planner: the pairs of a day with homework, deadlines, getting ready for the next
 * pairs and going over every subject in turn fitted into the free time between them. Ticking a
 * block off or putting it off plans the rest of the day again, and subjects studied lately step
 * back for the ones that waited.
 */
@Composable
internal fun DayPlannerSection(
    weeks: List<ScheduleWeek>,
    marks: List<LksMark>,
    plans: List<StudyPlan>,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
    /** Plans this day only, without the day chips, e.g. for a page of the day-by-day schedule. */
    fixedDate: LocalDate? = null,
) {
    val store = (LocalContext.current.applicationContext as LecturialApplication).dayPlanner
    val memory by store.memory.collectAsState()
    val today = now.toLocalDate()
    var chosen by rememberSaveable { mutableStateOf(today) }
    val date = fixedDate ?: chosen
    val events = remember(weeks) { weeks.flatMap { it.days }.flatMap { it.events } }
    val tasks = remember(date, events, marks, plans, memory) {
        studyTasks(date, events, marks, plans, memory.lastStudied, memory.settings)
    }
    val blocks = remember(date, now, events, plans, tasks, memory) {
        planDay(date, now, events, plans, tasks, memory.doneOn(date), memory.minutesOn(date), memory.settings)
    }
    Column(modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (fixedDate == null) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0L..6L).map { today.plusDays(it) }.forEach { day ->
                    FilterChip(selected = day == date, onClick = { chosen = day }, label = { Text(plannerDayLabel(day, today)) })
                }
            }
        }
        val study = blocks.filter { it.kind == DayBlockKind.Study }.sumOf { it.minutes }
        val free = blocks.filter { it.kind == DayBlockKind.Free }.sumOf { it.minutes }
        val pairs = blocks.count { it.kind == DayBlockKind.Lesson }
        Caption(
            listOfNotNull(
                pairs.takeIf { it > 0 }?.let { "пар $it" },
                study.takeIf { it > 0 }?.let { "учёба ${durationText(it)}" },
                free.takeIf { it > 0 }?.let { "свободно ${durationText(it)}" },
                memory.minutesOn(date).takeIf { it > 0 }?.let { "уже сделано ${durationText(it)}" },
            ).joinToString(" · ").ifEmpty { if (date.isBefore(today)) "День прошёл" else "День уже закончился" },
        )
        blocks.forEachIndexed { index, block ->
            PlannerBlockRow(
                block = block,
                shape = settingsGroupShape(index, blocks.size),
                onDone = block.task?.let { task -> { store.setDone(date, task, studied = true) } },
                onPutOff = block.task?.let { task -> { store.putOff(date, task) } },
            )
        }
        val doneTasks = memory.done[date].orEmpty()
        if (doneTasks.isNotEmpty()) {
            val byKey = tasks.associateBy { it.key }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Caption("Отмечено: ${doneTasks.count { it.value > 0 }} сделано, ${doneTasks.count { it.value == 0 }} отложено")
                TextButton(onClick = {
                    doneTasks.keys.forEach { key -> byKey[key]?.let { store.setDone(date, it, studied = false) } }
                }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Вернуть всё", Modifier.padding(start = 6.dp))
                }
            }
        }
        val settings = memory.settings
        SettingsSection("Как планировать") {
            Caption("Учиться до")
            ConnectedChoice(listOf(20, 21, 22, 23), settings.dayEnd.hour, { "$it:00" }) { hour ->
                store.updateSettings(settings.copy(dayEnd = LocalTime.of(hour, 0)))
            }
            Caption("Блок учёбы")
            ConnectedChoice(listOf(25, 45, 60), settings.blockMinutes, { "$it мин" }) { minutes ->
                store.updateSettings(settings.copy(blockMinutes = minutes))
            }
            Caption("Не больше в день")
            ConnectedChoice(listOf(120, 180, 240, 360), settings.maxStudyMinutes, { durationText(it) }) { minutes ->
                store.updateSettings(settings.copy(maxStudyMinutes = minutes))
            }
        }
    }
}

@Composable
private fun PlannerBlockRow(
    block: DayBlock,
    shape: RoundedCornerShape,
    onDone: (() -> Unit)?,
    onPutOff: (() -> Unit)?,
) {
    val accent = when (block.kind) {
        DayBlockKind.Lesson -> MaterialTheme.colorScheme.primary
        DayBlockKind.Event -> MaterialTheme.colorScheme.tertiary
        DayBlockKind.Study -> MaterialTheme.colorScheme.secondary
        DayBlockKind.Break -> MaterialTheme.colorScheme.outlineVariant
        DayBlockKind.Free -> Color.Transparent
    }
    val quiet = block.kind == DayBlockKind.Break || block.kind == DayBlockKind.Free
    Surface(
        shape = shape,
        color = if (quiet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = if (quiet) 44.dp else 64.dp).padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(48.dp)) {
                Text(block.start.format(TimeFormat), style = MaterialTheme.typography.labelLarge)
                Caption(block.end.format(TimeFormat))
            }
            Box(
                Modifier
                    .width(4.dp)
                    .height(if (quiet) 24.dp else 40.dp)
                    .background(accent, RoundedCornerShape(2.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    block.title,
                    style = if (quiet) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                    color = if (quiet) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (block.kind == DayBlockKind.Lesson) FontWeight.SemiBold else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                block.detail?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            if (onPutOff != null) {
                IconButton(onClick = onPutOff) { Icon(Icons.Rounded.Close, contentDescription = "Отложить на другой день") }
            }
            if (onDone != null) {
                FilledTonalIconButton(onClick = onDone) { Icon(Icons.Rounded.Check, contentDescription = "Сделано") }
            }
        }
    }
}

private fun plannerDayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Сегодня"
    today.plusDays(1) -> "Завтра"
    else -> day.format(ChipDate).replaceFirstChar { it.uppercase(RussianLocale) }
}

private fun durationText(minutes: Int): String = when {
    minutes < 60 -> "$minutes мин"
    minutes % 60 == 0 -> "${minutes / 60} ч"
    else -> "${minutes / 60} ч ${minutes % 60} мин"
}

private val RussianLocale = Locale.forLanguageTag("ru")
private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val ChipDate = DateTimeFormatter.ofPattern("EE d", RussianLocale)
