package com.example.prayertimes

import java.time.*
import kotlin.math.*

data class City(val name: String, val lat: Double, val lon: Double, val zone: String, val method: String)
data class PrayerTimes(val fajr: Instant?, val sunrise: Instant?, val dhuhr: Instant?, val asr: Instant?, val maghrib: Instant?, val isha: Instant?) {
    fun get(key: String): Instant? = when (key) {
        "fajr" -> fajr; "sunrise" -> sunrise; "dhuhr" -> dhuhr; "asr" -> asr; "maghrib" -> maghrib; "isha" -> isha; else -> null
    }
}

object PrayerCalc {
    val cities = listOf(
        City("مكة المكرمة",21.4225,39.8262,"Asia/Riyadh","umm"), City("المدينة المنورة",24.4672,39.6111,"Asia/Riyadh","umm"),
        City("الرياض",24.7136,46.6753,"Asia/Riyadh","umm"), City("جدة",21.5433,39.1728,"Asia/Riyadh","umm"),
        City("القاهرة",30.0444,31.2357,"Africa/Cairo","egy"), City("الإسكندرية",31.2001,29.9187,"Africa/Cairo","egy"),
        City("دبي",25.2048,55.2708,"Asia/Dubai","mwl"), City("الكويت",29.3759,47.9774,"Asia/Kuwait","mwl"),
        City("الدوحة",25.2854,51.531,"Asia/Qatar","umm"), City("بغداد",33.3152,44.3661,"Asia/Baghdad","mwl"),
        City("عمّان",31.9454,35.9284,"Asia/Amman","mwl"), City("دمشق",33.5138,36.2765,"Asia/Damascus","mwl"),
        City("بيروت",33.8938,35.5018,"Asia/Beirut","mwl"), City("صنعاء",15.3694,44.191,"Asia/Aden","umm"),
        City("الخرطوم",15.5007,32.5599,"Africa/Khartoum","egy"), City("طرابلس",32.8872,13.1913,"Africa/Tripoli","mwl"),
        City("تونس",36.8065,10.1815,"Africa/Tunis","mwl"), City("الجزائر",36.7538,3.0588,"Africa/Algiers","mwl"),
        City("الرباط",34.0209,-6.8416,"Africa/Casablanca","mwl"), City("إسطنبول",41.0082,28.9784,"Europe/Istanbul","mwl"),
        City("لندن",51.5072,-0.1276,"Europe/London","mwl"), City("نيويورك",40.7128,-74.006,"America/New_York","isna"),
        City("أوكلاند (نيوزيلندا)",-36.8509,174.7645,"Pacific/Auckland","mwl"), City("ويلينغتون (نيوزيلندا)",-41.2866,174.7756,"Pacific/Auckland","mwl"),
        City("كرايستشيرش (نيوزيلندا)",-43.5321,172.6362,"Pacific/Auckland","mwl")
    )
    private val rad = PI / 180.0
    private fun sinD(x: Double) = sin(x * rad)
    private fun cosD(x: Double) = cos(x * rad)
    private fun tanD(x: Double) = tan(x * rad)
    private fun fix(x: Double, m: Double) = ((x % m) + m) % m

    fun calculate(date: LocalDate, city: City, method: String, asrFactor: Int): PrayerTimes {
        val zone = ZoneId.of(city.zone)
        val noonUtc = date.atTime(12, 0).toInstant(ZoneOffset.UTC)
        val off = zone.rules.getOffset(noonUtc).totalSeconds / 3600.0
        val jd = noonUtc.toEpochMilli() / 86400000.0 + 2440587.5
        val d = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * d, 360.0)
        val q = fix(280.459 + 0.98564736 * d, 360.0)
        val l = fix(q + 1.915 * sinD(g) + 0.02 * sinD(2 * g), 360.0)
        val e = 23.439 - 3.6e-7 * d
        var ra = atan2(cosD(e) * sinD(l), cosD(l)) / rad / 15.0
        ra = fix(ra, 24.0)
        val dec = asin(sinD(e) * sinD(l)) / rad
        val eqt = q / 15.0 - ra
        val eq = when { eqt > 12 -> eqt - 24; eqt < -12 -> eqt + 24; else -> eqt }
        val noon = 12 + off - city.lon / 15.0 - eq
        fun t(angle: Double): Double {
            val c = (-sinD(angle) - sinD(dec) * sinD(city.lat)) / (cosD(dec) * cosD(city.lat))
            return if (c > 1 || c < -1) Double.NaN else acos(c) / rad / 15.0
        }
        val angles = when (method) {
            "egy" -> 19.5 to 17.5; "kar" -> 18.0 to 18.0; "isna" -> 15.0 to 15.0; "umm" -> 18.5 to 17.0; else -> 18.0 to 17.0
        }
        val asrAngle = -atan(1.0 / (asrFactor + tan(abs(city.lat - dec)))) / rad
        val sr = noon - t(-0.833)
        val ss = noon + t(-0.833)
        val values = if (method == "umm") listOf(noon - t(-angles.first), sr, noon + 0.0333, noon + t(asrAngle), ss + 0.0167, ss + 1.5)
            else listOf(noon - t(-angles.first), sr, noon + 0.0333, noon + t(asrAngle), ss + 0.0167, noon + t(-angles.second))
        fun instant(hours: Double): Instant? = if (hours.isNaN() || hours.isInfinite()) null else date.atStartOfDay(ZoneOffset.UTC).toInstant().plusMillis(((hours - off) * 3600000.0).roundToLong())
        return PrayerTimes(instant(values[0]), instant(values[1]), instant(values[2]), instant(values[3]), instant(values[4]), instant(values[5]))
    }
}
