package com.joyodigitama.huaweiwear

import android.Manifest
import android.content.Context
import android.os.Build
import android.webkit.JavascriptInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Berkas yang dipilih pengguna lewat pemilih berkas sistem. */
class PickedFile(val name: String, val mime: String?, val bytes: ByteArray)

/** Hal yang hanya bisa dilakukan Activity. Dipasang oleh aplikasi. */
interface RunNowHost {
  /** Minta izin. True kalau semua [required] diberikan; [optional] hanya diminta. Boleh dipanggil dari thread mana pun. */
  suspend fun ask(required: List<String>, optional: List<String> = emptyList()): Boolean

  /** Buka layar di aplikasi. False kalau layar itu tidak ada. Boleh dipanggil dari thread mana pun. */
  fun open(route: String): Boolean

  /** Jalankan JavaScript di halaman. Implementasi yang memastikan jalan di thread utama. */
  fun eval(js: String)

  /** Buka pemilih berkas sistem (mime mis. "application/octet-stream"; bintang-garis-miring-bintang untuk semua jenis) dan baca isinya. Null kalau dibatalkan. */
  suspend fun pickFile(mime: String): PickedFile?
}

/**
 * Pintu panggilan dari halaman ke Huawei core. Didaftarkan ke WebView dengan nama `ReactNativeWebView`,
 * jadi `rn(...)` di index.html dan pengaturan.html jalan tanpa diubah: halaman memanggil
 * `postMessage({type:"rpc", id, method, args})` dan jawabannya dikirim balik lewat `window.__rn(id, ok, nilai)`.
 *
 * Semua metode didaftarkan di [table]: satu baris per metode berisi aturan argumen dan isinya.
 * Metode yang tidak ada di tabel tidak bisa dipanggil. Menambah metode baru = menambah satu baris `t.m(...)`
 * di salah satu fungsi `addXxx` di bawah (atau fungsi kelompok baru yang didaftarkan di [table]).
 * Hasil (Map, List, angka, teks, null) diubah ke JSON oleh [toJson].
 */
class RunNowBridge(context: Context, private val host: RunNowHost) {
  private val app = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val runtime: WearRuntime get() = WearRuntime.get(app)

  // ---- tabel metode ----

  /** Aturan argumen satu metode: null kalau benar, selain itu pesan kesalahan untuk pengguna. */
  private fun interface Rule {
    fun check(args: List<Any?>): String?
  }

  private class Rpc(val rule: Rule, val call: suspend (List<Any?>) -> Any?)

  private val table: Map<String, Rpc> = HashMap<String, Rpc>().also { t ->
    addSync(t)
    addBasics(t)
    addWeather(t)
    addNotifications(t)
    addLocation(t)
    addHealth(t)
    addScreens(t)
  }

  private fun MutableMap<String, Rpc>.m(name: String, rule: Rule = NONE, call: suspend (List<Any?>) -> Any?) {
    this[name] = Rpc(rule, call)
  }

  // ---- masuk dari halaman ----

  @JavascriptInterface
  fun postMessage(raw: String?) {
    val msg = try { JSONObject(raw ?: return) } catch (_: Exception) { return }
    if (msg.optString("type") != "rpc") return
    val id = msg.optLong("id", -1L)
    val method = msg.optString("method")
    if (id < 0 || method.isEmpty()) return
    val args = jsonList(msg.optJSONArray("args"))
    scope.launch {
      try {
        reply(id, true, run(method, args))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        reply(id, false, e.message ?: e.javaClass.simpleName)
      }
    }
  }

  // ---- keluar ke halaman ----

  private val listener = object : WearEvents {
    override fun state(state: LinkState, error: String?) = Unit
    override fun battery(level: Int) = Unit
    override fun syncComplete(summary: SyncSummary) {
      if (pushesWatchData) pushWatchData()
    }
    override fun log(direction: LogDirection, serviceId: Int?, commandId: Int?, hex: String?, message: String) = Unit

    override fun hrvProgress(secondsLeft: Int, beats: Int) {
      host.eval("window.__hrvTick&&window.__hrvTick($secondsLeft);")
    }

    override fun heartRate(bpm: Int, tsMs: Long) {
      host.eval("window.nativeLiveHr&&window.nativeLiveHr($bpm,$tsMs);")
    }
  }

  /** Hanya bridge milik halaman utama (index.html) yang mengirim data jam; MainActivity menyalakannya. */
  @Volatile
  var pushesWatchData: Boolean = false

  /**
   * Kirim data jam (wellness, aktivitas, latihan) ke halaman lewat `window.nativeImport`. Dipanggil setelah halaman selesai
   * dimuat dan setelah tiap sinkron. Tanpa database jam atau tanpa data, tidak mengirim apa-apa. Aman dipanggil dari thread mana pun.
   */
  fun pushWatchData() {
    scope.launch(Dispatchers.IO) {
      try {
        val r = WatchImport.build(app) ?: return@launch
        if (r.wellnessDays == 0 && r.acts == 0 && r.workouts == 0) return@launch
        // Pemisah baris Unicode sah di JSON tetapi memutus literal JavaScript.
        val js = r.json.toString().replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
        host.eval("window.nativeImport&&window.nativeImport($js);")
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        // database jam belum terbaca: dicoba lagi pada sinkron berikutnya
      }
    }
  }

