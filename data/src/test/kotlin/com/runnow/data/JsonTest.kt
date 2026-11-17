package com.runnow.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonTest {
    @Test
    fun parsesAndStringifiesLikeJavaScript() {
        val text = """{"a":1,"b":[true,null,"x\ny\u00e9"],"c":{"d":-2.5e2,"e":0.1},"f":""}"""
        val v = Json.parse(text)
        assertEquals(JNum(1.0), v["a"])
        assertEquals(JNum(-250.0), v["c"]["d"])
        assertEquals("x\ny\u00e9", v["b"].items!![2].text())
        assertEquals("""{"a":1,"b":[true,null,"x\ny\u00e9"],"c":{"d":-250,"e":0.1},"f":""}""".replace("\\u00e9", "\u00e9"), Json.stringify(v))
    }

    @Test
    fun numberTextMatchesJavaScript() {
        assertEquals("5", Json.numText(5.0))
        assertEquals("0", Json.numText(-0.0))
        assertEquals("0.00001", Json.numText(1e-5))
        assertEquals("1.5", Json.numText(1.5))
        assertEquals("1794729600000", Json.numText(1794729600000.0))
        assertEquals("null", Json.numText(Double.NaN))
    }

    @Test
    fun rejectsBrokenJson() {
        assertFailsWith<JsonException> { Json.parse("{") }
        assertFailsWith<JsonException> { Json.parse("[1,]") }
        assertFailsWith<JsonException> { Json.parse("1 2") }
    }

    @Test
    fun javaScriptCoercions() {
        assertEquals(0.0, JNull.number())
        assertEquals(12.0, JStr("12").number())
        assertEquals(true, JStr("abc").number().isNaN())
        assertEquals(true, (null as JsonValue?).number().isNaN())
        assertEquals(false, JStr("").truthy())
        assertEquals(true, JStr("0").truthy())
        assertEquals(false, JNum(0.0).truthy())
        assertEquals(0.0, (null as JsonValue?).numberOr0())
        assertEquals(3.0, JStr("3").numberOr0())
    }

    @Test
    fun toFixedMatchesJavaScript() {
        assertEquals("-7.8", toFixed(-7.8, 1))
        assertEquals("-0.0", toFixed(-0.04, 1))
        assertEquals("0.0", toFixed(-0.0, 1))
        assertEquals("1.00", toFixed(1.005, 2)) // 1.005 sebenarnya 1.00499..., JS juga memberi "1.00"
        assertEquals("0.13", toFixed(0.125, 2)) // tepat di tengah: menjauhi nol
    }

    @Test
    fun parsesIsoDatesLikeJavaScript() {
        val utc = java.time.ZoneOffset.UTC
        val wib = java.time.ZoneOffset.ofHours(7)
        assertEquals(1794700800000L, Ingest.parseDate("2026-11-15", wib))
        assertEquals(1794700800000L + 6 * 3600_000L, Ingest.parseDate("2026-11-15T06:00:00", utc))
        assertEquals(1794700800000L + 6 * 3600_000L - 7 * 3600_000L, Ingest.parseDate("2026-11-15T06:00:00", wib))
        assertEquals(1794700800000L + 6 * 3600_000L, Ingest.parseDate("2026-11-15T13:00:00+07:00", wib))
        assertEquals(1794700800000L + 6 * 3600_000L, Ingest.parseDate("2026-11-15T06:00:00Z", wib))
        assertEquals(null, Ingest.parseDate("bukan tanggal", utc))
        assertEquals(null, Ingest.parseDate("2026-13-40", utc))
    }
}
