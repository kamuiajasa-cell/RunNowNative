package com.runnow.domain

import kotlin.math.max
import kotlin.math.min

private val ZONE_RE = Regex("Zone (\\d)(?:–(\\d))?")
private val ZONE_BOUNDS = mapOf(
    1 to (0.0 to 0.81), 2 to (0.81 to 0.89), 3 to (0.9 to 0.93), 4 to (0.94 to 0.99), 5 to (1.0 to 1.06),
)
private val RW_TITLES = listOf(
    "Easy Run", "Easy terbatas", "Easy recovery", "Long Run", "Easy taper", "Long dipotong", "Recovery Run",
)

private fun step(a: String, b: String) = a to b

/** Saran latihan hari ini, lengkap dengan dekorasi (zona HR, run-walk, kekuatan, nutrisi). */
fun Snapshot.designToday(): Recommendation {
    val st = stage()
    val r = designTodayCore(st)
    val ph = racePhase()
    var reason = r.reason
    if (ph != null && ph.k != "race") {
        val label = mapOf("taper" to "Taper", "peak" to "Peak", "build" to "Build")[ph.k]
        reason += " ($label · H-${ph.days})"
    }
    if (ph != null && ph.k == "race" && r.status == "done") reason += " Race hari ini sudah dilari. Pulihkan diri."
    return decorate(r.copy(reason = reason), st)
}

fun Snapshot.decorate(r: Recommendation, st: Stage = stage()): Recommendation {
    val a = settings.athlete
    val lthr = a.lthr
    val fl = flagInfo()
    var hr = r.hr
    var pace = r.pace
    val steps = r.steps.toMutableList()

    if (lthr != 0) {
        if (hr.contains("LTHR")) {
            hr = "Jangan lewat $lthr bpm di awal"
        } else {
            val m = ZONE_RE.find(hr)
            if (m != null) {
                val x = m.groupValues[1].toInt()
                val y = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: x
                val lo = jsRound(lthr * ZONE_BOUNDS.getValue(x).first)
                val hi = jsRound(lthr * ZONE_BOUNDS.getValue(y).second)
                val band = if (lo != 0L) "$lo–$hi" else "<$hi"
                hr = hr.replaceRange(m.range, m.value + " · " + band + " bpm")
            }
        }
    }
    if (a.ltPace.isNotEmpty() && pace == "Mendekati LTPace") pace = a.ltPace + " /km (LTPace)"

    if (rwOn() && r.status == "train" && st.s >= 3 && RW_TITLES.contains(r.title)) {
        val run = settings.rw.run
        val walk = settings.rw.walk
        val cyc = (run + walk) / 60.0
        val dur = parseIntJs(r.duration)?.takeIf { it != 0 } ?: 40
        val n = max(4L, jsRound((dur - 10) / cyc)).toInt()
        val hasNum = pace.any { it.isDigit() }
        steps.clear()
        steps += step("Pemanasan", "5 mnt jalan")
        steps += step(
            "Inti",
            "$n× (${rwT(run)} lari + ${rwT(walk)} jalan)" +
                (if (hasNum) ", lari @ " + pace.replaceFirst(" /km", "") else ""),
        )
        steps += step("Pendinginan", "5 mnt jalan")
        if (hasNum) pace += " (saat lari)"
    }

    if ((r.status == "rest" || r.tag == "RECOVERY") && !(fl != null && fl.lv == 2)) {
        steps += step(
            "Kekuatan",
            "15 mnt, 2×/minggu: glute bridge 2×12, calf raise 2×15, plank 2×30 dtk, keseimbangan 1 kaki 2×30 dtk",
        )
    }
    if ((r.title == "Easy Run" || r.title == "Easy terbatas") && r.score > 65 && (dow == 2 || dow == 4)) {
        steps += step("Opsional", "4× 20 dtk strides santai, jeda jalan 1 mnt")
    }
    if (r.title == "Long Run" && (parseIntJs(r.duration) ?: 0) >= 60) {
        steps += step("Nutrisi", "Karbo 30–60 g/jam mulai menit 45, minum 150–250 ml tiap 20 mnt")
    }
    if (r.title == "Hari lomba" && settings.raceDist >= 21) {
        steps += step("Nutrisi", "Karbo 40–60 g/jam sejak menit 40, minum di tiap pos")
    }
    return r.copy(hr = hr, pace = pace, steps = steps)
}

