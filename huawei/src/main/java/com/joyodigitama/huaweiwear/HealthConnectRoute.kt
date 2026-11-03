package com.joyodigitama.huaweiwear

/**
 * The GPS route of a workout as Health Connect takes it (`ExerciseRoute` of an `ExerciseSessionRecord`), planned from the
 * stored `workout_points` without any Health Connect type so it runs in plain JVM tests. [HealthConnectExporter] maps each
 * [Point] to an `ExerciseRoute.Location`.
 *
 * What Health Connect (connect-client 1.1.0) requires of a route, and what [plan] therefore guarantees:
 * - every location time is at or after the session start and strictly before its end;
 * - the times are strictly increasing;
 * - latitude within -90..90 and longitude within -180..180.
 * `workout_points` keeps no GPS accuracy, so a location carries none (`horizontalAccuracy` and `verticalAccuracy` are optional).
 */
internal object HealthConnectRoute {
  /** Fewest fixes worth a route: a line needs two. */
  const val MIN_POINTS = 2

  /**
   * Most locations written for one session. Health Connect documents no count, but each insert is one binder transaction
   * (about 1 MB), a route location is ~50 bytes parcelled and a long ride stores a fix per second (10 h = 36000): 2000 points
   * (a fix every 18 s over 10 h) keep one record far from the limit and still draw the same line on a map.
   */
  const val MAX_POINTS = 2000

  /** One `ExerciseRoute.Location`: unix seconds, degrees, metres above sea level (null: not stored). */
  data class Point(val timeSec: Long, val lat: Double, val lon: Double, val altM: Double?)

  /** The usable fixes of [points] inside `[startSec, endSec)`, thinned to at most [maxPoints]; empty when fewer than [MIN_POINTS] remain. */
  fun plan(points: List<WorkoutPoint>, startSec: Long, endSec: Long, maxPoints: Int = MAX_POINTS): List<Point> {
    val fixes = ArrayList<Point>()
    var lastSec = Long.MIN_VALUE
    for (p in points) {
      val lat = p.lat ?: continue
      val lon = p.lon ?: continue
      if (!usable(lat, lon)) continue
      if (p.ts < startSec || p.ts >= endSec) continue
      // Health Connect wants strictly increasing times: a repeated second keeps its first fix.
      if (p.ts <= lastSec) continue
      lastSec = p.ts
      fixes.add(Point(p.ts, lat, lon, p.alt?.takeIf { it.isFinite() }))
    }
    if (fixes.size < MIN_POINTS) return emptyList()
    return thin(fixes, maxPoints)
  }

  /** A coordinate on Earth that is not the 0,0 a GPS without a fix reports (same rule as `isUsableCoordinate` in `routeModel.ts`). */
  fun usable(lat: Double, lon: Double): Boolean {
    if (!lat.isFinite() || !lon.isFinite()) return false
    if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) return false
    return Math.abs(lat) > 1e-6 || Math.abs(lon) > 1e-6
  }

  /** [fixes] reduced to [max] evenly spaced by index, the first and the last always kept. */
  private fun thin(fixes: List<Point>, max: Int): List<Point> {
    if (fixes.size <= max) return fixes
    val last = fixes.size - 1L
    return List(max) { i -> fixes[(i * last / (max - 1)).toInt()] }
  }

  /**
   * The export signature of a workout ([HealthConnectExporter] writes a workout again when it changes): [base] plus the number of
   * stored GPS fixes when this export can attach a route, so a workout already written without one is written once more, as an
   * update of the same Health Connect record (same client id, newer version), the moment the route permission is granted.
   */
  fun signature(base: String, gpsFixes: Int, routeAllowed: Boolean): String =
    if (routeAllowed && gpsFixes >= MIN_POINTS) "$base|r$gpsFixes" else base
}
