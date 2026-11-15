process.env.TZ = "UTC";
const fs = require("fs");
const { buildDataset, runCase, DAY, mulberry32 } = require("./design_harness");

const M = mulberry32(20261115);
const pick = (a) => a[Math.floor(M() * a.length)];
const chance = (p) => M() < p;

// ---------- dataset ----------
const datasets = [];
for (let i = 0; i < 64; i++) {
  const paceBase = pick([5.0, 5.4, 5.6, 5.9, 6.3, 7.0, 7.6, 8.5, 9.5, 11.5]);
  const hr = chance(0.8);
  const withForm = chance(0.4);
  const sp = {
    shift: i % 7, seed: 1000 + i, days: pick([45, 60, 70]), skip: pick([0.2, 0.3, 0.45]), hr, icuLoad: chance(0.3),
    hillRate: chance(0.3) ? 11 : pick([0, 3]), streak: chance(0.12) ? pick([2, 3, 5, 6]) : 0,
    yHard: chance(0.15), yLong: chance(0.1), y2Long: chance(0.1), todayRun: chance(0.08), thr: chance(0.25) ? pick([1, 2, 3]) : 0,
    wdays: pick([0, 4, 35, 35, 35]),
    hrvDrop: pick([0, 0, 0.1, 0.22, 0.3]), rhrUp: pick([0, 0, 2, 5]), sleepCut: pick([0, 0, 1.2]), stressUp: pick([0, 0, 20]),
    rest: hr ? pick([50, 56, 62, 66]) : pick([0, 56]), max: hr ? pick([0, 185, 190]) : 0, age: pick([0, 30, 42]), afS: pick([0, 0, 2, 3, 4]),
    paceBase, vxRace: chance(0.05),
    form: withForm ? (chance(0.5) ? { tsb: pick([-30, -18, -5, 8]) } : { ctl: pick([45, 60]), atl: pick([40, 62, 78, 95]) }) : null,
  };
  datasets.push(buildDataset("d" + i, sp));
}

// Dataset "bugar": HRV naik, RHR turun, supaya kesiapan tinggi dan cabang quality (VO2/hills) ikut teruji.
for (let i = 64; i < 88; i++) {
  const sp = {
    shift: [2, 4, 5][i % 3], seed: 2000 + i, days: 60, skip: 0.35, hr: true, icuLoad: false,
    hillRate: i % 2 ? 11 : 2, streak: 0, yHard: false, yLong: false, y2Long: false, todayRun: false,
    thr: [0, 1, 2, 2, 3, 4, 5, 2][i % 8], wdays: 35, hrvDrop: -0.15, rhrUp: -5, sleepCut: 0, stressUp: 0,
    rest: 55, max: 188, age: 35, afS: 0, paceBase: [5.0, 5.4, 5.6, 5.0][i % 4], vxRace: false, form: null,
  };
  datasets.push(buildDataset("d" + i, sp));
}

// ---------- case ----------
const ymdOf = (ms) => new Date(ms).toISOString().slice(0, 10);
function makeCase(ds) {
  const style = pick(["", "conservative", "moderate", "moderate", "aggressive", "aggressive"]);
  let plan = {};
  const mode = M();
  const types = ["auto", "easy", "easy", "quality", "quality", "long", "rest"];
  if (mode < 0.4) {
    for (const d of [1, 2, 3, 4, 5, 6, 0]) { if (chance(0.85)) plan[d] = { t: pick(types), km: pick([0, 5, 8, 10, 12, 16, 22]) }; }
  } else if (mode < 0.6) {
    const dow = new Date(ds.now).getUTCDay();
    plan[dow] = { t: pick(["easy", "quality", "long", "rest"]), km: pick([0, 6, 10, 18]) };
    if (chance(0.5)) plan[(dow + 1) % 7] = { t: "long", km: 16 };
  }
  let race = "", raceDist = 0, raceGoal = "";
  if (chance(0.45)) {
    const off = pick([0, 1, 2, 3, 5, 7, 8, 9, 11, 13, 15, 20, 30, 45, -3]);
    race = ymdOf(ds.now + off * DAY);
    raceDist = pick([0, 5, 10, 21.1, 42.2]);
    raceGoal = pick(["", "0:50:00", "1:45:30", "55:00", "abc", "1:"]);
  } else if (chance(0.05)) { race = "bukan-tanggal"; }
  const rw = chance(0.3) ? { on: true, ...pick([{ run: 225, walk: 30 }, { run: 60, walk: 60 }, { run: 120, walk: 45 }]) } : { on: false, run: 225, walk: 30 };
  const heat = chance(0.2) ? ymdOf(ds.now) : chance(0.05) ? "2020-01-01" : "";
  const flag = chance(0.28) ? { lv: pick([1, 2]), since: ds.now - Math.floor(M() * 16) * DAY - Math.floor(M() * 5) * 36e5 } : null;
  const athlete = chance(0.45) ? { lthr: pick([0, 160, 172]), ltPace: pick(["", "4:30", "5:10"]) } : {};
  return { style, plan, race, raceDist, raceGoal, rw, heat, flag, athlete };
}

