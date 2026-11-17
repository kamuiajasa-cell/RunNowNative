package com.runnow.app.data

import android.content.Context
import com.joyodigitama.huaweiwear.WatchImport
import com.runnow.data.Codec
import com.runnow.data.WatchImportData
import com.runnow.data.WatchProfile
import com.runnow.data.WatchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sumber data jam: memanggil modul huawei langsung (tanpa WebView/RPC). [WatchImport.build] membaca database jam
 * dan menyusun `{wellness, workouts}` yang sama seperti yang dulu dikirim ke `window.nativeImport`; hasilnya
 * diurai [Codec.parseWatchImport]. [profileProvider] diisi nanti dengan profil dari WearRuntime (Langkah 7/10);
 * selama belum, profil jam tidak dipakai. BELUM DIUJI di perangkat.
 */
class HuaweiWatchSource(
    private val context: Context,
    private val profileProvider: () -> WatchProfile? = { null },
) : WatchSource {
    override suspend fun watchData(): WatchImportData? =
        withContext(Dispatchers.IO) {
            val r = WatchImport.build(context) ?: return@withContext null
            Codec.parseWatchImport(r.json.toString())
        }

    override fun profile(): WatchProfile? = profileProvider()
}
