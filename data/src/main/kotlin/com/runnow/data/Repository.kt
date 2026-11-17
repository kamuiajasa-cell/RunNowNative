package com.runnow.data

import com.runnow.domain.Act
import com.runnow.domain.ActKind
import com.runnow.domain.ClimAvg
import com.runnow.domain.ClimCtx
import com.runnow.domain.GeoLoc
import com.runnow.domain.GpsPick
import com.runnow.domain.ProcessResult
import com.runnow.domain.Settings
import com.runnow.domain.StreamSummary
import com.runnow.domain.SyncInfo
import com.runnow.domain.WxData
import com.runnow.domain.clAvg
import com.runnow.domain.climCtxOf
import com.runnow.domain.climDate
import com.runnow.domain.climHour
import com.runnow.domain.clPers
import com.runnow.domain.gPick
import com.runnow.domain.processData
import com.runnow.domain.summarizeStream
import com.runnow.domain.withProcessed
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.max
import kotlin.math.min

data class SyncResult(val ok: Boolean, val added: Int = 0, val message: String = "", val offline: Boolean = false)

class IngestSummary(val wellness: Int, val workouts: Int)

/**
 * Lapisan data RunSense: menggabungkan Intervals.icu, jam Huawei, cuaca, dan penyimpanan. Padanan sync(), pullWatch(),
 * ingestWatch(), ensureStream(), clMatch(), wxGet(), persist(), restore(), dan logout() di runsense.html.
 *
 * Tidak aman dipakai dari banyak thread sekaligus untuk operasi yang sama; operasi jaringan dijaga dengan penanda
 * `syncing`/`pulling`/`clBusy` seperti di HTML. Perubahan data memanggil [onChange].
 */
