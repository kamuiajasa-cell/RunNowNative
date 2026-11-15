package com.runnow.domain

import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal const val DAY_MS = 86_400_000L

/** Setara Math.round di JS: .5 dibulatkan ke atas, juga untuk bilangan negatif (kotlin.math.round membulatkan ke genap). */
internal fun jsRound(x: Double): Long = floor(x + 0.5).toLong()

internal fun clamp(v: Double, a: Double, b: Double): Double = max(a, min(b, v))

/** Nilai tengah versi HTML: elemen urutan ke floor(n/2) dari data terurut. */
internal fun medianOf(xs: List<Double>): Double = xs.sorted()[xs.size / 2]

// ---------- Format teks (id-ID) ----------

fun f1(n: Double?): String {
    if (n == null || !n.isFinite()) return "—"
    val r = jsRound(n * 10) / 10.0
    return String.format(Locale.US, "%.1f", r).removeSuffix(".0")
}

/** Pace menit/km -> "m:ss". */
fun mp(p: Double): String {
    if (!(p > 0)) return "—"
    val tot = jsRound(p * 60)
    return "${tot / 60}:${(tot % 60).toString().padStart(2, '0')}"
}

/** Pace detik/km -> "m:ss". */
fun formatPace(sec: Double): String {
    val tot = jsRound(sec)
    return "${tot / 60}:${(tot % 60).toString().padStart(2, '0')}"
}

/** Detik -> "mm:ss". */
fun formatTime(sec: Double): String {
    val tot = jsRound(sec)
    return "${(tot / 60).toString().padStart(2, '0')}:${(tot % 60).toString().padStart(2, '0')}"
}

/** Menit -> "45 mnt" / "1 j 5 m". */
fun fmtM(m: Double): String {
    if (!(m > 0)) return "—"
    if (m < 60) return "${jsRound(m)} mnt"
    val rest = jsRound(m % 60)
    return "${floor(m / 60).toLong()} j" + if (rest > 0) " $rest m" else ""
}

/** Jam desimal -> "6j15". */
fun fmtHm(h: Double): String {
    val m = jsRound(h * 60)
    return "${m / 60}j${(m % 60).toString().padStart(2, '0')}"
}

fun durText(p: Double): String = when {
    p >= 0.95 -> "4–8 mnt"
    p >= 0.85 -> "8–20 mnt"
    p >= 0.8 -> "20–30 mnt"
    p >= 0.65 -> "30–45 mnt"
    p >= 0.6 -> "45–90+ mnt"
    else -> ">90 mnt"
}

fun band(m: Double?): String? = when {
    m == null -> null
    m < 7.9 -> "Rendah"
    m < 10.9 -> "Menengah"
    else -> "Tinggi"
}

/** parseInt JS: angka di awal teks (spasi dan tanda boleh), null bila tidak ada (NaN). */
internal fun parseIntJs(s: String): Int? =
    Regex("^\\s*[+-]?\\d+").find(s)?.value?.trim()?.toIntOrNull()

/** Number(...) JS untuk teks: kosong = 0, tidak valid = NaN. */
internal fun jsNumber(s: String): Double {
    val t = s.trim()
    return if (t.isEmpty()) 0.0 else t.toDoubleOrNull() ?: Double.NaN
}

/** Angka seperti dicetak JS: 18.0 jadi "18". */
internal fun numStr(d: Double): String =
    if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()

/** (x).toFixed(0) JS: setengah dibulatkan menjauhi nol, dan tanda minus tetap ada untuk x < 0. */
internal fun toFixed0(x: Double): String {
    val r = java.math.BigDecimal(Math.abs(x)).setScale(0, java.math.RoundingMode.HALF_UP).toPlainString()
    return if (x < 0) "-$r" else r
}

internal val VX_LB = mapOf("easy" to "Easy", "long" to "Long", "thr" to "Threshold", "vo2" to "VO2max", "race" to "Lomba")
