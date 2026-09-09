package site.garsyanimultiusaha.gawone.mitra

import android.content.Context
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

internal data class Stage4ICrashNotice(
    val timestampMs: Long,
    val exceptionType: String,
    val fingerprint: String
)

internal object Stage4ICrashShield {
    private const val PREF = "gawone_stage4i_crash_recovery"
    private const val KEY_TS = "last_crash_ts"
    private const val KEY_TYPE = "last_crash_type"
    private const val KEY_FP = "last_crash_fingerprint"
    private val installed = AtomicBoolean(false)

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return

        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val fingerprintSource = buildString {
                    append(error.javaClass.name)
                    error.stackTrace.take(12).forEach {
                        append('|')
                        append(it.className)
                        append('#')
                        append(it.methodName)
                        append(':')
                        append(it.lineNumber)
                    }
                }
                app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_TS, System.currentTimeMillis())
                    .putString(KEY_TYPE, error.javaClass.simpleName.take(80))
                    .putString(KEY_FP, sha256(fingerprintSource).take(16))
                    .commit()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun consumeNotice(context: Context): Stage4ICrashNotice? {
        val prefs = context.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val ts = prefs.getLong(KEY_TS, 0L)
        if (ts <= 0L) return null

        val notice = Stage4ICrashNotice(
            timestampMs = ts,
            exceptionType = prefs.getString(KEY_TYPE, "RuntimeError").orEmpty(),
            fingerprint = prefs.getString(KEY_FP, "unknown").orEmpty()
        )
        prefs.edit().clear().apply()
        return notice
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
