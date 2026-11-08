package com.rassvet.lecturial

import android.app.Application
import com.rassvet.lecturial.data.ScheduleAnalyticsRepository
import com.rassvet.lecturial.data.ScheduleRepository
import com.rassvet.lecturial.data.StudyPlanRepository
import com.rassvet.lecturial.data.local.LecturialDatabase
import com.rassvet.lecturial.data.local.UserPreferences
import com.rassvet.lecturial.data.devicecalendar.DeviceCalendarSync
import com.rassvet.lecturial.data.lks.LksRepository
import com.rassvet.lecturial.data.outlook.OutlookSync
import com.rassvet.lecturial.data.network.SiriusScheduleDataSource
import com.rassvet.lecturial.data.network.GitHubUpdateChecker
import com.rassvet.lecturial.notify.LessonAlerts
import com.rassvet.lecturial.notify.DeadlineAlerts
import com.rassvet.lecturial.notify.UpdateNotifier
import com.rassvet.lecturial.widget.TodayScheduleWidget
import java.io.File
import com.rassvet.lecturial.data.local.DayPlannerStore
import kotlinx.coroutines.flow.first

class LecturialApplication : Application() {
    val preferences by lazy { UserPreferences(this) }
    val database by lazy { LecturialDatabase.create(this) }
    // Only a release build is the file on GitHub; debug and benchmark APKs always differ from it.
    val updateChecker by lazy {
        GitHubUpdateChecker(installedApk = File(applicationInfo.sourceDir).takeIf { BuildConfig.BUILD_TYPE == "release" })
    }
    val updatesDirectory by lazy { File(cacheDir, "updates") }
    val studyPlanRepository by lazy {
        StudyPlanRepository(database) {
            TodayScheduleWidget.updateAll(this)
            DeadlineAlerts.sync(this)
            outlook.requestSync()
            googleCalendar.requestSync()
        }
    }
    val analyticsRepository by lazy {
        ScheduleAnalyticsRepository(this, database, { SiriusScheduleDataSource() })
    }
    val repository by lazy {
        ScheduleRepository(
            database = database,
            remote = SiriusScheduleDataSource(),
            extracurricularChoice = { preferences.extracurricularChoice.first() },
            onCacheUpdated = {
                TodayScheduleWidget.updateAll(this)
                LessonAlerts.sync(this)
                outlook.requestSync()
                googleCalendar.requestSync()
            },
        )
    }

    /** Keeps the "Lecturial 2" calendar in Outlook in step with the schedule and plans. */
    val outlook by lazy { OutlookSync(this) }

    /** Keeps a calendar of the phone's Google account in step with the schedule and plans. */
    val googleCalendar by lazy { DeviceCalendarSync(this) }

    /** The student's personal account: grades, attendance, session, notifications and news. */
    val lks by lazy { LksRepository(this) }
    val dayPlanner by lazy { DayPlannerStore(this) }

    override fun onCreate() {
        super.onCreate()
        TodayScheduleWidget.updateAll(this)
        LessonAlerts.sync(this)
        DeadlineAlerts.sync(this)
        UpdateNotifier.schedule(this)
        UpdateNotifier.check(this, minInterval = UpdateNotifier.START_INTERVAL)
    }
}
