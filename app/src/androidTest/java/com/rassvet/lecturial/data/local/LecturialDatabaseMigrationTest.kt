package com.rassvet.lecturial.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LecturialDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "migration-3-4.db"
    private var database: LecturialDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migration3To4AddsExactDefaultsAndPreservesPlan() {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null).use { sqlite ->
            sqlite.execSQL(
                """
                CREATE TABLE `schedule_events` (
                    `groupName` TEXT NOT NULL, `id` TEXT NOT NULL, `date` TEXT NOT NULL,
                    `startTime` TEXT NOT NULL, `endTime` TEXT NOT NULL, `pairNumber` INTEGER,
                    `discipline` TEXT NOT NULL, `type` TEXT NOT NULL, `classroom` TEXT,
                    `address` TEXT, `teachers` TEXT NOT NULL, `comment` TEXT, `onlineUrl` TEXT,
                    PRIMARY KEY(`groupName`, `id`)
                )
                """.trimIndent(),
            )
            sqlite.execSQL(
                """
                CREATE TABLE `week_cache` (
                    `groupName` TEXT NOT NULL, `weekStart` TEXT NOT NULL,
                    `updatedAtMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`groupName`, `weekStart`)
                )
                """.trimIndent(),
            )
            sqlite.execSQL(
                """
                CREATE TABLE `study_plans` (
                    `id` TEXT NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL,
                    `note` TEXT NOT NULL, `dueDate` TEXT, `isCompleted` INTEGER NOT NULL,
                    `createdAtMillis` INTEGER NOT NULL, `dueTime` TEXT,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            sqlite.execSQL(
                "INSERT INTO `study_plans` VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("legacy", "deadline", "Старый план", "", "2026-11-12", 0, 123L, "14:25"),
            )
            sqlite.version = 3
        }

        database = Room.databaseBuilder(context, LecturialDatabase::class.java, databaseName)
            .addMigrations(LecturialDatabase.MIGRATION_3_4, LecturialDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        val sqlite = requireNotNull(database).openHelper.writableDatabase

        val defaults = buildMap<String, String?> {
            sqlite.query("PRAGMA table_info(`study_plans`)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
                while (cursor.moveToNext()) {
                    put(cursor.getString(nameIndex), cursor.getString(defaultIndex))
                }
            }
        }
        assertEquals("0", defaults["isAllDay"])
        assertEquals("'none'", defaults["recurrence"])
        assertEquals("-7689217", defaults["color"])
        assertEquals("0", defaults["updatedAtMillis"])
        assertNull(defaults["endDate"])
        assertNull(defaults["endTime"])
        assertNull(defaults["reminderMinutesBefore"])
        assertNull(defaults["todoistId"])

        val eventColumns = buildList {
            sqlite.query("PRAGMA table_info(`schedule_events`)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }
        assertEquals(true, "studentGroup" in eventColumns)

        sqlite.query("SELECT * FROM `study_plans` WHERE `id` = 'legacy'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("isAllDay")))
            assertEquals("none", cursor.getString(cursor.getColumnIndexOrThrow("recurrence")))
            assertEquals(-7689217, cursor.getInt(cursor.getColumnIndexOrThrow("color")))
            assertEquals(123L, cursor.getLong(cursor.getColumnIndexOrThrow("updatedAtMillis")))
        }
    }
}