package com.joyodigitama.huaweiwear

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Satu jam yang ditemukan (sudah dipasangkan di Android, diingat aplikasi, atau hasil scan). */
data class WatchDevice(
  val address: String,
  val name: String,
  val model: String?,
  /** "tested", "experimental", "unsupported" atau "unknown". */
  val support: String,
  val reason: String?,
  /** Android mencatat jam ini sebagai terpasang; null bila tidak ditanyakan (hasil scan). */
  val paired: Boolean?,
  /** Aplikasi pernah tersambung ke jam ini dan masih menyimpan alamatnya. */
  val remembered: Boolean,
)

/** Hasil tarik HRV manual. [stored] = jumlah malam yang disimpan, [result] = keterangan dari jam. */
data class WatchHrvResult(val result: String, val stored: Int, val records: Int?)

/** Status koneksi. [link]: disconnected, connecting, authenticating, initializing, connected, syncing. */
data class WatchState(
  val link: String = "disconnected",
  val battery: Int? = null,
  val name: String? = null,
  val lastSyncSec: Long? = null,
  val error: String? = null,
)

/** Ringkasan satu sinkron; waktu dalam detik unix. */
data class WatchSyncResult(
  val fromSec: Long,
  val toSec: Long,
  val samples: Int,
  val sleep: Int,
  val steps: Int,
  val calories: Int,
  val distance: Int,
  val workouts: Int,
)

/** Kegagalan dari lapisan jam; [code] salah satu ERR_* (mis. [HuaweiWatch.ERR_BIND_CONFIRMATION]). */
class WatchException(val code: String, message: String) : Exception(message)

/**
 * Pintu masuk publik ke modul jam untuk aplikasi (menggantikan jembatan Expo/JS JoyoFit).
 * Semua kelas lain di modul ini bersifat `internal`.
 */
class HuaweiWatch private constructor(context: Context) {
  private val appContext = context.applicationContext
  private val runtime = WearRuntime.get(appContext)

  private val _state = MutableStateFlow(
    WatchState(
      link = runtime.link.currentState(),
      battery = runtime.status.value.battery,
      name = runtime.settings.watchName,
      lastSyncSec = runtime.status.value.lastSyncSec,
    ),
  )
  private val _found = MutableStateFlow<List<WatchDevice>>(emptyList())
  private val _scanning = MutableStateFlow(false)
  private val _log = MutableStateFlow<List<String>>(emptyList())

  val state: StateFlow<WatchState> = _state.asStateFlow()
  val found: StateFlow<List<WatchDevice>> = _found.asStateFlow()
  val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

  /** 200 baris log terakhir (arah, pesan); berguna untuk melaporkan masalah dengan jam yang masih eksperimental. */
  val log: StateFlow<List<String>> = _log.asStateFlow()

  private val listener = object : WearEvents {
    override fun state(state: LinkState, error: String?) {
      _state.update { it.copy(link = state.wire, error = error) }
    }

    override fun battery(level: Int) {
      _state.update { it.copy(battery = level) }
    }

    override fun syncComplete(summary: SyncSummary) {
      _state.update { it.copy(lastSyncSec = summary.to) }
    }

    override fun log(direction: LogDirection, serviceId: Int?, commandId: Int?, hex: String?, message: String) {
      addLog("${direction.wire} $message")
    }

    override fun deviceDiscovered(device: DiscoveredDevice) {
      val d = device.toJs().toWatchDevice()
      _found.update { list -> list.filter { it.address != d.address } + d }
    }

    override fun scanState(scanning: Boolean, error: String?) {
      _scanning.value = scanning
      if (error != null) addLog("error scan: $error")
    }
  }

  init {
    runtime.addListener(listener)
    runtime.ensureServiceRunning()
  }

  private fun addLog(line: String) {
    _log.update { (it + line).takeLast(200) }
  }

  private inline fun <T> guard(block: () -> T): T =
    try {
      block()
    } catch (e: WearException) {
      throw WatchException(e.errorCode, e.message ?: e.errorCode)
    }

  /** Jam yang sudah dipasangkan di Android ditambah yang diingat aplikasi. Butuh izin Bluetooth. */
  fun bonded(): List<WatchDevice> = guard { runtime.link.bondedDevices().map { it.toWatchDevice() } }

  fun startScan() {
    _found.value = emptyList()
    guard { runtime.link.startScan() }
  }

  fun stopScan() {
    runtime.link.stopScan()
  }

  /** True bila sambungan pertama ke [address] perlu konfirmasi (ikatan pertama menggantikan pasangan HP lama jam). */
  fun needsBind(address: String): Boolean = runtime.link.needsBind(address)

  /**
   * Menyambung ke [address]. Bila jam belum pernah diikat dengan HP ini, panggilan pertama gagal dengan
   * [ERR_BIND_CONFIRMATION]; tampilkan peringatan, lalu ulangi dengan [confirmBind] = true.
   */
  suspend fun connect(address: String, confirmBind: Boolean = false) {
    guard {
      if (confirmBind) runtime.confirmBind(address)
      runtime.confirmPairing(address)
      val info = runtime.connectFromApp(address)
      _state.update { it.copy(name = info["name"] as? String ?: it.name) }
    }
  }

  fun disconnect() {
    runtime.disconnectFromApp()
  }

  suspend fun refreshBattery(): Int = guard { runtime.link.refreshBattery() }

  /** Menarik data dari jam ke database `filesDir/SQLite/joyofit.db`. [full] = seluruh riwayat yang disimpan jam. */
  suspend fun sync(full: Boolean = false, days: Int? = null): WatchSyncResult = guard {
    val s = runtime.link.syncActivity(fullHistory = full, sinceDays = days)
    WatchSyncResult(s.from, s.to, s.samples, s.sleep, s.totalSteps, s.totalCalories, s.totalDistance, s.workouts)
  }

  /** Tarik HRV malam secara manual dari seluruh riwayat stres yang disimpan jam. */
  suspend fun syncHrv(): WatchHrvResult = guard {
    val d = runtime.link.syncHrvOnly()
    if (d == null) WatchHrvResult("tidak ada keterangan", 0, null) else WatchHrvResult(d.result, d.stored, d.records)
  }

  fun forget(address: String) {
    runtime.forget(address)
    _state.update { it.copy(name = null) }
  }

  companion object {
    const val ERR_BIND_CONFIRMATION = "ERR_BIND_CONFIRMATION"
    const val ERR_BLUETOOTH_PERMISSION = "ERR_BLUETOOTH_PERMISSION"
    const val ERR_BLUETOOTH_OFF = "ERR_BLUETOOTH_OFF"

    @Volatile
    private var instance: HuaweiWatch? = null

    fun get(context: Context): HuaweiWatch =
      instance ?: synchronized(this) { instance ?: HuaweiWatch(context).also { instance = it } }

    /** Izin yang harus diminta ke pengguna sebelum scan dan connect. */
    fun requiredPermissions(): List<String> = buildList {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.BLUETOOTH_SCAN)
      } else {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
      }
    }

    fun missingPermissions(context: Context): List<String> =
      requiredPermissions().filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
  }
}

private fun Map<String, Any?>.toWatchDevice(): WatchDevice {
  val address = this["address"] as? String ?: ""
  return WatchDevice(
    address = address,
    name = (this["name"] as? String).orEmpty().ifBlank { address },
    model = this["model"] as? String,
    support = this["support"] as? String ?: "unknown",
    reason = this["reason"] as? String,
    paired = this["paired"] as? Boolean,
    remembered = this["remembered"] as? Boolean ?: false,
  )
}
