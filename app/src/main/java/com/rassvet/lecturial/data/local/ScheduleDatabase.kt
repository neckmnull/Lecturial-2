package com.rassvet.lecturial.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "schedule_events", primaryKeys = ["groupName", "id"])
data class ScheduleEventEntity(
    val groupName: String,
    val id: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val pairNumber: Int?,
    val discipline: String,
    val type: String,
    val classroom: String?,
    val address: String?,
    val teachers: String,
    val comment: String?,
    val onlineUrl: String?,
)

@Entity(tableName = "week_cache", primaryKeys = ["groupName", "weekStart"])
data class WeekCacheEntity(
    val groupName: String,
    val weekStart: String,
    val updatedAtMillis: Long,
)

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_events WHERE groupName = :group AND date BETWEEN :start AND :end ORDER BY date, startTime, pairNumber")
    suspend fun eventsForWeek(group: String, start: String, end: String): List<ScheduleEventEntity>

    @Query("SELECT * FROM schedule_events WHERE groupName = :group AND date = :date ORDER BY startTime, pairNumber")
    suspend fun eventsForDay(group: String, date: String): List<ScheduleEventEntity>

    @Query("SELECT * FROM week_cache WHERE groupName = :group AND weekStart = :weekStart LIMIT 1")
    suspend fun weekCache(group: String, weekStart: String): WeekCacheEntity?

    @Query("DELETE FROM schedule_events WHERE groupName = :group AND date BETWEEN :start AND :end")
    suspend fun deleteWeek(group: String, start: String, end: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<ScheduleEventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeekCache(cache: WeekCacheEntity)
}

@Database(
    entities = [ScheduleEventEntity::class, WeekCacheEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LecturialDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        fun create(context: Context): LecturialDatabase = Room.databaseBuilder(
            context,
            LecturialDatabase::class.java,
            "lecturial.db",
        ).build()
    }
}
