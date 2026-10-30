package com.rassvet.lecturial.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rassvet.lecturial.LecturialApplication
import com.rassvet.lecturial.data.devicecalendar.DeviceCalendarInfo
import com.rassvet.lecturial.data.devicecalendar.DeviceCalendarState
import com.rassvet.lecturial.data.devicecalendar.DeviceCalendarSync
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Settings → Google Календарь: pick a calendar of the phone's Google account and what goes
 * into it. The account's own sync carries the events to Google Calendar everywhere.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GoogleCalendarScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val sync = (LocalContext.current.applicationContext as LecturialApplication).googleCalendar
    val state by sync.state.collectAsState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { sync.refresh() }
    LaunchedEffect(Unit) { sync.refresh() }

    SettingsPage(title = "Google Календарь", onBack = onBack, modifier = modifier) {
        Text(
            "Пары и планы записываются в календарь Google-аккаунта на этом телефоне, а Google показывает их в " +
                "Календаре на телефоне, компьютере и в браузере. Входить в Google отдельно не нужно.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!state.hasPermission) {
            SettingsSection("Доступ к календарю", description = "Нужен, чтобы приложение могло добавлять события и обновлять их.") {
                Button(
                    onClick = { permission.launch(DeviceCalendarSync.CALENDAR_PERMISSIONS) },
                    modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                ) { Text("Разрешить доступ") }
            }
        } else {
            CalendarChoice(state, sync)
            if (state.selectedId != null) SyncSettings(state, sync)
        }
        state.error?.let { error ->
            Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CalendarChoice(state: DeviceCalendarState, sync: DeviceCalendarSync) {
    SettingsSection(
        "Календарь",
        description = "Лучше отдельный: создайте в Google Календаре календарь «Lecturial 2», и после синхронизации " +
            "аккаунта он появится здесь. Так пары не смешаются с вашими событиями.",
    ) {
        if (state.calendars.isEmpty()) {
            Text(
                "На телефоне нет календарей, куда можно записывать. Добавьте аккаунт Google в настройках телефона.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column {
            state.calendars.forEach { calendar ->
                CalendarRow(calendar, selected = calendar.id == state.selectedId) { sync.select(calendar) }
            }
        }
        TextButton(onClick = sync::refresh) { Text("Обновить список") }
    }
}

@Composable
private fun CalendarRow(calendar: DeviceCalendarInfo, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(calendar.color)))
        Column(Modifier.weight(1f)) {
            Text(calendar.name, style = MaterialTheme.typography.titleMedium)
            Text(
                if (calendar.isGoogle) calendar.account else "${calendar.account} · только на этом телефоне",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(selected = selected, onClick = null)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SyncSettings(state: DeviceCalendarState, sync: DeviceCalendarSync) {
    val settings = state.settings
    SettingsSection("Что отправлять") {
        WidgetSettingSwitch("Пары", "Все пары загруженных недель: аудитория, преподаватель, тип занятия", settings.lessons) {
            sync.updateSettings(settings.copy(lessons = it))
        }
        WidgetSettingSwitch("Планы", "Задачи, дедлайны и события из «Планов» с их датой и временем", settings.plans) {
            sync.updateSettings(settings.copy(plans = it))
        }
        PresetChoice(
            title = "Напоминание календаря",
            options = ReminderChoices,
            selected = settings.reminderMinutes ?: NO_REMINDER,
            label = { if (it == NO_REMINDER) "Нет" else "за $it мин" },
            onSelected = { sync.updateSettings(settings.copy(reminderMinutes = it.takeIf { minutes -> minutes != NO_REMINDER })) },
        )
    }
    SettingsSection(null) {
        Text(syncStatus(state), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.syncing) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        Button(
            onClick = sync::syncInBackground,
            enabled = !state.syncing,
            modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Синхронизировать сейчас")
        }
        OutlinedButton(onClick = sync::disconnect, modifier = Modifier.fillMaxWidth()) { Text("Отключить и убрать события") }
        Text(
            "Убираются только события, которые добавило приложение; ваши собственные остаются.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun syncStatus(state: DeviceCalendarState): String = when {
    state.syncing -> "Синхронизация…"
    state.lastSyncMillis != null -> buildString {
        append("Обновлено ")
        append(Instant.ofEpochMilli(state.lastSyncMillis).atZone(ZoneId.systemDefault()).format(SyncTime))
        state.lastSummary?.let { append(" · ").append(it) }
    }
    else -> "Ещё не синхронизировано"
}

private const val NO_REMINDER = -1
private val ReminderChoices = listOf(NO_REMINDER, 10, 15, 30)
private val SyncTime = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru"))
