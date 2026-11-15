// Menjalankan kode ASLI processData/coachLine/intensityLine/syncLine dari runsense.html dengan jam palsu (Langkah 3).
process.env.TZ = "UTC";
const fs = require("fs");
const { setNow } = require("./design_harness"); // juga memasang FakeDate dan $ palsu
const lines = fs.readFileSync(process.env.RUNSENSE_HTML || "runsense.html", "utf8").split("\n");
const OFF = lines.findIndex((l) => l.startsWith("  const clamp = (v, a, b)")) + 1 - 797; // geser otomatis (lihat design_harness.js)
const slice = (a, b) => lines.slice(a + OFF - 1, b + OFF).join("\n"); // 1-based inklusif, acuan Langkah 2
const sliceAbs = (a, b) => lines.slice(a - 1, b).join("\n"); // nomor baris absolut
const find = (re, from = 0) => { for (let i = from; i < lines.length; i++) if (re.test(lines[i])) return i + 1; throw new Error("tidak ketemu: " + re); };

const pdStart = find(/^  function processData\(\)/);
const pdEnd = find(/^  \/\* -+ Tampilan gabungan/, pdStart) - 1;          // sampai sebelum komentar berikutnya
const clStart = find(/^  function coachLine\(/, pdEnd);
const clEnd = find(/^  function renderAll\(\)/, clStart) - 1;
const hhmmLine = find(/^  const hhmm = /);
const syncStart = find(/^  function syncLine\(/);
const syncEnd = find(/^  \}/, syncStart);

const code =
  sliceAbs(find(/^  const SN = /), find(/^  const VX_LB = /)) + "\n" +  // SN, VX_LB
  slice(797, 1130) + "\n" +                      // engine
  slice(1939, 1939) + "\n" +
  slice(1941, 2555) + "\n" +                     // rencana + designToday
  "function renderAll() {}\n" +
  sliceAbs(pdStart, pdEnd) + "\n" +
  sliceAbs(clStart, clEnd) + "\n" +
  sliceAbs(hhmmLine, hhmmLine) + "\n" +
  sliceAbs(syncStart, syncEnd) + "\n" +
  "return { processData, coachLine, intensityLine, syncLine };";

const factory = new Function("state", code);

/** Jalankan satu kasus; kembalikan state setelah processData + keluaran baris-baris teks. */
function runProcess(ds, cs) {
  setNow(ds.now);
  const state = JSON.parse(JSON.stringify({
    acts: ds.acts, wellness: ds.wellness, vx: ds.vx, rest: ds.rest, max: ds.max, age: ds.age, afS: ds.afS,
    style: cs.style, plan: cs.plan, race: cs.race, raceDist: cs.raceDist, raceGoal: cs.raceGoal,
    rw: cs.rw, heat: cs.heat, flag: cs.flag, athlete: null, apiKey: "x",
    goal: cs.goal, icuName: cs.icuName, lthrC: cs.lthrC, ltpC: cs.ltpC, restManual: cs.restManual,
    icuLast: cs.icuLast, hwLast: cs.hwLast,
  }));
  const E = factory(state);
  E.processData();
  const r = state.recommendation;
  return {
    state,
    cl: [E.coachLine(r, state.athlete), E.coachLine({ ...r, rested: true }, state.athlete), E.coachLine({ ...r, walk: true }, state.athlete)],
    il: [E.intensityLine(r), E.intensityLine({ ...r, rested: true })],
    sync: E.syncLine(),
  };
}
module.exports = { runProcess };
