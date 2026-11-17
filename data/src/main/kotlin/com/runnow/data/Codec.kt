package com.runnow.data

import com.runnow.domain.Act
import com.runnow.domain.ActKind
import com.runnow.domain.ClimCtx
import com.runnow.domain.GeoLoc
import com.runnow.domain.GpsPick
import com.runnow.domain.GpsPoint
import com.runnow.domain.Profile
import com.runnow.domain.StreamData
import com.runnow.domain.StreamDec
import com.runnow.domain.StreamFade
import com.runnow.domain.StreamFast
import com.runnow.domain.StreamSummary
import com.runnow.domain.Wellness
import com.runnow.domain.WxData
import com.runnow.domain.WxHourly
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/** Nilai.toFixed(digits) ala JavaScript (separuh dibulatkan menjauhi nol, "-0.0" untuk negatif kecil). */
fun toFixed(x: Double, digits: Int): String {
    val s = BigDecimal(abs(x)).setScale(digits, RoundingMode.HALF_UP).toPlainString()
    return if (x < 0) "-$s" else s
}

/**
 * Kodek JSON untuk penyimpanan dan jaringan. Format penyimpanan sama dengan localStorage di HTML
 * (kunci "runsense_auto_v2" dan "runsense_clim"), jadi data lama bisa dibaca dan data baru bisa dibandingkan dengan
 * hasil `persist()` HTML di tes.
 */
object Codec {
    private fun n(x: Double) = JNum(x)
    private fun s(x: String) = JStr(x)
    private fun obj(vararg p: Pair<String, JsonValue>) = JObj(linkedMapOf(*p))

    /** Number(x) tetapi NaN dan null menjadi 0 (data lama yang rusak tidak boleh menjatuhkan aplikasi). */
    private fun d0(v: JsonValue?): Double = v.number().let { if (it.isNaN()) 0.0 else it }
    private fun dOrNull(v: JsonValue?): Double? = if (v == null || v is JNull) null else v.number().takeIf { !it.isNaN() }
    private fun i0(v: JsonValue?): Int = d0(v).toInt()

    // ---------- AppData ----------

    private fun kindText(k: ActKind) = when (k) { ActKind.RUN -> "run"; ActKind.WALK -> "walk"; ActKind.OTHER -> "other" }

    private fun encodeAct(a: Act): JsonValue = JArr(
        listOf(
            n(a.start.toDouble()), s(kindText(a.kind)), n(a.min), n(a.km), n(a.avgHr), n(a.elev), s(a.lid), s(a.name), n(a.maxHr),
            if (a.icuId == null) JNull else s(a.icuId!!), n(a.load),
        ),
    )

    private fun decodeAct(v: JsonValue): Act? {
        val it = v.items ?: return null
        if (it.size < 10) return null
        val start = d0(it[0]).toLong()
        val kind = when (it[1].text()) { "run" -> ActKind.RUN; "walk" -> ActKind.WALK; else -> ActKind.OTHER }
        return Act(
            start = start, kind = kind, min = d0(it[2]), km = d0(it[3]), avgHr = d0(it[4]), elev = d0(it[5]),
            lid = it[6].text() ?: "t$start", name = it[7].text() ?: "", maxHr = d0(it[8]),
            icuId = if (it[9] is JNull) null else it[9].text(), load = if (it.size > 10) d0(it[10]) else 0.0,
        )
    }

    private fun encodeWellness(w: Wellness): JsonValue {
        val f = LinkedHashMap<String, JsonValue>()
        f["id"] = s(w.id)
        if (w.restingHR != 0.0) f["restingHR"] = n(w.restingHR)
        if (w.rhrSrc.isNotEmpty()) f["rhrSrc"] = s(w.rhrSrc)
        if (w.hrv != 0.0) f["hrv"] = n(w.hrv)
        if (w.hrvSrc.isNotEmpty()) f["hrvSrc"] = s(w.hrvSrc)
        if (w.hrvSpot) f["hrvSpot"] = JBool(true)
        if (w.sleepSecs != 0.0) f["sleepSecs"] = n(w.sleepSecs)
        if (w.sleepScore != 0.0) f["sleepScore"] = n(w.sleepScore)
        if (w.steps != 0.0) f["steps"] = n(w.steps)
        if (w.spO2 != 0.0) f["spO2"] = n(w.spO2)
        if (w.stress != 0.0) f["stress"] = n(w.stress)
        if (w.stressMax != 0.0) f["stressMax"] = n(w.stressMax)
        if (w.stressNight != 0.0) f["stressNight"] = n(w.stressNight)
        w.ctl?.let { f["ctl"] = n(it) }
        w.atl?.let { f["atl"] = n(it) }
        w.tsb?.let { f["tsb"] = n(it) }
        return JObj(f)
    }

