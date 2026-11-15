package com.runnow.domain

import kotlin.math.abs

/** Satu catatan review. [k]: "g" (hijau/baik), "y" (kuning/waspada), "r" (merah/berat). */
data class ReviewNote(val k: String, val text: String)

data class ReviewSection(val title: String, val notes: List<ReviewNote>)

/** Hasil reviewRun. [headline] null bila aktivitas bukan lari valid; [sections] hanya yang berisi catatan. */
data class RunReview(
    val tone: String,
    val headline: String?,
    val summary: String,
    val sections: List<ReviewSection>,
    val flat: List<ReviewNote>,
)

data class ReviewHeadline(val k: String, val text: String)

/**
 * reviewRun() di HTML: review lari komprehensif (intensitas, eksekusi, beban, dan "lanjut").
 * Membaca LTHR dan LTPace dari [Settings.athlete]; isi lewat [withProcessed] setelah processData, sama seperti
 * HTML yang membacanya dari state.athlete. Ringkasan stream dibaca dari [Snapshot.streams] berdasarkan icuId.
 */
fun Snapshot.reviewRun(a: Act): RunReview {
    val empty = RunReview("g", null, "", emptyList(), emptyList())
    if (a.kind != ActKind.RUN || !(a.km > 0) || !(a.min > 0)) return empty

    val st = stage()
    val bigM = st.m
    val pace = a.min / a.km
    val dur = a.min
    val km = a.km
    val hr = if (a.avgHr > 0) a.avgHr else 0.0
    val elev = if (a.elev > 0) a.elev else 0.0
    val maxH = if (a.maxHr > 0) a.maxHr else 0.0
    val icuLoad = if (a.load > 0) a.load else 0.0
    val load = if (icuLoad > 0) icuLoad else loadOf(a).toDouble()
    val det = detectType(a, st)
    val c = metsOf(pace)
    val pct: Double? = if (bigM != null) c / bigM else null
    val lim = pctAt(dur)
    val lthr = settings.athlete.lthr
    val ltPace = settings.athlete.ltPace
    val rst = restHr()
    val mx = maxHr()
    val name = a.name.trim()

    val sInt = ArrayList<ReviewNote>()
    val sEx = ArrayList<ReviewNote>()
    val sLoad = ArrayList<ReviewNote>()
    val sNext = ArrayList<ReviewNote>()
    fun push(sec: MutableList<ReviewNote>, k: String, text: String) {
        sec += ReviewNote(k, text)
    }
    val why = ArrayList<String>()
    val typeLabel = VX_LB[det.type] ?: det.type

    // —— Summary line ——
    val summary =
        f1(km) + " km · " + fmtM(dur) + " · " + mp(pace) + "/km" +
            (if (hr > 0) " · HR " + jsRound(hr) + (if (maxH > 0) "–" + jsRound(maxH) else "") else "") +
            (if (load > 0) " · load " + jsRound(load) else "") +
            (if (elev > 0) " · tanjakan " + jsRound(elev) + " m" else "") +
            (if (det.type.isNotEmpty()) " · $typeLabel" else "") +
            (if (name.isNotEmpty()) " · “" + name.take(28) + (if (name.length > 28) "…" else "") + "”" else "")

    // —— 1. Intensitas (METs / capacity) ——
    if (bigM != null && pct != null) {
        push(
            sInt, "g",
            "Sesi ini ≈ " + jsRound(pct * 100) + "% dari kapasitasmu (Max ≈ " + f1(bigM) + " METs). Di intensitas ini tubuh biasanya tahan " +
                durText(pct) + ". Batas nyaman untuk " + fmtM(dur) + " sekitar " + jsRound(lim * 100) + "%.",
        )

        if (dur >= 10 && pct > lim + 0.07) {
            push(
                sInt, "y",
                "Di atas ambang tahan untuk durasi ini. Capek di tengah wajar — bukan tanda lemah. " +
                    "Untuk bangun kapasitas, target ≈65% (pace ≈ " + mp(paceOf(0.65 * bigM)) + "/km) atau run-walk.",
            )
        } else if (dur >= 20 && pct <= 0.68) {
            push(sInt, "g", "Intensitas rata-ratanya masih zona aerobik (≤68% kapasitas), cocok ditahan 45–90+ menit.")
        } else if (pct >= 0.9) {
            push(sInt, "r", "Sangat dekat kapasitas maksimal. Hanya aman sebagai blok pendek / tes, bukan volume panjang.")
        } else if (pct > 0.78) {
            push(sInt, "y", "Zona quality/threshold. Pastikan ini disengaja, bukan easy yang kebablasan.")
        }

        // Bandingkan dengan LTPace bila ada
        if (ltPace.isNotEmpty() && pace > 0) {
            val parts = ltPace.split(":")
            if (parts.size == 2) {
                val p0 = parts[0].toDoubleOrNull()
                val p1 = parts[1].toDoubleOrNull()
                val ltMin = if (p0 != null && p1 != null) p0 + p1 / 60 else Double.NaN
                if (ltMin > 0) {
                    val delta = (pace - ltMin) * 60 // + = lebih lambat dari LT
                    if (delta < -5) {
                        push(sInt, "y", "Pace lebih kencang dari LTPace (~" + ltPace + ") sekitar " + jsRound(-delta) + " dtk/km.")
                    } else if (delta > 40 && det.type == "easy") {
                        push(sInt, "g", "Jauh di bawah LTPace (~" + ltPace + ") — easy yang aman.")
                    } else if (abs(delta) <= 12 && (det.type == "thr" || pct > 0.78)) {
                        push(sInt, "g", "Dekat LTPace (~" + ltPace + ") — eksekusi threshold masuk akal.")
                    }
                }
            }
        }
    } else {
        push(sInt, "y", "Belum ada estimasi Max METs yang stabil. Intensitas dibaca kasar dari HR/pace saja. Sinkron lebih banyak lari agar kapasitas terbaca.")
    }

    // —— Analisis HR ——
    if (hr > 0 && lthr > 0) {
        val ratio = hr / lthr
        if (ratio >= 1.02) {
            push(sInt, "r", "Detak rata-rata (" + jsRound(hr) + ") sudah di atas LTHR (~" + lthr + " bpm). Ini intensitas threshold atau lebih.")
        } else if (ratio >= 0.92) {
            push(sInt, "y", "Detak dekat LTHR (" + jsRound(hr) + " dari ~" + lthr + "). Ini zona sesi keras.")
        } else if (ratio <= 0.82) {
            push(sInt, "g", "Detak jauh di bawah LTHR, terasa mudah buat jantung.")
        } else {
            push(sInt, "g", "Detak rata-rata " + jsRound(hr) + " bpm (~" + jsRound(ratio * 100) + "% LTHR).")
        }
        if (maxH > hr + 15) {
            why += "detak sempat melonjak"
            push(sEx, "y", "Detak jantung sempat naik sampai " + jsRound(maxH) + ", mungkin karena tanjakan atau sempat ngebut.")
        }
    } else if (hr > 0 && rst > 0 && mx > rst) {
        val hrr = (hr - rst) / (mx - rst)
        if (hrr >= 0.88) push(sInt, "r", "Detak ≈ " + jsRound(hrr * 100) + "% dari cadangan jantungmu, sangat keras.")
        else if (hrr >= 0.78) push(sInt, "y", "Detak ≈ " + jsRound(hrr * 100) + "% dari cadangan jantungmu, zona sesi keras.")
        else if (hrr <= 0.65) push(sInt, "g", "Detak ≈ " + jsRound(hrr * 100) + "% dari cadangan jantungmu, zona aerobik.")
        else push(sInt, "g", "HR ≈ " + jsRound(hrr * 100) + "% reserve.")
    } else if (!(hr > 0)) {
        push(sInt, "y", "Tanpa data HR. Analisis hanya dari pace/METs — pastikan jam terhubung ke Intervals.")
    }

    // —— 2. Eksekusi: konsistensi jenis sesi, pace vs riwayat ——
    push(sEx, "g", "Jenis sesi: $typeLabel (keyakinan " + det.conf + "), " + det.why + ".")

    if (det.type == "easy" && pct != null && pct > 0.75) {
        push(sEx, "y", "Berlabel easy tapi intensitas di atas zona easy. Kalau recovery, pelankan lain kali — ini sumber kelelahan tersembunyi.")
    }
    if ((det.type == "thr" || det.type == "vo2") && pct != null && pct < 0.72) {
        push(sEx, "y", "Label $typeLabel tapi intensitas relatif rendah. Konfirmasi jenis sesi jika perlu.")
    }
    if (det.type == "long") {
        push(sEx, if (km >= 16) "y" else "g", "Long " + f1(km) + " km. Makan cukup, tidur cukup, lalu beri 24–48 jam easy setelahnya.")
    }

    // Pace vs baseline easy terakhir
    val prev = acts.filter { x ->
        x.kind == ActKind.RUN && x.start < a.start && x.start > a.start - 28 * DAY_MS && x.km >= 3 && x.min > 0
    }
    val easyPaces = prev
        .filter { detectType(it, st).type == "easy" }
        .map { it.min / it.km }
        .filter { it > 3 && it < 12 }
    if (easyPaces.size >= 3) {
        val avg = easyPaces.fold(0.0) { s, p -> s + p } / easyPaces.size
        val diffSec = (pace - avg) * 60
        if (det.type == "easy" && diffSec < -20) {
            push(sEx, "y", "Easy " + jsRound(-diffSec) + " dtk/km lebih kencang dari easy 4 minggu terakhir (baseline ~" + mp(avg) + "/km).")
        } else if (det.type == "easy" && diffSec > 35) {
            push(sEx, "g", "Lebih pelan " + jsRound(diffSec) + " dtk/km dari easy biasa — pemulihan aktif yang baik.")
        } else if (det.type == "easy") {
            push(sEx, "g", "Pace easy selaras dengan baseline (~" + mp(avg) + "/km).")
        }
    }

    // Dua lari di hari yang sama?
    val sameDay = acts.filter { x -> x.kind == ActKind.RUN && d0(x.start) == d0(a.start) && x.start != a.start }
    if (sameDay.isNotEmpty()) {
        val extraKm = sameDay.fold(0.0) { s, x -> s + x.km }
        push(sEx, "y", "Ada " + sameDay.size + " lari lain di hari yang sama (+" + f1(extraKm) + " km). Total harian " + f1(km + extraKm) + " km.")
    }

    // Elevasi
    if (elev >= 150) {
        push(sEx, "y", "Elevasi +" + jsRound(elev) + " m — pace lebih lambat wajar; jangan bandingkan flat 1:1.")
    } else if (elev >= 60) {
        push(sEx, "g", "Elevasi +" + jsRound(elev) + " m.")
    }

    // Khusus tahap
    if (st.s <= 2) {
        push(sEx, "g", "Tahap fondasi (T" + st.s + "): bangun kapasitas dulu. Label zona di jam sering tidak stabil — ikuti napas dan %METs.")
    } else if (st.s == 3 && bigM != null && pct != null && pct > 0.72 && det.type == "easy") {
        push(sEx, "y", "Tahap 3: easy ideal ≤ ~70% kapasitas. Coba " + mp(paceOf(0.68 * bigM)) + "/km lain kali.")
    }

    // —— Stream (jika tersedia) ——
    val icuId = a.icuId
    val sx: StreamSummary? = if (!icuId.isNullOrEmpty()) streams[icuId] else null
    if (sx != null && sx.none) {
        push(sEx, "g", "Data stream tidak tersedia dari Intervals untuk sesi ini. Fade dan start tidak dianalisis.")
    } else if (sx != null) {
        val steady = det.type == "easy" || det.type == "long" || det.type == "race"
        val fast = sx.fast
        val fade = sx.fade
        val dec = sx.dec
        val tiz = sx.tiz
        if (steady && fast != null) {
            push(
                sEx, "y",
                "Start terlalu kencang: km 1–2 " + mp(fast.p12) + "/km vs median sisanya " + mp(fast.med) + "/km (" + fast.pct +
                    "% lebih cepat). Pelankan dua km pertama.",
            )
        }
        if (steady && fade != null) {
            push(sEx, "y", "Melambat di akhir: km terakhir " + fade.pct + "% lebih lambat dari bagian tengah. Mulai lebih pelan atau potong durasi.")
        }
        if (steady && dec != null) {
            push(
                sEx, if (dec.pct >= 5) "y" else "g",
                if (dec.pct >= 5) {
                    "Di paruh kedua detak jantung naik sekitar " + dec.pct + "% dibanding pace-nya. Dasar aerobikmu belum stabil untuk durasi ini."
                } else {
                    "Detak jantung stabil dari awal sampai akhir (selisih " + dec.pct + "%). Tanda dasar aerobik yang kuat."
                },
            )
        }
        if (tiz != null) {
            push(
                sEx, "g",
                "Sebaran waktu per zona detak jantung: " +
                    tiz.mapIndexed { i, v -> if (v > 0) "Z" + (i + 1) + " " + v + "%" else "" }.filter { it.isNotEmpty() }.joinToString(" · ") + ".",
            )
            if ((det.type == "easy" || det.type == "long") && tiz.drop(2).sum() >= 50) {
                why += "banyak waktu di intensitas sedang"
                push(sEx, "y", "Lebih dari separuh waktunya di Z3 ke atas, jadi lebih keras dari easy murni. Pelankan sedikit supaya benar-benar aerobik.")
            }
        }
    }

    // —— 3. Beban dan konteks minggu ——
    val weekLoad = loadDays(7)
    val weekKm = acts
        .filter { it.start >= nowMs - 7 * DAY_MS && it.kind == ActKind.RUN }
        .fold(0.0) { s, x -> s + x.km }

    push(
        sLoad, "g",
        "Beban sesi ini " + jsRound(load) + " poin (" + (if (icuLoad > 0) "dari Intervals" else "perkiraan app") +
            "). Total 7 hari terakhir ≈ " + f1(weekLoad.toDouble()) + " poin dan " + f1(weekKm) + " km.",
    )

    if (load >= 120) {
        push(sLoad, "r", "Beban sangat tinggi. Jangan lari keras atau long besok.")
    } else if (load >= 80) {
        push(sLoad, "y", "Beban sesi ini tinggi. Beri tubuh waktu untuk pulih.")
    }

    if (weekLoad > 0 && load / weekLoad >= 0.45) {
        push(sLoad, "y", "Sesi ini menyumbang " + jsRound((load / weekLoad) * 100) + "% dari beban 7 hari terakhir, porsi yang besar.")
    }

    // Beruntun berat
    var streak = 0
    for (i in 0 until 5) {
        val day = today - i * DAY_MS
        val dayActs = acts.filter { d0(it.start) == day && it.kind == ActKind.RUN }
        val dayLoad = dayActs.sumOf { loadOf(it) }
        val dayHard = dayActs.any { x ->
            val t = detectType(x, st).type
            t == "thr" || t == "vo2" || t == "race" || loadOf(x) >= 70
        }
        if (dayHard || dayLoad >= 70) streak++ else break
    }
    if (streak >= 3) {
        push(sLoad, "r", "$streak hari berturut-turut berat. Risiko kelelahan menumpuk, selipkan hari istirahat.")
    } else if (streak == 2) {
        push(sLoad, "y", "Dua hari beruntun cukup berat. Besok lebih aman easy atau libur.")
    }

    // Bandingkan dengan rata-rata beban sesi 14 hari sebelumnya
    val prevSessions = acts
        .filter { x -> x.kind == ActKind.RUN && x.start < a.start && x.start >= a.start - 14 * DAY_MS }
        .map { loadOf(it) }
        .filter { it > 0 }
    if (prevSessions.size >= 4) {
        val avgL = prevSessions.fold(0.0) { s, v -> s + v } / prevSessions.size
        if (load > avgL * 1.6) {
            why += "bebanmu naik cepat"
            push(sLoad, "y", "Sesi ini " + jsRound((load / avgL - 1) * 100) + "% lebih berat dari rata-rata lari 2 minggu terakhir.")
        }
    }

    // —— 4. Lanjut ——
    val hard = (pct != null && pct > 0.78) || load >= 80 || det.type == "thr" || det.type == "vo2" || det.type == "race"
    val longish = km >= 14 || det.type == "long"
    val tomorrow: String
    val toneNext: String
    if (hard && longish) {
        tomorrow = "Besok: istirahat penuh atau jalan santai 20–30 menit. Jangan lari keras."
        toneNext = "r"
    } else if (hard) {
        tomorrow = "Besok: lari santai (masih bisa ngobrol) atau istirahat. Jangan lari keras dua hari beruntun."
        toneNext = "y"
    } else if (longish) {
        tomorrow = "Besok: lari santai 30–40 menit atau jalan santai. Kaki butuh pulih, bukan dipaksa."
        toneNext = "y"
    } else if (streak >= 2) {
        tomorrow = "Besok: utamakan pemulihan. Skor kesiapan bagus belum tentu badan siap kalau beberapa hari terakhir berat."
        toneNext = "y"
    } else if (pct != null && pct <= 0.68) {
        tomorrow = "Besok: boleh lanjut sesuai rencana bila bangun tidur terasa oke."
        toneNext = "g"
    } else {
        tomorrow = "Besok: cek kondisi pagi (detak istirahat, mood, kaki). Kalau ragu, lari santai saja."
        toneNext = "g"
    }
    push(sNext, toneNext, tomorrow)

    if (det.type == "easy" && pct != null && pct <= 0.68) {
        push(sNext, "g", "Tujuan sesi santai tercapai. Inilah yang membangun dasar aerobik. Jangan dijadikan tempo hanya karena lagi enak.")
    } else if (det.type == "thr" || det.type == "vo2") {
        push(sNext, "g", "Sesi kerasnya sudah masuk. Nilainya ada di pemulihan dan lari santai berikutnya, bukan di seberapa kencang kamu bisa.")
    } else if (det.type == "long") {
        push(sNext, "g", "Long itu melatih ketahanan dan mental. Yang dihitung selesai dengan terkendali, bukan ngebut di 5 km terakhir.")
    }

    // —— Nada dan judul ——
    val allNotes = sInt + sEx + sLoad + sNext
    var tone = "g"
    val nR = allNotes.count { it.k == "r" }
    val nY = allNotes.count { it.k == "y" }
    if (nR >= 1) tone = "r" else if (nY >= 2) tone = "y"

    val headline = when {
        tone == "r" -> "Sesi ini berat. Prioritaskan pemulihan."
        tone == "y" ->
            if (why.isNotEmpty()) "Hati-hati: " + why.take(2).joinToString(" dan ") + ". Besok santai dulu."
            else "Ada sinyal untuk waspada. Besok santai dulu."
        det.type == "easy" && pct != null && pct <= 0.68 -> "Lari santai yang terkendali. Dasar aerobikmu bertambah."
        det.type == "long" -> "Long selesai. Bebannya perlu dipulihkan dengan baik."
        det.type == "thr" || det.type == "vo2" -> "Sesi keras terlaksana. Sekarang waktunya pulih."
        else -> "Sesi terkendali. Lanjut sesuai tubuh, bukan ego."
    }

    val sections = listOf(
        ReviewSection("Intensitas", sInt),
        ReviewSection("Eksekusi", sEx),
        ReviewSection("Beban & konteks", sLoad),
        ReviewSection("Lanjut", sNext),
    ).filter { it.notes.isNotEmpty() }

    return RunReview(tone = tone, headline = headline, summary = summary, sections = sections, flat = allNotes)
}

