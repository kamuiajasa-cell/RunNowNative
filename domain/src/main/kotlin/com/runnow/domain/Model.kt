package com.runnow.domain

import java.time.Instant
import java.time.ZoneId

/** Jenis aktivitas. Di HTML: a[1] = "run" | "walk" | "other". */
enum class ActKind { RUN, WALK, OTHER }

/**
 * Satu aktivitas. Di HTML berupa array:
 * [0]=start, [1]=kind, [2]=menit, [3]=km, [4]=avgHr, [5]=elev, [6]=lid, [7]=name, [8]=maxHr, [9]=icuId, [10]=load.
 * Nilai 0.0 berarti "tidak ada", sama seperti di HTML.
 */
data class Act(
    val start: Long,
    val kind: ActKind,
    val min: Double,
    val km: Double,
    val avgHr: Double = 0.0,
    val elev: Double = 0.0,
    val lid: String,
    val name: String = "",
    val maxHr: Double = 0.0,
    val icuId: String? = null,
    val load: Double = 0.0,
)

/** Data wellness harian. [id] = "yyyy-MM-dd". Nilai 0.0 berarti tidak ada. */
data class Wellness(
    val id: String,
    val restingHR: Double = 0.0,
    val rhrSrc: String = "",
    val hrv: Double = 0.0,
    val hrvSrc: String = "",
    val hrvSpot: Boolean = false,
    val sleepSecs: Double = 0.0,
    val sleepScore: Double = 0.0,
    val steps: Double = 0.0,
    val spO2: Double = 0.0,
    val stress: Double = 0.0,
    val stressMax: Double = 0.0,
    val stressNight: Double = 0.0,
    /** Dari Intervals.icu; null bila tidak ada. */
    val ctl: Double? = null,
    val atl: Double? = null,
    val tsb: Double? = null,
)

/** Profil pelari. state.rest / state.max / state.age / state.afS di HTML. */
data class Profile(
    val rest: Double = 0.0,
    val max: Double = 0.0,
    val age: Int = 0,
    /** Tahap terakhir yang tersimpan (untuk histeresis). 0 = belum ada. */
    val afS: Int = 0,
)

/**
 * Masukan lengkap untuk engine. Semua fungsi engine adalah extension function di sini,
 * jadi tidak ada state global dan mudah dites dengan waktu tetap.
 */
data class Snapshot(
    val profile: Profile,
    val acts: List<Act>,
    val wellness: List<Wellness>,
    /** state.vx: startMs -> jenis sesi yang dikonfirmasi manual. */
    val manualTypes: Map<Long, String> = emptyMap(),
    val nowMs: Long = System.currentTimeMillis(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val settings: Settings = Settings(),
) {
    /** d0() di HTML: tengah malam tanggal lokal, dinyatakan sebagai epoch ms UTC. */
    fun d0(ms: Long): Long =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay() * DAY_MS

    val today: Long get() = d0(nowMs)
}

data class MaxEstimate(
    val m: Double?,
    val src: String,
    val n: Int,
    val conf: String,
    val perf: Double?,
    val hrr: Double?,
)

data class Stage(
    val s: Int,
    val m: Double?,
    val e: MaxEstimate,
    val raw: Int?,
    val unknown: Boolean,
    /** Nilai yang harus disimpan ke Profile.afS (di HTML: state.afS = s). */
    val afS: Int,
)

data class Autonomic(
    val lock: Boolean,
    val severe: Boolean,
    val warn: Boolean,
    val why: String,
    val hrvPct: Int?,
    val rhrD: Int?,
    val hrv: Int?,
    val rhr: Int?,
    val sleepH: Double?,
    val stress: Double?,
    val stressD: Int?,
)

data class SessionGuess(
    val type: String,
    val conf: String,
    val why: String,
    val confirmed: Boolean = false,
)

data class Rhr28(val avg: Int, val n: Int)

data class WalkRunWorkout(
    val k: String,
    val tot: Int,
    /** Pasangan (nama langkah, keterangan). */
    val steps: List<Pair<String, String>>,
)

// ---------- Pengaturan dan rencana (Langkah 2) ----------

/** Satu hari rencana minggu. [t] = "auto" | "easy" | "quality" | "long" | "rest". */
data class PlanDay(val t: String, val km: Double = 0.0)

/** Mode run-walk. [run] dan [walk] dalam detik per interval (default 3:45 lari, 0:30 jalan). */
data class RunWalkSettings(val on: Boolean = false, val run: Int = 225, val walk: Int = 30)

/** Flag kaki: lv 1 = was-was (7 hari), lv 2 = sakit (14 hari). [since] = epoch ms saat ditandai. */
data class LegFlag(val lv: Int, val since: Long)

data class AthleteSettings(val lthr: Int = 0, val ltPace: String = "")

data class Settings(
    /** "conservative" | "moderate" | "aggressive". */
    val style: String = "moderate",
    /** Kunci = hari dalam minggu, 0 = Minggu .. 6 = Sabtu. */
    val plan: Map<Int, PlanDay> = emptyMap(),
    /** Tanggal race "yyyy-MM-dd" atau "". */
    val race: String = "",
    val raceDist: Double = 0.0,
    /** Target waktu "h:mm:ss" atau "mm:ss" atau "". */
    val raceGoal: String = "",
    val rw: RunWalkSettings = RunWalkSettings(),
    /** Tanggal "yyyy-MM-dd" bila hari ini ditandai panas, selain itu "". */
    val heat: String = "",
    val flag: LegFlag? = null,
    val athlete: AthleteSettings = AthleteSettings(),
)

data class RacePhase(val days: Int, val k: String)

data class FlagInfo(val lv: Int, val dayN: Int)

data class EasyRange(val lo: Double, val hi: Double, val src: String, val base: Double?)

/** Saran latihan hari ini. */
data class Recommendation(
    /** "ok" | "wn" | "bad". */
    val k: String,
    /** "train" | "rest" | "done". */
    val status: String,
    val title: String,
    val duration: String,
    val hr: String,
    val pace: String,
    val reason: String,
    /** Pasangan (nama, keterangan). */
    val steps: List<Pair<String, String>>,
    val score: Int,
    val stage: Int? = null,
    val tag: String,
    /** Baris workout untuk dikirim ke jam (hanya sesi quality). */
    val wl: List<String>? = null,
)
