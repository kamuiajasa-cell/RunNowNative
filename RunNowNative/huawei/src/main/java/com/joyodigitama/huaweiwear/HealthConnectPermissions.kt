package com.joyodigitama.huaweiwear

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.PermissionController

/**
 * Permintaan izin Health Connect (kontrak resmi [PermissionController.createRequestPermissionResultContract]) dalam bentuk
 * yang bisa didaftarkan Activity di modul `app`, yang tidak melihat pustaka Health Connect. Masukan: izin yang diminta;
 * keluaran: izin yang sekarang diberikan. Dipanggil lewat [RunNowHost.requestHealthConnect].
 */
class HealthConnectPermissions : ActivityResultContract<Set<String>, Set<String>>() {
  private val inner = PermissionController.createRequestPermissionResultContract()

  override fun createIntent(context: Context, input: Set<String>): Intent = inner.createIntent(context, input)

  override fun parseResult(resultCode: Int, intent: Intent?): Set<String> = inner.parseResult(resultCode, intent)
}
