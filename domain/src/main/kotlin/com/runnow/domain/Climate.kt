package com.runnow.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Cuaca adalah konteks lingkungan, bukan pengukuran kondisi tubuh atau WBGT.
 * Port domain dari Climate-aware coach di runsense.html (brief-3).
 */
data class ClimateConditions(
    val temperatureC: Double?,
    val humidityPct: Double?,
    val apparentTemperatureC: Double?,
    val windKmh: Double?,
    val precipitationMm: Double = 0.0,
    val solarRadiationWm2: Double = 0.0,
    val weatherCode: Int = 0,
    val samples: Int = 0,
    val apparentMinC: Double? = null,
    val apparentMaxC: Double? = null,
)

data class ClimateRun(
    val startMs: Long,
    val durationMinutes: Double,
    val distanceKm: Double,
    val averageHr: Double,
    val averagePaceMinKm: Double,
    val isEasy: Boolean,
)

data class ClimateContext(
    val startMs: Long,
    val conditions: ClimateConditions,
    val source: String = "Open-Meteo",
    val matchedWithGps: Boolean,
    val gpsPointCount: Int,
    val note: String? = null,
)

data class ClimateBin(
    val lowC: Double,
    val highC: Double,
    val count: Int,
    val relativeEfficiencyPct: Double,
    val centerC: Double,
)

data class PersonalClimateProfile(
    val available: Boolean,
    val sampleCount: Int,
    val candidateCount: Int,
    val optimumC: Double? = null,
    val optimumLowC: Double? = null,
    val comfortableBelowC: Int? = null,
    val heavyAboveC: Int? = null,
    val confidence: String? = null,
    val bins: List<ClimateBin> = emptyList(),
    val explanation: String,
)

data class ClimateStatus(val level: String, val label: String, val reason: String)

data class ClimateAdvice(
    val status: ClimateStatus,
    val effectiveTemperatureC: Double,
    val message: String,
    val personalComparison: String?,
    val stopWarning: String?,
    val confidenceNote: String,
)

data class ClimateTimeSlot(
    val startMs: Long,
    val conditions: ClimateConditions,
    val score: Double,
    val betterThanNow: Boolean,
    val explanation: List<String>,
)

private data class ClimateEffortSample(val at: Long, val effectiveTemp: Double, val efficiency: Double)

/** Suhu efektif model = suhu udara (atau apparent bila suhu asli tak ada) + efek radiasi matahari. */
fun ClimateConditions.effectiveTemperatureC(): Double? =
    (temperatureC ?: apparentTemperatureC)?.plus((solarRadiationWm2.takeIf { it.isFinite() } ?: 0.0) / 150.0)

/** Ambil nilai tengah seperti median sederhana di HTML (indeks floor(n/2)). */
private fun median(values: List<Double>): Double {
    if (values.isEmpty()) return Double.NaN
    val s = values.filter { it.isFinite() }.sorted()
    return if (s.isEmpty()) Double.NaN else s[s.size / 2]
}

/**
 * Profil personal hanya memakai easy run yang memiliki HR, jarak, pace, dan konteks cuaca valid.
 * Efisiensi dibandingkan median sesi easy lain dalam jendela ±28 hari untuk mengurangi bias tren fitness.
 * Minimal 12 pembanding yang tersebar pada sedikitnya 3 kelompok suhu.
 */
