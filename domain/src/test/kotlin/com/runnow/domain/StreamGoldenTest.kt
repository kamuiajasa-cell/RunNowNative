package com.runnow.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.fail

/**
 * Membandingkan summarizeStream() dan hwSummary() Kotlin dengan kode asli runsense.html.
 * Data: src/test/resources/golden_stream.txt (dibuat otomatis oleh gen/gen_stream.js).
 */
class StreamGoldenTest {
    private fun fmt(r: StreamSummary?): String {
        if (r == null) return "null"
        if (r.none) return "none"
        return listOfNotNull(
            r.fast?.let { "f=" + it.p12 + "," + it.med + "," + it.pct },
            r.fade?.let { "a=" + it.pct },
            r.dec?.let { "d=" + it.pct },
            r.tiz?.let { "z=" + it.joinToString(",") },
        ).joinToString(";")
    }

    /** Sama persis kecuali bilangan desimal, yang dibandingkan dengan toleransi (p12 dan median pace). */
    private fun looseEq(a: String, b: String): Boolean {
        if (a == b) return true
        val x = a.split(";", "=", ",")
        val y = b.split(";", "=", ",")
        if (x.size != y.size) return false
        for (i in x.indices) {
            if (x[i] == y[i]) continue
            val p = x[i].toDoubleOrNull() ?: return false
            val q = y[i].toDoubleOrNull() ?: return false
            if (abs(p - q) > 1e-9 * max(1.0, max(abs(p), abs(q)))) return false
        }
        return true
    }

    private fun snap(rest: String, max: String, age: String) =
        Snapshot(Profile(rest.toDouble(), max.toDouble(), age.toInt(), 0), emptyList(), emptyList())

    private fun doubles(s: String) = s.split(",").map { it.toDouble() }

    @Test
    fun streamSummariesMatchHtml() {
        val text = javaClass.getResource("/golden_stream.txt")!!.readText()
        val problems = ArrayList<String>()
        var total = 0

        var id = ""
        var sn: Snapshot? = null
        var minN: Int? = null
        var time: List<Double> = emptyList()
        var dist: List<Double> = emptyList()
        var hr: List<Double>? = null
        var pts: List<DoubleArray>? = null

        for (line in text.lines()) {
            if (line.isEmpty() || line.startsWith("#")) continue
            val body = line.drop(2)
            when (line[0]) {
                'G' -> {
                    val t = body.split(" ")
                    id = t[0]; sn = snap(t[1], t[2], t[3]); minN = if (t[4] == "-") null else t[4].toInt(); pts = null
                }
                'T' -> time = doubles(body)
                'Q' -> dist = doubles(body)
                'H' -> hr = if (body == "-") null else doubles(body)
                'U' -> {
                    val t = body.split(" ")
                    id = t[0]; sn = snap(t[1], t[2], t[3]); pts = emptyList()
                }
                'P' -> pts = body.split(",").map { p -> p.split(":").map { it.toDouble() }.toDoubleArray() }
                'Y' -> {
                    total++
                    val s = sn!!
                    val got = try {
                        val p = pts
                        val r = if (p != null) s.hwSummary(p)
                        else if (minN == null) s.summarizeStream(StreamData(time, dist, hr))
                        else s.summarizeStream(StreamData(time, dist, hr), minN)
                        fmt(r)
                    } catch (e: Throwable) {
                        "EXCEPTION: $e"
                    }
                    if (!looseEq(body, got)) problems += "[$id]\n    diharapkan: $body\n    didapat:    $got"
                }
            }
        }
        if (problems.isNotEmpty()) fail("${problems.size} selisih dari $total kasus:\n" + problems.take(12).joinToString("\n"))
        check(total >= 50) { "Data uji terlalu sedikit: $total" }
    }
}
