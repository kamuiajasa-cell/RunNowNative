package com.runnow.data

import com.runnow.domain.LegFlag
import com.runnow.domain.PlanDay
import com.runnow.domain.RunWalkSettings
import com.runnow.domain.Settings

/**
 * Pengaturan pengguna, disimpan dengan kunci dan format JSON yang sama seperti localStorage di runsense.html:
 * runsense_goal, runsense_style, runsense_plan, runsense_race, runsense_racex, runsense_rw, runsense_flag,
 * runsense_heat, runsense_rest (hari istirahat), runsense_adv (mode lanjutan).
 */
class SettingsStore(private val store: KeyValueStore) {
    companion object {
        const val K_GOAL = "runsense_goal"
        const val K_STYLE = "runsense_style"
        const val K_PLAN = "runsense_plan"
        const val K_RACE = "runsense_race"
        const val K_RACEX = "runsense_racex"
        const val K_RW = "runsense_rw"
        const val K_FLAG = "runsense_flag"
        const val K_HEAT = "runsense_heat"
        const val K_REST = "runsense_rest"
        const val K_ADV = "runsense_adv"
        val GOALS = listOf("5K", "10K", "Half", "Marathon", "Umum")
        val STYLES = listOf("moderate", "conservative", "aggressive")
    }

    private fun parse(key: String): JsonValue? = try {
        store.read(key)?.let { Json.parse(it) }
    } catch (e: Exception) {
        null
    }

    private fun num(v: JsonValue?): Double? = if (v is JNum) v.v else if (v is JStr) v.v.toDoubleOrNull() else null

    fun load(): Settings {
        val goal = store.read(K_GOAL)?.takeIf { it in GOALS } ?: "5K"
        val style = store.read(K_STYLE)?.takeIf { it in STYLES } ?: "moderate"
        val plan = LinkedHashMap<Int, PlanDay>()
        (parse(K_PLAN) as? JObj)?.fields?.forEach { (k, v) ->
            val d = k.toIntOrNull() ?: return@forEach
            val o = v as? JObj ?: return@forEach
            val t = (o.fields["t"] as? JStr)?.v ?: return@forEach
            if (d in 0..6) plan[d] = PlanDay(t, (num(o.fields["km"]) ?: 0.0).coerceIn(0.0, 60.0))
        }
        val racex = parse(K_RACEX) as? JObj
        val rwObj = parse(K_RW) as? JObj
        val rw = if (rwObj != null && (num(rwObj.fields["run"]) ?: 0.0) > 0) {
            RunWalkSettings(
                on = (rwObj.fields["on"] as? JBool)?.v ?: false,
                run = (num(rwObj.fields["run"]) ?: 225.0).toInt(),
                walk = (num(rwObj.fields["walk"]) ?: 30.0).toInt(),
            )
        } else RunWalkSettings()
        val fo = parse(K_FLAG) as? JObj
        val flag = fo?.let {
            val lv = (num(it.fields["lv"]) ?: 0.0).toInt()
            val since = (num(it.fields["since"]) ?: 0.0).toLong()
            if (lv == 1 || lv == 2) LegFlag(lv, since) else null
        }
        return Settings(
            style = style,
            plan = plan,
            race = store.read(K_RACE) ?: "",
            raceDist = num(racex?.fields?.get("d")) ?: 0.0,
            raceGoal = (racex?.fields?.get("g") as? JStr)?.v ?: "",
            rw = rw,
            heat = store.read(K_HEAT) ?: "",
            flag = flag,
            goal = goal,
        )
    }

    /** Simpan semuanya (dipanggil setelah tiap perubahan; datanya kecil). */
    fun save(s: Settings) {
        store.write(K_GOAL, s.goal)
        store.write(K_STYLE, s.style)
        store.write(
            K_PLAN,
            Json.stringify(JObj(LinkedHashMap<String, JsonValue>().also { m ->
                s.plan.toSortedMap().forEach { (d, p) ->
                    m[d.toString()] = JObj(linkedMapOf("t" to JStr(p.t), "km" to JNum(p.km)))
                }
            })),
        )
        store.write(K_RACE, s.race)
        store.write(K_RACEX, Json.stringify(JObj(linkedMapOf("d" to JNum(s.raceDist), "g" to JStr(s.raceGoal)))))
        store.write(
            K_RW,
            Json.stringify(JObj(linkedMapOf("on" to JBool(s.rw.on), "run" to JNum(s.rw.run.toDouble()), "walk" to JNum(s.rw.walk.toDouble())))),
        )
        store.write(K_HEAT, s.heat)
        val f = s.flag
        if (f == null) store.remove(K_FLAG)
        else store.write(K_FLAG, Json.stringify(JObj(linkedMapOf("lv" to JNum(f.lv.toDouble()), "since" to JNum(f.since.toDouble())))))
    }

    /** Hari yang ditandai istirahat ("yyyy-MM-dd") atau "". */
    var restDay: String
        get() = store.read(K_REST) ?: ""
        set(v) {
            if (v.isEmpty()) store.remove(K_REST) else store.write(K_REST, v)
        }

    var advanced: Boolean
        get() = store.read(K_ADV) == "1"
        set(v) = store.write(K_ADV, if (v) "1" else "0")
}