/** reviewHeadline() untuk review terstruktur: null bila tidak ada judul. */
fun reviewHeadline(rev: RunReview?): ReviewHeadline? {
    if (rev == null) return null
    val h = rev.headline ?: return null
    if (h.isEmpty()) return null
    return ReviewHeadline(rev.tone.ifEmpty { "g" }, h)
}

/** reviewHeadline() untuk larik catatan (bentuk lama). */
fun reviewHeadline(notes: List<ReviewNote>): ReviewHeadline {
    if (notes.any { it.k == "r" }) return ReviewHeadline("r", "Sesi ini berat. Prioritaskan pemulihan.")
    if (notes.count { it.k == "y" } >= 2) return ReviewHeadline("y", "Ada sinyal untuk waspada. Besok santai dulu.")
    return ReviewHeadline("g", "Sesi terkendali.")
}

// ---------- Uji estimator Max METs (backtest) ----------

/** Satu lari yang diuji: estimasi memakai HANYA lari sebelumnya, dibandingkan dengan METs tersirat dari lari itu. */
data class BacktestPoint(val t: Long, val est: Double, val run: Double, val conf: String, val km: Double, val st: Int)

/**
 * Hasil runBacktest. [stats] null bila belum cukup data (butuh minimal 10 lari teruji); selain itu 4 baris
 * (label, nilai) persis seperti di HTML. [recent] = 8 uji terakhir, terbaru dulu, sebagai (label, nilai).
 */
