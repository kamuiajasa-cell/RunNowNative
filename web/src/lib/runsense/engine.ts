import type {
  Act,
  Athlete,
  Derived,
  Goal,
  GoalEst,
  HistoryItem,
  Plan,
  PlanBlock,
  PlanStep,
  Style,
  Wellness,
} from "./model";
import { STAGE_NAMES } from "./model";

const PACE_METS: [number, number][] = [
  [4, 14.5],
  [4.5, 13],
  [5, 12],
  [5.5, 11],
  [6, 9.8],
  [6.5, 9],
  [7, 8.3],
  [7.5, 7.7],
  [8, 7.2],
  [8.5, 6.7],
  [9, 6.3],
  [9.5, 5.9],
  [10, 5.5],
  [10.5, 5.1],
  [11, 4.8],
  [12, 4.3],
  [13, 3.9],
  [14, 3.6],
];

export const clamp = (v: number, a: number, b: number) => Math.max(a, Math.min(b, v));

export function f1(n: number | null | undefined) {
  if (n == null || !Number.isFinite(n)) return "—";
  return (Math.round(n * 10) / 10).toFixed(1).replace(/\.0$/, "");
}

export function paceLabel(minPerKm: number) {
  if (!(minPerKm > 0)) return "—";
  const m = Math.floor(minPerKm);
  const s = Math.round((minPerKm - m) * 60);
  return `${m}:${String(s % 60).padStart(2, "0")}`;
}

export function clock(sec: number) {
  const s = Math.max(0, Math.round(sec));
  const m = Math.floor(s / 60);
  const r = s % 60;
  if (m >= 60) {
    const h = Math.floor(m / 60);
    return `${h}:${String(m % 60).padStart(2, "0")}:${String(r).padStart(2, "0")}`;
  }
  return `${String(m).padStart(2, "0")}:${String(r).padStart(2, "0")}`;
}

export function fmtMin(m: number) {
  if (!(m > 0)) return "—";
  if (m < 60) return `${Math.round(m)} mnt`;
  const h = Math.floor(m / 60);
  const r = Math.round(m % 60);
  return r ? `${h} j ${r} m` : `${h} j`;
}

export function dayStart(ms: number) {
  const d = new Date(ms);
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
}

export function todayStart() {
  return dayStart(Date.now());
}

const DAY = ["Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab"];

export function metsAt(pace: number) {
  if (pace <= PACE_METS[0][0]) return PACE_METS[0][1];
  const last = PACE_METS[PACE_METS.length - 1];
  if (pace >= last[0]) return last[1];
  for (let i = 1; i < PACE_METS.length; i++) {
    if (pace <= PACE_METS[i][0]) {
      const t = (pace - PACE_METS[i - 1][0]) / (PACE_METS[i][0] - PACE_METS[i - 1][0]);
      return PACE_METS[i - 1][1] + t * (PACE_METS[i][1] - PACE_METS[i - 1][1]);
    }
  }
  return 4;
}

export function paceOf(mets: number) {
  let lo = 3.5;
  let hi = 16;
  for (let i = 0; i < 36; i++) {
    const mid = (lo + hi) / 2;
    if (metsAt(mid) < mets) lo = mid;
    else hi = mid;
  }
  return (lo + hi) / 2;
}

function pctAt(min: number) {
  const T: [number, number][] = [
    [4, 1],
    [6, 0.975],
    [25, 0.825],
    [67, 0.625],
    [90, 0.6],
  ];
  if (min <= 4) return 1;
  if (min >= 90) return 0.6;
  for (let i = 1; i < T.length; i++) {
    if (min <= T[i][0]) {
      return T[i - 1][1] + ((T[i][1] - T[i - 1][1]) * (min - T[i - 1][0])) / (T[i][0] - T[i - 1][0]);
    }
  }
  return 0.6;
}

export function durText(p: number) {
  if (p >= 0.95) return "4–8 mnt";
  if (p >= 0.85) return "8–20 mnt";
  if (p >= 0.8) return "20–30 mnt";
  if (p >= 0.65) return "30–45 mnt";
  if (p >= 0.6) return "45–90+ mnt";
  return ">90 mnt";
}