  /** Mulai meneruskan progres HRV dan detak live ke halaman. Panggil dari onCreate. */
  fun attach() = runtime.addListener(listener)

  /** Berhenti meneruskan. Panggil dari onDestroy. */
  fun detach() {
    runtime.removeListener(listener)
    scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
  }

  /** Detak live hanya boleh jalan selama halaman terlihat; jam cepat habis kalau tidak. */
  fun setLive(active: Boolean) {
    try {
      runtime.liveHeartRate.setActive(active)
    } catch (_: Exception) {
      // jam belum tersambung: tidak ada yang perlu dilakukan
    }
  }

  private fun reply(id: Long, ok: Boolean, value: Any?) {
    val literal = when (val j = toJson(value)) {
      null -> "null"
      is String -> JSONObject.quote(j)
      else -> j.toString()
    }
    host.eval("window.__rn&&window.__rn($id,$ok,$literal);")
  }

  private suspend fun run(method: String, args: List<Any?>): Any? {
    val rpc = table[method] ?: throw IllegalArgumentException("Metode tidak diizinkan: $method")
    rpc.rule.check(args)?.let { throw IllegalArgumentException(it) }
    return rpc.call(args)
  }

  // ---- kelompok metode ----

  /** Sinkron dan latar belakang. */
  private fun addSync(t: MutableMap<String, Rpc>) {
    t.m("syncNow") {
      requireConnected()
      runtime.link.syncActivity(fullHistory = false, sinceDays = null)
      null
    }
    t.m("getBackgroundStatus") { runtime.backgroundStatus() }
    t.m("getBackgroundRestrictions") { runtime.backgroundRestrictions() }
    t.m("setBackgroundEnabled", BOOL) { a ->
      if (a[0] != true) {
        runtime.setBackground(null)
      } else {
        val address = runtime.settings.watchAddress ?: throw IllegalStateException("Hubungkan jam dulu lewat layar Koneksi.")
        if (!notificationsAllowed()) throw IllegalStateException("Izin notifikasi belum diberikan.")
        if (!bluetoothAllowed()) throw IllegalStateException("Izin Bluetooth belum diberikan.")
        runtime.setBackground(address)
      }
      runtime.backgroundStatus()
    }
    t.m("setSyncIntervalMinutes", intIn("Interval sinkron tidak valid.", SYNC_INTERVALS)) { a ->
      runtime.setSyncInterval((a[0] as Number).toInt())
      runtime.backgroundStatus()
    }
    t.m("openBatteryExemption") { runtime.openBatteryExemption(); null }
    t.m("openAutoStartSettings") { runtime.openAutoStartSettings(); null }
  }

  /** Jam dan dasar: profil, zona detak, bahasa jam, baterai, riwayat penuh, laporan dukungan, lupakan jam. */
  private fun addBasics(t: MutableMap<String, Rpc>) {
    t.m("getUserProfile") { profileJs() }
    t.m("setUserProfile", PROFILE) { a ->
      val o = a[0] as JSONObject
      val profile = UserProfileData(
        heightCm = o.getInt("heightCm"),
        weightKg = o.getDouble("weightKg"),
        birthYear = o.getInt("birthYear"),
        sex = o.getString("sex"),
        dailyStepGoal = o.getInt("dailyStepGoal"),
      )
      mapOf("sent" to runtime.setUserProfile(profile))
    }
    t.m("getHeartRateZonesStatus") { zonesJs() }
    t.m("setHeartRateZones", ZONES) { a ->
      val o = a[0] as JSONObject?
      val zones = o?.let {
        HeartRateZonesCalc.UserZones(
          method = if (it.getString("method") == "hrr") HeartRateZonesCalc.Method.HRR else HeartRateZonesCalc.Method.MHR,
          maxHr = it.getInt("maxHr"),
          restingHr = it.getInt("restingHr"),
          zones = zonesList(it) ?: throw IllegalArgumentException("Zona tidak valid."),
          warningEnabled = it.getBoolean("warningEnabled"),
          warningLimit = it.getInt("warningLimit"),
        )
      }
      mapOf("sent" to runtime.setHeartRateZones(zones))
    }
    t.m("getWatchLanguage") { mapOf("language" to runtime.settings.watchLanguage) }
    t.m("setWatchLanguage", LANG) { a -> mapOf("took" to runtime.language.setPinned(a[0] as String?)) }
    t.m("getForceBle") { runtime.forceBle() }
    t.m("setForceBle", BOOL) { a -> runtime.setForceBle(a[0] as Boolean); null }
    t.m("getMorningSummaryEnabled") { runtime.morningSummaryEnabled() }
    t.m("setLiveHeartRate", BOOL) { a -> runtime.liveHeartRate.setActive(a[0] as Boolean); null }
    t.m("getSupportReport") { runtime.supportReport() }
    // Apa yang akan dikirim ke halaman utama; untuk memeriksa bahwa sinkron benar-benar mengisi database jam.
    t.m("getWatchImportSummary") {
      withContext(Dispatchers.IO) {
        val r = WatchImport.build(app)
        mapOf(
          "available" to (r != null),
          "wellnessDays" to (r?.wellnessDays ?: 0),
          "acts" to (r?.acts ?: 0),
          "workouts" to (r?.workouts ?: 0),
          "withRoute" to (r?.withRoute ?: 0),
        )
      }
    }
    t.m("refreshBattery") {
      requireConnected()
      mapOf("level" to runtime.link.refreshBattery())
    }
    t.m("syncFullHistory") {
      requireConnected()
      runtime.link.syncActivity(fullHistory = true, sinceDays = null)
      null
    }
    t.m("refetchWorkoutData", REFETCH) { a ->
      requireConnected()
      val arr = a[0] as JSONArray
      runtime.link.refetchWorkoutData(List(arr.length()) { i -> arr.getLong(i) }).toJs()
    }
    // Tanpa argumen: yang dilupakan selalu jam yang sedang diingat, bukan alamat kiriman halaman.
    t.m("forgetDevice") {
      val address = runtime.settings.watchAddress ?: throw IllegalStateException("Belum ada jam yang diingat.")
      runtime.forget(address)
      null
    }
  }