fun personalClimateProfile(
    runs: List<ClimateRun>,
    contexts: Map<Long, ClimateContext>,
): PersonalClimateProfile {
    val candidates = runs.mapNotNull { run ->
        val c = contexts[run.startMs]?.conditions ?: return@mapNotNull null
        val temp = c.effectiveTemperatureC() ?: return@mapNotNull null
        if (!run.isEasy || run.averageHr <= 0 || run.distanceKm < 2 || run.averagePaceMinKm <= 0) return@mapNotNull null
        ClimateEffortSample(run.startMs, temp, (run.distanceKm * 1000.0 / (run.durationMinutes * 60.0)) / run.averageHr)
    }
    val comparable = candidates.mapNotNull { sample ->
        val neighbors = candidates.filter { it !== sample && abs(it.at - sample.at) <= 28L * 86_400_000L }
        if (neighbors.size < 4) null
        else ClimateEffortSample(sample.at, sample.effectiveTemp, median(neighbors.map { it.efficiency }).let {
            if (it > 0) sample.efficiency / it - 1 else Double.NaN
        })
    }.filter { it.efficiency.isFinite() }

    val fallback = PersonalClimateProfile(
        available = false,
        sampleCount = comparable.size,
        candidateCount = candidates.size,
        explanation = "Ambang masih default (25° / 29°): baru ${comparable.size} easy run yang bisa dipakai (butuh minimal 12, tersebar di ≥ 3 rentang suhu).",
    )
    if (comparable.size < 12) return fallback

    val temps = comparable.map { it.effectiveTemp }.sorted()
    val center = median(temps)
    val bulk = temps.count { abs(it - center) <= 1.5 }
    val other = temps.filter { abs(it - center) > 1.5 }
    val reason = buildString {
        append("$bulk dari ${comparable.size} easy run ada di suhu efektif ${(center - 1.5).roundToInt()}–${(center + 1.5).roundToInt()}°C")
        if (other.isNotEmpty()) append("; ${other.size} sisanya tersebar di ${other.minOrNull()!!.roundToInt()}–${other.maxOrNull()!!.roundToInt()}°C, belum ada rentang lain dengan ≥ 3 lari")
        append(". Perlu ≥ 3 rentang suhu dengan ≥ 3 lari tiap rentang")
    }

    fun bins(span: Double): List<ClimateBin> {
        val sorted = comparable.sortedBy { it.effectiveTemp }
        val groups = mutableListOf<MutableList<ClimateEffortSample>>()
        var current = mutableListOf<ClimateEffortSample>()
        sorted.forEach { sample ->
            current += sample
            if (current.size >= 3 && sample.effectiveTemp - current.first().effectiveTemp >= span) {
                groups += current
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) {
            if (current.size >= 3 || groups.isEmpty()) groups += current
            else groups.last().addAll(current)
        }
        return groups.map { g ->
            ClimateBin(g.first().effectiveTemp, g.last().effectiveTemp, g.size,
                median(g.map { it.efficiency }) * 100,
                (g.first().effectiveTemp + g.last().effectiveTemp) / 2)
        }
    }

    for (span in listOf(2.0, 1.0)) {
        val grouped = bins(span)
        if (grouped.size < 3) continue
        val best = grouped.maxBy { it.relativeEfficiencyPct }
        val bestIndex = grouped.indexOf(best)
        var cool = best.highC
        var hot = best.highC
        var sawDrop = false
        var found = false
        for (i in bestIndex + 1 until grouped.size) {
            val drop = best.relativeEfficiencyPct - grouped[i].relativeEfficiencyPct
            if (drop <= 1.5 && !sawDrop) cool = grouped[i].highC
            else if (drop <= 4.0) { sawDrop = true; hot = grouped[i].highC }
            else { sawDrop = true; found = true; break }
        }
        val coolValue = cool.roundToInt().coerceIn(20, 30)
        return PersonalClimateProfile(
            available = true,
            sampleCount = comparable.size,
            candidateCount = candidates.size,
            optimumC = (best.centerC * 10).roundToInt() / 10.0,
            optimumLowC = best.lowC,
            comfortableBelowC = if (sawDrop) coolValue else null,
            heavyAboveC = if (found) hot.roundToInt().coerceIn(coolValue + 2, 34) else null,
            confidence = when { comparable.size < 20 -> "awal"; comparable.size < 40 -> "cukup"; else -> "matang" },
            bins = grouped,
            explanation = reason,
        )
    }
    return fallback.copy(explanation = "$reason; rentang suhu belum cukup terpisah untuk membuat ambang personal.")
}

/** Kelas suhu relatif; ambang personal hanya mengubah kenyamanan, bukan batas keselamatan. */
fun climateClass(c: ClimateConditions, profile: PersonalClimateProfile): String {
    val v = c.effectiveTemperatureC() ?: return "tidak diketahui"
    val cool = if (profile.available) profile.comfortableBelowC?.toDouble() ?: 25.0 else 25.0
    val hot = if (profile.available) profile.heavyAboveC?.toDouble() ?: 29.0 else 29.0
    return when {
        v < cool -> "sejuk"
        v < hot -> "hangat"
        (c.humidityPct ?: 0.0) >= 70 -> "panas & lembap"
        else -> "panas"
    }
}

/** Penilaian cuaca. Petir/hujan lebat dan batas panas absolut selalu mengalahkan profil personal. */
fun climateStatus(c: ClimateConditions, profile: PersonalClimateProfile): ClimateStatus {
    val v = c.effectiveTemperatureC() ?: return ClimateStatus("unknown", "Data belum tersedia", "suhu tidak tersedia")
    val rain = c.precipitationMm
    if (c.weatherCode >= 95 || rain >= 8) return ClimateStatus("bad", "Berisiko", "ada petir atau hujan sangat lebat")
    if (v >= 38) return ClimateStatus("bad", "Berisiko", "tekanan panas estimasi sangat tinggi")
    if (rain >= 2 || v >= 33) return ClimateStatus("bad", "Kurang disarankan", if (rain >= 2) "hujan cukup deras" else "panas terik")
    val cool = if (profile.available) profile.comfortableBelowC?.toDouble() ?: 25.0 else 25.0
    val hot = if (profile.available) profile.heavyAboveC?.toDouble() ?: 29.0 else 29.0
    val warningThreshold = if (profile.available) {
        (cool + (profile.heavyAboveC?.toDouble() ?: (cool + 4.0))) / 2.0
    } else 27.0
    return if (v >= warningThreshold || rain >= 0.3)
        ClimateStatus("wn", "Perlu penyesuaian", if (v >= warningThreshold) "hangat/panas" else "gerimis")
    else ClimateStatus("ok", "Nyaman", "kondisi relatif mendukung")
}

/** Mengubah cuaca menjadi arahan, tanpa mengubah target/angka latihan asli. */
fun climateAdvice(
    conditions: ClimateConditions,
    profile: PersonalClimateProfile,
    sessionTag: String?,
    isRestDay: Boolean,
    comparisonHrDelta: Int? = null,
    comparisonCount: Int = 0,
    comparisonConfidence: String? = null,
): ClimateAdvice {
    val status = climateStatus(conditions, profile)
    val effective = conditions.effectiveTemperatureC() ?: Double.NaN
    val hard = sessionTag.equals("QUALITY", true)
    val long = sessionTag.equals("LONG", true)
    val message = when {
        isRestDay -> "Hari ini bukan hari lari, jadi cuaca hanya menjadi catatan."
        status.level == "unknown" -> "Data cuaca belum tersedia. Coach memakai logika latihan biasa."
        status.level == "ok" -> "Kondisi mendukung. Jalankan sesi sesuai rencana."
        status.label == "Berisiko" -> "Jangan lari di luar ruang sekarang. Pilih indoor atau tunda."
        status.label == "Kurang disarankan" -> "Lebih baik geser ke jam lain atau latihan indoor. Target asli sesi tetap sama, hanya waktunya yang bergeser."
        hard -> "Sesi berat sebaiknya digeser ke jam lebih sejuk. Kalau tetap sekarang, ikuti HR/RPE, bukan pace target."
        long -> "Panas menumpuk selama durasi panjang. Rencanakan air, rute teduh, dan jangan hanya lihat cuaca saat start."
        else -> "Jaga effort tetap santai, jangan kejar pace seperti saat sejuk. Pantau HR dan rasa tubuh, cari rute teduh, bawa air."
    }
    val personal = if (!isRestDay && status.level != "ok" && status.level != "unknown") {
        if (comparisonHrDelta != null && comparisonCount > 0) {
            "Di kondisi ${climateClass(conditions, profile)}, HR easy biasanya ${if (comparisonHrDelta > 0) "+" else ""}$comparisonHrDelta bpm dibanding saat sejuk (pace sebanding, $comparisonCount sesi, ${comparisonConfidence ?: "belum matang"})."
        } else "Data pribadi belum cukup, jadi panduan memakai effort dan HR, bukan angka pace."
    } else null
    return ClimateAdvice(
        status, effective, message, personal,
        if (!isRestDay && status.level != "ok" && status.level != "unknown") "Berhenti dan cari pertolongan bila pusing, bingung, atau lemas berat." else null,
        if (profile.available) "Ambang personal dari ${profile.sampleCount} easy run (${profile.confidence}); batas panas absolut tidak dipersonalisasi."
        else "Ambang default 25° / 27° / 29°; ini estimasi cuaca, bukan pengukuran WBGT atau kondisi tubuh.",
    )
}

/** Skor pemilihan waktu, mengikuti WeatherAwareRecommendation di HTML. */
fun climateSlotScore(c: ClimateConditions, profile: PersonalClimateProfile): Double {
    val v = c.effectiveTemperatureC() ?: return Double.POSITIVE_INFINITY
    val optimal = profile.optimumC
    val thermal = if (profile.available && optimal != null) {
        if (v < optimal) (optimal - v) * 0.5 else v - optimal
    } else v
    return thermal + (if (c.precipitationMm >= 0.3) 6.0 else 0.0) +
        (if (c.precipitationMm >= 2) 10.0 else 0.0) +
        (if (c.weatherCode >= 95) 50.0 else 0.0)
}

/** Pilih maksimal dua jam siang (05.00–18.00) dalam 40 jam mendatang, terpisah ≥2 jam. */
fun recommendClimateSlots(
    nowMs: Long,
    durationMinutes: Int,
    forecast: List<Pair<Long, ClimateConditions>>,
    current: ClimateConditions,
    profile: PersonalClimateProfile,
): List<ClimateTimeSlot> {
    val candidates = forecast.filter { (time, _) ->
        val hour = java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).hour
        time > nowMs && time <= nowMs + 40L * 3_600_000L && hour in 5..18
    }.map { (time, conditions) -> Triple(time, conditions, climateSlotScore(conditions, profile)) }
        .sortedBy { it.third }
    if (candidates.isEmpty()) return emptyList()
    val chosen = mutableListOf(candidates.first())
    candidates.firstOrNull { abs(it.first - candidates.first().first) >= 2L * 3_600_000L }?.let { chosen += it }
    val currentScore = climateSlotScore(current, profile) + if (current.precipitationMm >= 0.3) 6.0 else 0.0
    return chosen.map { (time, c, score) ->
        val why = mutableListOf<String>()
        val temp = c.temperatureC ?: c.apparentTemperatureC
        if (temp != null) why += "suhu ${temp.roundToInt()}°C" +
            (if (c.apparentTemperatureC != null && c.temperatureC != null) " (terasa ${c.apparentTemperatureC.roundToInt()}°C)" else "")
        val nowTemp = current.temperatureC
        if (nowTemp != null && c.temperatureC != null && nowTemp - c.temperatureC >= 2)
            why += "lebih sejuk ${(nowTemp - c.temperatureC).roundToInt()}°C dari sekarang"
        if (current.solarRadiationWm2 - c.solarRadiationWm2 >= 150) why += "matahari lebih lemah"
        why += if (c.precipitationMm < 0.3) "kering" else "ada hujan"
        ClimateTimeSlot(time, c, score, score <= currentScore - 1.5, why)
    }
}
