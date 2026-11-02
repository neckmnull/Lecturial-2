package com.rassvet.lecturial.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rassvet.lecturial.LecturialApplication
import com.rassvet.lecturial.data.lks.LksClient
import com.rassvet.lecturial.model.LksDiscipline
import com.rassvet.lecturial.model.LksDisciplineStats
import com.rassvet.lecturial.model.LksMark
import com.rassvet.lecturial.model.LksSnapshot
import com.rassvet.lecturial.model.lksStats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import com.rassvet.lecturial.model.AssessmentKind
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.assessment
import com.rassvet.lecturial.model.assessmentEvents
import com.rassvet.lecturial.model.assessmentHint
import com.rassvet.lecturial.model.assessmentKind
import com.rassvet.lecturial.model.fivePointGrade
import com.rassvet.lecturial.model.isAssessmentEvent
import com.rassvet.lecturial.model.normalizeRoom
import com.rassvet.lecturial.model.parseEventMinutes
import com.rassvet.lecturial.model.sameDisciplineName
import java.time.temporal.ChronoUnit

private enum class StudyTab(val title: String) { Grades("Оценки"), Session("Сессия"), Alerts("Уведомления"), News("Новости") }

/** The Study tab: grades, attendance, session, notifications and news from the personal account. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StudyScreen(modifier: Modifier = Modifier, scheduleEvents: List<ScheduleEvent> = emptyList()) {
    val lks = (LocalContext.current.applicationContext as LecturialApplication).lks
    val state by lks.state.collectAsState()
    var signingIn by rememberSaveable { mutableStateOf(false) }
    if (signingIn) {
        LksLoginScreen(
            onSignedIn = { cookies ->
                lks.connect(cookies)
                signingIn = false
            },
            onClose = { signingIn = false },
            modifier = modifier,
        )
        return
    }
    LaunchedEffect(state.connected) { if (state.connected) lks.refreshIfStale() }
    var tab by rememberSaveable { mutableStateOf(StudyTab.Grades) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Учёба", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(
                    studyStatus(state.connected, state.snapshot),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.connected) {
                IconButton(onClick = { lks.refresh() }, enabled = !state.refreshing) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Обновить из личного кабинета")
                }
            }
        }
        if (state.refreshing) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        val snapshot = state.snapshot
        if (!state.connected && snapshot == null) {
            ConnectCard(onConnect = { signingIn = true })
            return@Column
        }
        if (!state.connected) {
            OutlinedButton(onClick = { signingIn = true }, modifier = Modifier.fillMaxWidth()) { Text("Войти снова — показаны сохранённые данные") }
        }
        ConnectedChoice(StudyTab.entries, tab, { it.title }) { tab = it }
        if (snapshot == null) {
            Caption("Загружаем данные из личного кабинета…")
            return@Column
        }
        when (tab) {
            StudyTab.Grades -> GradesTab(snapshot)
            StudyTab.Session -> SessionTab(snapshot, scheduleEvents)
            StudyTab.Alerts -> AlertsTab(snapshot)
            StudyTab.News -> NewsTab(snapshot)
        }
        if (state.connected) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = lks::disconnect, modifier = Modifier.fillMaxWidth()) { Text("Выйти из личного кабинета") }
        }
    }
}

@Composable
private fun ConnectCard(onConnect: () -> Unit) {
    SettingsSection(
        "Личный кабинет студента",
        description = "Оценки и посещаемость по каждой паре, домашние задания, сессия с экзаменами и зачётами, " +
            "уведомления и новости университета. Новые оценки и уведомления приходят пушами.",
    ) {
        Button(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        ) { Text("Войти в личный кабинет") }
        Caption("Вход на странице lks.siriusuniversity.ru. Приложение хранит только сессию — в зашифрованном виде на телефоне.")
    }
}

// ------------------------------------------------------------------------------- grades

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradesTab(snapshot: LksSnapshot) {
    val stats = remember(snapshot) { lksStats(snapshot.marks) }
    if (stats.isEmpty()) {
        Caption("В журнале пока нет записей.")
        return
    }
    val allGrades = stats.flatMap { it.grades }
    val recorded = snapshot.marks.filter { it.absent != null }
    var attendanceOpen by rememberSaveable { mutableStateOf(false) }
    if (attendanceOpen) AttendanceSheet(stats, onDismiss = { attendanceOpen = false })
    var order = 0
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
        StatTile(allGrades.takeIf { it.isNotEmpty() }?.average()?.let(::oneDecimalRu) ?: "—", "средний балл", Modifier.weight(1f).revealOnAppear(order++))
        StatTile(
            recorded.takeIf { it.isNotEmpty() }?.let { "${(it.count { mark -> mark.absent == false } * 100.0 / it.size).roundToInt()} %" } ?: "—",
            "посещаемость",
            Modifier.weight(1f).revealOnAppear(order++),
            onClick = { attendanceOpen = true }.takeIf { recorded.isNotEmpty() },
        )
        StatTile(
            "${snapshot.marks.count { it.absent == true }}",
            "пропусков",
            Modifier.weight(1f).revealOnAppear(order++),
            onClick = { attendanceOpen = true }.takeIf { recorded.isNotEmpty() },
        )
    }
    val homework = remember(snapshot) {
        val from = LocalDate.now().minusDays(7)
        snapshot.marks.filter { !it.homework.isNullOrBlank() && !it.date.isBefore(from) }.sortedBy { it.date }
    }
    if (homework.isNotEmpty()) {
        SectionTitle("Домашние задания", Modifier.revealOnAppear(order++))
        homework.forEachIndexed { index, mark ->
            Surface(shape = settingsGroupShape(index, homework.size), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(mark.discipline, style = MaterialTheme.typography.titleSmall)
                    Text(mark.homework.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    Caption("Задано ${mark.date.format(ShortDate)} · ${mark.type}")
                }
            }
        }
    }
    SectionTitle("По дисциплинам", Modifier.revealOnAppear(order++))
    stats.forEachIndexed { index, discipline ->
        DisciplineCard(discipline, settingsGroupShape(index, stats.size), Modifier.revealOnAppear(order++))
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val content = @Composable {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onClick != null) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (onClick == null) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier, content = content)
    } else {
        val interaction = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = modifier.pressScale(interaction),
            interactionSource = interaction,
            content = content,
        )
    }
}

/** Attendance by discipline and every missed lesson, newest first, from the attendance tiles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttendanceSheet(stats: List<LksDisciplineStats>, onDismiss: () -> Unit) {
    val missed = remember(stats) { stats.flatMap { it.marks }.filter { it.absent == true }.sortedByDescending { it.date } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Посещаемость", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Caption("Только занятия, где преподаватель отметил присутствие в журнале.")
            SectionTitle(if (missed.isEmpty()) "Пропусков нет" else "Пропуски · ${missed.size}")
            missed.forEachIndexed { index, mark ->
                Surface(shape = settingsGroupShape(index, missed.size), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.width(56.dp)) {
                            Text(mark.date.format(ShortDate), style = MaterialTheme.typography.labelLarge)
                            mark.startTime?.let { Caption(it) }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(mark.discipline, style = MaterialTheme.typography.titleSmall)
                            Caption(listOfNotNull(mark.type.takeIf { it.isNotBlank() }, mark.topic?.let { "тема: $it" }).joinToString(" · "))
                        }
                        GradeBadge("Н", MaterialTheme.colorScheme.error)
                    }
                }
            }
            val rated = stats.filter { it.attendance != null }.sortedBy { it.attendance }
            if (rated.isNotEmpty()) {
                SectionTitle("По дисциплинам")
                rated.forEach { discipline ->
                    val recorded = discipline.marks.count { it.absent != null }
                    NamedBarRow(
                        label = discipline.name,
                        fraction = discipline.attendance!!.toFloat(),
                        value = "${(discipline.attendance!! * 100).roundToInt()} %",
                        caption = "был на ${recorded - discipline.absences} из $recorded" +
                            (discipline.absences.takeIf { it > 0 }?.let { " · пропусков $it" } ?: ""),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisciplineCard(stats: LksDisciplineStats, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(stats.name) { mutableStateOf(false) }
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "discipline-arrow")
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = { expanded = !expanded },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().pressScale(interaction),
        interactionSource = interaction,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stats.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Caption(
                        listOfNotNull(
                            "${stats.grades.size} ${gradesWord(stats.grades.size)}",
                            stats.attendance?.let { "посещаемость ${(it * 100).roundToInt()} %" },
                            stats.absences.takeIf { it > 0 }?.let { "пропусков $it" },
                        ).joinToString(" · "),
                    )
                }
                stats.average?.let { GradeBadge(oneDecimalRu(it), gradeColor(it)) }
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = if (expanded) "Свернуть" else "Все занятия", modifier = Modifier.rotate(arrow))
            }
            if (stats.grades.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    stats.grades.takeLast(MAX_CHIPS).forEach { GradeBadge(it.toString(), gradeColor(it.toDouble())) }
                }
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    stats.marks.forEach { MarkRow(it) }
                }
            }
        }
    }
}

@Composable
private fun MarkRow(mark: LksMark) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.width(56.dp)) {
            Text(mark.date.format(ShortDate), style = MaterialTheme.typography.labelLarge)
            mark.startTime?.let { Caption(it) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(mark.type, style = MaterialTheme.typography.bodyMedium)
            mark.topic?.let { Caption("Тема: $it") }
            mark.homework?.let { Caption("ДЗ: $it") }
        }
        when {
            mark.grade != null -> GradeBadge(mark.grade.toString(), gradeColor(mark.grade.toDouble()))
            mark.absent == true -> GradeBadge("Н", MaterialTheme.colorScheme.error)
            mark.absent == false -> Caption("был")
        }
    }
}

@Composable
private fun GradeBadge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.16f), contentColor = color) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** Five-point scale colours; ten- and hundred-point grades are read on their own scale. */
@Composable
private fun gradeColor(value: Double): Color {
    val five = fivePointGrade(value)
    return when {
        five >= 4.5 -> MaterialTheme.colorScheme.primary
        five >= 3.5 -> MaterialTheme.colorScheme.tertiary
        five >= 2.5 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }
}

