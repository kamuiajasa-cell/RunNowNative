package com.joyodigitama.huaweiwear

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Satu latihan yang disimpan jam. Waktu dalam detik unix, durasi dalam detik, jarak dalam meter. */
data class WatchWorkout(
  val id: Long,
  val startSec: Long,
  val endSec: Long,
  val sportType: Int,
  val sportName: String,
  val durationSec: Int,
  val distanceM: Int,
  val calories: Int,
  val avgHr: Int?,
  val maxHr: Int?,
  val steps: Int,
  val complete: Boolean,
)

/** Satu titik rekaman latihan (HR per titik, GPS bila ada). */
data class WatchWorkoutPoint(
  val tsSec: Long,
  val lat: Double?,
  val lon: Double?,
  val alt: Double?,
  val hr: Int?,
  val speed: Double?,
  val cadence: Int?,
)

/** Satu sampel detak jantung dari rekaman harian jam. */
data class WatchHrSample(val tsSec: Long, val hr: Int)

/** Ringkasan satu hari (tanggal lokal `YYYY-MM-DD`). */
data class WatchDay(
  val date: String,
  val steps: Int,
  val calories: Int,
  val distanceM: Int,
  /** Nilai HR istirahat terendah yang dicatat jam hari itu. */
  val restingHr: Int?,
  val avgHr: Int?,
  val minHr: Int?,
  val maxHr: Int?,
  val spo2Avg: Int?,
)

/** Satu sesi tidur (potongan yang berjarak kurang dari 3 jam digabung). Waktu dalam detik unix. */
data class WatchSleepNight(
  val startSec: Long,
  val endSec: Long,
  /** Total tidur tanpa menit terjaga. */
  val asleepSec: Long,
  val deepSec: Long,
  val lightSec: Long,
  val remSec: Long,
  val awakeSec: Long,
  val score: Int?,
  val efficiency: Int?,
)

/** Status satu metrik kesehatan: apakah jam mendukungnya dan berapa baris yang sudah tersimpan. */
data class WatchMetricStatus(
  val type: String,
  val title: String,
  val supported: Boolean,
  val rows: Int,
  val syncedAtSec: Long?,
  val note: String?,
)

/** Satu nilai metrik kesehatan; [valuesJson] memuat semua nilai mentah dari jam. */
data class WatchMetricPoint(val tsSec: Long, val value: Double?, val valuesJson: String)

/** Satu pengukuran HRV. [source] misalnya `night` atau `spot`. */
data class WatchHrv(
  val startSec: Long,
  val endSec: Long,
  val source: String,
  val rmssd: Double,
  val sdnn: Double?,
  val beats: Int,
)

data class WatchStoredDevice(
  val address: String,
  val name: String,
  val model: String?,
  val software: String?,
  val lastSyncSec: Long?,
)

/** Isi database jam sekilas. */
data class WatchDataOverview(
  val hrSamples: Int,
  val workouts: Int,
  val sleepSegments: Int,
  val healthMetricRows: Int,
  val hrvReadings: Int,
  val firstSampleSec: Long?,
  val lastSampleSec: Long?,
  val devices: List<WatchStoredDevice>,
)

/**
 * Pembaca data jam untuk aplikasi (hanya baca). Data ditulis oleh [HuaweiWatch.sync] ke
 * `filesDir/SQLite/joyofit.db`; kelas ini membukanya dengan koneksi baca-saja, jadi tidak mengganggu penulisan.
 * Semua fungsi bersifat blocking: panggil dari thread latar (mis. `Dispatchers.IO`). Bila database belum ada atau
 * gagal dibaca, hasilnya kosong dan [lastError] berisi sebabnya.
 */
class WatchData(context: Context) {
  private val file = File(File(context.applicationContext.filesDir, "SQLite"), "joyofit.db")

  @Volatile
  var lastError: String? = null
    private set

  /** True bila jam pernah disinkronkan (file database ada). */
  fun available(): Boolean = file.exists()

  private fun <T> read(fallback: T, block: (SQLiteDatabase) -> T): T {
    if (!file.exists()) return fallback
    return try {
      val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
      try {
        lastError = null
        block(db)
      } finally {
        db.close()
      }
    } catch (e: Exception) {
      lastError = e.message ?: e.javaClass.simpleName
      fallback
    }
  }

  private fun Cursor.nLong(i: Int): Long? = if (isNull(i)) null else getLong(i)

  private fun Cursor.nInt(i: Int): Int? = if (isNull(i)) null else getInt(i)

  private fun Cursor.nDouble(i: Int): Double? = if (isNull(i)) null else getDouble(i)

