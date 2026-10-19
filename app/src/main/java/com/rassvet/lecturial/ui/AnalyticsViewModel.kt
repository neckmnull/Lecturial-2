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
import com.rassvet.lecturial.model.intradayRoomLoad
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

enum class AnalyticsTab(val title: String) { FreeTime("Общее время"), Load("Нагрузка"), Rooms("Аудитории") }

enum class FreeTimeRange(val title: String) { ThisWeek("Эта неделя"), NextWeek("Следующая"), TwoWeeks("2 недели") }

/** A teacher or group taking part in an analysis; [key] is a schedule key. */
data class AnalyticsParticipant(val key: String, val label: String)

data class FreeTimeResult(
    val recommended: List<SlotAvailability>,
    val freeByDay: Map<LocalDate, List<PairSlot>>,
)

data class LoadResult(val target: AnalyticsParticipant, val range: ClosedRange<LocalDate>, val stats: OccupancyStats)

data class RoomsResult(
    val range: ClosedRange<LocalDate>,
    val loads: List<RoomLoad>,
    val intraday: List<Pair<PairSlot, Double>>,
)

data class AnalyticsProgress(val text: String, val done: Int, val total: Int) {
    val fraction: Float get() = if (total <= 0) 0f else done.toFloat() / total
}

data class AnalyticsUiState(
    val tab: AnalyticsTab = AnalyticsTab.FreeTime,
    val searchMode: ScheduleSearchMode = ScheduleSearchMode.Teacher,
    val query: String = "",
    val isSearching: Boolean = false,
    val teacherResults: List<TeacherInfo> = emptyList(),
    val groupResults: List<String> = emptyList(),
    val specialistOnly: Boolean = true,
    val searchError: String? = null,
    val participants: List<AnalyticsParticipant> = emptyList(),
    val freeRange: FreeTimeRange = FreeTimeRange.ThisWeek,
    val freeResult: FreeTimeResult? = null,
    val loadTarget: AnalyticsParticipant? = null,
    val loadPeriod: AnalyticsPeriod = AnalyticsPeriod.Week,
    val loadResult: LoadResult? = null,
    val roomPeriod: AnalyticsPeriod = AnalyticsPeriod.Week,
    val roomsResult: RoomsResult? = null,
    val progress: AnalyticsProgress? = null,
    val error: String? = null,
) {
    val isBusy: Boolean get() = progress != null

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
            _state.update {
                it.copy(
                    participants = listOf(me),
                    loadTarget = me,
                    searchMode = if (ScheduleTarget.isTeacher(key)) ScheduleSearchMode.Teacher else ScheduleSearchMode.Group,
                    specialistOnly = ScheduleTarget.isTeacher(key),
                )
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
        state.copy(participants = state.participants.filterNot { it.key == key }, freeResult = null)
    }

    fun setLoadTarget(participant: AnalyticsParticipant) = _state.update {
        it.copy(loadTarget = participant, loadResult = null, query = "", teacherResults = emptyList(), groupResults = emptyList())
    }

    fun setFreeRange(range: FreeTimeRange) = _state.update { it.copy(freeRange = range, freeResult = null) }

    fun setLoadPeriod(period: AnalyticsPeriod) = _state.update { it.copy(loadPeriod = period, loadResult = null) }

    fun setRoomPeriod(period: AnalyticsPeriod) = _state.update { it.copy(roomPeriod = period, roomsResult = null) }

    fun cancel() {
        workJob?.cancel()
        _state.update { it.copy(progress = null) }
    }

    /** Requirements 2 and 5: pairs when every chosen teacher or group is free. */
    fun findFreeTime() {
        val participants = _state.value.participants
        if (participants.size < 1) return
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
            )
            _state.update { it.copy(freeResult = result) }
        }
    }

    /** Requirement 3: occupancy of a teacher or group over a week, month or semester. */
    fun computeLoad() {
        val target = _state.value.loadTarget ?: return
        val range = _state.value.loadPeriod.range(today())
        runWork { report ->
            val weeks = com.rassvet.lecturial.model.weekStartsOf(range).size
            val done = AtomicInteger(0)
            report(AnalyticsProgress("Загружаем недели", 0, weeks))
            val lessons = repository.lessons(target.key, range.start, range.endInclusive, today()) {
                report(AnalyticsProgress("Загружаем недели", done.incrementAndGet(), weeks))
            }
            _state.update { it.copy(loadResult = LoadResult(target, range, occupancyStats(lessons, range))) }
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
    }
}