export function band(m: number | null) {
  if (m == null) return "—";
  if (m < 7.9) return "Rendah";
  if (m < 10.9) return "Menengah";
  return "Tinggi";
}

export function loadOf(a: Act) {
  if (!(a.min > 0)) return 0;
  if (a.kind === "run") return Math.round(a.min * metsAt(a.km > 0 ? a.min / a.km : 8) * 0.55);
  if (a.kind === "walk") return Math.round(a.min * 2.2);
  return Math.round(a.min * 1.5);
}

function loadDays(acts: Act[], d: number) {
  const cut = Date.now() - d * 864e5;
  return acts.filter((a) => a.t >= cut).reduce((s, a) => s + loadOf(a), 0);
}

export function estMax(acts: Act[], rest: number, max: number) {
  const out = { m: null as number | null, src: "", n: 0, conf: "", perf: null as number | null, hrr: null as number | null };
  const now = Date.now();
  const runs = acts.filter(
    (x) =>
      x.kind === "run" &&
      x.km >= 1.5 &&
      x.min >= 8 &&
      x.t > now - 56 * 864e5 &&
      x.min / x.km >= 3 &&
      x.min / x.km <= 14,
  );
  const rng = max - rest;
  if (rest > 0 && rng >= 40) {
    const pts: { m: number; w: number }[] = [];
    runs.forEach((x) => {
      if (x.min < 10 || !(x.hr > 0)) return;
      const f = (x.hr - rest) / rng;
      if (f < 0.45 || f > 1.02) return;
      pts.push({ m: 1 + (metsAt(x.min / x.km) - 1) / Math.min(1, f), w: f * f });
    });
    if (pts.length >= 3) {
      pts.sort((a, b) => a.m - b.m);
      const tw = pts.reduce((s, p) => s + p.w, 0);
      let acc = 0;
      let med = pts[0].m;
      for (const p of pts) {
        acc += p.w;
        if (acc >= tw / 2) {
          med = p.m;
          break;
        }
      }
      out.hrr = med;
      out.n = pts.length;
    }
  }
  if (runs.length >= 3) {
    let best = 0;
    runs.forEach((x) => {
      const m = metsAt(x.min / x.km) / pctAt(x.min);
      if (m > best) best = m;
    });
    if (best > 0) out.perf = best;
  }
  const cl = (v: number) => clamp(v, 4, 25);
  if (out.hrr != null) {
    out.m = cl(Math.max(out.hrr, out.perf != null ? 0.9 * out.perf : 0));
    out.src = `HR+pace dari ${out.n} lari (56 hari)`;
    out.conf = out.n >= 6 ? "baik" : "sedang";
  } else if (out.perf != null) {
    out.m = cl(0.9 * out.perf);
    out.src = "batas bawah performa pace";
    out.conf = "rendah";
  }
  return out;
}

export function stageOf(acts: Act[], rest: number, max: number, prev: number) {
  const e = estMax(acts, rest, max);
  if (e.m == null) {
    return { s: acts.some((r) => r.kind === "run") ? 4 : 2, m: null as number | null, e };
  }
  const M = e.m;
  const raw = M < 7.9 ? 1 : M < 9.2 ? 2 : M < 12 ? 3 : 4;
  let s = raw;
  if (prev && prev !== raw && Math.abs(prev - raw) === 1) {
    const up = raw > prev;
    const lo = Math.min(prev, raw);
    if (lo < 3) {
      const b = lo === 1 ? 7.9 : 9.2;
      if (up ? M < b + 0.3 : M > b - 0.3) s = prev;
    }
  }
  return { s, m: M, e };
}

