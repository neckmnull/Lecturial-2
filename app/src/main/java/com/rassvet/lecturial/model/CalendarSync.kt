package com.rassvet.lecturial.model

import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** What the user chose to send to the calendar. */
data class CalendarSyncSettings(
    val lessons: Boolean = true,
    val plans: Boolean = true,
    /** Minutes before the start for the calendar's own reminder; null keeps it off. */
    val reminderMinutes: Int? = null,
)

/** One event as it should look in the calendar. */
data class CalendarEntry(
    /** Stable identity across syncs: the lesson or plan occurrence it comes from. */
    val key: String,
    val subject: String,
    val date: LocalDate,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val location: String?,
    val body: String,
    val reminderMinutes: Int?,
) {
    /** Changes whenever anything shown in the calendar changes. */
    val fingerprint: String
        get() = sha256(listOf(subject, start, end, allDay, location, body, reminderMinutes).joinToString("\u001F"))
}

/** An event already in the calendar: its identity, the calendar's id for it and what was sent. */
data class SyncedEntry(val key: String, val remoteId: String, val fingerprint: String, val date: LocalDate)

data class SyncPlan(
    val create: List<CalendarEntry>,
    val update: List<Pair<SyncedEntry, CalendarEntry>>,
    val delete: List<SyncedEntry>,
) {
    val isEmpty: Boolean get() = create.isEmpty() && update.isEmpty() && delete.isEmpty()
}

/**
 * What to change so the calendar matches [desired]. Events before [windowStart] are history
 * and stay as they are, even if they are no longer in the schedule.
 */
fun planSync(desired: List<CalendarEntry>, synced: List<SyncedEntry>, windowStart: LocalDate): SyncPlan {
    val wanted = desired.associateBy { it.key }
    val existing = synced.associateBy { it.key }
    return SyncPlan(
        create = desired.filter { it.key !in existing },
        update = desired.mapNotNull { entry ->
            existing[entry.key]?.takeIf { it.fingerprint != entry.fingerprint }?.let { it to entry }
        },
        delete = synced.filter { it.key !in wanted && !it.date.isBefore(windowStart) },
    )
}

/** The last line of every event the app writes; it also tells the app's events apart in a calendar. */
const val LESSON_MARK = "Из расписания Lecturial 2"
const val PLAN_MARK = "Из планов Lecturial 2"

/**
 * An event in the calendar that carries the app's mark, whoever wrote it: this install, one
 * whose data was cleared since, or another build. [key] is known when the event recorded it;
 * [look] is its title and times, to recognise older events that did not.
 */
data class MarkedEvent(val remoteId: String, val key: String?, val look: String)

/** The marked events taken over by this sync, and the extra copies to remove. */
data class Adoption(val tracked: List<SyncedEntry>, val strays: List<String>)

/**
 * Brings the calendar's marked events that the local record doesn't know into the sync. Each
 * wanted entry may own one event: a marked event with its key, or else one that looks the same,
 * is adopted, with a blank fingerprint so this sync rewrites it. Everything else marked is a
 * stray copy, e.g. left by an earlier install, so the same lesson never shows up twice.
 */
fun adoptMarked(
    desired: List<CalendarEntry>,
    tracked: List<SyncedEntry>,
    marked: List<MarkedEvent>,
    look: (CalendarEntry) -> String,
): Adoption {
    val known = tracked.mapTo(HashSet()) { it.remoteId }
    val owned = tracked.mapTo(HashSet()) { it.key }
    val wanted = desired.associateBy { it.key }
    val byLook = desired.groupBy(look)
    val adopted = ArrayList<SyncedEntry>()
    val strays = ArrayList<String>()
    marked.filter { it.remoteId !in known }.forEach { event ->
        val entry = event.key?.let { wanted[it] }?.takeIf { it.key !in owned }
            ?: byLook[event.look]?.firstOrNull { it.key !in owned }
        if (entry == null) {
            strays += event.remoteId
        } else {
            owned += entry.key
            adopted += SyncedEntry(entry.key, event.remoteId, "", entry.date)
        }
    }
    return Adoption(tracked + adopted, strays)
}

/** Calendar events for the lessons of the schedule, in the names the user gave them. */
fun lessonEntries(
    events: List<ScheduleEvent>,
    aliases: Map<String, String>,
    reminderMinutes: Int?,
    /** The zone the calendars write in; a lesson's time is the university's. */
    phoneZone: ZoneId = UniversityZone,
): List<CalendarEntry> = events.mapNotNull { event ->
    fun phoneTime(minutes: Int) = event.date.atTime(minutes / 60, minutes % 60)
        .atZone(UniversityZone).withZoneSameInstant(phoneZone).toLocalDateTime()
    val start = parseEventMinutes(event.startTime) ?: return@mapNotNull null
    val end = parseEventMinutes(event.endTime) ?: return@mapNotNull null
    if (end <= start) return@mapNotNull null
    val room = event.classroom?.takeIf { it.isNotBlank() }?.let { normalizeRoom(it) }
        ?.takeIf { it.kind != RoomKind.Online }?.name
    val title = renamedDiscipline(event.discipline, aliases)
    CalendarEntry(
        key = "lesson:${event.id}",
        subject = if (event.type.isNotBlank()) "$title · ${event.type}" else title,
        date = event.date,
        start = phoneTime(start),
        end = phoneTime(end),
        allDay = false,
        location = room,
        body = listOfNotNull(
            event.type.takeIf { it.isNotBlank() }?.let { "Тип: $it" },
            event.teachers.takeIf { it.isNotEmpty() }?.let { "Преподаватель: ${it.joinToString(", ")}" },
            event.studentGroup?.takeIf { it.isNotBlank() }?.let { "Группа: $it" },
            event.onlineUrl?.takeIf { it.isNotBlank() }?.let { "Ссылка: $it" },
            event.comment?.takeIf { it.isNotBlank() },
            LESSON_MARK,
        ).joinToString("\n"),
        reminderMinutes = reminderMinutes,
    )
}

/** Calendar events for the plans that occur between [from] and [to], one per occurrence. */
fun planEntries(
    plans: List<StudyPlan>,
    from: LocalDate,
    to: LocalDate,
    reminderMinutes: Int?,
): List<CalendarEntry> = plans.filterNot { it.isCompleted }.flatMap { plan ->
    generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
        .mapNotNull { date -> plan.occurrenceOn(date) }
        .map { occurrence ->
            val date = occurrence.dueDate!!
            val timed = !occurrence.isAllDay && occurrence.dueTime != null
            val start = date.atTime(occurrence.dueTime ?: LocalTime.MIDNIGHT)
            val end = when {
                !timed -> (occurrence.endDate ?: date).plusDays(1).atStartOfDay()
                occurrence.endTime != null -> (occurrence.endDate ?: date).atTime(occurrence.endTime).takeIf { it.isAfter(start) }
                    ?: start.plusMinutes(PLAN_MINUTES)
                else -> start.plusMinutes(PLAN_MINUTES)
            }
            CalendarEntry(
                key = "plan:${plan.id}:$date",
                subject = "${plan.type.title}: ${plan.title}",
                date = date,
                start = if (timed) start else date.atStartOfDay(),
                end = end,
                allDay = !timed,
                location = null,
                body = listOfNotNull(plan.note.takeIf { it.isNotBlank() }, PLAN_MARK).joinToString("\n"),
                reminderMinutes = plan.reminderMinutesBefore ?: reminderMinutes,
            )
        }
        .toList()
}

private const val PLAN_MINUTES = 30L

private fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
