// Derived from Gadgetbridge (AGPL-3.0-or-later; see NOTICE): devices/huawei/{HuaweiHeartRateZones,HuaweiHeartRateZonesSpec},
// model/heartratezones/{HeartRateZones,HeartRateZonesConfig,HeartRateZonesUtils} and the TLV layout of
// service/devices/huawei/p2p/HuaweiP2PTrackService.getHRZonesData. GB's defaults are ported as they are; user-edited zones
// (Huawei Health's zone screen, [UserZones]) are JoyoFit's own addition on top of the same Config/TLV layout.
package com.joyodigitama.huaweiwear

/** Default (or user-configured, [UserZones]) heart-rate zones and the TLV the watch's `hw.unitedevice.track` module takes. Pure, no Android. */
internal object HeartRateZonesCalc {
  const val MAXIMUM_HEART_RATE = 220
  const val DEFAULT_REST_HEART_RATE = 60

  /** [huaweiType] is HuaweiHeartRateZonesSpec.HUAWEI_TYPE_*, [hrCorrection] its getHRCorrection. */
  enum class Posture(val huaweiType: Int, val hrCorrection: Int) {
    UPRIGHT(1, 0),
    SITTING(2, 6),
    SWIMMING(3, 10),
    OTHER(4, 5),
  }

  /** [huaweiCode] is HuaweiHeartRateZonesSpec.HUAWEI_CALCULATE_METHOD_*. */
  enum class Method(val huaweiCode: Int) { MHR(0), HRR(1), LTHR(3) }

  data class Zones(
    val method: Method,
    val threshold: Int,
    val resting: Int,
    val zone1: Int,
    val zone2: Int,
    val zone3: Int,
    val zone4: Int,
    val zone5: Int,
  ) {
    /** HeartRateZones.isValid. */
    fun isValid(): Boolean =
      checkValue(threshold) && checkValue(resting) && checkValue(zone1) && checkValue(zone2) && checkValue(zone3) &&
        checkValue(zone4) && checkValue(zone5) && zone1 <= zone2 && zone2 <= zone3 && zone3 <= zone4 && zone4 <= zone5

    /** HeartRateZones.hasValidData. */
    fun hasValidData(): Boolean {
      val valid = zone1 > 0 && zone2 > 0 && zone3 > 0 && zone4 > 0 && zone5 > 0 && threshold > 0
      return if (method == Method.HRR) valid && resting > 0 else valid
    }

    /** `{zone1..zone5, threshold-or-resting}` as [FitnessData.HeartRateZoneConfigPacket] takes them. */
    fun asArray(): IntArray = intArrayOf(zone1, zone2, zone3, zone4, zone5, if (method == Method.HRR) resting else threshold)
  }

  data class Config(
    val posture: Posture,
    val warningEnable: Boolean,
    val warningHRLimit: Int,
    val currentMethod: Method,
    val zones: List<Zones>,
  ) {
    fun byMethod(method: Method): Zones? = zones.firstOrNull { it.method == method }

    /** HeartRateZonesConfig.isValid. */
    fun isValid(): Boolean = checkValue(warningHRLimit) && warningHRLimit > 0 && zones.isNotEmpty() && zones.all { it.isValid() }
  }

  /**
   * The zones the user set in the app (`HeartRateZonesConfig` in `HuaweiWear.types.ts`): [method] is [Method.MHR] (max-HR %) or
   * [Method.HRR] (HR reserve), [zones] the lowest bpm of Z1..Z5.
   */
  data class UserZones(
    val method: Method,
    val maxHr: Int,
    val restingHr: Int,
    val zones: List<Int>,
    val warningEnabled: Boolean,
    val warningLimit: Int,
  ) {
    /** What is wrong with the values, or null when the watch can take them. */
    fun problem(): String? = when {
      method != Method.MHR && method != Method.HRR -> "Zone method must be max or hrr"
      maxHr !in MAX_HR_RANGE -> "Max heart rate must be ${MAX_HR_RANGE.first} to ${MAX_HR_RANGE.last} bpm"
      restingHr !in RESTING_HR_RANGE -> "Resting heart rate must be ${RESTING_HR_RANGE.first} to ${RESTING_HR_RANGE.last} bpm"
      restingHr >= maxHr -> "Resting heart rate must be below the max heart rate"
      zones.size != 5 -> "Zones must be the lowest bpm of five zones"
      zones[0] < MIN_ZONE_HR -> "Zone 1 must start at $MIN_ZONE_HR bpm or more"
      zones.zipWithNext().any { (a, b) -> a >= b } -> "Zone limits must rise strictly"
      zones[4] >= maxHr -> "Zone 5 must start below the max heart rate"
      warningLimit !in MAX_HR_RANGE -> "Heart-rate limit must be ${MAX_HR_RANGE.first} to ${MAX_HR_RANGE.last} bpm"
      else -> null
    }

    /** The user's own set for [method]. */
    fun zonesOf(): Zones = Zones(method, maxHr, restingHr, zones[0], zones[1], zones[2], zones[3], zones[4])
  }

