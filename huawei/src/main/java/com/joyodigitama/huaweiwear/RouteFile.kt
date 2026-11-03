// Port of Gadgetbridge's service/devices/huawei/utils/HuaweiConvertTrack.java and HuaweiRouteTrack.java (AGPL-3.0-or-later; see NOTICE): the file Huawei
// Health sends as business type 6 of the P2P module `fitness` when it puts a route on the watch ("Send to wearable"). Huawei's own converter is the native
// `libtrack_feature_extra.so` (`TrackFeatureExtraction.ProcessTrackInfo` / `GetTrackResultForWatch`); GB reimplemented its output. The arithmetic is kept
// exactly as GB has it (float slope and intercept, truncating int casts, `Math.round`), because the Swift port and the golden vectors in `RouteFileTest`
// must agree to the byte.
package com.joyodigitama.huaweiwear

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiTLV

/** One fix of a route: degrees, degrees, metres (0 when [RouteTrack.hasAltitude] is false). */
internal class RouteFix(val lat: Double, val lon: Double, val alt: Double)

/** `HuaweiRouteTrack`. [id] is 36 characters in Huawei Health (a UUID); the watch lists the route by [name]. */
internal class RouteTrack(
  val id: String,
  val name: String,
  val fixes: List<RouteFix>,
  val hasAltitude: Boolean,
  val totalTimeSec: Long,
  /** 2 is the layout with altitude profile (watch sets ExpandCapability 216), 1 the plain line. */
  val version: Int = 2,
)

internal object RouteFile {
  const val BUSINESS_ROUTE_HANDSHAKE = 5
  const val BUSINESS_ROUTE_FILE = 6

  /** The route's name field in the version-2 header is 128 bytes; the watch's own limit is not known (it answers code 5 when a name is too long). */
  const val MAX_NAME_BYTES = 100
  const val MAX_ID_BYTES = 36

  /** Huawei Health asks the converter for `max(watch's point limit, 200)` points. */
  const val MIN_POINT_LIMIT = 200

  const val FILE_NAME = "byte.bin"

  private const val DEGREES_TO_RADIANS = Math.PI / 180.0
  private const val DEG_90_RADIAN = Math.PI / 2.0
  private const val METERS_PER_DEGREE = 1111949.375
  private const val EARTH_RADIUS_METERS = 6371393.0
  private const val COORDINATE_TOLERANCE = 0.0001f
  private const val POINT_INDEX_THRESHOLD = 30
  private const val POINT_DISTANCE_THRESHOLD = 50
  private const val DISTANCE_THRESHOLD = 200
  private const val V2_HEADER_BYTES = 0x108
  private const val V2_POINT_BYTES = 0x38
  private const val V2_PROFILE_BYTES = 3384
  private const val V1_HEADER_BYTES = 0xc
  private const val V1_POINT_BYTES = 0x28
  private const val PROFILE_POINTS = 83

  /** Validates what JS sends; the exception message is shown to the user (Indonesian copy is the app's job, these are the protocol limits). */
  fun validate(track: RouteTrack) {
    fun bad(what: String): Nothing = throw WearException(ErrorCodes.INVALID_ARGUMENT, what)
    if (track.name.isBlank()) bad("Give the route a name")
    if (track.name.toByteArray(Charsets.UTF_8).size > MAX_NAME_BYTES) bad("The route name is too long")
    if (track.id.toByteArray(Charsets.UTF_8).size > MAX_ID_BYTES) bad("The route id is too long")
    if (track.fixes.size < 2) bad("A route needs at least 2 points")
    if (track.fixes.size > 20_000) bad("A route has at most 20000 points")
    for (fix in track.fixes) {
      if (!(fix.lat.isFinite() && fix.lon.isFinite() && fix.alt.isFinite())) bad("A route point is not a number")
      if (isLatLonNotValid(fix.lat, fix.lon)) bad("A route point is outside the map")
    }
    if (track.fixes[0].lat == 0.0) bad("The first point of a route cannot be at latitude 0")
    if (track.version != 1 && track.version != 2) bad("Unknown route file version")
    if (track.version == 1 && track.hasAltitude) bad("The plain route file has no altitude")
  }

  private fun isLatLonNotValid(lat: Double, lon: Double) = lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0

  /** The 36-byte P2P header (businessType, version 1, totalLength, businessHeadLength 0, messageId; little endian) followed by [payload]. */
  fun message(businessType: Int, messageId: Int, payload: ByteArray): ByteArray = CourseProtobuf.message(businessType, messageId, payload)

