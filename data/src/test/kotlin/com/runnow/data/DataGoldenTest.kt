package com.runnow.data

import com.runnow.domain.GeoLoc
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.fail

/**
 * Menjalankan alur sync, ingestWatch, ensureStream, dan clMatch di RunSenseRepository dengan Intervals.icu, cuaca,
 * dan penyimpanan palsu, lalu membandingkan hasil simpanannya dengan hasil kode asli runsense.html
 * (src/test/resources/golden_data.txt, dibuat oleh domain/gen/gen_data.js).
 */
class DataGoldenTest {
    // ---------- palsu ----------
    private class FakeStore : KeyValueStore {
        val m = HashMap<String, String>()
        override fun read(key: String) = m[key]
        override fun write(key: String, value: String) { m[key] = value }
        override fun remove(key: String) { m.remove(key) }
    }

    private class FakeIcu(val resp: Map<String, String>) : IcuApi {
        override suspend fun get(path: String): JsonValue {
            val spec = resp[path.substringBefore("?")] ?: throw IcuException(IcuException.Kind.HTTP, 404, "HTTP 404")
            val v = Json.parse(spec)
            if (v is JObj) {
                if (v.fields.containsKey("__throw")) throw IcuException(IcuException.Kind.NETWORK, 0, "Failed to fetch")
                val st = v.fields["__status"]
                if (st is JNum) {
                    val code = st.v.toInt()
                    if (code == 401 || code == 403) throw IcuException(IcuException.Kind.AUTH, code, "API key ditolak")
                    throw IcuException(IcuException.Kind.HTTP, code, "HTTP $code")
                }
            }
            return v
        }
    }

    /** Pasangan Kotlin dari synthWx() di domain/gen/data_harness.js: hanya aritmetika bilangan bulat. */
    private class FakeWeather(val now: () -> Long, val fail: Boolean) : WeatherApi {
        private fun synth(la: Double, lo: Double, from: Long, to: Long): JsonValue {
            val time = ArrayList<JsonValue>()
            val cols = LinkedHashMap<String, ArrayList<JsonValue>>()
            for (k in listOf("temperature_2m", "relative_humidity_2m", "apparent_temperature", "wind_speed_10m", "precipitation", "shortwave_radiation", "cloud_cover", "weather_code", "precipitation_probability")) cols[k] = ArrayList()
            val latI = Math.round(la * 100)
            val lonI = Math.round(lo * 100)
            var ms = from
            while (ms <= to) {
                val h = ((ms / 3_600_000L) % 24).toInt()
                val dn = Math.floorDiv(ms, 86_400_000L)
                time += JStr(Instant.ofEpochMilli(ms).toString().substring(0, 13) + ":00")
                val t = 22 + ((h * 5 + abs(latI) * 3 + dn * 2) % 13)
                fun add(k: String, v: Double) { cols.getValue(k).add(JNum(v)) }
                add("temperature_2m", t.toDouble())
                add("apparent_temperature", (t + ((h + dn) % 4)).toDouble())
                add("relative_humidity_2m", (50 + ((h * 7 + abs(lonI)) % 41)).toDouble())
                add("wind_speed_10m", ((h * 3 + dn) % 17).toDouble())
                add("precipitation", if (((h + dn + latI) % 23) == 0L) 2.5 else if (((h + dn) % 11) == 0L) 0.4 else 0.0)
                add("shortwave_radiation", if (h in 6..17) (((h - 5) * 60) % 700).toDouble() else 0.0)
                add("cloud_cover", ((h * 13) % 101).toDouble())
                add("weather_code", if (((h + dn) % 29) == 0L) 95.0 else ((h % 5) * 3).toDouble())
                add("precipitation_probability", ((h * 9) % 60).toDouble())
                ms += 3_600_000L
            }
            if (latI % 7 == 0L && abs(latI) % 2 == 1L) {
                cols.getValue("temperature_2m")[3] = JNull
                cols.getValue("apparent_temperature")[3] = JNull
            }
            val h = LinkedHashMap<String, JsonValue>()
            h["time"] = JArr(time)
            for ((k, v) in cols) h[k] = JArr(v)
            return JObj(linkedMapOf("hourly" to JObj(h)))
        }

        override suspend fun forecast(lat: Double, lon: Double): JsonValue? {
            if (fail) return null
            val d0 = Math.floorDiv(now(), 86_400_000L) * 86_400_000L
            return synth(lat, lon, d0 - 7 * 86_400_000L, d0 + 3 * 86_400_000L - 3_600_000L)
        }

        override suspend fun archive(lat: Double, lon: Double, day: String): JsonValue? {
            if (fail) return null
            val from = LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            return synth(lat, lon, from, from + 86_400_000L - 3_600_000L)
        }

        override suspend fun geocode(name: String): GeoLoc? = null
        override suspend fun currentTemp(lat: Double, lon: Double): Double? = null
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
        return result!!.getOrThrow()
    }

    // ---------- pembanding ----------
    /** Kunci bernilai kosong (null, 0, false, "") pada objek wellness dianggap tidak ada; cache cuaca = kunci dan waktunya. */
    private fun norm(v: JsonValue, key: String? = null): JsonValue = when (v) {
        is JArr -> JArr(v.items.map { x ->
            if (key == "wellness" && x is JObj) JObj(x.fields.filterValues { !(it is JNull || (it is JNum && it.v == 0.0) || (it is JBool && !it.v) || (it is JStr && it.v.isEmpty())) })
            else norm(x)
        })
        is JObj -> JObj(LinkedHashMap<String, JsonValue>().also { m ->
            for ((k, x) in v.fields) m[k] = if (k == "wx" && x is JObj) JObj(x.fields.mapValues { (_, w) -> if (w is JObj) (w["t"] ?: JNull) else w }) else norm(x, k)
        })
        else -> v
    }

