package com.runnow.app

import android.os.Build
import android.webkit.WebView
import com.joyodigitama.huaweiwear.WatchDataSignal

/** Layar utama: https://runnow2.kamui-aja-sa.workers.dev di dalam WebView, ditambah jembatan ke Huawei core. */
class MainActivity : BridgeActivity() {
    private companion object {
        const val REMOTE_HOST = "runnow2.kamui-aja-sa.workers.dev"
        const val REMOTE_URL = "https://$REMOTE_HOST"
    }

    // Halaman utama kini memuat situs Run Now langsung dari Cloudflare Workers.
    override fun startUrl(): String = REMOTE_URL

    // Tautan di dalam situs Run Now tetap dibuka di WebView, bukan di browser.
    override fun isTrustedHost(host: String?): Boolean = host == REMOTE_HOST

    override fun openRoute(route: String): Boolean = Routes.open(this, route)

    // Notifikasi pengingat: Android.setPlan dan Android.test.
    override fun onWebCreated(web: WebView) {
        // Halaman utama menerima data jam (wellness, aktivitas, latihan) lewat window.nativeImport.
        bridge.pushesWatchData = true
        web.addJavascriptInterface(
            AndroidBridge(applicationContext, { askNotificationPermission() }, { on -> setTracking(on) }),
            "Android",
        )
    }

    override fun onPageLoaded() {
        bridge.pushWatchData()
    }

    override fun onResume() {
        super.onResume()
        // Setelah impor ekspor Huawei (di layar pengaturan) isi database jam berubah: kirim ulang ke halaman.
        if (WatchDataSignal.take()) bridge.pushWatchData()
    }

    override fun wantsLiveHr(): Boolean = true

    @Volatile private var tracking = false

    override fun keepWebAlive(): Boolean = tracking

    // Rekam lari: layar tetap menyala dan service latar depan menjaga proses (dan GPS) tetap hidup.
    private fun setTracking(on: Boolean) {
        tracking = on
        runOnUiThread {
            if (on) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                askNotificationPermission()
                RunTrackService.start(this)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                RunTrackService.stop(this)
            }
        }
    }

    // Back di halaman utama menyembunyikan aplikasi, tidak menutupnya.
    override fun onBackUnhandled() {
        moveTaskToBack(true)
    }

    private fun askNotificationPermission() {
        runOnUiThread {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                launchPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }
}
