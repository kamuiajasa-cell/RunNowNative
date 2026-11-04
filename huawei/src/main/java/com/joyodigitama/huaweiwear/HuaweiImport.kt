package com.joyodigitama.huaweiwear

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Penanda "database jam berubah di luar sinkron" (mis. setelah impor ekspor Huawei). Halaman utama membacanya saat
 * kembali ke layar dan mengirim ulang data jam ke index.html. Dibaca dan dihapus oleh [take].
 */
object WatchDataSignal {
  @Volatile
  private var pending = false

  fun mark() {
    pending = true
  }

  fun take(): Boolean {
    val was = pending
    pending = false
    return was
  }
}

/**
 * Satu impor sekali jalan: membuka ZIP ekspor Privacy Centre Huawei, menjalankan [HuaweiExportImporter] ke database jam
 * (`joyofit.db`) dan melaporkan progres lewat [emit]. Kata sandi disimpan di `CharArray` yang dihapus saat impor selesai;
 * tidak disimpan dan tidak dicatat di log.
 */
internal class HuaweiImportController(
  private val context: Context,
  private val syncStore: SyncStore,
  private val emit: (Map<String, Any?>) -> Unit,
) {
  private val running = AtomicBoolean(false)

  @Volatile
  private var cancelRequested = false

  val isRunning: Boolean get() = running.get()

  fun cancel() {
    cancelRequested = true
  }

  fun displayName(uri: String): String? = HuaweiExportFiles.displayName(context, uri)

  /** Menjalankan impor; melempar [IllegalStateException] berbahasa Indonesia bila gagal. */
  suspend fun run(uri: String, password: String): Map<String, Any?> = withContext(Dispatchers.IO) {
    if (!running.compareAndSet(false, true)) throw IllegalStateException("Impor lain sedang berjalan.")
    cancelRequested = false
    val chars = password.toCharArray()
    try {
      HuaweiExportFiles.open(context, uri, chars).use { opened ->
        val importer = HuaweiExportImporter(
          store = SqliteHuaweiImportStore { syncStore.db() },
          onProgress = { emit(it.toJs()) },
          isCancelled = { cancelRequested },
        )
        importer.run(opened.source).toJs()
      }
    } catch (e: HuaweiImportException) {
      throw IllegalStateException(
        when (e.kind) {
          HuaweiImportException.Kind.BAD_PASSWORD -> "Kata sandi ZIP salah."
          HuaweiImportException.Kind.NOT_A_ZIP -> "Berkas ini tidak bisa dibuka sebagai ZIP (${e.message})."
          HuaweiImportException.Kind.NOT_AN_EXPORT -> "ZIP ini bukan ekspor data Huawei Health (${e.message})."
          HuaweiImportException.Kind.STORAGE -> "Penyimpanan bermasalah: ${e.message}"
        },
        e,
      )
    } finally {
      chars.fill('\u0000')
      running.set(false)
    }
  }
}
