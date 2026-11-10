package com.joyodigitama.huaweiwear

/**
 * Reverse-engineered bitmap and RLE decoder for Huawei watch face binaries (`com.huawei.watchface` / `watchface.bin`).
 * Extracts embedded ARGB8888, RGB565, or RGB24 textures and converts them into standard 32-bit BMP byte arrays
 * for runtime preview extraction without external dependencies.
 */
internal object WatchfaceBitmapDecoder {
  /**
   * Reverse-engineered extractor for watch faces that lack preview/cover.jpg.
   * Scans com.huawei.watchface binary for full-screen background bitmaps
   * compressed using Huawei RLE and converts them to standard 32-bit BMP.
   */
  fun extractPreviewFromBinary(data: ByteArray, targetW: Int = 194, targetH: Int = 368): Pair<ByteArray, String>? {
    try {
      // 1. Primary: check if data is a Huawei Watchface binary with an image table.
      // In Huawei Theme Studio, slot 0 in the table (Image 001) is the official composited preview thumbnail.
      if (data.size >= 16) {
        val layoutLen = (data[2].toInt() and 0xFF) or ((data[3].toInt() and 0xFF) shl 8)
        val tableLen = (data[4].toInt() and 0xFF) or
          ((data[5].toInt() and 0xFF) shl 8) or
          ((data[6].toInt() and 0xFF) shl 16) or
          ((data[7].toInt() and 0xFF) shl 24)
        val tableStart = 16 + layoutLen
        val binStart = tableStart + tableLen

        if (tableLen >= 8 && binStart <= data.size) {
          val off0 = (data[tableStart].toInt() and 0xFF) or
            ((data[tableStart + 1].toInt() and 0xFF) shl 8) or
            ((data[tableStart + 2].toInt() and 0xFF) shl 16) or
            ((data[tableStart + 3].toInt() and 0xFF) shl 24)
          val size0 = (data[tableStart + 4].toInt() and 0xFF) or
            ((data[tableStart + 5].toInt() and 0xFF) shl 8) or
            ((data[tableStart + 6].toInt() and 0xFF) shl 16) or
            ((data[tableStart + 7].toInt() and 0xFF) shl 24)

          val imgStart = binStart + off0
          if (size0 >= 8 && imgStart + size0 <= data.size) {
            val previewBmp = decodeBlobToBmp(data, imgStart, size0)
            if (previewBmp != null) {
              return previewBmp to "image/bmp"
            }
          }
        }
      }

      // 2. Secondary: scan the binary for the best full-screen or preview thumbnail bitmap
      var bestOffset = -1
      var bestScore = -1

      var i = 0
      val limit = data.size - 8
      while (i <= limit) {
        if (data[i] == 0x45.toByte() && data[i + 1] == 0x23.toByte()) {
          val formatCode = (data[i + 2].toInt() and 0xFF) or ((data[i + 3].toInt() and 0xFF) shl 8)
          if (formatCode == 0x8888 || formatCode == 0xF565) {
            val w = (data[i + 4].toInt() and 0xFF) or ((data[i + 5].toInt() and 0xFF) shl 8)
            val h = (data[i + 6].toInt() and 0xFF) or ((data[i + 7].toInt() and 0xFF) shl 8)
            val isExact = (w == targetW && h == targetH)
            val isPreviewThumb = (w in 120..260 && h in 200..320)
            val isAcceptable = (w in 120..600 && h in 160..800)
            if (isExact || isPreviewThumb || isAcceptable) {
              val score = when {
                isExact -> 10_000_000 + w * h
                isPreviewThumb -> 5_000_000 + w * h
                else -> w * h
              }
              if (score > bestScore) {
                bestScore = score
                bestOffset = i
              }
            }
          }
          i += 8
        } else {
          i++
        }
      }

      if (bestOffset < 0) return null
      val bmp = decodeBlobToBmp(data, bestOffset, data.size - bestOffset) ?: return null
      return bmp to "image/bmp"
    } catch (_: Exception) {
      return null
    }
  }