class RunSenseRepository(
    private val store: KeyValueStore,
    private val icu: IcuApi,
    private val weather: WeatherApi,
    private val watch: WatchSource?,
    private val locator: Locator?,
    private val clock: Clock,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    companion object {
        const val KEY_STORE = "runsense_auto_v2"
        const val KEY_CLIM = "runsense_clim"
        const val KEY_API = "runsense_key"
        const val KEY_SRC = "runsense_src"
        const val KEY_WX = "runsense_wx"
        private const val DAY = 864e5
    }

    var data: AppData = AppData()
        private set
    var climate: ClimateState = ClimateState()
        private set
    var apiKey: String? = null
        private set

    /** state.src: "" | "watch". */
    var source: String = ""
        private set
    var onChange: (() -> Unit)? = null

    val isDemo: Boolean get() = apiKey == "demo"

    private var syncing = false
    private var pulling = false
    private var clBusy = false
    private val sxBusy = HashSet<String>()

    // ---------- penyimpanan ----------

    /** restore() di HTML: baca data, cuaca, kunci API, dan sumber dari penyimpanan. */
    fun restore() {
        apiKey = store.read(KEY_API)
        source = store.read(KEY_SRC) ?: ""
        data = Codec.decodeApp(store.read(KEY_STORE)) ?: AppData()
        climate = Codec.decodeClimate(store.read(KEY_CLIM))
        onChange?.invoke()
    }

    private fun persist() {
        if (isDemo) return // data demo tidak pernah disimpan
        try {
            store.write(KEY_STORE, Codec.encodeApp(data))
        } catch (e: Exception) {
        }
    }

    private fun update(d: AppData) {
        data = d
        persist()
        onChange?.invoke()
    }

    private fun clSave() {
        climate = Codec.trimClimate(climate)
        try {
            store.write(KEY_CLIM, Codec.encodeClimate(climate))
        } catch (e: Exception) {
        }
    }

    fun login(key: String) {
        apiKey = key
        if (key != "demo") store.write(KEY_API, key)
        onChange?.invoke()
    }

    /** Masuk mode demo: data contoh hanya di memori. */
    fun enterDemo(demoData: AppData) {
        apiKey = "demo"
        data = demoData
        onChange?.invoke()
    }

    fun setSource(src: String) {
        source = src
        store.write(KEY_SRC, src)
    }

    /** logout() di HTML: hapus kunci dan seluruh data lokal (cache cuaca dibiarkan, seperti di HTML). */
    fun logout() {
        store.remove(KEY_API)
        store.remove(KEY_SRC)
        store.remove(KEY_STORE)
        source = ""
        apiKey = null
        data = AppData()
        onChange?.invoke()
    }

    // ---------- pengolahan (domain) ----------

    /** processData(): hasilnya dipakai UI. RHR otomatis dan tahap (afS) yang baru ikut disimpan. */
    fun process(settings: Settings = Settings()): ProcessResult {
        val snap = data.snapshot(clock.now(), zone, settings)
        val r = snap.processData(SyncInfo(data.icuName, data.icuLast, data.hwLast))
        if (r.profile != data.profile) update(data.copy(profile = r.profile))
        return r
    }

    /** Snapshot lengkap (profil, athlete hasil hitung, ringkasan stream) untuk reviewRun/runBacktest. */
    fun processedSnapshot(settings: Settings = Settings()): com.runnow.domain.Snapshot {
        val snap = data.snapshot(clock.now(), zone, settings)
        return snap.withProcessed(snap.processData(SyncInfo(data.icuName, data.icuLast, data.hwLast)))
    }

    // ---------- Intervals.icu ----------

    private fun fmtDate(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()

    private fun hhmm(ms: Long) = climHour(ms, zone)

    /** sync() di HTML. [silent] = tanpa pesan "isi API key". */
    suspend fun sync(silent: Boolean = false): SyncResult {
        val key = apiKey
        if (key.isNullOrEmpty() || key == "demo") return SyncResult(false, message = if (silent) "" else "Isi API key dulu")
        if (syncing) return SyncResult(false)
        syncing = true
        try {
            var d = data
            try {
                d = Ingest.applyAthlete(d, icu.get("athlete/0"), clock.now(), zone)
            } catch (e: Exception) {
            }
            val now = clock.now()
            val old = fmtDate((now - (if (d.acts.isNotEmpty()) 100 else 400) * DAY).toLong())
            val neu = fmtDate((now + DAY).toLong())
            val acts = icu.get("athlete/0/activities?oldest=$old&newest=$neu")
            val well: JsonValue = try {
                icu.get("athlete/0/wellness?oldest=$old&newest=$neu")
            } catch (e: Exception) {
                JArr(emptyList())
            }
            val (d1, added) = Ingest.mergeActs(d, acts.items ?: emptyList(), zone)
            var d2 = Ingest.dedupeActs(d1).first
            if (well is JArr) d2 = Ingest.mergeIcuWellness(d2, well.items)
            d2 = d2.copy(icuLast = clock.now())
            update(d2)
            val msg = "Sinkron · $added baru/diubah · " + d2.icuName.ifEmpty { "OK" } + " · " + hhmm(d2.icuLast) +
                (if (d2.hwLast != 0L) " · Jam " + hhmm(d2.hwLast) else "")
            return SyncResult(true, added, msg)
        } catch (e: IcuException) {
            val off = e.kind == IcuException.Kind.NETWORK
            return SyncResult(false, message = if (off) "Offline · memakai data lokal" else "Gagal: " + e.message, offline = off)
        } catch (e: Exception) {
            return SyncResult(false, message = "Gagal: " + (e.message ?: e.toString()))
        } finally {
            syncing = false
        }
    }

    /** ensureStream() di HTML: ambil dan ringkas stream sebuah lari dari Intervals.icu, sekali saja. */
    suspend fun ensureStream(a: Act) {
        val id = a.icuId
        val key = apiKey
        if (id.isNullOrEmpty() || key.isNullOrEmpty() || key == "demo") return
        if (id.startsWith("hw")) return // lari dari jam: ringkasan dihitung dari titik data jam saat masuk
        if (data.sx.containsKey(id) || sxBusy.contains(id)) return
        if (a.kind != ActKind.RUN || a.min < 5 || a.km < 0.3) return
        sxBusy.add(id)
        var result: StreamSummary? = null
        try {
            val raw = icu.get("activity/${java.net.URLEncoder.encode(id, "UTF-8").replace("+", "%20")}/streams.json?types=time,distance,heartrate")
            val sd = Codec.parseStream(raw)
            result = (if (sd != null) data.snapshot(clock.now(), zone).summarizeStream(sd, 30) else null) ?: StreamSummary.NONE
        } catch (e: IcuException) {
            // 4xx = stream memang tidak ada (disimpan); jaringan atau auth = coba lagi nanti
            if (e.kind == IcuException.Kind.HTTP && e.status in 400..499) result = StreamSummary.NONE
        } catch (e: Exception) {
        }
        sxBusy.remove(id)
        val r = result ?: return
        val sx = LinkedHashMap(data.sx)
        sx[id] = r
        while (sx.size > Ingest.MAX_SX) sx.remove(sx.keys.first())
        update(data.copy(sx = sx))
    }

    // ---------- jam Huawei ----------

    /** pullProfile() di HTML: HR maks, RHR, dan umur dari profil jam, hanya yang belum terisi. */
    private fun pullProfile() {
        val p = data.profile
        if (isDemo || (p.max > 0 && p.rest > 0 && p.age > 0)) return
        val w = watch?.profile() ?: return
        var prof = p
        if (!(prof.max > 0) && w.maxHr >= 100) prof = prof.copy(max = w.maxHr.toDouble())
        if (!(prof.rest > 0) && w.restingHr > 30) prof = prof.copy(rest = w.restingHr.toDouble())
        if (w.birthYear > 1900 && !(prof.age > 0)) {
            val year = Instant.ofEpochMilli(clock.now()).atZone(zone).year
            prof = prof.copy(age = max(12, min(90, year - w.birthYear)))
        }
        update(data.copy(profile = prof))
    }

    /** pullWatch() di HTML: profil lalu data jam. Mengembalikan ringkasan bila ada data yang masuk. */
    suspend fun pullWatch(): IngestSummary? {
        val src = watch ?: return null
        if (pulling) return null
        pulling = true
        try {
            pullProfile()
            val d = src.watchData() ?: return null
            return ingestWatch(d)
        } catch (e: Exception) {
            return null
        } finally {
            pulling = false
        }
    }

    /** ingestWatch() di HTML. null di mode demo. */
    fun ingestWatch(w: WatchImportData): IngestSummary? {
        if (isDemo) return null
        val res = Ingest.ingestWatch(data, w, clock.now(), zone) { ms -> climate.gps.containsKey(ms) }
        update(res.data)
        if (res.gps.isNotEmpty()) {
            climate = climate.copy(gps = climate.gps + res.gps)
            clSave()
        }
        return IngestSummary(res.wellness, res.workouts)
    }

    // ---------- cuaca dan iklim ----------

    private fun r2(x: Double) = toFixed(x, 2).toDouble()

    /** Lokasi perangkat sekali bila belum ada lokasi (climHome di HTML). */
    suspend fun ensureLocation(): GeoLoc? {
        climate.loc?.let { return it }
        val d = locator?.lastLocation() ?: return null
        val loc = GeoLoc(r2(d.lat), r2(d.lon), t = clock.now())
        climate = climate.copy(loc = loc)
        clSave()
        return loc
    }

    /** clLoc() di HTML: pilih lokasi lewat nama kota; cache cuaca dan konteks lama dibuang. null bila tidak ketemu. */
    suspend fun setLocationByName(name: String): GeoLoc? {
        val g = weather.geocode(name) ?: return null
        val loc = g.copy(manual = true, t = clock.now())
        climate = climate.copy(loc = loc, wx = emptyMap(), ctx = emptyMap())
        clSave()
        return loc
    }

    /** wxGet() di HTML: cuaca per grid 0,1 derajat dengan cache; gagal -> cache lama (bila ada). */
    suspend fun wxGet(la: Double, lo: Double, mode: String, day: String): WxData? {
        val k = mode + toFixed(la, 1) + "," + toFixed(lo, 1) + (if (mode == "ar") day else "")
        val c = climate.wx[k]
        val ttl = if (mode == "fc") 30 * 60e3 else 30 * DAY
        val now = clock.now()
        if (c != null && now - c.t < ttl) return c
        return try {
            val j = if (mode == "fc") weather.forecast(r2(la), r2(lo)) else weather.archive(r2(la), r2(lo), day)
            val o = Codec.parseWx(j, now) ?: return c
            val wx = LinkedHashMap(climate.wx)
            wx[k] = o
            climate = climate.copy(wx = wx)
            clSave()
            o
        } catch (e: Exception) {
            c
        }
    }

    /** gPick dari stream latlng Intervals; null = gagal sementara, coba lagi nanti (clGpsIcu di HTML). */
    private suspend fun gpsFromIcu(a: Act): GpsPick? {
        val id = a.icuId ?: ""
        if (id.isEmpty() || id.startsWith("hw")) return GpsPick(none = true).also { putGps(a.start, it) }
        val key = apiKey
        if (key.isNullOrEmpty() || key == "demo") return null
        return try {
            val raw = icu.get("activity/$id/streams?types=time,latlng")
            val ll = Codec.parseLatLng(raw)
            val g = if (ll.la != null && ll.lo != null) gPick(ll.t, ll.la, ll.lo, a.min * 60) else GpsPick(none = true)
            putGps(a.start, g)
            g
        } catch (e: IcuException) {
            if (e.kind == IcuException.Kind.HTTP && e.status in 400..499) GpsPick(none = true).also { putGps(a.start, it) } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun putGps(ms: Long, g: GpsPick) {
        val m = LinkedHashMap(climate.gps)
        m[ms] = g
        climate = climate.copy(gps = m)
    }

    /**
     * clMatch() di HTML: cocokkan cuaca ke <= 10 lari terbaru yang belum punya konteks (titik GPS aktivitas, atau
     * lokasi terakhir bila tanpa GPS). Mengembalikan jumlah lari yang diproses; panggil lagi selama > 0
     * (HTML menjadwalkan ulang dirinya 800 ms kemudian).
     */
    suspend fun climateMatch(): Int {
        if (clBusy) return 0
        val now = clock.now()
        fun needs(x: Act): Boolean {
            val c = climate.ctx[x.start] ?: return true
            if (c.gps) return false
            val g = climate.gps[x.start]
            return g == null || g.hasPoints
        }
        val todo = data.acts
            .filter { it.kind == ActKind.RUN && it.km >= 1.5 && now - it.start > 3600e3 && needs(it) }
            .sortedByDescending { it.start }
            .take(10)
        if (todo.isEmpty()) return 0
        clBusy = true
        var n = 0
        try {
            val snap = data.snapshot(now, zone)
            val noKey = apiKey.isNullOrEmpty() || apiKey == "demo"
            for (a in todo) {
                val g = climate.gps[a.start] ?: gpsFromIcu(a)
                val loc = climate.loc
                if (g == null && !(loc != null && noKey)) continue
                val pts: List<Triple<Double, Double, Double>> = when {
                    g != null && g.hasPoints -> g.p.map { Triple(it.sec, it.lat, it.lon) }
                    loc != null -> listOf(Triple(0.0, loc.lat, loc.lon))
                    else -> continue
                }
                val fc = now - a.start < 7 * DAY
                val cs = ArrayList<ClimAvg>()
                var ok = true
                for (i in pts.indices) {
                    val t0 = a.start + pts[i].first * 1e3
                    val t1 = if (i + 1 < pts.size) a.start + pts[i + 1].first * 1e3 else a.start + a.min * 60e3
                    val o = wxGet(pts[i].second, pts[i].third, if (fc) "fc" else "ar", climDate(t0.toLong(), zone))
                    if (o == null) { ok = false; break }
                    val c = clAvg(o, t0, max(t1, t0 + 1), zone)
                    if (c != null) cs.add(c)
                }
                if (!ok) continue
                val ctx = LinkedHashMap(climate.ctx)
                if (cs.isEmpty()) {
                    ctx[a.start] = ClimCtx(na = true)
                } else {
                    val P = snap.clPers(climate.ctx)
                    ctx[a.start] = climCtxOf(cs, P, clock.now(), g != null && g.hasPoints, pts.size)
                }
                climate = climate.copy(ctx = ctx)
                n++
            }
        } catch (e: Exception) {
        }
        clBusy = false
        if (n > 0) {
            clSave()
            onChange?.invoke()
        }
        return n
    }

    /** autoHeat() di HTML: suhu udara asli >= 30 C = hari panas. Sekali sehari. True bila UI perlu menyalakan mode panas. */
    suspend fun autoHeat(): Boolean {
        if (isDemo) return false
        val today = Instant.ofEpochMilli(clock.now()).atZone(zone).toLocalDate().toString()
        if (store.read(KEY_WX) == today) return false
        val loc = locator?.lastLocation() ?: return false
        val t = weather.currentTemp(loc.lat, loc.lon) ?: return false
        store.write(KEY_WX, today)
        return t >= 30
    }
}
