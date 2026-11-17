package com.runnow.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClimateTest {
    @Test fun solarLoadRaisesEffectiveTemperature() {
        val c = ClimateConditions(temperatureC = 28.0, humidityPct = 80.0, apparentTemperatureC = 32.0, windKmh = 4.0, solarRadiationWm2 = 300.0)
        assertEquals(30.0, c.effectiveTemperatureC())
    }

    @Test fun thunderstormAndAbsoluteHeatOverridePersonalThresholds() {
        val personal = PersonalClimateProfile(true, 30, 30, optimumC = 22.0, comfortableBelowC = 24, heavyAboveC = 27, confidence = "cukup", explanation = "")
        val thunder = ClimateConditions(temperatureC = 24.0, humidityPct = 50.0, apparentTemperatureC = 24.0, windKmh = 3.0, weatherCode = 95)
        val extreme = ClimateConditions(temperatureC = 38.0, humidityPct = 30.0, apparentTemperatureC = 38.0, windKmh = 2.0)
        assertEquals("Berisiko", climateStatus(thunder, personal).label)
        assertEquals("Berisiko", climateStatus(extreme, personal).label)
    }

    @Test fun unknownWeatherDoesNotInventSafeStatus() {
        val c = ClimateConditions(null, null, null, null)
        assertEquals("unknown", climateStatus(c, PersonalClimateProfile(false, 0, 0, explanation = "")).level)
    }

    @Test fun adviceNeverChangesTargetAndWarnsForHardSession() {
        val c = ClimateConditions(temperatureC = 30.0, humidityPct = 75.0, apparentTemperatureC = 35.0, windKmh = 4.0)
        val advice = climateAdvice(c, PersonalClimateProfile(false, 0, 0, explanation = ""), "QUALITY", false)
        assertTrue(advice.message.contains("digeser"))
        assertTrue(advice.stopWarning != null)
    }

    @Test fun slotsStayInFutureAndAreSeparated() {
        val now = 1_800_000_000_000L
        val base = ClimateConditions(temperatureC = 32.0, humidityPct = 70.0, apparentTemperatureC = 36.0, windKmh = 4.0)
        val cooler = ClimateConditions(temperatureC = 27.0, humidityPct = 70.0, apparentTemperatureC = 29.0, windKmh = 4.0)
        val profile = PersonalClimateProfile(false, 0, 0, explanation = "")
        val slots = recommendClimateSlots(now, 45, listOf(now - 1 to cooler, now + 3_600_000 to cooler, now + 4 * 3_600_000 to base), base, profile)
        assertTrue(slots.all { it.startMs > now })
        assertFalse(slots.size == 2 && kotlin.math.abs(slots[0].startMs - slots[1].startMs) < 2 * 3_600_000L)
    }
}
