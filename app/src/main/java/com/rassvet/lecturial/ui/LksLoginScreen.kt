package com.rassvet.lecturial.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.rassvet.lecturial.data.lks.LksClient

/**
 * The personal account's own sign-in page in a WebView. The password goes to the site only;
 * once the browser lands past the sign-in form, the site's cookies are handed to [onSignedIn].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun LksLoginScreen(onSignedIn: (cookies: String) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var loading by remember { mutableStateOf(true) }
    var done by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)
    // The sign-in page stays out of screenshots, screen recordings and the recent apps preview.
    val window = LocalActivity.current?.window
    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Закрыть") }
                    Column(Modifier.weight(1f)) {
                        Text("Вход в личный кабинет", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
                            Text(
                                "Пароль вводится на сайте университета, приложение его не видит",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (loading) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        Box(Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    CookieManager.getInstance().setAcceptCookie(true)
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        // No taps through an overlay another app draws over the password form.
                        filterTouchesWhenObscured = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val url = request.url
                                if (!request.isForMainFrame || (url.scheme == "https" && isUniversityHost(url.host))) return false
                                // Other sites open in the browser, away from the sign-in page and its cookies.
                                if (url.scheme == "https" || url.scheme == "http") {
                                    runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, url)) }
                                }
                                return true
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                loading = true
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                loading = false
                                val address = url.orEmpty()
                                if (address.toUri().host != LksClient.BASE.toUri().host || done) return
                                if (address.contains("/login") || address.contains("password")) {
                                    // "Remember me" keeps the session for weeks instead of hours.
                                    view.evaluateJavascript(
                                        "(function(){var r=document.querySelector('input[name=remember]');if(r){r.checked=true;}})()",
                                        null,
                                    )
                                    return
                                }
                                val cookies = CookieManager.getInstance().getCookie(LksClient.BASE).orEmpty()
                                if (cookies.isNotBlank()) {
                                    done = true
                                    // The pages seen after sign-in hold the student's data; none of it stays in the WebView.
                                    view.clearCache(true)
                                    view.clearHistory()
                                    onSignedIn(cookies)
                                }
                            }
                        }
                        loadUrl(LksClient.LOGIN_URL)
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}

/** The university's own sites: the account's pages may move between them while signing in. */
private fun isUniversityHost(host: String?): Boolean =
    host != null && (host == UNIVERSITY_DOMAIN || host.endsWith(".$UNIVERSITY_DOMAIN"))

private const val UNIVERSITY_DOMAIN = "siriusuniversity.ru"
