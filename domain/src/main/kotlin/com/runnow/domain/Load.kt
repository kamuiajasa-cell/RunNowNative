package com.runnow.domain

/**
 * Training load satu aktivitas. Memakai load dari Intervals bila ada; selain itu perkiraan skala TSS
 * (jam x 100 x IF^2) supaya sebanding dengan ambang engine.
 */
fun Snapshot.loadOf(a: Act): Int {
    if (!(a.min > 0)) return 0
    if (a.load > 0) return jsRound(a.load).toInt()
    val h = a.min / 60
    return when (a.kind) {
        ActKind.RUN -> {
            val mx = maxHr()
            val rs = restHr()
            val raw = if (a.avgHr > 0 && mx > rs && rs > 0) {
                ((a.avgHr - rs) / (mx - rs)) / 0.88
            } else {
                (metsOf(if (a.km > 0) a.min / a.km else 8.0) / 11.5) * 0.95
            }
            val iff = clamp(raw, 0.45, 1.15)
            jsRound(h * 100 * iff * iff).toInt()
        }
        ActKind.WALK -> jsRound(a.min * 0.35).toInt()
        ActKind.OTHER -> jsRound(a.min * 0.8).toInt()
    }
}

/** Total load aktivitas dalam [d] hari terakhir. */
fun Snapshot.loadDays(d: Int): Int {
    val cut = nowMs - d * DAY_MS
    return acts.filter { it.start >= cut }.sumOf { loadOf(it) }
}