function readiness(acts: Act[], wellness: Wellness[]) {
  let sc = 70;
  let inputs = 0;
  const rh = wellness.filter((w) => (w.restingHR ?? 0) > 30).map((w) => w.restingHR as number);
  if (rh.length >= 8) {
    const b = rh.slice(-29, -1).sort((p, q) => p - q);
    sc += clamp((b[Math.floor(b.length / 2)] - rh[rh.length - 1]) * 1.3, -14, 12);
    inputs++;
  }
  const acute = loadDays(acts, 7);
  const chronic = loadDays(acts, 28) / 4;
  if (chronic > 25) {
    inputs++;
    const r = acute / chronic;
    if (r > 1.5) sc -= 20;
    else if (r > 1.3) sc -= 12;
    else if (r > 1.15) sc -= 5;
    else if (r >= 0.8 && r <= 1.1) sc += 5;
    else if (r < 0.6) sc -= 6;
  }
  const y = todayStart() - 864e5;
  if (acts.some((a) => dayStart(a.t) === y && a.kind === "run" && loadOf(a) > 90)) sc -= 10;
  let streak = 0;
  for (let i = 0; i < 5; i++) {
    const t = todayStart() - i * 864e5;
    const L = acts.filter((a) => dayStart(a.t) === t).reduce((s, a) => s + loadOf(a), 0);
    if (L > 70) streak++;
    else break;
  }
  if (streak >= 3) sc -= 12;
  else if (streak >= 2) sc -= 5;
  return inputs ? clamp(Math.round(sc), 22, 98) : null;
}

function step(
  kind: PlanStep["kind"],
  title: string,
  detail: string,
  seconds: number,
  intensity: PlanStep["intensity"] = "none",
  low = 0,
  high = 0,
): PlanStep {
  return { kind, title, detail, seconds, intensity, low, high };
}

function block(repeat: number, steps: PlanStep[]): PlanBlock {
  return { repeat, steps };
}

function totalLabel(blocks: PlanBlock[]) {
  const sec = blocks.reduce((s, b) => s + b.repeat * b.steps.reduce((x, st) => x + st.seconds, 0), 0);
  return fmtMin(sec / 60);
}

