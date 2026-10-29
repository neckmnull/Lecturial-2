package com.rassvet.lecturial.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rassvet.lecturial.model.StudyPlan
import com.rassvet.lecturial.model.AppDisplayOptions
import com.rassvet.lecturial.model.StudyPlanColors
import com.rassvet.lecturial.model.StudyPlanRecurrence
import com.rassvet.lecturial.model.StudyPlanType
import com.rassvet.lecturial.model.StudyPlanUrgency
import com.rassvet.lecturial.model.dueLabel
import com.rassvet.lecturial.model.occursOn
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
    displayOptions: AppDisplayOptions = AppDisplayOptions(),
    requestedPlanId: String? = null,
    requestedPlanKey: Long = 0L,
    onRequestedPlanOpened: (Long) -> Unit = {},
    onSave: (StudyPlan) -> Unit,
    onCompletedChanged: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    var filter by remember { mutableStateOf(PlansFilter.Active) }
    var editingPlan by remember { mutableStateOf<StudyPlan?>(null) }
    var creatingPlan by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val active = plans.count { !it.isCompleted }
    val todayCount = plans.count { !it.isCompleted && it.occursOn(today) }
    val overdue = plans.count { it.urgency(today) == StudyPlanUrgency.Overdue }

    LaunchedEffect(requestedPlanId, requestedPlanKey, plans) {
        val requested = requestedPlanId?.let { id -> plans.firstOrNull { it.id == id } }
        if (requested != null) {
            filter = if (requested.isCompleted) PlansFilter.Completed else PlansFilter.Active
            editingPlan = requested
            onRequestedPlanOpened(requestedPlanKey)
        }
    }

    Box(Modifier.fillMaxSize()) {
        run {
            val selectedFilter = filter
            val visiblePlans = plans.filter { plan ->
                when (selectedFilter) {
                    PlansFilter.Active -> !plan.isCompleted
                    PlansFilter.Today -> !plan.isCompleted && plan.occursOn(today)
                    PlansFilter.Completed -> plan.isCompleted
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 96.dp),
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
                    PlansFilterButtons(selected = selectedFilter, onSelected = { filter = it })
                }
                if (visiblePlans.isEmpty()) {
                    item(key = "empty-${selectedFilter.name}") {
                        EmptyPlans(selectedFilter, Modifier.animateItem())
                    }
                } else {
                    items(visiblePlans, key = { it.id }) { plan ->
                        StudyPlanCard(
                            plan = plan,
                            today = today,
                            displayOptions = displayOptions,
                            modifier = Modifier.animateItem(
                                fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                                placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                            ),
                            onToggle = { onCompletedChanged(plan.id, !plan.isCompleted) },
                            onEdit = { editingPlan = plan },
                        )
                    }
                }
            }
        }
        MediumFloatingActionButton(
            onClick = { creatingPlan = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .semantics { contentDescription = "Добавить план" },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(FloatingActionButtonDefaults.MediumIconSize),
            )
        }
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

