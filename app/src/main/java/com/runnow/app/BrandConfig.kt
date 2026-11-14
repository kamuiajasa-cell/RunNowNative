package com.runnow.app

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Nama, ikon, splash, dan badge aplikasi yang diambil dari server (URL yang dimuat layar utama).
 *
 * Sumbernya `manifest.webmanifest` milik server:
 *  - nama      : `name` / `short_name`
 *  - ikon      : entri `icons` terbesar yang bukan monochrome
 *  - splash    : kunci `splash` (URL gambar) bila ada, kalau tidak memakai ikon; warna dari `background_color`
 *  - badge     : kunci `badge` (URL gambar), atau ikon ber-`purpose: monochrome`, atau `badge-96.png` di server
 *
 * Semuanya disimpan di filesDir/brand supaya tetap tampil saat offline. Tanpa cache, aplikasi memakai resource bawaan.
 *
 * Batasan Android: ikon peluncur dan label di AndroidManifest tidak bisa diganti saat aplikasi berjalan. Yang bisa:
 * splash dalam aplikasi, ikon kecil notifikasi (badge), nama + ikon di layar Recents, dan pintasan layar utama
 * (nama + ikon dari server) lewat [requestPin].
 */
internal object BrandConfig {
    private const val PREFS = "runnow_brand"
    private const val KEY = "brand"
    private const val DIR = "brand"
    private const val SHORTCUT_ID = "brand_main"
    private const val STALE_MS = 12L * 60 * 60 * 1000
    private const val RETRY_MS = 5L * 60 * 1000

    private val running = AtomicBoolean(false)
    @Volatile private var lastTry = 0L
    @Volatile private var lastTryUrl = ""

    class Brand(
        val source: String,
        val name: String,
        val shortName: String,
        val background: Int?,
        val theme: Int?,
        val iconUrl: String,
        val badgeUrl: String,
        val splashUrl: String,
        val iconMaskable: Boolean,
        val fetchedAt: Long,
    ) {
        val title: String get() = name.ifBlank { shortName }
    }

    // ---------- baca cache ----------