const NOFLAG = null, RW0 = { on: false, run: 225, walk: 30 };
function directed(ds) {
  const dow = new Date(ds.now).getUTCDay();
  const base = { style: "moderate", plan: {}, race: "", raceDist: 0, raceGoal: "", rw: RW0, heat: "", flag: NOFLAG, athlete: {} };
  const withPlan = (t, km, extra) => ({ ...base, ...extra, plan: { [dow]: { t, km } } });
  const raceIn = (d, dist, goal) => ({ race: ymdOf(ds.now + d * DAY), raceDist: dist, raceGoal: goal });
  return [
    { ...base, style: "aggressive", plan: { [dow]: { t: "quality", km: 10 } } },
    { ...base, style: "aggressive" },
    { ...base, style: "moderate" },
    withPlan("long", 18, {}),
    withPlan("long", 18, { style: "aggressive" }),
    withPlan("easy", 8, {}),
    withPlan("rest", 0, {}),
    withPlan("quality", 10, { style: "aggressive", ...raceIn(20, 10, "0:50:00") }),
    withPlan("quality", 10, { style: "aggressive", ...raceIn(11, 21.1, "1:45:30") }),
    withPlan("quality", 10, { style: "aggressive", ...raceIn(9, 5, "") }),
    withPlan("long", 18, { ...raceIn(6, 10, "") }),
    withPlan("easy", 8, { ...raceIn(4, 10, "") }),
    { ...base, ...raceIn(1, 10, "") },
    { ...base, ...raceIn(30, 42.2, "3:59:00"), style: "aggressive" },
    withPlan("easy", 8, { rw: { on: true, run: 225, walk: 30 }, athlete: { lthr: 168, ltPace: "4:30" } }),
    withPlan("quality", 10, { style: "aggressive", heat: ymdOf(ds.now), athlete: { lthr: 168, ltPace: "4:30" } }),
  ];
}

const cases = [];
const cover = {};
for (const ds of datasets) {
  const list = directed(ds);
  for (let k = 0; k < 4; k++) list.push(makeCase(ds));
  for (const cs of list) {
    let r;
    try { r = runCase(ds, cs); } catch (e) { console.error("JS error", ds.id, e.message); continue; }
    cases.push({ ds: ds.id, cs, r });
    const key = r.title + " [" + r.tag + "]";
    cover[key] = (cover[key] || 0) + 1;
  }
}

// ---------- tulis resource ----------
const n = (v) => String(v);
const dash = (v) => (v === undefined || v === null || v === "" ? "-" : String(v));
const SEP = "@@", PS = "##", PI = "^^";
let out = "# DIHASILKAN OTOMATIS dari runsense.html (gen/gen_design.js). Jangan edit tangan.\n";
for (const ds of datasets) {
  out += `D ${ds.id} ${ds.now} ${ds.rest} ${ds.max} ${ds.age} ${ds.afS}\n`;
  for (const a of ds.acts) out += `A ${a[0]} ${a[1]} ${a[2]} ${a[3]} ${a[4]} ${a[5]} ${a[8]} ${a[10]}\n`;
  for (const w of ds.wellness) {
    out += `W ${w.id} ${dash(w.restingHR)} ${dash(w.hrv)} ${w.hrvSpot ? 1 : 0} ${dash(w.sleepSecs)} ${dash(w.stress)} ${dash(w.ctl)} ${dash(w.atl)} ${dash(w.tsb)}\n`;
  }
  for (const [k, v] of Object.entries(ds.vx)) out += `X ${k} ${v}\n`;
  out += "E\n";
}
const bad = (s) => { if (typeof s === "string" && (s.includes(SEP) || s.includes(PS) || s.includes(PI) || s.includes("\n"))) throw new Error("separator in " + s); return s; };
for (const c of cases) {
  const cs = c.cs;
  const plan = Object.entries(cs.plan).map(([d, p]) => `${d}:${p.t}:${p.km}`).join(",") || "-";
  const flag = cs.flag ? `${cs.flag.lv}:${cs.flag.since}` : "-";
  const rw = `${cs.rw.on ? 1 : 0}:${cs.rw.run}:${cs.rw.walk}`;
  const lthr = cs.athlete.lthr || 0;
  out += `C ${c.ds} ${dash(cs.style)} ${dash(cs.heat)} ${flag} ${dash(cs.race)} ${cs.raceDist} ${dash(cs.raceGoal)} ${rw} ${lthr} ${dash(cs.athlete.ltPace)} ${plan}\n`;
  const r = c.r;
  const steps = r.steps.map((s) => bad(s[0]) + PI + bad(s[1])).join(PS);
  const wl = r.wl ? r.wl.map(bad).join(PS) : "-";
  out += "R " + [bad(r.k), bad(r.status), bad(r.title), bad(r.duration), bad(r.hr), bad(r.pace), bad(r.tag), r.score, r.stage === undefined ? "-" : r.stage, bad(r.reason), steps === "" ? "-" : steps, wl].map((x) => String(x)).join(SEP) + "\n";
}
const dest = process.argv[2] || __dirname + "/../src/test/resources/golden_design.txt";
fs.writeFileSync(dest, out);
console.log("cases", cases.length, "datasets", datasets.length, "bytes", out.length);
console.log(Object.entries(cover).sort((a, b) => b[1] - a[1]).map(([k, v]) => v + "  " + k).join("\n"));
