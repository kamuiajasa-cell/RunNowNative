package com.runnow.app

import android.content.Intent

/**
 * Peta alamat layar (dari `openScreen` di halaman) ke layar di aplikasi.
 * Alamat harus lolos daftar di RunNowBridge.ALLOWED_ROUTES / PENGATURAN dulu; di sini hanya yang benar-benar ada yang dibuka.
 */
internal object Routes {
    /** Kelompok yang ada di pengaturan.html. Tambah di sini bersama kelompok baru di tabel G pada pengaturan.html. */
    private val GROUPS = setOf(
        "sinkron", "dasar", "cuaca", "notifikasi", "aplikasi", "alarm", "gnss", "rute", "kursus", "hidup-sehat", "wajah", "foto-jam", "kustom-wajah", "keselamatan", "tidur-siklus", "target", "latihan-jam", "kesehatan", "health-connect", "impor-huawei", "musik",
    )

    private val ALIAS = mapOf("/settings/notification-apps" to "aplikasi", "/watch/alarms" to "alarm", "/watch/faces" to "wajah", "/settings/import-huawei" to "impor-huawei")

    fun open(from: BridgeActivity, route: String): Boolean {
        if (route == "/") {
            // Dari pengaturan: tutup dan kembali ke halaman utama. Dari halaman utama: sudah di sana.
            if (from is SettingsActivity) from.runOnUiThread { from.finish() }
            return true
        }
        if (route == "/settings/koneksi" || route == "/watch") {
            from.runOnUiThread { from.startActivity(Intent(from, PairingActivity::class.java)) }
            return true
        }
        val group: String = ALIAS[route] ?: when {
            route == "/settings" || route == "/pengaturan" -> ""
            route.startsWith("/settings/") -> route.removePrefix("/settings/")
            route.startsWith("/pengaturan/") -> route.removePrefix("/pengaturan/")
            else -> return false
        }
        if (group.isNotEmpty() && group !in GROUPS) return false
        if (from is SettingsActivity) {
            from.goGroup(group)
        } else {
            from.runOnUiThread {
                from.startActivity(Intent(from, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_GROUP, group))
            }
        }
        return true
    }
}
