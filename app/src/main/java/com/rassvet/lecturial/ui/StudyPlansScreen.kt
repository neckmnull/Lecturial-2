package com.rassvet.lecturial.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rassvet.lecturial.model.StudyPlan
import com.rassvet.lecturial.model.StudyPlanType
import com.rassvet.lecturial.model.StudyPlanUrgency
import com.rassvet.lecturial.model.dueLabel
import com.rassvet.lecturial.model.urgency
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import java.util.UUID

private enum class PlansFilter(val title: String) {
    Active("Активные"), Today("Сегодня"), Completed("Готово")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudyPlansScreen(
    plans: List<StudyPlan>,
    onSave: (StudyPlan) -> Unit,
    onCompletedChanged: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    var filter by remember { mutableStateOf(PlansFilter.Active) }
    var editingPlan by remember { mutableStateOf<StudyPlan?>(null) }
    var creatingPlan by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val active = plans.count { !it.isCompleted }
    val todayCount = plans.count { !it.isCompleted && it.dueDate == today }
    val overdue = plans.count { it.urgency(today) == StudyPlanUrgency.Overdue }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = filter,
            transitionSpec = {
                (fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 16 }) togetherWith
                    (fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 20 })
            },
            label = "plans-filter",
        ) { selectedFilter ->
            val visiblePlans = plans.filter { plan ->
                when (selectedFilter) {
                    PlansFilter.Active -> !plan.isCompleted
                    PlansFilter.Today -> !plan.isCompleted && plan.dueDate == today
                    PlansFilter.Completed -> plan.isCompleted
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "heading") {
                    Text("Планы", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Цели, задачи и дедлайны хранятся только на этом устройстве.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item(key = "summary") { PlansSummary(active, todayCount, overdue) }
                item(key = "filters") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlansFilter.entries.forEach { option ->
                            FilterChip(
                                selected = selectedFilter == option,
                                onClick = { filter = option },
                                label = { Text(option.title) },
                            )
                        }
                    }
                }
                if (visiblePlans.isEmpty()) {
                    item(key = "empty-${selectedFilter.name}") {
                        EmptyPlans(selectedFilter)
                    }
                } else {
                    items(visiblePlans, key = { it.id }) { plan ->
                        StudyPlanCard(
                            plan = plan,
                            today = today,
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(260),
                                placementSpec = tween(260),
                                fadeOutSpec = tween(180),
                            ),
                            onToggle = { onCompletedChanged(plan.id, !plan.isCompleted) },
                            onEdit = { editingPlan = plan },
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { creatingPlan = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) { Text("+", style = MaterialTheme.typography.headlineMedium) }
    }

    if (creatingPlan || editingPlan != null) {
        StudyPlanEditor(
            plan = editingPlan,
            onDismiss = {
                creatingPlan = false
                editingPlan = null
            },
            onSave = {
                onSave(it)
                creatingPlan = false
                editingPlan = null
            },
            onDelete = editingPlan?.let { plan ->
                {
                    onDelete(plan.id)
                    editingPlan = null
                }
            },
        )
    }
}

@Composable
private fun PlansSummary(active: Int, today: Int, overdue: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SummaryCell("$active", "активных", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        SummaryCell("$today", "на сегодня", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
        SummaryCell("$overdue", "просрочено", MaterialTheme.colorScheme.error, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCell(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyPlans(filter: PlansFilter) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (filter == PlansFilter.Completed) "Пока ничего не завершено" else "Здесь пока свободно",
                style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                if (filter == PlansFilter.Today) "На сегодня планов нет" else "Нажмите +, чтобы добавить первый пункт",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StudyPlanCard(
    plan: StudyPlan,
    today: LocalDate,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    var expanded by rememberSaveable(plan.id) { mutableStateOf(false) }
    val urgency = plan.urgency(today)
    val targetAccent = when (urgency) {
        StudyPlanUrgency.Overdue -> MaterialTheme.colorScheme.error
        StudyPlanUrgency.Today -> MaterialTheme.colorScheme.tertiary
        StudyPlanUrgency.Upcoming -> MaterialTheme.colorScheme.primary
        StudyPlanUrgency.WithoutDate -> MaterialTheme.colorScheme.secondary
        StudyPlanUrgency.Completed -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val accent by animateColorAsState(targetAccent, tween(220), label = "plan-accent")
    val cardAlpha by animateFloatAsState(if (plan.isCompleted) 0.66f else 1f, tween(220), label = "plan-alpha")
    val checkAlpha by animateFloatAsState(if (plan.isCompleted) 1f else 0f, tween(180), label = "plan-check")
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .alpha(cardAlpha)
            .semantics {
                contentDescription = if (expanded) "Свернуть план: ${plan.title}" else "Развернуть план: ${plan.title}"
            },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Surface(
                modifier = Modifier
                    .size(30.dp)
                    .semantics {
                        contentDescription = if (plan.isCompleted) "Вернуть в активные" else "Отметить выполненным"
                    }
                    .clickable(onClick = onToggle),
                shape = CircleShape,
                color = if (plan.isCompleted) accent else Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(2.dp, accent),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "✓",
                        modifier = Modifier.alpha(checkAlpha),
                        color = MaterialTheme.colorScheme.surface,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${plan.type.symbol} ${plan.type.title.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                    )
                    val dueText = buildString {
                        append(plan.dueLabel(today))
                        plan.dueTime?.let { append(" • ${it.format(planTimeFormatter)}") }
                    }
                    Text("  •  $dueText", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    plan.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (plan.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
                ) {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text(
                            plan.type.description,
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                        )
                        if (plan.note.isNotBlank()) {
                            Text(
                                plan.note,
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = onEdit, modifier = Modifier.align(Alignment.End)) {
                            Text("Изменить")
                        }
                    }
                }
                if (!expanded && plan.note.isNotBlank()) {
                    Text(
                        plan.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun StudyPlanEditor(
    plan: StudyPlan?,
    onDismiss: () -> Unit,
    onSave: (StudyPlan) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var type by remember(plan) { mutableStateOf(plan?.type ?: StudyPlanType.Task) }
    var title by remember(plan) { mutableStateOf(plan?.title.orEmpty()) }
    var note by remember(plan) { mutableStateOf(plan?.note.orEmpty()) }
    var dueDate by remember(plan) { mutableStateOf(plan?.dueDate) }
    var dueTime by remember(plan) { mutableStateOf(plan?.dueTime) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (plan == null) "Новый план" else "Изменить план") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudyPlanType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text("${option.symbol} ${option.title}") },
                        )
                    }
                }
                AnimatedContent(
                    targetState = type,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "plan-type-description",
                ) { selectedType ->
                    Text(
                        "${selectedType.symbol} ${selectedType.description}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(100) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Название") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(300) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Заметка (необязательно)") },
                    minLines = 2,
                    maxLines = 4,
                )
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(dueDate?.let { "Срок: ${planDateLabel(it)}" } ?: "Добавить срок")
                }
                if (dueDate != null) {
                    OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(dueTime?.let { "Время: ${it.format(planTimeFormatter)}" } ?: "Добавить время")
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { dueDate = null; dueTime = null }) { Text("Убрать дату") }
                        if (dueTime != null) TextButton(onClick = { dueTime = null }) { Text("Убрать время") }
                    }
                }
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        StudyPlan(
                            id = plan?.id ?: UUID.randomUUID().toString(),
                            type = type,
                            title = title.trim(),
                            note = note.trim(),
                            dueDate = dueDate,
                            dueTime = dueTime.takeIf { dueDate != null },
                            isCompleted = plan?.isCompleted ?: false,
                            createdAtMillis = plan?.createdAtMillis ?: System.currentTimeMillis(),
                        ),
                    )
                },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )

    if (showDatePicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = dueDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dueDate = pickerState.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Отмена") } },
        ) { DatePicker(pickerState) }
    }

    if (showTimePicker) {
        val initialTime = dueTime ?: LocalTime.of(9, 0)
        val timePickerState = rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Время выполнения") },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(timePickerState) } },
            confirmButton = {
                TextButton(onClick = {
                    dueTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Отмена") } },
        )
    }
}

private fun planDateLabel(date: LocalDate): String =
    date.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.forLanguageTag("ru")))

internal val planTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")