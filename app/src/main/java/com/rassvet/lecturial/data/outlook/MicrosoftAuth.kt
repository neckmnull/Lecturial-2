package com.rassvet.lecturial.data.outlook

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Access to a Microsoft account: the access token for Graph and the token that renews it. */
data class MicrosoftTokens(val accessToken: String, val refreshToken: String, val expiresAtMillis: Long)

/**
 * Sign-in with a Microsoft account through the browser: the OAuth 2.0 authorization code
 * flow with PKCE for a public client, so the app keeps no secret. Works for personal
 * accounts and for work or school accounts whose organisation allows the app.
 */
class MicrosoftAuth(
    private val clientId: String,
    private val http: OkHttpClient,
    private val authority: String = AUTHORITY,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun authorizeUrl(redirectUri: String, state: String, codeChallenge: String): String = buildString {
        append("$authority/authorize?client_id=").append(encode(clientId))
        append("&response_type=code")
        append("&redirect_uri=").append(encode(redirectUri))
        append("&response_mode=query")
        append("&scope=").append(encode(SCOPES))
        append("&state=").append(encode(state))
        append("&code_challenge=").append(encode(codeChallenge))
        append("&code_challenge_method=S256")
        append("&prompt=select_account")
    }

    suspend fun exchangeCode(code: String, redirectUri: String, codeVerifier: String): MicrosoftTokens = token(
        "grant_type" to "authorization_code",
        "code" to code,
        "redirect_uri" to redirectUri,
        "code_verifier" to codeVerifier,
    )

    suspend fun refresh(refreshToken: String): MicrosoftTokens = token(
        "grant_type" to "refresh_token",
        "refresh_token" to refreshToken,
    ).let { it.copy(refreshToken = it.refreshToken.ifEmpty { refreshToken }) }

    private suspend fun token(vararg fields: Pair<String, String>): MicrosoftTokens = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().add("client_id", clientId).add("scope", SCOPES)
        fields.forEach { (name, value) -> form.add(name, value) }
        val request = Request.Builder().url("$authority/token").post(form.build()).build()
        val body = try {
            http.newCall(request).execute().use { it.body.string() }
        } catch (failure: IOException) {
            throw IOException("Не удалось связаться с Microsoft. Проверьте интернет", failure)
        }
        val json = runCatching { JSONObject(body) }.getOrElse { throw IOException("Microsoft вернул некорректный ответ") }
        if (json.has("error")) {
            throw MicrosoftAuthException(json.optString("error"), json.optString("error_description").lineSequence().firstOrNull().orEmpty())
        }
        MicrosoftTokens(
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token"),
            // A minute early, so a token never expires in the middle of a request.
            expiresAtMillis = now() + (json.optLong("expires_in", 3600) - 60) * 1000,
        )
    }

    companion object {
        const val AUTHORITY = "https://login.microsoftonline.com/common/oauth2/v2.0"
        const val SCOPES = "offline_access User.Read Calendars.ReadWrite"

        /** The redirect registered for the app: "<application id>://oauth". */
        fun redirectUri(context: Context): String = "${context.packageName}://oauth"

        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    }
}

class MicrosoftAuthException(val code: String, description: String) :
    IOException(description.ifBlank { "Microsoft отклонил вход ($code)" })

/** PKCE (RFC 7636): a random verifier and its S256 challenge. */
object Pkce {
    fun verifier(random: SecureRandom = SecureRandom()): String =
        base64Url(ByteArray(32).also(random::nextBytes))

    fun challenge(verifier: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    private fun base64Url(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/**
 * Keeps the Microsoft tokens encrypted with a key that never leaves the Android Keystore;
 * the files the app stores hold only ciphertext.
 */
internal class TokenVault(context: Context) {
    private val prefs = context.getSharedPreferences("outlook_auth", Context.MODE_PRIVATE)

    fun load(): MicrosoftTokens? = runCatching {
        val stored = prefs.getString(KEY_TOKENS, null) ?: return null
        val json = JSONObject(decrypt(stored))
        MicrosoftTokens(json.getString("access"), json.getString("refresh"), json.getLong("expires"))
    }.getOrNull()

    fun save(tokens: MicrosoftTokens) {
        val json = JSONObject()
            .put("access", tokens.accessToken)
            .put("refresh", tokens.refreshToken)
            .put("expires", tokens.expiresAtMillis)
        prefs.edit().putString(KEY_TOKENS, encrypt(json.toString())).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_TOKENS).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val sealed = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_BYTES))
        }
        return String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "lecturial_outlook_tokens"
        const val KEY_TOKENS = "tokens"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
