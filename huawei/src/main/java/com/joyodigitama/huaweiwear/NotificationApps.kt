package com.joyodigitama.huaweiwear

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** The app chooser's data: launcher apps, whether their notifications are forwarded, and a cached icon file. */
internal object NotificationApps {
  private const val ICON_PX = 96

  suspend fun list(context: Context, settings: WearSettings): List<Map<String, Any?>> = coroutineScope {
    val pm = context.packageManager
    val cache = AppIconCache(File(context.cacheDir, "notification-app-icons"))
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val seen = HashSet<String>()
    val found = ArrayList<ApplicationInfo>()
    for (resolved in launcherActivities(pm, launcher)) {
      val info = resolved.activityInfo?.applicationInfo ?: continue
      if (info.packageName == context.packageName || !seen.add(info.packageName)) continue
      found.add(info)
    }
    // Cache hits are a file check; misses render in parallel.
    found
      .map { info ->
        async {
          val packageName = info.packageName
          val label = pm.getApplicationLabel(info).toString().ifBlank { packageName }
          val enabled = NotificationPolicy.appAllowed(settings.appChoice(packageName), NotificationPolicy.isSystemApp(info.flags))
          label.lowercase() to mapOf(
            "packageName" to packageName,
            "label" to label,
            "enabled" to enabled,
            "iconUri" to iconUri(pm, cache, packageName),
          )
        }
      }
      .awaitAll()
      .sortedBy { it.first }
      .map { it.second }
  }

  /** `ResolveInfoFlags` exists from Android 13 (API 33); the overload with an int is what Android 12 and below have. */
  @Suppress("DEPRECATION")
  private fun launcherActivities(pm: PackageManager, launcher: Intent): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
    } else {
      pm.queryIntentActivities(launcher, 0)
    }

  private fun iconUri(pm: PackageManager, cache: AppIconCache, packageName: String): String? =
    try {
      val updated = pm.getPackageInfo(packageName, 0).lastUpdateTime
      val file = cache.cached(packageName, updated)
        ?: cache.store(packageName, updated) { out -> render(pm.getApplicationIcon(packageName), out) }
      file?.let { Uri.fromFile(it).toString() }
    } catch (_: Exception) {
      null
    }

  /** Draws any drawable (adaptive icons included) into a square ICON_PX bitmap and writes it as PNG. */
  private fun render(icon: Drawable, out: File): Boolean {
    val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
    return try {
      // Fresh copy: the drawable's bounds are shared and several renders run in parallel.
      val drawable = icon.constantState?.newDrawable()?.mutate() ?: icon
      drawable.setBounds(0, 0, ICON_PX, ICON_PX)
      drawable.draw(Canvas(bitmap))
      FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } finally {
      bitmap.recycle()
    }
  }
}
