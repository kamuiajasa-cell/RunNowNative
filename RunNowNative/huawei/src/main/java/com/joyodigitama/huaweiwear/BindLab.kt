package com.joyodigitama.huaweiwear

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException

/**
 * Developer bind lab, started by [BleProbeReceiver] (`--es lab TAG [--es stop negotiation|pin|step2] [--ez commit true] [--ez adopt true]`).
 * It authenticates over LE GATT on a session of its own, with a throwaway client identity (its own android id, fake MAC and auth
 * key in separate preferences) instead of the one the watch is bound to, so the watch sees a phone that never bound before, as it
 * would for an iPhone. `--es lab real` presents the stored identity instead, which only reconnects (it never binds). Every frame
 * and the secrets of the session go to logcat in full under the JoyoFitBle tag.
 *
 * A stop ends the authentication early. Without one, a lab identity that never bound runs the whole bind, which makes the watch
 * drop the binding the app holds (a watch keeps one), so that needs `commit`; a lab identity that already bound just reconnects.
 * After a lab authenticates it reads the battery to prove the key works, and with `adopt` the lab identity then replaces the
 * stored one, so the app keeps working with it. The lab never connects the app's own link and never syncs; the background link is
 * paused meanwhile and put back after.
 *
 * Android 14 and newer cannot run the first HiChain step of a bind: they ask for ATT MTU 517 whatever the app requests, the watch
 * then answers in 514-byte notifications and the stack drops everything over 512 bytes (gatt_cl.cc, GATT_MAX_ATTR_LEN), so the
 * reply to operationCode 1 step 1 (about 835 bytes) loses its head and the bind times out. The lab refuses those runs there.
 */
internal object BindLab {
  private const val TAG = "JoyoFitBle"

  /** A lab session logs every frame in full, unlike [LogcatMirrorEvents]. */
  private object LabEvents : WearEvents {
    override fun state(state: LinkState, error: String?) {
      Log.i(TAG, "lab: state ${state.wire}${if (error != null) ": $error" else ""}")
    }

    override fun battery(level: Int) {
      Log.i(TAG, "lab: battery $level%")
    }

    override fun syncComplete(summary: SyncSummary) = Unit

    override fun log(direction: LogDirection, serviceId: Int?, commandId: Int?, hex: String?, message: String) {
      val command = if (serviceId != null && commandId != null) " %02x/%02x".format(serviceId, commandId) else ""
      Log.i(TAG, "[${direction.wire}]$command $message${if (hex != null) " $hex" else ""}")
    }
  }

  @SuppressLint("MissingPermission")
  suspend fun run(runtime: WearRuntime, rawAddress: String, tag: String, lab: AuthLab, commit: Boolean, adopt: Boolean) {
    val stop = lab.stopAfter
    val address = rawAddress.trim().uppercase()
    if (!Regex("[A-Za-z0-9]{1,16}").matches(tag)) {
      Log.e(TAG, "lab: the identity tag must be 1 to 16 letters or digits")
      return
    }
    val real = tag == "real"
    if (adopt && (real || stop != null)) {
      Log.e(TAG, "lab: adopt needs a lab identity (not real) and no stop")
      return
    }

    val wasBackground = runtime.settings.backgroundEnabled
    val wasPaused = runtime.paused
    Log.i(TAG, "lab: start, address $address, identity $tag, stop ${stop ?: "none (full bind)"}")
    runtime.disconnectFromApp()

    var session: HuaweiSession? = null
    var transport: BleTransport? = null
    try {
      val context = runtime.appContext
      val adapter = context.getSystemService(BluetoothManager::class.java).adapter
      val device = adapter.getRemoteDevice(address)
      val name = (try { device.name } catch (e: SecurityException) { null }) ?: runtime.settings.watchName
      val model = WatchModels.match(name)
      lateinit var live: HuaweiSession
      val ble = BleTransport(
        context = context,
        device = device,
        newHonorProtocol = model.newHonorProtocol,
        watchName = name,
        onData = { chunk -> live.onData(chunk) },
        onRemoteClosed = { cause -> Log.i(TAG, "lab: watch closed the link: ${cause?.message}") },
        log = { line -> LabEvents.log(LogDirection.INFO, null, null, null, line) },
      )
      live = HuaweiSession(ble, LabEvents)
      live.params.setTransactionsCrypted(model.crypted)
      session = live
      transport = ble

      ble.connect(Timeouts.CONNECT_MS)
      val store = DeviceStore(context)
      val identity = if (real) store.forDevice(address) else store.forLab(address, tag)
      identity.ensureCreated()
      Log.i(TAG, "lab: identity $tag androidId ${identity.androidId} firstConnection ${identity.isFirstConnection}")
      if (stop == null && identity.isFirstConnection) {
        if (real) throw WearException(ErrorCodes.PROTOCOL, "The stored identity has no completed bind; the lab never binds it")
        if (!commit) {
          throw WearException(
            ErrorCodes.PROTOCOL,
            "This run would bind the watch to the lab identity and so replace the binding the app holds; pass --ez commit true for that",
          )
        }
      }
      if (!real && identity.isFirstConnection && (stop == null || stop == BindStop.ISO_MAC_VERIFIED) &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
      ) {
        throw WearException(
          ErrorCodes.PROTOCOL,
          "Android 14+ drops the watch's 514-byte notifications, so the reply to HiChain operationCode 1 step 1 never arrives on this phone",
        )
      }
      val authenticator = HuaweiAuthenticator(live, identity, LabEvents, model.deviceType(ModelTransport.LE), lab)
      // The stored identity never binds; a lab identity binds only with commit, or stops before anything changes on the watch.
      val mode = authenticator.authenticate(firstBindAllowed = !real && (commit || stop != null))
      Log.i(TAG, "lab: AUTHENTICATED ${mode.wire}, protocolVersion ${authenticator.protocolVersion}, bonded ${!identity.isFirstConnection}")
      val battery = live.queryBattery(Timeouts.QUERY_MS)
      Log.i(TAG, "lab: BATTERY $battery% over the new key")
      if (adopt) {
        store.adoptLab(address, tag)
        Log.i(TAG, "lab: ADOPTED, the app's stored identity for $address is now lab identity $tag")
      }
      Log.i(TAG, "lab: RESULT success")
    } catch (e: WearException) {
      Log.e(TAG, "lab: RESULT ended [${e.errorCode}]: ${e.message}")
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "lab: RESULT failed: ${e.javaClass.simpleName}: ${e.message}", e)
    } finally {
      session?.close(WearException(ErrorCodes.DISCONNECTED, "Bind lab finished"))
      transport?.close()
      if (wasBackground && !wasPaused) runtime.settings.watchAddress?.let { runtime.setBackground(it) }
      Log.i(TAG, "lab: done, background link ${if (wasBackground && !wasPaused) "resumes" else "left as it was"}")
    }
  }
}
