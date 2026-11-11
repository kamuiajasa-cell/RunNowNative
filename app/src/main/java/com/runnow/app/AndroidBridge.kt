package com.runnow.app

import android.content.Context
import android.webkit.JavascriptInterface

/** `Android.setPlan` dan `Android.test` yang dipakai index.html untuk pengingat latihan. */
class AndroidBridge(
    private val context: Context,
    private val askPermission: () -> Unit,
    private val onTracking: (Boolean) -> Unit = {},
) {
    /** Dipanggil menu Lari: true saat rekaman dimulai, false saat selesai. Menjaga layar menyala dan GPS tetap hidup. */
    @JavascriptInterface
    fun setTracking(on: Boolean) {
        onTracking(on)
    }

    /** Daftar pengingat: JSON [{at, title, body, big}]. Menggantikan daftar sebelumnya. */
    @JavascriptInterface
    fun setPlan(json: String?) {
        PlanNotifier.set(context, json ?: "[]")
        askPermission()
    }

    @JavascriptInterface
    fun test(title: String?, body: String?, big: String?) {
        askPermission()
        PlanNotifier.show(context, 1, title.orEmpty(), body.orEmpty(), big.orEmpty())
    }
}
