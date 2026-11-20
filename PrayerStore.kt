package com.example.prayertimes

import android.content.Context

object PrayerStore {
    private fun p(c: Context) = c.getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
    fun cityIndex(c: Context) = p(c).getInt("city", 22).coerceIn(0, PrayerCalc.cities.lastIndex)
    fun method(c: Context) = p(c).getString("method", PrayerCalc.cities[cityIndex(c)].method) ?: "mwl"
    fun city(c: Context): City {
        val raw = p(c).getString("gps_city", null)
        if (raw != null) {
            val parts = raw.split(",")
            if (parts.size == 3) try { return City("موقعي الحالي", parts[0].toDouble(), parts[1].toDouble(), parts[2], "mwl") } catch (_: Exception) { }
        }
        return PrayerCalc.cities[cityIndex(c)]
    }
    fun asr(c: Context) = p(c).getInt("asr", 1)
    fun enabled(c: Context, key: String) = p(c).getBoolean("enabled_$key", true)
    fun setEnabled(c: Context, key: String, value: Boolean) { p(c).edit().putBoolean("enabled_$key", value).apply() }
    fun setCity(c: Context, i: Int) { p(c).edit().remove("gps_city").putInt("city", i).putString("method", PrayerCalc.cities[i].method).apply() }
    fun setMethod(c: Context, value: String) { p(c).edit().putString("method", value).apply() }
    fun setAsr(c: Context, value: Int) { p(c).edit().putInt("asr", value).apply() }
    fun isDone(c: Context, key: String) = p(c).getBoolean("done_$key", false)
    fun markDone(c: Context, key: String) { p(c).edit().putBoolean("done_$key", true).apply() }

}
