package com.runnow.domain

import java.time.Instant
import java.time.LocalDate
import kotlin.math.max

// Perhitungan layar Progres (renderProgress di runsense.html): volume mingguan, komposisi sesi,
// kepatuhan rencana, dan kebugaran (CTL/ATL). Logika dan teks wawasan disalin dari HTML; grafiknya di :app.

data class WeekBucket(
    val start: LocalDate,
    val km: Double,
    val n: Int,
    val min: Double,
    val easy: Double,
    val qual: Double,
    val long: Double,
    val other: Double,
)

fun LocalDate.mondayOf(): LocalDate = minusDays((dayOfWeek.value - 1).toLong())

private fun Snapshot.dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

/** Hitung desimal ala fmt1 di HTML: satu angka di belakang koma, koma sebagai pemisah, tanpa ",0". */
fun fmt1c(v: Double): String {
    val r = Math.round(v * 10) / 10.0
    return if (r == Math.floor(r)) r.toLong().toString() else r.toString().replace('.', ',')
}

/** [n] minggu terakhir (terlama dulu), minggu berjalan terakhir. Hanya lari dengan km > 0 dan menit > 0. */
fun Snapshot.progressWeeks(n: Int): List<WeekBucket> {
    val w0 = dayOf(nowMs).mondayOf()
    val starts = (n - 1 downTo 0).map { w0.minusWeeks(it.toLong()) }
    val idx = starts.withIndex().associate { it.value to it.index }
    // km, n, min, easy, qual, long, other
    val acc = Array(n) { DoubleArray(7) }
    for (x in acts) {
        if (x.kind != ActKind.RUN || !(x.km > 0) || !(x.min > 0)) continue
        val i = idx[dayOf(x.start).mondayOf()] ?: continue
        val w = acc[i]
        w[0] += x.km; w[1] += 1.0; w[2] += x.min
        val ty = try { detectType(x).type } catch (e: Exception) { "easy" }
        when (ty) {
            "long" -> w[5] += x.km
            "thr", "vo2", "race" -> w[4] += x.km
            "easy" -> w[3] += x.km
            else -> w[6] += x.km
        }
    }
    return starts.mapIndexed { i, s ->
        val w = acc[i]
        WeekBucket(s, w[0], w[1].toInt(), w[2], w[3], w[4], w[5], w[6])
    }
}

/** Ada lari sama sekali (kartu "Belum ada data lari" bila tidak). */
fun Snapshot.hasRuns(): Boolean = acts.any { it.kind == ActKind.RUN && it.km > 0 && it.min > 0 }

fun volumeInsight(weeks: List<WeekBucket>, rangeWeeks: Int): String {
    val active = weeks.count { it.n >= 1 }
    val active2 = weeks.count { it.n >= 2 }
    val last = weeks.last()
    val half = weeks.size / 2
    val km1 = weeks.take(half).sumOf { it.km } / max(1, half)
    val km2 = weeks.drop(half).sumOf { it.km } / max(1, weeks.size - half)
    val volDelta = if (km1 > 0.5) (((km2 - km1) / km1) * 100).let { Math.round(it).toInt() } else null
    return when {
        last.km == 0.0 && active > 0 -> "Minggu ini belum ada volume. Kalau memang recovery week, itu wajar. Jangan paksa mengejar rata-rata."
        volDelta != null && volDelta >= 15 -> "Rata-rata paruh akhir +$volDelta% dibanding paruh awal. Naik volume bagus, tapi jaga agar tidak dua lonjakan besar beruntun tanpa minggu lebih ringan."
        volDelta != null && volDelta <= -15 -> "Rata-rata paruh akhir $volDelta% dari paruh awal. Kalau ini deload sengaja, bagus. Kalau tidak, cek konsistensi dan kesiapan."
        active2 >= max(3, rangeWeeks / 2) -> "Volume relatif stabil. Konsistensi seperti ini yang membangun kapasitas jangka panjang."
        else -> "Masih banyak minggu sepi. Fokus dulu mengisi 3–4 minggu aktif beruntun sebelum mengejar angka tinggi."
    }
}

/** Persen perubahan minggu ini terhadap minggu lalu; null bila minggu lalu kosong. */
fun weekDeltaPct(weeks: List<WeekBucket>): Int? {
    if (weeks.size < 2) return null
    val last = weeks[weeks.size - 1]
    val prev = weeks[weeks.size - 2]
    return if (prev.km > 0) Math.round(((last.km - prev.km) / prev.km) * 100).toInt() else null
}

data class SessionMix(val easyPct: Int, val qualPct: Int, val longPct: Int, val easyKm: Double, val qualKm: Double, val longKm: Double, val insight: String)

/** Null bila total jarak easy+quality+long <= 0,5 km. */
fun mixOf(weeks: List<WeekBucket>): SessionMix? {
    val te = weeks.sumOf { it.easy }
    val tq = weeks.sumOf { it.qual }
    val tl = weeks.sumOf { it.long }
    val tot = te + tq + tl
    if (!(tot > 0.5)) return null
    val pe = Math.round(te / tot * 100).toInt()
    val pq = Math.round(tq / tot * 100).toInt()
    val pl = max(0, 100 - pe - pq)
    val active = weeks.count { it.n >= 1 }
    val ins = when {
        pq >= 35 -> "Porsi quality cukup tinggi. Pastikan easy benar-benar easy agar tidak menumpuk lelah."
        pq <= 8 && active >= 4 -> "Hampir semua easy/long. Setelah fondasi stabil, 1 sesi quality per minggu biasanya cukup."
        pl >= 30 -> "Long mendominasi volume. Bagus untuk ketahanan, jaga recovery 24–48 jam setelahnya."
        else -> "Campuran easy–quality–long terlihat seimbang untuk rentang ini."
    }
    return SessionMix(pe, pq, pl, te, tq, tl, ins)
}

