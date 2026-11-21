package com.runnow.app.ui

import com.runnow.domain.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pengingat latihan dari rencana minggu (Settings.plan): satu notifikasi per hari untuk 14 hari ke depan pada jam
 * pilihan. Keluarannya JSON [{at,title,body,big}] yang diterima [com.runnow.app.PlanNotifier.set].
 * Hari "auto" tidak diingatkan karena sesinya baru diputuskan coach pada hari itu.
 */
object PlanReminders {
    private val LABEL = mapOf("easy" to "Easy", "quality" to "Quality", "long" to "Long", "rest" to "Istirahat")

    fun build(settings: Settings, nowMs: Long, zone: ZoneId, at: LocalTime, includeRest: Boolean): String {
        val arr = JSONArray()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        for (i in 0 until 14) {
            val day = today.plusDays(i.toLong())
            val p = settings.plan[day.dayOfWeek.value % 7] ?: continue
            val label = LABEL[p.t] ?: continue // "auto" dan jenis asing dilewati
            if (p.t == "rest" && !includeRest) continue
            val whenMs = day.atTime(at).atZone(zone).toInstant().toEpochMilli()
            if (whenMs <= nowMs) continue
            val km = if (p.km > 0) (Math.round(p.km * 10) / 10.0).toString().replace(".0", "").replace('.', ',') + " km" else ""
            val title = if (p.t == "rest") "Run Now · Sesi hari ini: Istirahat" else "Run Now · Sesi hari ini: $label"
            val body = if (p.t == "rest") "Pulih juga bagian dari latihan." else km.ifEmpty { "Buka Run Now untuk detail sesi." }
            arr.put(JSONObject().put("at", whenMs).put("title", title).put("body", body).put("big", body))
        }
        return arr.toString()
    }
}
