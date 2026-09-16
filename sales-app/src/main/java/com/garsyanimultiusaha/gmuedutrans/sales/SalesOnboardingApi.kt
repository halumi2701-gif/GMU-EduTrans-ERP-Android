package com.garsyanimultiusaha.gmuedutrans.sales

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SalesOnboardingApi {
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun onboardingRequired(session: SalesSession): Boolean = withContext(Dispatchers.IO) {
        val userId = URLEncoder.encode(session.userId, "UTF-8")
        val arr = JSONArray(
            request(
                method = "GET",
                path = "/rest/v1/profiles?select=onboarding_required&id=eq.$userId&limit=1",
                body = null,
                accessToken = session.accessToken
            )
        )
        if (arr.length() == 0) throw IllegalStateException("Profil Sales tidak ditemukan.")
        arr.getJSONObject(0).optBoolean("onboarding_required", false)
    }

    suspend fun completeOnboarding(session: SalesSession, newPassword: String): String = withContext(Dispatchers.IO) {
        validatePassword(newPassword)

        request(
            method = "PUT",
            path = "/auth/v1/user",
            body = JSONObject().put("password", newPassword).toString(),
            accessToken = session.accessToken
        )

        val response = request(
            method = "POST",
            path = "/rest/v1/rpc/gmu_sales_complete_onboarding",
            body = "{}",
            accessToken = session.accessToken
        )
        response.trim().trim('"')
    }

    fun validatePassword(password: String) {
        val strong = password.length >= 10 &&
            password.any(Char::isUpperCase) &&
            password.any(Char::isLowerCase) &&
            password.any(Char::isDigit) &&
            password.any { !it.isLetterOrDigit() }
        if (!strong) {
            throw IllegalArgumentException("Password baru minimal 10 karakter dan harus berisi huruf besar, huruf kecil, angka, serta simbol.")
        }
    }

    private fun request(method: String, path: String, body: String?, accessToken: String): String {
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("apikey", key)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = if (stream == null) "" else BufferedReader(InputStreamReader(stream)).use { it.readText() }
        connection.disconnect()
        if (status !in 200..299) {
            val message = runCatching { JSONObject(text).optString("message").ifBlank { JSONObject(text).optString("error") } }
                .getOrNull()
                .orEmpty()
                .ifBlank { "Onboarding Sales gagal diproses." }
            throw IllegalStateException(message)
        }
        return text.ifBlank { "{}" }
    }
}
