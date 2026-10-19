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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    entities = [ScheduleEventEntity::class, WeekCacheEntity::class, StudyPlanEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class LecturialDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun studyPlanDao(): StudyPlanDao

    companion object {
        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `study_plans` (
                        `id` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `dueDate` TEXT,
                        `isCompleted` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `study_plans` ADD COLUMN `dueTime` TEXT")
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `study_plans` ADD COLUMN `endDate` TEXT")
                db.execSQL("ALTER TABLE `study_plans` ADD COLUMN `endTime` TEXT")
                db.execSQL("ALTER TABLE `study_plans` ADD COLUMN `isAllDay` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `study_plans` ADD COLUMN `recurrence` TEXT NOT NULL DEFAULT 'none'")
                db.execSQL("ALTER TABLE `study_plans` ADD COLUMN `reminderMinutesBefore` INTEGER")
                db.execSQL("ALTER TABLE `study_plans` ADD COLUMN `color` INTEGER NOT NULL DEFAULT -7689217")
                db.execSQL("ALTER TABLE `study_plans` ADD COLUMN `todoistId` TEXT")
                db.execSQL("ALTER TABLE `study_plans` ADD COLUMN `updatedAtMillis` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `study_plans` SET `updatedAtMillis` = `createdAtMillis` WHERE `updatedAtMillis` = 0")
            }
        }

        fun create(context: Context): LecturialDatabase = Room.databaseBuilder(
            context,
            LecturialDatabase::class.java,
            "lecturial.db",
        ).addMigrations(migration1To2, migration2To3, MIGRATION_3_4).build()
    }
}