data class BacktestResult(
    val validRuns: Int,
    val points: List<BacktestPoint>,
    val flips: Int,
    val stats: List<Pair<String, String>>?,
    val recent: List<Pair<String, String>>,
) {
    val tested: Int get() = points.size
    val enough: Boolean get() = stats != null

    /** Teks subjudul di HTML. */
    val subtitle: String get() = "$tested lari bisa diuji dari $validRuns lari valid"
}

/**
 * runBacktest() di HTML tanpa bagian DOM: hanya perhitungannya. Teks penjelas dan pesan "belum cukup" ditulis
 * di UI. Pakai Snapshot hasil [withProcessed] supaya RHR otomatis sama dengan di HTML.
 */
fun Snapshot.runBacktest(): BacktestResult {
    val runs = acts.filter { it.kind == ActKind.RUN && it.km >= 2 && it.min >= 10 }.sortedBy { it.start }
    val res = ArrayList<BacktestPoint>()
    for (r in runs) {
        val e = estMax(acts.filter { it.start < r.start }, r.start)
        val m = e.m ?: continue
        res += BacktestPoint(
            t = r.start, est = m, run = metsOf(r.min / r.km) / pctAt(r.min), conf = e.conf, km = r.km,
            st = if (m < 7.9) 1 else if (m < 9.2) 2 else if (m < 12) 3 else 4,
        )
    }
    var flips = 0
    res.forEachIndexed { i, x -> if (i > 0 && x.st != res[i - 1].st) flips++ }
    val n = res.size

    var stats: List<Pair<String, String>>? = null
    var recent: List<Pair<String, String>> = emptyList()
    if (n >= 10) {
        val diff = res.map { it.est - it.run }
        val under = jsRound((100.0 * res.count { it.est < it.run * 0.95 }) / n)
        stats = listOf(
            "Selisih tengah (estimasi − lari)" to f1(medianOf(diff)) + " METs",
            "Rata-rata selisih mutlak" to f1(diff.fold(0.0) { a, d -> a + abs(d) } / n) + " METs",
            "Estimasi di bawah lari nyata" to "$under% lari",
            "Perpindahan tahap" to "$flips× dalam $n lari",
        )
        recent = res.takeLast(8).reversed().map { x ->
            (dateDayMonthId(x.t) + " · " + f1(x.km) + " km") to
                ("est " + f1(x.est) + " · lari " + f1(x.run) + " · T" + x.st)
        }
    }
    return BacktestResult(validRuns = runs.size, points = res, flips = flips, stats = stats, recent = recent)
}
