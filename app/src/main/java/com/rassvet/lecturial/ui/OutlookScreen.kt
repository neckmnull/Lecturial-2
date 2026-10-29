package com.rassvet.lecturial.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rassvet.lecturial.LecturialApplication
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Settings → Outlook: sign in with Microsoft and choose what goes to the calendar. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OutlookSettingsScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val outlook = (context.applicationContext as LecturialApplication).outlook
    val state by outlook.state.collectAsState()
    SettingsPage(title = "Outlook", onBack = onBack, modifier = modifier) {
        Text(
            "Пары и планы появляются в отдельном календаре «Lecturial 2» в вашем Outlook — на телефоне, " +
                "компьютере и в браузере — и обновляются сами после каждого обновления расписания.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            !state.available -> Text(
                "В этой сборке синхронизация с Outlook выключена: для неё нужен идентификатор приложения Microsoft.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            !state.signedIn -> {
                Button(
                    onClick = { outlook.startSignIn(context) },
                    enabled = !state.signingIn,
                    modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                ) { Text(if (state.signingIn) "Ждём вход в браузере…" else "Войти через Microsoft") }
                Text(
                    "Подойдёт личный аккаунт Microsoft (Outlook.com, Hotmail) или рабочий и учебный, если организация " +
                        "разрешает сторонние приложения. Приложение получает доступ только к календарю.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> SignedInSettings(state.account.orEmpty(), state, outlook)
        }
        state.error?.let { error ->
            Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SignedInSettings(
    account: String,
    state: com.rassvet.lecturial.data.outlook.OutlookState,
    outlook: com.rassvet.lecturial.data.outlook.OutlookSync,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(account, style = MaterialTheme.typography.titleMedium)
                Text(
                    syncStatus(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (state.syncing) LinearWavyProgressIndicator(Modifier.fillMaxWidth())

    Text("Что отправлять", style = MaterialTheme.typography.titleMedium)
    val settings = state.settings
    SyncSwitch("Пары", "Все пары загруженных недель: аудитория, преподаватель, тип занятия", settings.lessons) {
        outlook.updateSettings(settings.copy(lessons = it))
    }
    SyncSwitch("Планы", "Задачи, дедлайны и события из «Планов» с их датой и временем", settings.plans) {
        outlook.updateSettings(settings.copy(plans = it))
    }
    PresetChoice(
        title = "Напоминание Outlook",
        options = ReminderChoices,
        selected = settings.reminderMinutes ?: NO_REMINDER,
        label = { if (it == NO_REMINDER) "Нет" else "за $it мин" },
        onSelected = { outlook.updateSettings(settings.copy(reminderMinutes = it.takeIf { minutes -> minutes != NO_REMINDER })) },
    )
    Text(
        "Напоминания приложения о парах работают независимо. У плана со своим напоминанием остаётся его время.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = outlook::syncInBackground,
        enabled = !state.syncing,
        modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
    ) {
        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text("Синхронизировать сейчас")
    }
    OutlinedButton(onClick = outlook::signOut, modifier = Modifier.fillMaxWidth()) { Text("Выйти из аккаунта") }
    Text(
        "После выхода календарь «Lecturial 2» и его события остаются в Outlook — удалить календарь можно там.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SyncSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$title: ${if (checked) "включено" else "выключено"}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun syncStatus(state: com.rassvet.lecturial.data.outlook.OutlookState): String = when {
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
