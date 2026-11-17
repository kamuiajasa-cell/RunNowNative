// Menjalankan kode ASLI lapisan data dan cuaca dari runsense.html (Langkah 5 dan 6): row/mergeActs/sync/ingestWatch/
// persist/dedupeActs/ensureStream dan seluruh blok "Climate-aware coach" (cl*, wxGet, clMatch, gPick), dengan jam palsu,
// localStorage palsu, dan fetch palsu. Nomor baris dicari lewat penanda, jadi tahan terhadap pergeseran baris.
process.env.TZ = "UTC";
const fs = require("fs");
const { setNow } = require("./design_harness"); // memasang FakeDate (global Date) dan $ palsu
const lines = fs.readFileSync(process.env.RUNSENSE_HTML || "runsense.html", "utf8").split("\n");
const OFF = lines.findIndex((l) => l.startsWith("  const clamp = (v, a, b)")) + 1 - 797;
const slice = (a, b) => lines.slice(a + OFF - 1, b + OFF).join("\n"); // acuan Langkah 2
const sliceAbs = (a, b) => lines.slice(a - 1, b).join("\n");
const find = (re, from = 0) => { for (let i = from; i < lines.length; i++) if (re.test(lines[i])) return i + 1; throw new Error("tidak ketemu: " + re); };
const fnBlock = (re) => { const s = find(re); const e = find(/^  \}$/, s); return sliceAbs(s, e); };
const oneLine = (re) => { const s = find(re); return sliceAbs(s, s); };

const climStart = find(/^  const CK = "runsense_clim";/);
const climEnd = find(/^  window\.openAct = openAct;/) - 1;

const code =
  sliceAbs(find(/^  const SN = /), find(/^  const VX_LB = /)) + "\n" +
  oneLine(/^  const KEY_STORE = /) + "\n" +
  slice(797, 1130) + "\n" +
  "function renderAll() {}\nconst SYNC = {};\nfunction setSync(m, c) { SYNC.msg = m; SYNC.cls = c; }\nfunction showError(m) { SYNC.err = m; }\nfunction afterIngest() {}\nlet curRec = null;\n" +
  oneLine(/^  let syncing = false;/) + "\n" +
  fnBlock(/^  async function icuGet\(path\)/) + "\n" +
  oneLine(/^  const sxBusy = \{\};/) + "\n" + oneLine(/^  let curAct = 0;/) + "\n" +
  fnBlock(/^  function summarizeStream\(raw, a, minN\)/) + "\n" +
  fnBlock(/^  async function ensureStream\(a\)/) + "\n" +
  fnBlock(/^  function kind\(t\)/) + "\n" +
  fnBlock(/^  function row\(a\)/) + "\n" +
  fnBlock(/^  function mergeActs\(list\)/) + "\n" +
  fnBlock(/^  async function sync\(silent\)/) + "\n" +
  fnBlock(/^  function persist\(\)/) + "\n" +
  oneLine(/^  const isHw = /) + "\n" +
  fnBlock(/^  function matchIcu\(r\)/) + "\n" +
  fnBlock(/^  function dedupeActs\(\)/) + "\n" +
  fnBlock(/^  function hwSummary\(pts\)/) + "\n" +
  fnBlock(/^  function ingestWatch\(d\)/) + "\n" +
  sliceAbs(climStart, climEnd) + "\n" +
  "return { state, SYNC, get CL() { return CL; }, set CL(v) { CL = v; }, clAvg, clV, clTh, clCls, clK, clPers, clSc, clPersTxt, clStat, clProfile, gOK, gPick, clGpsPts, clGpsIcu, clMatch, wxGet, clSlots, clSave," +
  " mergeActs, sync, persist, dedupeActs, ingestWatch, ensureStream, row, kind, matchIcu, hwSummary, summarizeStream, icuGet };";
const factory = new Function("state", code);

// ---- lingkungan palsu ----
const store = new Map();
global.localStorage = { getItem: (k) => (store.has(k) ? store.get(k) : null), setItem: (k, v) => { store.set(k, String(v)); }, removeItem: (k) => { store.delete(k); } };
global.window = global.window || {};
global.navigator = global.navigator || {};
global.setTimeout = () => 0; // clMatch menjadwalkan dirinya sendiri; pemanggilan berikutnya dilakukan manual
global.btoa = global.btoa || ((s) => Buffer.from(s, "binary").toString("base64"));

