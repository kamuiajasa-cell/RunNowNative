package com.joyodigitama.huaweiwear

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * `SportsHealth data & desciption/SportsHealth-Data.xls` (the typo is Huawei's): one workbook with 77 sheets, most of them
 * empty or about the account (devices, privacy choices, medals). The one JoyoFit reads is `10-user sport daily statistics`:
 * a row per day, `recordDay` (`20201114`), `timeZone` (`+0700`, with a leading tab) and `sportBasicInfoData`
 * (`{"steps":1711,"distance":1087,"calorie":66726,"duration":47,...}`, distance in metres, calories in small calories).
 * It reaches days the per-minute files do not (a real export: 757 days here, 447 there), so each day becomes an
 * [ImportedDayTotal]; the store writes what the day's minute rows fall short of as one row from noon.
 * Sheets about weight, goals and best records hold the account's current values, not history, and are not read.
 */
internal object HuaweiSportsHealthParser {
  private const val MAX_STEPS = 200_000
  private const val MAX_METRES = 300_000
  private const val MAX_CALORIES = 30_000_000

  fun parse(file: ByteArray, out: HuaweiImportCollector) {
    val sheets = HuaweiXlsReader.readSheets(file) { it.contains("sport daily statistics", ignoreCase = true) }
    for (sheet in sheets) {
      val header = sheet.rows.firstOrNull() ?: continue
      val dayCol = header.entries.firstOrNull { it.value.trim() == "recordDay" }?.key ?: continue
      val zoneCol = header.entries.firstOrNull { it.value.trim() == "timeZone" }?.key
      val dataCol = header.entries.firstOrNull { it.value.trim() == "sportBasicInfoData" }?.key ?: continue
      for (row in sheet.rows.drop(1)) {
        val day = row[dayCol]?.trim() ?: continue
        val info = HuaweiExportJson.embeddedObject(row[dataCol]?.trim())
        if (info == null) {
          out.rejected("sportBasicInfoData", "no-number", HuaweiHealthDetailParser.sampleOf(row[dataCol]))
          continue
        }
        parseDay(day, row[zoneCol ?: -1], info, out)
      }
    }
  }

  private fun parseDay(day: String, zone: String?, info: JSONObject, out: HuaweiImportCollector) {
    val date = try {
      LocalDate.of(day.substring(0, 4).toInt(), day.substring(4, 6).toInt(), day.substring(6, 8).toInt())
    } catch (e: RuntimeException) {
      out.rejected("recordDay", "no-time", day.take(20))
      return
    }
    val steps = HuaweiExportJson.number(info.opt("steps"))?.roundToInt() ?: 0
    val distance = HuaweiExportJson.number(info.opt("distance"))?.roundToInt() ?: 0
    val calories = HuaweiExportJson.number(info.opt("calorie"))?.roundToInt() ?: 0
    if (steps !in 0..MAX_STEPS || distance !in 0..MAX_METRES || calories !in 0..MAX_CALORIES) {
      out.rejected("sportBasicInfoData", "out-of-range", "$steps steps/$distance m/$calories cal")
      return
    }
    if (steps == 0 && distance == 0 && calories == 0) return
    val offset = offsetOf(zone, date)
    val start = date.atStartOfDay().toEpochSecond(offset)
    out.dayTotal(ImportedDayTotal(start, start + 86_400, start + 43_200, steps, distance, calories))
  }

  /** `+0700` as a zone offset; the phone's own zone on that day when the cell is empty or odd. */
  private fun offsetOf(zone: String?, date: LocalDate): ZoneOffset {
    val text = zone?.filter { it == '+' || it == '-' || it.isDigit() }.orEmpty()
    if (text.length == 5 && (text[0] == '+' || text[0] == '-')) {
      val hours = text.substring(1, 3).toIntOrNull()
      val minutes = text.substring(3, 5).toIntOrNull()
      if (hours != null && minutes != null && hours <= 18 && minutes < 60) {
        val seconds = (hours * 3600 + minutes * 60) * if (text[0] == '-') -1 else 1
        return ZoneOffset.ofTotalSeconds(seconds)
      }
    }
    return ZoneId.systemDefault().rules.getOffset(Instant.ofEpochSecond(date.toEpochDay() * 86_400))
  }
}