    private fun same(what: String, exp: String, got: String?, problems: MutableList<String>) {
        if (exp == "-" || got == null) {
            if (!(exp == "-" && got == null)) problems += "$what: diharapkan ${if (exp == "-") "(kosong)" else "ada"}, didapat ${if (got == null) "(kosong)" else "ada"}"
            return
        }
        val a = norm(Json.parse(exp))
        val b = norm(Json.parse(got))
        if (a != b) {
            val ea = Json.stringify(a)
            val eb = Json.stringify(b)
            var i = 0
            while (i < ea.length && i < eb.length && ea[i] == eb[i]) i++
            problems += "$what berbeda mulai posisi $i:\n      diharapkan: …${ea.substring(max0(i - 60), minOf(ea.length, i + 120))}\n      didapat:    …${eb.substring(max0(i - 60), minOf(eb.length, i + 120))}"
        }
    }

    private fun max0(x: Int) = if (x < 0) 0 else x

    private class Sc(val kind: String, val name: String) {
        var state = ""
        var clim: String? = null
        val icu = LinkedHashMap<String, String>()
        var now = 0L
        var key = "KEY"
        var wxFail = false
        var watch = ""
        var act = 0L
        val out = LinkedHashMap<String, String>()
    }

    @Test
    fun dataLayerMatchesHtml() {
        val text = javaClass.getResource("/golden_data.txt")!!.readText()
        val scs = ArrayList<Sc>()
        var cur: Sc? = null
        for (line in text.lines()) {
            if (line.isEmpty() || line.startsWith("#")) continue
            val p = line.split("\t")
            when (p[0]) {
                "S" -> { cur = Sc(p[1], p[2]); scs += cur }
                "I" -> when (p[1]) {
                    "state" -> cur!!.state = p[2]
                    "clim" -> cur!!.clim = p[2]
                    "icu" -> cur!!.icu[p[2]] = p[3]
                    "now" -> cur!!.now = p[2].toLong()
                    "key" -> cur!!.key = p.getOrElse(2) { "" }
                    "wxfail" -> cur!!.wxFail = p[2] == "1"
                    "watch" -> cur!!.watch = p[2]
                    "act" -> cur!!.act = p[2].toLong()
                }
                "O" -> cur!!.out[p[1]] = p.getOrElse(2) { "" }
            }
        }

        val problems = ArrayList<String>()
        var checks = 0
        for (sc in scs) {
            val mark = problems.size
            val store = FakeStore()
            store.write(RunSenseRepository.KEY_STORE, sc.state)
            sc.clim?.let { store.write(RunSenseRepository.KEY_CLIM, it) }
            val repo = RunSenseRepository(store, FakeIcu(sc.icu), FakeWeather({ sc.now }, sc.wxFail), null, null, { sc.now }, ZoneOffset.UTC)
            repo.restore()
            if (sc.key.isNotEmpty()) repo.login(sc.key)
            try {
                when (sc.kind) {
                    "sync" -> {
                        val r = runSuspend { repo.sync() }
                        checks += 2
                        if (r.ok.toString() != sc.out["ok"]) problems += "ok: diharapkan ${sc.out["ok"]}, didapat ${r.ok}"
                        if (r.message != sc.out["msg"]) problems += "pesan: diharapkan ${sc.out["msg"]}, didapat ${r.message}"
                        same("simpanan", sc.out.getValue("store"), store.read(RunSenseRepository.KEY_STORE), problems)
                    }
                    "watch" -> {
                        val w = Codec.parseWatchImport(sc.watch) ?: fail("data jam tidak terbaca")
                        repo.ingestWatch(w)
                        checks += 2
                        same("simpanan", sc.out.getValue("store"), store.read(RunSenseRepository.KEY_STORE), problems)
                        same("cuaca/GPS", sc.out.getValue("clim"), store.read(RunSenseRepository.KEY_CLIM), problems)
                    }
                    "stream" -> {
                        val a = repo.data.acts.first { it.start == sc.act }
                        runSuspend { repo.ensureStream(a) }
                        checks++
                        same("simpanan", sc.out.getValue("store"), store.read(RunSenseRepository.KEY_STORE), problems)
                    }
                    "match" -> {
                        for (round in 1..3) {
                            runSuspend { repo.climateMatch() }
                            checks++
                            same("cuaca putaran $round", sc.out.getValue("clim$round"), store.read(RunSenseRepository.KEY_CLIM), problems)
                        }
                    }
                }
            } catch (e: Throwable) {
                problems += "EXCEPTION: $e\n" + e.stackTrace.take(6).joinToString("\n") { "        $it" }
            }
            for (i in mark until problems.size) problems[i] = "[${sc.kind}/${sc.name}] " + problems[i]
        }
        if (problems.isNotEmpty()) fail("${problems.size} selisih dari $checks perbandingan (${scs.size} skenario). Contoh pertama:\n" + problems.take(10).joinToString("\n"))
        check(scs.size >= 50 && checks > 100) { "Data uji terlalu sedikit: ${scs.size} skenario, $checks perbandingan" }
    }
}