private class QualitySession(
    val title: String,
    val dur: Int,
    val hr: String,
    val pace: String,
    val core: String,
    val wl: List<String>,
    val why: String,
)

fun Snapshot.designTodayCore(st: Stage = stage()): Recommendation {
    val score = readiness() ?: 60 // netral-konservatif bila data belum cukup
    val style = settings.style.ifEmpty { "moderate" }
    val qOk = st.s > 2
    val au = autonomic()
    val fl = flagInfo()
    val heat = heatOn()
    val t0 = today
    val sm = st.m

    // ---- Sudah lari hari ini ----
    val done = acts.filter { d0(it.start) == t0 && (it.kind == ActKind.RUN || it.kind == ActKind.WALK) }
    if (done.isNotEmpty()) {
        val km = done.sumOf { it.km }
        val mins = done.sumOf { it.min }
        val pace = if (km > 0) mins / km else 0.0
        var hrSum = 0.0
        var hrW = 0.0
        for (a in done) {
            if (a.avgHr > 0 && a.min > 0) {
                hrSum += a.avgHr * a.min
                hrW += a.min
            }
        }
        val avgHr = if (hrW > 0) jsRound(hrSum / hrW) else 0L
        val main = done.sortedByDescending { it.min }.first()
        val det = if (main.kind == ActKind.RUN) detectType(main, st) else null
        return Recommendation(
            k = "ok", status = "done", title = "Sudah selesai", duration = fmtM(mins),
            hr = if (avgHr != 0L) "HR $avgHr bpm" else "—",
            pace = if (pace > 0) mp(pace) + " /km" else "—",
            reason = "${f1(km)} km · ${fmtM(mins)} dari Intervals.icu" +
                (if (det != null && det.type != "easy") " · terdeteksi ${VX_LB[det.type] ?: det.type}" else "") +
                ". Bagus.",
            steps = emptyList(), score = score, tag = "HARI INI",
        )
    }

    // ---- Form (TSB) dari Intervals ----
    val latest = wellness.lastOrNull()
    val lCtl = latest?.ctl
    val lAtl = latest?.atl
    val lTsb = latest?.tsb
    val tsb: Double? = if (lCtl != null && lAtl != null) lCtl - lAtl else lTsb

    // ---- Flag sakit mengalahkan semua yang lain ----
    if (fl != null && fl.lv == 2) {
        if (fl.dayN <= 3) {
            return Recommendation(
                k = "bad", status = "rest", title = "Istirahat total", duration = "—", hr = "—", pace = "—",
                reason = "Kaki sakit, hari ke-${fl.dayN} dari ${FLAG_DAYS[2]}. Rest. Jangan tes kaki dengan lari. " +
                    "Kalau nyeri tidak turun setelah 3 hari, periksakan ke dokter atau fisioterapis.",
                steps = listOf(step("Saran", "Kompres bila bengkak, jalan seperlunya saja")),
                score = score, tag = "RECOVERY",
            )
        }
        if (fl.dayN >= 8) {
            return Recommendation(
                k = "wn", status = "train", title = "Kembali lari bertahap", duration = "20 menit",
                hr = "Zone 1–2", pace = "Santai",
                reason = "Kaki sakit, hari ke-${fl.dayN} dari ${FLAG_DAYS[2]}. Coba lari pendek dulu. " +
                    "Berhenti dan tandai Sakit lagi bila nyeri muncul.",
                steps = listOf(
                    step("Pemanasan", "5 mnt jalan"),
                    step("Inti", "5× (1 mnt lari + 2 mnt jalan), berhenti bila nyeri"),
                    step("Pendinginan", "3 mnt jalan"),
                ),
                score = score, stage = 1, tag = "RECOVERY",
            )
        }
        return Recommendation(
            k = "wn", status = "train", title = "Jalan sangat mudah", duration = "20–30 menit",
            hr = "Zone 1", pace = "Santai, tanpa lari",
            reason = "Kaki sakit, hari ke-${fl.dayN} dari ${FLAG_DAYS[2]}. Jalan saja. Berhenti kalau nyeri naik. " +
                "Lari lagi setelah jalan 30 menit tanpa nyeri, mulai dari easy pendek.",
            steps = listOf(step("Inti", "20–30 mnt jalan santai"), step("Batas", "Nyeri naik = pulang")),
            score = score, stage = 1, tag = "RECOVERY",
        )
    }

    // ---- Beban 7 hari dan kemarin ----
    var qualityCount = 0
    for (a in acts) {
        if (nowMs - a.start > 7 * DAY_MS) continue
        val det = detectType(a, st)
        if (det.type == "thr" || det.type == "vo2" || loadOf(a) > 75) qualityCount++
    }

    val y0 = t0 - DAY_MS
    var yHard = false
    var yLong = false
    var yLoad = 0
    for (a in acts.filter { d0(it.start) == y0 && it.kind == ActKind.RUN }) {
        yLoad += loadOf(a)
        val d = detectType(a, st)
        if (d.type == "thr" || d.type == "vo2" || d.type == "race") yHard = true
        if (d.type == "long" || a.km >= 14) yLong = true
        if (sm != null && a.km > 0 && a.min > 0) {
            val p = metsOf(a.min / a.km) / sm
            if (p > 0.8) yHard = true
        }
    }
    if (yLoad >= 90) yHard = true

    val dow = this.dow
    val pl = planOf(dow)
    val ph = racePhase()
    fun ep(): Pair<Double, Double> {
        val e = easyRange(score, au, heat, st)
        var lo = e.lo
        var hi = e.hi
        if (sm != null && st.s == 3) {
            lo = max(lo, paceOf(0.7 * sm))
            hi = lo * 1.08
        }
        return lo to hi
    }

    // ---- Hari lomba ----
    if (ph != null && ph.k == "race") {
        val gp = goalPace()
        return Recommendation(
            k = "ok", status = "train", title = "Hari lomba", duration = "Sesuai target",
            hr = "Jangan lewat LTHR di awal",
            pace = if (gp != 0.0) mp(gp) + " /km target · 1–2 km awal +5–10 dtk/km"
            else "Mulai tahan, 1–2 km pertama lebih lambat dari target",
            reason = "Hari H. Jangan coba hal baru: sepatu, gel, pace. Start terlalu kencang yang paling sering menghancurkan race.",
            steps = listOf(
                step("Pemanasan", "10 mnt jog + 3 strides"),
                step("Inti", "Race. Negative split lebih aman"),
                step("Pendinginan", "10 mnt jalan"),
            ),
            score = score, stage = st.s, tag = "LOMBA",
        )
    }

    // ---- Hari lari berturut-turut ----
    var cons = 0
    for (i in 1..7) {
        val t = t0 - i * DAY_MS
        if (acts.any { d0(it.start) == t && it.kind == ActKind.RUN }) cons++ else break
    }
    if (cons >= (if (st.s <= 2) 3 else 5)) {
        return Recommendation(
            k = "wn", status = "rest", title = "Istirahat (wajib)", duration = "—", hr = "—", pace = "—",
            reason = "Sudah $cons hari lari berturut-turut. Satu hari rest menjaga tulang dan tendon beradaptasi.",
            steps = emptyList(), score = score, tag = "RECOVERY",
        )
    }

    // ---- Taper ----
    if (ph != null && ph.k == "taper" && st.s >= 3 && score >= 42) {
        val d = ph.days
        val (lo, hi) = ep()
        if (pl != null && pl.t == "rest") {
            return Recommendation(
                k = "ok", status = "rest", title = "Rest (rencana)", duration = "—", hr = "—", pace = "—",
                reason = "H-$d. Rest sesuai rencana. Kaki lagi dikumpulkan, jangan diisi.",
                steps = emptyList(), score = score, tag = "RECOVERY",
            )
        }
        if (d <= 2) {
            return Recommendation(
                k = "ok", status = "train", title = "Shakeout", duration = "20 menit", hr = "Zone 1–2",
                pace = mp(hi) + "–" + mp(hi * 1.08) + " /km",
                reason = "H-$d. Cuma buka kaki. Kalau terasa berat, jalan saja.",
                steps = listOf(step("Inti", "15 mnt jog santai"), step("Akhir", "3 × 20 dtk strides, jeda penuh")),
                score = score, stage = st.s, tag = "TAPER",
            )
        }
        if (pl != null && pl.t == "quality" && d >= 8 && !au.lock && fl == null && !yHard && !yLong &&
            score >= 65 && qualityCount < 1
        ) {
            return Recommendation(
                k = "wn", status = "train", title = "Sharpener singkat", duration = "30 menit total",
                hr = "Zone 3–4", pace = "Mendekati LTPace",
                reason = "H-$d. Quality terakhir sebelum race, volumenya dipotong. Rasa tajam, bukan capek.",
                steps = listOf(
                    step("Pemanasan", "10 mnt easy + strides"),
                    step("Inti", "2×5 mnt threshold, jeda 2 mnt"),
                    step("Pendinginan", "8 mnt easy"),
                ),
                score = score, stage = st.s, tag = "TAPER",
            )
        }
        var mins = if (d > 7) 40 else 30
        val longCut = pl != null && pl.t == "long"
        if (longCut) {
            mins = if (d > 7) 60 else 40
        } else if (pl != null && pl.km > 0) {
            mins = min(mins.toLong(), max(20L, jsRound(pl.km * (lo + hi) / 2))).toInt()
        }
        if (fl != null) mins = max(15L, jsRound(mins * 0.7)).toInt()
        return Recommendation(
            k = "ok", status = "train", title = if (longCut) "Long dipotong" else "Easy taper",
            duration = "$mins menit", hr = "Zone 2", pace = mp(lo) + "–" + mp(hi) + " /km",
            reason = "H-$d. " +
                (if (longCut) "Long dipotong jadi $mins menit." else "Volume turun, intensitas dijaga.") +
                (if (pl != null && pl.t == "quality") " Quality di rencana ditahan, terlalu dekat race atau kondisi belum cukup." else ""),
            steps = listOf(
                step("Pemanasan", "5 mnt jog"),
                step("Inti", "${max(10, mins - 10)} mnt @ ${mp(lo)}–${mp(hi)}"),
                step("Pendinginan", "5 mnt easy"),
            ),
            score = score, stage = st.s, tag = "TAPER",
        )
    }

    // ---- Rest total ----
    if (score < 42 || (tsb != null && tsb < -25)) {
        return Recommendation(
            k = "bad", status = "rest", title = "Istirahat Total", duration = "—", hr = "—", pace = "—",
            reason = if (tsb != null) "Form sangat rendah (TSB ${toFixed0(tsb)}). Prioritaskan recovery."
            else "Kesiapan rendah. Recovery dulu.",
            steps = listOf(step("Saran", "Jalan santai 20–30 mnt, tanpa target")),
            score = score, tag = "RECOVERY",
        )
    }

    // ---- Recovery run ----
    if (score < 55 || au.severe || (tsb != null && tsb < -12) || qualityCount >= 3 ||
        (style == "conservative" && score < 65)
    ) {
        return Recommendation(
            k = "wn", status = "train", title = "Recovery Run", duration = "30–45 menit",
            hr = "Zone 1–2", pace = "Sangat mudah",
            reason = if (au.severe) "Sinyal tubuh jelek (${au.why}). Recovery dulu, quality dikunci."
            else if (qualityCount >= 3) "Sudah $qualityCount sesi berat minggu ini. Recovery dulu."
            else "Form masih negatif atau kesiapan sedang. Jaga tetap mudah.",
            steps = listOf(
                step("Pemanasan", "3 mnt jalan"),
                step("Inti", "25–35 mnt sangat mudah, bisa ngobrol bebas"),
                step("Pendinginan", "3 mnt jalan"),
            ),
            score = score, tag = "RECOVERY",
        )
    }

    // ---- Tahap 1: jalan ----
    if (st.s == 1) {
        val mins = if (score > 70) 35 else 25
        return Recommendation(
            k = "ok", status = "train", title = "Jalan cepat", duration = "$mins menit",
            hr = "Zone 2 (napas)", pace = "10:00–10:40 /km",
            reason = if (sm != null)
                "Tahap fondasi. Max ≈ ${f1(sm)} METs. Jalan cepat ~4,8 METs = stimulus yang tepat. Naik ke run-walk bila estimasi ≥ 7,9."
            else "Bangun fondasi aerobik dulu sebelum non-stop.",
            steps = listOf(
                step("Pemanasan", "3 mnt jalan"),
                step("Inti", "${mins - 6} mnt jalan cepat, masih bisa bicara"),
                step("Pendinginan", "3 mnt jalan"),
            ),
            score = score, stage = 1, tag = "FONDASI",
        )
    }

    // ---- Tahap 2: run-walk ----
    if (st.s == 2) {
        val w = walkRunWorkout(Math.floorMod(t0 / DAY_MS, 3L).toInt(), if (score > 68) 1.0 else 0.75)
        val pr = if (sm != null) paceOf(0.9 * sm) else 9.5
        return Recommendation(
            k = "ok", status = "train", title = "Run-walk · White Plan ${w.k}", duration = "${w.tot} menit",
            hr = "Ikuti napas", pace = mp(pr) + "–" + mp(pr * 1.08) + " /km (saat lari)",
            reason = if (sm != null)
                "Lari non-stop 8:15 ≈ ${jsRound((metsOf(8.25) / sm) * 100)}% kapasitas — tahan ${durText(metsOf(8.25) / sm)}. " +
                    "Makanya run-walk dulu; capek di menit awal wajar."
            else "Bangun toleransi lari dengan pola Daniels White Plan fase I.",
            steps = w.steps, score = score, stage = 2, tag = "FONDASI",
        )
    }

    // ---- Rencana minggu: rest ----
    if (pl != null && pl.t == "rest") {
        return Recommendation(
            k = "ok", status = "rest", title = "Rest (rencana)", duration = "—", hr = "—", pace = "—",
            reason = "Rest sesuai rencana minggu. Recovery juga latihan.",
            steps = listOf(step("Saran", "Tidur cukup, jalan santai kalau mau")),
            score = score, tag = "RECOVERY",
        )
    }

    // ---- Rencana minggu: long (tahap 3+, tidak setelah hari keras/long) ----
    val wk = planWeekKm()
    if (pl != null && pl.t == "long" && st.s >= 3 && fl == null && !yHard && !yLong) {
        val (lo, hi) = ep()
        val cut = cutbackDue(st)
        val mins0 = if (pl.km > 0) clamp(jsRound(pl.km * (lo + hi) / 2).toDouble(), 50.0, 150.0).toInt() else 75
        val mins = if (cut) max(40L, jsRound(mins0 * 0.7)).toInt() else mins0
        val over = wk > 0 && pl.km > 0.3 * wk
        return Recommendation(
            k = "wn", status = "train", title = "Long Run", duration = "$mins menit", hr = "Zone 2",
            pace = mp(lo) + "–" + mp(hi * 1.05) + " /km",
            reason = "Long sesuai rencana. Pace easy plus sedikit, bukan race pace." +
                (if (pl.km != 0.0) " Target ${if (cut) numStr(jsRound(pl.km * 0.7).toDouble()) else numStr(pl.km)} km." else "") +
                (if (cut) " Cutback: 3 minggu long tanpa turun, minggu ini dipangkas ~30%. Saran, boleh diabaikan." else "") +
                (if (over) " Long ${jsRound((pl.km / wk) * 100)}% volume minggu, di atas batas ~30%." else ""),
            steps = listOf(
                step("Pemanasan", "10 mnt jog"),
                step("Inti", "${max(30, mins - 15)} mnt easy, stabil"),
                step("Pendinginan", "5 mnt jalan"),
            ),
            score = score, stage = st.s, tag = "LONG",
        )
    }

    // ---- Guardrail 48 jam ----
    val y2 = t0 - 2 * DAY_MS
    val y2Long = acts.any {
        d0(it.start) == y2 && it.kind == ActKind.RUN && (detectType(it, st).type == "long" || it.km >= 14)
    }
    val tmrLong = hasPlan() && planOf((dow + 1) % 7)?.t == "long"

    val wantQuality = qOk && fl == null && !au.lock && !yHard && !yLong &&
        (style == "aggressive" || (style == "moderate" && score >= 72)) &&
        (if (hasPlan()) (pl != null && pl.t == "quality") else (dow == 2 || dow == 4 || dow == 5)) &&
        !y2Long && !tmrLong && qualityCount < 2 && st.s >= 3

    if (wantQuality) {
        // Rotasi: tempo -> cruise -> VO2 (VO2 hanya form tinggi, tahap >= 4). Durasi kerja naik bertahap.
        val n28 = acts.count {
            it.kind == ActKind.RUN && nowMs - it.start <= 28 * DAY_MS &&
                (detectType(it, st).type == "thr" || detectType(it, st).type == "vo2")
        }
        val lv = min(3, n28)
        val vo2Ok = score >= 75 && st.s >= 4 && !au.warn && !heat
        val hillsOk = hillsRelevant()
        val idx = n28 % 3
        val gp = goalPace()
        val rp = ph != null && ph.k == "peak" && gp > 0 && n28 % 2 == 1
        val kindQ = if (rp) "racepace"
        else if (idx == 0) "tempo"
        else if (idx == 1) "cruise"
        else if (vo2Ok && !(hillsOk && (n28 / 3) % 2 == 1)) "vo2"
        else if (hillsOk) "hills"
        else "cruise"

        val q: QualitySession = when (kindQ) {
            "racepace" -> {
                val dd = if (settings.raceDist != 0.0) settings.raceDist else 10.0
                val w = if (dd >= 21) 10 else if (dd >= 10) 8 else 5
                val r = (if (dd >= 21) listOf(2, 3, 3, 4) else listOf(3, 4, 4, 5))[lv]
                QualitySession(
                    "Race-pace intervals", 18 + r * (w + 2) - 2, "Zone 3–4", mp(gp) + " /km (target race)",
                    "$r×$w mnt di pace target, jeda 2 mnt jog",
                    listOf("Warmup", "- 10m Z2 HR", "", "${r}x", "- ${w}m Z4 HR", "- 2m Z1 HR", "", "Cooldown", "- 8m Z1 HR"),
                    "Race-pace $r×$w mnt: melatih tubuh memegang pace target.",
                )
            }
            "tempo" -> {
                val w = listOf(20, 23, 26, 30)[lv]
                QualitySession(
                    "Tempo continuous", w + 18, "Zone 3–4", "Mendekati LTPace",
                    "$w mnt tempo steady, tepat di bawah LTPace",
                    listOf("Warmup", "- 10m Z2 HR", "", "Main set", "- ${w}m Z4 HR", "", "Cooldown", "- 8m Z1 HR"),
                    "Tempo continuous $w mnt: membangun daya tahan di threshold.",
                )
            }
            "cruise" -> {
                val (r, w) = listOf(4 to 5, 4 to 6, 5 to 6, 5 to 7)[lv]
                QualitySession(
                    "Cruise intervals", 18 + r * w + (r - 1), "Zone 4", "Mendekati LTPace",
                    "$r×$w mnt threshold, jeda 1 mnt jog",
                    listOf("Warmup", "- 10m Z2 HR", "", "${r}x", "- ${w}m Z4 HR", "- 1m Z1 HR", "", "Cooldown", "- 8m Z1 HR"),
                    "Cruise $r×$w mnt: threshold terkendali dengan jeda pendek.",
                )
            }
            "hills" -> {
                val r = listOf(6, 7, 8, 8)[lv]
                QualitySession(
                    "Hill repeats", jsRound(18 + r * 3.25).toInt(), "Zone 4–5 (usaha, bukan pace)",
                    "Tanjakan 4–6%, usaha ≈ 5K",
                    "$r×75 dtk tanjakan keras tapi terkontrol, turun jog 2 mnt",
                    listOf("Warmup", "- 10m Z2 HR", "", "${r}x", "- 75s Z4 HR", "- 2m Z1 HR", "", "Cooldown", "- 8m Z1 HR"),
                    "Hills $r×75 dtk: kekuatan dan ekonomi lari tanpa beban pace. Medanmu memang berbukit.",
                )
            }
            else -> {
                val r = if (lv < 1) 4 else 5
                QualitySession(
                    "VO2 singkat", 18 + r * 3 + (r - 1) * 2, "Zone 5", "Sekitar pace 5K, terkontrol",
                    "$r×3 mnt keras tapi terkontrol, jeda 2 mnt jog",
                    listOf("Warmup", "- 10m Z2 HR", "", "${r}x", "- 3m Z5 HR", "- 2m Z1 HR", "", "Cooldown", "- 8m Z1 HR"),
                    "VO2 $r×3 mnt: stimulus singkat. Form lagi tinggi, jadi boleh.",
                )
            }
        }
        return Recommendation(
            k = "wn", status = "train", title = q.title, duration = "${q.dur} menit total", hr = q.hr, pace = q.pace,
            reason = q.why + " Quality ke-${n28 + 1} dalam 28 hari. Bukan all-out, kerja terkontrol." +
                (if (heat) " Panas: ikuti HR, bukan pace. Pace boleh melambat." else ""),
            steps = listOf(
                step("Pemanasan", "10 mnt easy + strides"),
                step("Inti", q.core),
                step("Pendinginan", "8 mnt easy"),
            ),
            score = score, stage = st.s, tag = "QUALITY", wl = q.wl,
        )
    }

    // ---- Easy ----
    val er = easyRange(score, au, heat, st)
    var lo = er.lo
    var hi = er.hi
    if (sm != null && st.s == 3) {
        val cap = paceOf(0.7 * sm)
        lo = max(lo, cap)
        hi = lo * 1.08
    }
    var mins = if (score > 72) 50 else if (score > 55) 40 else 32
    if (pl != null && pl.t == "easy" && pl.km > 0) {
        mins = clamp(jsRound(pl.km * (lo + hi) / 2).toDouble(), 20.0, 90.0).toInt()
    }
    if (yHard || yLong) mins = min(mins, 35)
    if (fl != null) mins = max(15L, jsRound(mins * 0.7)).toInt()

    val easyNote = (if (er.src == "baseline") " Pace dari easy 4 minggu terakhirmu (~${mp(er.base ?: 0.0)})." else "") +
        (if (heat) " Panas: pace dilonggarkan ~6%, pakai HR sebagai patokan." else "") +
        (if (fl != null) " Kaki was-was hari ke-${fl.dayN}/${FLAG_DAYS[1]}: volume −30%, tanpa quality. Kalau nyeri muncul saat lari, berhenti dan tandai Sakit." else "")
    val planNote = if (pl != null && (pl.t == "quality" || pl.t == "long") && !wantQuality) {
        " Rencana hari ini " + (if (pl.t == "quality") "quality" else "long") + ", digeser jadi easy: " +
            (if (au.lock) "sinyal tubuh jelek (${au.why})."
            else if (fl != null) "kaki was-was."
            else if (tmrLong) "besok long, jarak kurang dari 48 jam."
            else "kondisi atau guardrail belum mengizinkan.")
    } else ""
    val yNote = planNote + (if (yHard) " Kemarin keras — hari ini wajib easy jujur."
    else if (yLong) " Kemarin long — volume pendek saja." else "")

    val mid = (lo + hi) / 2
    return Recommendation(
        k = if (yHard || yLong) "wn" else "ok", status = "train",
        title = if (st.s == 3) "Easy terbatas" else if (yHard || yLong) "Easy recovery" else "Easy Run",
        duration = "$mins menit", hr = "Zone 2", pace = mp(lo) + "–" + mp(hi) + " /km",
        reason = (if (sm != null) {
            if (st.s == 3) {
                "Pace dijaga ≤~70% kapasitas (Max ≈ ${f1(sm)}). ≈ ${jsRound((metsOf(mid) / sm) * 100)}% Max · tahan ${durText(metsOf(mid) / sm)}."
            } else {
                "≈ ${jsRound((metsOf(mid) / sm) * 100)}% Max · tahan ${durText(metsOf(mid) / sm)}. Konsistensi > intensitas."
            }
        } else "Bangun volume aerobik. Tetap di zona nyaman.") + yNote + easyNote,
        steps = listOf(
            step("Pemanasan", "5 mnt jog"),
            step("Inti", "${mins - 10} mnt @ ${mp(lo)}–${mp(hi)}"),
            step("Pendinginan", "5 mnt easy"),
        ),
        score = score, stage = st.s, tag = "AEROBIK",
    )
}
