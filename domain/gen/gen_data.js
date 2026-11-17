// Membuat ../data/src/test/resources/golden_data.txt (sync, ingestWatch, ensureStream, clMatch, persist) dari kode ASLI
// runsense.html (Langkah 6). Pakai: RUNSENSE_HTML=/path/ke/runsense.html node gen_data.js
process.env.TZ = "UTC";
const fs = require("fs");
const { buildDataset, DAY } = require("./design_harness");
const { makeEnv } = require("./data_harness");

function mulberry32(a){return function(){a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
const M = mulberry32(20261120);
const pick = (a) => a[Math.floor(M() * a.length)];
const chance = (p) => M() < p;
const ri = (a, b) => a + Math.floor(M() * (b - a + 1));
const KEY = "runsense_auto_v2", CK = "runsense_clim";
const iso = (ms) => new Date(ms).toISOString().slice(0, 19);

const base = (i, o) => ({
  shift: i % 7, seed: 9000 + i, days: 24, skip: 0.2, hr: true, icuLoad: false, hillRate: 3, streak: 0, yHard: false, yLong: false,
  y2Long: false, todayRun: false, thr: 0, wdays: 6, hrvDrop: 0, rhrUp: 0, sleepCut: 0, stressUp: 0,
  rest: 55, max: 188, age: 35, afS: 0, paceBase: 6.0, vxRace: false, form: null, ...o,
});

/** Dataset dengan beberapa baris Intervals (bukan jam), vx, dan sx supaya jalur dedupe/replace terpakai. */
function mkDs(id, i, o) {
  const ds = buildDataset(id, base(i, o));
  ds.sx = ds.sx || {};
  const runs = ds.acts.filter((a) => a[1] === "run");
  runs.slice(0, 4).forEach((a, k) => { ds.vx[a[0]] = pick(["easy", "long", "thr"]); if (k % 2 === 0) ds.sx[a[9]] = { fast: { p12: 5.2, med: 6, pct: 12 }, tiz: [10, 40, 30, 15, 5] }; });
  for (let k = 0; k < 3; k++) {
    const st = ds.now - ri(3, 40) * DAY - ri(0, 20) * 36e5;
    ds.acts.push([st, "run", ri(30, 70), ri(5, 12), ri(130, 160), ri(0, 80), "icu_i" + (100 + k), "Lari ICU", ri(165, 185), "i" + (100 + k), ri(30, 90)]);
  }
  ds.acts.sort((a, b) => a[0] - b[0]);
  return ds;
}
function initOf(ds, extra) {
  const E = makeEnv(ds, extra || {}, null, {});
  E.persist();
  return E.store.get(KEY);
}
function scEnv(init, now, extra, cl, icu) {
  const st = JSON.parse(init);
  const ds = { acts: st.acts, wellness: st.wellness, vx: st.vx, sx: st.sx, rest: st.rest, max: st.max, age: st.age, afS: st.afS, now };
  const E = makeEnv(ds, { icuName: st.icuName, icuLast: st.icuLast, hwLast: st.hwLast, restManual: st.restManual, lthrC: st.lthrC, ltpC: st.ltpC, ...(extra || {}) }, cl, icu);
  E.store.set(KEY, init);
  if (cl) E.store.set(CK, JSON.stringify(cl));
  return E;
}

// ---------- Intervals.icu palsu ----------
const NAMES = ["Lari pagi", "Easy run", "Tempo 5K", "", "Long run di sekitar waduk dan taman kota yang sangat panjang sekali", "Jalan sore", "Gowes", "Fartlek 🏃"];
function icuAct(start, o = {}) {
  const type = o.type || pick(["Run", "Run", "Run", "TrailRun", "VirtualRun", "Walk", "Hike", "Ride", "WeightTraining"]);
  const isRun = /run/i.test(type);
  const a = {
    id: o.id !== undefined ? o.id : pick([ri(1e6, 9e6), "i" + ri(1e6, 9e6)]),
    type, name: o.name !== undefined ? o.name : pick(NAMES),
    distance: isRun ? ri(3000, 21000) : ri(0, 12000), moving_time: ri(900, 6000),
  };
  if (chance(0.5)) a.start_date_local = iso(start); else a.start_date = iso(start) + "Z";
  if (chance(0.7)) a.average_heartrate = ri(125, 165);
  if (chance(0.5)) a.max_heartrate = ri(160, 190); else if (chance(0.3)) a.max_hr = ri(160, 190);
  if (chance(0.6)) a.total_elevation_gain = ri(0, 120);
  const lk = pick(["icu_training_load", "training_load", "hrss", null]);
  if (lk) a[lk] = ri(20, 120) + (chance(0.3) ? 0.5 : 0);
  if (chance(0.15)) { delete a.moving_time; a.elapsed_time = ri(900, 6000); }
  return Object.assign(a, o.extra || {});
}
function icuActs(ds) {
  const out = [];
  const hwRuns = ds.acts.filter((a) => a[1] === "run" && /^hw/.test(a[9]));
  const n = ri(6, 14);
  for (let k = 0; k < n; k++) {
    const m = pick(["near", "near", "far", "far", "far", "replace", "bad"]);
    if (m === "near" && hwRuns.length) { const h = chance(0.6) ? pick(hwRuns.slice(0, 4)) : pick(hwRuns); out.push(icuAct(h[0] + ri(-9, 9) * 60e3, { type: "Run" })); }
    else if (m === "replace") { const id = "i" + (100 + ri(0, 2)); const o = ds.acts.find((a) => a[9] === id); out.push(icuAct(o[0] + (chance(0.5) ? 0 : ri(1, 10) * 60e3), { id, type: "Run" })); }
    else if (m === "bad") out.push(pick([{ id: "x1", type: "Run", distance: 5000, moving_time: 1800 }, icuAct(ds.now - 5 * DAY, { type: "Run", extra: { distance: 0 } }), icuAct(ds.now - 6 * DAY, { type: "Run", extra: { moving_time: 0, elapsed_time: 0 } }), null, { id: 5, type: "Run", start_date: "not-a-date", distance: 4000, moving_time: 1500 }]));
    else out.push(icuAct(ds.now - ri(1, 9) * DAY - ri(0, 20) * 36e5));
  }
  return out;
}
function icuWell(ds) {
  const out = [];
  for (let d = 0; d < ri(3, 12); d++) {
    const day = new Date(ds.now - d * DAY).toISOString().slice(0, 10);
    const w = {}; w[pick(["id", "id", "date"])] = day;
    if (chance(0.8)) { w.ctl = ri(30, 70) + 0.25; w.atl = ri(30, 90) + 0.5; }
    if (chance(0.3)) w.tsb = ri(-20, 10);
    if (chance(0.6)) w[pick(["restingHR", "resting_hr"])] = ri(48, 62);
    if (chance(0.5)) w[pick(["hrv", "hrvSDNN"])] = ri(35, 90);
    if (chance(0.1)) w.ctl = String(ri(30, 70));
    out.push(w);
  }
  out.push({ id: "bukan-tanggal", ctl: 1 }, { date: "2026-11-1", ctl: 2 });
  return out;
}
function icuAthlete() {
  return pick([
    { name: "Andi Pratama", max_hr: 188, resting_hr: 52, birthday: "1990-05-04" },
    { athlete: { firstname: "Budi", lastname: "Santoso", max_hr: 182, resting_hr: 58, birthday: "1984-12-31" } },
    { firstname: "Citra", resting_hr: 0, max_hr: 0 },
    { name: "Dewi dengan nama yang sangat sangat panjang sekali untuk uji batas empat puluh karakter" },
    {}, null,
    { __status: 404 },
  ]);
}

// ---------- jam ----------
function hwPts(n, o = {}) {
  const pts = []; let t = 0, la = -7.8 + ri(0, 9) / 100, lo = 112.0 + ri(0, 9) / 100;
  for (let i = 0; i < n; i++) {
    t += 10; la += ri(-3, 3) / 10000; lo += ri(0, 4) / 10000;
    const p = [t, ri(125, 165), Math.round((pick([2.6, 3.1, 3.7]) + ri(-3, 3) / 10) * 10) / 10, ri(0, 30), ri(60, 90), o.noGps ? null : la, o.noGps ? null : lo];
    if (o.nullSome && chance(0.1)) { p[5] = null; p[6] = null; }
    pts.push(p);
  }
  return pts;
}
function watchData(ds) {
  const wellness = [], workouts = [];
  for (let d = 0; d < ri(2, 8); d++) {
    const w = { id: new Date(ds.now - d * DAY).toISOString().slice(0, 10) };
    if (chance(0.7)) w.restingHR = pick([ri(45, 62), 0, 20, "55"]);
    if (chance(0.6)) { w.hrv = pick([ri(30, 90), 0, -5]); if (chance(0.3)) w.hrvSrc = "spot"; }
    if (chance(0.6)) w.sleepSecs = ri(18000, 30000);
    if (chance(0.5)) w.sleepScore = ri(50, 95);
    if (chance(0.5)) w.steps = ri(2000, 15000);
    if (chance(0.4)) w.spO2 = ri(93, 99);
    if (chance(0.4)) { w.stress = ri(10, 60); w.stressMax = ri(60, 95); w.stressNight = ri(5, 40); }
    wellness.push(w);
  }
  wellness.push({ id: "2026-13", restingHR: 50 }, null, { id: "2026-11-02T08:00:00", restingHR: 51 });
  const hwRuns = ds.acts.filter((a) => a[1] === "run");
  for (let k = 0; k < ri(4, 9); k++) {
    const m = pick(["new", "new", "matchIcu", "again", "walk", "ride", "bad", "short"]);
    const start = ds.now - ri(1, 12) * DAY - ri(0, 20) * 36e5;
    const w = { start, kind: "run", durSec: ri(1200, 5400), distM: ri(3000, 18000), kcal: 300, steps: 5000, avgHr: ri(130, 165), maxHr: ri(165, 185), elevUp: ri(0, 90), name: pick(NAMES), iid: "hw" + start };
    if (m === "matchIcu") { const o = ds.acts.find((a) => a[9] && !/^hw/.test(a[9])); w.start = o[0] + ri(-10, 10) * 60e3; w.iid = "hw" + w.start; }
    if (m === "again") { const o = pick(hwRuns.filter((a) => /^hw/.test(a[9]))); if (o) { w.start = o[0]; w.iid = o[9]; w.distM = o[3] * 1000 + 100; } }
    if (m === "walk") { w.kind = "walk"; w.durSec = ri(600, 3000); }
    if (m === "ride") w.kind = "ride";
    if (m === "bad") Object.assign(w, pick([{ durSec: 0 }, { iid: "" }, { start: 0 }, { distM: 0 }]));
    if (m === "short") w.durSec = ri(300, 900);
    const pm = pick(["full", "full", "short", "none", "nullgps", "nogps"]);
    if (pm === "full") w.pts = hwPts(ri(60, 250)); else if (pm === "short") w.pts = hwPts(ri(5, 39)); else if (pm === "nullgps") w.pts = hwPts(ri(60, 120), { nullSome: true }); else if (pm === "nogps") w.pts = hwPts(ri(60, 120), { noGps: true });
    workouts.push(w);
  }
  return { wellness, acts: [], workouts };
}

// ---------- stream ----------
function mkStream(n, dt, v0, o = {}) {
  const time = [], dist = [], hr = []; let d = 0;
  for (let i = 0; i < n; i++) {
    let v = v0; if (o.fast && d < 2000) v *= 1.14; if (o.fade && i / n > 0.7) v *= 0.88;
    time.push(i * dt); dist.push(Math.round(d)); d += v * dt; hr.push(Math.round(140 + (o.drift || 0) * (i / n) + ri(-3, 3)));
  }
  return { time, dist, hr };
}
function icuStream(form, s, withHr = true) {
  if (form === "array") { const a = [{ type: "time", data: s.time }, { type: "distance", data: s.dist }]; if (withHr) a.push({ type: "heartrate", data: s.hr }); return a; }
  const o = { time: { data: s.time }, distance: s.dist }; if (withHr) o.heartrate = { data: s.hr }; return o;
}
function icuLatLng(form, n, dur) {
  const t = [], la = [], lo = []; let a = -7.8 + ri(0, 9) / 100, b = 112.0 + ri(0, 9) / 100;
  for (let i = 0; i < n; i++) { t.push(i * Math.floor(dur / n)); a += ri(-3, 3) / 10000; b += ri(0, 4) / 10000; la.push(a); lo.push(b); }
  if (form === "split") return [{ type: "time", data: t }, { type: "latlng", data: la, data2: lo }];
  if (form === "pairs") return { time: { data: t }, latlng: { data: la.map((x, i) => [x, lo[i]]) } };
  return [{ type: "time", data: t }];
}

// ---------- rangkaian skenario ----------
const SC = [];
const j = JSON.stringify;
function scenario(kind, name, build) { SC.push({ kind, name, build }); }
/** Cache cuaca besar dan deterministik: yang dibandingkan hanya kunci dan waktu simpannya. */
function slim(ck) { if (!ck) return "-"; const o = JSON.parse(ck); o.wx = Object.fromEntries(Object.entries(o.wx).map(([k, v]) => [k, v.t])); return j(o); }

// sync
for (let i = 0; i < 16; i++) {
  scenario("sync", "sync" + i, () => {
    const ds = mkDs("sy" + i, i, i % 4 === 3 ? { days: 3, skip: 0.9 } : { days: pick([18, 26]) });
    if (i % 5 === 2) { ds.rest = 0; ds.max = 0; ds.age = 0; }
    const init = initOf(ds, i % 4 === 1 ? { icuName: "Lama" } : {});
    const fail = i >= 12 ? pick(["acts500", "acts401", "net", "wellfail"]) : null;
    const icu = { "athlete/0": icuAthlete(), "athlete/0/activities": icuActs(ds), "athlete/0/wellness": icuWell(ds) };
    if (fail === "acts500") icu["athlete/0/activities"] = { __status: 500 };
    if (fail === "acts401") icu["athlete/0/activities"] = { __status: 401 };
    if (fail === "net") icu["athlete/0/activities"] = { __throw: 1 };
    if (fail === "wellfail") icu["athlete/0/wellness"] = { __status: 500 };
    const E = scEnv(init, ds.now, {}, null, icu);
    return (async () => {
      const ok = await E.sync();
      return [["I", "state", init], ...Object.entries(icu).map(([p, v]) => ["I", "icu", p, j(v)]), ["I", "now", ds.now], ["I", "op", "sync"],
        ["O", "ok", String(ok)], ["O", "msg", E.SYNC.msg || "-"], ["O", "store", E.store.get(KEY) || "-"]];
    })();
  });
}
// watch
for (let i = 0; i < 14; i++) {
  scenario("watch", "watch" + i, () => {
    const ds = mkDs("wa" + i, 20 + i, { days: pick([18, 26]), rest: i % 4 === 0 ? 0 : 55 });
    const init = initOf(ds);
    const cl = i % 3 === 0 ? { loc: null, ctx: {}, wx: {}, gps: Object.fromEntries(ds.acts.filter((a) => a[1] === "run").slice(0, 2).map((a) => [a[0], { none: 1 }])) } : { loc: null, ctx: {}, wx: {}, gps: {} };
    const wd = watchData(ds);
    const E = scEnv(init, ds.now, {}, cl, {});
    E.ingestWatch(JSON.parse(JSON.stringify(wd)));
    return [["I", "state", init], ["I", "clim", j(cl)], ["I", "now", ds.now], ["I", "watch", j(wd)], ["I", "op", "watch"],
      ["O", "store", E.store.get(KEY) || "-"], ["O", "clim", slim(E.store.get(CK))]];
  });
}
// stream
for (let i = 0; i < 12; i++) {
  scenario("stream", "stream" + i, () => {
    const ds = mkDs("st" + i, 40 + i, { days: 22 });
    const init = initOf(ds);
    const st = JSON.parse(init);
    const target = i === 9 ? st.acts.find((a) => /^hw/.test(a[9]) && a[1] === "run") : i === 10 ? st.acts.find((a) => a[1] === "run" && a[9] && !/^hw/.test(a[9]) && (a[2] < 20 || a[3] < 3)) || st.acts.find((a) => a[9] && !/^hw/.test(a[9])) : st.acts.find((a) => a[9] === "i" + (100 + (i % 3)));
    target[2] = i === 10 ? 10 : Math.max(target[2], 45); target[3] = i === 10 ? 2 : Math.max(target[3], 8);
    const init2 = (() => { const o = JSON.parse(init); const a = o.acts.find((x) => x[0] === target[0]); a[2] = target[2]; a[3] = target[3]; return j(o); })();
    const s = mkStream(pick([320, 400]), pick([8, 12]), pick([2.8, 3.4]), { fast: chance(0.5), fade: chance(0.5), drift: pick([0, 12]) });
    let resp = icuStream(pick(["array", "object"]), s, !(i === 3));
    if (i === 4) resp = { __status: 404 }; if (i === 5) resp = { __status: 500 }; if (i === 6) resp = { __status: 401 }; if (i === 7) resp = { __throw: 1 }; if (i === 8) resp = [{ type: "time", data: [1, 2, 3] }];
    const icu = {}; icu["activity/" + target[9] + "/streams"] = resp;
    const E = scEnv(init2, ds.now, i === 11 ? { apiKey: "" } : {}, null, icu);
    return (async () => {
      await E.ensureStream(E.state.acts.find((a) => a[0] === target[0]));
      return [["I", "state", init2], ...Object.entries(icu).map(([p, v]) => ["I", "icu", p, j(v)]), ["I", "now", ds.now], ["I", "key", i === 11 ? "" : "KEY"], ["I", "act", target[0]], ["I", "op", "stream"],
        ["O", "store", E.store.get(KEY) || "-"]];
    })();
  });
}
// match (3 putaran)
for (let i = 0; i < 12; i++) {
  scenario("match", "match" + i, () => {
    const ds = mkDs("ma" + i, 60 + i, { days: pick([24, 30]), skip: 0.1 });
    const init = initOf(ds);
    const st = JSON.parse(init);
    const runs = st.acts.filter((a) => a[1] === "run");
    const loc = i % 4 === 3 ? null : { lat: -7.83, lon: 112.04, t: ds.now - DAY };
    const gps = {}, ctx = {};
    runs.slice(0, 3).forEach((a) => { gps[a[0]] = i % 2 ? { none: 1 } : { p: [[0, -7.9, 112.1], [900, -7.91, 112.12], [1800, -7.92, 112.13]] }; });
    runs.slice(3, 5).forEach((a) => { ctx[a[0]] = { t: 27, rh: 70, at: 30, w: 3, sr: 200, p: 0, code: 0, n: 1, amin: 30, amax: 30, cls: "hangat", src: "Open-Meteo", at0: ds.now, gps: 1, ng: 1, q: "x" }; });
    runs.slice(5, 6).forEach((a) => { ctx[a[0]] = { na: 1 }; });
    const cl = { loc, ctx, wx: {}, gps };
    const icu = {};
    runs.filter((a) => a[9] && !/^hw/.test(a[9])).forEach((a) => { icu["activity/" + a[9] + "/streams"] = pick([icuLatLng("split", 60, a[2] * 60), icuLatLng("pairs", 40, a[2] * 60), icuLatLng("none", 10, 100), { __status: 404 }, { __status: 500 }]); });
    const extra = i === 7 ? { apiKey: "" } : {};
    const E = scEnv(init, ds.now, extra, cl, icu);
    if (i === 5) E.state.apiKey = "demo";
    const wxFail = i === 10;
    return (async () => {
      const outs = [];
      for (let p = 0; p < 3; p++) { await E.clMatch(); outs.push(["O", "clim" + (p + 1), slim(E.store.get(CK))]); }
      return [["I", "state", init], ["I", "clim", j(cl)], ...Object.entries(icu).map(([pp, v]) => ["I", "icu", pp, j(v)]), ["I", "now", ds.now],
        ["I", "key", i === 7 ? "" : i === 5 ? "demo" : "KEY"], ["I", "wxfail", wxFail ? "1" : "0"], ["I", "op", "match"], ...outs];
    })();
  });
}

(async () => {
  let out = "# DIHASILKAN OTOMATIS dari runsense.html (gen/gen_data.js). Jangan edit tangan. Pemisah antar kolom = TAB.\n";
  const cov = {};
  for (const sc of SC) {
    const lines = await sc.build();
    out += `S\t${sc.kind}\t${sc.name}\n`;
    for (const l of lines) {
      if (l.some((x) => String(x).includes("\n") || String(x).includes("\t"))) throw new Error("pemisah di " + sc.name);
      out += l.join("\t") + "\n";
    }
    out += "E\n";
    cov[sc.kind] = (cov[sc.kind] || 0) + 1;
  }
  const dest = process.argv[2] || __dirname + "/../../data/src/test/resources/golden_data.txt";
  fs.writeFileSync(dest, out);
  console.log("bytes", out.length, cov);
})();
