// Membuat src/test/resources/golden_stream.txt (summarizeStream, hwSummary) dari kode ASLI runsense.html (Langkah 4).
// Pakai: RUNSENSE_HTML=/path/ke/runsense.html node gen_stream.js
process.env.TZ = "UTC";
const fs = require("fs");
const { runStream, runHw } = require("./review_harness");

function mulberry32(a){return function(){a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
const M = mulberry32(20261118);
const pick = (a) => a[Math.floor(M() * a.length)];
const chance = (p) => M() < p;
const ri = (a, b) => a + Math.floor(M() * (b - a + 1));

/** Stream sintetis bilangan bulat: time (detik), distance (meter kumulatif), heartrate. */
function makeStream(o) {
  const n = o.n, dt = o.dt, t0 = o.t0 || 0;
  const time = [], dist = [], hr = [];
  let d = 0;
  for (let i = 0; i < n; i++) {
    const frac = i / n;
    let v = o.v0;
    if (o.fast && d < 2000) v *= 1.14;
    if (o.fade && frac > 0.7) v *= 0.88;
    v *= 1 + 0.03 * Math.sin(i / 7);
    time.push(t0 + i * dt);
    dist.push(Math.round(d));
    d += v * dt;
    let h = o.hr0 + (o.drift || 0) * frac + ri(-3, 3);
    if (chance(0.02)) h = ri(0, 39); // titik HR hilang / rendah
    hr.push(Math.round(h));
  }
  return { time, distance: dist, heartrate: hr };
}

const cases = [];
const PARAMS = [[55, 188, 35], [55, 188, 35], [0, 0, 35], [60, 0, 40], [150, 140, 30], [50, 190, 0]];
for (let i = 0; i < 40; i++) {
  const [rest, max, age] = pick(PARAMS);
  const o = {
    n: pick([320, 360, 400]), dt: pick([5, 6, 8, 12, 15]), v0: pick([2.6, 3.0, 3.4, 4.0]), t0: pick([0, 0, 90]),
    fast: chance(0.4), fade: chance(0.4), hr0: pick([125, 140, 152, 165]), drift: pick([0, 0, 4, 12]),
  };
  let raw = makeStream(o), minN;
  const kind = i % 10;
  if (kind === 1) raw.heartrate = null;                                      // tanpa HR
  else if (kind === 2) raw.heartrate = raw.heartrate.slice(0, -1);            // panjang HR beda
  else if (kind === 3) { o.n = 250; raw = makeStream(o); }                    // terlalu pendek -> null
  else if (kind === 4) raw.distance = raw.distance.slice(0, -1);              // time/dist beda panjang -> null
  else if (kind === 5) { o.n = 320; o.dt = 5; raw = makeStream(o); }          // T < 2400: tanpa decoupling
  else if (kind === 6) { o.n = 250; raw = makeStream(o); minN = 100; }        // minN kustom lolos
  else if (kind === 7) minN = 0;                                              // minN 0 -> 300
  const args = { rest, max, age, minN };
  let res = runStream(rest, max, age, raw, minN);
  cases.push({ id: "s" + i, args, raw, res });
}

// hwSummary: titik [detik, hr, m/s]
const hw = [];
for (let i = 0; i < 16; i++) {
  const [rest, max, age] = pick(PARAMS);
  const n = i === 0 ? 30 : i === 1 ? 40 : ri(60, 220);
  const dt = pick([5, 8, 10, 25]);
  const pts = [];
  let t = pick([0, 100]);
  for (let j = 0; j < n; j++) {
    t += dt;
    let sp = Math.round((pick([2.6, 3.1, 3.7]) * (j < 20 && chance(0.7) ? 1.15 : 1) + ri(-3, 3) / 10) * 10) / 10;
    let h = Math.round(pick([130, 150, 165]) + ri(-4, 4) + (j / n) * 8);
    let p = [t, h, sp];
    if (i % 5 === 2 && chance(0.15)) p = [t, h];            // kecepatan hilang
    if (i % 5 === 3 && chance(0.1)) p = [t];                // hanya waktu
    if (i % 5 === 4 && chance(0.1)) p = [t - dt, h, sp];    // waktu mundur/duplikat -> dilewati
    if (chance(0.03)) p[1] = 0;
    pts.push(p);
  }
  hw.push({ id: "h" + i, args: { rest, max, age }, pts, res: runHw(rest, max, age, pts) });
}

const fmt = (r) => {
  if (r === null) return "null";
  if (r.none) return "none";
  return [
    r.fast ? `f=${r.fast.p12},${r.fast.med},${r.fast.pct}` : null,
    r.fade ? `a=${r.fade.pct}` : null,
    r.dec ? `d=${r.dec.pct}` : null,
    r.tiz ? `z=${r.tiz.join(",")}` : null,
  ].filter(Boolean).join(";");
};
let out = "# DIHASILKAN OTOMATIS dari runsense.html (gen/gen_stream.js). Jangan edit tangan.\n";
for (const c of cases) {
  out += `G ${c.id} ${c.args.rest} ${c.args.max} ${c.args.age} ${c.args.minN === undefined ? "-" : c.args.minN}\n`;
  out += "T " + c.raw.time.join(",") + "\n";
  out += "Q " + c.raw.distance.join(",") + "\n";
  out += "H " + (c.raw.heartrate ? c.raw.heartrate.join(",") : "-") + "\n";
  out += "Y " + fmt(c.res) + "\n";
}
for (const c of hw) {
  out += `U ${c.id} ${c.args.rest} ${c.args.max} ${c.args.age}\n`;
  out += "P " + c.pts.map((p) => p.join(":")).join(",") + "\n";
  out += "Y " + fmt(c.res) + "\n";
}
const dest = process.argv[2] || __dirname + "/../src/test/resources/golden_stream.txt";
fs.writeFileSync(dest, out);
const cov = {};
for (const c of [...cases, ...hw]) { const k = c.res === null ? "null" : c.res.none ? "none" : Object.keys(c.res).sort().join("+"); cov[k] = (cov[k] || 0) + 1; }
console.log("bytes", out.length, cov);
