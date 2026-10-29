package com.rassvet.lecturial.ui

import android.app.Activity
import android.os.Bundle
import com.rassvet.lecturial.LecturialApplication
import com.rassvet.lecturial.MainActivity

/**
 * Where the browser returns after the Microsoft sign-in ("<application id>://oauth"). It
 * hands the answer to the sync, which finishes signing in in the background, and goes back
 * to the Outlook settings.
 */
class OutlookAuthActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { (application as LecturialApplication).outlook.completeSignIn(it) }
        startActivity(MainActivity.openOutlookIntent(this))
        finish()
    }
}
