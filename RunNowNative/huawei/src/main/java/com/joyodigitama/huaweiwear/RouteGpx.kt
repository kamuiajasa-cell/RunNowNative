package com.joyodigitama.huaweiwear

import android.util.Xml
import java.io.ByteArrayInputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Membaca garis rute dari berkas GPX 1.0 / 1.1 untuk dikirim ke jam: jalur (`trk/trkseg/trkpt`) bila punya garis,
 * kalau tidak rute rencana (`rte/rtept`). Titik arah (`wpt`) dan ekstensi (detak, daya, tambahan Garmin dan Strava) dilewati.
 * Port dari `gpx.ts` dan `routeModel.ts` di JoyoFit (cleanRoutePoints, completeAltitudes); jumlah titik dikurangi merata
 * ke [MAX_POINTS], lalu inti jam menyederhanakannya lagi sesuai batas jam (`RouteFile.convert`).
 */
internal object RouteGpx {
  const val MAX_BYTES = 20 * 1024 * 1024
  const val MAX_POINTS = 3000

  class Parsed(val name: String?, val fixes: List<RouteFix>, val hasAltitude: Boolean, val distanceM: Double)

  private class Pt(val lat: Double, val lon: Double, var alt: Double?)

  private val OWNERS = setOf("trk", "rte", "metadata")

