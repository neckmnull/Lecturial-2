package com.rassvet.lecturial.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rassvet.lecturial.data.ScheduleAnalyticsRepository
import com.rassvet.lecturial.data.local.UserPreferences
import com.rassvet.lecturial.model.AnalyticsPeriod
import com.rassvet.lecturial.model.OccupancyStats
import com.rassvet.lecturial.model.PairSlot
import com.rassvet.lecturial.model.RoomLoad
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.ScheduleTarget
import com.rassvet.lecturial.model.SlotAvailability
import com.rassvet.lecturial.model.TeacherInfo
import com.rassvet.lecturial.model.LoadLevel
import com.rassvet.lecturial.model.coursePairs
import com.rassvet.lecturial.model.courseTeacherNames
import com.rassvet.lecturial.model.intradayRoomLoad
import com.rassvet.lecturial.model.loadLevel
import com.rassvet.lecturial.model.studentGroups
import com.rassvet.lecturial.model.surnameWithInitials
import com.rassvet.lecturial.model.weekStartsOf
import com.rassvet.lecturial.model.weeklyPairs
import com.rassvet.lecturial.model.isSpecialistGroup
import com.rassvet.lecturial.model.occupancyStats
import com.rassvet.lecturial.model.recommendMeetingSlots
import com.rassvet.lecturial.model.roomLoads
import com.rassvet.lecturial.model.slotAvailability
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AnalyticsTab(val title: String) { Load("Нагрузка"), FreeTime("Общее время"), Rooms("Аудитории") }

enum class LoadSort(val title: String) { ByLoad("По нагрузке"), ByName("По алфавиту") }

enum class FreeTimeRange(val title: String) { ThisWeek("Эта неделя"), NextWeek("Следующая"), TwoWeeks("2 недели") }

/** A teacher or group taking part in an analysis; [key] is a schedule key. */
data class AnalyticsParticipant(val key: String, val label: String)

data class FreeTimeResult(
    val recommended: List<SlotAvailability>,
    val freeByDay: Map<LocalDate, List<PairSlot>>,
    /** Lessons of each participant, by label, to show a day's schedule under a slot. */
    val schedules: Map<String, List<ScheduleEvent>>,
)

/** Identifies a pair on a date in the free-time results. */
data class SlotKey(val date: LocalDate, val pair: Int)

val SlotAvailability.key: SlotKey get() = SlotKey(date, slot.number)

/** A free room proposed for a meeting slot. */
data class RoomSuggestion(val isLoading: Boolean, val room: String? = null, val note: String? = null)

/** One teacher or group in the course overview. */
data class LoadRow(
    val participant: AnalyticsParticipant,
    val stats: OccupancyStats,
    /** Average over the weeks the course has lessons in. */
    val pairsPerWeek: Double,
    /** Pairs held for the course groups; null for group rows. */
    val coursePairs: Int?,
    val weekly: List<Pair<LocalDate, Int>>,
) {
    val level: LoadLevel get() = loadLevel(pairsPerWeek)
    val isTeacher: Boolean get() = ScheduleTarget.isTeacher(participant.key)
    val shortName: String get() = if (isTeacher) surnameWithInitials(participant.label) else participant.label
}

data class CourseLoadResult(
    val range: ClosedRange<LocalDate>,
    val teachers: List<LoadRow>,
    val groups: List<LoadRow>,
    /** Names from group schedules that the teacher search couldn't match. */
    val unresolved: List<String>,
)

data class RoomsResult(
    val range: ClosedRange<LocalDate>,
    val loads: List<RoomLoad>,
    val intraday: List<Pair<PairSlot, Double>>,
)

data class AnalyticsProgress(val text: String, val done: Int, val total: Int) {
    val fraction: Float get() = if (total <= 0) 0f else done.toFloat() / total
}