  private fun count(db: SQLiteDatabase, table: String): Int =
    try {
      db.rawQuery("SELECT COUNT(*) FROM $table", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    } catch (e: Exception) {
      0
    }

  private fun nowSec(): Long = System.currentTimeMillis() / 1000

  fun overview(): WatchDataOverview =
    read(WatchDataOverview(0, 0, 0, 0, 0, null, null, emptyList())) { db ->
      var first: Long? = null
      var last: Long? = null
      db.rawQuery("SELECT MIN(timestamp), MAX(timestamp) FROM step_samples", null).use {
        if (it.moveToFirst()) {
          first = it.nLong(0)
          last = it.nLong(1)
        }
      }
      val devices = ArrayList<WatchStoredDevice>()
      try {
        db.rawQuery("SELECT address, name, product_model, software_version, last_sync FROM device", null).use {
          while (it.moveToNext()) {
            devices.add(WatchStoredDevice(it.getString(0), it.getString(1), it.getString(2), it.getString(3), it.nLong(4)))
          }
        }
      } catch (e: Exception) {
        // tabel device belum ada: abaikan
      }
      WatchDataOverview(
        hrSamples = count(db, "step_samples"),
        workouts = count(db, "workouts"),
        sleepSegments = count(db, "sleep_segments"),
        healthMetricRows = count(db, "health_metrics"),
        hrvReadings = count(db, "hrv_readings"),
        firstSampleSec = first,
        lastSampleSec = last,
        devices = devices,
      )
    }

  /** Latihan terbaru lebih dulu. */
  fun workouts(limit: Int = 50): List<WatchWorkout> =
    read(emptyList()) { db ->
      val out = ArrayList<WatchWorkout>()
      val n = limit.coerceIn(1, 500)
      db.rawQuery(
        "SELECT id, start, end, sport_type, sport_name, duration, distance, calories, avg_hr, max_hr, steps, complete " +
          "FROM workouts ORDER BY start DESC LIMIT $n",
        null,
      ).use {
        while (it.moveToNext()) {
          out.add(
            WatchWorkout(
              id = it.getLong(0),
              startSec = it.getLong(1),
              endSec = it.getLong(2),
              sportType = it.getInt(3),
              sportName = it.getString(4) ?: "",
              durationSec = it.getInt(5),
              distanceM = it.getInt(6),
              calories = it.getInt(7),
              avgHr = it.nInt(8),
              maxHr = it.nInt(9),
              steps = it.getInt(10),
              complete = it.getInt(11) != 0,
            ),
          )
        }
      }
      out
    }

  /** Titik rekaman satu latihan, berurutan menurut waktu. */
  fun workoutPoints(workoutId: Long): List<WatchWorkoutPoint> =
    read(emptyList()) { db ->
      val out = ArrayList<WatchWorkoutPoint>()
      db.rawQuery(
        "SELECT ts, lat, lon, alt, hr, speed, cadence FROM workout_points WHERE workout_id = ? ORDER BY ts",
        arrayOf(workoutId.toString()),
      ).use {
        while (it.moveToNext()) {
          out.add(WatchWorkoutPoint(it.getLong(0), it.nDouble(1), it.nDouble(2), it.nDouble(3), it.nInt(4), it.nDouble(5), it.nInt(6)))
        }
      }
      out
    }

  /** Sampel HR harian jam dalam rentang [fromSec, toSec). */
  fun hrSamples(fromSec: Long, toSec: Long): List<WatchHrSample> =
    read(emptyList()) { db ->
      val out = ArrayList<WatchHrSample>()
      db.rawQuery(
        "SELECT timestamp, heart_rate FROM step_samples WHERE heart_rate IS NOT NULL AND heart_rate > 0 " +
          "AND timestamp >= ? AND timestamp < ? ORDER BY timestamp",
        arrayOf(fromSec.toString(), toSec.toString()),
      ).use {
        while (it.moveToNext()) out.add(WatchHrSample(it.getLong(0), it.getInt(1)))
      }
      out
    }

  /** Ringkasan harian, hari terbaru lebih dulu. */
  fun days(count: Int = 14): List<WatchDay> =
    read(emptyList()) { db ->
      val out = ArrayList<WatchDay>()
      val cutoff = nowSec() - count.coerceIn(1, 365) * 86400L
      db.rawQuery(
        "SELECT date(timestamp, 'unixepoch', 'localtime') AS d, SUM(steps), SUM(calories), SUM(distance), " +
          "MIN(NULLIF(resting_heart_rate, 0)), AVG(NULLIF(heart_rate, 0)), MIN(NULLIF(heart_rate, 0)), " +
          "MAX(heart_rate), AVG(NULLIF(spo2, 0)) " +
          "FROM step_samples WHERE timestamp >= ? GROUP BY d ORDER BY d DESC",
        arrayOf(cutoff.toString()),
      ).use {
        while (it.moveToNext()) {
          out.add(
            WatchDay(
              date = it.getString(0) ?: "",
              steps = it.getInt(1),
              calories = it.getInt(2),
              distanceM = it.getInt(3),
              restingHr = it.nInt(4),
              avgHr = it.nDouble(5)?.let { v -> Math.round(v).toInt() },
              minHr = it.nInt(6),
              maxHr = it.nInt(7),
              spo2Avg = it.nDouble(8)?.let { v -> Math.round(v).toInt() },
            ),
          )
        }
      }
      out
    }

  private class Seg(val start: Long, val end: Long, val stage: String?)

  /** Sesi tidur, yang terbaru lebih dulu. */
  fun sleepNights(count: Int = 14): List<WatchSleepNight> =
    read(emptyList()) { db ->
      val cutoff = nowSec() - count.coerceIn(1, 365) * 86400L
      val segs = ArrayList<Seg>()
      db.rawQuery(
        "SELECT start, end, stage FROM sleep_segments WHERE end >= ? ORDER BY start",
        arrayOf(cutoff.toString()),
      ).use {
        while (it.moveToNext()) segs.add(Seg(it.getLong(0), it.getLong(1), it.getString(2)))
      }

      val scores = ArrayList<LongArray>() // start, end, score, efficiency (-1 bila kosong)
      try {
        db.rawQuery("SELECT start, end, score, efficiency FROM sleep_scores WHERE end >= ?", arrayOf(cutoff.toString())).use {
          while (it.moveToNext()) {
            scores.add(longArrayOf(it.getLong(0), it.getLong(1), it.getLong(2), it.nLong(3) ?: -1L))
          }
        }
      } catch (e: Exception) {
        // tabel sleep_scores belum ada: abaikan
      }

      val sessions = ArrayList<MutableList<Seg>>()
      for (s in segs) {
        val cur = sessions.lastOrNull()
        if (cur != null && s.start - cur.maxOf { it.end } <= SESSION_GAP_SEC) cur.add(s) else sessions.add(mutableListOf(s))
      }

      val out = ArrayList<WatchSleepNight>()
      for (session in sessions) {
        // Bila ada baris bertahap, pakai itu saja supaya baris tanpa tahap tidak terhitung dua kali.
        val staged = session.filter { it.stage != null }
        val use = if (staged.isNotEmpty()) staged else session
        var deep = 0L
        var light = 0L
        var rem = 0L
        var awake = 0L
        var other = 0L
        for (s in use) {
          val d = maxOf(0L, s.end - s.start)
          when (s.stage) {
            "deep" -> deep += d
            "light" -> light += d
            "rem" -> rem += d
            "awake" -> awake += d
            else -> other += d
          }
        }
        val start = session.minOf { it.start }
        val end = session.maxOf { it.end }
        val sc = scores.firstOrNull { it[0] <= end && it[1] >= start }
        out.add(
          WatchSleepNight(
            startSec = start,
            endSec = end,
            asleepSec = deep + light + rem + other,
            deepSec = deep,
            lightSec = light,
            remSec = rem,
            awakeSec = awake,
            score = sc?.get(2)?.toInt(),
            efficiency = sc?.get(3)?.takeIf { it >= 0 }?.toInt(),
          ),
        )
      }
      out.sortedByDescending { it.startSec }
    }

  /** Daftar metrik kesehatan yang dicek jam: mana yang didukung dan berapa barisnya. */
  fun metricStatus(): List<WatchMetricStatus> =
    read(emptyList()) { db ->
      val rows = HashMap<String, Int>()
      try {
        db.rawQuery("SELECT type, COUNT(*) FROM health_metrics GROUP BY type", null).use {
          while (it.moveToNext()) rows[it.getString(0)] = it.getInt(1)
        }
      } catch (e: Exception) {
        // belum ada baris
      }
      val out = ArrayList<WatchMetricStatus>()
      db.rawQuery("SELECT type, title, supported, synced_at, note FROM health_metric_status ORDER BY title", null).use {
        while (it.moveToNext()) {
          val type = it.getString(0)
          out.add(WatchMetricStatus(type, it.getString(1) ?: type, it.getInt(2) != 0, rows[type] ?: 0, it.nLong(3), it.getString(4)))
        }
      }
      out
    }

  /** Nilai satu metrik kesehatan dalam [count] hari terakhir, yang terlama lebih dulu. */
  fun metric(type: String, count: Int = 30): List<WatchMetricPoint> =
    read(emptyList()) { db ->
      val out = ArrayList<WatchMetricPoint>()
      val cutoff = nowSec() - count.coerceIn(1, 365) * 86400L
      db.rawQuery(
        "SELECT ts, value, values_json FROM health_metrics WHERE type = ? AND ts >= ? ORDER BY ts",
        arrayOf(type, cutoff.toString()),
      ).use {
        while (it.moveToNext()) out.add(WatchMetricPoint(it.getLong(0), it.nDouble(1), it.getString(2) ?: "{}"))
      }
      out
    }

  /** Pengukuran HRV (malam atau spot), yang terbaru lebih dulu. */
  fun hrv(count: Int = 30): List<WatchHrv> =
    read(emptyList()) { db ->
      val out = ArrayList<WatchHrv>()
      val cutoff = nowSec() - count.coerceIn(1, 365) * 86400L
      db.rawQuery(
        "SELECT start, end, source, rmssd, sdnn, beats FROM hrv_readings WHERE end >= ? ORDER BY start DESC",
        arrayOf(cutoff.toString()),
      ).use {
        while (it.moveToNext()) out.add(WatchHrv(it.getLong(0), it.getLong(1), it.getString(2) ?: "", it.getDouble(3), it.nDouble(4), it.getInt(5)))
      }
      out
    }

  private companion object {
    const val SESSION_GAP_SEC = 3 * 3600L
  }
}
