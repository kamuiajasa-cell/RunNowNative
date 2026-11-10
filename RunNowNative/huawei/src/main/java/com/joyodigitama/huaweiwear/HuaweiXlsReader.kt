package com.joyodigitama.huaweiwear

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A minimal reader for the old Excel format (`.xls`, an OLE2 compound file holding a BIFF8 workbook stream): enough for
 * `SportsHealth-Data.xls` of a Huawei export, which is plain text and number cells. It follows the published layouts
 * (MS-CFB for the container, MS-XLS for the records) and reads only what it needs: the sheet list, the shared string table
 * with its CONTINUE records, and LABELSST / LABEL / NUMBER / RK / MULRK cells. Formulas, formats, styles and charts are skipped.
 *
 * A damaged file throws [XlsException]; the caller decides what that costs (the importer counts the file as failed).
 */
internal object HuaweiXlsReader {
  class XlsException(message: String) : Exception(message)

  /** Cells by row then column; numbers that are whole are written without a decimal point (`20201114`, not `2.0201114E7`). */
  class Sheet(val name: String, val rows: List<Map<Int, String>>)

  private val SIGNATURE = 0xE11AB1A1E011CFD0uL.toLong()
  private const val ENDOFCHAIN = -2

  private const val BOF = 0x0809
  private const val EOF = 0x000A
  private const val BOUNDSHEET = 0x0085
  private const val SST = 0x00FC
  private const val CONTINUE = 0x003C
  private const val LABELSST = 0x00FD
  private const val LABEL = 0x0204
  private const val NUMBER = 0x0203
  private const val RK = 0x027E
  private const val MULRK = 0x00BD

  /** Largest worksheet the reader builds in memory: cells, not bytes. */
  private const val MAX_CELLS = 2_000_000

  /** The sheets of [file] whose name [wanted] accepts. */
  fun readSheets(file: ByteArray, wanted: (String) -> Boolean): List<Sheet> {
    val book = workbookStream(file)
    val strings = ArrayList<String>()
    val sheets = ArrayList<Pair<String, Int>>()
    var pos = 0
    var depth = 0
    val sst = ArrayList<ByteArray>()
    var inSst = false
    while (pos + 4 <= book.size) {
      val id = u16(book, pos)
      val len = u16(book, pos + 2)
      val bodyStart = pos + 4
      if (bodyStart + len > book.size) break
      if (inSst && id != CONTINUE) {
        parseSst(sst, strings)
        sst.clear()
        inSst = false
      }
      when (id) {
        BOF -> depth++
        EOF -> {
          depth--
          if (depth <= 0) break
        }
        BOUNDSHEET -> if (depth == 1) {
          val offset = u32(book, bodyStart).toInt()
          val nameLength = book[bodyStart + 6].toInt() and 0xFF
          val wide = (book[bodyStart + 7].toInt() and 1) != 0
          val name = if (wide) String(book, bodyStart + 8, nameLength * 2, Charsets.UTF_16LE) else String(book, bodyStart + 8, nameLength, Charsets.ISO_8859_1)
          sheets.add(name to offset)
        }
        SST -> {
          inSst = true
          sst.clear()
          sst.add(book.copyOfRange(bodyStart + 8, bodyStart + len))
        }
        CONTINUE -> if (inSst) sst.add(book.copyOfRange(bodyStart, bodyStart + len))
      }
      pos = bodyStart + len
    }
    if (inSst) parseSst(sst, strings)
    return sheets.filter { wanted(it.first) }.map { (name, offset) -> Sheet(name, readSheet(book, offset, strings)) }
  }

  // --- the compound file ---------------------------------------------------------------------------------------