data class PlanAdherence(
    val planKm: Double, val doneKm: Double, val planN: Int, val doneN: Int,
    val pctKm: Int?, val pctN: Int?, val insight: String,
)

/** Minggu berjalan vs rencana. Null bila tidak ada hari rencana (selain auto) atau tidak ada yang berbobot. */
fun Snapshot.planAdherence(): PlanAdherence? {
    val hasType = settings.plan.values.any { it.t.isNotEmpty() && it.t != "auto" }
    if (!hasType) return null
    val w0 = dayOf(nowMs).mondayOf()
    val weekActs = acts.filter { it.kind == ActKind.RUN && dayOf(it.start).let { d -> d >= w0 && d < w0.plusDays(7) } }
    var planKm = 0.0
    var planN = 0
    var doneN = 0
    for (d in 0 until 7) {
        val day = w0.plusDays(d.toLong())
        val p = settings.plan[day.dayOfWeek.value % 7] ?: continue
        if (p.t.isEmpty() || p.t == "auto" || p.t == "rest") continue
        planN++
        planKm += p.km
        if (weekActs.any { dayOf(it.start) == day }) doneN++
    }
    val doneKm = weekActs.sumOf { it.km }
    if (!(planKm > 0 || planN > 0)) return null
    val pctKm = if (planKm > 0) Math.round(doneKm / planKm * 100).toInt() else null
    val pctN = if (planN > 0) Math.round(doneN.toDouble() / planN * 100).toInt() else null
    val ins = when {
        pctKm != null && pctKm in 90..120 -> "Volume minggu ini mendekati rencana. Pertahankan konsistensi."
        pctKm != null && pctKm < 70 -> "Masih di bawah rencana. Lebih baik selesaikan sisa hari dengan easy daripada memaksa quality."
        pctKm != null && pctKm > 130 -> "Sudah jauh di atas rencana. Hati-hati overload, prioritaskan recovery."
        else -> "Rencana hanya panduan; sesuaikan dengan kesiapan harian di beranda."
    }
    return PlanAdherence(planKm, doneKm, planN, doneN, pctKm, pctN, ins)
}

data class FitPoint(val day: LocalDate, val ctl: Double, val atl: Double)

/** Titik CTL/ATL dari wellness dalam rentang [n] minggu; diringkas ke <= 80 titik. Kosong bila kurang dari 3. */
fun Snapshot.fitPoints(n: Int): List<FitPoint> {
    val from = dayOf(nowMs).mondayOf().minusWeeks((n - 1).toLong())
    val all = wellness.mapNotNull { w ->
        val c = w.ctl ?: return@mapNotNull null
        val a = w.atl ?: return@mapNotNull null
        val d = try { LocalDate.parse(w.id) } catch (e: Exception) { return@mapNotNull null }
        if (d < from) null else FitPoint(d, c, a)
    }
    if (all.size < 3) return emptyList()
    if (all.size <= 80) return all
    val step = Math.ceil(all.size / 80.0).toInt()
    return all.filterIndexed { i, _ -> i % step == 0 || i == all.size - 1 }
}

data class FitSummary(val ctl: Double, val dCtl: Double, val tsb: Double, val label: String, val insight: String)

/** Ringkasan memakai titik penuh pertama dan terakhir ([pts] boleh sudah diringkas; hasil HTML memakai data penuh). */
fun fitSummary(pts: List<FitPoint>): FitSummary? {
    if (pts.size < 3) return null
    val l = pts.last()
    val f = pts.first()
    val tsb = l.ctl - l.atl
    val dCtl = l.ctl - f.ctl
    val tl = when {
        tsb > 10 -> "segar/siap"
        tsb >= -10 -> "seimbang"
        tsb >= -30 -> "sedang membangun"
        else -> "sangat lelah"
    }
    fun one(x: Double) = (Math.round(x * 10) / 10.0).toString()
    val ins = when {
        tsb < -25 -> "TSB sangat negatif: beban akut tinggi. Utamakan easy/rest sampai TSB naik mendekati nol."
        tsb > 15 && dCtl < 1 -> "Kamu segar tapi kebugaran (CTL) datar. Ini jendela bagus untuk quality terkontrol."
        dCtl >= 3 -> "CTL naik +${one(dCtl)} di rentang ini. Kapasitas kronis bertambah. Pertahankan kenaikan gradual."
        dCtl <= -3 -> "CTL turun ${one(dCtl)}. Bisa deload yang sehat, atau tanda volume terlalu jarang."
        else -> "TSB ${one(tsb)} ($tl). Kebugaran relatif stabil; sesuaikan intensitas dengan skor kesiapan harian."
    }
    return FitSummary(l.ctl, dCtl, tsb, tl, ins)
}

/** Ambang "data tipis": kurang dari max(3, 25% rentang) minggu aktif. */
fun thinData(weeks: List<WeekBucket>, rangeWeeks: Int): Boolean = weeks.count { it.n >= 1 } < max(3, (rangeWeeks * 0.25).toInt())