const jsonRes = (body, status = 200) => ({ ok: status >= 200 && status < 300, status, json: async () => JSON.parse(JSON.stringify(body)) });

/** Cuaca sintetis: hanya aritmetika bilangan bulat supaya pasangan Kotlin-nya identik (lihat FakeWeather di tes :data). */
function synthWx(url) {
  const u = new URL(url);
  const la = +u.searchParams.get("latitude"), lo = +u.searchParams.get("longitude");
  let from, to; // ms UTC (zona = UTC karena TZ=UTC), jam bulat
  if (u.pathname.endsWith("/forecast")) {
    const past = +u.searchParams.get("past_days"), fc = +u.searchParams.get("forecast_days");
    const d0 = Math.floor(Date.now() / 864e5) * 864e5;
    from = d0 - past * 864e5; to = d0 + fc * 864e5 - 36e5;
  } else {
    const day = u.searchParams.get("start_date");
    from = Date.parse(day + "T00:00:00Z"); to = from + 864e5 - 36e5;
  }
  const time = [], T = [], RH = [], AT = [], W = [], P = [], SR = [], CC = [], WC = [], PP = [];
  const latI = Math.round(la * 100), lonI = Math.round(lo * 100);
  for (let ms = from; ms <= to; ms += 36e5) {
    const h = new Date(ms).getUTCHours(), dn = Math.floor(ms / 864e5);
    time.push(new Date(ms).toISOString().slice(0, 13) + ":00");
    const t = 22 + ((h * 5 + Math.abs(latI) * 3 + dn * 2) % 13);
    T.push(t); AT.push(t + ((h + dn) % 4)); RH.push(50 + ((h * 7 + Math.abs(lonI)) % 41));
    W.push((h * 3 + dn) % 17); P.push(((h + dn + latI) % 23) === 0 ? 2.5 : ((h + dn) % 11) === 0 ? 0.4 : 0);
    SR.push(h >= 6 && h <= 17 ? ((h - 5) * 60) % 700 : 0); CC.push((h * 13) % 101); WC.push(((h + dn) % 29) === 0 ? 95 : (h % 5) * 3); PP.push((h * 9) % 60);
  }
  if (latI % 7 === 0 && Math.abs(latI) % 2 === 1) { T[3] = null; AT[3] = null; } // sebagian kosong (hanya aritmetika bilangan bulat)
  return { hourly: { time, temperature_2m: T, relative_humidity_2m: RH, apparent_temperature: AT, wind_speed_10m: W, precipitation: P, shortwave_radiation: SR, cloud_cover: CC, weather_code: WC, precipitation_probability: PP } };
}

/**
 * Buat lingkungan baru untuk satu kasus.
 *  ds: dataset (buildDataset); st: bidang tambahan state; cl: CL awal (disimpan ke localStorage); icu: pemetaan path->respons / fungsi.
 */
function makeEnv(ds, st, cl, icu) {
  setNow(ds.now);
  store.clear();
  if (cl) store.set("runsense_clim", JSON.stringify(cl));
  const log = [];
  global.fetch = async (url, opt) => {
    url = String(url);
    log.push(url);
    if (url.includes("open-meteo.com")) {
      if (st && st.wxFail) return jsonRes({}, 500);
      return jsonRes(synthWx(url));
    }
    const path = url.replace("https://intervals.icu/api/v1/", "");
    const r = icu && (typeof icu === "function" ? icu(path) : icu[path.split("?")[0]] !== undefined ? icu[path.split("?")[0]] : icu[path]);
    if (r === undefined) return jsonRes({}, 404);
    if (r && r.__throw) throw new TypeError("Failed to fetch");
    if (r && r.__status) return jsonRes({}, r.__status);
    return jsonRes(r);
  };
  const state = JSON.parse(JSON.stringify({
    acts: ds.acts, wellness: ds.wellness, vx: ds.vx, sx: ds.sx || {}, rest: ds.rest, max: ds.max, age: ds.age, afS: ds.afS,
    athlete: null, apiKey: "KEY", icuName: "", icuLast: 0, hwLast: 0, restManual: 0, lthrC: 0, ltpC: 0, ...(st || {}),
  }));
  const E = factory(state);
  E.log = log;
  E.store = store;
  return E;
}
module.exports = { makeEnv, synthWx };