    private fun decodeWellness(v: JsonValue): Wellness? {
        val id = v["id"].text() ?: return null
        return Wellness(
            id = id, restingHR = d0(v["restingHR"]), rhrSrc = v["rhrSrc"].text() ?: "", hrv = d0(v["hrv"]), hrvSrc = v["hrvSrc"].text() ?: "",
            hrvSpot = v["hrvSpot"].truthy(), sleepSecs = d0(v["sleepSecs"]), sleepScore = d0(v["sleepScore"]), steps = d0(v["steps"]),
            spO2 = d0(v["spO2"]), stress = d0(v["stress"]), stressMax = d0(v["stressMax"]), stressNight = d0(v["stressNight"]),
            ctl = dOrNull(v["ctl"]), atl = dOrNull(v["atl"]), tsb = dOrNull(v["tsb"]),
        )
    }

    fun encodeStream(x: StreamSummary): JsonValue {
        if (x.none) return obj("none" to n(1.0))
        val f = LinkedHashMap<String, JsonValue>()
        x.fast?.let { f["fast"] = obj("p12" to n(it.p12), "med" to n(it.med), "pct" to n(it.pct.toDouble())) }
        x.fade?.let { f["fade"] = obj("pct" to n(it.pct.toDouble())) }
        x.dec?.let { f["dec"] = obj("pct" to n(it.pct.toDouble())) }
        x.tiz?.let { f["tiz"] = JArr(it.map { v -> n(v.toDouble()) }) }
        return JObj(f)
    }

    fun decodeStream(v: JsonValue?): StreamSummary? {
        if (v == null || v !is JObj) return null
        if (v["none"].truthy()) return StreamSummary.NONE
        val fast = v["fast"]
        val fade = v["fade"]
        val dec = v["dec"]
        val tiz = v["tiz"].items
        val out = StreamSummary(
            fast = if (fast != null && fast is JObj) StreamFast(d0(fast["p12"]), d0(fast["med"]), i0(fast["pct"])) else null,
            fade = if (fade != null && fade is JObj) StreamFade(i0(fade["pct"])) else null,
            dec = if (dec != null && dec is JObj) StreamDec(i0(dec["pct"])) else null,
            tiz = tiz?.map { i0(it) },
        )
        return if (out.fast == null && out.fade == null && out.dec == null && out.tiz == null) StreamSummary.NONE else out
    }

    fun encodeApp(d: AppData): String {
        val vx = LinkedHashMap<String, JsonValue>()
        d.vx.forEach { (k, v) -> vx[k.toString()] = s(v) }
        val sx = LinkedHashMap<String, JsonValue>()
        d.sx.forEach { (k, v) -> sx[k] = encodeStream(v) }
        val p = d.profile
        return Json.stringify(
            obj(
                "acts" to JArr(d.acts.map { encodeAct(it) }),
                "wellness" to JArr(d.wellness.map { encodeWellness(it) }),
                "vx" to JObj(vx),
                "icuLast" to n(d.icuLast.toDouble()),
                "icuName" to s(d.icuName),
                "afS" to n(p.afS.toDouble()),
                "rest" to n(p.rest),
                "restManual" to n(if (p.restManual) 1.0 else 0.0),
                "lthrC" to n(p.lthrC.toDouble()),
                "ltpC" to n(p.ltpC.toDouble()),
                "max" to n(p.max),
                "age" to n(p.age.toDouble()),
                "hwLast" to n(d.hwLast.toDouble()),
                "sx" to JObj(sx),
            ),
        )
    }

