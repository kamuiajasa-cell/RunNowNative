package com.runnow.app

import android.os.Build
import android.webkit.WebView
import com.joyodigitama.huaweiwear.WatchDataSignal

/** Layar utama: index.html di dalam WebView, ditambah jembatan ke Huawei core. */
class MainActivity : BridgeActivity() {
    override fun startUrl(): String = "https://$ORIGIN_HOST/index.html"

    override fun openRoute(route: String): Boolean = Routes.open(this, route)

    // Notifikasi pengingat: Android.setPlan dan Android.test.
    override fun onWebCreated(web: WebView) {
        // Halaman utama menerima data jam (wellness, aktivitas, latihan) lewat window.nativeImport.
        bridge.pushesWatchData = true
        web.addJavascriptInterface(AndroidBridge(applicationContext) { askNotificationPermission() }, "Android")
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
