package com.mpls.salattv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Parsed prayer data, mirroring the original web app's PrayerData shape. */
data class PrayerData(
    val timings: Map<String, String>,   // e.g. "Fajr" -> "05:12"
    val hijriDay: Int,
    val hijriMonthNumber: Int,
    val hijriMonthEn: String,
    val hijriMonthAr: String,
    val hijriYear: String
)

data class Holiday(val en: String, val ar: String)

object PrayerRepository {

    // Same source + method (ISNA) and same Minneapolis address as the original app.
    private const val URL_STR =
        "https://api.aladhan.com/v1/timingsByAddress?address=Minneapolis,%20MN%2055408&method=2"

    /** Fetch prayer + Hijri data. Retries a few times before giving up. */
    suspend fun fetchPrayerData(retries: Int = 3): PrayerData = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        repeat(retries) { attempt ->
            try {
                return@withContext requestOnce()
            } catch (e: Exception) {
                lastError = e
                // brief backoff between attempts (cancellable)
                kotlinx.coroutines.delay((1000L * (attempt + 1)).coerceAtMost(5000L))
            }
        }
        throw lastError ?: RuntimeException("Failed to load prayer times")
    }

    private fun requestOnce(): PrayerData {
        val conn = (URL(URL_STR).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("User-Agent", "MarhabaTV/1.0")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw RuntimeException("HTTP ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val data = JSONObject(body).getJSONObject("data")

            val timingsJson = data.getJSONObject("timings")
            val timings = HashMap<String, String>()
            val keys = timingsJson.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                // API returns e.g. "05:12 (CST)" -> keep "05:12"
                timings[k] = timingsJson.getString(k).trim().substringBefore(' ')
            }

            val hijri = data.getJSONObject("date").getJSONObject("hijri")
            val month = hijri.getJSONObject("month")
            return PrayerData(
                timings = timings,
                hijriDay = hijri.getString("day").toIntOrNull() ?: 0,
                hijriMonthNumber = month.getInt("number"),
                hijriMonthEn = month.optString("en", ""),
                hijriMonthAr = month.optString("ar", ""),
                hijriYear = hijri.optString("year", "")
            )
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Holiday detection — identical rules to the original hijriService.ts:
     *  - Ramadan (month 9)            -> Ramadan Mubarak
     *  - Shawwal 1 (month 10, day 1)  -> Eid Mubarak (Eid al-Fitr)
     *  - Dhul-Hijjah 10 (month 12,10) -> Eid Mubarak (Eid al-Adha)
     */
    fun getHolidayMessage(monthNumber: Int, day: Int): Holiday? {
        return when {
            monthNumber == 9 -> Holiday("Ramadan Mubarak", "رمضان مبارك")
            monthNumber == 10 && day == 1 -> Holiday("Eid Mubarak", "عيد مبارك")
            monthNumber == 12 && day == 10 -> Holiday("Eid Mubarak", "عيد مبارك")
            else -> null
        }
    }
}
