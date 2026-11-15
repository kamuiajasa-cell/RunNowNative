package com.runnow.domain

import java.time.Instant
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Nama tahap (SN di HTML), indeks 0 kosong. */
internal val STAGE_NAMES = listOf("", "Fondasi: jalan cepat", "Run-walk (White Plan)", "Easy terbatas", "Terstruktur")

/** Info sinkronisasi (state.icuName / icuLast / hwLast). Waktu dalam epoch ms, 0 = belum pernah. */
data class SyncInfo(val icuName: String = "", val icuLast: Long = 0L, val hwLast: Long = 0L)

/** state.athlete di HTML. */
data class AthleteSummary(
    val name: String,
    val ctl: Double?,
    val atl: Double?,
    val tsb: Double?,
    /** LTHR yang dipakai (custom bila ada, selain itu auto). */
    val lthr: Int?,
    /** LTPace "m:ss" yang dipakai. */
    val ltPace: String?,
    val lthrAuto: Int?,
    val ltPaceAuto: String?,
    val formLabel: String,
    /** "form-baik" | "form-sedang" | "form-lelah". */
    val formClass: String,
    val score: Int?,
    val mets: Double?,
    val stage: Int,
    val stageName: String,
    val est: MaxEstimate,
)

/** state.goalEst di HTML. */
data class GoalEstimate(val current: String, val good: String, val projection: String)

/** Satu baris di state.activities (25 aktivitas terbaru). */
data class ActivityItem(
    val start: Long,
    val date: String,
    val title: String,
    val meta: String,
    val load: Int,
    val type: String,
    val conf: String,
    val why: String,
    val confirmed: Boolean,
    val act: Act,
)

/**
 * Hasil processData: semua yang di HTML ditulis ke `state` supaya UI tinggal membaca.
 * [profile] membawa nilai baru `rest` (RHR otomatis) dan `afS` (histeresis tahap): simpan ke penyimpanan.
 */
data class ProcessResult(
    val athlete: AthleteSummary,
    val recommendation: Recommendation,
    val goalEst: GoalEstimate,
    val activities: List<ActivityItem>,
    val profile: Profile,
    val syncLine: String,
)

private val ID_DAYS = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min") // DayOfWeek.value 1 = Senin
private val ID_MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")

/** toLocaleDateString("id-ID", {weekday:"short", day:"numeric", month:"short"}) -> "Min, 15 Nov". */
internal fun Snapshot.dateShortId(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    return "${ID_DAYS[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${ID_MONTHS[d.monthValue - 1]}"
}

/** toLocaleDateString("id-ID", {day:"numeric", month:"short"}) -> "15 Nov". */
internal fun Snapshot.dateDayMonthId(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    return "${d.dayOfMonth} ${ID_MONTHS[d.monthValue - 1]}"
}

/** hhmm() di HTML: toLocaleTimeString("id-ID", {hour:"2-digit", minute:"2-digit"}) -> "08.05". */
internal fun Snapshot.hhmm(ms: Long): String {
    val t = Instant.ofEpochMilli(ms).atZone(zone)
    return t.hour.toString().padStart(2, '0') + "." + t.minute.toString().padStart(2, '0')
}

private fun goalKm(goal: String): Double = when (goal) {
    "5K" -> 5.0
    "10K" -> 10.0
    "Half" -> 21.0975
    "Marathon" -> 42.195
    "Umum" -> 5.0
    else -> 5.0
}

/** hm() di HTML: detik -> "mm:ss" atau "h:mm:ss". */
private fun hmText(t: Double): String =
    if (t >= 3600) "${floor(t / 3600).toLong()}:${formatTime(t % 3600)}" else formatTime(t)

/**
 * processData() di HTML: RHR otomatis, LTHR, LTPace, estimasi 5K/target, form, saran hari ini,
 * dan daftar 25 aktivitas terbaru. Tidak menyentuh state; semua hasil ada di [ProcessResult].
 * Urutan sama dengan HTML: stage() dihitung dulu, afS barunya dipakai langkah berikutnya.
 */
