package com.garsyanimultiusaha.gmuedutrans.erp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object PushNotifications {
    const val CHANNEL_ID = "gmu_critical_decisions"
    private const val TOKEN_PREFS = "gmu_push_device"
    private const val TOKEN_KEY = "fcm_token"
    private const val SESSION_PREFS = "gmu_native_session_secure"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionPrefs: SharedPreferences? = null
    private var sessionListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var cachedAccessToken: String? = null

    fun isConfigured(): Boolean =
        BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
            BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
            BuildConfig.FIREBASE_SENDER_ID.isNotBlank()

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        createNotificationChannel(appContext)
        observeSession(appContext)

        if (!isConfigured()) return
        ensureFirebase(appContext) ?: return

        runCatching { FirebaseMessaging.getInstance().isAutoInitEnabled = true }
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful && !task.result.isNullOrBlank()) {
                onNewToken(appContext, task.result)
            }
        }
    }

    fun onNewToken(context: Context, token: String) {
        if (token.isBlank()) return
        context.applicationContext
            .getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(TOKEN_KEY, token)
            .apply()

        currentAccessToken(context.applicationContext)?.let { access ->
            registerDevice(context.applicationContext, access, token)
        }
    }

    fun storedToken(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
            .getString(TOKEN_KEY, null)
            ?.takeIf { it.isNotBlank() }

    private fun ensureFirebase(context: Context): FirebaseApp? {
        FirebaseApp.getApps(context).firstOrNull()?.let { return it }
        return runCatching {
            val options = FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .build()
            FirebaseApp.initializeApp(context, options)
        }.getOrNull()
    }

    private fun observeSession(context: Context) {
        if (sessionPrefs != null) return
        val prefs = openSessionPrefs(context) ?: return
        sessionPrefs = prefs
        cachedAccessToken = prefs.getString("access", null)?.takeIf { it.isNotBlank() }

        sessionListener = SharedPreferences.OnSharedPreferenceChangeListener { shared, _ ->
            val previous = cachedAccessToken
            val current = shared.getString("access", null)?.takeIf { it.isNotBlank() }
            cachedAccessToken = current

            if (current != null) {
                storedToken(context)?.let { registerDevice(context, current, it) }
            } else if (previous != null) {
                storedToken(context)?.let { deactivateDevice(previous, it) }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(sessionListener)

        cachedAccessToken?.let { access ->
            storedToken(context)?.let { registerDevice(context, access, it) }
        }
    }

    private fun openSessionPrefs(context: Context): SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            SESSION_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()

    private fun currentAccessToken(context: Context): String? {
        cachedAccessToken?.takeIf { it.isNotBlank() }?.let { return it }
        val prefs = sessionPrefs ?: openSessionPrefs(context).also { sessionPrefs = it }
        return prefs?.getString("access", null)?.takeIf { it.isNotBlank() }
    }

    private fun registerDevice(context: Context, accessToken: String, token: String) {
        scope.launch {
            val deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .trim()
                .take(200)
            callDeviceControl(
                accessToken,
                JSONObject()
                    .put("action", "register")
                    .put("token", token)
                    .put("device_name", deviceName)
                    .put("app_version", BuildConfig.VERSION_NAME)
                    .toString()
            )
        }
    }

    private fun deactivateDevice(accessToken: String, token: String) {
        scope.launch {
            callDeviceControl(
                accessToken,
                JSONObject()
                    .put("action", "deactivate")
                    .put("token", token)
                    .toString()
            )
        }
    }

    private fun callDeviceControl(accessToken: String, body: String) {
        runCatching {
            val conn = (URL(BuildConfig.SUPABASE_URL + "/functions/v1/gmu-push-device-control")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 15000
                setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            stream?.use { BufferedReader(InputStreamReader(it)).readText() }
            conn.disconnect()
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Keputusan Kritis ERP",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Approval, eskalasi, dan keputusan penting GMU EduTrans ERP"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }
}