  /** Cuaca. */
  private fun addWeather(t: MutableMap<String, Rpc>) {
    t.m("getWeatherStatus") { runtime.weather.status() }
    t.m("setWeatherEnabled", BOOL) { a -> runtime.weather.setEnabled(a[0] as Boolean); runtime.weather.status() }
    t.m("setWeatherUnit", oneOf("Satuan suhu tidak dikenal.", "celsius", "fahrenheit")) { a ->
      runtime.weather.setUnit(a[0] as String)
      runtime.weather.status()
    }
    t.m("setWeatherIntervalMinutes", intIn("Interval cuaca tidak valid.", WEATHER_INTERVALS)) { a ->
      runtime.weather.setIntervalMinutes((a[0] as Number).toInt())
      runtime.weather.status()
    }
    t.m("setWeatherLocationMode", oneOf("Mode lokasi tidak dikenal.", "current", "city")) { a ->
      val mode = a[0] as String
      if (mode == "current" && !host.ask(listOf(Manifest.permission.ACCESS_COARSE_LOCATION))) {
        throw IllegalStateException("Izin lokasi belum diberikan.")
      }
      runtime.weather.setLocationMode(mode)
      runtime.weather.status()
    }
    t.m("setWeatherCity", text("Nama kota harus 2 sampai 80 huruf.", 2, 80)) { a ->
      runtime.weather.setCity((a[0] as String).trim()).toJs()
    }
    t.m("refreshWeather") { runtime.weather.refreshNow() }
  }

  /** Notifikasi dan panggilan. */
  private fun addNotifications(t: MutableMap<String, Rpc>) {
    t.m("getNotificationAccess") { runtime.notificationAccessStatus() }
    t.m("openNotificationAccess") { runtime.openNotificationAccess(); null }
    t.m("setNotificationsEnabled", BOOL) { a ->
      runtime.setNotificationsEnabled(a[0] as Boolean)
      runtime.notificationAccessStatus()
    }
    t.m("setMorningSummaryEnabled", BOOL) { a ->
      if (a[0] == true && !notificationsAllowed()) throw IllegalStateException("Izin notifikasi belum diberikan.")
      runtime.setMorningSummaryEnabled(a[0] as Boolean)
      null
    }
    t.m("getPhoneFeatures") { runtime.features.status() }
    t.m("setCallsEnabled", BOOL) { a ->
      if (a[0] == true) {
        val ok = host.ask(
          required = listOf(Manifest.permission.READ_PHONE_STATE),
          optional = listOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS),
        )
        if (!ok) throw IllegalStateException("Izin telepon belum diberikan.")
      }
      runtime.features.settings.callsEnabled = a[0] as Boolean
      runtime.features.status()
    }
    t.m("setCallControlEnabled", BOOL) { a ->
      if (a[0] == true && !host.ask(listOf(Manifest.permission.ANSWER_PHONE_CALLS))) {
        throw IllegalStateException("Izin menjawab panggilan belum diberikan.")
      }
      runtime.features.settings.callControlEnabled = a[0] as Boolean
      runtime.features.status()
    }
    t.m("setFindPhoneEnabled", BOOL) { a ->
      val on = a[0] as Boolean
      runtime.features.settings.findPhoneEnabled = on
      // Dimatikan saat sedang berbunyi: ponsel harus ikut diam.
      if (!on) runtime.features.stopFindPhone("Find my phone switched off")
      runtime.features.status()
    }
    // Cari jam dari HP: true membunyikan jam, false menghentikannya.
    t.m("findWatch", BOOL) { a ->
      requireConnected()
      runtime.features.findWatch(a[0] as Boolean)
      null
    }

    // Aplikasi yang notifikasinya diteruskan. Daftar tanpa ikon: halaman tidak bisa memuat berkas ikon, dan ikon memperlambat.
    t.m("getNotificationApps") {
      withContext(Dispatchers.IO) { NotificationApps.list(app, runtime.settings, icons = false) }
    }
    t.m("setNotificationAppEnabled", APP_CHOICE) { a ->
      runtime.settings.setAppChoice(a[0] as String, a[1] as Boolean)
      null
    }