  /** Handshake body: TLV tag 5 = the route's name (GB `HuaweiTLV.put(5, name)`; the length is Huawei's big-endian base-128 varint, `81 48` for 200). */
  fun handshakeBody(name: String): ByteArray = HuaweiTLV().put(5, name).serialize()

  /** The watch's answer to the handshake or to the file: `int32 LE` code, then (handshake only) `int32 LE` point limit. Null for a body shorter than 4 bytes. */
  class Answer(val code: Int, val maxPoints: Int?)

  fun parseAnswer(body: ByteArray): Answer? {
    if (body.size < 4) return null
    val buffer = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN)
    val code = buffer.getInt()
    val max = if (buffer.remaining() >= 4) buffer.getInt() else null
    return Answer(code, max)
  }

  fun pointLimit(answer: Answer?): Int = maxOf(answer?.maxPoints ?: 0, MIN_POINT_LIMIT)

  private class Pt {
    var lon = 0.0
    var lat = 0.0
    var dx = 0
    var dy = 0
    var index = 0
    var distance = 0
    var type = 0

    fun set(o: Pt) {
      lon = o.lon
      lat = o.lat
      dx = o.dx
      dy = o.dy
      index = o.index
      distance = o.distance
      type = o.type
    }

    fun copy(): Pt = Pt().also { it.set(this) }
  }

  /** `HuaweiConvertTrack`: one instance per conversion (it carries the simplification state). */
  private class Converter(val maxPoints: Int) {
    val simplified = ArrayList<Pt>()
    val base = Pt()
    val current = Pt()
    val previous = Pt()
    val ref = Pt()
    val intermediate = Pt()
    var maxDeviationFromLine = 0
    var slope = 0.0f
    var intercept = 0.0f
    var accumulatedSegmentDistance = 0
    var total3d = 0.0
    var totalDistance = 0.0
    lateinit var alt: DoubleArray
    lateinit var cumulative: DoubleArray
    lateinit var cumulative3d: DoubleArray
    lateinit var ascent: DoubleArray
    lateinit var descent: DoubleArray
    var profile: ByteArray? = null

    fun calculateLineEquation(p1: Pt, p2: Pt) {
      val deltaX = p1.dx - p2.dx
      slope = if (abs(deltaX) < 1) tan(DEG_90_RADIAN).toFloat() else (p1.dy - p2.dy).toFloat() / deltaX.toFloat()
      intercept = p1.dy.toFloat() - slope * p1.dx.toFloat()
    }

    fun distance(x1: Int, y1: Int, x2: Int, y2: Int): Double = sqrt((x1 - x2).toDouble() * (x1 - x2).toDouble() + (y1 - y2).toDouble() * (y1 - y2).toDouble())

    fun between(p1: Pt, p2: Pt): Int = distance(p1.dx, p1.dy, p2.dx, p2.dy).toInt()

    /** Java `float` arithmetic throughout: `-slope * dx + dy - intercept` is a float expression. */
    fun distanceFromLine(p: Pt): Long {
      val numerator: Float = abs(-slope * p.dx.toFloat() + p.dy.toFloat() - intercept)
      val denominator = sqrt((slope * slope).toDouble() + 1.0)
      return Math.round(numerator.toDouble() / denominator)
    }

    fun addAndSimplify(point: Pt, type: Int) {
      val toAdd = point.copy()
      toAdd.type = type
      simplified.add(toAdd)
      val size = simplified.size
      if (size > maxPoints && size > 3) {
        var selected = -1
        var minDistance = Int.MAX_VALUE
        for (i in 1 until size - 2) {
          val d = between(simplified[i], simplified[i + 1])
          if (d < minDistance) {
            selected = i
            minDistance = d
          }
        }
        if (selected > -1 && selected < size - 1) {
          val p1 = simplified[selected]
          val p2 = simplified[selected + 1]
          val merged = if (p1.type == 1) p1.type else p2.type
          p1.lon = (p1.lon + p2.lon) / 2
          p1.lat = (p1.lat + p2.lat) / 2
          p1.dx = (p2.dx + p1.dx) / 2
          p1.dy = (p2.dy + p1.dy) / 2
          p1.index = (p1.index + p2.index) / 2
          p1.distance = (p1.distance + p2.distance) / 2
          p1.type = merged
          simplified.removeAt(selected + 1)
        }
      }
    }

    fun processSegment() {
      if (current.index - previous.index > POINT_INDEX_THRESHOLD && between(previous, current) > POINT_DISTANCE_THRESHOLD) {
        if (between(intermediate, previous) > DISTANCE_THRESHOLD) {
          if (between(ref, intermediate) > DISTANCE_THRESHOLD) {
            calculateLineEquation(ref, intermediate)
            ref.set(intermediate)
            addAndSimplify(intermediate, 3)
          }
        }
        calculateLineEquation(ref, previous)
        ref.set(previous)
        addAndSimplify(ref, 8)
        calculateLineEquation(ref, current)
        ref.set(current)
        addAndSimplify(ref, 9)
        intermediate.set(current)
        maxDeviationFromLine = 0
        return
      }
      val distanceFromReference = between(current, ref)
      if (maxDeviationFromLine - distanceFromReference > DISTANCE_THRESHOLD) {
        calculateLineEquation(ref, intermediate)
        ref.set(intermediate)
        addAndSimplify(intermediate, 4)
        calculateLineEquation(intermediate, current)
        maxDeviationFromLine = between(intermediate, current)
        intermediate.set(current)
        return
      }
      if (maxDeviationFromLine < distanceFromReference) {
        intermediate.set(current)
        maxDeviationFromLine = distanceFromReference
      }
      if (distanceFromLine(current) <= POINT_DISTANCE_THRESHOLD) return
      if (between(current, intermediate) > POINT_DISTANCE_THRESHOLD) {
        calculateLineEquation(ref, intermediate)
        ref.set(intermediate)
        addAndSimplify(intermediate, 3)
      }
      calculateLineEquation(ref, current)
      ref.set(current)
      addAndSimplify(current, 2)
      intermediate.set(current)
      maxDeviationFromLine = 0
    }

    fun convert(track: RouteTrack): ByteArray {
      val fixes = track.fixes
      val n = fixes.size
      if (n < 2) return ByteArray(0)
      if (track.version == 1 && track.hasAltitude) return ByteArray(0)
      if (fixes[0].lat == 0.0) return ByteArray(0)
      if (isLatLonNotValid(fixes[0].lat, fixes[0].lon)) return ByteArray(0)

      base.lon = fixes[0].lon
      base.lat = fixes[0].lat
      base.dx = 0
      base.dy = 0
      base.index = 0
      base.distance = 0
      previous.set(base)
      current.set(base)
      accumulatedSegmentDistance = 0
      var state = 1

      for (i in 1 until n) {
        val lat = fixes[i].lat
        val lon = fixes[i].lon
        if (isLatLonNotValid(lat, lon)) continue
        val lonDiff = abs(lon - previous.lon)
        if (min(lonDiff, 360.0 - lonDiff) > 10.0) continue

        previous.set(current)
        val deltaLong = lon - base.lon
        val deltaLat = lat - base.lat
        current.lon = lon
        current.lat = lat
        current.dx = (deltaLong * METERS_PER_DEGREE * cos(base.lat * DEGREES_TO_RADIANS)).toInt()
        current.dy = (deltaLat * METERS_PER_DEGREE).toInt()
        current.index = i
        accumulatedSegmentDistance += between(previous, current)
        current.distance = (accumulatedSegmentDistance + 5) / 10

        if (state == 1) {
          // `COORDINATE_TOLERANCE / Math.cos(...)` is float / double in Java: the float widens to double first.
          if (abs(base.lon - lon) <= COORDINATE_TOLERANCE.toDouble() / cos(lat * DEGREES_TO_RADIANS)) {
            if (abs(base.lat - lat) <= COORDINATE_TOLERANCE.toDouble()) continue
            slope = tan(DEG_90_RADIAN).toFloat()
          } else {
            slope = ((base.lat - lat) / (base.lon - lon)).toFloat()
          }
          addAndSimplify(base, 1)
          intercept = 0f
          maxDeviationFromLine = 0
          ref.set(base)
          intermediate.set(base)
          state = 2
        }
        processSegment()
      }

      if (current.index != 0) {
        addAndSimplify(current, 4)
        if (simplified.isNotEmpty()) simplified[simplified.size - 1].distance = current.distance
      }

      alt = DoubleArray(n)
      cumulative = DoubleArray(n)
      cumulative3d = DoubleArray(n)
      ascent = DoubleArray(n)
      descent = DoubleArray(n)
      val lat = DoubleArray(n)
      val lon = DoubleArray(n)
      var prevLon = 0.0
      var prevLat = 0.0
      for (i in 0 until n) {
        lat[i] = fixes[i].lat
        lon[i] = fixes[i].lon
        alt[i] = fixes[i].alt
        if (i > 0) cumulative[i] = cumulative[i - 1] + haversine(prevLat, prevLon, lat[i], lon[i])
        prevLon = lon[i]
        prevLat = lat[i]
      }
      totalDistance = cumulative[n - 1]
      total3d = totalDistance

      if (track.version != 1 && track.hasAltitude) {
        for (i in 1 until n) {
          val dist = cumulative[i] - cumulative[i - 1]
          val deltaAlt = alt[i] - alt[i - 1]
          cumulative3d[i] = cumulative3d[i - 1] + sqrt(dist * dist + deltaAlt * deltaAlt)
          if (deltaAlt <= 0.0) {
            descent[i] = descent[i - 1] + deltaAlt
          } else {
            ascent[i] = ascent[i - 1] + deltaAlt
          }
        }
        total3d = cumulative3d[n - 1]
        profile = elevationProfile(lat, lon)
      }
      return serialize(track)
    }

    /** The 83-point altitude profile after the points of a version-2 file. */
    fun elevationProfile(lat: DoubleArray, lon: DoubleArray): ByteArray {
      val n = lat.size
      val totalAscent = ascent[n - 1]
      val totalDescent = descent[n - 1]
      val buf = ByteBuffer.allocate(V2_PROFILE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
      buf.putInt(0)
      buf.putInt((totalDistance * 10.0).toInt())
      buf.putInt((total3d * 10.0).toInt())
      buf.putInt((totalAscent * 10.0).toInt())
      buf.putInt((totalDescent * -10.0).toInt())
      buf.putInt(0)

      buf.putInt(Math.round(alt[0] * 10.0).toInt())
      buf.putInt(0)
      buf.putDouble(lon[0])
      buf.putDouble(lat[0])
      buf.putInt((cumulative[0] * 10.0).toInt())
      buf.putInt((cumulative3d[0] * 10.0).toInt())
      buf.putInt((ascent[0] * 10.0).toInt())
      buf.putInt((descent[0] * -10.0).toInt())

      var currentElevation = Math.round(alt[0] * 10.0).toInt()
      var currentDistance = cumulative[0]
      val segmentIncrement = (totalDistance / PROFILE_POINTS) / 10.0
      var currentPoint = 1
      var currentLat = lat[0]
      var currentLon = lon[0]
      var currentDist = (cumulative[0] * 10.0).toInt()
      var currentDist3d = (cumulative3d[0] * 10.0).toInt()
      var currentAsc = (ascent[0] * 10.0).toInt()
      var currentDesc = (descent[0] * -10.0).toInt()

      for (i in 1 until PROFILE_POINTS) {
        val targetDistance = (totalDistance / PROFILE_POINTS) * i.toDouble()
        var closestIdx = currentPoint
        var nextIdx = n - 1
        if (currentPoint < n) {
          var minDiff = Double.MAX_VALUE
          for (searchIdx in currentPoint until n) {
            val searchDist = cumulative[searchIdx]
            val diff = abs(searchDist - targetDistance)
            if (diff < minDiff) {
              closestIdx = searchIdx
              minDiff = diff
            }
            if (searchDist > targetDistance) {
              nextIdx = searchIdx
              break
            }
          }
          currentDistance = cumulative[closestIdx]
        }

        if (targetDistance - segmentIncrement > currentDistance || segmentIncrement + targetDistance < currentDistance) {
          val distDiff = cumulative[nextIdx] * 10.0 - currentDist.toDouble()
          val ratio = (targetDistance * 10.0 - currentDist.toDouble()) / distDiff
          currentLat += (lat[nextIdx] - currentLat) * ratio
          currentLon += (lon[nextIdx] - currentLon) * ratio
          currentElevation += ((alt[nextIdx] * 10.0 - currentElevation.toDouble()) * ratio).toInt()
          currentDist += (distDiff * ratio).toInt()
          currentDist3d += (ratio * (cumulative3d[nextIdx] * 10.0 - currentDist3d.toDouble())).toInt()
          currentAsc += (ratio * (ascent[nextIdx] * 10.0 - currentAsc.toDouble())).toInt()
          currentDesc += (ratio * (descent[nextIdx] * -10.0 - currentDesc.toDouble())).toInt()
        } else {
          currentLat = lat[closestIdx]
          currentLon = lon[closestIdx]
          currentPoint = closestIdx + 1
          currentElevation = Math.round(alt[closestIdx] * 10.0).toInt()
          currentDist = (cumulative[closestIdx] * 10.0).toInt()
          currentDist3d = (cumulative3d[closestIdx] * 10.0).toInt()
          currentAsc = (ascent[closestIdx] * 10.0).toInt()
          currentDesc = (descent[closestIdx] * -10.0).toInt()
        }

        buf.putInt(currentElevation)
        buf.putInt(0)
        buf.putDouble(currentLon)
        buf.putDouble(currentLat)
        buf.putInt(currentDist)
        buf.putInt(currentDist3d)
        buf.putInt(currentAsc)
        buf.putInt(currentDesc)
        currentDistance = cumulative[closestIdx]
      }

      buf.putInt(Math.round(alt[n - 1] * 10.0).toInt())
      buf.putInt(0)
      buf.putDouble(lon[n - 1])
      buf.putDouble(lat[n - 1])
      buf.putInt((totalDistance * 10.0).toInt())
      buf.putInt((total3d * 10.0).toInt())
      buf.putInt((totalAscent * 10.0).toInt())
      buf.putInt((totalDescent * -10.0).toInt())
      return buf.array()
    }

    fun serialize(track: RouteTrack): ByteArray {
      if (track.version != 1) {
        val saved = min(maxPoints, simplified.size)
        val header = ByteBuffer.allocate(V2_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(-1)
        header.putInt(track.version)
        header.putInt(track.totalTimeSec.toInt())
        header.putFloat(totalDistance.toFloat())
        header.putInt(saved)
        header.put(track.id.toByteArray().copyOf(0x24))
        header.put(track.name.toByteArray().copyOf(0x80))
        header.putFloat(total3d.toFloat())
        header.putInt(if (track.hasAltitude) 1 else 0)
        header.putInt(0)
        header.putInt(0)
        header.put(0)
        header.put(0)
        header.putShort(0)

        var size = saved * V2_POINT_BYTES + V2_HEADER_BYTES
        if (track.hasAltitude) size += 0xd38
        val out = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        out.put(header.array())
        for (i in 0 until saved) {
          val pt = simplified[i]
          out.putDouble(pt.lon)
          out.putDouble(pt.lat)
          out.putInt(pt.dx)
          out.putInt(pt.dy)
          out.putInt(pt.index)
          out.putInt(Math.round(cumulative[pt.index]).toInt())
          out.putShort(0)
          out.put(pt.type.toByte())
          out.put(0)
          out.putInt(Math.round(alt[pt.index]).toInt())
          out.putInt(Math.round(cumulative3d[pt.index]).toInt())
          out.putInt(Math.round(ascent[pt.index]).toInt())
          out.putInt(Math.round(abs(descent[pt.index])).toInt())
          out.putInt(0)
        }
        val altitudeBlock = profile
        if (track.hasAltitude && altitudeBlock != null) out.put(altitudeBlock)
        return out.array()
      }

      val saved = simplified.size
      val out = ByteBuffer.allocate(saved * V1_POINT_BYTES + V1_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
      out.putInt(track.totalTimeSec.toInt())
      out.putFloat(totalDistance.toFloat())
      out.putInt(saved)
      for (pt in simplified) {
        out.putDouble(pt.lon)
        out.putDouble(pt.lat)
        out.putInt(pt.dx)
        out.putInt(pt.dy)
        out.putInt(pt.index)
        out.putInt(pt.distance)
        out.putShort(0)
        out.put(pt.type.toByte())
        out.put(0)
        out.put(0)
        out.put(0)
        out.put(0)
        out.put(0)
      }
      return out.array()
    }

    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
      val prevLatRad = lat1 * DEGREES_TO_RADIANS
      val curLatRad = lat2 * DEGREES_TO_RADIANS
      val deltaLatHalfSin = sin((prevLatRad - curLatRad) / 2)
      val deltaLonHalfSin = sin((lon1 * DEGREES_TO_RADIANS - lon2 * DEGREES_TO_RADIANS) / 2)
      val a = deltaLatHalfSin * deltaLatHalfSin + cos(prevLatRad) * cos(curLatRad) * deltaLonHalfSin * deltaLonHalfSin
      val centralAngle = 2 * atan2(sqrt(a), sqrt(1 - a))
      return centralAngle * EARTH_RADIUS_METERS
    }
  }

  /** `HuaweiConvertTrack.convertTrackToBin`: the route file body for at most [maxPoints] simplified points; empty when the route cannot be converted. */
  fun convert(track: RouteTrack, maxPoints: Int): ByteArray = Converter(maxPoints).convert(track)

  /** How many points a converted file holds (`savePoints` in the header: version 2 at offset 16, version 1 at offset 8). */
  fun pointCount(file: ByteArray, version: Int): Int {
    val offset = if (version == 1) 8 else 16
    if (file.size < offset + 4) return 0
    return ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN).getInt(offset)
  }
}
