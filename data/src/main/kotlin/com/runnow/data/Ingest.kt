package com.runnow.data

import com.runnow.domain.Act
import com.runnow.domain.ActKind
import com.runnow.domain.GpsPick
import com.runnow.domain.Wellness
import com.runnow.domain.StreamSummary
import com.runnow.domain.gpsFromWatchPoints
import com.runnow.domain.hwSummary
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pengolahan data masuk: Intervals.icu dan jam. Port dari row/kind/mergeActs/dedupeActs/matchIcu/ingestWatch dan
 * bagian-bagian sync() di runsense.html. Semua fungsi murni (tidak menyentuh jaringan atau penyimpanan).
 */
object Ingest {
    const val MAX_ACTS = 500
    const val MAX_WELLNESS = 400
    const val MAX_SX = 60

    private val ISO = Regex("""^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?)?\s*(Z|[+-]\d{2}:?\d{2})?$""")

    /**
     * `+new Date(text)` untuk bentuk ISO yang dikirim Intervals.icu: tanggal saja = UTC, tanggal-jam tanpa zona =
     * waktu lokal [zone], dan dengan Z atau offset = mutlak. null bila tidak valid (JS: Invalid Date).
     */
    fun parseDate(text: String, zone: ZoneId): Long? {
        val m = ISO.matchEntire(text.trim()) ?: return null
        val g = m.groupValues
        return try {
            val d = LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt())
            if (g[4].isEmpty()) {
                d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            } else {
                val ms = if (g[7].isEmpty()) 0 else g[7].padEnd(3, '0').substring(0, 3).toInt()
                val ldt = LocalDateTime.of(d, java.time.LocalTime.of(g[4].toInt(), g[5].toInt(), if (g[6].isEmpty()) 0 else g[6].toInt(), ms * 1_000_000))
                val off = g[8]
                val z = when {
                    off.isEmpty() -> zone
                    off == "Z" -> ZoneOffset.UTC
                    else -> ZoneOffset.of(if (off.contains(':')) off else off.substring(0, 3) + ":" + off.substring(3))
                }
                ldt.atZone(z).toInstant().toEpochMilli()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun dateOf(v: JsonValue?, zone: ZoneId): Long? = when (v) {
        is JNum -> if (v.v.isFinite()) v.v.toLong() else null
        is JStr -> parseDate(v.v, zone)
        else -> null
    }

    /** kind() di HTML. */
    fun kindOf(t: String?): ActKind {
        val s = (t ?: "").lowercase()
        return if (Regex("run|trail|treadmill").containsMatchIn(s)) ActKind.RUN
        else if (Regex("walk|hike").containsMatchIn(s)) ActKind.WALK
        else ActKind.OTHER
    }

    private fun firstTruthy(vararg v: JsonValue?): Double {
        for (x in v) if (x.truthy()) return x.number()
        return 0.0
    }

    /** row() di HTML: satu objek aktivitas Intervals.icu menjadi baris lari; null bila tidak layak. */
    fun row(a: JsonValue?, zone: ZoneId): Act? {
        if (!a.truthy()) return null
        val tv = if (a["type"].truthy()) a["type"] else a["name"]
        val k = kindOf(tv.text())
        val sv = if (a["start_date_local"].truthy()) a["start_date_local"] else a["start_date"]
        if (!sv.truthy()) return null
        val start = dateOf(sv, zone) ?: return null
        val km = a["distance"].numberOr0() / 1e3
        val sec = firstTruthy(a["moving_time"], a["elapsed_time"])
        if (sec <= 0 || (km <= 0 && k == ActKind.RUN)) return null
        val idv = a["id"]
        val id = if (idv == null || idv is JNull) null else idv.text()
        return Act(
            start = start, kind = k, min = sec / 60, km = km,
            avgHr = a["average_heartrate"].numberOr0(), elev = a["total_elevation_gain"].numberOr0(),
            lid = if (!id.isNullOrEmpty()) "icu_$id" else "t$start",
            name = (if (a["name"].truthy()) a["name"].text() ?: "" else "").take(50),
            maxHr = firstTruthy(a["max_heartrate"], a["max_hr"]), icuId = id,
            load = firstTruthy(a["icu_training_load"], a["training_load"], a["hrss"]),
        )
    }

    private fun isHw(r: Act): Boolean = !r.icuId.isNullOrEmpty() && r.icuId!!.startsWith("hw")

    /** Lari yang sama dari Intervals (bukan jam), jenis sama, selisih mulai <= 12 menit. */
    fun matchIcu(acts: List<Act>, r: Act): Act? =
        acts.firstOrNull { x -> !x.icuId.isNullOrEmpty() && !isHw(x) && x.kind == r.kind && abs((x.start - r.start).toDouble()) <= 12 * 60e3 }

    private fun trimActs(list: List<Act>): List<Act> {
        val sorted = list.sortedBy { it.start } // stabil, seperti Array.sort
        return if (sorted.size > MAX_ACTS) sorted.subList(sorted.size - MAX_ACTS, sorted.size) else sorted
    }

    /** mergeActs() di HTML. Mengembalikan data baru dan jumlah baris yang baru atau berubah. */
    fun mergeActs(d: AppData, list: List<JsonValue>, zone: ZoneId): Pair<AppData, Int> {
        val acts = ArrayList(d.acts)
        val vx = LinkedHashMap(d.vx)
        val idx = HashMap<String, Int>()
        acts.forEachIndexed { i, r -> if (!r.icuId.isNullOrEmpty()) idx[r.icuId!!] = i }
        var n = 0
        for (raw in list) {
            val r = row(raw, zone) ?: continue
            val i = if (!r.icuId.isNullOrEmpty()) idx[r.icuId!!] else null
            if (i != null) {
                val o = acts[i]
                if (o != r) {
                    if (o.start != r.start && vx.containsKey(o.start)) {
                        vx[r.start] = vx[o.start]!!
                        vx.remove(o.start)
                    }
                    acts[i] = r
                    n++
                }
                continue
            }
            acts.add(r)
            if (!r.icuId.isNullOrEmpty()) idx[r.icuId!!] = acts.size - 1
            n++
        }
        return d.copy(acts = trimActs(acts), vx = vx) to n
    }

    /** dedupeActs() di HTML: lari jam yang kembar dengan Intervals dibuang; Intervals menang. */
    fun dedupeActs(d: AppData): Pair<AppData, Int> {
        val sx = LinkedHashMap(d.sx)
        val vx = LinkedHashMap(d.vx)
        var n = 0
        val kept = d.acts.filter { r ->
            if (!isHw(r)) return@filter true
            val m = matchIcu(d.acts, r) ?: return@filter true
            val rs = sx[r.icuId!!]
            if (rs != null && !sx.containsKey(m.icuId!!)) sx[m.icuId!!] = rs
            val rv = vx[r.start]
            if (rv != null && !vx.containsKey(m.start)) vx[m.start] = rv
            sx.remove(r.icuId!!)
            vx.remove(r.start)
            n++
            false
        }
        return d.copy(acts = kept, sx = sx, vx = vx) to n
    }

    private val DAY = Regex("""^\d{4}-\d{2}-\d{2}$""")

    private fun fillRest(d: AppData): AppData {
        if (d.profile.rest > 0) return d
        val r = d.wellness.lastOrNull { it.restingHR > 30 } ?: return d
        return d.copy(profile = d.profile.copy(rest = r.restingHR))
    }

    private fun trimWellness(m: Map<String, Wellness>): List<Wellness> {
        val l = m.values.sortedBy { it.id }
        return if (l.size > MAX_WELLNESS) l.subList(l.size - MAX_WELLNESS, l.size) else l
    }

    /** Bagian athlete/0 di sync(): nama, HR maks, RHR, dan umur bila belum terisi. */
    fun applyAthlete(d: AppData, p: JsonValue?, nowMs: Long, zone: ZoneId): AppData {
        val a = (if (p["athlete"].truthy()) p["athlete"] else p) ?: return d
        if (!a.truthy()) return d
        var out = d
        val fn = a["firstname"]
        val ln = a["lastname"]
        val joined = listOf(fn, ln).filter { it.truthy() }.joinToString(" ") { it.text() ?: "" }
        val name = (if (a["name"].truthy()) a["name"].text() ?: "" else joined).trim()
        if (name.isNotEmpty()) out = out.copy(icuName = name.take(40))
        var prof = out.profile
        if (!(prof.max > 0) && a["max_hr"].number() > 0) prof = prof.copy(max = a["max_hr"].number())
        if (!(prof.rest > 0) && a["resting_hr"].number() > 0) prof = prof.copy(rest = a["resting_hr"].number())
        val bd = a["birthday"]
        if (!(prof.age > 0) && bd.truthy()) {
            val by = dateOf(bd, zone)
            if (by != null) {
                val nowY = Instant.ofEpochMilli(nowMs).atZone(zone).year
                val byY = Instant.ofEpochMilli(by).atZone(zone).year
                prof = prof.copy(age = max(12, min(90, nowY - byY)))
            }
        }
        return out.copy(profile = prof)
    }

    /** Bagian wellness di sync(): data jam adalah kebenaran operasional, Intervals hanya mengisi yang kosong. */
    fun mergeIcuWellness(d: AppData, list: List<JsonValue>): AppData {
        val map = LinkedHashMap<String, Wellness>()
        d.wellness.forEach { map[it.id] = it }
        for (w in list) {
            val idv = if (w["id"].truthy()) w["id"] else w["date"]
            val id = (if (idv.truthy()) idv.text() ?: "" else "").take(10)
            if (!DAY.matches(id)) continue
            val prev = map[id] ?: Wellness(id = id)
            fun nn(v: JsonValue?): Double? = if (v == null || v is JNull) null else v.number()
            var next = prev.copy(id = id, ctl = nn(w["ctl"]), atl = nn(w["atl"]), tsb = nn(w["tsb"]))
            val rhr = firstTruthy(w["restingHR"], w["resting_hr"]).let { if (it == 0.0 || it.isNaN()) null else it }
            val hvv = firstTruthy(w["hrv"], w["hrvSDNN"]).let { if (it == 0.0 || it.isNaN()) null else it }
            if (prev.rhrSrc != "jam") next = next.copy(restingHR = rhr ?: prev.restingHR)
            if (prev.hrvSrc != "jam") next = next.copy(hrv = hvv ?: prev.hrv)
            map[id] = next
        }
        return fillRest(d.copy(wellness = trimWellness(map)))
    }

    class IngestResult(val data: AppData, val wellness: Int, val workouts: Int, val gps: Map<Long, GpsPick>)

    private fun pos(v: Double?): Double = if (v != null && v.isFinite() && v > 0) v else 0.0

    /**
     * ingestWatch() di HTML. [hasGps] memberi tahu apakah sebuah lari sudah punya GPS ringkas (CL.gps[ms]);
     * yang baru ada di [IngestResult.gps]. Bidang kosong dari jam tidak menimpa nilai yang ada.
     */
    fun ingestWatch(d0: AppData, w: WatchImportData, nowMs: Long, zone: ZoneId, hasGps: (Long) -> Boolean): IngestResult {
        var d = d0
        var nw = 0
        var nk = 0
        val gpsNew = LinkedHashMap<Long, GpsPick>()

        val map = LinkedHashMap<String, Wellness>()
        d.wellness.forEach { map[it.id] = it }
        for (x in w.wellness) {
            val id = x.id.take(10)
            if (!DAY.matches(id)) continue
            var o = map[id] ?: Wellness(id = id)
            if (pos(x.restingHR) > 30) o = o.copy(restingHR = x.restingHR!!, rhrSrc = "jam")
            if (pos(x.hrv) > 0) o = o.copy(hrv = x.hrv!!, hrvSrc = "jam", hrvSpot = x.hrvSpot)
            if (pos(x.sleepSecs) > 0) o = o.copy(sleepSecs = x.sleepSecs!!)
            if (pos(x.sleepScore) > 0) o = o.copy(sleepScore = x.sleepScore!!)
            if (pos(x.steps) > 0) o = o.copy(steps = x.steps!!)
            if (pos(x.spO2) > 0) o = o.copy(spO2 = x.spO2!!)
            if (pos(x.stress) > 0) o = o.copy(stress = x.stress!!)
            if (pos(x.stressMax) > 0) o = o.copy(stressMax = x.stressMax!!)
            if (pos(x.stressNight) > 0) o = o.copy(stressNight = x.stressNight!!)
            map[id] = o
            nw++
        }
        d = fillRest(d.copy(wellness = trimWellness(map)))

        val acts = ArrayList(d.acts)
        val sx = LinkedHashMap(d.sx)
        val idx = HashMap<String, Int>()
        acts.forEachIndexed { i, r -> if (!r.icuId.isNullOrEmpty()) idx[r.icuId!!] = i }
        for (x in w.workouts) {
            if (!(x.durSec > 0) || x.iid.isNullOrEmpty() || !(x.start > 0)) continue
            val k = if (x.kind == "run") ActKind.RUN else if (x.kind == "walk") ActKind.WALK else ActKind.OTHER
            val km = (if (x.distM.isNaN()) 0.0 else x.distM) / 1000
            if (k == ActKind.RUN && km <= 0) continue
            val id = x.iid
            val r = Act(
                start = x.start.toLong(), kind = k, min = x.durSec / 60, km = km,
                avgHr = if (x.avgHr.isNaN()) 0.0 else x.avgHr, elev = if (x.elevUp.isNaN()) 0.0 else x.elevUp,
                lid = id, name = x.name.take(50), maxHr = if (x.maxHr.isNaN()) 0.0 else x.maxHr, icuId = id, load = 0.0,
            )
            val m = matchIcu(acts, r)
            val key = m?.icuId ?: id
            val pts = x.pts
            if (k == ActKind.RUN && pts != null) {
                val ms = m?.start ?: r.start
                if (!hasGps(ms) && !gpsNew.containsKey(ms)) gpsNew[ms] = gpsFromWatchPoints(pts, x.durSec)
            }
            if (k == ActKind.RUN && r.min >= 20 && r.km >= 3 && !sx.containsKey(key) && pts != null && pts.size >= 40) {
                sx[key] = d.copy(sx = sx).snapshot(nowMs, zone).hwSummary(pts) ?: StreamSummary.NONE
            }
            if (m != null) continue
            val i = idx[id]
            if (i != null) {
                if (acts[i] != r) {
                    acts[i] = r
                    nk++
                }
            } else {
                acts.add(r)
                idx[id] = acts.size - 1
                nk++
            }
        }
        while (sx.size > MAX_SX) sx.remove(sx.keys.first())
        d = d.copy(acts = trimActs(acts), sx = sx, hwLast = nowMs)
        return IngestResult(d, nw, nk, gpsNew)
    }
}
