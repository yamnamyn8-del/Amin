package com.example.prayertimes

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var nextName: TextView
    private lateinit var nextCount: TextView
    private lateinit var nextAt: TextView
    private lateinit var list: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() { refresh(); handler.postDelayed(this, 1000) } }
    private val prayerNames = linkedMapOf("fajr" to "الفجر", "sunrise" to "الشروق", "dhuhr" to "الظهر", "asr" to "العصر", "maghrib" to "المغرب", "isha" to "العشاء")
    private val methods = linkedMapOf("mwl" to "رابطة العالم الإسلامي", "umm" to "أم القرى", "egy" to "الهيئة المصرية العامة للمساحة", "kar" to "جامعة العلوم الإسلامية - كراتشي", "isna" to "أمريكا الشمالية (ISNA)")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(12,26,24)
        window.navigationBarColor = android.graphics.Color.rgb(12,26,24)
        buildUi()
        createChannel()
        requestNotificationPermission()
        AlarmScheduler.scheduleAll(this)
        maybeShowCurrentWindow()
    }
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (nm.getNotificationChannel(AlarmScheduler.CHANNEL_ID) == null) nm.createNotificationChannel(android.app.NotificationChannel(AlarmScheduler.CHANNEL_ID, "تنبيهات الصلاة", android.app.NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null); enableVibration(false) })
        }
    }
    private fun buildUi() {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 22, 22, 28); setBackgroundColor(android.graphics.Color.rgb(12,26,24)) }
        scroll.addView(root); setContentView(scroll)
        val heading = TextView(this).apply { text = "مواقيت الصلاة"; textSize = 28f; gravity = Gravity.CENTER; setTextColor(-1); setPadding(0, 8, 0, 18) }
        root.addView(heading)
        val hero = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(18, 22, 18, 22); setBackgroundColor(android.graphics.Color.rgb(10,46,40)) }
        root.addView(hero, matchWrap())
        hero.addView(label("الصلاة القادمة", 16f, 0xffc4d2ca.toInt()))
        nextName = label("—", 38f, -1); hero.addView(nextName)
        nextCount = label("--:--:--", 34f, 0xffe9c46a.toInt()); hero.addView(nextCount)
        nextAt = label("", 15f, -1); hero.addView(nextAt)

        val citySpinner = Spinner(this)
        citySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, PrayerCalc.cities.map { it.name } + "موقعي الحالي")
        citySpinner.setSelection(if (getSharedPreferences("prayer_settings", MODE_PRIVATE).contains("gps_city")) PrayerCalc.cities.size else PrayerStore.cityIndex(this))
        root.addView(sectionTitle("المدينة"))
        root.addView(citySpinner, matchWrap())
        citySpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == PrayerCalc.cities.size) { requestGps() } else if (position != PrayerStore.cityIndex(this@MainActivity) || getSharedPreferences("prayer_settings", MODE_PRIVATE).contains("gps_city")) { PrayerStore.setCity(this@MainActivity, position); AlarmScheduler.scheduleAll(this@MainActivity); refresh() }
            }
        }
        val gps = Button(this).apply { text = "موقعي الحالي"; setOnClickListener { requestGps() } }; root.addView(gps, matchWrap())

        root.addView(sectionTitle("مواقيت اليوم"))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 6, 12, 6); setBackgroundColor(android.graphics.Color.rgb(19,38,31)) }
        root.addView(list, matchWrap())
        root.addView(sectionTitle("الإعدادات"))
        root.addView(label("طريقة الحساب", 15f, 0xffb7c8bd.toInt()))
        val methodSpinner = Spinner(this)
        methodSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, methods.values.toList())
        methodSpinner.setSelection(methods.keys.indexOf(PrayerStore.method(this)).coerceAtLeast(0))
        root.addView(methodSpinner, matchWrap())
        methodSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { val value = methods.keys.elementAt(position); if (value != PrayerStore.method(this@MainActivity)) { PrayerStore.setMethod(this@MainActivity, value); AlarmScheduler.scheduleAll(this@MainActivity); refresh() } }
        }
        root.addView(label("المذهب (وقت العصر)", 15f, 0xffb7c8bd.toInt()))
        val asrSpinner = Spinner(this)
        asrSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("الجمهور (شافعي / مالكي / حنبلي)", "الحنفي"))
        asrSpinner.setSelection(if (PrayerStore.asr(this) == 2) 1 else 0)
        root.addView(asrSpinner, matchWrap())
        asrSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { val factor = if (position == 1) 2 else 1; if (factor != PrayerStore.asr(this@MainActivity)) { PrayerStore.setAsr(this@MainActivity, factor); AlarmScheduler.scheduleAll(this@MainActivity); refresh() } }
        }
        val test = Button(this).apply { text = "تجربة الشاشة السوداء الآن"; setOnClickListener { startActivity(Intent(this@MainActivity, AlertActivity::class.java).apply { putExtra("name", "الظهر"); putExtra("key", "test-${System.currentTimeMillis()}"); putExtra("end", System.currentTimeMillis() + 3600000) }) } }
        root.addView(test, matchWrap())
        val timedTest = Button(this).apply { text = "اختبار التنبيه بعد دقيقتين"; setOnClickListener { scheduleTestAlarm() } }
        root.addView(timedTest, matchWrap())
        root.addView(sectionTitle("تفعيل التنبيهات والصلاحيات"))
        addPermissionButton("السماح بالإشعارات") { if (Build.VERSION.SDK_INT >= 33) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10) }
        addPermissionButton("السماح بالتنبيهات الدقيقة") { if (Build.VERSION.SDK_INT >= 31) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) }
        addPermissionButton("السماح بعرض شاشة الصلاة فوق التطبيقات") { if (Build.VERSION.SDK_INT >= 23) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        addPermissionButton("السماح بعرض ملء الشاشة (Android 14+)") { if (Build.VERSION.SDK_INT >= 34) startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))) }
        addPermissionButton("استثناء التطبيق من تحسين البطارية") { if (Build.VERSION.SDK_INT >= 23) startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
        root.addView(label("ملاحظة: يعتمد ظهور الشاشة فوق التطبيقات والهاتف مقفلًا على سماح Android بإشعارات ملء الشاشة، وإعدادات الشركة المصنّعة للهاتف. تأكد من المواقيت مع جهة موثوقة في بلدك.", 13f, 0xff93a39a.toInt()))
    }
    private fun addPermissionButton(text: String, action: () -> Unit) { root.addView(Button(this).apply { this.text = text; setOnClickListener { action() } }, matchWrap()) }
    private fun sectionTitle(s: String) = label(s, 19f, 0xffe9c46a.toInt()).apply { setPadding(0, 20, 0, 8) }
    private fun label(s: String, size: Float, color: Int) = TextView(this).apply { text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER; textDirection = View.TEXT_DIRECTION_RTL; setPadding(4, 5, 4, 5) }
    private fun matchWrap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 7 }
    private fun requestNotificationPermission() { if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10) }
    private fun requestGps() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 11); return }
        try {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            val loc: Location? = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).mapNotNull { provider -> try { lm.getLastKnownLocation(provider) } catch (_: Exception) { null } }.maxByOrNull { it.time }
            if (loc == null) { Toast.makeText(this, "لم يتوفر موقع محفوظ. فعّل الموقع ثم حاول مجددًا.", Toast.LENGTH_LONG).show(); return }
            val zone = ZoneId.systemDefault().id
            val p = getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
            p.edit().putString("gps_city", "${loc.latitude},${loc.longitude},$zone").putString("method", "mwl").apply()
            Toast.makeText(this, "تم تحديث موقعك الحالي.", Toast.LENGTH_SHORT).show()
            refresh(); AlarmScheduler.scheduleAll(this)
        } catch (_: SecurityException) { Toast.makeText(this, "تعذّر الوصول إلى الموقع.", Toast.LENGTH_SHORT).show() }
    }
    private fun refresh() {
        val city = PrayerStore.city(this)
        val zone = ZoneId.of(city.zone); val now = Instant.now(); val date = LocalDate.now(zone)
        val times = PrayerCalc.calculate(date, city, PrayerStore.method(this), PrayerStore.asr(this))
        val upcoming = listOf("fajr" to "الفجر", "dhuhr" to "الظهر", "asr" to "العصر", "maghrib" to "المغرب", "isha" to "العشاء").mapNotNull { (key, name) -> times.get(key)?.let { Triple(key, name, it) } }.firstOrNull { it.third.isAfter(now) }
        val next = upcoming ?: run { val tomorrow = PrayerCalc.calculate(date.plusDays(1), city, PrayerStore.method(this), PrayerStore.asr(this)); Triple("fajr", "الفجر", tomorrow.fajr ?: now) }
        nextName.text = next.second
        val seconds = Duration.between(now, next.third).seconds.coerceAtLeast(0)
        nextCount.text = String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
        nextAt.text = "عند ${format(next.third, zone)}"
        list.removeAllViews()
        prayerNames.forEach { (key, name) ->
            val time = times.get(key)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(4, 8, 4, 8) }
            val nameView = TextView(this).apply { text = name; textSize = 18f; setTextColor(-1); gravity = Gravity.RIGHT; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            val timeView = TextView(this).apply { text = time?.let { format(it, zone) } ?: "—"; textSize = 17f; setTextColor(0xffe9c46a.toInt()); gravity = Gravity.CENTER }
            row.addView(nameView); row.addView(timeView)
            if (key != "sunrise") {
                val toggle = Switch(this).apply { isChecked = PrayerStore.enabled(this@MainActivity, key); text = ""; setOnCheckedChangeListener { _, checked -> PrayerStore.setEnabled(this@MainActivity, key, checked); AlarmScheduler.scheduleAll(this@MainActivity); refresh() } }
                row.addView(toggle)
            }
            list.addView(row)
            val line = View(this).apply { setBackgroundColor(0xff25403a.toInt()) }; list.addView(line, LinearLayout.LayoutParams(-1, 1))
        }
    }
    private fun format(instant: Instant, zone: ZoneId): String = DateTimeFormatter.ofPattern("h:mm a", Locale("ar")).withZone(zone).format(instant)
    private fun scheduleTestAlarm() {
        val manager = getSystemService(ALARM_SERVICE) as AlarmManager
        val start = System.currentTimeMillis() + 2 * 60 * 1000L
        val end = start + 60 * 60 * 1000L
        val key = "test-alarm-${System.currentTimeMillis()}"
        val alarmIntent = Intent(this, PrayerAlarmReceiver::class.java).apply { putExtra("key", key); putExtra("name", "اختبار التنبيه"); putExtra("start", start); putExtra("end", end) }
        val pi = android.app.PendingIntent.getBroadcast(this, key.hashCode(), alarmIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val show = android.app.PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        try {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(start, show), pi)
            Toast.makeText(this, "تم ضبط اختبار بعد دقيقتين. اقفل الهاتف وانتظر التنبيه.", Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) { Toast.makeText(this, "فعّل إذن التنبيهات الدقيقة أولًا.", Toast.LENGTH_LONG).show() }
    }
    private fun maybeShowCurrentWindow() {
        val w = PrayerWindows.current(this) ?: return
        if (!PrayerStore.isDone(this, w.key)) {
            window.decorView.postDelayed({ if (!isFinishing) startActivity(Intent(this, AlertActivity::class.java).apply { putExtra("key", w.key); putExtra("name", w.name); putExtra("start", w.start.toEpochMilli()); putExtra("end", w.end.toEpochMilli()) }) }, 450)
        }
    }
    override fun onResume() { super.onResume(); refresh(); AlarmScheduler.scheduleAll(this); handler.post(tick) }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }
}