  private fun workbookStream(file: ByteArray): ByteArray {
    if (file.size < 512 || ByteBuffer.wrap(file, 0, 8).order(ByteOrder.LITTLE_ENDIAN).long != SIGNATURE) throw XlsException("not an OLE2 file")
    val sectorSize = 1 shl u16(file, 0x1E)
    val miniSectorSize = 1 shl u16(file, 0x20)
    if (sectorSize !in 512..65_536 || miniSectorSize !in 32..4096) throw XlsException("bad sector size")
    val dirStart = i32(file, 0x30)
    val miniCutoff = u32(file, 0x38).toInt()
    val miniFatStart = i32(file, 0x3C)
    var difatStart = i32(file, 0x44)
    val difatCount = i32(file, 0x48)

    fun sectorOffset(index: Int): Int {
      val offset = (index.toLong() + 1) * sectorSize
      if (index < 0 || offset + sectorSize > file.size) throw XlsException("sector $index outside the file")
      return offset.toInt()
    }

    // The FAT sectors: 109 ids in the header, the rest in the DIFAT chain.
    val fatSectors = ArrayList<Int>()
    for (i in 0 until 109) {
      val id = i32(file, 0x4C + i * 4)
      if (id >= 0) fatSectors.add(id)
    }
    var difat = 0
    while (difatStart >= 0 && difat < difatCount) {
      val at = sectorOffset(difatStart)
      for (i in 0 until sectorSize / 4 - 1) {
        val id = i32(file, at + i * 4)
        if (id >= 0) fatSectors.add(id)
      }
      difatStart = i32(file, at + sectorSize - 4)
      difat++
    }
    val perSector = sectorSize / 4
    val fat = IntArray(fatSectors.size * perSector)
    for ((n, sector) in fatSectors.withIndex()) {
      val at = sectorOffset(sector)
      for (i in 0 until perSector) fat[n * perSector + i] = i32(file, at + i * 4)
    }

    fun chain(start: Int, table: IntArray): List<Int> {
      val out = ArrayList<Int>()
      var s = start
      while (s >= 0) {
        if (s >= table.size || out.size > table.size) throw XlsException("broken sector chain")
        out.add(s)
        s = table[s]
      }
      return out
    }

    fun readChain(start: Int, size: Long?): ByteArray {
      val sectors = chain(start, fat)
      val total = sectors.size.toLong() * sectorSize
      val out = ByteArray(minOf(size ?: total, total).toInt())
      var written = 0
      for (sector in sectors) {
        if (written >= out.size) break
        val n = minOf(sectorSize, out.size - written)
        System.arraycopy(file, sectorOffset(sector), out, written, n)
        written += n
      }
      return out
    }

    val directory = readChain(dirStart, null)
    var workbook: Pair<Int, Long>? = null
    var rootStart = ENDOFCHAIN
    for (at in 0 until directory.size / 128) {
      val entry = at * 128
      val nameLength = u16(directory, entry + 0x40)
      if (nameLength < 2 || nameLength > 64) continue
      val name = String(directory, entry, nameLength - 2, Charsets.UTF_16LE)
      val type = directory[entry + 0x42].toInt()
      val start = i32(directory, entry + 0x74)
      val size = u32(directory, entry + 0x78)
      if (type == 5) rootStart = start
      if (type == 2 && (name == "Workbook" || name == "Book")) workbook = start to size
    }
    val (start, size) = workbook ?: throw XlsException("no Workbook stream")
    if (size >= miniCutoff) return readChain(start, size)

    // A small stream lives in the mini stream: 64-byte sectors inside the root entry's stream, chained by the mini FAT.
    val mini = readChain(rootStart, null)
    val miniFatBytes = readChain(miniFatStart, null)
    val miniFat = IntArray(miniFatBytes.size / 4) { i32(miniFatBytes, it * 4) }
    val out = ByteArray(size.toInt())
    var written = 0
    for (sector in chain(start, miniFat)) {
      if (written >= out.size) break
      val at = sector * miniSectorSize
      if (at + miniSectorSize > mini.size) throw XlsException("mini sector $sector outside the mini stream")
      val n = minOf(miniSectorSize, out.size - written)
      System.arraycopy(mini, at, out, written, n)
      written += n
    }
    return out
  }

  // --- the shared string table ---------------------------------------------------------------------------------

  /** Walks the chunks (the SST record body, then each CONTINUE body) as one stream whose string characters may restart with a flag byte. */
  private class Chunks(val chunks: List<ByteArray>) {
    var chunk = 0
    var pos = 0

    fun hasMore(): Boolean {
      while (chunk < chunks.size && pos >= chunks[chunk].size) {
        chunk++
        pos = 0
      }
      return chunk < chunks.size
    }

    fun byte(): Int {
      if (!hasMore()) throw XlsException("string table ends early")
      return chunks[chunk][pos++].toInt() and 0xFF
    }

    fun u16(): Int = byte() or (byte() shl 8)

    fun u32(): Int = u16() or (u16() shl 16)

    fun skip(n: Int) {
      var left = n
      while (left > 0) {
        if (!hasMore()) throw XlsException("string table ends early")
        val take = minOf(left, chunks[chunk].size - pos)
        pos += take
        left -= take
      }
    }

