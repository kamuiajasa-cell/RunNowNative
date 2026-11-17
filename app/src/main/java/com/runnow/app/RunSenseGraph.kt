package com.runnow.app.data

import android.content.Context
import com.runnow.data.IcuHttp
import com.runnow.data.OpenMeteoHttp
import com.runnow.data.RunSenseRepository
import com.runnow.data.WatchProfile
import java.io.File
import java.time.ZoneId

/**
 * Merakit RunSenseRepository untuk aplikasi: berkas di filesDir/runsense, HttpURLConnection, jam Huawei, dan lokasi
 * perangkat. Dipanggil sekali (mis. dari ViewModel di Langkah 7). BELUM DIUJI di perangkat.
 */
object RunSenseGraph {
    fun create(context: Context, profileProvider: () -> WatchProfile? = { null }): RunSenseRepository {
        val app = context.applicationContext
        val http = UrlConnectionHttpClient()
        var repo: RunSenseRepository? = null
        val icu = IcuHttp(http) { repo?.apiKey }
        val r = RunSenseRepository(
            store = FileKeyValueStore(File(app.filesDir, "runsense")),
            icu = icu,
            weather = OpenMeteoHttp(http),
            watch = HuaweiWatchSource(app, profileProvider),
            locator = AndroidLocator(app),
            clock = { System.currentTimeMillis() },
            zone = ZoneId.systemDefault(),
        )
        repo = r
        return r
    }
}
