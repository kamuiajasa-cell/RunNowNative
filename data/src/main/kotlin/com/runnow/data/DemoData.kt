package com.runnow.data

import com.runnow.domain.Act
import com.runnow.domain.ActKind
import com.runnow.domain.PlanDay
import com.runnow.domain.Profile
import com.runnow.domain.Settings
import com.runnow.domain.StreamDec
import com.runnow.domain.StreamFade
import com.runnow.domain.StreamFast
import com.runnow.domain.StreamSummary
import com.runnow.domain.Wellness
import java.time.Instant
import java.time.ZoneId

/** Data dan pengaturan contoh untuk "Coba Demo" (enterDemo di HTML). Hanya di memori, tidak pernah disimpan. */
class DemoBundle(val data: AppData, val settings: Settings)

object DemoData {
    private const val DAY = 864e5

    fun create(nowMs: Long, zone: ZoneId = ZoneId.systemDefault(), baseGoal: String = "5K", baseStyle: String = "moderate"): DemoBundle {
        fun run(ago: Double, min: Double, km: Double, hr: Double, lid: String, name: String, icu: String? = null) =
            Act(start = (nowMs - ago * DAY).toLong(), kind = ActKind.RUN, min = min, km = km, avgHr = hr, lid = lid, name = name, icuId = icu)
        // Di HTML lari "d6" tertulis dua kali (kemungkinan salah ketik); di sini hanya sekali.
        val acts = listOf(
            run(0.4, 48.0, 8.2, 141.0, "d1", "Easy", "x1"),
            run(1.3, 34.0, 6.4, 159.0, "d2", "Tempo"),
            run(2.2, 92.0, 15.0, 147.0, "d3", "Long"),
            run(3.5, 40.0, 7.1, 138.0, "d4", "Easy"),
            run(5.1, 26.0, 4.8, 164.0, "d5", "Intervals"),
            run(8.0, 55.0, 9.5, 145.0, "d6", "Easy"),
            run(12.0, 38.0, 6.8, 150.0, "d7", "Steady"),
        )
        val wellness = (13 downTo 1).map { i ->
            val day = Instant.ofEpochMilli((nowMs - i * DAY).toLong()).atZone(zone).toLocalDate().toString()
            Wellness(
                id = day, ctl = 44.2, atl = 50.1, tsb = -5.9,
                restingHR = if (i == 1) 58.0 else (52 + i % 2).toDouble(),
                hrv = if (i == 1) 49.0 else (62 + i % 3).toDouble(),
            )
        }
        val sx = mapOf("x1" to StreamSummary(fast = StreamFast(5.75, 6.15, 7), fade = StreamFade(6), dec = StreamDec(6), tiz = listOf(8, 58, 24, 9, 1)))
        val data = AppData(
            profile = Profile(rest = 52.0, max = 185.0, age = 32, afS = 3),
            acts = acts, wellness = wellness, sx = sx, icuLast = nowMs, icuName = "Demo Runner",
        )
        val race = Instant.ofEpochMilli((nowMs + 10 * DAY).toLong()).atZone(zone).toLocalDate().toString()
        val settings = Settings(
            style = baseStyle, goal = baseGoal, race = race,
            plan = mapOf(
                1 to PlanDay("easy", 6.0), 2 to PlanDay("quality", 8.0), 3 to PlanDay("easy", 6.0), 4 to PlanDay("rest", 0.0),
                5 to PlanDay("easy", 5.0), 6 to PlanDay("long", 14.0), 0 to PlanDay("rest", 0.0),
            ),
        )
        return DemoBundle(data, settings)
    }
}
