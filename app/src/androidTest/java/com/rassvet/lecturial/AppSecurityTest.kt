package com.rassvet.lecturial

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.security.NetworkSecurityPolicy
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rassvet.lecturial.ui.OutlookAuthActivity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The promises of docs/SECURITY.md, read from the installed app as Android sees it. */
@RunWith(AndroidJUnit4::class)
class AppSecurityTest {
    private val app = testApp
    private val packageManager = app.packageManager
    private val authority = "${app.packageName}.fileprovider"

    @Test
    fun backupsAreOffAndPlainHttpIsForbidden() {
        assertEquals(0, app.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)
        assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("lks.siriusuniversity.ru"))
    }

    @Test
    fun onlyTheExpectedPartsOfTheAppAreOpenToOthers() {
        val info = packageManager.getPackageInfo(
            app.packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS,
        )
        val open = listOfNotNull(info.activities, info.receivers, info.services, info.providers).flatMap { it.asList() }
            .filter { it.exported && it.name.startsWith("com.rassvet.lecturial") }
            .map { it.name }
            .toSet()
        val expected = setOf(
            "com.rassvet.lecturial.MainActivity",
            "com.rassvet.lecturial.ui.OutlookAuthActivity",
            "com.rassvet.lecturial.notify.BootCompletedReceiver",
            // Exists in debug builds only, for the tests themselves.
            "com.rassvet.lecturial.ui.ComposeTestActivity",
        )
        assertEquals("Open to other apps beyond the list", emptySet<String>(), open - expected)
    }

    @Test
    fun theAppAsksForNoPermissionItDoesNotUse() {
        val requested = packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()

        assertFalse("android.permission.VIBRATE" in requested)
        assertFalse("android.permission.WAKE_LOCK" in requested)
        assertFalse("android.permission.READ_EXTERNAL_STORAGE" in requested)
        assertFalse("android.permission.ACCESS_FINE_LOCATION" in requested)
        assertTrue("android.permission.REQUEST_INSTALL_PACKAGES" in requested)
    }

    @Test
    fun onlyTheUpdateFolderCanBeHandedToTheInstaller() {
        val update = File(app.cacheDir, "updates/Lecturial-2-1.0.apk")
        assertEquals("content", FileProvider.getUriForFile(app, authority, update).scheme)

        listOf(
            File(app.filesDir, "lks/snapshot.json"),
            File(app.filesDir.parentFile, "shared_prefs/lks.xml"),
            File(app.getDatabasePath("lecturial.db").path),
            File(app.cacheDir, "updates/../../files/lks/snapshot.json"),
        ).forEach { secret ->
            assertThrows(secret.path, IllegalArgumentException::class.java) { FileProvider.getUriForFile(app, authority, secret) }
        }
    }

    @Test
    fun theMicrosoftRedirectComesBackToTheApp() {
        val redirect = Intent(Intent.ACTION_VIEW, Uri.parse("${app.packageName}://oauth?code=x"))
            .addCategory(Intent.CATEGORY_BROWSABLE)

        val target = packageManager.resolveActivity(redirect, PackageManager.MATCH_DEFAULT_ONLY)

        assertEquals(OutlookAuthActivity::class.java.name, target?.activityInfo?.name)
    }
}
