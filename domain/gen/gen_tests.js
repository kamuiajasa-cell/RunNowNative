process.env.TZ = "UTC";
const { run, NOW } = require("./harness");
const S = require("./scen");

const D = (n) => (n === null || n === undefined ? "null" : Number.isInteger(n) ? n + ".0" : String(n));
const L = (n) => n + "L";
const Sx = (s) => JSON.stringify(s).replace(/\$/g, "\\$");
const I = (n) => (n === null || n === undefined ? "null" : String(n));
const kind = (k) => (k === "run" ? "RUN" : k === "walk" ? "WALK" : "OTHER");

let out = `package com.runnow.domain

// DIHASILKAN OTOMATIS dari kode asli runsense.html (baris 797-1130) yang dijalankan di Node dengan TZ=UTC.
// Jangan edit tangan; ubah skenario di gen/scen.js lalu jalankan ulang generator.

import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoldenTest {
    private val now = ${L(NOW)}
    private val eps = 1e-9

    private fun near(exp: Double?, act: Double?, msg: String = "") {
        if (exp == null || act == null) { assertEquals(exp, act, msg); return }
        assertTrue(kotlin.math.abs(exp - act) <= eps, "$msg: diharapkan $exp, dapat $act")
    }

    private fun act(start: Long, kind: String, min: Double, km: Double, hr: Double, elev: Double, id: String, name: String, maxHr: Double, load: Double) =
        Act(start, ActKind.valueOf(kind), min, km, hr, elev, id, name, maxHr, id, load)

    private fun snap(rest: Double, max: Double, age: Int, afS: Int, acts: List<Act>, wl: List<Wellness>, vx: Map<Long, String>) =
        Snapshot(Profile(rest, max, age, afS), acts, wl, vx, now, ZoneOffset.UTC)
`;

for (const sc of S) {
  const o = run(sc);
  const st = o.state;
  const fn = "scenario_" + sc.name.replace(/[^A-Za-z0-9_]/g, "_");
  out += `
    private fun ${fn}_snap(): Snapshot {
        val acts = listOf(
${st.acts.map(a => `            act(${L(a[0])}, "${kind(a[1])}", ${D(a[2])}, ${D(a[3])}, ${D(a[4])}, ${D(a[5])}, ${Sx(a[6])}, ${Sx(a[7])}, ${D(a[8])}, ${D(a[10])}),`).join("\n")}
        )
        val wl = listOf(
${st.wellness.map(w => `            Wellness(${Sx(w.id)}${w.restingHR != null ? `, restingHR = ${D(w.restingHR)}, rhrSrc = ${Sx(w.rhrSrc)}` : ""}${w.hrv != null ? `, hrv = ${D(w.hrv)}, hrvSrc = ${Sx(w.hrvSrc)}${w.hrvSpot ? ", hrvSpot = true" : ""}` : ""}${w.sleepSecs != null ? `, sleepSecs = ${D(w.sleepSecs)}` : ""}${w.stress != null ? `, stress = ${D(w.stress)}` : ""}),`).join("\n")}
        )
        val vx = mapOf<Long, String>(${Object.entries(st.vx).map(([k, v]) => `${L(k)} to ${Sx(v)}`).join(", ")})
        return snap(${D(st.rest)}, ${D(st.max)}, ${st.age}, ${st.afS}, acts, wl, vx)
    }

    @Test
    fun ${fn}() {
        val sn = ${fn}_snap()
        // estMax
        val e = sn.estMax()
        near(${D(o.estMax.m)}, e.m, "estMax.m")
        near(${D(o.estMax.perf)}, e.perf, "estMax.perf")
        near(${D(o.estMax.hrr)}, e.hrr, "estMax.hrr")
        assertEquals(${Sx(o.estMax.src)}, e.src)
        assertEquals(${Sx(o.estMax.conf)}, e.conf)
        assertEquals(${o.estMax.n}, e.n)
        // stage
        val g = sn.stage()
        assertEquals(${o.stage.s}, g.s)
        near(${D(o.stage.m)}, g.m, "stage.m")
        assertEquals(${I(o.stage.raw)}, g.raw)
        assertEquals(${o.stage.unknown}, g.unknown)
        assertEquals(${o.afSAfterStage}, g.afS)
        // load
        val expLoads = listOf(${o.loads.join(", ")})
        assertEquals(expLoads, sn.acts.map { sn.loadOf(it) })
        assertEquals(${o.ld7}, sn.loadDays(7))
        assertEquals(${o.ld28}, sn.loadDays(28))
        // rhr28 / maxHr
        ${o.rhr28 ? `assertEquals(Rhr28(${o.rhr28.avg}, ${o.rhr28.n}), sn.rhr28())` : `assertNull(sn.rhr28())`}
        near(${D(o.maxHr)}, sn.maxHr(), "maxHr")
        // autonomic
        val au = sn.autonomic()
        assertEquals(${o.autonomic.lock}, au.lock, "lock")
        assertEquals(${o.autonomic.severe}, au.severe, "severe")
        assertEquals(${o.autonomic.warn}, au.warn, "warn")
        assertEquals(${Sx(o.autonomic.why)}, au.why)
        assertEquals(${I(o.autonomic.hrvPct)}, au.hrvPct)
        assertEquals(${I(o.autonomic.rhrD)}, au.rhrD)
        assertEquals(${I(o.autonomic.hrv)}, au.hrv)
        assertEquals(${I(o.autonomic.rhr)}, au.rhr)
        near(${D(o.autonomic.sleepH)}, au.sleepH, "sleepH")
        near(${D(o.autonomic.stress)}, au.stress, "stress")
        assertEquals(${I(o.autonomic.stressD)}, au.stressD)
        // readiness
        assertEquals(${I(o.readiness)}, sn.readiness())
        // detectType (8 aktivitas terakhir)
        val expDetect = listOf(
${o.detect.map(d => `            listOf(${L(d.start)}.toString(), ${Sx(d.type)}, ${Sx(d.conf)}, ${Sx(d.why)}, "${d.confirmed}"),`).join("\n")}
        )
        val gotDetect = sn.acts.takeLast(8).map { a -> val t = sn.detectType(a, g); listOf(a.start.toString(), t.type, t.conf, t.why, t.confirmed.toString()) }
        assertEquals(expDetect, gotDetect)
    }
`;
}

