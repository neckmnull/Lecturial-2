package com.rassvet.lecturial.data.network

import com.rassvet.lecturial.model.AppUpdateState
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.nio.file.Files

class GitHubUpdateCheckerTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `reports newer stable GitHub release`() = runBlocking {
        server.enqueue(release("v1.5.0"))
        val checker = checker()

        assertEquals(
            AppUpdateState.Available(
                "1.5.0",
                "https://github.com/neckmnull/Lecturial-2/releases/tag/v1.5.0",
                server.url("downloads/v1.5.0/Lecturial-2-v1.5.0.apk").toString(),
                "Lecturial-2-v1.5.0.apk",
            ),
            checker.check("1.4.5-debug"),
        )
        val request = server.takeRequest()
        assertEquals("application/vnd.github+json", request.getHeader("Accept"))
        assertTrue(request.getHeader("User-Agent").orEmpty().startsWith("Lecturial-2/1.4.5-debug"))
    }

    @Test
    fun `reports current app as up to date`() = runBlocking {
        server.enqueue(release("v1.4.5"))

        assertEquals(AppUpdateState.UpToDate("1.4.5"), checker().check("1.4.5"))
    }

    @Test
    fun `release notes can make older versions update`() = runBlocking {
        server.enqueue(release("v1.11.0", notes = "Что нового\n<!-- lecturial-min-version: 1.11.0 -->"))
        assertTrue((checker().check("1.10.1") as AppUpdateState.Available).required)

        // A version that is old but still allowed gets an ordinary update.
        server.enqueue(release("v1.12.0", notes = "<!-- lecturial-min-version: v1.10.0 -->"))
        assertEquals(false, (checker().check("1.10.1") as AppUpdateState.Available).required)
    }

    @Test
    fun `the hidden version line is found in the notes`() {
        assertEquals("1.11.0", requiredMinVersion("text <!-- lecturial-min-version: 1.11.0 --> more"))
        assertEquals("2.0", requiredMinVersion("<!--lecturial-min-version:v2.0-->"))
        assertEquals(null, requiredMinVersion("Обычный релиз"))
    }

    @Test
    fun `compares numeric version segments`() {
        assertTrue(compareVersions("1.10.0", "1.9.9") > 0)
        assertTrue(compareVersions("2.0", "1.99.99") > 0)
        assertEquals(0, compareVersions("v1.4.5", "1.4.5-debug"))
    }

    @Test
    fun `explains GitHub rate limit`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("rate limited"))

        val failure = assertThrows(IOException::class.java) {
            runBlocking { checker().check("1.4.5") }
        }

        assertEquals("Лимит запросов GitHub исчерпан. Повторите проверку позже", failure.message)
    }

    @Test
    fun `explains that no stable releases exist`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))

        val failure = assertThrows(IOException::class.java) {
            runBlocking { checker().check("1.4.5") }
        }

        assertEquals("На GitHub пока нет опубликованных стабильных релизов", failure.message)
    }

    @Test
    fun `rejects malformed GitHub response`() {
        server.enqueue(MockResponse().setBody("not-json"))

        val failure = assertThrows(IOException::class.java) {
            runBlocking { checker().check("1.4.5") }
        }

        assertEquals("GitHub вернул некорректный ответ", failure.message)
    }

    @Test
    fun `rejects untrusted release URL`() {
        server.enqueue(
            MockResponse().setBody(
                """{"tag_name":"v1.5.0","html_url":"https://example.com/app.apk"}""",
            ),
        )

        assertThrows(IOException::class.java) {
            runBlocking { checker().check("1.4.5") }
        }
    }

    @Test
    fun `rejects newer release without trusted apk asset`() {
        server.enqueue(
            MockResponse().setBody(
                """{
                  "tag_name":"v1.5.0",
                  "html_url":"https://github.com/neckmnull/Lecturial-2/releases/tag/v1.5.0",
                  "assets":[{
                    "name":"Lecturial-2-v1.5.0.apk",
                    "browser_download_url":"https://example.com/Lecturial-2-v1.5.0.apk"
                  }]
                }""",
            ),
        )

        val failure = assertThrows(IOException::class.java) {
            runBlocking { checker().check("1.4.5") }
        }

        assertEquals("В релизе v1.5.0 не найден APK-файл", failure.message)
    }

    @Test
    fun `downloads apk atomically and reports progress`() = runBlocking {
        val apkBytes = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 1, 2, 3, 4)
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/vnd.android.package-archive")
                .setBody(okio.Buffer().write(apkBytes)),
        )
        val directory = Files.createTempDirectory("lecturial-update-test").toFile()
        val destination = directory.resolve("update.apk")
        val update = AppUpdateState.Available(
            "1.5.0",
            "https://github.com/neckmnull/Lecturial-2/releases/tag/v1.5.0",
            server.url("downloads/v1.5.0/update.apk").toString(),
            "update.apk",
        )
        val progress = mutableListOf<Int?>()

        try {
            assertEquals(destination, checker().downloadApk(update, destination, progress::add))
            assertTrue(destination.readBytes().contentEquals(apkBytes))
            assertTrue(progress.contains(100))
            assertEquals(false, directory.resolve("update.apk.part").exists())
        } finally {
            directory.deleteRecursively()
        }
        Unit
    }

    private fun checker() = GitHubUpdateChecker(
        server.url("latest").toString(),
        OkHttpClient(),
        server.url("downloads/").toString(),
    )

    private fun release(version: String, notes: String = "") = MockResponse().setBody(
        """
        {
          "tag_name": "$version",
          "body": ${org.json.JSONObject.quote(notes)},
          "html_url": "https://github.com/neckmnull/Lecturial-2/releases/tag/$version",
          "draft": false,
          "prerelease": false,
          "assets": [{
            "name": "Lecturial-2-$version.apk",
            "browser_download_url": "${server.url("downloads/$version/Lecturial-2-$version.apk")}"
          }]
        }
        """.trimIndent(),
    )
}