package com.runnow.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.WebView
import android.widget.Toast
import com.joyodigitama.huaweiwear.WatchDataSignal

/** Layar utama: situs Run Now (URL diatur di Pengaturan > Alamat server) di dalam WebView, ditambah jembatan ke Huawei core. */
class MainActivity : BridgeActivity() {
    // URL yang sedang dimuat; dipakai untuk mendeteksi perubahan dari layar pengaturan.
    private var loadedUrl: String = ""
    private var errorShown = false

    // Selalu URL terakhir yang disimpan, jadi aplikasi dibuka ulang langsung memuat URL itu.
    override fun startUrl(): String = ServerConfig.get(this).also { loadedUrl = it }

    // Tautan di dalam situs Run Now tetap dibuka di WebView, bukan di browser.
    override fun isTrustedHost(host: String?): Boolean =
        host != null && (host == ServerConfig.DEFAULT_HOST || host == Uri.parse(ServerConfig.get(this)).host)

    // URL gagal dimuat: buka langsung Pengaturan > Alamat server supaya bisa diperbaiki.
    override fun onLoadError(url: String, reason: String) {
        if (Uri.parse(url).host == ORIGIN_HOST) return
        runOnUiThread {
            if (isFinishing || errorShown) return@runOnUiThread
            errorShown = true
            Toast.makeText(this, "Gagal memuat $url ($reason)", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_GROUP, "server"))
        }
    }

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
        errorShown = false
        // URL diubah di layar pengaturan: muat URL baru.
        val now = ServerConfig.get(this)
        if (now != loadedUrl) {
            loadedUrl = now
            web.loadUrl(now)
        }
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
