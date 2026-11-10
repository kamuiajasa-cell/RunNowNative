package com.joyodigitama.huaweiwear

import java.io.File

/**
 * Disk cache of rendered app icons, one PNG per package and install version. The file name carries the package and
 * its `lastUpdateTime`, so an updated app gets a new file and the old one is deleted.
 */
internal class AppIconCache(private val dir: File) {
  fun fileFor(packageName: String, lastUpdateTime: Long): File = File(dir, fileName(packageName, lastUpdateTime))

  /** The cached icon, or null when it has to be rendered. Empty files (interrupted writes) count as missing. */
  fun cached(packageName: String, lastUpdateTime: Long): File? =
    fileFor(packageName, lastUpdateTime).takeIf { it.isFile && it.length() > 0 }

  /** Writes through a temp file so a reader never sees half a PNG, then drops older versions of the package. */
  fun store(packageName: String, lastUpdateTime: Long, write: (File) -> Boolean): File? {
    dir.mkdirs()
    val target = fileFor(packageName, lastUpdateTime)
    val tmp = File(dir, "${target.name}.${Thread.currentThread().id}.tmp")
    val ok = try {
      write(tmp) && tmp.length() > 0 && tmp.renameTo(target)
    } catch (_: Exception) {
      false
    }
    if (!ok) {
      tmp.delete()
      return null
    }
    dir.listFiles()?.forEach { f ->
      if (f != target && f.name.startsWith(prefix(packageName)) && f.name.endsWith(SUFFIX)) f.delete()
    }
    return target
  }

  companion object {
    const val SUFFIX = ".png"

    private fun prefix(packageName: String) = packageName.replace(Regex("[^A-Za-z0-9._]"), "_") + "@"

    fun fileName(packageName: String, lastUpdateTime: Long) = prefix(packageName) + lastUpdateTime + SUFFIX
  }
}
