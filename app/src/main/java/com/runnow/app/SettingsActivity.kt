package com.runnow.app

import android.webkit.WebView
import org.json.JSONObject

/**
 * Layar pengaturan: pengaturan.html di dalam WebView. Dibuka dari tab Setting lewat openScreen("/settings")
 * atau openScreen("/pengaturan/<kelompok>"). Back kembali dari kelompok ke daftar, lalu menutup layar ini
 * dan kembali ke index.html yang masih terbuka di bawahnya.
 */
class SettingsActivity : BridgeActivity() {
    override fun startUrl(): String {
        val group = intent.getStringExtra(EXTRA_GROUP).orEmpty()
        return "https://$ORIGIN_HOST/pengaturan.html" + if (group.isEmpty()) "" else "#$group"
    }

    override fun openRoute(route: String): Boolean = Routes.open(this, route)

    // Kelompok "Alamat server" di pengaturan.html membaca dan menyimpan URL lewat window.RunNowServer.
    override fun onWebCreated(web: WebView) {
        web.addJavascriptInterface(
            ServerBridge(applicationContext) { host.eval("window.onBrandUpdated&&window.onBrandUpdated();") },
            "RunNowServer",
        )
    }

    /** Pindah ke kelompok lain tanpa membuka layar baru. Kosong = kembali ke daftar. */
    fun goGroup(group: String) {
        host.eval("location.hash=" + JSONObject.quote(group) + ";")
    }

    companion object {
        const val EXTRA_GROUP = "group"
    }
}
