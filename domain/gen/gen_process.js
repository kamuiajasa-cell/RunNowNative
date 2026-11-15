// Membuat src/test/resources/golden_process.txt dari kode ASLI runsense.html (Langkah 3).
// Pakai: RUNSENSE_HTML=/path/ke/runsense.html node gen_process.js
process.env.TZ = "UTC";
const fs = require("fs");
const { buildDataset, DAY } = require("./design_harness");
const { runProcess } = require("./process_harness");

function mulberry32(a){return function(){a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
const M = mulberry32(20261116);
const pick = (a) => a[Math.floor(M() * a.length)];
const chance = (p) => M() < p;

// ---------- dataset ----------
const datasets = [];
const base = (i, o) => ({
  shift: i % 7, seed: 3000 + i, days: 60, skip: 0.3, hr: true, icuLoad: false, hillRate: 3, streak: 0, yHard: false, yLong: false,
  y2Long: false, todayRun: false, thr: 0, wdays: 35, hrvDrop: 0, rhrUp: 0, sleepCut: 0, stressUp: 0,
  rest: 55, max: 188, age: 35, afS: 0, paceBase: 5.8, vxRace: false, form: null, ...o,
});
for (let i = 0; i < 60; i++) {
  const hr = chance(0.8);
  datasets.push(buildDataset("p" + i, base(i, {
    days: pick([25, 45, 60, 70]), skip: pick([0.2, 0.3, 0.45]), hr, icuLoad: chance(0.3),
    hillRate: pick([0, 3, 11]), thr: chance(0.3) ? pick([1, 2, 3, 5]) : 0, wdays: pick([0, 4, 35, 35]),
    hrvDrop: pick([0, 0.1, 0.25]), rhrUp: pick([0, 2, 5]),
    rest: hr ? pick([50, 56, 62, 66, 0]) : pick([0, 56]), max: hr ? pick([0, 185, 190]) : 0, age: pick([0, 30, 42]),
    afS: pick([0, 0, 2, 3, 4]), paceBase: pick([5.0, 5.4, 5.6, 5.9, 6.3, 7.0, 7.6, 8.5, 9.5, 11.5]),
    streak: chance(0.1) ? pick([2, 5]) : 0, yHard: chance(0.15), yLong: chance(0.1), vxRace: chance(0.05),
    form: chance(0.4) ? (chance(0.5) ? { tsb: pick([-30, -18, -5, 8]) } : { ctl: pick([45, 60]), atl: pick([40, 62, 78, 95]) }) : null,
  })));
}
// kasus tepi: LTHR dari max <140, rentang HR <40, tanpa wellness, tanpa lari, sedikit lari, HR tinggi (koreksi LTHR)
const edges = [
  { rest: 120, max: 150 }, { rest: 60, max: 130 }, { rest: 0, max: 0, age: 0 }, { rest: 55, max: 0, age: 50 },
  { days: 3, skip: 0.9 }, { days: 1, skip: 1 }, { wdays: 0 }, { paceBase: 4.6, thr: 4, max: 185 }, { paceBase: 4.4, thr: 5, max: 180, rest: 50 },
  { hr: false, rest: 0, max: 0 }, { days: 70, skip: 0.1, paceBase: 5.0 }, { days: 70, skip: 0.1, paceBase: 12 },
];
edges.forEach((o, k) => {
  const ds = buildDataset("e" + k, base(60 + k, o));
  if (k === 6) ds.wellness = [];
  if (k === 5) ds.acts = ds.acts.filter((a) => a[1] !== "run");
  datasets.push(ds);
});

// ---------- kasus ----------
const ymdOf = (ms) => new Date(ms).toISOString().slice(0, 10);
const GOALS = ["5K", "10K", "Half", "Marathon", "Umum", "5K"];
function makeCase(ds, k) {
  const dow = new Date(ds.now).getUTCDay();
  const plan = chance(0.4) ? { [dow]: { t: pick(["easy", "quality", "long", "rest"]), km: pick([0, 6, 10, 18]) } } : {};
  let race = "", raceDist = 0, raceGoal = "";
  if (chance(0.35)) { race = ymdOf(ds.now + pick([0, 2, 5, 9, 14, 30, -3]) * DAY); raceDist = pick([0, 5, 10, 21.1]); raceGoal = pick(["", "0:50:00", "1:45:30"]); }
  return {
    goal: k === 0 ? "5K" : pick(GOALS),
    icuName: k === 0 ? "" : pick(["", "Andi", "Budi_Santoso"]),
    lthrC: k === 0 ? 0 : pick([0, 0, 165, 172]),
    ltpC: k === 0 ? 0 : pick([0, 0, 270, 305]),
    restManual: k === 0 ? 0 : pick([0, 0, 1]),
    icuLast: k === 0 ? 0 : pick([0, ds.now - 5 * 60e3, ds.now - 3 * 3600e3]),
    hwLast: k === 0 ? 0 : pick([0, ds.now - 20 * 60e3]),
    style: pick(["moderate", "aggressive", "conservative"]), plan, race, raceDist, raceGoal,
    rw: chance(0.3) ? { on: true, run: 225, walk: 30 } : { on: false, run: 225, walk: 30 },
    heat: chance(0.15) ? ymdOf(ds.now) : "",
    flag: chance(0.2) ? { lv: pick([1, 2]), since: ds.now - Math.floor(M() * 10) * DAY } : null,
  };
}

const cases = [];
for (const ds of datasets) {
  for (let k = 0; k < 3; k++) {
    const cs = makeCase(ds, k);
    let r;
    try { r = runProcess(ds, cs); } catch (e) { console.error("JS error", ds.id, e.message); continue; }
    cases.push({ ds: ds.id, cs, r });
  }
}

// ---------- tulis resource ----------
const dash = (v) => (v === undefined || v === null || v === "" ? "-" : String(v));
const SEP = "@@", PS = "##", PI = "^^";
const bad = (s) => { if (typeof s === "string" && (s.includes(SEP) || s.includes(PS) || s.includes(PI) || s.includes("\n"))) throw new Error("separator in " + s); return s; };
let out = "# DIHASILKAN OTOMATIS dari runsense.html (gen/gen_process.js). Jangan edit tangan.\n";
for (const ds of datasets) {
  out += `D ${ds.id} ${ds.now} ${ds.rest} ${ds.max} ${ds.age} ${ds.afS}\n`;
  for (const a of ds.acts) out += `A ${a[0]} ${a[1]} ${a[2]} ${a[3]} ${a[4]} ${a[5]} ${a[8]} ${a[10]}\n`;
  for (const w of ds.wellness) out += `W ${w.id} ${dash(w.restingHR)} ${dash(w.hrv)} ${w.hrvSpot ? 1 : 0} ${dash(w.sleepSecs)} ${dash(w.stress)} ${dash(w.ctl)} ${dash(w.atl)} ${dash(w.tsb)}\n`;
  for (const [k, v] of Object.entries(ds.vx)) out += `X ${k} ${v}\n`;
  out += "E\n";
}
for (const c of cases) {
  const cs = c.cs, r = c.r, st = r.state, a = st.athlete, e = a.est, rec = st.recommendation;
  const plan = Object.entries(cs.plan).map(([d, p]) => `${d}:${p.t}:${p.km}`).join(",") || "-";
  const flag = cs.flag ? `${cs.flag.lv}:${cs.flag.since}` : "-";
  const rw = `${cs.rw.on ? 1 : 0}:${cs.rw.run}:${cs.rw.walk}`;
  out += `C ${c.ds} ${cs.goal} ${cs.lthrC} ${cs.ltpC} ${cs.restManual} ${dash(cs.icuName)} ${cs.icuLast} ${cs.hwLast} ${cs.style} ${dash(cs.heat)} ${flag} ${dash(cs.race)} ${cs.raceDist} ${dash(cs.raceGoal)} ${rw} ${plan}\n`;
  const acts = st.activities.map((x) => [x.start, x.date, x.title, x.meta, x.load, x.type, x.conf, x.why, x.confirmed ? 1 : 0].map((v) => bad(dash(v))).join(PI)).join(PS);
  const f = [
    a.name, a.ctl, a.atl, a.tsb, a.lthr, a.ltPace, a.lthrAuto, a.ltPaceAuto, a.formLabel, a.formClass, a.score, a.mets, a.stage, a.stageName,
    e.m, e.src, e.n, e.conf, e.perf, e.hrr,
    st.goalEst.current, st.goalEst.good, st.goalEst.projection, st.rest, st.afS,
    rec.k, rec.status, rec.title, rec.duration, rec.hr, rec.pace, rec.tag, rec.score, rec.stage, rec.reason,
    ...r.cl, ...r.il, r.sync,
  ].map((v) => bad(dash(v)));
  f.push(dash(acts)); // sudah memuat pemisah ## dan ^^ yang disengaja
  out += "R " + f.join(SEP) + "\n";
}
const dest = process.argv[2] || __dirname + "/../src/test/resources/golden_process.txt";
fs.writeFileSync(dest, out);
console.log("cases", cases.length, "datasets", datasets.length, "bytes", out.length);
const cov = {};
for (const c of cases) { const a = c.r.state.athlete; for (const k of ["formLabel", "stage"]) cov[k + "=" + a[k]] = (cov[k + "=" + a[k]] || 0) + 1; cov["lthr:" + (a.lthrAuto == null ? "null" : "ada")] = (cov["lthr:" + (a.lthrAuto == null ? "null" : "ada")] || 0) + 1; cov["est5k:" + (c.r.state.goalEst.current === "—:—" ? "kosong" : "ada")] = (cov["est5k:" + (c.r.state.goalEst.current === "—:—" ? "kosong" : "ada")] || 0) + 1; }
console.log(cov);
