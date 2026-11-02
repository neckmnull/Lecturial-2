package com.rassvet.lecturial.data.local

import android.content.Context
import androidx.core.content.edit
import com.rassvet.lecturial.model.DayPlannerSettings
import com.rassvet.lecturial.model.StudyTask
import com.rassvet.lecturial.model.normalizeDisciplineName
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** What the day planner remembers: the tasks done or put off per day and when each subject was last studied. */
data class DayPlannerMemory(
    /** Date → task key → minutes studied (0 when the task was put off). */
    val done: Map<LocalDate, Map<String, Int>> = emptyMap(),
    /** Normalised discipline name → the last day it was studied. */
    val lastStudied: Map<String, LocalDate> = emptyMap(),
    val settings: DayPlannerSettings = DayPlannerSettings(),
) {
    fun doneOn(date: LocalDate): Set<String> = done[date]?.keys.orEmpty()

    fun minutesOn(date: LocalDate): Int = done[date]?.values?.sum() ?: 0
}

/** Keeps [DayPlannerMemory] on the device; a month of history is enough for the planner. */
class DayPlannerStore(context: Context) {
    private val prefs = context.getSharedPreferences("day_planner", Context.MODE_PRIVATE)
    private val _memory = MutableStateFlow(load())
    val memory: StateFlow<DayPlannerMemory> = _memory.asStateFlow()

    /** Marks [task] studied on [date], or takes the mark back. */
    fun setDone(date: LocalDate, task: StudyTask, studied: Boolean) = update { memory ->
        val day = memory.done[date].orEmpty().toMutableMap()
        if (studied) day[task.key] = task.minutes else day.remove(task.key)
        val lastStudied = memory.lastStudied.toMutableMap()
        val name = task.discipline?.let(::normalizeDisciplineName)?.takeIf { it.isNotEmpty() }
        if (name != null && studied && (lastStudied[name]?.isBefore(date) != false)) lastStudied[name] = date
        memory.copy(done = memory.done + (date to day), lastStudied = lastStudied)
    }

    /** Leaves [task] out of [date] without counting it as studied. */
    fun putOff(date: LocalDate, task: StudyTask) = update { memory ->
        memory.copy(done = memory.done + (date to (memory.done[date].orEmpty() + (task.key to 0))))
    }

    fun updateSettings(settings: DayPlannerSettings) = update { it.copy(settings = settings) }

    private fun update(change: (DayPlannerMemory) -> DayPlannerMemory) {
        val updated = change(_memory.value).let { memory ->
            val oldest = LocalDate.now().minusDays(KEEP_DAYS)
            memory.copy(done = memory.done.filterKeys { !it.isBefore(oldest) }.filterValues { it.isNotEmpty() })
        }
        _memory.value = updated
        prefs.edit { putString(KEY, updated.toJson().toString()) }
    }

    private fun load(): DayPlannerMemory = runCatching {
        val json = JSONObject(prefs.getString(KEY, null) ?: return DayPlannerMemory())
        val done = json.optJSONObject("done")?.let { days ->
            days.keys().asSequence().associate { day ->
                val tasks = days.getJSONObject(day)
                LocalDate.parse(day) to tasks.keys().asSequence().associateWith { tasks.optInt(it) }
            }
        }.orEmpty()
        val last = json.optJSONObject("last")?.let { names ->
            names.keys().asSequence().associateWith { LocalDate.parse(names.getString(it)) }
        }.orEmpty()
        val defaults = DayPlannerSettings()
        val settings = json.optJSONObject("settings")?.let {
            DayPlannerSettings(
                dayEnd = LocalTime.of(it.optInt("endHour", defaults.dayEnd.hour).coerceIn(17, 23), 0),
                blockMinutes = it.optInt("block", defaults.blockMinutes).coerceIn(20, 90),
                maxStudyMinutes = it.optInt("max", defaults.maxStudyMinutes).coerceIn(30, 600),
            )
        } ?: defaults
        DayPlannerMemory(done, last, settings)
    }.getOrDefault(DayPlannerMemory())

    private fun DayPlannerMemory.toJson() = JSONObject()
        .put("done", JSONObject().apply { done.forEach { (day, tasks) -> put(day.toString(), JSONObject(tasks)) } })
        .put("last", JSONObject().apply { lastStudied.forEach { (name, day) -> put(name, day.toString()) } })
        .put(
            "settings",
            JSONObject().put("endHour", settings.dayEnd.hour).put("block", settings.blockMinutes).put("max", settings.maxStudyMinutes),
        )

    private companion object {
        const val KEY = "memory"
        const val KEEP_DAYS = 30L
    }
}