  fun parse(bytes: ByteArray): Parsed {
    if (bytes.isEmpty()) throw IllegalArgumentException("Berkas kosong.")
    if (bytes.size > MAX_BYTES) throw IllegalArgumentException("Berkas terlalu besar (paling banyak 20 MB).")

    val track = ArrayList<Pt>()
    val planned = ArrayList<Pt>()
    val names = HashMap<String, String>()
    try {
      val p = Xml.newPullParser()
      p.setInput(ByteArrayInputStream(bytes), null)
      val stack = ArrayList<String>()
      var point: Pt? = null
      var into: ArrayList<Pt>? = null
      var capture: String? = null
      val buf = StringBuilder()
      var sawRoot = false

      var event = p.eventType
      while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
          XmlPullParser.START_TAG -> {
            val name = p.name ?: ""
            val parent = stack.lastOrNull()
            if (!sawRoot) {
              sawRoot = true
              if (name != "gpx") throw IllegalArgumentException("Ini bukan berkas GPX.")
            }
            if (name == "trkpt" || name == "rtept") {
              val lat = p.getAttributeValue(null, "lat")?.trim()?.toDoubleOrNull() ?: Double.NaN
              val lon = p.getAttributeValue(null, "lon")?.trim()?.toDoubleOrNull() ?: Double.NaN
              point = Pt(lat, lon, null)
              into = if (name == "trkpt") track else planned
            } else if (name == "name" && parent != null && parent in OWNERS && !names.containsKey(parent)) {
              capture = "name"
              buf.setLength(0)
            } else if (name == "ele" && point != null && (parent == "trkpt" || parent == "rtept")) {
              capture = "ele"
              buf.setLength(0)
            }
            stack.add(name)
          }
          XmlPullParser.TEXT -> if (capture != null) buf.append(p.text)
          XmlPullParser.END_TAG -> {
            val name = p.name ?: ""
            val at = stack.lastIndexOf(name)
            if (at >= 0) while (stack.size > at) stack.removeAt(stack.size - 1)
            if (name == "trkpt" || name == "rtept") {
              point?.let { into?.add(it) }
              point = null
              capture = null
            } else if (capture == "name" && name == "name") {
              val owner = stack.lastOrNull()
              val value = buf.toString().trim()
              if (value.isNotEmpty() && owner != null && owner in OWNERS && !names.containsKey(owner)) names[owner] = value
              capture = null
            } else if (capture == "ele" && name == "ele") {
              point?.alt = buf.toString().trim().toDoubleOrNull()?.takeIf { it.isFinite() }
              capture = null
            }
          }
        }
        event = p.next()
      }
      if (!sawRoot) throw IllegalArgumentException("Ini bukan berkas GPX.")
    } catch (e: XmlPullParserException) {
      throw IllegalArgumentException("Berkas ini bukan GPX yang valid.")
    } catch (e: java.io.IOException) {
      throw IllegalArgumentException("Berkas ini bukan GPX yang valid.")
    }

    val trackPoints = clean(track)
    val routePoints = clean(planned)
    val useTrack = trackPoints.size >= 2 || routePoints.size < 2
    val line = completeAltitudes(if (useTrack) trackPoints else routePoints)
    if (line.size < 2) throw IllegalArgumentException("Berkas ini tidak punya garis rute (minimal 2 titik).")

    val hasAltitude = line.all { it.alt != null }
    val shown = decimate(line, MAX_POINTS)
    var distance = 0.0
    for (i in 1 until line.size) distance += haversineM(line[i - 1], line[i])
    val fixes = shown.map { RouteFix(it.lat, it.lon, if (hasAltitude) it.alt ?: 0.0 else 0.0) }
    val name = (if (useTrack) names["trk"] else names["rte"]) ?: names["metadata"]
    return Parsed(name, fixes, hasAltitude, distance)
  }

  private fun usable(lat: Double, lon: Double): Boolean {
    if (!lat.isFinite() || !lon.isFinite()) return false
    if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) return false
    return abs(lat) > 1e-6 || abs(lon) > 1e-6
  }

  /** Buang titik tanpa koordinat yang bisa dipakai dan pengulangan koordinat yang sama berturut-turut. */
  private fun clean(points: List<Pt>): List<Pt> {
    val out = ArrayList<Pt>(points.size)
    for (p in points) {
      if (!usable(p.lat, p.lon)) continue
      val last = out.lastOrNull()
      if (last != null && last.lat == p.lat && last.lon == p.lon) continue
      out.add(Pt(p.lat, p.lon, p.alt?.takeIf { it.isFinite() }))
    }
    return out
  }

  /**
   * Beri tiap titik ketinggian bila sebagian besar punya: celah diisi interpolasi antar tetangga (menurut indeks), ujung memakai yang terdekat.
   * Kalau kurang dari separuh titik punya ketinggian, semuanya dibuang (tebakan akan lebih banyak karangan).
   */
  private fun completeAltitudes(points: List<Pt>): List<Pt> {
    val n = points.size
    if (n == 0) return emptyList()
    val known = ArrayList<Int>()
    for (i in 0 until n) if (points[i].alt != null) known.add(i)
    if (known.size == n) return points.map { Pt(it.lat, it.lon, it.alt) }
    if (known.isEmpty() || known.size * 2 < n) return points.map { Pt(it.lat, it.lon, null) }

    val out = points.map { Pt(it.lat, it.lon, it.alt) }
    var k = 0
    for (i in 0 until n) {
      if (known[k] == i) continue
      while (k + 1 < known.size && known[k + 1] < i) k++
      val before = if (known[k] < i) known[k] else -1
      val after = if (known[k] < i) (if (k + 1 < known.size) known[k + 1] else -1) else known[k]
      if (before >= 0 && after >= 0) {
        val a = points[before].alt!!
        val b = points[after].alt!!
        out[i].alt = a + (b - a) * (i - before) / (after - before).toDouble()
      } else {
        out[i].alt = points[if (before >= 0) before else after].alt
      }
    }
    return out
  }

  /** Kurangi merata ke paling banyak [max] titik; titik pertama dan terakhir selalu ikut. */
  private fun decimate(points: List<Pt>, max: Int): List<Pt> {
    val n = points.size
    if (n <= max) return points
    val out = ArrayList<Pt>(max)
    for (i in 0 until max) out.add(points[(i.toLong() * (n - 1) / (max - 1)).toInt()])
    return out
  }

  private fun haversineM(a: Pt, b: Pt): Double {
    val r = 6371008.8
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * atan2(sqrt(h), sqrt(1 - h))
  }
}
