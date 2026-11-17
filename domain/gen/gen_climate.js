// Membuat src/test/resources/golden_climate.txt (clAvg, clPers, clTh, clCls, clStat, clProfile, clSlots) dari kode ASLI
// runsense.html (Langkah 5). Pakai: RUNSENSE_HTML=/path/ke/runsense.html node gen_climate.js
process.env.TZ = "UTC";
const fs = require("fs");
const { buildDataset } = require("./design_harness");
const { makeEnv } = require("./data_harness");

function mulberry32(a){return function(){a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
const M = mulberry32(20261119);
const pick = (a) => a[Math.floor(M() * a.length)];
const chance = (p) => M() < p;
const ri = (a, b) => a + Math.floor(M() * (b - a + 1));

const base = (i, o) => ({
  shift: i % 7, seed: 7000 + i, days: 70, skip: 0.1, hr: true, icuLoad: false, hillRate: 3, streak: 0, yHard: false, yLong: false,
  y2Long: false, todayRun: false, thr: 0, wdays: 20, hrvDrop: 0, rhrUp: 0, sleepCut: 0, stressUp: 0,
  rest: 55, max: 188, age: 35, afS: 0, paceBase: 6.0, vxRace: false, form: null, ...o,
});
const datasets = [];
for (let i = 0; i < 30; i++) {
  datasets.push(buildDataset("c" + i, base(i, {
    days: pick([30, 60, 70, 70]), skip: pick([0.05, 0.1, 0.3]), hr: chance(0.9), thr: chance(0.15) ? pick([1, 3]) : 0,
    paceBase: pick([5.2, 5.8, 6.4, 7.5]), afS: pick([0, 2, 3, 4]), rest: pick([0, 55, 60]), max: pick([0, 188]), age: pick([0, 35]),
  })));
}

// ---- konteks cuaca per lari ----
const CLS = ["sejuk", "hangat", "panas", "panas & lembap"];
const fmtN = (v) => (v === null || v === undefined ? "-" : String(v));
for (const ds of datasets) {
  ds.ctx = {};
  const center = pick([23, 25.5, 28, 30.5]);
  for (const a of ds.acts) {
    if (a[1] !== "run" || chance(0.15)) continue;
    if (chance(0.06)) { ds.ctx[a[0]] = { na: 1 }; continue; }
    const t = Math.round((center + pick([-3, -1.5, 0, 1.5, 3, 5]) + ri(-6, 6) / 10) * 10) / 10;
    const at = Math.round((t + ri(0, 40) / 10) * 10) / 10;
    const c = { t, rh: ri(45, 90), at, w: ri(0, 15), sr: pick([0, 120, 300, 550]), p: pick([0, 0, 0, 0.4, 2.5, 9]), code: pick([0, 0, 3, 45, 95]), n: ri(1, 3),
      amin: at - 1, amax: at + 1, cls: pick(CLS), src: "Open-Meteo", at0: ds.now, gps: chance(0.5) ? 1 : 0, ng: ri(1, 5), q: "x" };
    if (chance(0.08)) { c.t = null; c.rh = null; }
    ds.ctx[a[0]] = c;
  }
}

// ---- data cuaca per jam ----
const hourStr = (ms) => new Date(ms).toISOString().slice(0, 13) + ":00";
function genWx(now, variant) {
  const t0 = Math.floor(now / 36e5) * 36e5 - 36 * 36e5;
  const time = [], names = ["temperature_2m", "relative_humidity_2m", "apparent_temperature", "wind_speed_10m", "precipitation", "shortwave_radiation", "cloud_cover", "weather_code"];
  const h = {}; names.forEach((n) => (h[n] = []));
  for (let i = 0; i < 96; i++) {
    const ms = t0 + i * 36e5, hr = new Date(ms).getUTCHours();
    time.push(hourStr(ms));
    const nul = () => chance(0.04);
    const base = 24 + (hr >= 9 && hr <= 15 ? 7 : hr >= 6 && hr <= 18 ? 3 : 0) + (variant === 1 ? -4 : 0) + ri(-1, 1);
    h.temperature_2m.push(nul() ? null : base);
    h.relative_humidity_2m.push(nul() ? null : ri(45, 92));
    h.apparent_temperature.push(nul() ? null : base + ri(0, 4));
    h.wind_speed_10m.push(ri(0, 14));
    h.precipitation.push(chance(0.1) ? pick([0.4, 2.5, 9]) : 0);
    h.shortwave_radiation.push(hr >= 6 && hr <= 17 ? pick([40, 180, 420, 700]) : 0);
    h.cloud_cover.push(ri(0, 100));
    h.weather_code.push(chance(0.03) ? 95 : pick([0, 1, 3, 45, 61]));
  }
  if (variant === 1) { delete h.apparent_temperature; delete h.shortwave_radiation; }
  return { t: now - 10 * 60e3, h: { time, ...h }, src: "Open-Meteo" };
}

const SAMPLES = [
  { t: 22, rh: 60, at: 23, w: 5, sr: 0, p: 0, code: 0, n: 1 }, { t: 26, rh: 75, at: 29, w: 3, sr: 300, p: 0, code: 3, n: 1 },
  { t: 28, rh: 80, at: 33, w: 3, sr: 450, p: 0.4, code: 3, n: 1 }, { t: 30, rh: 55, at: 31, w: 8, sr: 600, p: 0, code: 0, n: 1 },
  { t: 34, rh: 70, at: 40, w: 2, sr: 700, p: 0, code: 0, n: 2 }, { t: 25, rh: 90, at: 27, w: 1, sr: 0, p: 3, code: 61, n: 1 },
  { t: 27, rh: 65, at: 28, w: 6, sr: 100, p: 0, code: 95, n: 1 }, { t: null, rh: 70, at: 30, w: 4, sr: 200, p: 0, code: 0, n: 1 },
  { t: 24.5, rh: 50, at: 25, w: 10, sr: 150, p: 0, code: 0, n: 1 }, { t: 31, rh: 72, at: 36, w: 2, sr: 500, p: 9, code: 80, n: 1 },
];

// ---- jalankan kasus ----
const cases = [];
for (const ds of datasets) {
  const wxs = [genWx(ds.now, 0), genWx(ds.now, 1)];
  const E = makeEnv(ds, {}, { loc: null, ctx: ds.ctx, wx: {}, gps: {} }, {});
  const r = { P: E.clPers(), th: E.clTh(), txt: E.clPersTxt(), prof: E.clProfile(), cls: [], slots: [], avg: [] };
  for (const s of SAMPLES) r.cls.push({ cls: E.clCls(s), st: E.clStat(s), sc: E.clSc(s) });
  wxs.forEach((o, wi) => {
    [2, 7].forEach((si) => [30, 90].forEach((dur) => r.slots.push({ wi, si, dur, out: E.clSlots(o, dur, SAMPLES[si]) })));
    [[0, 3], [1, 30], [10, 50]].forEach(([a, b]) => { const f = o.t + a * 36e5, t = f + b * 36e5; r.avg.push({ wi, from: f, to: t, out: E.clAvg(o, f, t) }); });
  });
  r.avg.push({ wi: 0, from: wxs[0].t + 1e12, to: wxs[0].t + 1e12 + 36e5, out: E.clAvg(wxs[0], wxs[0].t + 1e12, wxs[0].t + 1e12 + 36e5) });
  cases.push({ ds, wxs, r });
}

// ---- tulis ----
const SEP = "@@";
const bad = (s) => { if (typeof s === "string" && (s.includes(SEP) || s.includes("\n"))) throw new Error("separator in " + s); return s; };
const persLine = (P) => [P.ok ? 1 : 0, P.n, P.cand, P.reason, P.opt, P.optLo, P.cool, P.hot, P.conf,
  P.bins ? P.bins.map((b) => [b.lo, b.hi, b.n, b.med, b.c].join(",")).join(";") : null].map((v) => bad(fmtN(v) === "" ? "-" : fmtN(v))).join(SEP);
const climLine = (c) => (c ? [c.t, c.rh, c.at, c.w, c.sr, c.p, c.code, c.n].map(fmtN).join(",") : "null");
let out = "# DIHASILKAN OTOMATIS dari runsense.html (gen/gen_climate.js). Jangan edit tangan.\n";
for (const w of cases.flatMap((c) => c.wxs.map((o, i) => ({ id: c.ds.id + "w" + i, o })))) {
  out += `H ${w.id} ${w.o.t}\nHT ${w.o.h.time.join(",")}\n`;
  for (const [k, v] of Object.entries(w.o.h)) if (k !== "time") out += `HS ${k} ${v.map((x) => (x === null ? "~" : x)).join(",")}\n`;
  out += "HE\n";
}
for (const { ds, r } of cases) {
  out += `D ${ds.id} ${ds.now} ${ds.rest} ${ds.max} ${ds.age} ${ds.afS}\n`;
  for (const a of ds.acts) out += `A ${a[0]} ${a[1]} ${a[2]} ${a[3]} ${a[4]} ${a[5]} ${a[8]} ${a[10]}\n`;
  for (const w of ds.wellness) out += `W ${w.id} ${fmtN(w.restingHR)} ${fmtN(w.hrv)} ${w.hrvSpot ? 1 : 0} ${fmtN(w.sleepSecs)} ${fmtN(w.stress)} ${fmtN(w.ctl)} ${fmtN(w.atl)} ${fmtN(w.tsb)}\n`;
  for (const [k, v] of Object.entries(ds.vx)) out += `X ${k} ${v}\n`;
  for (const [k, c] of Object.entries(ds.ctx)) {
    out += c.na ? `K ${k} na\n` : `K ${k} ${[c.t, c.rh, c.at, c.w, c.sr, c.p, c.code, c.n, c.amin, c.amax, c.cls, c.gps, c.ng].map((v) => fmtN(v)).join(",")}\n`;
  }
  out += "E\n";
  out += "P " + persLine(r.P) + "\n";
  out += `T ${r.th.cool},${r.th.hot},${r.th.wn}\n`;
  out += "X2 " + bad(r.txt) + "\n";
  const S = Object.entries(r.prof.S).map(([k, v]) => `${k}=${v.n},${v.hr},${v.pace}`).join(";") || "-";
  const CM = Object.entries(r.prof.cmp).map(([k, v]) => `${k}=${v.d},${v.n},${v.conf}`).join(";") || "-";
  out += `F ${S}${SEP}${CM}\n`;
  r.cls.forEach((c, i) => { out += `C ${i}${SEP}${c.cls}${SEP}${c.st.k}${SEP}${c.st.lb}${SEP}${c.st.why}${SEP}${c.sc}\n`; });
  for (const s of r.slots) out += `S ${ds.id}w${s.wi} ${s.si} ${s.dur}${SEP}${s.out.length ? s.out.map((x) => (x.better ? 1 : 0) + "|" + bad(x.txt)).join("##") : "-"}\n`;
  for (const a of r.avg) out += `V ${ds.id}w${a.wi} ${a.from} ${a.to}${SEP}${climLine(a.out)}\n`;
  out += "Z\n";
}
const dest = process.argv[2] || __dirname + "/../src/test/resources/golden_climate.txt";
fs.writeFileSync(dest, out);
const cov = {};
for (const { r } of cases) { cov[r.P.ok ? "pers:ok" : "pers:belum"] = (cov[r.P.ok ? "pers:ok" : "pers:belum"] || 0) + 1; if (Object.keys(r.prof.cmp).length) cov["cmp"] = (cov["cmp"] || 0) + 1; for (const s of r.slots) cov["slotsNonEmpty"] = (cov["slotsNonEmpty"] || 0) + (s.out.length ? 1 : 0); if (r.P.ok && r.P.cool != null) cov["cool"] = (cov["cool"] || 0) + 1; if (r.P.ok && r.P.hot != null) cov["hot"] = (cov["hot"] || 0) + 1; }
console.log("bytes", out.length, cov);