  fun decodeBlobToBmp(data: ByteArray, offset: Int, size: Int): ByteArray? {
    if (size < 8 || offset + size > data.size) return null
    if (data[offset] != 0x45.toByte() || data[offset + 1] != 0x23.toByte()) return null

    val formatCode = (data[offset + 2].toInt() and 0xFF) or ((data[offset + 3].toInt() and 0xFF) shl 8)
    val w = (data[offset + 4].toInt() and 0xFF) or ((data[offset + 5].toInt() and 0xFF) shl 8)
    val h = (data[offset + 6].toInt() and 0xFF) or ((data[offset + 7].toInt() and 0xFF) shl 8)
    if (w <= 0 || h <= 0 || w > 2000 || h > 2000) return null

    val totalPixels = w * h
    val rawPixels = ByteArray(totalPixels * 4) // Stores BGRA row-major

    val end = offset + size
    var streamIdx = offset + 8
    var px = 0

    when (formatCode) {
      0x8888 -> {
        // ARGB8888: 4 bytes per pixel (Huawei stores B, G, R, A)
        while (px < totalPixels && streamIdx < end) {
          if (streamIdx + 12 <= end &&
            data[streamIdx] == 0x89.toByte() && data[streamIdx + 1] == 0x67.toByte() &&
            data[streamIdx + 2] == 0x45.toByte() && data[streamIdx + 3] == 0x23.toByte()
          ) {
            val b = data[streamIdx + 4]
            val g = data[streamIdx + 5]
            val r = data[streamIdx + 6]
            val a = data[streamIdx + 7]
            val count = (data[streamIdx + 8].toInt() and 0xFF) or
              ((data[streamIdx + 9].toInt() and 0xFF) shl 8) or
              ((data[streamIdx + 10].toInt() and 0xFF) shl 16) or
              ((data[streamIdx + 11].toInt() and 0xFF) shl 24)
            streamIdx += 12

            for (c in 0 until count) {
              if (px >= totalPixels) break
              val o = px * 4
              rawPixels[o] = b
              rawPixels[o + 1] = g
              rawPixels[o + 2] = r
              rawPixels[o + 3] = a
              px++
            }
          } else {
            if (streamIdx + 4 > end) break
            val o = px * 4
            rawPixels[o] = data[streamIdx]
            rawPixels[o + 1] = data[streamIdx + 1]
            rawPixels[o + 2] = data[streamIdx + 2]
            rawPixels[o + 3] = data[streamIdx + 3]
            streamIdx += 4
            px++
          }
        }
      }
      0xF565 -> {
        // RGB565: 2 bytes per pixel, packed pairs in RLE
        while (px < totalPixels && streamIdx < end) {
          if (streamIdx + 12 <= end &&
            data[streamIdx] == 0x89.toByte() && data[streamIdx + 1] == 0x67.toByte() &&
            data[streamIdx + 2] == 0x45.toByte() && data[streamIdx + 3] == 0x23.toByte()
          ) {
            val p1 = (data[streamIdx + 4].toInt() and 0xFF) or ((data[streamIdx + 5].toInt() and 0xFF) shl 8)
            val p2 = (data[streamIdx + 6].toInt() and 0xFF) or ((data[streamIdx + 7].toInt() and 0xFF) shl 8)
            val pairs = (data[streamIdx + 8].toInt() and 0xFF) or
              ((data[streamIdx + 9].toInt() and 0xFF) shl 8) or
              ((data[streamIdx + 10].toInt() and 0xFF) shl 16) or
              ((data[streamIdx + 11].toInt() and 0xFF) shl 24)
            streamIdx += 12

            for (c in 0 until pairs) {
              if (px >= totalPixels) break
              write565ToBgra(p1, rawPixels, px++)
              if (px < totalPixels) write565ToBgra(p2, rawPixels, px++)
            }
          } else {
            if (streamIdx + 2 > end) break
            val p1 = (data[streamIdx].toInt() and 0xFF) or ((data[streamIdx + 1].toInt() and 0xFF) shl 8)
            streamIdx += 2
            if (px < totalPixels) write565ToBgra(p1, rawPixels, px++)
          }
        }
      }
      0x8808 -> {
        // RGB24: 3 bytes per pixel (B, G, R)
        while (px < totalPixels && streamIdx < end) {
          if (streamIdx + 9 <= end &&
            data[streamIdx] == 0x89.toByte() && data[streamIdx + 1] == 0x67.toByte() &&
            data[streamIdx + 2] == 0x45.toByte()
          ) {
            val b = data[streamIdx + 3]
            val g = data[streamIdx + 4]
            val r = data[streamIdx + 5]
            val count = (data[streamIdx + 6].toInt() and 0xFF) or
              ((data[streamIdx + 7].toInt() and 0xFF) shl 8) or
              ((data[streamIdx + 8].toInt() and 0xFF) shl 16)
            streamIdx += 9

            for (c in 0 until count) {
              if (px >= totalPixels) break
              val o = px * 4
              rawPixels[o] = b
              rawPixels[o + 1] = g
              rawPixels[o + 2] = r
              rawPixels[o + 3] = 0xFF.toByte()
              px++
            }
          } else {
            if (streamIdx + 3 > end) break
            val o = px * 4
            rawPixels[o] = data[streamIdx]
            rawPixels[o + 1] = data[streamIdx + 1]
            rawPixels[o + 2] = data[streamIdx + 2]
            rawPixels[o + 3] = 0xFF.toByte()
            streamIdx += 3
            px++
          }
        }
      }
      else -> return null
    }

    if (px < totalPixels) return null
    return encodeBottomUpBmp(rawPixels, w, h)
  }

