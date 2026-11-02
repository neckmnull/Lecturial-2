package com.rassvet.lecturial.data.lks

import com.rassvet.lecturial.model.LksMark
import com.rassvet.lecturial.model.LksNews
import com.rassvet.lecturial.model.LksNotification
import com.rassvet.lecturial.model.LksSemester
import com.rassvet.lecturial.model.LksSnapshot
import com.rassvet.lecturial.model.parseLksJournal
import com.rassvet.lecturial.model.parseLksTrajectory
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** The personal account no longer accepts the saved session: the user has to sign in again. */
class LksSignedOutException : IOException("Сессия личного кабинета истекла. Войдите заново")

/**
 * Reads the student's personal account (lks.siriusuniversity.ru) with the cookies of a
 * sign-in made in a WebView. The site is Laravel with Livewire: the journal and trajectory
 * come as JSON in their components' `wire:initial-data`, the rest is parsed from the page.
 */
class LksClient(
    /** "name=value; name=value" as the WebView's cookie manager gives it. */
    cookieHeader: String,
    baseClient: OkHttpClient = SharedClient,
) {
    private val jar = SessionJar(cookieHeader)
    private val http = baseClient.newBuilder().cookieJar(jar).followRedirects(true).build()

    /** The cookies as they are now; the site renews the session on the way. */
    val cookieHeader: String get() = jar.header()

    suspend fun snapshot(nowMillis: Long = System.currentTimeMillis()): LksSnapshot = withContext(Dispatchers.IO) {
        val dashboard = page("/dashboard")
        LksSnapshot(
            marks = journal(),
            semesters = trajectory(),
            notifications = notifications(dashboard),
            unreadNotifications = unreadCount(dashboard),
            news = news(dashboard),
            loadedAtMillis = nowMillis,
        )
    }

    private fun journal(): List<LksMark> {
        val data = componentData(page("/journal/list"), "journal-list") ?: return emptyList()
        return data.optJSONObject("grades")?.let(::parseLksJournal).orEmpty()
    }

    private fun trajectory(): List<LksSemester> =
        componentData(page("/student/trajectory"), "trajectory")?.let(::parseLksTrajectory).orEmpty()

    private fun page(path: String): Document {
        val request = Request.Builder().url(BASE + path).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            // A lapsed session ends on the sign-in form.
            if (response.request.url.encodedPath.startsWith("/login")) throw LksSignedOutException()
            if (!response.isSuccessful) throw IOException("Личный кабинет ответил ${response.code}")
            return Jsoup.parse(response.body.string(), BASE)
        }
    }

    private fun componentData(document: Document, name: String): JSONObject? =
        document.select("[wire:initial-data]").asSequence()
            .mapNotNull { runCatching { JSONObject(it.attr("wire:initial-data")) }.getOrNull() }
            .firstOrNull { it.optJSONObject("fingerprint")?.optString("name") == name }
            ?.optJSONObject("serverMemo")?.optJSONObject("data")

    private fun notificationsRoot(document: Document) =
        document.select("[wire:initial-data]").firstOrNull { it.attr("wire:initial-data").contains("\"user-notifications-menu\"") }

    private fun notifications(document: Document): List<LksNotification> {
        val root = notificationsRoot(document) ?: return emptyList()
        return root.select("[wire:click^=openNotification]").mapNotNull { item ->
            val id = NotificationId.find(item.attr("wire:click"))?.groupValues?.get(1) ?: return@mapNotNull null
            val parts = item.select("div").filter { it.children().isEmpty() }.map { it.text().trim() }.filter { it.isNotEmpty() }
            LksNotification(
                id = id,
                title = parts.getOrNull(0).orEmpty(),
                text = parts.getOrNull(1).orEmpty(),
                time = parts.getOrNull(2).orEmpty(),
            )
        }.distinctBy { it.id }
    }

    private fun unreadCount(document: Document): Int {
        val root = notificationsRoot(document) ?: return 0
        return LeadingNumber.find(root.text())?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun news(document: Document): List<LksNews> =
        document.select("a[href*=/news/show/]").mapNotNull { link ->
            val id = NewsId.find(link.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
            val text = link.text().trim()
            val date = NewsDate.find(text)?.value.orEmpty()
            val title = text.replace(date, "").replace("Подробнее", "").trim()
            if (title.isEmpty()) null else LksNews(id, title, date)
        }.distinctBy { it.id }

    private class SessionJar(header: String) : CookieJar {
        private val cookies = linkedMapOf<String, Cookie>()

        init {
            val url = BASE.toHttpUrl()
            header.split(';').map { it.trim() }.filter { '=' in it }.forEach { pair ->
                val name = pair.substringBefore('=').trim()
                val value = pair.substringAfter('=').trim()
                cookies[name] = Cookie.Builder().name(name).value(value).domain(url.host).path("/").secure().build()
            }
        }

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { this.cookies[it.name] = it }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.values.filter { it.matches(url) }

        @Synchronized
        fun header(): String = cookies.values.joinToString("; ") { "${it.name}=${it.value}" }
    }

    companion object {
        const val BASE = "https://lks.siriusuniversity.ru"
        const val LOGIN_URL = "$BASE/login"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) Lecturial/2"
        private val SharedClient by lazy { OkHttpClient() }
        private val NotificationId = Regex("""openNotification\('([^']+)'\)""")
        private val NewsId = Regex("""/news/show/(\d+)""")
        private val NewsDate = Regex("""\d{4}-\d{2}-\d{2}(?: \d{2}:\d{2}(?::\d{2})?)?""")
        private val LeadingNumber = Regex("""^\s*(\d+)""")
    }
}
