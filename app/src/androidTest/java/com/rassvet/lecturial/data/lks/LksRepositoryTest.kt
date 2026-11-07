package com.rassvet.lecturial.data.lks

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rassvet.lecturial.eventually
import com.rassvet.lecturial.grantNotifications
import com.rassvet.lecturial.onMain
import com.rassvet.lecturial.testApp
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The personal account on a real Keystore and real files, with the site answered by the test. */
@RunWith(AndroidJUnit4::class)
class LksRepositoryTest {
    private enum class Site { Normal, Maintenance, SignedOut, Throttled }

    private val app = testApp
    private val prefs = app.getSharedPreferences("lks", Context.MODE_PRIVATE)
    private val cache = File(app.filesDir, "lks/snapshot.json")
    private val requests = AtomicInteger()
    private val firstRequest = CountDownLatch(1)

    @Volatile private var site = Site.Normal
    @Volatile private var grade: Int? = 5
    @Volatile private var gate: CountDownLatch? = null

    private val http = OkHttpClient.Builder().addInterceptor { chain ->
        requests.incrementAndGet()
        firstRequest.countDown()
        gate?.await(15, TimeUnit.SECONDS)
        val request = chain.request()
        val answered = if (site == Site.SignedOut) request.newBuilder().url(LksClient.LOGIN_URL).build() else request
        Response.Builder()
            .request(answered)
            .protocol(Protocol.HTTP_1_1)
            .code(if (site == Site.Throttled) 429 else 200)
            .message("OK")
            .apply { if (site == Site.Throttled) header("Retry-After", "600") }
            .body(page(request.url.encodedPath).toResponseBody("text/html".toMediaType()))
            .build()
    }.build()

    private fun component(name: String, data: String): String {
        val state = """{"fingerprint":{"name":"$name"},"serverMemo":{"data":$data}}""".replace("\"", "&quot;")
        return """<div wire:initial-data="$state"></div>"""
    }

    private fun page(path: String): String = when {
        site == Site.Maintenance -> "<html><body>Технические работы</body></html>"
        path == "/journal/list" -> component(
            "journal-list",
            """{"grades":{"2026/2027":{"2026-11-02":{"disciplines":{"a":{"discipline":"Физика","grades":{"m1":
            {"mark_id":"m1","grade_as_long":${grade ?: "null"},"start_date":"02.11.2026","start_time":"13:30",
            "action_type_name":"Лекции","absent":0}}}}}}}}""",
        )
        path == "/student/trajectory" -> component("trajectory", """{"semesters":{}}""")
        else -> component("user-notifications-menu", "{}")
    }

    private fun repository() = LksRepository(app) { LksClient(it, http) }

    private fun LksRepository.signIn() {
        onMain { connect("session=secret") }
        eventually("the first load") { state.value.snapshot != null && !state.value.refreshing }
    }

    /** As if the last attempt was long ago, for the checks that aren't about the limit. */
    private fun forgetLastAttempt() {
        prefs.edit().remove("last_attempt").commit()
    }

    private fun clean() {
        prefs.edit().clear().commit()
        cache.parentFile?.deleteRecursively()
        LksRepository.cancel(app)
        app.getSystemService(NotificationManager::class.java).cancel(GRADES_NOTIFICATION)
    }

    @Before
    fun setUp() = clean()

    @After
    fun tearDown() = clean()

    @Test
    fun theSessionAndTheGradesAreStoredSealed() {
        val repository = repository()
        repository.signIn()

        assertEquals(5, repository.state.value.snapshot!!.marks.single().grade)
        assertFalse(prefs.getString("cookies", null)!!.contains("secret"))
        assertFalse(cache.readText().contains("Физика"))
        // Another start of the app reads them back.
        val reopened = repository()
        assertTrue(reopened.state.value.connected)
        assertEquals("m1", reopened.state.value.snapshot!!.marks.single().id)
    }

    @Test
    fun aPlainCacheFromAnOlderVersionIsSealedOnFirstRead() {
        cache.parentFile!!.mkdirs()
        cache.writeText("""{"loadedAt":7,"unread":2,"marks":[],"semesters":[],"notifications":[],"news":[]}""")

        val snapshot = repository().state.value.snapshot!!

        assertEquals(2, snapshot.unreadNotifications)
        assertFalse("Still plain text", cache.readText().startsWith("{"))
    }

    @Test
    fun signingOutDuringARefreshLeavesNothingBehind() = runBlocking {
        val hold = CountDownLatch(1)
        gate = hold
        val repository = repository()
        onMain { repository.connect("session=secret") }
        assertTrue(firstRequest.await(15, TimeUnit.SECONDS))

        onMain { repository.disconnect() }
        hold.countDown()
        // Queued behind the running refresh, so that one has finished when this returns.
        repository.refreshNow(notify = false)

        eventually("the sign-out") { !prefs.contains("cookies") && !cache.exists() }
        assertFalse(repository.state.value.connected)
        assertNull(repository.state.value.snapshot)
        assertFalse("Back after a restart", repository().state.value.connected)
    }

