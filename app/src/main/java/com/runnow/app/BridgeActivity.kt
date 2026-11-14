package com.runnow.app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.ValueCallback
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.webkit.WebViewAssetLoader
import com.joyodigitama.huaweiwear.HealthConnectPermissions
import com.joyodigitama.huaweiwear.HuaweiWatch
import com.joyodigitama.huaweiwear.PickedFile
import com.joyodigitama.huaweiwear.PickedUri
import com.joyodigitama.huaweiwear.RunNowBridge
import com.joyodigitama.huaweiwear.RunNowHost
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val ORIGIN_HOST = "runnow.local"

/**
 * Layar berisi satu WebView yang memuat halaman dari assets, ditambah jembatan ke Huawei core.
 * Dipakai oleh [MainActivity] (index.html) dan [SettingsActivity] (pengaturan.html).
 */
abstract class BridgeActivity : ComponentActivity() {
    protected lateinit var web: WebView
    protected lateinit var bridge: RunNowBridge
    private var pending: CompletableDeferred<Unit>? = null
    private var picking: CompletableDeferred<Uri?>? = null

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pending?.complete(Unit)
    }

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        picking?.complete(uri)
    }

    private var asking: CompletableDeferred<Set<String>>? = null

    // Pemilih berkas untuk <input type="file"> di halaman (Pilih file CSV, cadangan, dan sebagainya).
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val clip = data?.clipData
        val uris: Array<Uri>? = when {
            result.resultCode != android.app.Activity.RESULT_OK || data == null -> null
            clip != null -> Array(clip.itemCount) { clip.getItemAt(it).uri }
            data.data != null -> arrayOf(data.data!!)
            else -> null
        }
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
    }

    // Izin lokasi untuk navigator.geolocation di halaman (menu Lari).
    private var geoOrigin: String? = null
    private var geoCallback: GeolocationPermissions.Callback? = null
    private val geoPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val ok = result[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        geoCallback?.invoke(geoOrigin, ok, false)
        geoCallback = null
        geoOrigin = null
    }

    // Izin tulis Health Connect: dialog milik Health Connect, hasilnya izin yang sekarang diberikan.
    private val healthConnect = registerForActivityResult(HealthConnectPermissions()) { granted ->
        asking?.complete(granted)
    }

    protected val host: RunNowHost = object : RunNowHost {
        override suspend fun ask(required: List<String>, optional: List<String>): Boolean {
            val missing = (required + optional).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) {
                val done = CompletableDeferred<Unit>()
                withContext(Dispatchers.Main) {
                    pending = done
                    permissions.launch(missing.toTypedArray())
                }
                done.await()
            }
            return required.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        }

        override fun open(route: String): Boolean = openRoute(route)

        override fun eval(js: String) {
            runOnUiThread { if (::web.isInitialized) web.evaluateJavascript(js, null) }
        }

        override suspend fun pickUri(mime: String): PickedUri? {
            val done = CompletableDeferred<Uri?>()
            withContext(Dispatchers.Main) {
                picking = done
                picker.launch(mime)
            }
            val uri = done.await() ?: return null
            return PickedUri(uri.toString(), withContext(Dispatchers.IO) { displayName(uri) })
        }

        override fun keepScreenOn(on: Boolean) {
            runOnUiThread {
                if (on) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        override suspend fun requestHealthConnect(permissions: Set<String>): Set<String> {
            val done = CompletableDeferred<Set<String>>()
            withContext(Dispatchers.Main) {
                asking = done
                healthConnect.launch(permissions)
            }
            return done.await()
        }

        override suspend fun pickFile(mime: String): PickedFile? {
            val done = CompletableDeferred<Uri?>()
            withContext(Dispatchers.Main) {
                picking = done
                picker.launch(mime)
            }
            val uri = done.await() ?: return null
            return withContext(Dispatchers.IO) {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) null else PickedFile(displayName(uri), contentResolver.getType(uri), bytes)
            }
        }
    }

    /** Alamat halaman awal, mis. https://runnow.local/index.html. */
    protected abstract fun startUrl(): String

    /** Buka layar lain untuk `openScreen`. False kalau layar itu tidak ada. */
    protected abstract fun openRoute(route: String): Boolean

    /** True selama halaman merekam lari: WebView tidak dijeda saat layar mati supaya GPS dan detak jantung tetap masuk. */
    protected open fun keepWebAlive(): Boolean = false

    /** Host tambahan (selain runnow.local) yang boleh dibuka di dalam WebView ini. */
    protected open fun isTrustedHost(host: String?): Boolean = false

    /** Hook untuk menambah antarmuka JS lain ke WebView. */
    protected open fun onWebCreated(web: WebView) {}

    /** Halaman utama gagal dimuat (mis. URL salah atau tidak ada koneksi). */
    protected open fun onLoadError(url: String, reason: String) {}

    /** Halaman selesai dimuat. */
    protected open fun onPageLoaded() {}

    /** Detak jantung live hanya diminta dari jam oleh layar yang memakainya. */
    protected open fun wantsLiveHr(): Boolean = false

    /** Dipanggil saat tombol Back ditekan dan halaman tidak menanganinya sendiri. */
    protected open fun onBackUnhandled() {
        finish()
    }

    protected fun launchPermissions(list: Array<String>) {
        permissions.launch(list)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Menyiapkan runtime jam (dan service latar belakang bila diaktifkan) sebelum halaman memanggilnya.
        HuaweiWatch.get(applicationContext)
        bridge = RunNowBridge(applicationContext, host)
        bridge.attach()

        val loader = WebViewAssetLoader.Builder()
            .setDomain(ORIGIN_HOST)
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val u = request.url
                    // Run Now memanggil /api/icu/...; di aplikasi, permintaan itu diteruskan ke Intervals.icu dari sini.
                    if (u.host == ORIGIN_HOST && u.path?.startsWith("/api/icu/") == true) return proxyIcu(request)
                    return loader.shouldInterceptRequest(u)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    onPageLoaded()
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) onLoadError(request.url.toString(), error.description?.toString().orEmpty())
                }

                // Hanya halaman bawaan yang dibuka di sini; tautan lain dibuka di browser.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (request.url.host == ORIGIN_HOST || isTrustedHost(request.url.host)) return false
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    } catch (_: Exception) {
                    }
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: WebChromeClient.FileChooserParams?,
                ): Boolean {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = filePathCallback
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        if (fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }
                    }
                    return try {
                        fileChooser.launch(intent)
                        true
                    } catch (_: Exception) {
                        fileCallback?.onReceiveValue(null)
                        fileCallback = null
                        false
                    }
                }

                override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                    val host = origin?.let { Uri.parse(it).host }
                    if (callback == null || origin == null || !(host == ORIGIN_HOST || isTrustedHost(host))) {
                        callback?.invoke(origin, false, false)
                        return
                    }
                    val fine = android.Manifest.permission.ACCESS_FINE_LOCATION
                    val coarse = android.Manifest.permission.ACCESS_COARSE_LOCATION
                    if (checkSelfPermission(fine) == PackageManager.PERMISSION_GRANTED) {
                        callback.invoke(origin, true, false)
                    } else {
                        geoOrigin = origin
                        geoCallback = callback
                        geoPermission.launch(arrayOf(fine, coarse))
                    }
                }
            }
            // `rn(...)` di halaman memanggil window.ReactNativeWebView.postMessage, jadi jembatan memakai nama itu.
            addJavascriptInterface(bridge, "ReactNativeWebView")
        }
        onWebCreated(web)
        setContentView(web)
        web.loadUrl(startUrl())

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Halaman menjawab true kalau ia sendiri yang menangani Back (tutup dialog, kembali ke daftar).
                web.evaluateJavascript("window.nativeBack?window.nativeBack():false") { r ->
                    if (r != "true") onBackUnhandled()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        bridge.setLive(wantsLiveHr())
    }

    override fun onPause() {
        // Saat merekam lari, biarkan WebView dan detak jantung live tetap jalan walau layar mati.
        if (!keepWebAlive()) {
            bridge.setLive(false)
            web.onPause()
        }
        super.onPause()
    }

    override fun onDestroy() {
        bridge.detach()
        web.destroy()
        super.onDestroy()
    }

    private fun displayName(uri: Uri): String {
        val fromProvider = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        return fromProvider ?: uri.lastPathSegment ?: "berkas"
    }
}