// Tes helper (sekali saja, dari skenario pertama)
const m = run(S[0]).misc;
out += `
    @Test
    fun helpers() {
${m.mets.map(([p, v]) => `        near(${D(v)}, metsAt(${D(p)}), "metsAt(${p})")`).join("\n")}
${m.paceOf.map(([p, v]) => `        near(${D(v)}, paceOf(${D(p)}), "paceOf(${p})")`).join("\n")}
${m.pct.map(([p, v]) => `        near(${D(v)}, pctAt(${D(p)}), "pctAt(${p})")`).join("\n")}
${m.dur.map(([p, v]) => `        assertEquals(${Sx(v)}, durText(${D(p)}))`).join("\n")}
${m.band.map(([p, v]) => `        assertEquals(${v === null ? "null" : Sx(v)}, band(${p === null ? "null" : D(p)}))`).join("\n")}
${m.f1.map(([p, v]) => `        assertEquals(${Sx(v)}, f1(${p === null ? "null" : D(p)}))`).join("\n")}
${m.mp.map(([p, v]) => `        assertEquals(${Sx(v)}, mp(${D(p)}))`).join("\n")}
${m.fp.map(([p, a, b]) => `        assertEquals(${Sx(a)}, formatPace(${D(p)}))\n        assertEquals(${Sx(b)}, formatTime(${D(p)}))`).join("\n")}
${m.fmtM.map(([p, v]) => `        assertEquals(${Sx(v)}, fmtM(${D(p)}))`).join("\n")}
${m.fmtHm.map(([p, v]) => `        assertEquals(${Sx(v)}, fmtHm(${D(p)}))`).join("\n")}
    }

    @Test
    fun walkRun() {
${m.wp.map(([k, s, w]) => `        assertEquals(WalkRunWorkout(${Sx(w.k)}, ${w.tot}, listOf(${w.steps.map(x => `${Sx(x[0])} to ${Sx(x[1])}`).join(", ")})), walkRunWorkout(${k}, ${D(s)}))`).join("\n")}
    }
}
`;
require("fs").writeFileSync(__dirname + "/../src/test/kotlin/com/runnow/domain/GoldenTest.kt", out);
console.log("lines", out.split("\n").length);