    /** restore() di HTML. null bila teks bukan JSON yang valid (data dianggap kosong). */
    fun decodeApp(text: String?): AppData? {
        if (text.isNullOrEmpty()) return null
        val j = try { Json.parse(text) } catch (e: JsonException) { return null }
        if (j !is JObj) return null
        val vx = LinkedHashMap<Long, String>()
        (j["vx"] as? JObj)?.fields?.forEach { (k, v) -> k.toLongOrNull()?.let { l -> v.text()?.let { t -> vx[l] = t } } }
        val sx = LinkedHashMap<String, StreamSummary>()
        (j["sx"] as? JObj)?.fields?.forEach { (k, v) -> decodeStream(v)?.let { sx[k] = it } }
        return AppData(
            profile = Profile(
                rest = d0(j["rest"]), max = d0(j["max"]), age = i0(j["age"]), afS = i0(j["afS"]),
                restManual = j["restManual"].truthy(), lthrC = i0(j["lthrC"]), ltpC = i0(j["ltpC"]),
            ),
            acts = j["acts"].items?.mapNotNull { decodeAct(it) } ?: emptyList(),
            wellness = j["wellness"].items?.mapNotNull { decodeWellness(it) } ?: emptyList(),
            vx = vx, sx = sx,
            icuLast = d0(j["icuLast"]).toLong(), icuName = j["icuName"].text() ?: "", hwLast = d0(j["hwLast"]).toLong(),
        )
    }

    // ---------- Cuaca ----------

    private fun doubles(v: JsonValue?): List<Double?>? = v.items?.map { if (it is JNum) it.v else null }

    /** `j.hourly` dari Open-Meteo. null bila tidak ada `hourly.time` (di HTML: lempar, lalu pakai cache lama). */
    fun parseWx(j: JsonValue?, nowMs: Long): WxData? {
        val h = j["hourly"] as? JObj ?: return null
        val time = h["time"].items?.map { it.text() ?: "" } ?: return null
        val series = LinkedHashMap<String, List<Double?>>()
        for ((k, v) in h.fields) if (k != "time") doubles(v)?.let { series[k] = it }
        return WxData(nowMs, WxHourly(time, series))
    }

    fun encodeWx(o: WxData): JsonValue {
        val h = LinkedHashMap<String, JsonValue>()
        h["time"] = JArr(o.h.time.map { s(it) })
        o.h.series.forEach { (k, v) -> h[k] = JArr(v.map { x -> if (x == null) JNull else n(x) }) }
        return obj("t" to n(o.t.toDouble()), "h" to JObj(h), "src" to s(o.src))
    }

    private fun encodeCtx(c: ClimCtx): JsonValue {
        if (c.na) return obj("na" to n(1.0))
        fun nv(x: Double?): JsonValue = if (x == null) JNull else n(x)
        return obj(
            "t" to nv(c.t), "rh" to nv(c.rh), "at" to nv(c.at), "w" to nv(c.w), "sr" to n(c.sr), "p" to n(c.p), "code" to n(c.code),
            "n" to n(c.n.toDouble()), "amin" to n(c.amin), "amax" to n(c.amax), "cls" to s(c.cls), "src" to s(c.src), "at0" to n(c.at0.toDouble()),
            "gps" to n(if (c.gps) 1.0 else 0.0), "ng" to n(c.ng.toDouble()), "q" to s(c.q),
        )
    }

    private fun decodeCtx(v: JsonValue): ClimCtx? {
        if (v !is JObj) return null
        if (v["na"].truthy()) return ClimCtx(na = true)
        return ClimCtx(
            t = dOrNull(v["t"]), rh = dOrNull(v["rh"]), at = dOrNull(v["at"]), w = dOrNull(v["w"]), sr = d0(v["sr"]), p = d0(v["p"]), code = d0(v["code"]),
            n = i0(v["n"]), amin = d0(v["amin"]), amax = d0(v["amax"]), cls = v["cls"].text() ?: "", src = v["src"].text() ?: "",
            at0 = d0(v["at0"]).toLong(), gps = v["gps"].truthy(), ng = i0(v["ng"]), q = v["q"].text() ?: "",
        )
    }

