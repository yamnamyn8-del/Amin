package com.example.prayertimes

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra("key") ?: return
        if (PrayerStore.isDone(context, key)) return
        val name = intent.getStringExtra("name") ?: "الصلاة"
        val start = intent.getLongExtra("start", System.currentTimeMillis())
        val end = intent.getLongExtra("end", start + 3600000)
        val activity = Intent(context, AlertActivity::class.java).apply {
            putExtra("key", key); putExtra("name", name); putExtra("start", start); putExtra("end", end)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreen = PendingIntent.getActivity(context, key.hashCode(), activity, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(AlarmScheduler.CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(AlarmScheduler.CHANNEL_ID, "تنبيهات الصلاة", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null); enableVibration(false); description = "تنبيه صامت عند دخول وقت الصلاة"
            })
        }
        val notification = NotificationCompat.Builder(context, AlarmScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("حان الآن وقت صلاة $name")
            .setContentText("اضغط لعرض شاشة الصلاة")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .build()
        nm.notify(key.hashCode(), notification)
        AlarmScheduler.scheduleAll(context)
    }
}