fun Snapshot.processData(sync: SyncInfo = SyncInfo()): ProcessResult {
    // applyRhrAuto
    var sn = this
    if (!profile.restManual) {
        rhr28()?.let { sn = sn.copy(profile = sn.profile.copy(rest = it.avg.toDouble())) }
    }

    // CTL / ATL / TSB dari wellness terakhir
    var ctl: Double? = null
    var atl: Double? = null
    var tsb: Double? = null
    val latest = sn.wellness.lastOrNull()
    if (latest != null) {
        ctl = latest.ctl
        atl = latest.atl
        val c = latest.ctl
        val a = latest.atl
        if (c != null && a != null) {
            tsb = c - a
        } else if (latest.tsb != null) {
            tsb = latest.tsb
        }
    }

    val runs = sn.acts.filter { it.kind == ActKind.RUN && it.min >= 15 && it.km >= 1.5 }
    val st0 = sn.stage()
    sn = sn.copy(profile = sn.profile.copy(afS = st0.afS))
    val bigM = st0.m

    // LTHR dan LTPace dari kapasitas
    var lthr: Int? = null
    var ltPace: String? = null
    var ltPaceMin: Double? = null
    val mx = sn.maxHr()
    val rst = sn.restHr()
    val rng = mx - rst

    if (mx >= 140 && rng >= 40) {
        lthr = jsRound(rst + 0.88 * rng).toInt()
    } else if (mx >= 140) {
        lthr = jsRound(mx * 0.88).toInt()
    }

    // Koreksi LTHR dari lari yang benar-benar keras
    class Hard(val hr: Double, val dens: Double)
    val lthrBase = lthr?.takeIf { it != 0 } ?: 150
    val hardHR = runs
        .filter { it.avgHr > 0 && it.km > 0 && it.min >= 12 }
        .map { a ->
            val pace = a.min / a.km
            Hard(a.avgHr, a.avgHr / max(pace, 4.0))
        }
        .filter { it.hr >= lthrBase * 0.92 }
        .sortedByDescending { it.dens }
    if (hardHR.size >= 2) {
        val top = hardHR.take(min(4, hardHR.size))
        val med = top.map { it.hr }.sorted()[top.size / 2]
        if (lthr == null || med >= lthr - 5) lthr = jsRound(med).toInt()
    }

    // LTPace: ~85% Max METs, lalu dipadukan dengan best effort 2-12 km
    if (bigM != null && bigM >= 6) {
        ltPaceMin = paceOf(0.85 * bigM)
        ltPace = mp(ltPaceMin)
    }
    class Cand(val pace: Double, val km: Double)
    val candidates = runs
        .filter { it.km >= 2 && it.km <= 12 && it.min >= 12 }
        .map { Cand(it.min / it.km, it.km) }
        .sortedBy { it.pace }
    if (candidates.isNotEmpty()) {
        val best = candidates[0]
        val estLT = best.pace * (if (best.km >= 4 && best.km <= 8) 1.03 else 1.06)
        val cur = ltPaceMin
        ltPaceMin = if (cur != null) max(best.pace * 1.02, min(cur, estLT)) else estLT
        ltPace = mp(ltPaceMin)
    }

    // Estimasi 5K
    var est5kSec: Double? = null
    for (a in runs) {
        if (a.km < 2 || a.min < 10) continue
        val pace = a.min / a.km
        val t5 = a.min * (5 / a.km).pow(1.06)
        val pct = if (bigM != null) metsOf(pace) / bigM else 0.75
        if (pct < 0.72 && a.km > 6) continue
        val candidate = t5 * 60
        if (est5kSec == null || candidate < est5kSec) est5kSec = candidate
    }
    if (bigM != null && bigM >= 7) {
        val pace5 = paceOf(0.96 * bigM)
        val fromMets = pace5 * 5 * 60
        val cur = est5kSec
        est5kSec = if (cur == null) fromMets else min(cur, fromMets * 1.08)
    }

    var formLabel = "Sedang"
    var formClass = "form-sedang"
    val score = sn.readiness()
    val tsbV = tsb
    if (tsbV != null) {
        if (tsbV >= 5) { formLabel = "Baik"; formClass = "form-baik" }
        else if (tsbV < -15) { formLabel = "Lelah"; formClass = "form-lelah" }
    } else if (score != null) {
        if (score >= 72) { formLabel = "Baik"; formClass = "form-baik" }
        else if (score < 48) { formLabel = "Lelah"; formClass = "form-lelah" }
    } else {
        formLabel = "Data kurang"
    }

    val st = sn.stage()
    sn = sn.copy(profile = sn.profile.copy(afS = st.afS))

    val lthrAuto = lthr
    val ltPaceAuto = ltPace
    if (sn.profile.lthrC > 0) lthr = sn.profile.lthrC
    if (sn.profile.ltpC > 0) ltPace = mp(sn.profile.ltpC / 60.0)

    val athlete = AthleteSummary(
        name = sync.icuName.ifEmpty { "Runner" },
        ctl = ctl, atl = atl, tsb = tsb, lthr = lthr, ltPace = ltPace,
        lthrAuto = lthrAuto, ltPaceAuto = ltPaceAuto,
        formLabel = formLabel, formClass = formClass, score = score,
        mets = st.m, stage = st.s, stageName = STAGE_NAMES.getOrElse(st.s) { "—" }.ifEmpty { "—" },
        est = st.e,
    )

    // designToday() membaca state.athlete.lthr / ltPace yang baru saja dihitung
    val withAthlete = sn.copy(
        settings = sn.settings.copy(athlete = AthleteSettings(lthr = lthr ?: 0, ltPace = ltPace ?: "")),
    )
    val recommendation = withAthlete.designToday()

    val gd = goalKm(sn.settings.goal)
    val e5 = est5kSec
    val goalEst = if (e5 != null) {
        GoalEstimate(
            current = hmText(e5 * (gd / 5).pow(1.06)),
            good = "Lari terbaik + METs",
            projection = if (gd > 10) "rendah (ekstrapolasi)" else st.e.conf.ifEmpty { "rendah" },
        )
    } else {
        GoalEstimate("—:—", "Data belum cukup", "—")
    }

    val activities = sn.acts
        .sortedByDescending { it.start }
        .take(25)
        .map { a ->
            val m = floor(a.min).toLong()
            val s = jsRound((a.min % 1) * 60)
            val det = sn.detectType(a, st)
            ActivityItem(
                start = a.start,
                date = sn.dateShortId(a.start),
                title = when (a.kind) { ActKind.RUN -> "Lari"; ActKind.WALK -> "Jalan"; ActKind.OTHER -> "Aktivitas" },
                meta = f1(a.km) + " km  •  " + m + ":" + s.toString().padStart(2, '0') +
                    (if (a.avgHr > 0) "  •  HR " + jsRound(a.avgHr) else ""),
                load = sn.loadOf(a),
                type = det.type, conf = det.conf, why = det.why, confirmed = det.confirmed,
                act = a,
            )
        }

    return ProcessResult(
        athlete = athlete,
        recommendation = recommendation,
        goalEst = goalEst,
        activities = activities,
        profile = sn.profile,
        syncLine = sn.syncLine(sync),
    )
}

