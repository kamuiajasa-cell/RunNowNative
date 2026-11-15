// Menjalankan kode ASLI dari runsense.html (baris 797-1130) dengan state buatan, untuk membuat nilai acuan test Kotlin.
const fs = require("fs");
const lines = fs.readFileSync(process.env.RUNSENSE_HTML || "runsense.html", "utf8").split("\n");
const OFF = lines.findIndex((l) => l.startsWith("  const clamp = (v, a, b)")) + 1 - 797; // geser otomatis bila nomor baris runsense.html berubah
const code = lines.slice(796 + OFF, 1130 + OFF).join("\n");
const NOW = Date.UTC(2026, 10, 15, 8, 0, 0);
const realNow = Date.now; Date.now = () => NOW;

function mulberry32(a){return function(){a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}

function build(sc) {
  const rnd = mulberry32(sc.seed);
  const acts = [];
  for (let d = sc.days; d >= 0; d--) {
    if (rnd() < sc.skip) continue;
    const start = NOW - d * 864e5 + Math.floor(rnd() * 3) * 36e5;
    const r = rnd();
    const kind = r < 0.8 ? "run" : r < 0.9 ? "walk" : "other";
    const km = kind === "run" ? Math.round((2 + rnd() * (d % 7 === 0 ? 12 : 6)) * 100) / 100 : kind === "walk" ? Math.round((1 + rnd() * 3) * 100) / 100 : 0;
    const pace = (sc.paceBase || 5) + rnd() * 2.2;
    const min = kind === "run" ? Math.round(km * pace * 100) / 100 : Math.round((20 + rnd() * 40) * 100) / 100;
    const hr = sc.hr ? Math.round(140 + rnd() * 35) : 0;
    const load = sc.icuLoad && rnd() < 0.5 ? Math.round(30 + rnd() * 90) : 0;
    const id = "hw" + start;
    acts.push([start, kind, min, km, hr, Math.round(rnd() * 80), id, "Lari " + d, hr ? hr + 12 : 0, id, load]);
  }
  const wellness = [];
  for (let d = sc.wdays; d >= 0; d--) {
    const id = new Date(NOW - d * 864e5).toISOString().slice(0, 10);
    const drop = d < 3 ? sc.hrvDrop : 0;
    const w = { id };
    if (rnd() < 0.95) { w.restingHR = Math.round(52 + rnd() * 4 + (d < 3 ? sc.rhrUp : 0)); w.rhrSrc = "jam"; }
    if (rnd() < 0.9) { w.hrv = Math.round((60 + rnd() * 10) * (1 - drop)); w.hrvSrc = "jam"; if (rnd() < 0.08) w.hrvSpot = true; }
    if (rnd() < 0.9) w.sleepSecs = Math.round((6.2 + rnd() * 1.6 - (d < 2 ? sc.sleepCut : 0)) * 3600);
    if (rnd() < 0.8) w.stress = Math.round(30 + rnd() * 10 + (d < 2 ? sc.stressUp : 0));
    wellness.push(w);
  }
  return { acts, wellness };
}

function run(sc) {
  const { acts, wellness } = build(sc);
  const vx = {}; const runs = acts.filter(a => a[1] === "run");
  if (runs.length > 3) vx[runs[runs.length - 3][0]] = "long";
  const state = { acts, wellness, vx, rest: sc.rest, max: sc.max, age: sc.age, afS: sc.afS };
  const fn = new Function("state", code + "\nreturn {estMax,stage,loadOf,loadDays,autonomic,readiness,detectType,metsAt,metsOf,paceOf,pctAt,f1,mp,formatPace,formatTime,fmtM,fmtHm,durText,band,wp,rhr28,maxHr,restHr,d0,today};");
  const E = fn(state);
  const out = { sc, state: JSON.parse(JSON.stringify(state)) };
  out.estMax = E.estMax();
  const st1 = E.stage(); out.afSAfterStage = state.afS;
  out.stage = { s: st1.s, m: st1.m, raw: st1.raw === undefined ? null : st1.raw, unknown: !!st1.unknown };
  out.loads = acts.map(a => E.loadOf(a));
  out.ld7 = E.loadDays(7); out.ld28 = E.loadDays(28);
  const au = E.autonomic(); out.autonomic = au;
  out.readiness = E.readiness();
  out.detect = acts.slice(-8).map(a => { const t = E.detectType(a); return { start: a[0], type: t.type, conf: t.conf, why: t.why, confirmed: !!t.confirmed }; });
  out.rhr28 = E.rhr28();
  out.maxHr = E.maxHr();
  out.misc = {
    mets: [3.5,4,4.2,5,5.25,6.1,7.7,9.99,12.5,14,16].map(p => [p, E.metsAt(p)]),
    paceOf: [4,5.5,7.2,9,12,14.5].map(m => [m, E.paceOf(m)]),
    pct: [3,4,5,10,25,40,67,80,90,120].map(m => [m, E.pctAt(m)]),
    dur: [0.99,0.9,0.82,0.7,0.61,0.5].map(p => [p, E.durText(p)]),
    band: [null,5,7.9,10,10.9,14].map(m => [m, E.band(m)]),
    f1: [null, 0, 4.95, 12.25, 3.04, 7.0, -0.05].map(n => [n, E.f1(n)]),
    mp: [0, 4.9833, 5.5, 6.999, 12.01].map(p => [p, E.mp(p)]),
    fp: [0, 299.5, 360, 3599.5].map(s => [s, E.formatPace(s), E.formatTime(s)]),
    fmtM: [0, 12.4, 59.5, 60, 61.5, 125.2].map(m => [m, E.fmtM(m)]),
    fmtHm: [5.5, 6.25, 7.999].map(h => [h, E.fmtHm(h)]),
    wp: [0,1,2].flatMap(k => [1, 0.8, 0.5, 0.2, 0].map(s => [k, s, E.wp(k, s)]))
  };
  return out;
}
module.exports = { run, NOW };