    private fun encodeGps(g: GpsPick): JsonValue =
        if (g.none) obj("none" to n(1.0)) else obj("p" to JArr(g.p.map { JArr(listOf(n(it.sec), n(it.lat), n(it.lon))) }))

    private fun decodeGps(v: JsonValue): GpsPick? {
        if (v !is JObj) return null
        if (v["none"].truthy()) return GpsPick(none = true)
        val p = v["p"].items ?: return null
        return GpsPick(p = p.mapNotNull { e -> e.items?.let { x -> GpsPoint(d0(x.getOrNull(0)), d0(x.getOrNull(1)), d0(x.getOrNull(2))) } })
    }

    /** clSave() di HTML: cache cuaca <= 40, konteks dan GPS <= 400 (yang kunci terkecil dibuang). */
    fun trimClimate(c: ClimateState): ClimateState {
        val wx = if (c.wx.size > 40) c.wx.entries.toList().takeLast(40).associate { it.key to it.value } else c.wx
        fun <V> top(m: Map<Long, V>): Map<Long, V> {
            if (m.size <= 400) return m
            val drop = m.keys.map { it.toString() }.sorted().take(m.size - 400).map { it.toLong() }.toSet()
            return m.filterKeys { it !in drop }
        }
        return c.copy(wx = LinkedHashMap(wx), ctx = top(c.ctx), gps = top(c.gps))
    }

    fun encodeClimate(c: ClimateState): String {
        val loc: JsonValue = c.loc?.let { l ->
            val f = LinkedHashMap<String, JsonValue>()
            f["lat"] = n(l.lat); f["lon"] = n(l.lon)
            if (l.name.isNotEmpty()) f["name"] = s(l.name)
            if (l.manual) f["manual"] = n(1.0)
            f["t"] = n(l.t.toDouble())
            JObj(f)
        } ?: JNull
        val ctx = LinkedHashMap<String, JsonValue>(); c.ctx.forEach { (k, v) -> ctx[k.toString()] = encodeCtx(v) }
        val wx = LinkedHashMap<String, JsonValue>(); c.wx.forEach { (k, v) -> wx[k] = encodeWx(v) }
        val gps = LinkedHashMap<String, JsonValue>(); c.gps.forEach { (k, v) -> gps[k.toString()] = encodeGps(v) }
        return Json.stringify(obj("loc" to loc, "ctx" to JObj(ctx), "wx" to JObj(wx), "gps" to JObj(gps)))
    }

    fun decodeClimate(text: String?): ClimateState {
        if (text.isNullOrEmpty()) return ClimateState()
        val j = try { Json.parse(text) } catch (e: JsonException) { return ClimateState() }
        if (j !is JObj) return ClimateState()
        val l = j["loc"]
        val loc = if (l is JObj) GeoLoc(d0(l["lat"]), d0(l["lon"]), l["name"].text() ?: "", l["manual"].truthy(), d0(l["t"]).toLong()) else null
        val ctx = LinkedHashMap<Long, ClimCtx>()
        (j["ctx"] as? JObj)?.fields?.forEach { (k, v) -> k.toLongOrNull()?.let { l2 -> decodeCtx(v)?.let { ctx[l2] = it } } }
        val wx = LinkedHashMap<String, WxData>()
        (j["wx"] as? JObj)?.fields?.forEach { (k, v) ->
            val w = parseWx(obj("hourly" to (v["h"] ?: JNull)), d0(v["t"]).toLong())
            if (w != null) wx[k] = w.copy(src = v["src"].text() ?: "Open-Meteo")
        }
        val gps = LinkedHashMap<Long, GpsPick>()
        (j["gps"] as? JObj)?.fields?.forEach { (k, v) -> k.toLongOrNull()?.let { l2 -> decodeGps(v)?.let { gps[l2] = it } } }
        return ClimateState(loc, ctx, wx, gps)
    }

    // ---------- Data jam dan stream ----------

