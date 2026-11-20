package com.example.prayertimes

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.*

object AlarmScheduler {
    const val CHANNEL_ID = "prayer_alerts_silent"
    fun scheduleAll(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences("scheduled_alarms", Context.MODE_PRIVATE)
        prefs.getStringSet("ids", emptySet())?.forEach { raw ->
            val old = raw.toIntOrNull() ?: return@forEach
            val pi = PendingIntent.getBroadcast(context, old, Intent(context, PrayerAlarmReceiver::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (pi != null) { manager.cancel(pi); pi.cancel() }
        }
        val scheduled = mutableSetOf<String>()
        val city = PrayerStore.city(context)
        val today = LocalDate.now(ZoneId.of(city.zone))
        PrayerWindows.all(context, today, 8).filter { it.start.isAfter(Instant.now().plusSeconds(2)) }.forEach { w ->
            val id = w.key.hashCode()
            val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                putExtra("key", w.key); putExtra("name", w.name); putExtra("start", w.start.toEpochMilli()); putExtra("end", w.end.toEpochMilli())
            }
            val pi = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val showIntent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val info = AlarmManager.AlarmClockInfo(w.start.toEpochMilli(), showIntent)
            try { manager.setAlarmClock(info, pi); scheduled += id.toString() } catch (_: SecurityException) { }
        }
        prefs.edit().putStringSet("ids", scheduled).apply()
    }
}
