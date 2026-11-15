// Membuat src/test/resources/golden_review.txt (reviewRun, reviewHeadline, runBacktest) dari kode ASLI runsense.html (Langkah 4).
// Pakai: RUNSENSE_HTML=/path/ke/runsense.html node gen_review.js
process.env.TZ = "UTC";
const fs = require("fs");
const { buildDataset } = require("./design_harness");
const { runReview } = require("./review_harness");

function mulberry32(a){return function(){a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
const M = mulberry32(20261117);
const pick = (a) => a[Math.floor(M() * a.length)];
const chance = (p) => M() < p;
const ri = (a, b) => a + Math.floor(M() * (b - a + 1));

const base = (i, o) => ({
  shift: i % 7, seed: 5000 + i, days: 60, skip: 0.3, hr: true, icuLoad: false, hillRate: 3, streak: 0, yHard: false, yLong: false,
  y2Long: false, todayRun: false, thr: 0, wdays: 35, hrvDrop: 0, rhrUp: 0, sleepCut: 0, stressUp: 0,
  rest: 55, max: 188, age: 35, afS: 0, paceBase: 5.8, vxRace: false, form: null, ...o,
});
const NAMES = ["Lari", "Lari", "Lari", "", "Lari pagi di sekitar waduk dan taman kota yang panjang", "Tempo 5K", "Long run Minggu"];
const datasets = [];
for (let i = 0; i < 34; i++) {
  const hr = chance(0.8);
  datasets.push(buildDataset("r" + i, base(i, {
    days: pick([25, 45, 60, 70]), skip: pick([0.15, 0.3, 0.45]), hr, icuLoad: chance(0.4), hillRate: pick([0, 3, 7]),
    thr: chance(0.3) ? pick([1, 2, 3, 5]) : 0, wdays: pick([0, 4, 35]), hrvDrop: pick([0, 0.2]), rhrUp: pick([0, 4]),
    rest: hr ? pick([50, 56, 62, 0]) : pick([0, 56]), max: hr ? pick([0, 185, 190]) : 0, age: pick([0, 30, 42]),
    afS: pick([0, 0, 2, 3, 4]), paceBase: pick([4.8, 5.2, 5.6, 5.9, 6.4, 7.2, 8.0, 9.5]),
    streak: chance(0.25) ? pick([2, 3, 5]) : 0, yHard: chance(0.2), yLong: chance(0.15), y2Long: chance(0.1), todayRun: chance(0.3),
    vxRace: chance(0.1),
  })));
}
[{ days: 3, skip: 0.9 }, { days: 12, skip: 0.2 }, { rest: 0, max: 0, age: 0 }, { hr: false, rest: 0, max: 0 }, { paceBase: 4.4, thr: 5, streak: 5 }].forEach((o, k) => {
  datasets.push(buildDataset("re" + k, base(40 + k, o)));
});

// nama, beban pecahan, dan ringkasan stream
const sxSpec = (o) => o.none ? "none" : [
  o.fast ? `f=${o.fast.p12},${o.fast.med},${o.fast.pct}` : null,
  o.fade ? `a=${o.fade.pct}` : null,
  o.dec ? `d=${o.dec.pct}` : null,
  o.tiz ? `z=${o.tiz.join(",")}` : null,
].filter(Boolean).join(";");
for (const ds of datasets) {
  ds.sx = {};
  for (const a of ds.acts) {
    a[7] = pick(NAMES);
    if (a[10] > 0 && chance(0.4)) a[10] = a[10] + 0.37;
    if (a[1] === "run" && chance(0.4)) {
      if (chance(0.15)) { ds.sx[a[9]] = { none: 1 }; continue; }
      const o = {};
      if (chance(0.5)) o.fast = { p12: ri(48, 60) / 10, med: ri(55, 70) / 10, pct: ri(6, 18) };
      if (chance(0.4)) o.fade = { pct: ri(5, 12) };
      if (chance(0.6)) o.dec = { pct: ri(-4, 9) };
      if (chance(0.7)) { const z = [ri(0, 30), ri(10, 60), ri(0, 40), ri(0, 25), ri(0, 10)]; o.tiz = z; }
      ds.sx[a[9]] = Object.keys(o).length ? o : { none: 1 };
    }
  }
}

const cases = [];
for (const ds of datasets) {
  for (let k = 0; k < 2; k++) {
    const cs = k === 0 ? { lthrC: 0, ltpC: 0, restManual: 0 } : { lthrC: pick([0, 165, 172]), ltpC: pick([0, 270, 305]), restManual: pick([0, 1]) };
    const acts = ds.acts;
    const picks = new Set(acts.slice(-4).map((a) => a[0]));
    const runs = acts.filter((a) => a[1] === "run");
    for (let j = 0; j < 3 && runs.length; j++) picks.add(runs[ri(0, runs.length - 1)][0]);
    const w = acts.find((a) => a[1] !== "run"); if (w) picks.add(w[0]);
    let r;
    try { r = runReview(ds, cs, [...picks]); } catch (e) { console.error("JS error", ds.id, e.stack); continue; }
    cases.push({ ds: ds.id, cs, r });
  }
}

const dash = (v) => (v === undefined || v === null || v === "" ? "-" : String(v));
const F = "@@", SEC = "##", TN = "^^", NT = "§§", KV = "¦";
const bad = (s) => { if (typeof s === "string") for (const t of [F, SEC, TN, NT, KV, "\n"]) if (s.includes(t)) throw new Error("separator " + t + " in " + s); return s; };
let out = "# DIHASILKAN OTOMATIS dari runsense.html (gen/gen_review.js). Jangan edit tangan.\n";
for (const ds of datasets) {
  out += `D ${ds.id} ${ds.now} ${ds.rest} ${ds.max} ${ds.age} ${ds.afS}\n`;
  for (const a of ds.acts) out += `A ${a[0]} ${a[1]} ${a[2]} ${a[3]} ${a[4]} ${a[5]} ${a[8]} ${a[10]} ${dash(a[7])}\n`;
  for (const w of ds.wellness) out += `W ${w.id} ${dash(w.restingHR)} ${dash(w.hrv)} ${w.hrvSpot ? 1 : 0} ${dash(w.sleepSecs)} ${dash(w.stress)} ${dash(w.ctl)} ${dash(w.atl)} ${dash(w.tsb)}\n`;
  for (const [k, v] of Object.entries(ds.vx)) out += `X ${k} ${v}\n`;
  for (const a of ds.acts) if (ds.sx[a[9]]) out += `S ${a[0]} ${sxSpec(ds.sx[a[9]])}\n`;
  out += "E\n";
}
let nRev = 0;
for (const c of cases) {
  out += `C ${c.ds} ${c.cs.lthrC} ${c.cs.ltpC} ${c.cs.restManual}\n`;
  for (const x of c.r.reviews) {
    const v = x.rev;
    const secs = v.sections.map((s) => bad(s.title) + TN + s.notes.map((n) => bad(n.k) + KV + bad(n.text)).join(NT)).join(SEC);
    const f = [x.start, v.tone, v.headline, v.summary, secs, x.head && x.head.k, x.head && x.head.text, x.legacy.k, x.legacy.text]
      .map((t, i) => (i === 4 ? dash(t) : bad(dash(t)))); // kolom 4 (sections) memang berisi pemisah; isinya sudah diperiksa di atas
    out += "V " + f.join(F) + "\n";
    nRev++;
  }
  // backtest: label/nilai diambil dari HTML yang dihasilkan kode asli
  const pairs = [...c.r.body.matchAll(/<div><b>(.*?)<\/b><span>(.*?)<\/span><\/div>/g)].map((m) => bad(m[1]) + TN + bad(m[2]));
  const need = c.r.body.match(/sekarang (\d+)\)/);
  out += `K ${bad(c.r.sub)}${F}${pairs.length ? pairs.join(SEC) : "-"}${F}${need ? need[1] : "-"}\n`;
}
const dest = process.argv[2] || __dirname + "/../src/test/resources/golden_review.txt";
fs.writeFileSync(dest, out);
const cov = { reviews: nRev, cases: cases.length };
for (const c of cases) { for (const x of c.r.reviews) { const k = "tone=" + x.rev.tone; cov[k] = (cov[k] || 0) + 1; if (!x.rev.headline) cov.empty = (cov.empty || 0) + 1; } cov[c.r.body.includes("sekarang") ? "bt:belumCukup" : "bt:cukup"] = (cov[c.r.body.includes("sekarang") ? "bt:belumCukup" : "bt:cukup"] || 0) + 1; }
console.log("bytes", out.length, cov);
