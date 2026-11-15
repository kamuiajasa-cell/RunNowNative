package com.runnow.domain

/**
 * Tebakan jenis sesi tanpa stream: easy / long / thr / vo2.
 * [stg] dihitung sekali oleh pemanggil (stage() mahal dan sama untuk semua aktivitas).
 */
fun Snapshot.detectType(a: Act, stg: Stage = stage()): SessionGuess {
    if (a.kind != ActKind.RUN) return SessionGuess("easy", "rendah", "bukan lari")
    val km = a.km
    val min = a.min
    val pace = if (km > 0) min / km else 0.0

    manualTypes[a.start]?.takeIf { it.isNotEmpty() }?.let {
        return SessionGuess(it, "tinggi", "dikonfirmasi manual", confirmed = true)
    }

    // Long: jauh lebih panjang dari median lari 28 hari sebelumnya
    val recent = acts
        .filter { it.kind == ActKind.RUN && it.km >= 1 && it.start < a.start && it.start >= a.start - 28 * DAY_MS }
        .map { it.km }
    val med = if (recent.size >= 3) medianOf(recent) else 0.0
    if (km >= 6 && med > 0 && km >= 1.3 * med) {
        return SessionGuess(
            "long", "sedang",
            f1(km) + " km, " + jsRound((km / med - 1) * 100) + "% lebih jauh dari biasamu",
        )
    }

    // Keras menurut pace relatif kapasitas / load
    val load = loadOf(a)
    val sm = stg.m
    if (sm != null && pace > 0) {
        val pct = metsOf(pace) / sm
        if (pct >= 0.88 && min >= 8 && min <= 40) {
            return SessionGuess(
                if (min <= 20) "vo2" else "thr", "sedang",
                "intensitas ≈ " + jsRound(pct * 100) + "% Max METs",
            )
        }
        if (pct >= 0.78 && min >= 15) {
            return SessionGuess("thr", "rendah", "intensitas di atas easy (≈ " + jsRound(pct * 100) + "% Max)")
        }
    }
    if (load > 85 && min >= 20) {
        return SessionGuess("thr", "rendah", "training load tinggi ($load)")
    }
    return SessionGuess("easy", "sedang", "pace relatif merata / tidak ada pola keras")
}
