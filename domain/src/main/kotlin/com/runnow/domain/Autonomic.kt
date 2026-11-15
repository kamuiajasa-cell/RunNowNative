package com.runnow.domain

import java.time.LocalDate

private val ID_RE = Regex("^(\\d{4})-(\\d{2})-(\\d{2})")

/**
 * HRV semalam / RHR / tidur / stres vs baseline 28 hari -> kunci quality (data harus <= 2 hari lalu).
 * HRV hanya nilai malam (bukan ukur spot) dan dibanding baseline dari sumber yang sama.
 * Ambang tidur (< 5j30) dan stres (+15 dari median) adalah heuristik: hanya memperingatkan,
 * dan baru mengunci quality kalau bersamaan dengan HRV turun / RHR naik.
 */
fun Snapshot.autonomic(): Autonomic {
    val w = wellness
    val parts = ArrayList<String>()
    var hrvPct: Int? = null
    var rhrD: Int? = null
    var hrvOut: Int? = null
    var rhrOut: Int? = null
    var sleepH: Double? = null
    var stressOut: Double? = null
    var stressD: Int? = null

    fun fresh(x: Wellness): Boolean {
        val m = ID_RE.find(x.id) ?: return false
        val d = runCatching {
            LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }.getOrNull() ?: return false
        return today - d.toEpochDay() * DAY_MS <= 2 * DAY_MS
    }

    // HRV
    val hv = w.filter { it.hrv > 0 && !it.hrvSpot }
    val hl = hv.lastOrNull()
    val hr3 = hv.takeLast(3)
    val hm = if (hr3.isNotEmpty()) hr3.sumOf { it.hrv } / hr3.size else 0.0
    if (hl != null && fresh(hl)) {
        hrvOut = jsRound(hl.hrv).toInt()
        val base = hv.dropLast(hr3.size).filter { it.hrvSrc == hl.hrvSrc }.takeLast(28)
        if (base.size >= 7) {
            val m = medianOf(base.map { it.hrv })
            if (m > 0) {
                hrvPct = jsRound((hm / m - 1) * 100).toInt()
                if (hrvPct <= -8) parts += "HRV $hrvPct%"
            }
        }
    }

    // RHR
    val rh = w.filter { it.restingHR > 30 }
    val rl = rh.lastOrNull()
    val rr3 = rh.takeLast(3)
    val rm = if (rr3.isNotEmpty()) rr3.sumOf { it.restingHR } / rr3.size else 0.0
    if (rl != null && fresh(rl)) {
        rhrOut = jsRound(rl.restingHR).toInt()
        val base = rh.dropLast(rr3.size).filter { it.rhrSrc == rl.rhrSrc }.takeLast(28)
        if (base.size >= 7) {
            rhrD = jsRound(rm - medianOf(base.map { it.restingHR })).toInt()
            if (rhrD >= 3) parts += "RHR +$rhrD bpm"
        }
    }

    // Tidur
    val sl = w.lastOrNull { it.sleepSecs > 0 }
    if (sl != null && fresh(sl)) {
        sleepH = sl.sleepSecs / 3600
        if (sleepH < 5.5) parts += "Tidur " + fmtHm(sleepH)
    }

    // Stres
    val sr = w.lastOrNull { it.stress > 0 }
    if (sr != null && fresh(sr)) {
        stressOut = sr.stress
        val base = w.filter { it.stress > 0 && it.id != sr.id }.takeLast(28).map { it.stress }
        if (base.size >= 7) {
            stressD = jsRound(sr.stress - medianOf(base)).toInt()
            if (stressD >= 15) parts += "Stres +$stressD"
        }
    }

    val hLow = hrvPct != null && hrvPct <= -15
    val rHigh = rhrD != null && rhrD >= 5
    val sShort = sleepH != null && sleepH < 5.5
    val lock = hLow || rHigh ||
        (sShort && ((hrvPct != null && hrvPct <= -8) || (rhrD != null && rhrD >= 3)))
    val severe = (hrvPct != null && hrvPct <= -25) || (hLow && rHigh)

    return Autonomic(
        lock = lock, severe = severe, warn = parts.isNotEmpty(), why = parts.joinToString(", "),
        hrvPct = hrvPct, rhrD = rhrD, hrv = hrvOut, rhr = rhrOut,
        sleepH = sleepH, stress = stressOut, stressD = stressD,
    )
}

/** Skor kesiapan 22..98, atau null bila tidak ada satu pun masukan. */
fun Snapshot.readiness(): Int? {
    var sc = 70.0
    var inputs = 0
    val au = autonomic()
    val hrvPct = au.hrvPct
    if (hrvPct != null) {
        sc += clamp(hrvPct * 0.5, -12.0, 5.0)
        inputs++
    }
    val sleepH = au.sleepH
    if (sleepH != null) {
        inputs++
        if (sleepH < 5.5) {
            sc -= 8
        } else if (sleepH < 6.5) {
            sc -= 3
        }
    }
    val stressD = au.stressD
    if (stressD != null && stressD >= 15) sc -= 4

    val rh = wellness.filter { it.restingHR > 30 }.map { it.restingHR }
    if (rh.size >= 8) {
        // JS: rh.slice(-31, -3)
        val b = rh.subList(maxOf(0, rh.size - 31), rh.size - 3).sorted()
        val last3 = rh.takeLast(3).sum() / minOf(3, rh.size)
        sc += clamp((b[b.size / 2] - last3) * 1.3, -14.0, 12.0)
        inputs++
    }

    val acute = loadDays(7)
    val chronic = (loadDays(28) - acute) / 3.0
    if (chronic > 25) {
        inputs++
        val r = acute / chronic
        when {
            r > 1.5 -> sc -= 20
            r > 1.3 -> sc -= 12
            r > 1.15 -> sc -= 5
            r >= 0.8 && r <= 1.1 -> sc += 5
            r < 0.6 -> sc -= 6
        }
    }

    val y = today - DAY_MS
    if (acts.any { d0(it.start) == y && it.kind == ActKind.RUN && loadOf(it) > 90 }) sc -= 10

    var streak = 0
    for (i in 0 until 5) {
        val t = today - i * DAY_MS
        val l = acts.filter { d0(it.start) == t }.sumOf { loadOf(it) }
        if (l > 70) streak++ else break
    }
    if (streak >= 3) sc -= 12 else if (streak >= 2) sc -= 5

    return if (inputs > 0) clamp(jsRound(sc).toDouble(), 22.0, 98.0).toInt() else null
}
