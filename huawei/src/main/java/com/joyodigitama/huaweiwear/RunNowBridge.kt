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
    addWeather(t)
    addNotifications(t)
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
    override fun syncComplete(summary: SyncSummary) = Unit
    override fun log(direction: LogDirection, serviceId: Int?, commandId: Int?, hex: String?, message: String) = Unit

    override fun hrvProgress(secondsLeft: Int, beats: Int) {
      host.eval("window.__hrvTick&&window.__hrvTick($secondsLeft);")
    }

    override fun heartRate(bpm: Int, tsMs: Long) {
      host.eval("window.nativeLiveHr&&window.nativeLiveHr($bpm,$tsMs);")
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
      val link = HuaweiWatch.get(app).state.value.link
      if (link != "connected" && link != "syncing") throw IllegalStateException("Jam belum tersambung. Sambungkan lewat layar Koneksi.")
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
    t.m("setFindPhoneEnabled", BOOL) { a ->
      val on = a[0] as Boolean
      runtime.features.settings.findPhoneEnabled = on
      // Dimatikan saat sedang berbunyi: ponsel harus ikut diam.
      if (!on) runtime.features.stopFindPhone("Find my phone switched off")
      runtime.features.status()
    }
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

  private suspend fun notificationsAllowed(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || host.ask(listOf(Manifest.permission.POST_NOTIFICATIONS))

  private suspend fun bluetoothAllowed(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || host.ask(listOf(Manifest.permission.BLUETOOTH_CONNECT))

  /** Minta pengguna memilih berkas; untuk metode langkah 6 sampai 8 (kursus, rute, wajah jam, impor Huawei). */
  @Suppress("unused")
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
