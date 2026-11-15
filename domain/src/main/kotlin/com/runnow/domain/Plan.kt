package com.runnow.domain

import java.time.Instant
import java.time.LocalDate
import kotlin.math.max

/** Urutan hari di UI rencana: Sen..Sab lalu Min (0 = Minggu). */
val PLAN_DAYS = listOf(1, 2, 3, 4, 5, 6, 0)

/** Masa berlaku flag kaki (hari): 1 = was-was, 2 = sakit. */
val FLAG_DAYS = mapOf(1 to 7, 2 to 14)

private val DATE_RE = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

/** new Date().getDay(): 0 = Minggu .. 6 = Sabtu, menurut zona waktu. */
val Snapshot.dow: Int
    get() = Instant.ofEpochMilli(nowMs).atZone(zone).dayOfWeek.value % 7

/** ymd() di HTML: tanggal lokal "yyyy-MM-dd". */
fun Snapshot.ymd(): String = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toString()

fun Snapshot.heatOn(): Boolean = settings.heat == ymd()

fun Snapshot.rwOn(): Boolean = settings.rw.on

/** Rencana untuk hari [d] (0..6), atau null bila "auto"/kosong. */
fun Snapshot.planOf(d: Int): PlanDay? {
    val p = settings.plan[d] ?: return null
    return if (p.t.isNotEmpty() && p.t != "auto") PlanDay(p.t, if (p.km.isNaN()) 0.0 else p.km) else null
}

fun Snapshot.hasPlan(): Boolean = PLAN_DAYS.any { planOf(it) != null }

/** Total km rencana minggu (semua hari, termasuk yang "auto"). */
fun Snapshot.planWeekKm(): Double = PLAN_DAYS.sumOf { settings.plan[it]?.km ?: 0.0 }

/** Menit -> "m:ss" untuk pengaturan run-walk (n dalam detik). */
internal fun rwT(n: Int): String = "${n / 60}:${(n % 60).toString().padStart(2, '0')}"

/** Pace lari murni dari pace rata-rata run-walk (jalan dianggap ~11 mnt/km). */
fun Snapshot.runPaceOf(avg: Double): Double {
    val run = settings.rw.run.toDouble()
    val walk = settings.rw.walk.toDouble()
    val f = run / (run + walk)
    val inv = (1 / avg - (1 - f) / 11) / f
    return if (inv > 0) max(1 / inv, 4.5) else avg
}

/** Target pace race (mnt/km) dari jarak dan target waktu; 0 bila belum diisi. */
fun Snapshot.goalPace(): Double {
    val g = settings.raceGoal.split(":").map { jsNumber(it) }
    if (g.size < 2 || g.any { it.isNaN() }) return 0.0
    val sec = if (g.size == 3) g[0] * 3600 + g[1] * 60 + g[2] else g[0] * 60 + g[1]
    val km = settings.raceDist
    return if (km > 0 && sec > 0) sec / 60 / km else 0.0
}

/** Fase menuju race: build, peak, taper, race; null bila belum ada race atau sudah lewat. */
fun Snapshot.racePhase(): RacePhase? {
    if (settings.race.isEmpty()) return null
    val m = DATE_RE.find(settings.race) ?: return null
    val raceDay = runCatching {
        LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()).toEpochDay()
    }.getOrNull() ?: return null
    val days = (raceDay - today / DAY_MS).toInt()
    if (days < 0) return null
    val d = settings.raceDist
    val tp = if (d >= 42) 21 else if (d >= 21) 14 else if (d >= 10) 10 else if (d > 0) 7 else 14
    val k = if (days == 0) "race" else if (days <= tp) "taper" else if (days <= tp + 14) "peak" else "build"
    return RacePhase(days, k)
}

/** Flag aktif 7 hari (was-was) atau 14 hari (sakit), lalu kedaluwarsa sendiri. */
fun Snapshot.flagInfo(): FlagInfo? {
    val f = settings.flag ?: return null
    if (!(f.lv == 1 || f.lv == 2) || !(f.since > 0)) return null
    val dayN = Math.floorDiv(today - d0(f.since), DAY_MS).toInt() + 1
    if (dayN < 1 || dayN > (FLAG_DAYS[f.lv] ?: 0)) return null
    return FlagInfo(f.lv, dayN)
}

/** Long: 3 minggu terakhir semuanya ada long dan tidak ada yang turun -> saran cutback. */
fun Snapshot.cutbackDue(st: Stage = stage()): Boolean {
    val wkStart = today - ((dow + 6) % 7) * DAY_MS
    val longest = ArrayList<Double>()
    for (w in 1..3) {
        val a0 = wkStart - w * 7 * DAY_MS
        val a1 = a0 + 7 * DAY_MS
        var mx = 0.0
        for (x in acts) {
            val dd = d0(x.start)
            if (x.kind == ActKind.RUN && dd >= a0 && dd < a1 && (x.km >= 10 || detectType(x, st).type == "long")) {
                mx = max(mx, x.km)
            }
        }
        longest += mx
    }
    return longest.all { it > 0 } && longest.min() >= 0.85 * longest.max()
}

/** Median pace (mnt/km) lari easy 28 hari terakhir; null bila kurang dari 3 lari. */
fun Snapshot.easyBase(st: Stage = stage()): Double? {
    val cut = nowMs - 28 * DAY_MS
    val rw = rwOn()
    val paces = acts
        .filter { it.kind == ActKind.RUN && it.start >= cut && it.km >= 2.5 && it.min >= 15 }
        .filter { detectType(it, st).type == "easy" }
        .map { it.min / it.km }
        .filter { it >= 4.5 && it <= (if (rw) 14 else 11) }
        .map { if (rw) runPaceOf(it) else it }
    if (paces.size < 3) return null
    return paces.sorted()[paces.size / 2]
}

fun Snapshot.easyRange(score: Int, au: Autonomic?, heat: Boolean, st: Stage = stage()): EasyRange {
    val b = easyBase(st)
    var lo = 6.8
    var hi = 7.6
    var src = "default"
    if (b != null) {
        lo = b
        hi = b * 1.07
        src = "baseline"
    }
    var k = 1.0
    if (heat) k += 0.06
    if (score < 55 || (au != null && au.warn)) k += 0.04
    return EasyRange(lo * k, hi * k, src, b)
}

/** Hills dipakai hanya kalau medan latihanmu berbukit: median >= 8 m/km di 28 hari terakhir. */
fun Snapshot.hillsRelevant(): Boolean {
    val cut = nowMs - 28 * DAY_MS
    val l = acts
        .filter { it.kind == ActKind.RUN && it.start >= cut && it.km >= 3 && it.elev >= 0 }
        .map { it.elev / it.km }
    if (l.size < 4) return false
    return l.sorted()[l.size / 2] >= 8
}