    /** True when the next byte starts a new CONTINUE chunk (a string's characters carry a fresh flag byte there). */
    fun atChunkStart(): Boolean = pos == 0 && chunk > 0
  }

  private fun parseSst(chunks: List<ByteArray>, out: MutableList<String>) {
    val reader = Chunks(chunks)
    while (reader.hasMore()) {
      val length = reader.u16()
      val flags = reader.byte()
      var wide = (flags and 1) != 0
      val rich = (flags and 8) != 0
      val phonetic = (flags and 4) != 0
      val runs = if (rich) reader.u16() else 0
      val phoneticBytes = if (phonetic) reader.u32() else 0
      val text = StringBuilder(length)
      var remaining = length
      while (remaining > 0) {
        if (!reader.hasMore()) throw XlsException("string table ends early")
        // A string that continues in the next record starts that record with a flag byte of its own.
        if (remaining < length && reader.atChunkStart()) wide = (reader.byte() and 1) != 0
        val width = if (wide) 2 else 1
        val inChunk = (reader.chunks[reader.chunk].size - reader.pos) / width
        val take = minOf(remaining, maxOf(inChunk, 1))
        for (i in 0 until take) text.append(if (wide) reader.u16().toChar() else reader.byte().toChar())
        remaining -= take
      }
      reader.skip(runs * 4 + phoneticBytes)
      out.add(text.toString())
    }
  }

  // --- worksheets ----------------------------------------------------------------------------------------------

  private fun readSheet(book: ByteArray, offset: Int, strings: List<String>): List<Map<Int, String>> {
    val rows = ArrayList<MutableMap<Int, String>>()
    var cells = 0
    fun put(row: Int, col: Int, value: String) {
      if (++cells > MAX_CELLS) throw XlsException("worksheet too large")
      while (rows.size <= row) rows.add(HashMap())
      rows[row][col] = value
    }
    var pos = offset
    var depth = 0
    while (pos + 4 <= book.size) {
      val id = u16(book, pos)
      val len = u16(book, pos + 2)
      val at = pos + 4
      if (at + len > book.size) break
      when (id) {
        BOF -> depth++
        EOF -> {
          depth--
          if (depth <= 0) return rows
        }
        LABELSST -> if (depth == 1) strings.getOrNull(u32(book, at + 6).toInt())?.let { put(u16(book, at), u16(book, at + 2), it) }
        NUMBER -> if (depth == 1) put(u16(book, at), u16(book, at + 2), number(ByteBuffer.wrap(book, at + 6, 8).order(ByteOrder.LITTLE_ENDIAN).double))
        RK -> if (depth == 1) put(u16(book, at), u16(book, at + 2), number(rk(u32(book, at + 6))))
        MULRK -> if (depth == 1) {
          val row = u16(book, at)
          val first = u16(book, at + 2)
          val count = (len - 6) / 6
          for (i in 0 until count) put(row, first + i, number(rk(u32(book, at + 4 + i * 6 + 2))))
        }
        LABEL -> if (depth == 1) {
          val cch = u16(book, at + 6)
          val wide = (book[at + 8].toInt() and 1) != 0
          val bytes = minOf(if (wide) cch * 2 else cch, len - 9)
          if (bytes >= 0) put(u16(book, at), u16(book, at + 2), String(book, at + 9, bytes, if (wide) Charsets.UTF_16LE else Charsets.ISO_8859_1))
        }
      }
      pos = at + len
    }
    return rows
  }

  /** An RK value: a 30-bit number, either an IEEE double's high bits or an integer, optionally divided by 100. */
  private fun rk(raw: Long): Double {
    val bits = raw and 0xFFFFFFFFL
    var value = if ((bits and 2L) != 0L) {
      (bits.toInt() shr 2).toDouble()
    } else {
      java.lang.Double.longBitsToDouble((bits and 0xFFFFFFFCL) shl 32)
    }
    if ((bits and 1L) != 0L) value /= 100.0
    return value
  }

  private fun number(value: Double): String = if (value == Math.rint(value) && Math.abs(value) < 1e15) value.toLong().toString() else value.toString()

  private fun u16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

  private fun u32(b: ByteArray, at: Int): Long = u16(b, at).toLong() or (u16(b, at + 2).toLong() shl 16)

  private fun i32(b: ByteArray, at: Int): Int = u32(b, at).toInt()
}
