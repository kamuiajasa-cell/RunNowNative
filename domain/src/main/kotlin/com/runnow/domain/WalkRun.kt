package com.runnow.domain

import kotlin.math.max

/** Sesi run-walk tahap awal. k = 0 (A), 1 (B), selain itu (C). [sc] = skala volume 0,45..1. */
fun walkRunWorkout(k: Int, sc: Double = 1.0): WalkRunWorkout {
    val s = clamp(if (sc == 0.0 || sc.isNaN()) 1.0 else sc, 0.45, 1.0)
    if (k == 0) {
        val r = max(4L, jsRound(10 * s)).toInt()
        return WalkRunWorkout(
            "A", 10 + r * 2,
            listOf(
                "Pemanasan" to "5 mnt jalan",
                "Inti" to "$r× (1 mnt lari + 1 mnt jalan)",
                "Pendinginan" to "5 mnt jalan",
            ),
        )
    }
    if (k == 1) {
        val r = max(3L, jsRound(7 * s)).toInt()
        return WalkRunWorkout(
            "B", 9 + r * 3,
            listOf(
                "Pemanasan" to "5 mnt jalan",
                "Inti" to "$r× (2 mnt lari + 1 mnt jalan)",
                "Pendinginan" to "4 mnt jalan",
            ),
        )
    }
    val r1 = max(3L, jsRound(6 * s)).toInt()
    val r2 = max(4L, jsRound(8 * s)).toInt()
    return WalkRunWorkout(
        "C", jsRound(9 + r1 * 1.5 + r2 * 1.5).toInt(),
        listOf(
            "Pemanasan" to "5 mnt jalan",
            "Inti 1" to "$r1× (1 mnt lari + 30 dtk jalan)",
            "Inti 2" to "$r2× (30 dtk lari + 1 mnt jalan)",
            "Pendinginan" to "4 mnt jalan",
        ),
    )
}