// ------------------------------------------------------------------------------- session

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionTab(snapshot: LksSnapshot, events: List<ScheduleEvent>) {
    if (snapshot.semesters.isEmpty()) {
        Caption("Траектория обучения пока пуста.")
        return
    }
    var selectedKey by rememberSaveable { mutableStateOf(snapshot.semesters.last().key) }
    val semester = snapshot.semesters.firstOrNull { it.key == selectedKey } ?: snapshot.semesters.last()
    if (snapshot.semesters.size > 1) {
        ConnectedChoice(snapshot.semesters, semester, { it.title.substringAfter(" ").trim('(', ')').ifBlank { it.title } }) { selectedKey = it.key }
    }
    val today = LocalDate.now()
    val journal = remember(snapshot) { lksStats(snapshot.marks) }
    val (assessed, other) = semester.disciplines.partition { it.assessment != null }
    val byKind = assessed.groupBy { it.assessment!! }
    var order = 0
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
        StatTile("${byKind[AssessmentKind.Exam].orEmpty().size}", "экзаменов", Modifier.weight(1f).revealOnAppear(order++))
        StatTile("${byKind[AssessmentKind.GradedCredit].orEmpty().size}", "дифф. зачётов", Modifier.weight(1f).revealOnAppear(order++))
        StatTile("${byKind[AssessmentKind.Credit].orEmpty().size}", "зачётов", Modifier.weight(1f).revealOnAppear(order++))
        StatTile(oneDecimalRu(assessed.sumOf { it.credits ?: 0.0 }).removeSuffix(",0"), "зачётных единиц", Modifier.weight(1f).revealOnAppear(order++))
    }
    val upcoming = remember(events, today) {
        events.filter { !it.date.isBefore(today) && isAssessmentEvent(it) }
            .sortedWith(compareBy({ it.date }, { parseEventMinutes(it.startTime) }))
    }
    if (upcoming.isNotEmpty()) {
        SectionTitle("Ближайшие в расписании", Modifier.revealOnAppear(order++))
        upcoming.take(4).forEachIndexed { index, event ->
            Surface(
                shape = settingsGroupShape(index, upcoming.take(4).size),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth().revealOnAppear(order++),
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(event.discipline, style = MaterialTheme.typography.titleSmall)
                        Text(assessmentWhen(event), style = MaterialTheme.typography.bodyMedium)
                    }
                    GradeBadge(daysUntil(today, event.date), MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    SectionTitle(semester.title, Modifier.revealOnAppear(order++))
    AssessmentKind.entries.forEach { kind ->
        val disciplines = byKind[kind] ?: return@forEach
        Text(
            "${kind.section} · ${disciplines.size}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp).revealOnAppear(order++),
        )
        Caption(kind.explanation)
        disciplines.forEachIndexed { index, discipline ->
            val stats = journal.firstOrNull { sameDisciplineName(it.name, discipline.name) }
            SessionCard(
                discipline = discipline,
                kind = kind,
                journal = stats,
                dates = assessmentEvents(events, discipline.name, today),
                today = today,
                shape = settingsGroupShape(index, disciplines.size),
                modifier = Modifier.revealOnAppear(order++),
            )
        }
    }
    if (other.isNotEmpty()) {
        SectionTitle("Без аттестации", Modifier.revealOnAppear(order++))
        Caption(other.joinToString(" · ") { it.name })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionCard(
    discipline: LksDiscipline,
    kind: AssessmentKind,
    journal: LksDisciplineStats?,
    dates: List<ScheduleEvent>,
    today: LocalDate,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(discipline.name, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                discipline.controlForms.forEach { form ->
                    GradeBadge(form, assessmentColor(assessmentKind(form)))
                }
                discipline.results.forEach { GradeBadge(it, MaterialTheme.colorScheme.primary) }
                when (discipline.passed) {
                    true -> if (discipline.results.isEmpty()) GradeBadge(if (kind == AssessmentKind.Credit) "зачтено" else "сдано", MaterialTheme.colorScheme.primary)
                    false -> GradeBadge(if (kind == AssessmentKind.Credit) "не зачтено" else "не сдано", MaterialTheme.colorScheme.error)
                    null -> Unit
                }
            }
            dates.forEach { event ->
                Text(
                    "${event.type}: ${assessmentWhen(event)} · ${daysUntil(today, event.date)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            journal?.let { stats ->
                Caption(
                    listOfNotNull(
                        stats.average?.let { "средний ${oneDecimalRu(it)}" },
                        stats.grades.size.takeIf { it > 0 }?.let { "оценок $it" },
                        stats.attendance?.let { "посещаемость ${(it * 100).roundToInt()} %" },
                        stats.absences.takeIf { it > 0 }?.let { "пропусков $it" },
                    ).joinToString(" · "),
                )
            }
            assessmentHint(kind, journal)?.let { hint ->
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Caption(
                listOfNotNull(
                    discipline.credits?.let { "${oneDecimalRu(it).removeSuffix(",0")} ЗЕ" },
                    discipline.hours?.let { "$it ч" },
                ).joinToString(" · "),
            )
        }
    }
}

@Composable
private fun assessmentColor(kind: AssessmentKind): Color = when (kind) {
    AssessmentKind.Exam -> MaterialTheme.colorScheme.error
    AssessmentKind.GradedCredit -> MaterialTheme.colorScheme.tertiary
    AssessmentKind.Credit -> MaterialTheme.colorScheme.secondary
    AssessmentKind.Control -> MaterialTheme.colorScheme.outline
}

/** "11 ноября, ср · 10:20 · К_1, ауд. 1.34" */
private fun assessmentWhen(event: ScheduleEvent): String = listOfNotNull(
    event.date.format(SessionDate),
    event.startTime.takeIf { it.isNotBlank() },
    event.classroom?.takeIf { it.isNotBlank() }?.let { normalizeRoom(it).name },
).joinToString(" · ")

private fun daysUntil(today: LocalDate, date: LocalDate): String = when (val days = ChronoUnit.DAYS.between(today, date)) {
    0L -> "сегодня"
    1L -> "завтра"
    else -> "через $days дн."
}

private val SessionDate = DateTimeFormatter.ofPattern("d MMMM, EE", Locale.forLanguageTag("ru"))

// ------------------------------------------------------------------------------- notifications and news

@Composable
private fun AlertsTab(snapshot: LksSnapshot) {
    val uri = LocalUriHandler.current
    if (snapshot.unreadNotifications > 0) Caption("Непрочитанных в личном кабинете: ${snapshot.unreadNotifications}")
    if (snapshot.notifications.isEmpty()) Caption("Уведомлений нет.")
    snapshot.notifications.forEachIndexed { index, item ->
        val interaction = remember { MutableInteractionSource() }
        Surface(
            onClick = { runCatching { uri.openUri(LksClient.BASE + "/dashboard") } },
            shape = settingsGroupShape(index, snapshot.notifications.size),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().revealOnAppear(index).pressScale(interaction),
            interactionSource = interaction,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall)
                if (item.text.isNotBlank()) Text(item.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.time.isNotBlank()) Caption(item.time)
            }
        }
    }
}

@Composable
private fun NewsTab(snapshot: LksSnapshot) {
    val uri = LocalUriHandler.current
    if (snapshot.news.isEmpty()) Caption("Новостей нет.")
    snapshot.news.forEachIndexed { index, item ->
        val interaction = remember { MutableInteractionSource() }
        Surface(
            onClick = { runCatching { uri.openUri("${LksClient.BASE}/news/show/${item.id}") } },
            shape = settingsGroupShape(index, snapshot.news.size),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().revealOnAppear(index).pressScale(interaction),
            interactionSource = interaction,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (item.date.isNotBlank()) Caption(item.date.take(16))
            }
        }
    }
}

private fun studyStatus(connected: Boolean, snapshot: LksSnapshot?): String = when {
    snapshot != null && snapshot.loadedAtMillis > 0 -> "Личный кабинет · обновлено " +
        Instant.ofEpochMilli(snapshot.loadedAtMillis).atZone(ZoneId.systemDefault()).format(UpdatedTime)
    connected -> "Личный кабинет подключён"
    else -> "Оценки, сессия и новости из личного кабинета"
}

private fun oneDecimalRu(value: Double): String = String.format(Locale.forLanguageTag("ru"), "%.1f", value)

private fun gradesWord(count: Int): String = when {
    count % 100 in 11..14 -> "оценок"
    count % 10 == 1 -> "оценка"
    count % 10 in 2..4 -> "оценки"
    else -> "оценок"
}

private const val MAX_CHIPS = 12
private val ShortDate = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru"))
private val UpdatedTime = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru"))