/**
 * Kalimat pelatih untuk kartu "hari ini". [rested] = pengguna menandai istirahat, [walk] = pengguna
 * memilih alternatif jalan santai (keduanya keputusan UI, bukan bagian dari Recommendation).
 */
fun coachLine(r: Recommendation, formLabel: String, rested: Boolean = false, walk: Boolean = false): String {
    if (rested) return "Istirahat juga bagian dari latihan. Sampai besok."
    if (walk) return "Jalan pelan saja, tanpa target. Tubuhmu tetap bergerak tanpa dibebani."
    if (r.status == "rest") return "Tubuhmu butuh pulih dulu. Kalau mau bergerak, jalan santai saja, tanpa target."
    if (r.tag == "RECOVERY") return "Beban belakangan cukup berat, jadi hari ini lari sangat santai saja. Kamu harus masih bisa ngobrol bebas."
    if (r.tag == "TAPER") return "Menjelang race, volume diturunkan supaya kakimu segar. Jangan tergoda menambah."
    if (r.tag == "LOMBA") return "Hari lomba. Mulai sedikit di bawah target, percepat di paruh akhir."
    if (r.tag == "LONG") return "Lari panjang membangun daya tahan. Pelan, stabil, dan nikmati."
    if (r.stage == 1) return "Kita bangun dasarnya dulu dengan jalan cepat. Napas tetap nyaman, tidak perlu ngebut."
    if (r.stage == 2) return "Kapasitasmu masih dibangun, jadi kita pakai run-walk dulu. Capek di menit awal itu wajar."
    if (r.tag == "QUALITY") return "Tubuhmu siap untuk sesi yang lebih menantang. Kerja terkontrol, bukan habis-habisan."
    return if (formLabel == "Baik") "Tubuhmu siap. Lari santai, jaga napas tetap nyaman."
    else "Lari santai saja hari ini. Konsistensi lebih penting daripada kecepatan."
}

fun intensityLine(r: Recommendation, rested: Boolean = false): String {
    if (rested || r.status == "rest") return ""
    val p = r.pace
    if (p.isEmpty() || p == "—") return ""
    if (p.contains("/km")) {
        return "pace " + p.replaceFirst(" /km", "").replaceFirst(" (saat lari)", "") + " /km"
    }
    return p.lowercase()
}

/** syncLine() di HTML: "Sinkron · Intervals 08.05 · Jam 07.40" atau "Belum sinkron". */
fun Snapshot.syncLine(sync: SyncInfo): String {
    val p = ArrayList<String>()
    if (sync.icuLast != 0L) p += "Intervals " + hhmm(sync.icuLast)
    if (sync.hwLast != 0L) p += "Jam " + hhmm(sync.hwLast)
    return if (p.isNotEmpty()) "Sinkron · " + p.joinToString(" · ") else "Belum sinkron"
}

/**
 * Snapshot setelah processData: RHR otomatis dan afS baru ikut masuk, begitu juga LTHR dan LTPace hasil hitung
 * (yang di HTML dibaca reviewRun dari state.athlete). Pakai ini sebelum memanggil reviewRun atau runBacktest.
 */
fun Snapshot.withProcessed(r: ProcessResult): Snapshot = copy(
    profile = r.profile,
    settings = settings.copy(athlete = AthleteSettings(lthr = r.athlete.lthr ?: 0, ltPace = r.athlete.ltPace ?: "")),
)
