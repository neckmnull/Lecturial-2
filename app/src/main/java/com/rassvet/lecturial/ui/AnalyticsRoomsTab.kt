package com.rassvet.lecturial.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import com.rassvet.lecturial.model.RoomCategories
import com.rassvet.lecturial.model.RoomKind
import com.rassvet.lecturial.model.intradayRoomLoad
import com.rassvet.lecturial.model.naturalRoomKey
import com.rassvet.lecturial.model.RoomInfo
import com.rassvet.lecturial.model.SiriusPairSlots
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rassvet.lecturial.model.AnalyticsPeriod
import java.time.format.DateTimeFormatter

private const val TOP = 7
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// ------------------------------------------------------------------------------- rooms

@Composable
internal fun RoomsTab(state: AnalyticsUiState, viewModel: AnalyticsViewModel) {
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