    // Balasan cepat di jam. getQuickReplies = status dari JoyoFit ditambah isi tersimpan (replies), supaya isian bisa terisi.
    t.m("getQuickReplies") {
      runtime.p2p.quickRepliesStatus() + mapOf("replies" to (runtime.p2p.settings.quickReplies ?: emptyList()))
    }
    t.m("setQuickReplies", QUICK) { a ->
      val arr = a[0] as JSONArray
      val replies = List(arr.length()) { i -> arr.getString(i).trim() }
      mapOf("sent" to runtime.p2p.setQuickReplies(replies))
    }

    // Alarm di jam. setAlarms mengirim PERSIS isi yang dikirim (slot yang tidak ada dihapus), jadi halaman wajib membaca dulu.
    t.m("getAlarms") { runtime.features.cachedAlarms().toJs() }
    t.m("refreshAlarms") {
      requireConnected()
      runtime.features.refreshAlarms().toJs()
    }
    t.m("setAlarms", ALARMS) { a ->
      requireConnected()
      val o = a[0] as JSONObject
      val ev = o.getJSONArray("event")
      val event = List(ev.length()) { i ->
        val x = ev.getJSONObject(i)
        WatchAlarm(x.getInt("slot"), x.getBoolean("enabled"), x.getInt("hour"), x.getInt("minute"), x.getInt("repeat"), x.getString("name"))
      }
      val smart = o.optJSONObject("smart")?.let { s ->
        WatchSmartAlarm(s.getBoolean("enabled"), s.getInt("hour"), s.getInt("minute"), s.getInt("repeat"), s.getInt("aheadMinutes"))
      }
      runtime.features.setAlarms(WatchAlarms(event, smart, null)).toJs()
    }
  }

  /** Lokasi dan rute: data satelit (A-GPS), program lari, rute di jam, Healthy Living. */
  private fun addLocation(t: MutableMap<String, Rpc>) {
    // Data satelit. refreshAgps menunggu sampai selesai (bisa lama); halaman memantau getAgps sambil menunggu.
    t.m("getAgps") { runtime.ephemeris.status() }
    t.m("refreshAgps") { runtime.ephemeris.refreshNow() }
    t.m("cancelAgps") { runtime.ephemeris.cancel(); null }
    t.m("setAgpsLab", BOOL) { a ->
      runtime.ephemeris.setLabAgnss(a[0] as Boolean)
      runtime.ephemeris.status()
    }
    t.m("setAgpsPgnssLab", BOOL) { a ->
      runtime.ephemeris.setLabPgnss(a[0] as Boolean)
      runtime.ephemeris.status()
    }

    // Program lari (eksperimental, mati secara bawaan).
    t.m("getCourses") { runtime.p2p.coursesStatus() }
    t.m("setCoursesEnabled", BOOL) { a ->
      runtime.p2p.settings.experimentalCourses = a[0] as Boolean
      runtime.p2p.coursesStatus()
    }
    t.m("pushRunningCourse", COURSE) { a ->
      requireConnected()
      val o = a[0] as JSONObject
      val blocks = o.getJSONArray("blocks")
      val converted = List(blocks.length()) { i ->
        val b = blocks.getJSONObject(i)
        val steps = b.getJSONArray("steps")
        CourseBlock(
          b.getInt("repeat"),
          List(steps.length()) { j ->
            val x = steps.getJSONObject(j)
            CourseStep(
              kind = CourseStepKind.of(x.getString("kind"))!!,
              target = CourseTarget.of(x.getString("target"))!!,
              targetValue = x.getInt("targetValue"),
              intensity = CourseIntensity.of(x.getString("intensity"))!!,
              low = x.getInt("low"),
              high = x.getInt("high"),
            )
          },
        )
      }
      // Id program buatan sendiri; jam hanya butuh teks unik yang stabil (sama dengan JoyoFit).
      val id = "JF" + java.lang.Long.toString(System.currentTimeMillis(), 36).uppercase()
      runtime.p2p.pushCourse(RunningCourse(id, o.getString("name").trim(), converted))
    }

    // Rute di jam. Beda dari JoyoFit: berkas dipilih lewat pickRouteFile (GPX), lalu pushRoute hanya menerima nama.
    t.m("getRoutes") { runtime.p2p.routesStatus() + mapOf("picked" to pickedRouteInfo()) }
    t.m("pickRouteFile") {
      val file = pick("*/*")
      val parsed = RouteGpx.parse(file.bytes)
      pickedRoute = PickedRoute(file.name, parsed)
      pickedRouteInfo()
    }
    t.m("pushRoute", text("Nama rute harus 1 sampai 100 huruf.", 1, 100)) { a ->
      requireConnected()
      val picked = pickedRoute ?: throw IllegalStateException("Pilih berkas GPX dulu.")
      val name = truncateUtf8((a[0] as String).trim(), RouteFile.MAX_NAME_BYTES)
      val track = RouteTrack(
        java.util.UUID.randomUUID().toString(), name, picked.parsed.fixes, picked.parsed.hasAltitude, 0L,
      )
      runtime.p2p.pushRoute(track)
    }

    // Healthy Living.
    t.m("getHealthyLiving") { runtime.p2p.healthLivingStatus() }
    t.m("setHealthyLivingEnabled", BOOL) { a ->
      runtime.p2p.setHealthLivingEnabled(a[0] as Boolean)
      runtime.p2p.healthLivingStatus()
    }
    t.m("pushHealthyLiving") { mapOf("sent" to runtime.p2p.pushHealthLiving()) }
  }

  /** Sensor kesehatan, stres, dan HRV. */
  private fun addHealth(t: MutableMap<String, Rpc>) {
    t.m("getHealthSensors") { runtime.healthSensorsStatus() }
    t.m("setHealthSensor", SENSOR) { a ->
      val cur = runtime.settings.healthSensors
      val key = a[0] as String
      val next = when (key) {
        "automaticHeartRate" -> cur.copy(automaticHeartRate = a[1] as Boolean)
        "automaticSpo2" -> cur.copy(automaticSpo2 = a[1] as Boolean)
        "truSleep" -> cur.copy(truSleep = a[1] as Boolean)
        "highHeartRateAlert" -> cur.copy(highHeartRateAlert = (a[1] as Number).toInt())
        "lowHeartRateAlert" -> cur.copy(lowHeartRateAlert = (a[1] as Number).toInt())
        else -> throw IllegalArgumentException("Pengaturan sensor tidak dikenal.")
      }
      runtime.setHealthSensors(next)
      runtime.healthSensorsStatus()
    }
    t.m("getStressStatus") { runtime.stress.status() }
    t.m("setAutomaticStress", BOOL) { a -> runtime.stress.setEnabled(a[0] as Boolean) }
    t.m("measureHrv") { runtime.hrv.measure().toJs() }
    t.m("cancelHrvMeasurement") { runtime.hrv.cancel(); null }
  }

  /** Layar lain di aplikasi. */
  private fun addScreens(t: MutableMap<String, Rpc>) {
    t.m("openScreen", SCREEN) { a ->
      if (!host.open(a[0] as String)) throw IllegalStateException("Layar ini belum tersedia di aplikasi ini.")
      null
    }
  }

  // ---- bantuan untuk metode ----

  private fun requireConnected() {
    val link = HuaweiWatch.get(app).state.value.link
    if (link != "connected" && link != "syncing") throw IllegalStateException("Jam belum tersambung. Sambungkan lewat layar Koneksi.")
  }

  /** Profil tersimpan; kalau belum ada, angka contoh untuk isian (configured = false). */
  private fun profileJs(): Map<String, Any?> {
    val p = runtime.settings.profile
    return if (p != null) {
      p.toJs() + ("configured" to true)
    } else {
      mapOf(
        "configured" to false, "heightCm" to 170, "weightKg" to 65.0, "birthYear" to 1990,
        "sex" to "unknown", "dailyStepGoal" to 8000,
      )
    }
  }

  /** Status zona (configured, via) ditambah isi zona: yang tersimpan, atau bawaan untuk umur di profil (metode persen detak maksimum). */
  private fun zonesJs(): Map<String, Any?> {
    val saved = runtime.settings.heartRateZones
    val maxHr = (HeartRateZonesCalc.MAXIMUM_HEART_RATE - HeartRateZonesSync.age(runtime.settings.profile)).coerceIn(100, 219)
    val z = HeartRateZonesCalc.zones(HeartRateZonesCalc.Method.MHR, maxHr)
    val shown = saved ?: HeartRateZonesCalc.UserZones(
      HeartRateZonesCalc.Method.MHR, maxHr, HeartRateZonesCalc.DEFAULT_REST_HEART_RATE,
      listOf(z.zone1, z.zone2, z.zone3, z.zone4, z.zone5), true, maxHr,
    )
    return runtime.heartRateZonesStatus() + mapOf(
      "method" to if (shown.method == HeartRateZonesCalc.Method.HRR) "hrr" else "max",
      "maxHr" to shown.maxHr,
      "restingHr" to shown.restingHr,
      "zones" to shown.zones,
      "warningEnabled" to shown.warningEnabled,
      "warningLimit" to shown.warningLimit,
    )
  }

  private suspend fun notificationsAllowed(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || host.ask(listOf(Manifest.permission.POST_NOTIFICATIONS))

  private suspend fun bluetoothAllowed(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || host.ask(listOf(Manifest.permission.BLUETOOTH_CONNECT))

  /** Rute yang terakhir dipilih lewat pickRouteFile; hanya di memori, hilang saat aplikasi ditutup. */
  private class PickedRoute(val fileName: String, val parsed: RouteGpx.Parsed)

  @Volatile
  private var pickedRoute: PickedRoute? = null

  private fun pickedRouteInfo(): Map<String, Any?>? = pickedRoute?.let { p ->
    mapOf(
      "fileName" to p.fileName,
      "name" to (p.parsed.name ?: p.fileName.substringBeforeLast('.')),
      "points" to p.parsed.fixes.size,
      "distanceKm" to p.parsed.distanceM / 1000.0,
      "hasAltitude" to p.parsed.hasAltitude,
    )
  }

  /** Potong [text] paling banyak [maxBytes] byte UTF-8, tidak di tengah huruf. */
  private fun truncateUtf8(text: String, maxBytes: Int): String {
    var bytes = 0
    val out = StringBuilder()
    var i = 0
    while (i < text.length) {
      val cp = text.codePointAt(i)
      val size = when { cp < 0x80 -> 1; cp < 0x800 -> 2; cp < 0x10000 -> 3; else -> 4 }
      if (bytes + size > maxBytes) break
      bytes += size
      out.appendCodePoint(cp)
      i += Character.charCount(cp)
    }
    return out.toString().trim()
  }

  /** Minta pengguna memilih berkas; dipakai pickRouteFile (langkah 6) dan nanti wajah jam serta impor Huawei (langkah 7 dan 8). */
  private suspend fun pick(mime: String): PickedFile =
    host.pickFile(mime) ?: throw IllegalStateException("Pemilihan berkas dibatalkan.")

  private companion object {
    val WEATHER_INTERVALS = setOf(30, 60, 120, 240)
    val SYNC_INTERVALS = setOf(15, 30, 60, 120, 240)
    val SENSOR_BOOLEANS = setOf("automaticHeartRate", "automaticSpo2", "truSleep")
    val SENSOR_ALERTS = setOf("highHeartRateAlert", "lowHeartRateAlert")

    val ALLOWED_ROUTES = setOf(
      "/", "/workouts", "/plan", "/social", "/settings", "/watch", "/watch/alarms", "/watch/faces",
      "/settings/koneksi", "/settings/import-huawei", "/settings/sources", "/settings/notification-apps",
    )

    /** /pengaturan, /pengaturan/<kelompok>, /settings/<kelompok>. Kelompok yang tidak dikenal ditolak oleh aplikasi. */
    val PENGATURAN = Regex("^/(?:pengaturan|settings)(?:/[a-z0-9-]{1,30})?$")

    // Aturan argumen yang dipakai ulang.
    val NONE = Rule { a -> if (a.isEmpty()) null else "Metode ini tidak menerima argumen." }
    val BOOL = Rule { a -> if (a.firstOrNull() is Boolean) null else "Nilai harus benar atau salah." }

    fun oneOf(msg: String, vararg values: String) = Rule { a ->
      val x = a.firstOrNull()
      if (values.any { it == x }) null else msg
    }

    fun intIn(msg: String, set: Set<Int>) = Rule { a -> if (a.getOrNull(0).isIntIn(set)) null else msg }

    fun text(msg: String, min: Int, max: Int) = Rule { a ->
      if (a.size != 1) {
        "Metode ini menerima tepat satu argumen."
      } else {
        val q = (a[0] as? String)?.trim().orEmpty()
        if (q.length in min..max) null else msg
      }
    }

    val SENSOR = Rule { a ->
      val k = a.getOrNull(0)
      val v = a.getOrNull(1)
      when {
        k !is String -> "Nama pengaturan tidak valid."
        k in SENSOR_BOOLEANS -> if (v is Boolean) null else "Nilai harus benar atau salah."
        k in SENSOR_ALERTS ->
          if (v is Number && v.toDouble() == v.toInt().toDouble() && v.toInt() in 0..240) null else "Batas detak harus angka 0 sampai 240."
        else -> "Pengaturan sensor tidak dikenal."
      }
    }

    val SCREEN = Rule { a ->
      val r = a.getOrNull(0)
      if (r is String && (r in ALLOWED_ROUTES || PENGATURAN.matches(r))) null else "Layar tidak diizinkan."
    }

    val LANGS = setOf("id", "en")

    val LANG = Rule { a ->
      val v = a.getOrNull(0)
      if (a.size == 1 && (v == null || (v is String && LANGS.contains(v)))) null else "Bahasa tidak dikenal."
    }

    /** Bilangan bulat dari bidang JSON, atau null kalau tidak ada atau bukan bilangan bulat. */
    fun JSONObject.whole(name: String): Int? {
      val d = optDouble(name, Double.NaN)
      return if (!d.isNaN() && d == Math.floor(d) && Math.abs(d) < 1e9) d.toInt() else null
    }

    fun JSONObject.within(name: String, range: IntRange): Boolean {
      val v = whole(name) ?: return false
      return v in range
    }

    val SEXES = setOf<Any?>("male", "female", "unknown")

    val PROFILE = Rule { a ->
      val o = a.singleOrNull() as? JSONObject
      if (o == null) {
        "Profil tidak valid."
      } else {
        val w = o.optDouble("weightKg", Double.NaN)
        when {
          !o.within("heightCm", 100..250) -> "Tinggi badan harus 100 sampai 250 cm."
          w.isNaN() || w < 20.0 || w > 300.0 -> "Berat badan harus 20 sampai 300 kg."
          !o.within("birthYear", 1900..java.time.Year.now().value) -> "Tahun lahir harus 1900 sampai tahun ini."
          o.opt("sex") !in SEXES -> "Jenis kelamin harus male, female, atau unknown."
          !o.within("dailyStepGoal", 1000..100_000) -> "Target langkah harus 1000 sampai 100000."
          else -> null
        }
      }
    }
    /** Lima batas bawah zona sebagai bilangan bulat; null kalau bukan lima bilangan bulat. */
    fun zonesList(o: JSONObject): List<Int>? {
      val arr = o.optJSONArray("zones") ?: return null
      if (arr.length() != 5) return null
      val out = ArrayList<Int>(5)
      for (i in 0 until 5) {
        val d = arr.optDouble(i, Double.NaN)
        if (d.isNaN() || d != Math.floor(d) || Math.abs(d) >= 1e9) return null
        out.add(d.toInt())
      }
      return out
    }

    /** Argumen tunggal: null = kembali ke bawaan, atau objek zona. Aturan sama dengan HeartRateZonesCalc.UserZones.problem(). */
    val ZONES = Rule { a ->
      val o = a.getOrNull(0)
      when {
        a.size != 1 -> "Metode ini menerima tepat satu argumen."
        o == null -> null
        o !is JSONObject -> "Zona tidak valid."
        else -> {
          val z = zonesList(o)
          val max = o.whole("maxHr")
          val rest = o.whole("restingHr")
          when {
            o.optString("method") != "max" && o.optString("method") != "hrr" -> "Metode zona harus max atau hrr."
            !o.within("maxHr", 100..219) -> "Detak maksimum harus 100 sampai 219 bpm."
            !o.within("restingHr", 30..110) -> "Detak istirahat harus 30 sampai 110 bpm."
            rest != null && max != null && rest >= max -> "Detak istirahat harus di bawah detak maksimum."
            z == null -> "Isi kelima zona dengan angka bulat."
            z[0] < 30 -> "Zona 1 mulai dari 30 bpm atau lebih."
            z.zipWithNext().any { (x, y) -> x >= y } -> "Batas zona harus naik dari zona 1 sampai 5."
            max != null && z[4] >= max -> "Zona 5 harus mulai di bawah detak maksimum."
            o.opt("warningEnabled") !is Boolean -> "Alarm batas harus benar atau salah."
            !o.within("warningLimit", 100..219) -> "Batas alarm harus 100 sampai 219 bpm."
            else -> null
          }
        }
      }
    }

    val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    /** (nama paket, nyala). */
    val APP_CHOICE = Rule { a ->
      val p = a.getOrNull(0)
      when {
        a.size != 2 -> "Metode ini menerima dua argumen."
        p !is String || p.length > 255 || !PACKAGE_NAME.matches(p) -> "Nama paket tidak valid."
        a[1] !is Boolean -> "Nilai harus benar atau salah."
        else -> null
      }
    }

    /** Satu argumen: daftar 1 sampai SLOTS balasan, tiap balasan satu baris, paling banyak MAX_LENGTH huruf. Sama dengan QuickRepliesPacket.problem(). */
    val QUICK = Rule { a ->
      val arr = a.singleOrNull() as? JSONArray
      when {
        arr == null -> "Daftar balasan tidak valid."
        arr.length() !in 1..QuickRepliesPacket.SLOTS -> "Isi 1 sampai ${QuickRepliesPacket.SLOTS} balasan."
        else -> {
          var bad: String? = null
          for (i in 0 until arr.length()) {
            val s = (arr.opt(i) as? String)?.trim()
            if (s.isNullOrEmpty()) { bad = "Balasan tidak boleh kosong."; break }
            if ('\n' in s || '\r' in s) { bad = "Balasan harus satu baris."; break }
            if (s.codePointCount(0, s.length) > QuickRepliesPacket.MAX_LENGTH) { bad = "Balasan paling panjang ${QuickRepliesPacket.MAX_LENGTH} huruf."; break }
          }
          bad
        }
      }
    }

    /** Aturan sama dengan AlarmSync.validate(); pesannya untuk pengguna. Hari: Senin = 1 sampai Minggu = 64. */
    fun alarmsProblem(a: List<Any?>): String? {
      val o = a.singleOrNull() as? JSONObject ?: return "Alarm tidak valid."
      val ev = o.optJSONArray("event") ?: return "Daftar alarm tidak valid."
      if (ev.length() > EVENT_ALARM_SLOTS) return "Jam hanya punya $EVENT_ALARM_SLOTS slot alarm."
      val seen = HashSet<Int>()
      for (i in 0 until ev.length()) {
        val x = ev.optJSONObject(i) ?: return "Alarm tidak valid."
        val slot = x.whole("slot")
        when {
          slot == null || slot !in 1..EVENT_ALARM_SLOTS -> return "Slot alarm harus 1 sampai $EVENT_ALARM_SLOTS."
          !seen.add(slot) -> return "Slot alarm $slot dipakai dua kali."
          x.opt("enabled") !is Boolean -> return "Status alarm harus benar atau salah."
          !x.within("hour", 0..23) -> return "Jam alarm harus 0 sampai 23."
          !x.within("minute", 0..59) -> return "Menit alarm harus 0 sampai 59."
          !x.within("repeat", 0..ALARM_REPEAT_MAX) -> return "Hari ulang alarm tidak valid."
          x.opt("name") !is String || x.getString("name").length > ALARM_NAME_MAX -> return "Nama alarm paling panjang $ALARM_NAME_MAX huruf."
        }
      }
      val smart = o.optJSONObject("smart")
      if (smart != null) {
        when {
          smart.opt("enabled") !is Boolean -> return "Status alarm pintar harus benar atau salah."
          !smart.within("hour", 0..23) || !smart.within("minute", 0..59) -> return "Waktu alarm pintar tidak valid."
          !smart.within("repeat", 0..ALARM_REPEAT_MAX) -> return "Hari ulang alarm pintar tidak valid."
          !smart.within("aheadMinutes", 0..127) -> return "Jendela alarm pintar harus 0 sampai 127 menit."
        }
      } else if (o.has("smart") && !o.isNull("smart")) {
        return "Alarm pintar tidak valid."
      }
      return null
    }

    val ALARMS = Rule { a -> alarmsProblem(a) }

    private val COURSE_KINDS = setOf<Any?>("warmup", "run", "walk", "rest", "cooldown")
    private val COURSE_TARGETS = setOf<Any?>("time", "distance", "none")
    private val COURSE_INTENSITIES = setOf<Any?>("none", "pace", "hrPercent", "cadence")

    /** Aturan sama dengan CourseProtobuf.validate(); pesannya untuk pengguna. */
    fun courseProblem(a: List<Any?>): String? {
      val o = a.singleOrNull() as? JSONObject ?: return "Program tidak valid."
      val name = (o.opt("name") as? String)?.trim().orEmpty()
      if (name.isEmpty()) return "Beri nama pada program."
      if (name.toByteArray(Charsets.UTF_8).size > 64) return "Nama program terlalu panjang (paling banyak 64 byte)."
      val blocks = o.optJSONArray("blocks") ?: return "Program tidak valid."
      if (blocks.length() == 0) return "Tambahkan minimal satu langkah."
      var count = 0
      for (i in 0 until blocks.length()) {
        val b = blocks.optJSONObject(i) ?: return "Program tidak valid."
        val label = "Grup ${i + 1}"
        if (!b.within("repeat", 1..99)) return "$label: grup diulang 1 sampai 99 kali."
        val steps = b.optJSONArray("steps") ?: return "Program tidak valid."
        if (steps.length() == 0) return "$label: tambahkan langkah ke grup ini."
        count += steps.length()
        if (count > 100) return "Program paling banyak punya 100 langkah."
        for (j in 0 until steps.length()) {
          val x = steps.optJSONObject(j) ?: return "Program tidak valid."
          val where = "$label, langkah ${j + 1}"
          if (x.opt("kind") !in COURSE_KINDS) return "$where: jenis langkah tidak dikenal."
          val target = x.opt("target")
          if (target !in COURSE_TARGETS) return "$where: jenis target tidak dikenal."
          if (x.opt("intensity") !in COURSE_INTENSITIES) return "$where: target usaha tidak dikenal."
          if (x.whole("targetValue") == null || x.whole("low") == null || x.whole("high") == null) return "$where: isi angka bulat."
          when (target) {
            "time" -> if (!x.within("targetValue", 1..86_400)) return "$where: target waktu 1 detik sampai 24 jam."
            "distance" -> if (!x.within("targetValue", 1..100_000)) return "$where: target jarak 1 m sampai 100 km."
          }
          val lo = x.getInt("low")
          val hi = x.getInt("high")
          when (x.opt("intensity")) {
            "pace" -> when {
              !x.within("low", 60..900) || !x.within("high", 60..900) -> return "$where: batas kecepatan 1:00 sampai 15:00 per km."
              lo > hi -> return "$where: kecepatan yang lebih cepat harus berupa waktu yang lebih kecil."
            }
            "hrPercent" -> when {
              !x.within("low", 20..100) || !x.within("high", 20..100) -> return "$where: batas detak jantung 20 sampai 100 % dari maksimum."
              lo > hi -> return "$where: detak jantung yang lebih rendah harus di depan."
            }
            "cadence" -> when {
              !x.within("low", 100..300) || !x.within("high", 100..300) -> return "$where: batas kadensi 100 sampai 300 langkah per menit."
              lo > hi -> return "$where: kadensi yang lebih rendah harus di depan."
            }
          }
        }
      }
      return null
    }

    val COURSE = Rule { a -> courseProblem(a) }

    /** Daftar waktu mulai latihan (detik atau ms sesuai index.html), 1 sampai 200 angka. */
    val REFETCH = Rule { a ->
      val arr = a.singleOrNull() as? JSONArray
      if (arr == null || arr.length() !in 1..200) {
        "Daftar latihan harus berisi 1 sampai 200 waktu mulai."
      } else if ((0 until arr.length()).any { arr.opt(it) !is Number }) {
        "Waktu mulai latihan harus angka."
      } else {
        null
      }
    }

    fun Any?.isIntIn(set: Set<Int>): Boolean =
      this is Number && toDouble() == toInt().toDouble() && toInt() in set

    fun jsonList(arr: JSONArray?): List<Any?> =
      if (arr == null) emptyList() else List(arr.length()) { i -> arr.get(i).let { if (it == JSONObject.NULL) null else it } }

    /** Map/List/angka/teks Kotlin menjadi nilai JSON; angka tak terhingga menjadi null supaya JSONObject tidak melempar. */
    fun toJson(v: Any?): Any? = when (v) {
      null -> null
      is String, is Boolean, is Int, is Long -> v
      is Double -> if (v.isNaN() || v.isInfinite()) null else v
      is Float -> if (v.isNaN() || v.isInfinite()) null else v.toDouble()
      is Number -> v.toDouble()
      is Map<*, *> -> JSONObject().also { o -> v.forEach { (k, x) -> o.put(k.toString(), toJson(x) ?: JSONObject.NULL) } }
      is Iterable<*> -> JSONArray().also { a -> v.forEach { a.put(toJson(it) ?: JSONObject.NULL) } }
      is Array<*> -> JSONArray().also { a -> v.forEach { a.put(toJson(it) ?: JSONObject.NULL) } }
      else -> v.toString()
    }
  }
}