export function designToday(opts: {
  acts: Act[];
  wellness: Wellness[];
  style: Style;
  rest: number;
  max: number;
  prevStage: number;
  skipped: boolean;
}): Plan {
  const st = stageOf(opts.acts, opts.rest, opts.max, opts.prevStage);
  const score0 = readiness(opts.acts, opts.wellness);
  const score = score0 == null ? 60 : score0;
  const qOk = !(st.s <= 2);
  const done = opts.acts.filter((a) => dayStart(a.t) === todayStart() && (a.kind === "run" || a.kind === "walk"));

  if (done.length) {
    const km = done.reduce((s, a) => s + a.km, 0);
    const min = done.reduce((s, a) => s + a.min, 0);
    const pace = km > 0 ? min / km : 0;
    return {
      tag: "HARI INI",
      tone: "ok",
      status: "done",
      title: "Sudah selesai",
      durationLabel: fmtMin(min),
      hr: "—",
      pace: pace > 0 ? `${paceLabel(pace)} /km` : "—",
      reason: `${f1(km)} km · ${fmtMin(min)} sudah masuk. Menu berikutnya menyusul besok.`,
      blocks: [],
    };
  }

  if (opts.skipped) {
    return {
      tag: "SKIP",
      tone: "warn",
      status: "skip",
      title: "Dilewati",
      durationLabel: "—",
      hr: "—",
      pace: "—",
      reason: "Sesi hari ini dilewati. Istirahat juga bagian latihan.",
      blocks: [],
    };
  }

  let tsb: number | null = null;
  if (opts.wellness.length) {
    const latest = opts.wellness[opts.wellness.length - 1];
    if (latest.ctl != null && latest.atl != null) tsb = latest.ctl - latest.atl;
    else if (latest.tsb != null) tsb = latest.tsb;
  }

  let qualityCount = 0;
  const now = Date.now();
  opts.acts.forEach((a) => {
    if (now - a.t > 7 * 864e5) return;
    if (a.kind === "run" && loadOf(a) > 75) qualityCount++;
  });

  if (score < 42 || (tsb != null && tsb < -25)) {
    return {
      tag: "RECOVERY",
      tone: "bad",
      status: "rest",
      title: "Istirahat",
      durationLabel: "—",
      hr: "—",
      pace: "—",
      reason:
        score < 42
          ? "Beban terakhir terlalu rapat. Recovery dulu, jangan kirim sesi keras."
          : `Form sangat rendah (TSB ${Math.round(tsb ?? 0)}). Prioritaskan pemulihan.`,
      blocks: [block(1, [step("walk", "Opsional", "Jalan santai tanpa target", 20 * 60)])],
    };
  }

  if (score < 55 || (tsb != null && tsb < -12) || qualityCount >= 3 || (opts.style === "conservative" && score < 65)) {
    const blocks = [
      block(1, [
        step("warmup", "Pemanasan", "3 mnt jalan", 3 * 60),
        step("run", "Inti", "Sangat mudah, masih bisa ngobrol", 30 * 60, "hrPercent", 55, 68),
        step("cooldown", "Pendinginan", "3 mnt jalan", 3 * 60),
      ]),
    ];
    return {
      tag: "RECOVERY",
      tone: "warn",
      status: "train",
      title: "Recovery Run",
      durationLabel: totalLabel(blocks),
      hr: "Zone 1–2",
      pace: "Sangat mudah",
      reason:
        qualityCount >= 3
          ? `Sudah ${qualityCount} sesi berat minggu ini. Recovery dulu.`
          : "Form masih negatif. Jaga tetap mudah.",
      blocks,
    };
  }

  if (st.s === 1) {
    const mins = score > 70 ? 35 : 25;
    const blocks = [
      block(1, [
        step("warmup", "Pemanasan", "3 mnt jalan", 3 * 60),
        step("walk", "Inti", "Jalan cepat, masih bisa bicara", (mins - 6) * 60),
        step("cooldown", "Pendinginan", "3 mnt jalan", 3 * 60),
      ]),
    ];
    return {
      tag: "FONDASI",
      tone: "ok",
      status: "train",
      title: "Jalan cepat",
      durationLabel: totalLabel(blocks),
      hr: "Napas nyaman",
      pace: "10:00–10:40 /km",
      reason: st.m
        ? `Tahap fondasi. Max ≈ ${f1(st.m)} METs. Jalan cepat adalah stimulus yang tepat.`
        : "Bangun fondasi aerobik dulu.",
      blocks,
    };
  }

  if (st.s === 2) {
    const reps = score > 68 ? 8 : 6;
    const blocks = [
      block(1, [step("warmup", "Pemanasan", "5 mnt jalan", 5 * 60, "none", 0, 0)]),
      block(reps, [
        step("run", "Lari", "1 mnt lari", 60, "hrPercent", 70, 82),
        step("walk", "Jalan", "1 mnt jalan", 60),
      ]),
      block(1, [step("cooldown", "Pendinginan", "4 mnt jalan", 4 * 60)]),
    ];
    return {
      tag: "FONDASI",
      tone: "ok",
      status: "train",
      title: "Run-walk",
      durationLabel: totalLabel(blocks),
      hr: "Ikuti napas",
      pace: st.m ? `${paceLabel(paceOf(0.9 * st.m))} /km saat lari` : "Nyaman",
      reason: "Pola run-walk membangun toleransi sebelum lari non-stop.",
      blocks,
    };
  }

  const dow = new Date().getDay();
  const wantQuality =
    qOk &&
    (opts.style === "aggressive" || (opts.style === "moderate" && score >= 68)) &&
    (dow === 2 || dow === 4 || dow === 5) &&
    qualityCount < 2 &&
    st.s >= 3;

  if (wantQuality) {
    let lo = 250;
    let hi = 280;
    if (st.m) {
      const p = paceOf(0.85 * st.m);
      lo = Math.round(p * 60);
      hi = Math.round(p * 60 * 1.06);
    }
    const blocks = [
      block(1, [step("warmup", "Pemanasan", "10 mnt easy", 10 * 60, "hrPercent", 60, 72)]),
      block(3, [
        step("run", "Threshold", "6 mnt terkontrol", 6 * 60, "pace", lo, hi),
        step("rest", "Jeda", "2 mnt jog ringan", 2 * 60, "hrPercent", 55, 65),
      ]),
      block(1, [step("cooldown", "Pendinginan", "8 mnt easy", 8 * 60, "hrPercent", 55, 68)]),
    ];
    return {
      tag: "QUALITY",
      tone: "warn",
      status: "train",
      title: "Tempo",
      durationLabel: totalLabel(blocks),
      hr: "Zone 3–4",
      pace: `${paceLabel(lo / 60)}–${paceLabel(hi / 60)} /km`,
      reason: "Form cukup untuk stimulus kualitas. Bukan all-out.",
      blocks,
    };
  }

  let loPace = 6.8;
  let hiPace = 7.6;
  if (st.m && st.s === 3) {
    loPace = Math.max(loPace, paceOf(0.7 * st.m));
    hiPace = loPace * 1.08;
  } else if (st.m) {
    loPace = paceOf(0.65 * st.m);
    hiPace = loPace * 1.08;
  }
  const mins = score > 72 ? 50 : score > 55 ? 40 : 32;
  const loSec = Math.round(loPace * 60);
  const hiSec = Math.round(hiPace * 60);
  const blocks = [
    block(1, [
      step("warmup", "Pemanasan", "5 mnt jog", 5 * 60, "hrPercent", 55, 68),
      step(
        "run",
        "Inti",
        `${mins - 10} mnt @ ${paceLabel(loPace)}–${paceLabel(hiPace)}`,
        (mins - 10) * 60,
        "pace",
        loSec,
        hiSec,
      ),
      step("cooldown", "Pendinginan", "5 mnt easy", 5 * 60, "hrPercent", 50, 65),
    ]),
  ];
  return {
    tag: st.s === 3 ? "AEROBIK" : "AEROBIK",
    tone: "ok",
    status: "train",
    title: st.s === 3 ? "Easy terbatas" : "Easy Run",
    durationLabel: totalLabel(blocks),
    hr: "Zone 2",
    pace: `${paceLabel(loPace)}–${paceLabel(hiPace)} /km`,
    reason: st.m
      ? `≈ ${Math.round((metsAt((loPace + hiPace) / 2) / st.m) * 100)}% kapasitas · tahan ${durText(metsAt((loPace + hiPace) / 2) / st.m)}.`
      : "Bangun volume aerobik. Tetap di zona nyaman.",
    blocks,
  };
}

