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

/** Hal yang hanya bisa dilakukan Activity. Dipasang oleh aplikasi. */
interface RunNowHost {
  /** Minta izin. True kalau semua [required] diberikan; [optional] hanya diminta. Boleh dipanggil dari thread mana pun. */
  suspend fun ask(required: List<String>, optional: List<String> = emptyList()): Boolean

  /** Buka layar di aplikasi. False kalau layar itu tidak ada. Boleh dipanggil dari thread mana pun. */
  fun open(route: String): Boolean

  /** Jalankan JavaScript di halaman. Implementasi yang memastikan jalan di thread utama. */
  fun eval(js: String)
}

/**
 * Pintu panggilan dari index.html ke Huawei core. Didaftarkan ke WebView dengan nama `ReactNativeWebView`,
 * jadi `rn(...)` di index.html jalan tanpa diubah: halaman memanggil `postMessage({type:"rpc", id, method, args})`
 * dan jawabannya dikirim balik lewat `window.__rn(id, ok, nilai)`.
 *
 * Hanya metode di [ALLOWED] yang dijalankan, dengan pemeriksaan argumen yang sama seperti rpcRules.ts di JoyoFit.
 */
class RunNowBridge(context: Context, private val host: RunNowHost) {
  private val app = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val runtime: WearRuntime get() = WearRuntime.get(app)

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

  // ---- metode ----