  private fun write565ToBgra(p: Int, rawPixels: ByteArray, px: Int) {
    val r = (((p shr 11) and 0x1F) * 255 / 31).toByte()
    val g = (((p shr 5) and 0x3F) * 255 / 63).toByte()
    val b = ((p and 0x1F) * 255 / 31).toByte()
    val o = px * 4
    rawPixels[o] = b
    rawPixels[o + 1] = g
    rawPixels[o + 2] = r
    rawPixels[o + 3] = 0xFF.toByte()
  }

  private fun encodeBottomUpBmp(rawPixels: ByteArray, w: Int, h: Int): ByteArray {
    val pixelBytes = w * h * 4
    val fileSize = 54 + pixelBytes
    val bmp = ByteArray(fileSize)
    bmp[0] = 'B'.code.toByte()
    bmp[1] = 'M'.code.toByte()
    bmp[2] = (fileSize and 0xFF).toByte()
    bmp[3] = ((fileSize shr 8) and 0xFF).toByte()
    bmp[4] = ((fileSize shr 16) and 0xFF).toByte()
    bmp[5] = ((fileSize shr 24) and 0xFF).toByte()
    bmp[10] = 54
    bmp[14] = 40
    bmp[18] = (w and 0xFF).toByte()
    bmp[19] = ((w shr 8) and 0xFF).toByte()
    bmp[20] = ((w shr 16) and 0xFF).toByte()
    bmp[21] = ((w shr 24) and 0xFF).toByte()
    bmp[22] = (h and 0xFF).toByte()
    bmp[23] = ((h shr 8) and 0xFF).toByte()
    bmp[24] = ((h shr 16) and 0xFF).toByte()
    bmp[25] = ((h shr 24) and 0xFF).toByte()
    bmp[26] = 1
    bmp[28] = 32
    bmp[34] = (pixelBytes and 0xFF).toByte()
    bmp[35] = ((pixelBytes shr 8) and 0xFF).toByte()
    bmp[36] = ((pixelBytes shr 16) and 0xFF).toByte()
    bmp[37] = ((pixelBytes shr 24) and 0xFF).toByte()

    val rowBytes = w * 4
    var pOut = 54
    for (y in h - 1 downTo 0) {
      val rowStart = y * rowBytes
      System.arraycopy(rawPixels, rowStart, bmp, pOut, rowBytes)
      pOut += rowBytes
    }
    return bmp
  }
}