    private fun ptsOf(v: JsonValue?): List<DoubleArray>? =
        v.items?.mapNotNull { p -> p.items?.map { e -> if (e is JNull) 0.0 else e.number() }?.toDoubleArray() }

    private fun numOrNull(v: JsonValue?): Double? = if (v is JNum) v.v else null

    /** Isi `{wellness, workouts}` seperti yang dikirim WatchImport.build() di modul huawei (`acts` diabaikan). */
    fun parseWatchImport(text: String): WatchImportData? {
        val j = try { Json.parse(text) } catch (e: JsonException) { return null }
        if (j !is JObj) return null
        val well = j["wellness"].items?.mapNotNull { w ->
            if (!w.truthy()) return@mapNotNull null
            WatchWellnessIn(
                id = (if (w["id"].truthy()) w["id"].text() ?: "" else ""), restingHR = numOrNull(w["restingHR"]), hrv = numOrNull(w["hrv"]),
                hrvSpot = w["hrvSrc"].text() == "spot", sleepSecs = numOrNull(w["sleepSecs"]), sleepScore = numOrNull(w["sleepScore"]),
                steps = numOrNull(w["steps"]), spO2 = numOrNull(w["spO2"]), stress = numOrNull(w["stress"]),
                stressMax = numOrNull(w["stressMax"]), stressNight = numOrNull(w["stressNight"]),
            )
        } ?: emptyList()
        fun num0(v: JsonValue?): Double = v.number().let { if (it.isNaN()) 0.0 else it }
        val works = j["workouts"].items?.mapNotNull { w ->
            if (!w.truthy()) return@mapNotNull null
            WatchWorkoutIn(
                start = w["start"].number(), kind = w["kind"].text(), durSec = w["durSec"].number(), distM = num0(w["distM"]),
                avgHr = num0(w["avgHr"]), maxHr = num0(w["maxHr"]), elevUp = num0(w["elevUp"]),
                name = if (w["name"].truthy()) w["name"].text() ?: "" else "",
                iid = if (w["iid"].truthy()) w["iid"].text() else null, pts = ptsOf(w["pts"]),
            )
        } ?: emptyList()
        return WatchImportData(well, works)
    }

    /** get() di summarizeStream: bentuk array `[{type, data}]` atau objek `{type: [..] | {data: [..]}}`. */
    private fun streamSeries(raw: JsonValue?, t: String): List<Double>? {
        val data: JsonValue? = if (raw is JArr) {
            raw.items.firstOrNull { x -> x.truthy() && x["type"].text() == t }?.get("data")
        } else {
            val o = raw[t]
            if (o is JArr) o else o?.get("data")
        }
        return data.items?.map { if (it is JNull) 0.0 else it.number() }
    }

    /** Stream Intervals.icu untuk summarizeStream(); null bila time atau distance tidak ada. */
    fun parseStream(raw: JsonValue?): StreamData? {
        val time = streamSeries(raw, "time") ?: return null
        val dist = streamSeries(raw, "distance") ?: return null
        return StreamData(time, dist, streamSeries(raw, "heartrate"))
    }

    class LatLng(val t: List<Double?>?, val la: List<Double?>?, val lo: List<Double?>?)

    /** Bagian awal clGpsIcu(): `streams?types=time,latlng` menjadi waktu, lat, dan lon. */
    fun parseLatLng(raw: JsonValue?): LatLng {
        fun pick(t: String): JsonValue? =
            if (raw is JArr) raw.items.firstOrNull { x -> x.truthy() && x["type"].text() == t } else raw[t]
        val ll = pick("latlng")
        val tm = pick("time")
        var la = ll?.get("data")
        var lo = ll?.get("data2")
        val laItems = la.items
        var laList: List<Double?>? = null
        var loList: List<Double?>? = null
        if (laItems != null && laItems.isNotEmpty() && laItems[0] is JArr) {
            laList = laItems.map { x -> numOrNull(x.items?.getOrNull(0)) }
            loList = laItems.map { x -> numOrNull(x.items?.getOrNull(1)) }
        } else {
            laList = doubles(la)
            loList = doubles(lo)
        }
        return LatLng(doubles(tm?.get("data")), laList, loList)
    }
}
