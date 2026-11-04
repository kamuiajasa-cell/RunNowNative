package com.runnow.app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.webkit.WebViewAssetLoader
import com.joyodigitama.huaweiwear.HuaweiWatch
import com.joyodigitama.huaweiwear.PickedFile
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

    /** Hook untuk menambah antarmuka JS lain ke WebView. */
    protected open fun onWebCreated(web: WebView) {}

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
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(request.url)

                // Hanya halaman bawaan yang dibuka di sini; tautan lain dibuka di browser.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (request.url.host == ORIGIN_HOST) return false
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    } catch (_: Exception) {
                    }
                    return true
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
        bridge.setLive(false)
        web.onPause()
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