  val MAX_HR_RANGE = 100..219
  val RESTING_HR_RANGE = 30..110
  const val MIN_ZONE_HR = 30

  fun checkValue(v: Int): Boolean = v >= 0 && v < MAXIMUM_HEART_RATE

  /** `Math.round(((float) (x * pct)) / 100.0f)`, the float arithmetic of HuaweiHeartRateZones. */
  private fun pct(x: Int, pct: Int): Int = Math.round((x * pct).toFloat() / 100.0f)

  /** HuaweiHeartRateZones(method, HRThreshold) with the default resting rate: its constructor ends in `reset()`. */
  fun zones(method: Method, threshold: Int, resting: Int = DEFAULT_REST_HEART_RATE): Zones = when (method) {
    Method.MHR -> Zones(
      method, threshold, resting,
      zone1 = pct(threshold, 50), zone2 = pct(threshold, 60), zone3 = pct(threshold, 70), zone4 = pct(threshold, 80), zone5 = pct(threshold, 90),
    )
    Method.HRR -> {
      val calc = threshold - resting
      Zones(
        method, threshold, resting,
        zone1 = pct(calc, 59) + resting, zone2 = pct(calc, 74) + resting, zone3 = pct(calc, 84) + resting,
        zone4 = pct(calc, 88) + resting, zone5 = pct(calc, 95) + resting,
      )
    }
    Method.LTHR -> {
      val lthr = Math.round(resting.toFloat() + ((threshold - resting) * 85).toFloat() / 100.0f)
      Zones(
        method, lthr, resting,
        zone1 = pct(lthr, 67), zone2 = pct(lthr, 80), zone3 = pct(lthr, 89), zone4 = pct(lthr, 97), zone5 = pct(lthr, 102),
      )
    }
  }

  /** The methods GB builds per posture (getUprightZone/getSittingZone/...): upright also LTHR. */
  private fun methodsFor(posture: Posture): List<Method> =
    if (posture == Posture.UPRIGHT) listOf(Method.MHR, Method.HRR, Method.LTHR) else listOf(Method.MHR, Method.HRR)

  private fun config(posture: Posture, age: Int, methods: List<Method>): Config {
    val threshold = (MAXIMUM_HEART_RATE - age) - posture.hrCorrection
    return Config(
      posture,
      warningEnable = true,
      warningHRLimit = MAXIMUM_HEART_RATE - age,
      currentMethod = Method.MHR,
      zones = methods.map { zones(it, threshold) },
    )
  }

  /**
   * The same user values for every posture, no posture correction. The user's method carries [UserZones.zonesOf], the other
   * method the defaults for max/resting. [simpleOnly] (07/13: the packet has one MHR array) makes the MHR set the user's
   * whatever the method.
   */
  private fun userConfig(posture: Posture, user: UserZones, methods: List<Method>, simpleOnly: Boolean): Config = Config(
    posture,
    warningEnable = user.warningEnabled,
    warningHRLimit = user.warningLimit,
    currentMethod = if (simpleOnly) Method.MHR else user.method,
    zones = methods.map {
      when {
        simpleOnly -> user.zonesOf().copy(method = Method.MHR)
        it == user.method -> user.zonesOf()
        else -> zones(it, user.maxHr, user.restingHr)
      }
    },
  )

  /**
   * HuaweiHeartRateZonesSpec.getDeviceConfig for [age]: with Track all four postures; without Track but with the zone
   * command only upright (MHR, plus HRR when [extended]); otherwise nothing. With [user] the postures carry the user's zones
   * instead of the age defaults (the single place the user's config becomes [Config]s).
   */
  fun deviceConfig(age: Int, track: Boolean, simpleCommand: Boolean, extended: Boolean, user: UserZones? = null): List<Config> {
    if (!track && simpleCommand) {
      val methods = if (extended) listOf(Method.MHR, Method.HRR) else listOf(Method.MHR)
      return listOf(if (user != null) userConfig(Posture.UPRIGHT, user, methods, simpleOnly = !extended) else config(Posture.UPRIGHT, age, methods))
    }
    if (!track) return emptyList()
    return Posture.values().map { if (user != null) userConfig(it, user, methodsFor(it), simpleOnly = false) else config(it, age, methodsFor(it)) }
  }