    fun load(ctx: Context): Brand? {
        val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        return try {
            val o = JSONObject(s)
            Brand(
                source = o.optString("source"),
                name = o.optString("name"),
                shortName = o.optString("short"),
                background = if (o.has("bg")) o.getInt("bg") else null,
                theme = if (o.has("theme")) o.getInt("theme") else null,
                iconUrl = o.optString("icon"),
                badgeUrl = o.optString("badge"),
                splashUrl = o.optString("splash"),
                iconMaskable = o.optBoolean("maskable"),
                fetchedAt = o.optLong("at"),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun dir(ctx: Context) = File(ctx.filesDir, DIR)

    private fun bmp(ctx: Context, name: String): Bitmap? {
        val f = File(dir(ctx), name)
        return if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }

    fun icon(ctx: Context): Bitmap? = bmp(ctx, "icon.png")
    fun badge(ctx: Context): Bitmap? = bmp(ctx, "badge.png")
    fun splash(ctx: Context): Bitmap? = bmp(ctx, "splash.png") ?: icon(ctx)

    fun opaque(c: Int): Int = Color.rgb(Color.red(c), Color.green(c), Color.blue(c))

    /** Perkecil bitmap sebelum dikirim ke sistem (Binder punya batas ~1 MB per transaksi). */
    fun scaled(b: Bitmap, px: Int): Bitmap =
        if (px <= 0 || (b.width <= px && b.height <= px)) b else Bitmap.createScaledBitmap(b, px, px, true)

    /** Ikon kecil notifikasi: badge dari server (harus punya transparansi), kalau tidak ada pakai bawaan. */
    fun statIcon(ctx: Context): Icon {
        val b = badge(ctx)
        return if (b != null) Icon.createWithBitmap(b) else Icon.createWithResource(ctx, R.drawable.ic_stat_runnow)
    }

    /** Warna aksen notifikasi: theme_color server, atau hijau bawaan. */
    fun accent(ctx: Context): Int = load(ctx)?.theme?.let { opaque(it) } ?: 0xFF00D15C.toInt()

    /** Ringkasan untuk pengaturan.html. */
    fun describe(ctx: Context): String {
        val b = load(ctx) ?: return JSONObject().put("ok", false).toString()
        val d = dir(ctx)
        val hasIcon = File(d, "icon.png").exists()
        return JSONObject()
            .put("ok", true)
            .put("name", b.title)
            .put("source", b.source)
            .put("hasIcon", hasIcon)
            .put("hasSplash", File(d, "splash.png").exists() || hasIcon)
            .put("hasBadge", File(d, "badge.png").exists())
            .put("iconUrl", b.iconUrl)
            .put("splashUrl", b.splashUrl)
            .put("badgeUrl", b.badgeUrl)
            .put("at", b.fetchedAt)
            .toString()
    }

    fun clear(ctx: Context) {
        dir(ctx).deleteRecursively()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    // ---------- splash dalam aplikasi ----------

    /** Layar splash (latar background_color + ikon + nama) untuk menutupi WebView sampai halaman selesai dimuat. Null bila belum ada cache. */
    fun splashView(ctx: Context): View? {
        val b = load(ctx) ?: return null
        val img = splash(ctx)
        if (img == null && b.title.isBlank()) return null
        val bg = opaque(b.background ?: b.theme ?: 0xFF0A0A0C.toInt())
        val dp = ctx.resources.displayMetrics.density
        val lum = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255.0

        val root = FrameLayout(ctx).apply {
            setBackgroundColor(bg)
            isClickable = true // jangan teruskan sentuhan ke WebView di bawahnya
        }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        if (img != null) {
            val side = (128 * dp).toInt()
            col.addView(
                ImageView(ctx).apply {
                    setImageBitmap(img)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                },
                LinearLayout.LayoutParams(side, side),
            )
        }
        if (b.title.isNotBlank()) {
            col.addView(
                TextView(ctx).apply {
                    text = b.title
                    setTextColor(if (lum > 0.55) Color.BLACK else Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(0, (16 * dp).toInt(), 0, 0)
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
        }
        root.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        return root
    }

    // ---------- pintasan layar utama (nama + ikon dari server) ----------

    private fun shortcutInfo(ctx: Context, b: Brand, sm: ShortcutManager): ShortcutInfo {
        val bmp = icon(ctx)?.let { scaled(it, minOf(sm.iconMaxWidth, sm.iconMaxHeight)) }
        val ic = when {
            bmp == null -> Icon.createWithResource(ctx, R.mipmap.ic_launcher)
            b.iconMaskable -> Icon.createWithAdaptiveBitmap(bmp)
            else -> Icon.createWithBitmap(bmp)
        }
        val label = b.title.ifBlank { ctx.getString(R.string.app_name) }
        return ShortcutInfo.Builder(ctx, SHORTCUT_ID)
            .setShortLabel(b.shortName.ifBlank { label })
            .setLongLabel(label)
            .setIcon(ic)
            .setIntent(Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
            .build()
    }

    /** Minta peluncur memasang pintasan: "ok", "no-brand", "unsupported", atau "failed". */
    fun requestPin(ctx: Context): String {
        val b = load(ctx) ?: return "no-brand"
        val sm = ctx.getSystemService(ShortcutManager::class.java) ?: return "unsupported"
        if (!sm.isRequestPinShortcutSupported) return "unsupported"
        return try {
            if (sm.requestPinShortcut(shortcutInfo(ctx, b, sm), null)) "ok" else "failed"
        } catch (_: Exception) {
            "failed"
        }
    }

    /** Pintasan yang sudah dipasang ikut berubah bila nama atau ikon di server berubah. */
    fun updatePinned(ctx: Context) {
        try {
            val b = load(ctx) ?: return
            val sm = ctx.getSystemService(ShortcutManager::class.java) ?: return
            if (sm.pinnedShortcuts.none { it.id == SHORTCUT_ID }) return
            sm.updateShortcuts(listOf(shortcutInfo(ctx, b, sm)))
        } catch (_: Exception) {
        }
    }

    // ---------- ambil dari server ----------

    /**
     * Ambil manifest di latar belakang lalu panggil [done] di thread utama (true bila cache berhasil diperbarui).
     * Tanpa [force]: lewati bila cache untuk URL ini masih baru, atau percobaan terakhir belum 5 menit lalu.
     */
    fun refreshAsync(ctx: Context, pageUrl: String, force: Boolean, done: (Boolean) -> Unit = {}) {
        val app = ctx.applicationContext
        val now = System.currentTimeMillis()
        val main = Handler(Looper.getMainLooper())
        if (!force) {
            val cur = load(app)
            if (cur != null && cur.source == pageUrl && now - cur.fetchedAt < STALE_MS) return
            if (lastTryUrl == pageUrl && now - lastTry < RETRY_MS) return
        }
        if (!running.compareAndSet(false, true)) {
            if (force) main.post { done(false) }
            return
        }
        lastTry = now
        lastTryUrl = pageUrl
        thread(name = "brand-refresh") {
            val ok = try {
                refresh(app, pageUrl)
            } catch (_: Exception) {
                false
            } finally {
                running.set(false)
            }
            if (ok) {
                updatePinned(app)
            } else {
                // Server baru tanpa manifest: jangan biarkan nama/ikon server lama tetap tampil.
                val cur = load(app)
                if (cur != null && cur.source != pageUrl) clear(app)
            }
            main.post { done(ok) }
        }
    }

    private fun refresh(app: Context, pageUrl: String): Boolean {
        val page = URL(pageUrl)
        if (page.protocol != "http" && page.protocol != "https") return false

        // 1) Cari <link rel="manifest"> di halaman, 2) cadangan: /manifest.webmanifest dan /manifest.json.
        val candidates = ArrayList<String>()
        httpGet(pageUrl, 2_000_000)?.let { bytes ->
            val tag = Regex("<link[^>]+rel=[\"']manifest[\"'][^>]*>", RegexOption.IGNORE_CASE)
                .find(String(bytes, Charsets.UTF_8))?.value
            val href = tag?.let { Regex("href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
            if (href != null) candidates.add(URL(page, href).toString())
        }
        candidates.add(URL(page, "/manifest.webmanifest").toString())
        candidates.add(URL(page, "/manifest.json").toString())

        var manifest: JSONObject? = null
        var manifestUrl = ""
        for (c in candidates.distinct()) {
            val bytes = httpGet(c, 500_000) ?: continue
            try {
                manifest = JSONObject(String(bytes, Charsets.UTF_8))
                manifestUrl = c
                break
            } catch (_: Exception) {
            }
        }
        val m = manifest ?: return false
        val base = URL(manifestUrl)

        // Ikon terbesar yang bukan monochrome; monochrome dicadangkan untuk badge.
        var iconUrl = ""
        var maskable = false
        var monoUrl = ""
        var best = -1
        val arr = m.optJSONArray("icons")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val src = o.optString("src")
                if (src.isBlank() || src.substringBefore('?').endsWith(".svg", ignoreCase = true)) continue
                val abs = URL(base, src).toString()
                val purpose = o.optString("purpose", "any").lowercase()
                if (purpose.contains("monochrome")) {
                    monoUrl = abs
                    continue
                }
                val size = o.optString("sizes").split(' ').maxOfOrNull { it.substringBefore('x').toIntOrNull() ?: 0 } ?: 0
                if (size > best) {
                    best = size
                    iconUrl = abs
                    maskable = purpose.contains("maskable") && !purpose.contains("any")
                }
            }
        }
        val badgeUrl = m.optString("badge").takeIf { it.isNotBlank() }?.let { URL(base, it).toString() }
            ?: monoUrl.ifBlank { URL(page, "badge-96.png").toString() }
        val splashUrl = m.optString("splash").takeIf { it.isNotBlank() }?.let { URL(base, it).toString() }.orEmpty()

        val old = load(app)
        val d = dir(app).apply { mkdirs() }
        val iconOk = iconUrl.isNotBlank() && ensureImage(File(d, "icon.png"), iconUrl, old?.iconUrl, 512, false)
        val splashOk = splashUrl.isNotBlank() && ensureImage(File(d, "splash.png"), splashUrl, old?.splashUrl, 512, false)
        if (splashUrl.isBlank()) File(d, "splash.png").delete()
        // Badge notifikasi harus punya transparansi (hanya kanal alfa yang dipakai sistem); kalau tidak, pakai bawaan.
        val badgeOk = ensureImage(File(d, "badge.png"), badgeUrl, old?.badgeUrl, 96, true)

        val name = m.optString("name")
        val short = m.optString("short_name")
        if (name.isBlank() && short.isBlank() && !iconOk) return false

        val bg = parseColor(m.optString("background_color"))
        val theme = parseColor(m.optString("theme_color"))
        val o = JSONObject()
            .put("source", page.toString())
            .put("name", name)
            .put("short", short)
            .put("icon", if (iconOk) iconUrl else "")
            .put("badge", if (badgeOk) badgeUrl else "")
            .put("splash", if (splashOk) splashUrl else "")
            .put("maskable", maskable)
            .put("at", System.currentTimeMillis())
        if (bg != null) o.put("bg", bg)
        if (theme != null) o.put("theme", theme)
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
        return true
    }

    /** Unduh [url] ke [file] kecuali URL-nya sama dengan yang sudah tersimpan. File dihapus bila gagal. */
    private fun ensureImage(file: File, url: String, oldUrl: String?, maxPx: Int, needAlpha: Boolean): Boolean {
        if (file.exists() && oldUrl == url) return true
        if (saveImage(file, url, maxPx, needAlpha)) return true
        file.delete()
        return false
    }

    private fun saveImage(file: File, url: String, maxPx: Int, needAlpha: Boolean): Boolean {
        val bytes = httpGet(url, 3_000_000) ?: return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 4096 || bounds.outHeight > 4096) return false
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        if (bmp.width > maxPx || bmp.height > maxPx) {
            val k = maxPx.toFloat() / maxOf(bmp.width, bmp.height)
            bmp = Bitmap.createScaledBitmap(bmp, maxOf(1, (bmp.width * k).toInt()), maxOf(1, (bmp.height * k).toInt()), true)
        }
        if (needAlpha && !hasTransparency(bmp)) return false
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return true
    }

    private fun hasTransparency(b: Bitmap): Boolean {
        if (!b.hasAlpha()) return false
        val step = maxOf(1, b.width / 24)
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                if (Color.alpha(b.getPixel(x, y)) < 250) return true
                x += step
            }
            y += step
        }
        return false
    }

    private fun parseColor(s: String): Int? {
        var c = s.trim()
        if (c.isEmpty()) return null
        if (Regex("^#[0-9a-fA-F]{3}$").matches(c)) c = "#" + c.drop(1).map { "$it$it" }.joinToString("")
        return try {
            opaque(Color.parseColor(c))
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGet(url: String, maxBytes: Int): ByteArray? {
        val u = try { URL(url) } catch (_: Exception) { return null }
        if (u.protocol != "http" && u.protocol != "https") return null
        val conn = try { u.openConnection() as HttpURLConnection } catch (_: Exception) { return null }
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) return null
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
