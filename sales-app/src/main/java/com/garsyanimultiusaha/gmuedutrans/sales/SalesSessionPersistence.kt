package com.garsyanimultiusaha.gmuedutrans.sales

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object SalesSessionStore {
    private const val PREF = "gmu_sales_private_session"
    private const val KEY_REFRESH = "refresh_token"

    fun save(context: Context, refreshToken: String) {
        if (refreshToken.isBlank()) return
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_REFRESH, refreshToken).apply()
    }

    fun read(context: Context): String = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_REFRESH, "").orEmpty()

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

class SalesSessionRestoreApi {
    private val base = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun refresh(refreshToken: String): SalesSession = withContext(Dispatchers.IO) {
        if (refreshToken.isBlank()) throw IllegalStateException("No saved session")
        val payload = JSONObject().put("refresh_token", refreshToken).toString()
        val auth = JSONObject(request("POST", "/auth/v1/token?grant_type=refresh_token", payload, null))
        val access = auth.getString("access_token")
        val refreshed = auth.optString("refresh_token", refreshToken)
        val userId = auth.getJSONObject("user").getString("id")
        val profile = getProfile(access, userId)
        if (!profile.active || profile.role != "Sales") throw IllegalStateException("Saved session is not an active Sales account")
        SalesSession(access, refreshed, userId, profile)
    }

    private fun getProfile(accessToken: String, userId: String): SalesProfile {
        val id = URLEncoder.encode(userId, "UTF-8")
        val arr = JSONArray(request("GET", "/rest/v1/profiles?select=id,full_name,role,is_active&id=eq.$id&limit=1", null, accessToken))
        if (arr.length() == 0) throw IllegalStateException("Profil Sales tidak ditemukan")
        val x = arr.getJSONObject(0)
        return SalesProfile(x.getString("id"), x.optString("full_name", "Sales GMU"), x.optString("role", ""), x.optBoolean("is_active", false))
    }

    private fun request(method: String, path: String, body: String?, accessToken: String?): String {
        val c = (URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("apikey", key)
            setRequestProperty("Authorization", "Bearer ${accessToken ?: key}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = if (stream == null) "" else BufferedReader(InputStreamReader(stream)).use { it.readText() }
        c.disconnect()
        if (code !in 200..299) throw IllegalStateException("Saved session expired")
        return text.ifBlank { "{}" }
    }
}