    @Test
    fun signingInAgainIsNotHeldBackByTheLimit() {
        val repository = repository()
        repository.signIn()
        val firstLoad = requests.get()
        onMain { repository.disconnect() }
        eventually("the sign-out") { !prefs.contains("cookies") }

        repository.signIn()

        assertEquals(firstLoad * 2, requests.get())
    }

    @Test
    fun aManualRefreshWithinTheMinuteAsksNothingAndSaysWhy() = runBlocking {
        val repository = repository()
        repository.signIn()
        val asked = requests.get()

        repository.refreshNow(notify = false, manual = true)

        assertEquals(asked, requests.get())
        assertTrue(repository.state.value.error!!.startsWith("Обновить можно будет через"))
    }

    @Test
    fun automaticRefreshesStayQuietAndWaitLongerThanManualOnes() = runBlocking {
        val repository = repository()
        repository.signIn()
        val asked = requests.get()
        prefs.edit().putLong("last_attempt", System.currentTimeMillis() - 2 * 60_000L).commit()

        repository.refreshNow(notify = true)
        assertEquals("Two minutes are too soon for an automatic one", asked, requests.get())
        assertNull(repository.state.value.error)

        repository.refreshNow(notify = false, manual = true)
        assertTrue("A manual one may go", requests.get() > asked)
    }

    @Test
    fun openingTheAppTwiceAtOnceLoadsOnce() {
        val repository = repository()
        repository.signIn()
        forgetLastAttempt()
        val before = requests.get()

        // What onStart and the Study tab both do when the app opens.
        repository.refreshIfStale(maxAgeMillis = 0L)
        repository.refreshIfStale(maxAgeMillis = 0L)

        eventually("the refresh") { requests.get() > before && !repository.state.value.refreshing }
        Thread.sleep(500)
        assertEquals("One refresh is three pages", before + PAGES_PER_REFRESH, requests.get())
    }

    @Test
    fun aThrottledSitePausesEveryRefreshForAsLongAsItSays() = runBlocking {
        val repository = repository()
        repository.signIn()
        forgetLastAttempt()
        site = Site.Throttled

        repository.refreshNow(notify = false, manual = true)
        assertEquals("Личный кабинет просит подождать. Обновим позже", repository.state.value.error)
        assertEquals("The grades stay", "m1", repository.state.value.snapshot!!.marks.single().id)

        site = Site.Normal
        forgetLastAttempt()
        val asked = requests.get()
        repository.refreshNow(notify = true)
        repository.refreshNow(notify = false, manual = true)
        assertEquals(asked, requests.get())
        assertEquals("Обновить можно будет через 10 мин.", repository.state.value.error)

        val blockedUntil = prefs.getLong("blocked_until", 0L) - System.currentTimeMillis()
        assertTrue(blockedUntil in 590_000L..600_000L)
    }

    @Test
    fun aMaintenancePageKeepsTheGradesAndNoFalseAlarmFollows() = runBlocking {
        grantNotifications()
        val repository = repository()
        repository.signIn()
        forgetLastAttempt()
        site = Site.Maintenance

        repository.refreshNow(notify = true)

        assertEquals("m1", repository.state.value.snapshot!!.marks.single().id)
        assertTrue(repository.state.value.connected)
        assertEquals("Личный кабинет вернул неожиданную страницу", repository.state.value.error)
        val asked = requests.get()
        repository.refreshNow(notify = true)
        assertEquals("A failed attempt counts for the limit too", asked, requests.get())

        site = Site.Normal
        forgetLastAttempt()
        repository.refreshNow(notify = true)
        assertNull("The same grades aren't new", gradesNotification())
    }

    @Test
    fun aLapsedSessionAsksToSignInAndKeepsTheGrades() = runBlocking {
        val repository = repository()
        repository.signIn()
        forgetLastAttempt()
        site = Site.SignedOut

        repository.refreshNow(notify = false)

        assertFalse(repository.state.value.connected)
        assertFalse(prefs.contains("cookies"))
        assertEquals("m1", repository.state.value.snapshot!!.marks.single().id)
        assertEquals("Сессия личного кабинета истекла. Войдите заново", repository.state.value.error)
    }

    @Test
    fun aNewGradeNotifiesAndStaysOffTheLockScreen() = runBlocking {
        grantNotifications()
        assumeTrue(NotificationManagerCompat.from(app).areNotificationsEnabled())
        grade = null
        val repository = repository()
        repository.signIn()
        assertNull("The first load tells nothing", gradesNotification())
        forgetLastAttempt()
        grade = 5

        repository.refreshNow(notify = true)

        eventually("the notification") { gradesNotification() != null }
        val notification = gradesNotification()!!
        assertEquals("Новая оценка: 5", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        val public = notification.publicVersion.extras
        assertEquals("Есть новые данные", public.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertFalse(public.getCharSequence(Notification.EXTRA_TITLE).toString().contains("5"))
    }

    private fun gradesNotification(): Notification? = app.getSystemService(NotificationManager::class.java)
        .activeNotifications.firstOrNull { it.id == GRADES_NOTIFICATION }?.notification

    private companion object {
        const val GRADES_NOTIFICATION = 0x4C4B53
        const val PAGES_PER_REFRESH = 3
    }
}
