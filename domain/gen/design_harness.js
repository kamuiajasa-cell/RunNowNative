// Menjalankan kode ASLI runsense.html (engine + rencana + designToday) dengan jam palsu, untuk nilai acuan test Kotlin.
process.env.TZ = "UTC";
const fs = require("fs");
const lines = fs.readFileSync(process.env.RUNSENSE_HTML || "runsense.html", "utf8").split("\n");
const slice = (a, b) => lines.slice(a - 1, b).join("\n"); // 1-based inklusif

const code =
  slice(797, 1130) + "\n" +
  'const VX_LB = { easy: "Easy", long: "Long", thr: "Threshold", vo2: "VO2max", race: "Lomba" };\n' +
  slice(1939, 1939) + "\n" +
  slice(1941, 2555) + "\n" +
  "return { designToday, stage, readiness };";

const DAY = 864e5;
const BASE_NOW = Date.UTC(2026, 10, 15, 8, 0, 0); // Minggu 15 Nov 2026 08:00 UTC
let NOW = BASE_NOW;

const RealDate = Date;
class FakeDate extends RealDate {
  constructor(...a) { if (a.length === 0) super(NOW); else super(...a); }
  static now() { return NOW; }
}
global.Date = FakeDate;
global.$ = () => null;

const factory = new Function("state", code);

function mulberry32(a){return function(){a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}

function buildDataset(id, sp) {
  const now = BASE_NOW + sp.shift * DAY;
  const rnd = mulberry32(sp.seed);
  const acts = [];
  const push = (start, kind, min, km, hr, elev, load) => {
    const aid = "hw" + start;
    acts.push([start, kind, min, km, hr, elev, aid, "Lari", hr ? hr + 12 : 0, aid, load]);
  };
  for (let d = sp.days; d >= 1; d--) {
    if (rnd() < sp.skip) continue;
    const start = now - d * DAY + Math.floor(rnd() * 3) * 36e5;
    const r = rnd();
    const kind = r < 0.82 ? "run" : r < 0.92 ? "walk" : "other";
    const km = kind === "run" ? Math.round((2 + rnd() * (d % 7 === 0 ? 12 : 6)) * 100) / 100 : kind === "walk" ? Math.round((1 + rnd() * 3) * 100) / 100 : 0;
    const pace = sp.paceBase + rnd() * 2.2;
    const min = kind === "run" ? Math.round(km * pace * 100) / 100 : Math.round((20 + rnd() * 40) * 100) / 100;
    const hr = sp.hr ? Math.round(140 + rnd() * 35) : 0;
    const load = sp.icuLoad && rnd() < 0.5 ? Math.round(30 + rnd() * 90) : 0;
    const elev = kind === "run" ? Math.round(km * sp.hillRate * (0.6 + rnd() * 0.8)) : 0;
    push(start, kind, min, km, hr, elev, load);
  }
  for (let i = 1; i <= sp.streak; i++) push(now - i * DAY - 2 * 36e5, "run", 40, 6, sp.hr ? 150 : 0, 20, 0);
  if (sp.yHard) push(now - DAY - 3 * 36e5, "run", 55, 9.5, sp.hr ? 176 : 0, 30, 120);
  if (sp.yLong) push(now - DAY - 4 * 36e5, "run", 110, 16, sp.hr ? 152 : 0, 40, 0);
  if (sp.y2Long) push(now - 2 * DAY - 4 * 36e5, "run", 115, 17, sp.hr ? 150 : 0, 40, 0);
  if (sp.todayRun) push(now - 2 * 36e5, "run", 42, 6.4, sp.hr ? 148 : 0, 25, 0);
  if (sp.thr) for (let i = 0; i < sp.thr; i++) push(now - (3 + i * 5) * DAY, "run", 32, 7.2, sp.hr ? 178 : 0, 20, 105);
  acts.sort((a, b) => a[0] - b[0]);
  const wellness = [];
  for (let d = sp.wdays; d >= 0; d--) {
    const wid = new RealDate(now - d * DAY).toISOString().slice(0, 10);
    const drop = d < 3 ? sp.hrvDrop : 0;
    const w = { id: wid };
    if (rnd() < 0.95) { w.restingHR = Math.round(52 + rnd() * 4 + (d < 3 ? sp.rhrUp : 0)); w.rhrSrc = "jam"; }
    if (rnd() < 0.9) { w.hrv = Math.round((60 + rnd() * 10) * (1 - drop)); w.hrvSrc = "jam"; if (rnd() < 0.08) w.hrvSpot = true; }
    if (rnd() < 0.9) w.sleepSecs = Math.round((6.2 + rnd() * 1.6 - (d < 2 ? sp.sleepCut : 0)) * 3600);
    if (rnd() < 0.8) w.stress = Math.round(30 + rnd() * 10 + (d < 2 ? sp.stressUp : 0));
    wellness.push(w);
  }
  if (wellness.length && sp.form) {
    const last = wellness[wellness.length - 1];
    if (sp.form.tsb !== undefined) last.tsb = sp.form.tsb;
    else { last.ctl = sp.form.ctl; last.atl = sp.form.atl; }
  }
  const vx = {};
  const runs = acts.filter((a) => a[1] === "run");
  if (sp.vxRace && runs.length) vx[runs[runs.length - 1][0]] = "race";
  return { id, now, rest: sp.rest, max: sp.max, age: sp.age, afS: sp.afS, acts, wellness, vx };
}

function runCase(ds, cs) {
  NOW = ds.now;
  const state = JSON.parse(JSON.stringify({
    acts: ds.acts, wellness: ds.wellness, vx: ds.vx, rest: ds.rest, max: ds.max, age: ds.age, afS: ds.afS,
    style: cs.style, plan: cs.plan, race: cs.race, raceDist: cs.raceDist, raceGoal: cs.raceGoal,
    rw: cs.rw, heat: cs.heat, flag: cs.flag, athlete: cs.athlete, apiKey: "x",
  }));
  const E = factory(state);
  const r = E.designToday();
  return r;
}
module.exports = { buildDataset, runCase, DAY, BASE_NOW, mulberry32 };
