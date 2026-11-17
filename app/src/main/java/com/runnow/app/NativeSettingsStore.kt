package com.runnow.app

import android.content.Context
import com.runnow.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Preferensi atlet native; semua field opsional memakai nilai default domain bila belum pernah disimpan. */
class NativeSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("runnow_native_settings_v1", Context.MODE_PRIVATE)

    fun load(): Settings = runCatching {
        val o = JSONObject(prefs.getString("settings", "{}") ?: "{}")
        val planJson = o.optJSONObject("plan") ?: JSONObject()
        val plan = buildMap {
            val keys = planJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val day = key.toIntOrNull() ?: continue
                val value = planJson.optJSONObject(key) ?: continue
                put(day, PlanDay(value.optString("type", "auto"), value.optDouble("km", 0.0)))
            }
        }
        val flagJson = o.optJSONObject("flag")
        val rw = o.optJSONObject("runWalk")
        val athlete = o.optJSONObject("athlete")
        Settings(
            style = o.optString("style", "moderate"),
            plan = plan,
            race = o.optString("race", ""),
            raceDist = o.optDouble("raceDist", 0.0),
            raceGoal = o.optString("raceGoal", ""),
            rw = RunWalkSettings(rw?.optBoolean("on", false) ?: false, rw?.optInt("run", 225) ?: 225, rw?.optInt("walk", 30) ?: 30),
            heat = o.optString("heat", ""),
            flag = flagJson?.let { LegFlag(it.optInt("level"), it.optLong("since")) },
            athlete = AthleteSettings(athlete?.optInt("lthr", 0) ?: 0, athlete?.optString("ltPace", "") ?: ""),
            goal = o.optString("goal", "5K"),
        )
    }.getOrDefault(Settings())

    fun save(settings: Settings) {
        val plan = JSONObject().apply {
            settings.plan.forEach { (day, value) -> put(day.toString(), JSONObject().put("type", value.t).put("km", value.km)) }
        }
        val o = JSONObject()
            .put("style", settings.style).put("plan", plan)
            .put("race", settings.race).put("raceDist", settings.raceDist).put("raceGoal", settings.raceGoal)
            .put("runWalk", JSONObject().put("on", settings.rw.on).put("run", settings.rw.run).put("walk", settings.rw.walk))
            .put("heat", settings.heat)
            .put("flag", settings.flag?.let { JSONObject().put("level", it.lv).put("since", it.since) })
            .put("athlete", JSONObject().put("lthr", settings.athlete.lthr).put("ltPace", settings.athlete.ltPace))
            .put("goal", settings.goal)
        prefs.edit().putString("settings", o.toString()).apply()
    }

    fun clear() = prefs.edit().clear().apply()
}
