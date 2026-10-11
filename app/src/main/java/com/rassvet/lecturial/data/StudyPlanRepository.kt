package com.rassvet.lecturial.data

import com.rassvet.lecturial.data.local.LecturialDatabase
import com.rassvet.lecturial.data.local.StudyPlanEntity
import com.rassvet.lecturial.model.StudyPlan
import com.rassvet.lecturial.model.StudyPlanType
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StudyPlanRepository(
    private val database: LecturialDatabase,
    private val onPlansChanged: () -> Unit = {},
) {
    val plans: Flow<List<StudyPlan>> = database.studyPlanDao().observeAll().map { plans ->
        plans.map(StudyPlanEntity::toDomain)
    }

    suspend fun save(plan: StudyPlan) {
        require(plan.title.isNotBlank())
        database.studyPlanDao().upsert(plan.toEntity())
        onPlansChanged()
    }

    suspend fun setCompleted(id: String, completed: Boolean) {
        database.studyPlanDao().setCompleted(id, completed)
        onPlansChanged()
    }

    suspend fun delete(id: String) {
        database.studyPlanDao().delete(id)
        onPlansChanged()
    }

    suspend fun activeCount(): Int = database.studyPlanDao().activeCount()

    suspend fun all(): List<StudyPlan> = database.studyPlanDao().all().map(StudyPlanEntity::toDomain)
}

private fun StudyPlan.toEntity() = StudyPlanEntity(
    id = id,
    type = type.storageId,
    title = title.trim(),
    note = note.trim(),
    dueDate = dueDate?.toString(),
    dueTime = dueTime?.toString(),
    isCompleted = isCompleted,
    createdAtMillis = createdAtMillis,
)

private fun StudyPlanEntity.toDomain() = StudyPlan(
    id = id,
    type = StudyPlanType.fromStorageId(type),
    title = title,
    note = note,
    dueDate = dueDate?.let(LocalDate::parse),
    dueTime = dueTime?.let(LocalTime::parse),
    isCompleted = isCompleted,
    createdAtMillis = createdAtMillis,
)