  /**
   * HuaweiP2PTrackService.getHRZonesData as (tag, single byte value) pairs in GB's order, or null when GB would refuse
   * to send (an invalid config, e.g. an age that puts a value outside 0..219).
   */
  fun trackTlv(configs: List<Config>): List<Pair<Int, Int>>? {
    fun cfg(p: Posture) = configs.firstOrNull { it.posture == p }
    val upright = cfg(Posture.UPRIGHT)
    val sitting = cfg(Posture.SITTING)
    val swimming = cfg(Posture.SWIMMING)
    val other = cfg(Posture.OTHER)
    if (upright == null || sitting == null || swimming == null || other == null) return null
    if (!upright.isValid() || !sitting.isValid() || !swimming.isValid() || !other.isValid()) return null
    val uMhr = upright.byMethod(Method.MHR)
    val uHrr = upright.byMethod(Method.HRR)
    val uLthr = upright.byMethod(Method.LTHR)
    val sMhr = sitting.byMethod(Method.MHR)
    val sHrr = sitting.byMethod(Method.HRR)
    val wMhr = swimming.byMethod(Method.MHR)
    val wHrr = swimming.byMethod(Method.HRR)
    val oMhr = other.byMethod(Method.MHR)
    val oHrr = other.byMethod(Method.HRR)
    if (uMhr == null || uHrr == null || uLthr == null || sMhr == null || sHrr == null || wMhr == null || wHrr == null || oMhr == null || oHrr == null) return null

    val out = ArrayList<Pair<Int, Int>>()
    fun put(tag: Int, v: Int) {
      out.add(tag to (v and 0xFF))
    }
    fun flag(c: Config) = if (c.warningEnable) 1 else 0

    if (uMhr.hasValidData()) {
      put(0x2, uMhr.zone1); put(0x3, uMhr.zone2); put(0x4, uMhr.zone3); put(0x5, uMhr.zone4); put(0x6, uMhr.zone5)
      put(0x7, uMhr.threshold); put(0x8, flag(upright)); put(0x9, upright.warningHRLimit)
      put(0xa, upright.currentMethod.huaweiCode); put(0xb, uMhr.threshold)
    }
    put(0xc, uHrr.resting)
    if (uHrr.hasValidData()) {
      put(0xd, uHrr.zone1); put(0xe, uHrr.zone2); put(0xf, uHrr.zone3); put(0x10, uHrr.zone4); put(0x11, uHrr.zone5)
    }
    if (uLthr.hasValidData()) {
      put(0x3f, uLthr.threshold); put(0x40, uLthr.zone5); put(0x41, uLthr.zone4); put(0x42, uLthr.zone3); put(0x43, uLthr.zone2); put(0x44, uLthr.zone1)
    }
    if (sMhr.hasValidData()) {
      put(0x12, flag(sitting)); put(0x13, sitting.currentMethod.huaweiCode); put(0x14, sitting.warningHRLimit)
      put(0x15, sMhr.zone1); put(0x16, sMhr.zone2); put(0x17, sMhr.zone3); put(0x18, sMhr.zone4); put(0x19, sMhr.zone5); put(0x1a, sMhr.threshold)
    }
    if (sHrr.hasValidData()) {
      put(0x1b, sHrr.resting); put(0x1c, sHrr.zone1); put(0x1d, sHrr.zone2); put(0x1e, sHrr.zone3); put(0x1f, sHrr.zone4); put(0x20, sHrr.zone5)
    }
    if (wMhr.hasValidData()) {
      put(0x21, flag(swimming)); put(0x22, swimming.currentMethod.huaweiCode); put(0x23, swimming.warningHRLimit)
      put(0x24, wMhr.zone1); put(0x25, wMhr.zone2); put(0x26, wMhr.zone3); put(0x27, wMhr.zone4); put(0x28, wMhr.zone5); put(0x29, wMhr.threshold)
    }
    if (wHrr.hasValidData()) {
      put(0x2a, wHrr.resting); put(0x2b, wHrr.zone1); put(0x2c, wHrr.zone2); put(0x2d, wHrr.zone3); put(0x2e, wHrr.zone4); put(0x2f, wHrr.zone5)
    }
    if (oMhr.hasValidData()) {
      put(0x30, flag(other)); put(0x31, other.currentMethod.huaweiCode); put(0x32, other.warningHRLimit)
      put(0x33, oMhr.zone1); put(0x34, oMhr.zone2); put(0x35, oMhr.zone3); put(0x36, oMhr.zone4); put(0x37, oMhr.zone5); put(0x38, oMhr.threshold)
    }
    if (oHrr.hasValidData()) {
      put(0x39, oHrr.resting); put(0x3a, oHrr.zone1); put(0x3b, oHrr.zone2); put(0x3c, oHrr.zone3); put(0x3d, oHrr.zone4); put(0x3e, oHrr.zone5)
    }
    return out
  }
}