data class AnalyticsUiState(
    val tab: AnalyticsTab = AnalyticsTab.Load,
    val searchMode: ScheduleSearchMode = ScheduleSearchMode.Teacher,
    val query: String = "",
    val isSearching: Boolean = false,
    val teacherResults: List<TeacherInfo> = emptyList(),
    val groupResults: List<String> = emptyList(),
    val specialistOnly: Boolean = true,
    val searchError: String? = null,
    val participants: List<AnalyticsParticipant> = emptyList(),
    /** Participants switched off by a tap: they stay in the list but aren't compared. */
    val excludedParticipants: Set<String> = emptySet(),
    val freeRange: FreeTimeRange = FreeTimeRange.ThisWeek,
    val freeResult: FreeTimeResult? = null,
    val expandedSlot: SlotKey? = null,
    val roomSuggestions: Map<SlotKey, RoomSuggestion> = emptyMap(),
    val courseGroups: List<String> = emptyList(),
    val extraTeachers: List<AnalyticsParticipant> = emptyList(),
    /** Course groups and teachers switched off by a tap: listed, but left out of the load. */
    val excludedCourse: Set<String> = emptySet(),
    val editingCourse: Boolean = false,
    val loadPeriod: AnalyticsPeriod = AnalyticsPeriod.Month,
    val loadSort: LoadSort = LoadSort.ByLoad,
    val courseResult: CourseLoadResult? = null,
    val detail: LoadRow? = null,
    val roomPeriod: AnalyticsPeriod = AnalyticsPeriod.Week,
    val roomsResult: RoomsResult? = null,
    val progress: AnalyticsProgress? = null,
    val error: String? = null,
) {
    val isBusy: Boolean get() = progress != null

    val activeParticipants: List<AnalyticsParticipant>
        get() = participants.filterNot { it.key in excludedParticipants }

    val activeCourseGroups: List<String> get() = courseGroups.filterNot { it in excludedCourse }

    val activeExtraTeachers: List<AnalyticsParticipant>
        get() = extraTeachers.filterNot { it.key in excludedCourse }

    fun sorted(rows: List<LoadRow>): List<LoadRow> = when (loadSort) {
        LoadSort.ByLoad -> rows.sortedWith(compareByDescending<LoadRow> { it.stats.pairs }.thenBy { it.shortName })
        LoadSort.ByName -> rows.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.shortName })
    }

    /** Group results narrowed to specialist groups when that filter is on. */
    val visibleGroups: List<String>
        get() = if (specialistOnly) groupResults.filter(::isSpecialistGroup) else groupResults
}