function detectType(a: Act, acts: Act[], mets: number | null): { type: string; why: string } {
  if (a.kind !== "run") return { type: "lain", why: "Bukan lari" };
  const pace = a.km > 0 ? a.min / a.km : 0;
  const recent = acts
    .filter((x) => x.kind === "run" && x.km >= 1 && x.t < a.t && x.t >= a.t - 28 * 864e5)
    .map((x) => x.km);
  const med = recent.length >= 3 ? [...recent].sort((p, q) => p - q)[Math.floor(recent.length / 2)] : 0;
  if (a.km >= 6 && med > 0 && a.km >= 1.3 * med) {
    return { type: "Long", why: `${f1(a.km)} km, lebih jauh dari biasamu` };
  }
  if (mets && pace > 0) {
    const pct = metsAt(pace) / mets;
    if (pct >= 0.88 && a.min >= 8 && a.min <= 40) return { type: a.min <= 20 ? "VO2" : "Tempo", why: `≈ ${Math.round(pct * 100)}% kapasitas` };
    if (pct >= 0.78 && a.min >= 15) return { type: "Tempo", why: `Di atas easy (≈ ${Math.round(pct * 100)}%)` };
  }
  return { type: "Easy", why: "Usaha merata" };
}

function reviewToday(acts: Act[], mets: number | null, stage: number) {
  const notes: { tone: "g" | "y"; text: string }[] = [];
  const today = acts.filter((x) => dayStart(x.t) === todayStart() && x.kind === "run" && x.km > 0);
  for (const a of today) {
    if (!mets) continue;
    const pace = a.min / a.km;
    const pct = metsAt(pace) / mets;
    const lim = pctAt(a.min);
    if (a.min >= 10 && pct > lim + 0.07) {
      notes.push({
        tone: "y",
        text: `Intensitas ≈ ${Math.round(pct * 100)}% kapasitas. Di level itu tubuh biasanya tahan ${durText(pct)}. Besok pelankan.`,
      });
    } else if (a.min >= 20 && pct <= 0.68) {
      notes.push({ tone: "g", text: `≈ ${Math.round(pct * 100)}% kapasitas — zona yang bisa ditahan lama. Bagus untuk fondasi.` });
    }
  }
  if (today.length && stage <= 2) {
    notes.push({ tone: "g", text: "Masih tahap fondasi. Label zona di jam kurang andal; ikuti napas." });
  }
  return notes;
}

