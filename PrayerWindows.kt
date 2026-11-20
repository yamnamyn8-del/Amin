package com.example.prayertimes

import android.content.Context
import java.time.*

data class PrayerWindow(val key: String, val name: String, val start: Instant, val end: Instant, val dayKey: String)

object PrayerWindows {
    private val keys = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")
    private val names = mapOf("fajr" to "الفجر", "dhuhr" to "الظهر", "asr" to "العصر", "maghrib" to "المغرب", "isha" to "العشاء")
    fun all(context: Context, from: LocalDate, days: Int = 8): List<PrayerWindow> {
        val city = PrayerStore.city(context)
        val method = PrayerStore.method(context)
        val factor = PrayerStore.asr(context)
        val zone = ZoneId.of(city.zone)
        val out = mutableListOf<PrayerWindow>()
        for (offset in -1..days) {
            val date = from.plusDays(offset.toLong())
            val today = PrayerCalc.calculate(date, city, method, factor)
            val tomorrow = PrayerCalc.calculate(date.plusDays(1), city, method, factor)
            val ends = mapOf("fajr" to today.sunrise, "dhuhr" to today.asr, "asr" to today.maghrib, "maghrib" to today.isha, "isha" to tomorrow.fajr)
            for (key in keys) {
                if (!PrayerStore.enabled(context, key)) continue
                val start = today.get(key) ?: continue
                val end = ends[key] ?: continue
                val dayKey = date.toString()
                out += PrayerWindow("$key-$dayKey", names[key] ?: key, start, end, dayKey)
            }
        }
        return out.sortedBy { it.start }
    }
    fun current(context: Context, now: Instant = Instant.now()): PrayerWindow? = all(context, LocalDate.now(ZoneId.of(PrayerStore.city(context).zone)), 2).firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }
}