  private suspend fun run(method: String, args: List<Any?>): Any? {
    check(method, args)?.let { throw IllegalArgumentException(it) }
    val rt = runtime
    return when (method) {
      // Sinkron dan latar belakang
      "syncNow" -> {
        val link = HuaweiWatch.get(app).state.value.link
        if (link != "connected" && link != "syncing") throw IllegalStateException("Jam belum tersambung. Sambungkan lewat layar Koneksi.")
        rt.link.syncActivity(fullHistory = false, sinceDays = null)
        null
      }
      "getBackgroundStatus" -> rt.backgroundStatus()
      "getBackgroundRestrictions" -> rt.backgroundRestrictions()
      "setBackgroundEnabled" -> {
        if (args[0] != true) {
          rt.setBackground(null)
        } else {
          val address = rt.settings.watchAddress ?: throw IllegalStateException("Hubungkan jam dulu lewat layar Koneksi.")
          if (!notificationsAllowed()) throw IllegalStateException("Izin notifikasi belum diberikan.")
          if (!bluetoothAllowed()) throw IllegalStateException("Izin Bluetooth belum diberikan.")
          rt.setBackground(address)
        }
        rt.backgroundStatus()
      }
      "setSyncIntervalMinutes" -> {
        rt.setSyncInterval((args[0] as Number).toInt())
        rt.backgroundStatus()
      }
      "openBatteryExemption" -> { rt.openBatteryExemption(); null }
      "openAutoStartSettings" -> { rt.openAutoStartSettings(); null }

      // Cuaca
      "getWeatherStatus" -> rt.weather.status()
      "setWeatherEnabled" -> { rt.weather.setEnabled(args[0] as Boolean); rt.weather.status() }
      "setWeatherUnit" -> { rt.weather.setUnit(args[0] as String); rt.weather.status() }
      "setWeatherIntervalMinutes" -> { rt.weather.setIntervalMinutes((args[0] as Number).toInt()); rt.weather.status() }
      "setWeatherLocationMode" -> {
        val mode = args[0] as String
        if (mode == "current" && !host.ask(listOf(Manifest.permission.ACCESS_COARSE_LOCATION))) {
          throw IllegalStateException("Izin lokasi belum diberikan.")
        }
        rt.weather.setLocationMode(mode)
        rt.weather.status()
      }
      "setWeatherCity" -> rt.weather.setCity((args[0] as String).trim()).toJs()
      "refreshWeather" -> rt.weather.refreshNow()

      // Notifikasi dan panggilan
      "getNotificationAccess" -> rt.notificationAccessStatus()
      "openNotificationAccess" -> { rt.openNotificationAccess(); null }
      "setNotificationsEnabled" -> { rt.setNotificationsEnabled(args[0] as Boolean); rt.notificationAccessStatus() }
      "setMorningSummaryEnabled" -> {
        if (args[0] == true && !notificationsAllowed()) throw IllegalStateException("Izin notifikasi belum diberikan.")
        rt.setMorningSummaryEnabled(args[0] as Boolean)
        null
      }
      "getPhoneFeatures" -> rt.features.status()
      "setCallsEnabled" -> {
        if (args[0] == true) {
          val ok = host.ask(
            required = listOf(Manifest.permission.READ_PHONE_STATE),
            optional = listOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS),
          )
          if (!ok) throw IllegalStateException("Izin telepon belum diberikan.")
        }
        rt.features.settings.callsEnabled = args[0] as Boolean
        rt.features.status()
      }
      "setFindPhoneEnabled" -> {
        val on = args[0] as Boolean
        rt.features.settings.findPhoneEnabled = on
        // Dimatikan saat sedang berbunyi: ponsel harus ikut diam.
        if (!on) rt.features.stopFindPhone("Find my phone switched off")
        rt.features.status()
      }

      // Sensor kesehatan dan stres
      "getHealthSensors" -> rt.healthSensorsStatus()
      "setHealthSensor" -> {
        val cur = rt.settings.healthSensors
        val key = args[0] as String
        val next = when (key) {
          "automaticHeartRate" -> cur.copy(automaticHeartRate = args[1] as Boolean)
          "automaticSpo2" -> cur.copy(automaticSpo2 = args[1] as Boolean)
          "truSleep" -> cur.copy(truSleep = args[1] as Boolean)
          "highHeartRateAlert" -> cur.copy(highHeartRateAlert = (args[1] as Number).toInt())
          "lowHeartRateAlert" -> cur.copy(lowHeartRateAlert = (args[1] as Number).toInt())
          else -> throw IllegalArgumentException("Pengaturan sensor tidak dikenal.")
        }
        rt.setHealthSensors(next)
        rt.healthSensorsStatus()
      }
      "getStressStatus" -> rt.stress.status()
      "setAutomaticStress" -> rt.stress.setEnabled(args[0] as Boolean)

      // HRV
      "measureHrv" -> rt.hrv.measure().toJs()
      "cancelHrvMeasurement" -> { rt.hrv.cancel(); null }

      // Layar lain di aplikasi
      "openScreen" -> {
        if (!host.open(args[0] as String)) throw IllegalStateException("Layar ini belum tersedia di aplikasi ini.")
        null
      }

      else -> throw IllegalArgumentException("Metode tidak diizinkan: $method")
    }
  }

  private suspend fun notificationsAllowed(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || host.ask(listOf(Manifest.permission.POST_NOTIFICATIONS))

  private suspend fun bluetoothAllowed(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || host.ask(listOf(Manifest.permission.BLUETOOTH_CONNECT))

  // ---- aturan panggilan (sama dengan rpcRules.ts) ----

  private fun check(method: String, args: List<Any?>): String? {
    if (method !in ALLOWED) return "Metode tidak diizinkan: $method"
    if (method in NO_ARGS) return if (args.isEmpty()) null else "Metode ini tidak menerima argumen."
    if (method in BOOL_ARG) return if (args.firstOrNull() is Boolean) null else "Nilai harus benar atau salah."
    val a0 = args.getOrNull(0)
    val a1 = args.getOrNull(1)
    return when (method) {
      "setWeatherUnit" -> if (a0 == "celsius" || a0 == "fahrenheit") null else "Satuan suhu tidak dikenal."
      "setWeatherIntervalMinutes" -> if (a0.isIntIn(WEATHER_INTERVALS)) null else "Interval cuaca tidak valid."
      "setSyncIntervalMinutes" -> if (a0.isIntIn(SYNC_INTERVALS)) null else "Interval sinkron tidak valid."
      "setWeatherLocationMode" -> if (a0 == "current" || a0 == "city") null else "Mode lokasi tidak dikenal."
      "setWeatherCity" -> {
        if (args.size != 1) return "Metode ini menerima tepat satu argumen."
        val q = (a0 as? String)?.trim().orEmpty()
        if (q.length in 2..80) null else "Nama kota harus 2 sampai 80 huruf."
      }
      "setHealthSensor" -> when {
        a0 !is String -> "Nama pengaturan tidak valid."
        a0 in SENSOR_BOOLEANS -> if (a1 is Boolean) null else "Nilai harus benar atau salah."
        a0 in SENSOR_ALERTS -> if (a1 is Number && a1.toDouble() == a1.toInt().toDouble() && a1.toInt() in 0..240) null else "Batas detak harus angka 0 sampai 240."
        else -> "Pengaturan sensor tidak dikenal."
      }
      "openScreen" -> if (a0 is String && a0 in ALLOWED_ROUTES) null else "Layar tidak diizinkan."
      else -> "Metode tidak diizinkan: $method"
    }
  }

  private fun Any?.isIntIn(set: Set<Int>): Boolean =
    this is Number && toDouble() == toInt().toDouble() && toInt() in set

  private companion object {
    val WEATHER_INTERVALS = setOf(30, 60, 120, 240)
    val SYNC_INTERVALS = setOf(15, 30, 60, 120, 240)
    val SENSOR_BOOLEANS = setOf("automaticHeartRate", "automaticSpo2", "truSleep")
    val SENSOR_ALERTS = setOf("highHeartRateAlert", "lowHeartRateAlert")

    val NO_ARGS = setOf(
      "syncNow", "getBackgroundStatus", "getBackgroundRestrictions", "openBatteryExemption", "openAutoStartSettings",
      "getWeatherStatus", "refreshWeather", "getNotificationAccess", "openNotificationAccess", "getPhoneFeatures",
      "getHealthSensors", "getStressStatus", "measureHrv", "cancelHrvMeasurement",
    )
    val BOOL_ARG = setOf(
      "setBackgroundEnabled", "setWeatherEnabled", "setNotificationsEnabled", "setMorningSummaryEnabled",
      "setCallsEnabled", "setFindPhoneEnabled", "setAutomaticStress",
    )
    val ALLOWED = NO_ARGS + BOOL_ARG + setOf(
      "setSyncIntervalMinutes", "setWeatherUnit", "setWeatherIntervalMinutes", "setWeatherLocationMode",
      "setWeatherCity", "setHealthSensor", "openScreen",
    )
    val ALLOWED_ROUTES = setOf(
      "/", "/workouts", "/plan", "/social", "/settings", "/watch", "/watch/alarms", "/watch/faces",
      "/settings/koneksi", "/settings/import-huawei", "/settings/sources", "/settings/notification-apps",
    )

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
