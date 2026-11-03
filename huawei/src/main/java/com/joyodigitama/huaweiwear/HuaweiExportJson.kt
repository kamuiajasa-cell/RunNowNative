// The Huawei Privacy Centre export format is undocumented. The layout and quirks handled here come from reading what
// community converters report about real exports: m1ty/huawei2gpx, JordyThien/Huawei-Health-Importer and
// driemekasten/hhealth2zepp (all MIT; see NOTICE). No code was copied from them or from Hitrava (NPOSL).
package com.joyodigitama.huaweiwear

import java.io.Reader
import kotlin.math.abs
import org.json.JSONException
import org.json.JSONObject

/**
 * Streaming access to the JSON files of a Huawei export. The files are one big array (or one object) of records, some
 * tens of MB, and a record can be invalid JSON. So the file is cut into top-level `{...}` records by a string-aware
 * brace counter, and every record is parsed (and, if need be, repaired) on its own: one bad record costs only itself.
 */
internal object HuaweiExportJson {
  /** A record longer than this many characters is skipped instead of held in memory. */
  const val MAX_RECORD_CHARS = 48_000_000

  /** Huawei writes some maps with bare numeric keys: `{1.0:439.0,2.0:451.0}`. */
  private val UNQUOTED_NUMERIC_KEY = Regex("""([{,])\s*(-?\d+(?:\.\d+)?)\s*:""")

  /**
   * Reads [reader] to its end and calls [onRecord] with the text of every top-level object, in order. Anything outside
   * objects (the array brackets, commas, a BOM) is ignored. An object still open at the end of the input (a stray quote
   * made the counter lose track) goes to [onUnterminated]; one over [MAX_RECORD_CHARS] is counted by [onOversized].
   */
  fun forEachRecord(
    reader: Reader,
    onRecord: (String) -> Unit,
    onUnterminated: (String) -> Unit,
    onOversized: () -> Unit,
    maxRecordChars: Int = MAX_RECORD_CHARS,
  ) {
    val buffer = CharArray(16 * 1024)
    val record = StringBuilder()
    var depth = 0
    var inString = false
    var escaped = false
    // False while skipping a record that grew past the limit.
    var keeping = true
    while (true) {
      val n = reader.read(buffer)
      if (n < 0) break
      for (i in 0 until n) {
        val c = buffer[i]
        if (depth == 0) {
          if (c == '{') {
            depth = 1
            inString = false
            escaped = false
            keeping = true
            record.setLength(0)
            record.append(c)
          }
          continue
        }
        if (keeping) {
          record.append(c)
          if (record.length > maxRecordChars) {
            keeping = false
            record.setLength(0)
          }
        }
        if (inString) {
          when {
            escaped -> escaped = false
            c == '\\' -> escaped = true
            c == '"' -> inString = false
          }
          continue
        }
        when (c) {
          '"' -> inString = true
          '{', '[' -> depth++
          '}', ']' -> {
            depth--
            if (depth == 0) {
              if (keeping) onRecord(record.toString()) else onOversized()
              record.setLength(0)
            }
          }
        }
      }
    }
    if (depth > 0) {
      if (keeping) onUnterminated(record.toString()) else onOversized()
    }
  }

  /** Parses one record; retries once with bare numeric keys quoted. Null when the record is not usable JSON. */
  fun parseObject(text: String): JSONObject? {
    try {
      return JSONObject(text)
    } catch (e: JSONException) {
      // fall through to the repair
    }
    return try {
      JSONObject(UNQUOTED_NUMERIC_KEY.replace(text) { "${it.groupValues[1]}\"${it.groupValues[2]}\":" })
    } catch (e: JSONException) {
      null
    }
  }

  /** A JSON number, or a number written as a string (`"82.0"`); null for anything else. */
  fun number(value: Any?): Double? = when (value) {
    is Number -> value.toDouble()
    is String -> value.trim().toDoubleOrNull()
    else -> null
  }?.takeIf { it.isFinite() }

  /** An object, or an object written as a JSON string (Huawei embeds `{"avgSaturation":97}` in `value`). */
  fun embeddedObject(value: Any?): JSONObject? = when (value) {
    is JSONObject -> value
    is String -> value.trim().takeIf { it.startsWith("{") }?.let { parseObject(it) }
    else -> null
  }

  private const val MIN_EPOCH_MS = 946_684_800_000L // 2000-01-01
  private const val MAX_EPOCH_MS = 4_102_444_800_000L // 2100-01-01

  /** A unix time in milliseconds (or, for values below 1e11, in seconds) as milliseconds; null outside 2000..2100. */
  fun epochMs(value: Any?): Long? {
    val v = number(value) ?: return null
    val ms = if (abs(v) < 1e11) v * 1000.0 else v
    val rounded = ms.toLong()
    return rounded.takeIf { it in MIN_EPOCH_MS..MAX_EPOCH_MS }
  }
}
