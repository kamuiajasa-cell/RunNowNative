package com.joyodigitama.huaweiwear

import android.app.Activity
import android.os.Bundle
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView

/**
 * What Health Connect shows when the user taps the privacy-policy link of its permission dialog
 * (Android 13 and older) or "View permission usage" (Android 14+, through the manifest alias).
 * Plain framework views: it is not part of the React app and needs no theme library.
 */
internal class HealthConnectRationaleActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val pad = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, resources.displayMetrics).toInt()
    val text = TextView(this).apply {
      setPadding(pad, pad * 2, pad, pad)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
      this.text = RATIONALE
    }
    setContentView(ScrollView(this).apply { addView(text) })
  }

  private companion object {
    const val RATIONALE = "Run Now menyalin data yang dicatat jam Huawei Anda (langkah, jarak, energi aktif, " +
      "detak jantung, detak istirahat, oksigen darah, tidur, dan latihan) ke Health Connect, supaya aplikasi " +
      "lain yang Anda izinkan bisa membacanya.\n\n" +
      "Run Now hanya menulis ke Health Connect dan tidak pernah membaca data aplikasi lain. Menulis ke Health " +
      "Connect tidak mengirim apa pun lewat internet, data tidak dibagikan ke siapa pun, dan tidak dipakai " +
      "untuk iklan. Anda bisa mematikan salinan ini di Pengaturan > Health Connect dalam Run Now, dan menghapus " +
      "data yang sudah ditulis langsung dari aplikasi Health Connect."
  }
}
