package com.runnow.app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.webkit.WebViewAssetLoader
import com.joyodigitama.huaweiwear.HuaweiWatch
import com.joyodigitama.huaweiwear.RunNowBridge
import com.joyodigitama.huaweiwear.RunNowHost
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Satu-satunya layar utama: index.html di dalam WebView, ditambah jembatan ke Huawei core. */
class MainActivity : ComponentActivity() {
    private lateinit var web: WebView
    private lateinit var bridge: RunNowBridge
    private var pending: CompletableDeferred<Unit>? = null

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pending?.complete(Unit)
    }

    private val host = object : RunNowHost {
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

        override fun open(route: String): Boolean = when (route) {
            "/settings/koneksi", "/watch" -> {
                runOnUiThread { startActivity(Intent(this@MainActivity, PairingActivity::class.java)) }
                true
            }
            else -> false
        }

        override fun eval(js: String) {
            runOnUiThread { if (::web.isInitialized) web.evaluateJavascript(js, null) }
        }
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
            // `rn(...)` di index.html memanggil window.ReactNativeWebView.postMessage, jadi jembatan memakai nama itu.
            addJavascriptInterface(bridge, "ReactNativeWebView")
            // Notifikasi pengingat: Android.setPlan dan Android.test.
            addJavascriptInterface(AndroidBridge(applicationContext) { askNotificationPermission() }, "Android")
        }
        setContentView(web)
        web.loadUrl("https://$ORIGIN_HOST/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // index.html menjawab true kalau ia sendiri yang menangani Back (tutup dialog, kembali ke tab sebelumnya).
                web.evaluateJavascript("window.nativeBack?window.nativeBack():false") { r ->
                    if (r != "true") moveTaskToBack(true)
                }
            }
        })
    }

    private fun askNotificationPermission() {
        runOnUiThread {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        bridge.setLive(true)
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

    private companion object {
        const val ORIGIN_HOST = "runnow.local"
    }
}