/** Single-choice filter as an Expressive connected button group. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlansFilterButtons(selected: PlansFilter, onSelected: (PlansFilter) -> Unit) {
    val options = PlansFilter.entries
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val checked = selected == option
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelected(option) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shapes = connectedButtonShapes(index, options.size),
            ) {
                if (checked) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(ToggleButtonDefaults.IconSize))
                    Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                }
                Text(option.title, maxLines = 1)
            }
        }
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
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyPlans(filter: PlansFilter, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (filter == PlansFilter.Completed) Icons.Rounded.CheckCircle else Icons.Rounded.DateRange,
                contentDescription = null,
                modifier = Modifier.size(32.dp).padding(bottom = 4.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(if (filter == PlansFilter.Completed) "Пока ничего не завершено" else "Здесь пока свободно",
                style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                if (filter == PlansFilter.Today) "На сегодня планов нет" else "Нажмите кнопку «Добавить», чтобы создать первый план",
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
    displayOptions: AppDisplayOptions,
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
        onClick = { expanded = !expanded },
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
            .semantics {
                contentDescription = if (expanded) "Свернуть план: ${plan.title}" else "Развернуть план: ${plan.title}"
            },
        shape = RoundedCornerShape(displayOptions.tileCornerRadiusDp.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(
            alpha = displayOptions.tileOpacityPercent / 100f,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Surface(
                onClick = onToggle,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(30.dp)
                    .semantics {
                        contentDescription = if (plan.isCompleted) "Вернуть в активные" else "Отметить выполненным"
                    },
                shape = CircleShape,
                color = if (plan.isCompleted) accent else Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(2.dp, accent),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).alpha(checkAlpha),
                        tint = MaterialTheme.colorScheme.surface,
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
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) +
                        fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
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
    var endDate by remember(plan) { mutableStateOf(plan?.endDate) }
    var endTime by remember(plan) { mutableStateOf(plan?.endTime) }
    var isAllDay by remember(plan) { mutableStateOf(plan?.isAllDay ?: false) }
    var recurrence by remember(plan) { mutableStateOf(plan?.recurrence ?: StudyPlanRecurrence.None) }
    var reminderMinutes by remember(plan) { mutableStateOf(plan?.reminderMinutesBefore) }
    var color by remember(plan) { mutableIntStateOf(plan?.color ?: StudyPlanColors.default) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    val isEvent = type == StudyPlanType.Event
    val effectiveEndDate = endDate ?: dueDate
    val validRange = dueDate == null || effectiveEndDate == null || when {
        effectiveEndDate < dueDate!! -> false
        effectiveEndDate > dueDate!! || isAllDay -> true
        dueTime == null || endTime == null -> true
        else -> endTime!! >= dueTime!!
    }

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
                    Text(dueDate?.let { "${if (isEvent) "Начало" else "Срок"}: ${planDateLabel(it)}" } ?: "Добавить дату")
                }
                if (dueDate != null && isEvent) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isAllDay,
                            onClick = {
                                isAllDay = !isAllDay
                                if (isAllDay) { dueTime = null; endTime = null }
                            },
                            label = { Text("Весь день") },
                        )
                        StudyPlanRecurrence.entries.forEach { option ->
                            FilterChip(
                                selected = recurrence == option,
                                onClick = { recurrence = option },
                                label = { Text(option.title) },
                            )
                        }
                    }
                    if (!isAllDay) {
                        OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(dueTime?.let { "Начало: ${it.format(planTimeFormatter)}" } ?: "Добавить время начала")
                        }
                    }
                    OutlinedButton(onClick = { showEndDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(endDate?.let { "Окончание: ${planDateLabel(it)}" } ?: "Добавить дату окончания")
                    }
                    if (!isAllDay) {
                        OutlinedButton(onClick = { showEndTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(endTime?.let { "Время окончания: ${it.format(planTimeFormatter)}" } ?: "Добавить время окончания")
                        }
                    }
                    Text("Локальное уведомление", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            null to "Нет", 0 to "В момент", 10 to "За 10 мин.",
                            30 to "За 30 мин.", 60 to "За час", 1440 to "За день",
                        ).forEach { (minutes, label) ->
                            FilterChip(
                                selected = reminderMinutes == minutes,
                                onClick = { reminderMinutes = minutes },
                                label = { Text(label) },
                            )
                        }
                    }
                    Text("Цвет", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StudyPlanColors.values.forEach { option ->
                            Surface(
                                modifier = Modifier.size(if (color == option) 34.dp else 30.dp)
                                    .semantics { contentDescription = "Выбрать цвет события" },
                                shape = CircleShape,
                                color = Color(option),
                                border = if (color == option) androidx.compose.foundation.BorderStroke(
                                    3.dp, MaterialTheme.colorScheme.onSurface,
                                ) else null,
                                onClick = { color = option },
                            ) {}
                        }
                    }
                    if (!validRange) Text(
                        "Окончание не может быть раньше начала",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (dueDate != null) {
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
                enabled = title.isNotBlank() && (!isEvent || dueDate != null) && validRange,
                onClick = {
                    onSave(
                        StudyPlan(
                            id = plan?.id ?: UUID.randomUUID().toString(),
                            type = type,
                            title = title.trim(),
                            note = note.trim(),
                            dueDate = dueDate,
                            dueTime = dueTime.takeIf { dueDate != null && !isAllDay },
                            endDate = endDate.takeIf { isEvent },
                            endTime = endTime.takeIf { isEvent && !isAllDay },
                            isAllDay = isEvent && isAllDay,
                            recurrence = recurrence.takeIf { isEvent } ?: StudyPlanRecurrence.None,
                            reminderMinutesBefore = reminderMinutes.takeIf { isEvent },
                            color = color,
                            todoistId = plan?.todoistId,
                            isCompleted = plan?.isCompleted ?: false,
                            createdAtMillis = plan?.createdAtMillis ?: System.currentTimeMillis(),
                            updatedAtMillis = System.currentTimeMillis(),
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

    if (showEndDatePicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = (endDate ?: dueDate)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDate = pickerState.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showEndDatePicker = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { showEndDatePicker = false }) { Text("Отмена") } },
        ) { DatePicker(pickerState) }
    }

    if (showEndTimePicker) {
        val initialTime = endTime ?: dueTime?.plusHours(1) ?: LocalTime.of(10, 0)
        val timePickerState = rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            title = { Text("Время окончания") },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(timePickerState) } },
            confirmButton = {
                TextButton(onClick = {
                    endTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showEndTimePicker = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { showEndTimePicker = false }) { Text("Отмена") } },
        )
    }
}

private val planDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.forLanguageTag("ru"))

private fun planDateLabel(date: LocalDate): String = date.format(planDateFormatter)

internal val planTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")