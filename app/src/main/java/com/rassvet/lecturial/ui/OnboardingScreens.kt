package com.rassvet.lecturial.ui

import androidx.compose.material.icons.rounded.Search
import com.rassvet.lecturial.model.TeacherInfo
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ArrowDropDown
import com.rassvet.lecturial.R
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.foundation.Image
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.rassvet.lecturial.model.isForeignLanguage
import androidx.compose.material3.ButtonDefaults

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LanguageTeacherSelectionScreen(
    state: ScheduleUiState,
    onSelected: (String?) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    backContentDescription: String = "Назад к выбору группы",
) {
    var selectedTeacher by rememberSaveable(state.selectedGroup) { mutableStateOf(state.languageTeacher) }
    val teachers = state.loadedScheduleWeeks().flatMap { it.days }
        .flatMap { it.events }.filter { isForeignLanguage(it.discipline) }
        .flatMap { it.teachers }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    val loading = state.isLoading || state.isRefreshing || (state.loadedScheduleWeeks().isEmpty() && state.errorMessage == null)
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        BackIconButton(onBack, contentDescription = backContentDescription)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Шаг 2 из 2 · ${state.selectedGroup.orEmpty()}", color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge)
            Text("Преподаватель иностранного языка", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold)
            Text("Выберите своего преподавателя, чтобы в расписании остались только ваши занятия иностранного языка.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LoadingIndicator(Modifier.size(40.dp))
                        Text("Загружаем преподавателей…")
                    }
                }
                teachers.forEach { teacher ->
                    TeacherChoiceCard(teacher, selectedTeacher == teacher) { selectedTeacher = teacher }
                }
                if (!loading && teachers.isEmpty()) {
                    Text(state.errorMessage ?: "В выбранном диапазоне расписания пока нет преподавателей иностранного языка.")
                }
                if (state.errorMessage != null) {
                    TextButton(onClick = onRetry) { Text("Повторить загрузку") }
                }
                TeacherChoiceCard("Все преподаватели", selectedTeacher == "") { selectedTeacher = "" }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSelected(selectedTeacher?.takeIf { it.isNotBlank() }) },
            enabled = selectedTeacher != null,
            modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        ) { Text("Продолжить", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight)) }
        Text("Выбор можно изменить в настройках.", Modifier.fillMaxWidth().padding(top = 10.dp),
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TeacherChoiceCard(title: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f,
        tween(durationMillis = 100), label = "teacher-scale")
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        tween(durationMillis = 140), label = "teacher-color")
    Surface(onClick = onClick, interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics { this.selected = selected },
        shape = MaterialTheme.shapes.medium, color = color,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.titleMedium)
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GroupSelectionScreen(
    state: ScheduleUiState,
    onSearchQueryChanged: (String) -> Unit,
    onRetry: () -> Unit,
    onGroupSelected: (String) -> Unit,
    onTeacherSelected: (TeacherInfo) -> Unit = {},
    onSearchModeChanged: (ScheduleSearchMode) -> Unit = {},
) {
    val teacherSearch = state.searchMode == ScheduleSearchMode.Teacher
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var selectedTeacher by remember { mutableStateOf<TeacherInfo?>(null) }
    LaunchedEffect(state.availableGroups) {
        if (selectedGroup !in state.availableGroups) selectedGroup = null
    }
    LaunchedEffect(state.availableTeachers) {
        if (selectedTeacher !in state.availableTeachers) selectedTeacher = null
    }
    val hasSelection = if (teacherSearch) selectedTeacher != null else selectedGroup != null
    val nothingLoaded = if (teacherSearch) state.availableTeachers.isEmpty() else state.availableGroups.isEmpty()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
        Image(
            painter = painterResource(R.drawable.onboarding_sirius_logo),
            contentDescription = "Логотип Университета Сириус",
            modifier = Modifier
                .width(190.dp)
                .height(70.dp)
                .offset(x = (-6).dp),
        )
        Spacer(Modifier.height(2.dp))
        Text("Привет!", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            if (teacherSearch) "Найдите себя в списке" else "Из какой ты группы?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (teacherSearch) {
                "Покажем ваши пары во всех группах и напомним о них"
            } else {
                "Найди свою группу — дальше выбери преподавателя иностранного языка"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        ScheduleSearchModeButtons(mode = state.searchMode, onModeChanged = onSearchModeChanged)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.groupSearchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (teacherSearch) "Поиск преподавателя" else "Поиск группы") },
            placeholder = { Text(if (teacherSearch) "Например, Иванов" else "Например, ИОП-ИТ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        Spacer(Modifier.height(12.dp))
        when {
            state.isGroupsLoading && nothingLoaded ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .semantics { contentDescription = "Загрузка групп" },
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }
            state.groupsError != null && nothingLoaded -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.groupsError, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                TextButton(onClick = onRetry) { Text("Повторить") }
            }
            nothingLoaded -> Text(
                if (teacherSearch) "Преподаватели не найдены" else "Группы не найдены",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            teacherSearch -> state.availableTeachers.forEach { teacher ->
                val selected = selectedTeacher == teacher
                Surface(
                    onClick = { selectedTeacher = teacher },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .semantics { this.selected = selected },
                    shape = MaterialTheme.shapes.medium,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(teacher.name, style = MaterialTheme.typography.titleMedium)
                        teacher.department?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            else -> state.availableGroups.forEach { group ->
                val selected = selectedGroup == group
                Surface(
                    onClick = { selectedGroup = group },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .semantics { this.selected = selected },
                    shape = MaterialTheme.shapes.medium,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                ) {
                    Text(group, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (teacherSearch) selectedTeacher?.let(onTeacherSelected) else selectedGroup?.let(onGroupSelected)
            },
            enabled = hasSelection,
            modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        ) {
            AnimatedContent(
                targetState = hasSelection,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "continue-button-label",
            ) { selectionMade ->
                Text(
                    when {
                        selectionMade -> "Продолжить"
                        teacherSearch -> "Выберите себя в списке"
                        else -> "Выбери группу"
                    },
                    style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Выбор можно изменить позже в настройках.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GroupDropdown(
    state: ScheduleUiState,
    onSearchQueryChanged: (String) -> Unit,
    onRetry: () -> Unit,
    onSelected: (String) -> Unit,
    onTeacherSelected: (TeacherInfo) -> Unit = {},
    onSearchModeChanged: (ScheduleSearchMode) -> Unit = {},
) {
    val teacherSearch = state.searchMode == ScheduleSearchMode.Teacher
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = {
                expanded = true
                onRetry()
            },
            modifier = Modifier.semantics {
                contentDescription = "Выбрать группу, сейчас ${state.scheduleLabel}"
            },
        ) {
            Text(
                "Изменить",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            val arrowRotation by animateFloatAsState(
                if (expanded) 180f else 0f,
                MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "group-arrow",
            )
            Icon(
                Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.graphicsLayer { rotationZ = arrowRotation },
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ScheduleSearchModeButtons(
                mode = state.searchMode,
                onModeChanged = onSearchModeChanged,
                modifier = Modifier.width(300.dp).padding(horizontal = 8.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = state.groupSearchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier.width(300.dp).padding(horizontal = 8.dp),
                label = { Text(if (teacherSearch) "Поиск преподавателя" else "Поиск группы") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            val nothingLoaded = if (teacherSearch) state.availableTeachers.isEmpty() else state.availableGroups.isEmpty()
            when {
                state.isGroupsLoading && nothingLoaded -> Box(
                    Modifier
                        .width(300.dp)
                        .height(64.dp)
                        .semantics { contentDescription = "Загрузка групп" },
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }
                state.groupsError != null && nothingLoaded -> Column(
                    modifier = Modifier.width(300.dp).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.groupsError, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    TextButton(onClick = onRetry) { Text("Повторить") }
                }
                nothingLoaded -> Text(
                    if (teacherSearch) "Преподаватели не найдены" else "Группы не найдены",
                    modifier = Modifier.width(300.dp).padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
            if (teacherSearch) {
                state.availableTeachers.forEach { teacher ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(teacher.name)
                                teacher.department?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        onClick = { expanded = false; onTeacherSelected(teacher) },
                    )
                }
            } else {
                state.availableGroups.forEach { group ->
                    DropdownMenuItem(
                        text = { Text(group) },
                        onClick = { expanded = false; onSelected(group) },
                    )
                }
            }
        }
    }
}

/** Student / teacher switch as an Expressive connected button group. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScheduleSearchModeButtons(
    mode: ScheduleSearchMode,
    onModeChanged: (ScheduleSearchMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        listOf(ScheduleSearchMode.Group to "Студент", ScheduleSearchMode.Teacher to "Преподаватель")
            .forEachIndexed { index, (option, title) ->
                ToggleButton(
                    checked = mode == option,
                    onCheckedChange = { onModeChanged(option) },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    shapes = connectedButtonShapes(index, 2),
                ) { Text(title, maxLines = 1) }
            }
    }
}
