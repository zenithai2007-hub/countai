package com.zenithai.countai.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.zenithai.countai.util.CrashHandler

/**
 * Plain, dependency-free screen that shows the last crash's stack trace.
 * Kept deliberately simple (no view binding, no fragments) so it can open
 * even when the main UI is what crashed.
 *
 * The user screenshots this or taps "Copy" and pastes it to support.
 */
class CrashReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val trace = CrashHandler.readLatest(this)
            ?: "No crash log found.\n\nThe app closed unexpectedly but no error was recorded."

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#141414"))
            setPadding(32, 48, 32, 32)
        }

        val title = TextView(this).apply {
            text = "CountAI stopped — what went wrong:"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(0, 0, 0, 24)
        }

        val body = TextView(this).apply {
            text = trace.take(6000)
            setTextColor(Color.parseColor("#FF9D9D"))
            textSize = 12f
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this)
        scroll.addView(body)
        scroll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )

        val copyBtn = Button(this).apply {
            text = "Copy error"
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("CountAI crash", trace.take(6000)))
                Toast.makeText(this@CrashReportActivity, "Copied — paste it to support", Toast.LENGTH_LONG).show()
            }
        }

        val closeBtn = Button(this).apply {
            text = "Close"
            setOnClickListener {
                CrashHandler.clear(this@CrashReportActivity)
                finish()
            }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
            addView(copyBtn)
            addView(closeBtn)
        }

        root.addView(title)
        root.addView(scroll)
        root.addView(row)
        setContentView(root)
    }
}
