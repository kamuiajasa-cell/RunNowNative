// Menjalankan kode ASLI reviewRun / reviewHeadline / runBacktest / summarizeStream / hwSummary dari runsense.html (Langkah 4).
process.env.TZ = "UTC";
const fs = require("fs");
const { setNow } = require("./design_harness"); // memasang FakeDate dan $ palsu
const lines = fs.readFileSync(process.env.RUNSENSE_HTML || "runsense.html", "utf8").split("\n");
const OFF = lines.findIndex((l) => l.startsWith("  const clamp = (v, a, b)")) + 1 - 797;
const slice = (a, b) => lines.slice(a + OFF - 1, b + OFF).join("\n"); // acuan Langkah 2
const sliceAbs = (a, b) => lines.slice(a - 1, b).join("\n");
const find = (re, from = 0) => { for (let i = from; i < lines.length; i++) if (re.test(lines[i])) return i + 1; throw new Error("tidak ketemu: " + re); };
const fnBlock = (re) => { const s = find(re); const e = find(/^  \}$/, s); return sliceAbs(s, e); }; // fungsi tingkat atas berakhir di "  }"

const pdStart = find(/^  function processData\(\)/);
const pdEnd = find(/^  \/\* -+ Tampilan gabungan/, pdStart) - 1;

const code =
  sliceAbs(find(/^  const SN = /), find(/^  const VX_LB = /)) + "\n" +
  slice(797, 1130) + "\n" +
  slice(1939, 1939) + "\n" +
  slice(1941, 2555) + "\n" +
  "function renderAll() {}\n" +
  sliceAbs(pdStart, pdEnd) + "\n" +
  fnBlock(/^  function reviewRun\(a\)/) + "\n" +
  fnBlock(/^  function reviewHeadline\(rev\)/) + "\n" +
  fnBlock(/^  function runBacktest\(\)/) + "\n" +
  fnBlock(/^  function summarizeStream\(raw, a, minN\)/) + "\n" +
  fnBlock(/^  function hwSummary\(pts\)/) + "\n" +
  "return { processData, reviewRun, reviewHeadline, runBacktest, summarizeStream, hwSummary };";
const factory = new Function("state", code);

function makeState(ds, cs) {
  return JSON.parse(JSON.stringify({
    acts: ds.acts, wellness: ds.wellness, vx: ds.vx, sx: ds.sx || {}, rest: ds.rest, max: ds.max, age: ds.age, afS: ds.afS,
    style: "moderate", plan: {}, race: "", raceDist: 0, raceGoal: "", rw: { on: false, run: 225, walk: 30 }, heat: "", flag: null,
    athlete: null, apiKey: "x", goal: "5K", icuName: "", lthrC: cs.lthrC, ltpC: cs.ltpC, restManual: cs.restManual,
    icuLast: 0, hwLast: 0,
  }));
}

/** Satu kasus: processData dulu (mengisi state.athlete dan rest), lalu review beberapa aktivitas + backtest. */
function runReview(ds, cs, pickStarts) {
  setNow(ds.now);
  const state = makeState(ds, cs);
  const stubs = {};
  global.$ = (sel) => (stubs[sel] = stubs[sel] || { textContent: "", innerHTML: "", classList: { add() {}, remove() {}, contains() { return false; } } });
  const E = factory(state);
  E.processData();
  const reviews = pickStarts.map((start) => {
    const a = state.acts.find((x) => x[0] === start);
    const rev = E.reviewRun(a);
    return { start, rev, head: E.reviewHeadline(rev), legacy: E.reviewHeadline(rev.flat) };
  });
  E.runBacktest();
  return { state, reviews, sub: stubs["#mSub"].textContent, body: stubs["#mBody"].innerHTML };
}

/** Satu stream mentah (objek time/distance/heartrate) -> ringkasan JS. */
function runStream(rest, max, age, raw, minN) {
  setNow(1760000000000);
  const state = { acts: [], wellness: [], vx: {}, sx: {}, rest, max, age, afS: 0, athlete: null };
  const E = factory(state);
  return E.summarizeStream(raw, null, minN);
}
function runHw(rest, max, age, pts) {
  setNow(1760000000000);
  const state = { acts: [], wellness: [], vx: {}, sx: {}, rest, max, age, afS: 0, athlete: null };
  const E = factory(state);
  return E.hwSummary(pts);
}
module.exports = { runReview, runStream, runHw };
