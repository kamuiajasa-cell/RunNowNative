package com.runnow.domain

/** Tabel pace (menit/km) -> METs lari. */
private val P = arrayOf(
    4.0 to 14.5, 4.5 to 13.0, 5.0 to 12.0, 5.5 to 11.0, 6.0 to 9.8, 6.5 to 9.0,
    7.0 to 8.3, 7.5 to 7.7, 8.0 to 7.2, 8.5 to 6.7, 9.0 to 6.3, 9.5 to 5.9,
    10.0 to 5.5, 10.5 to 5.1, 11.0 to 4.8, 12.0 to 4.3, 13.0 to 3.9, 14.0 to 3.6,
)

fun metsAt(p: Double): Double {
    if (p <= P.first().first) return P.first().second
    if (p >= P.last().first) return P.last().second
    for (i in 1 until P.size) {
        if (p <= P[i].first) {
            val t = (p - P[i - 1].first) / (P[i].first - P[i - 1].first)
            return P[i - 1].second + t * (P[i].second - P[i - 1].second)
        }
    }
    return 4.0
}

fun metsOf(p: Double): Double = if (p > 0) metsAt(p) else 1.0

/** Kebalikan metsAt lewat bisection (METs turun saat pace melambat). */
fun paceOf(m: Double): Double {
    var lo = 3.5
    var hi = 16.0
    repeat(36) {
        val mid = (lo + hi) / 2
        if (metsAt(mid) > m) lo = mid else hi = mid
    }
    return (lo + hi) / 2
}

private val T = arrayOf(4.0 to 1.0, 6.0 to 0.975, 25.0 to 0.825, 67.0 to 0.625, 90.0 to 0.6)

/** Fraksi Max METs yang bisa dipertahankan selama [min] menit. */
fun pctAt(min: Double): Double {
    if (min <= 4) return 1.0
    if (min >= 90) return 0.6
    for (i in 1 until T.size) {
        if (min <= T[i].first) {
            return T[i - 1].second +
                (T[i].second - T[i - 1].second) * (min - T[i - 1].first) / (T[i].first - T[i - 1].first)
        }
    }
    return 0.6
}
