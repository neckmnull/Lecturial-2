package com.rassvet.lecturial.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "study_plans", primaryKeys = ["id"])
data class StudyPlanEntity(
    val id: String,
    val type: String,
    val title: String,
    val note: String,
    val dueDate: String?,
    val dueTime: String?,
    val isCompleted: Boolean,
    val createdAtMillis: Long,
)

@Dao
interface StudyPlanDao {
    @Query(
        """
        SELECT * FROM study_plans
        ORDER BY isCompleted ASC,
            CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END ASC,
            dueDate ASC,
            CASE WHEN dueTime IS NULL THEN 1 ELSE 0 END ASC,
            dueTime ASC,
            createdAtMillis DESC
        """,
    )
    fun observeAll(): Flow<List<StudyPlanEntity>>

    @Query(
        """
        SELECT * FROM study_plans
        ORDER BY isCompleted ASC, dueDate ASC,
            CASE WHEN dueTime IS NULL THEN 1 ELSE 0 END ASC, dueTime ASC
        """,
    )
    suspend fun all(): List<StudyPlanEntity>

    @Query("SELECT COUNT(*) FROM study_plans WHERE isCompleted = 0")
    suspend fun activeCount(): Int

    @Query(
        """
        SELECT * FROM study_plans WHERE isCompleted = 0
         ORDER BY CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END ASC, dueDate ASC,
             CASE WHEN dueTime IS NULL THEN 1 ELSE 0 END ASC, dueTime ASC, createdAtMillis DESC
        LIMIT 1
        """,
    )
    suspend fun nearestActive(): StudyPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: StudyPlanEntity)

    @Query("UPDATE study_plans SET isCompleted = :completed WHERE id = :id")
    suspend fun setCompleted(id: String, completed: Boolean)

    @Query("DELETE FROM study_plans WHERE id = :id")
    suspend fun delete(id: String)
}