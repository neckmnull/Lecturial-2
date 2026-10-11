package com.rassvet.lecturial

import android.app.Application
import com.rassvet.lecturial.data.ScheduleRepository
import com.rassvet.lecturial.data.StudyPlanRepository
import com.rassvet.lecturial.data.local.LecturialDatabase
import com.rassvet.lecturial.data.local.UserPreferences
import com.rassvet.lecturial.data.network.SiriusScheduleDataSource
import com.rassvet.lecturial.data.network.GitHubUpdateChecker
import com.rassvet.lecturial.notify.LessonAlerts
import com.rassvet.lecturial.notify.DeadlineAlerts
import com.rassvet.lecturial.widget.TodayScheduleWidget
import java.io.File

class LecturialApplication : Application() {
    val preferences by lazy { UserPreferences(this) }
    val database by lazy { LecturialDatabase.create(this) }
    val updateChecker by lazy { GitHubUpdateChecker() }
    val updatesDirectory by lazy { File(cacheDir, "updates") }
    val studyPlanRepository by lazy {
        StudyPlanRepository(database) {
            TodayScheduleWidget.updateAll(this)
            DeadlineAlerts.sync(this)
        }
    }
    val repository by lazy {
        ScheduleRepository(
            database = database,
            remote = SiriusScheduleDataSource(),
            onCacheUpdated = {
                TodayScheduleWidget.updateAll(this)
                LessonAlerts.sync(this)
            },
        )
    }

    override fun onCreate() {
        super.onCreate()
        TodayScheduleWidget.updateAll(this)
        LessonAlerts.sync(this)
        DeadlineAlerts.sync(this)
    }
}
