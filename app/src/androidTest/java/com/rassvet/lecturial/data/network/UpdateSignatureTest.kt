package com.rassvet.lecturial.data.network

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The update check reads an APK file as the installer will; it must take the app's own APK and refuse another app's. */
class UpdateSignatureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun theAppsOwnApkPasses() {
        assertTrue(isSameAppUpdate(context, File(context.packageCodePath)))
    }

    @Test
    fun anotherPackageIsRefused() {
        // Signed with the same debug key, but it is the test APK, not the app.
        assertFalse(isSameAppUpdate(context, File(instrumentation.context.packageCodePath)))
    }
}
