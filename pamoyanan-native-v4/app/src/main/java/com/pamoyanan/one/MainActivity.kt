package com.pamoyanan.one

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pamoyanan.one.ui.PamoyananApp
import com.pamoyanan.one.ui.theme.PamoyananTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val report = getSharedPreferences(DiagnosticApplication.PREFS, MODE_PRIVATE)
            .getString(DiagnosticApplication.KEY_REPORT, null)

        if (!report.isNullOrBlank()) {
            showDiagnostic(report)
            return
        }

        enableEdgeToEdge()
        setContent {
            PamoyananTheme {
                PamoyananApp()
            }
        }
    }

    private fun showDiagnostic(report: String) {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.rgb(255, 247, 247))
        }

        val title = TextView(this).apply {
            text = "PAMOYANAN ONE V20 — CRASH DIAGNOSTIC"
            textSize = 18f
            setTextColor(Color.rgb(150, 20, 20))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, pad, 0, pad)
        }
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val copy = Button(this).apply {
            text = "SALIN ERROR"
            setOnClickListener {
                val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("V20 crash report", report))
                Toast.makeText(this@MainActivity, "Error disalin", Toast.LENGTH_SHORT).show()
            }
        }
        val retry = Button(this).apply {
            text = "HAPUS & COBA LAGI"
            setOnClickListener {
                getSharedPreferences(DiagnosticApplication.PREFS, MODE_PRIVATE)
                    .edit().remove(DiagnosticApplication.KEY_REPORT).commit()
                recreate()
            }
        }
        actions.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(retry, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(actions)

        val text = TextView(this).apply {
            this.text = report
            textSize = 11f
            setTextColor(Color.BLACK)
            setTextIsSelectable(true)
            setPadding(0, pad, 0, pad)
        }
        val scroll = ScrollView(this).apply { addView(text) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }
}
