package com.runnow.app

import android.content.Context
import android.net.Uri
import android.webkit.JavascriptInterface

/** URL server Run Now yang dimuat layar utama. Disimpan permanen, jadi aplikasi ditutup dan dibuka lagi tetap memuat URL terakhir. */
internal object ServerConfig {
    const val DEFAULT_HOST = "runnow2.kamui-aja-sa.workers.dev"
    const val DEFAULT_URL = "https://$DEFAULT_HOST"
    private const val PREFS = "runnow_settings"
    private const val KEY_URL = "start_url"

    fun get(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_URL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_URL

    /** null = hapus pilihan, kembali ke URL bawaan. */
    fun set(ctx: Context, url: String?) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (url == null) remove(KEY_URL) else putString(KEY_URL, url)
        }.apply()
    }

    /** Tambah https:// kalau tanpa skema; null kalau bukan URL http(s) yang valid. */
    fun normalize(input: String?): String? {
        var s = input?.trim().orEmpty()
        if (s.isEmpty()) return null
        if (!s.contains("://")) s = "https://$s"
        val uri = Uri.parse(s)
        val scheme = uri.scheme?.lowercase()
        if ((scheme != "http" && scheme != "https") || uri.host.isNullOrEmpty()) return null
        return s.trimEnd('/')
    }
}

/** Dipakai pengaturan.html (kelompok "Alamat server") lewat window.RunNowServer. */
internal class ServerBridge(private val ctx: Context, private val onBrandDone: () -> Unit = {}) {
    @JavascriptInterface fun get(): String = ServerConfig.get(ctx)

    @JavascriptInterface fun defaultUrl(): String = ServerConfig.DEFAULT_URL

    /** Mengembalikan URL yang tersimpan, atau "" kalau input tidak valid. */
    @JavascriptInterface fun save(input: String?): String {
        val url = ServerConfig.normalize(input) ?: return ""
        ServerConfig.set(ctx, url)
        return url
    }

    @JavascriptInterface fun reset(): String {
        ServerConfig.set(ctx, null)
        return ServerConfig.DEFAULT_URL
    }

    /** JSON ringkasan nama/ikon/splash/badge dari server: {ok, name, hasIcon, hasSplash, hasBadge, ...}. */
    @JavascriptInterface fun brand(): String = BrandConfig.describe(ctx)

    /** Ambil ulang dari manifest server di latar belakang; selesai → window.onBrandUpdated(). */
    @JavascriptInterface fun brandRefresh(): String {
        BrandConfig.refreshAsync(ctx, ServerConfig.get(ctx), force = true) { onBrandDone() }
        return "started"
    }

    /** Minta peluncur memasang pintasan layar utama bernama + berikon dari server: ok / no-brand / unsupported / failed. */
    @JavascriptInterface fun brandPin(): String = BrandConfig.requestPin(ctx)

    @JavascriptInterface fun brandReset(): String {
        BrandConfig.clear(ctx)
        return "ok"
    }
}