class AnalyticsViewModel(
    private val repository: ScheduleAnalyticsRepository,
    private val preferences: UserPreferences,
    private val today: () -> LocalDate = LocalDate::now,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {
    private val _state = MutableStateFlow(AnalyticsUiState())
    val state: StateFlow<AnalyticsUiState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var workJob: Job? = null

    init {
        viewModelScope.launch {
            // Start from the user's own schedule: a teacher analyses themselves first.
            val key = preferences.selectedGroup.first() ?: return@launch
            val me = AnalyticsParticipant(key, ScheduleTarget.label(key, preferences.selectedTargetName.first()))
            val savedCourse = repository.courseGroups()
            val excludedCourse = repository.excludedCourseMembers()
            // Common free time starts with the teacher and the course's specialist groups.
            fun defaultParticipants(course: List<String>) =
                listOf(me) + course.filter(::isSpecialistGroup).map { AnalyticsParticipant(it, it) }
            _state.update {
                it.copy(
                    participants = defaultParticipants(savedCourse),
                    courseGroups = savedCourse.ifEmpty { if (ScheduleTarget.isTeacher(key)) emptyList() else listOf(key) },
                    excludedCourse = excludedCourse,
                    searchMode = if (ScheduleTarget.isTeacher(key)) ScheduleSearchMode.Teacher else ScheduleSearchMode.Group,
                    specialistOnly = ScheduleTarget.isTeacher(key),
                )
            }
            if (savedCourse.isEmpty() && ScheduleTarget.isTeacher(key)) {
                // A teacher's course defaults to the groups they teach themselves.
                runCatching {
                    val monday = today().with(DayOfWeek.MONDAY)
                    repository.lessons(key, monday, monday.plusDays(13), today())
                        .flatMap { lesson -> lesson.studentGroups() }
                        .distinct()
                        .sortedWith(String.CASE_INSENSITIVE_ORDER)
                }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { groups ->
                    _state.update { current ->
                        if (current.courseGroups.isNotEmpty()) current
                        else current.copy(
                            courseGroups = groups,
                            participants = if (current.participants == listOf(me)) defaultParticipants(groups) else current.participants,
                        )
                    }
                }
            }
        }
    }

    fun setTab(tab: AnalyticsTab) = _state.update { it.copy(tab = tab, error = null) }

    fun setSearchMode(mode: ScheduleSearchMode) {
        _state.update { it.copy(searchMode = mode, teacherResults = emptyList(), groupResults = emptyList(), searchError = null) }
        search(_state.value.query, debounce = false)
    }

    fun setSpecialistOnly(value: Boolean) = _state.update { it.copy(specialistOnly = value) }

    fun search(query: String, debounce: Boolean = true) {
        _state.update { it.copy(query = query, searchError = null) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(teacherResults = emptyList(), groupResults = emptyList(), isSearching = false) }
            return
        }
        val mode = _state.value.searchMode
        searchJob = viewModelScope.launch {
            if (debounce) delay(400)
            _state.update { it.copy(isSearching = true) }
            try {
                if (mode == ScheduleSearchMode.Teacher) {
                    val teachers = repository.teachers(query)
                    _state.update { if (it.query != query) it else it.copy(teacherResults = teachers, isSearching = false) }
                } else {
                    val groups = repository.groups(query)
                    _state.update { if (it.query != query) it else it.copy(groupResults = groups, isSearching = false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _state.update { it.copy(isSearching = false, searchError = failure.message ?: "Поиск не удался") }
            }
        }
    }

    fun addParticipant(participant: AnalyticsParticipant) = _state.update { state ->
        if (state.participants.any { it.key == participant.key }) state
        else state.copy(participants = state.participants + participant, freeResult = null, query = "",
            teacherResults = emptyList(), groupResults = emptyList())
    }

    fun removeParticipant(key: String) = _state.update { state ->
        state.copy(
            participants = state.participants.filterNot { it.key == key },
            excludedParticipants = state.excludedParticipants - key,
            freeResult = null,
        )
    }

    /** Switches a participant off or back on without taking it out of the list. */
    fun toggleParticipant(key: String) = _state.update { state ->
        val excluded = if (key in state.excludedParticipants) state.excludedParticipants - key else state.excludedParticipants + key
        state.copy(excludedParticipants = excluded, freeResult = null)
    }

    /** Switches a course group or teacher off or back on; it stays in the course list. */
    fun toggleCourseMember(key: String) {
        _state.update { state ->
            val excluded = if (key in state.excludedCourse) state.excludedCourse - key else state.excludedCourse + key
            state.copy(excludedCourse = excluded, courseResult = null)
        }
        repository.setExcludedCourseMembers(_state.value.excludedCourse)
    }

    /** Adds a group to the course, or an extra teacher when [participant] is a teacher. */
    fun addToCourse(participant: AnalyticsParticipant) {
        _state.update { state ->
            val cleared = state.copy(query = "", teacherResults = emptyList(), groupResults = emptyList(), courseResult = null)
            if (ScheduleTarget.isTeacher(participant.key)) {
                if (state.extraTeachers.any { it.key == participant.key }) cleared
                else cleared.copy(extraTeachers = state.extraTeachers + participant)
            } else {
                if (participant.key in state.courseGroups) cleared
                else cleared.copy(courseGroups = state.courseGroups + participant.key)
            }
        }
        repository.setCourseGroups(_state.value.courseGroups)
    }

    fun removeFromCourse(key: String) {
        _state.update { state ->
            state.copy(
                courseGroups = state.courseGroups - key,
                extraTeachers = state.extraTeachers.filterNot { it.key == key },
                excludedCourse = state.excludedCourse - key,
                courseResult = null,
            )
        }
        repository.setCourseGroups(_state.value.courseGroups)
        repository.setExcludedCourseMembers(_state.value.excludedCourse)
    }

    fun setEditingCourse(value: Boolean) = _state.update { it.copy(editingCourse = value) }

    fun setLoadSort(sort: LoadSort) = _state.update { it.copy(loadSort = sort) }

    fun openDetail(row: LoadRow?) = _state.update { it.copy(detail = row) }

    fun setFreeRange(range: FreeTimeRange) = _state.update { it.copy(freeRange = range, freeResult = null) }

    fun setLoadPeriod(period: AnalyticsPeriod) = _state.update { it.copy(loadPeriod = period, courseResult = null) }

    fun setRoomPeriod(period: AnalyticsPeriod) = _state.update { it.copy(roomPeriod = period, roomsResult = null) }

    /** Opens a recommended slot: the day's schedules and, once found, a free room. */
    fun toggleSlot(slot: SlotAvailability) {
        val key = slot.key
        val opening = _state.value.expandedSlot != key
        _state.update { it.copy(expandedSlot = if (opening) key else null) }
        if (opening && key !in _state.value.roomSuggestions) suggestRoom(slot)
    }

    /**
     * Looks for a free room among those the participants actually use, rooms used that day
     * first, and checks each one's schedule for the slot.
     */
    private fun suggestRoom(slot: SlotAvailability) {
        val result = _state.value.freeResult ?: return
        val key = slot.key
        _state.update { it.copy(roomSuggestions = it.roomSuggestions + (key to RoomSuggestion(isLoading = true))) }
        viewModelScope.launch {
            val lessons = result.schedules.values.flatten()
            val candidates = lessons.mapNotNull { lesson -> lesson.classroom?.trim()?.takeIf { it.isNotEmpty() }?.let { it to lesson.date } }
                .groupBy({ it.first }, { it.second })
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, List<LocalDate>>> { slot.date in it.value }.thenByDescending { it.value.size })
                .map { it.key }
                .take(ROOM_CANDIDATES)
            val weekStart = slot.date.with(DayOfWeek.MONDAY)
            val suggestion = try {
                candidates.firstNotNullOfOrNull { room ->
                    val id = repository.resolveClassroom(room) ?: return@firstNotNullOfOrNull null
                    val roomLessons = repository.lessons(ScheduleTarget.classroomKey(id), weekStart, weekStart.plusDays(6), today())
                    room.takeUnless { com.rassvet.lecturial.model.isBusy(roomLessons, slot.date, slot.slot) }
                }?.let { RoomSuggestion(isLoading = false, room = it, note = "свободна в это время, здесь уже проходят ваши занятия") }
                    ?: RoomSuggestion(isLoading = false, note = if (candidates.isEmpty()) "Нет данных об аудиториях участников" else "Привычные аудитории в это время заняты")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                RoomSuggestion(isLoading = false, note = "Не удалось проверить аудитории")
            }
            _state.update { it.copy(roomSuggestions = it.roomSuggestions + (key to suggestion)) }
        }
    }

    fun cancel() {
        workJob?.cancel()
        _state.update { it.copy(progress = null) }
    }

    /** Requirements 2 and 5: pairs when every chosen teacher or group is free. */
    fun findFreeTime() {
        val participants = _state.value.activeParticipants
        if (participants.isEmpty()) return
        val range = freeTimeRange(_state.value.freeRange)
        runWork { report ->
            val schedules = linkedMapOf<String, List<ScheduleEvent>>()
            participants.forEachIndexed { index, participant ->
                report(AnalyticsProgress("Загружаем: ${participant.label}", index, participants.size))
                schedules[participant.label] = repository.lessons(participant.key, range.start, range.endInclusive, today())
            }
            val availability = slotAvailability(schedules, range)
            val moment = now()
            val upcoming = availability.filter { it.date.atTime(it.slot.start).isAfter(moment) }
            val result = FreeTimeResult(
                recommended = recommendMeetingSlots(upcoming, moment),
                freeByDay = upcoming.filter { it.isFreeForAll }.groupBy({ it.date }, { it.slot }),
                schedules = schedules,
            )
            _state.update { it.copy(freeResult = result, expandedSlot = null, roomSuggestions = emptyMap()) }
        }
    }

    /**
     * Requirement 3 for a teacher: load of every teacher of the course groups (found in the
     * groups' schedules) plus extra teachers, and of the groups themselves.
     */
    fun computeCourse() {
        val groups = _state.value.activeCourseGroups
        val extra = _state.value.activeExtraTeachers
        if (groups.isEmpty() && extra.isEmpty()) return
        val range = _state.value.loadPeriod.range(today())
        val weeks = weekStartsOf(range).size
        runWork { report ->
            val groupLessons = linkedMapOf<String, List<ScheduleEvent>>()
            groups.forEachIndexed { index, group ->
                report(AnalyticsProgress("Расписание групп", index, groups.size))
                groupLessons[group] = repository.lessons(group, range.start, range.endInclusive, today())
            }
            val names = courseTeacherNames(groupLessons.values.flatten())
            val teachers = linkedMapOf<String, AnalyticsParticipant>()
            val unresolved = mutableListOf<String>()
            names.forEachIndexed { index, name ->
                report(AnalyticsProgress("Ищем преподавателей", index, names.size))
                val teacher = runCatching { repository.resolveTeacher(name) }.getOrElse { failure ->
                    if (failure is CancellationException) throw failure
                    null
                }
                if (teacher == null) unresolved += name
                else teachers[ScheduleTarget.teacherKey(teacher.id)] = AnalyticsParticipant(ScheduleTarget.teacherKey(teacher.id), teacher.name)
            }
            extra.forEach { teachers.putIfAbsent(it.key, it) }

            val total = teachers.size * weeks
            val done = AtomicInteger(0)
            val teacherLessons = java.util.concurrent.ConcurrentHashMap<String, List<ScheduleEvent>>()
            val queues = teachers.values.withIndex().groupBy({ it.index % WORKERS }, { it.value })
            coroutineScope {
                queues.values.map { batch ->
                    async {
                        val remote = repository.newRemote()
                        batch.forEach { teacher ->
                            teacherLessons[teacher.key] = repository.lessons(
                                teacher.key, range.start, range.endInclusive, today(), remote,
                            ) { report(AnalyticsProgress("Нагрузка преподавателей", done.incrementAndGet(), total)) }
                        }
                    }
                }.awaitAll()
            }

            // Average over the weeks that have lessons at all, so an unpublished end of the
            // semester doesn't dilute everyone's load.
            val allLessons = groupLessons.values.flatten() + teacherLessons.values.flatten()
            val activeWeeks = allLessons.filter { it.date in range }.map { it.date.with(DayOfWeek.MONDAY) }.toSet().size
                .coerceAtLeast(1)
            fun row(participant: AnalyticsParticipant, lessons: List<ScheduleEvent>, course: Boolean) = run {
                val stats = occupancyStats(lessons, range)
                LoadRow(
                    participant = participant,
                    stats = stats,
                    pairsPerWeek = stats.pairs.toDouble() / activeWeeks,
                    coursePairs = if (course) coursePairs(lessons, groups, range) else null,
                    weekly = weeklyPairs(lessons, range),
                )
            }
            val result = CourseLoadResult(
                range = range,
                teachers = teachers.values.map { row(it, teacherLessons[it.key].orEmpty(), course = true) },
                groups = groupLessons.map { (group, lessons) -> row(AnalyticsParticipant(group, group), lessons, course = false) },
                unresolved = unresolved,
            )
            _state.update { it.copy(courseResult = result) }
        }
    }

    /** Requirement 4: busiest and freest classrooms and their load through the day. */
    fun computeRooms() {
        val range = _state.value.roomPeriod.range(today())
        runWork { report ->
            report(AnalyticsProgress("Ищем аудитории", 0, 0))
            val rooms = repository.allClassrooms { found ->
                report(AnalyticsProgress("Найдено аудиторий: $found", 0, 0))
            }
            val weeks = com.rassvet.lecturial.model.weekStartsOf(range).size
            val total = rooms.size * weeks
            val done = AtomicInteger(0)
            val schedules = mutableMapOf<String, List<ScheduleEvent>>()
            val lock = Mutex()
            // Two sessions in parallel: noticeably faster without hammering the site.
            val queues = rooms.withIndex().groupBy({ it.index % WORKERS }, { it.value })
            coroutineScope {
                queues.values.map { batch ->
                    async {
                        val remote = repository.newRemote()
                        batch.forEach { room ->
                            val lessons = runCatching {
                                repository.lessons(
                                    ScheduleTarget.classroomKey(room), range.start, range.endInclusive, today(), remote,
                                ) {
                                    report(AnalyticsProgress("Загружаем аудитории", done.incrementAndGet(), total))
                                }
                            }.getOrElse { failure ->
                                if (failure is CancellationException) throw failure
                                emptyList()
                            }
                            val label = ScheduleTarget.classroomLabel(room)
                            lock.withLock { schedules[label] = schedules[label].orEmpty() + lessons }
                        }
                    }
                }.awaitAll()
            }
            val loads = roomLoads(schedules, range)
            // Rooms with no lessons at all (meeting rooms, guest rooms) would only dilute the daily curve.
            val used = loads.filter { it.busySlots > 0 }
            _state.update { it.copy(roomsResult = RoomsResult(range, loads, intradayRoomLoad(used))) }
        }
    }

    private fun freeTimeRange(range: FreeTimeRange): ClosedRange<LocalDate> {
        val date = today()
        val nextMonday = date.with(DayOfWeek.MONDAY).plusWeeks(1)
        return when (range) {
            FreeTimeRange.ThisWeek -> date..date.with(DayOfWeek.SUNDAY)
            FreeTimeRange.NextWeek -> nextMonday..nextMonday.plusDays(6)
            FreeTimeRange.TwoWeeks -> date..date.plusDays(13)
        }
    }

    private fun runWork(block: suspend (report: (AnalyticsProgress) -> Unit) -> Unit) {
        workJob?.cancel()
        _state.update { it.copy(error = null, progress = AnalyticsProgress("Готовим расчёт", 0, 0)) }
        workJob = viewModelScope.launch {
            try {
                block { progress -> _state.update { it.copy(progress = progress) } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _state.update { it.copy(error = failure.message ?: "Не удалось загрузить расписание") }
            } finally {
                _state.update { it.copy(progress = null) }
            }
        }
    }

    class Factory(
        private val repository: ScheduleAnalyticsRepository,
        private val preferences: UserPreferences,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AnalyticsViewModel(repository, preferences) as T
    }

    private companion object {
        const val WORKERS = 2
        const val ROOM_CANDIDATES = 6
    }
}
