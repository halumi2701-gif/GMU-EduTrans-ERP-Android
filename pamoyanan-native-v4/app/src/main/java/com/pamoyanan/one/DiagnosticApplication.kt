package com.pamoyanan.one

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class DiagnosticApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val report = buildString {
                    appendLine("PAMOYANAN ONE V20 DIAGNOSTIC")
                    appendLine("Thread: " + thread.name)
                    appendLine("Error: " + throwable.javaClass.name)
                    appendLine("Message: " + (throwable.message ?: "-"))
                    appendLine()
                    append(sw.toString())
                }
                getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_REPORT, report)
                    .commit()

                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("diagnostic_relaunch", true)
                }
                val pending = PendingIntent.getActivity(
                    this,
                    9042,
                    intent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarm = getSystemService(ALARM_SERVICE) as AlarmManager
                alarm.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 900L, pending)
            } catch (_: Throwable) {
            }

            if (previous != null) previous.uncaughtException(thread, throwable)
            else exitProcess(10)
        }
    }

    companion object {
        const val PREFS = "pamoyanan_v20_diagnostic"
        const val KEY_REPORT = "last_fatal_report"
    }
}