export function derive(opts: {
  acts: Act[];
  wellness: Wellness[];
  goal: Goal;
  style: Style;
  name: string;
  rest: number;
  max: number;
  age: number;
  prevStage: number;
  skipped: boolean;
}): Derived & { nextStage: number } {
  const maxHr = opts.max > 0 ? opts.max : opts.age > 0 ? Math.round(208 - 0.7 * opts.age) : 0;
  const st = stageOf(opts.acts, opts.rest, maxHr, opts.prevStage);
  const plan = designToday({ ...opts, max: maxHr, prevStage: st.s });

  let ctl: number | null = null;
  let atl: number | null = null;
  let tsb: number | null = null;
  if (opts.wellness.length) {
    const latest = opts.wellness[opts.wellness.length - 1];
    ctl = latest.ctl;
    atl = latest.atl;
    if (ctl != null && atl != null) tsb = ctl - atl;
    else tsb = latest.tsb;
  }

  const runs = opts.acts.filter((a) => a.kind === "run" && a.min >= 15 && a.km >= 1.5);
  let lthr: number | null = null;
  const rng = maxHr - opts.rest;
  if (maxHr >= 140 && rng >= 40) lthr = Math.round(opts.rest + 0.88 * rng);
  else if (maxHr >= 140) lthr = Math.round(maxHr * 0.88);

  let ltPaceMin: number | null = null;
  if (st.m && st.m >= 6) ltPaceMin = paceOf(0.85 * st.m);
  const candidates = runs
    .filter((a) => a.km >= 2 && a.km <= 12 && a.min >= 12)
    .map((a) => ({ pace: a.min / a.km, km: a.km }))
    .sort((a, b) => a.pace - b.pace);
  if (candidates.length) {
    const best = candidates[0];
    const estLT = best.pace * (best.km >= 4 && best.km <= 8 ? 1.03 : 1.06);
    ltPaceMin = ltPaceMin != null ? Math.max(best.pace * 1.02, Math.min(ltPaceMin, estLT)) : estLT;
  }

  let est5k: number | null = null;
  for (const a of runs) {
    if (a.km < 2 || a.min < 10) continue;
    const pace = a.min / a.km;
    const pct = st.m ? metsAt(pace) / st.m : 0.75;
    if (pct < 0.72 && a.km > 6) continue;
    const t5 = a.min * Math.pow(5 / a.km, 1.06) * 60;
    if (est5k == null || t5 < est5k) est5k = t5;
  }
  if (st.m && st.m >= 7) {
    const fromMets = paceOf(0.96 * st.m) * 5 * 60;
    est5k = est5k == null ? fromMets : Math.min(est5k, fromMets * 1.08);
  }

  const score = readiness(opts.acts, opts.wellness);
  let formLabel = "Sedang";
  let formClass: Athlete["formClass"] = "sedang";
  if (tsb != null) {
    if (tsb >= 5) {
      formLabel = "Baik";
      formClass = "baik";
    } else if (tsb < -15) {
      formLabel = "Lelah";
      formClass = "lelah";
    }
  } else if (score != null) {
    if (score >= 72) {
      formLabel = "Baik";
      formClass = "baik";
    } else if (score < 48) {
      formLabel = "Lelah";
      formClass = "lelah";
    }
  } else {
    formLabel = "Data kurang";
    formClass = "kurang";
  }

  const GD: Record<Goal, number> = { "5K": 5, "10K": 10, Half: 21.0975, Marathon: 42.195, Umum: 5 };
  const dist = GD[opts.goal] || 5;
  const hm = (t: number) => (t >= 3600 ? clock(t) : clock(t));
  const goalEst: GoalEst =
    est5k != null
      ? {
          current: hm(est5k * Math.pow(dist / 5, 1.06)),
          basis: "Lari terbaik + METs",
          confidence: dist > 10 ? "Rendah, ini ekstrapolasi" : st.e.conf || "rendah",
        }
      : { current: "—:—", basis: "Data belum cukup", confidence: "—" };

  const athlete: Athlete = {
    name: opts.name || "Runner",
    ctl,
    atl,
    tsb,
    lthr,
    ltPace: ltPaceMin ? `${paceLabel(ltPaceMin)} /km` : null,
    formLabel,
    formClass,
    mets: st.m,
    stage: st.s,
    stageName: STAGE_NAMES[st.s] || "—",
    estSrc: st.e.src,
    estConf: st.e.conf,
    score,
  };

  const history: HistoryItem[] = [...opts.acts]
    .sort((a, b) => b.t - a.t)
    .slice(0, 24)
    .map((a) => {
      const det = detectType(a, opts.acts, st.m);
      const pace = a.km > 0 ? a.min / a.km : 0;
      const m = Math.floor(a.min);
      const s = Math.round((a.min % 1) * 60);
      return {
        t: a.t,
        date: new Date(a.t).toLocaleDateString("id-ID", { weekday: "short", day: "numeric", month: "short" }),
        title: a.kind === "run" ? "Lari" : a.kind === "walk" ? "Jalan" : "Aktivitas",
        meta: `${f1(a.km)} km · ${m}:${String(s).padStart(2, "0")}${a.hr ? ` · HR ${Math.round(a.hr)}` : ""}`,
        load: loadOf(a),
        type: det.type,
        why: det.why,
        km: a.km,
        min: a.min,
        hr: a.hr,
        pace: pace > 0 ? `${paceLabel(pace)} /km` : "—",
      };
    });

  const start = todayStart() - ((new Date().getDay() + 6) % 7) * 864e5;
  let weekKm = 0;
  let weekSessions = 0;
  const week = [];
  for (let i = 0; i < 7; i++) {
    const tm = start + i * 864e5;
    const km = opts.acts.filter((x) => dayStart(x.t) === tm && (x.kind === "run" || x.kind === "walk")).reduce((s, x) => s + x.km, 0);
    if (km > 0) {
      weekKm += km;
      weekSessions++;
    }
    week.push({ name: DAY[new Date(tm).getDay()], km, today: tm === todayStart() });
  }

  return {
    athlete,
    plan,
    goalEst,
    history,
    week,
    weekKm,
    weekSessions,
    weekLoad: loadDays(opts.acts, 7),
    review: reviewToday(opts.acts, st.m, st.s),
    nextStage: st.s,
  };
}