/** Proxy GET ke https://intervals.icu/api/v1/..., meniru route server /api/icu/$ milik versi web. */
private fun proxyIcu(request: WebResourceRequest): WebResourceResponse {
    fun fail(code: Int, reason: String, msg: String) = WebResourceResponse(
        "text/plain", "utf-8", code, reason, mapOf("Cache-Control" to "no-store"),
        java.io.ByteArrayInputStream(msg.toByteArray()),
    )
    val rest = request.url.path.orEmpty().removePrefix("/api/icu/")
    if (request.method != "GET" || rest.isEmpty() || rest.contains("..") || rest.contains("://")) {
        return fail(400, "Bad Request", "Path tidak valid")
    }
    val auth = request.requestHeaders.entries.firstOrNull { it.key.equals("Authorization", ignoreCase = true) }?.value.orEmpty()
    if (!auth.startsWith("Basic ") || auth.length < 12 || auth.length > 400) {
        return fail(401, "Unauthorized", "API key wajib")
    }
    return try {
        val query = request.url.encodedQuery?.let { "?$it" }.orEmpty()
        val conn = java.net.URL("https://intervals.icu/api/v1/$rest$query").openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Authorization", auth)
        conn.setRequestProperty("Accept", "application/json")
        val code = conn.responseCode
        val stream = if (code in 200..399) conn.inputStream else conn.errorStream
        val body = stream?.use { it.readBytes() } ?: ByteArray(0)
        val mime = conn.contentType?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() } ?: "application/json"
        val reason = conn.responseMessage?.takeIf { it.isNotEmpty() } ?: "OK"
        WebResourceResponse(mime, "utf-8", code, reason, mapOf("Cache-Control" to "no-store"), java.io.ByteArrayInputStream(body))
    } catch (_: Exception) {
        fail(502, "Bad Gateway", "Gagal menghubungi Intervals.icu")
    }
}
