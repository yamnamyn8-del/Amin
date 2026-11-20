package com.example.prayertimes

import android.app.Activity
import android.app.NotificationManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class AlertActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var end = 0L
    private var key = ""
    private val ticker = object : Runnable { override fun run() { updateCountdown(); handler.postDelayed(this, 1000) } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        if (android.os.Build.VERSION.SDK_INT >= 27) setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.statusBarColor = Color.BLACK; window.navigationBarColor = Color.BLACK
        key = intent.getStringExtra("key") ?: "manual-${System.currentTimeMillis()}"
        end = intent.getLongExtra("end", System.currentTimeMillis() + 3600000)
        val name = intent.getStringExtra("name") ?: "الصلاة"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(28, 28, 28, 28); setBackgroundColor(Color.BLACK) }
        val title = TextView(this).apply { text = "حان الآن وقت صلاة $name"; setTextColor(Color.WHITE); textSize = 34f; gravity = Gravity.CENTER; textDirection = TextView.TEXT_DIRECTION_RTL }
        val countdown = TextView(this).apply { tag = "countdown"; setTextColor(Color.rgb(233,196,106)); textSize = 38f; gravity = Gravity.CENTER; setPadding(0, 28, 0, 34) }
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val done = Button(this).apply { text = "صليت"; textSize = 18f; setOnClickListener { PrayerStore.markDone(this@AlertActivity, key); closeAlert() } }
        val later = Button(this).apply { text = "لاحقًا"; textSize = 18f; setOnClickListener { closeAlert() } }
        buttons.addView(done); buttons.addView(later)
        root.addView(title); root.addView(countdown); root.addView(buttons)
        setContentView(root)
        updateCountdown(); handler.post(ticker)
    }
    private fun updateCountdown() {
        val left = ((end - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        val h = left / 3600; val m = (left % 3600) / 60; val s = left % 60
        val view = (window.decorView as? android.view.ViewGroup)?.findViewWithTag<TextView>("countdown")
        view?.text = "الوقت المتبقي للحاق بالصلاة\n" + String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        if (left == 0L) closeAlert()
    }
    private fun closeAlert() {
        handler.removeCallbacks(ticker)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(key.hashCode())
        finish()
    }
    override fun onDestroy() { handler.removeCallbacks(ticker); super.onDestroy() }
}