export function kindOf(type: string) {
  const t = String(type || "").toLowerCase();
  if (/run|trail|treadmill/.test(t)) return "run" as const;
  if (/walk|hike/.test(t)) return "walk" as const;
  return "other" as const;
}

export function rowFromIcu(a: Record<string, unknown>): Act | null {
  const k = kindOf(String(a.type || a.name || ""));
  const start = String(a.start_date_local || a.start_date || "");
  if (!start) return null;
  const e = new Date(start);
  if (!Number.isFinite(+e)) return null;
  const km = Number(a.distance || 0) / 1e3;
  const sec = Number(a.moving_time || a.elapsed_time || 0);
  if (sec <= 0 || (km <= 0 && k === "run")) return null;
  const id = a.id != null ? String(a.id) : `t${e.getTime()}`;
  return {
    t: e.getTime(),
    kind: k,
    min: sec / 60,
    km,
    hr: Number(a.average_heartrate || 0),
    id,
    name: String(a.name || "").slice(0, 50),
  };
}

export function greeting() {
  const h = new Date().getHours();
  if (h < 11) return "Selamat pagi";
  if (h < 15) return "Selamat siang";
  if (h < 18) return "Selamat sore";
  return "Selamat malam";
}

export function flattenBlocks(blocks: PlanBlock[]) {
  const out: PlanStep[] = [];
  for (const b of blocks) {
    for (let i = 0; i < b.repeat; i++) out.push(...b.steps);
  }
  return out;
}

export function planSeconds(blocks: PlanBlock[]) {
  return flattenBlocks(blocks).reduce((s, st) => s + st.seconds, 0);
}

export const STAGE_TIPS: Record<number, string> = {
  1: "Jalan cepat 20–35 menit. Naik ke run-walk bila estimasi melewati 7,9 METs.",
  2: "Run-walk sekitar 30 menit. Naik ke tahap 3 bila estimasi melewati 9,2 METs.",
  3: "Easy dibatasi sekitar 70% kapasitas.",
  4: "Planner terstruktur. Quality hanya saat form mengizinkan.",
};
