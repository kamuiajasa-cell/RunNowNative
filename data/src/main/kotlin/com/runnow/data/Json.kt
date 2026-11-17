package com.runnow.data

import java.math.BigDecimal

/** Pohon JSON minimal (tanpa dependensi). Urutan kunci objek dipertahankan. */
sealed class JsonValue

object JNull : JsonValue() {
    override fun toString() = "null"
}

data class JBool(val v: Boolean) : JsonValue()

data class JNum(val v: Double) : JsonValue()

data class JStr(val v: String) : JsonValue()

data class JArr(val items: List<JsonValue>) : JsonValue()

data class JObj(val fields: Map<String, JsonValue>) : JsonValue()

class JsonException(message: String) : Exception(message)

object Json {
    fun parse(text: String): JsonValue {
        val p = Parser(text)
        p.ws()
        val v = p.value()
        p.ws()
        if (p.i != text.length) throw JsonException("karakter berlebih di posisi ${p.i}")
        return v
    }

    /** Seperti JSON.stringify (tanpa spasi): bilangan bulat tanpa ".0", NaN/Infinity menjadi null. */
    fun stringify(v: JsonValue): String = StringBuilder().also { write(v, it) }.toString()

    private fun write(v: JsonValue, sb: StringBuilder) {
        when (v) {
            is JNull -> sb.append("null")
            is JBool -> sb.append(if (v.v) "true" else "false")
            is JNum -> sb.append(numText(v.v))
            is JStr -> quote(v.v, sb)
            is JArr -> {
                sb.append('[')
                v.items.forEachIndexed { i, x -> if (i > 0) sb.append(','); write(x, sb) }
                sb.append(']')
            }
            is JObj -> {
                sb.append('{')
                var first = true
                for ((k, x) in v.fields) {
                    if (!first) sb.append(',')
                    first = false
                    quote(k, sb)
                    sb.append(':')
                    write(x, sb)
                }
                sb.append('}')
            }
        }
    }

    /** Teks bilangan ala JavaScript. */
    fun numText(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return "null"
        if (d == 0.0) return "0"
        if (d == Math.floor(d) && Math.abs(d) < 1e21) return BigDecimal(d).toPlainString()
        val s = d.toString()
        return if (s.contains('E') || s.contains('e')) BigDecimal(s).stripTrailingZeros().toPlainString() else s
    }

    private fun quote(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c == '\b' -> sb.append("\\b")
                c == '\u000C' -> sb.append("\\f")
                c < ' ' -> sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var i = 0

        fun ws() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
        }

        fun fail(msg: String): Nothing = throw JsonException("$msg di posisi $i")

        fun value(): JsonValue {
            if (i >= s.length) fail("data berakhir terlalu cepat")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> JStr(str())
                't' -> lit("true", JBool(true))
                'f' -> lit("false", JBool(false))
                'n' -> lit("null", JNull)
                else -> if (c == '-' || c in '0'..'9') num() else fail("karakter tak terduga '$c'")
            }
        }

        fun lit(word: String, v: JsonValue): JsonValue {
            if (!s.startsWith(word, i)) fail("literal tidak valid")
            i += word.length
            return v
        }

        fun num(): JsonValue {
            val st = i
            if (s[i] == '-') i++
            while (i < s.length && s[i] in '0'..'9') i++
            if (i < s.length && s[i] == '.') { i++; while (i < s.length && s[i] in '0'..'9') i++ }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                while (i < s.length && s[i] in '0'..'9') i++
            }
            return JNum(s.substring(st, i).toDoubleOrNull() ?: fail("bilangan tidak valid"))
        }

        fun str(): String {
            i++ // "
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) fail("string tidak ditutup")
                val c = s[i++]
                if (c == '"') return sb.toString()
                if (c != '\\') { sb.append(c); continue }
                if (i >= s.length) fail("escape terpotong")
                when (val e = s[i++]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        if (i + 4 > s.length) fail("escape \\u terpotong")
                        sb.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: fail("escape \\u tidak valid"))
                        i += 4
                    }
                    else -> fail("escape tidak dikenal '$e'")
                }
            }
        }

        fun arr(): JsonValue {
            i++ // [
            val items = ArrayList<JsonValue>()
            ws()
            if (i < s.length && s[i] == ']') { i++; return JArr(items) }
            while (true) {
                ws()
                items += value()
                ws()
                if (i >= s.length) fail("array tidak ditutup")
                if (s[i] == ',') { i++; continue }
                if (s[i] == ']') { i++; return JArr(items) }
                fail("array tidak valid")
            }
        }

        fun obj(): JsonValue {
            i++ // {
            val f = LinkedHashMap<String, JsonValue>()
            ws()
            if (i < s.length && s[i] == '}') { i++; return JObj(f) }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') fail("kunci objek harus string")
                val k = str()
                ws()
                if (i >= s.length || s[i] != ':') fail("titik dua hilang")
                i++
                ws()
                f[k] = value()
                ws()
                if (i >= s.length) fail("objek tidak ditutup")
                if (s[i] == ',') { i++; continue }
                if (s[i] == '}') { i++; return JObj(f) }
                fail("objek tidak valid")
            }
        }
    }
}

// ---------- Pembantu membaca JSON dengan semantik JavaScript ----------

operator fun JsonValue?.get(key: String): JsonValue? = (this as? JObj)?.fields?.get(key)

val JsonValue?.items: List<JsonValue>? get() = (this as? JArr)?.items

/** Truthy ala JS: null/undefined/false/0/NaN/"" salah. */
fun JsonValue?.truthy(): Boolean = when (this) {
    null, is JNull -> false
    is JBool -> v
    is JNum -> v != 0.0 && !v.isNaN()
    is JStr -> v.isNotEmpty()
    else -> true
}

/** Number(x) ala JS. undefined -> NaN. */
fun JsonValue?.number(): Double = when (this) {
    null -> Double.NaN
    is JNull -> 0.0
    is JBool -> if (v) 1.0 else 0.0
    is JNum -> v
    is JStr -> if (v.isBlank()) 0.0 else v.trim().toDoubleOrNull() ?: Double.NaN
    else -> Double.NaN
}

/** Number(x || 0). */
fun JsonValue?.numberOr0(): Double = if (truthy()) number() else 0.0

/** String(x) ala JS untuk nilai skalar; null/undefined -> null. */
fun JsonValue?.text(): String? = when (this) {
    null, is JNull -> null
    is JStr -> v
    is JNum -> Json.numText(v)
    is JBool -> v.toString()
    else -> Json.stringify(this)
}

/** `x || fallback` untuk string. */
fun JsonValue?.textOr(fallback: String): String = if (truthy()) text() ?: fallback else fallback
