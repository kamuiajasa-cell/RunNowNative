import type { ActDetail, LtFit, PlanStep, SegStat } from "./model";

export type Streams = { time: number[]; dist: number[]; hr: number[] | null };

const BEST_KM = [1, 3, 5, 10];

function fill(a: number[]) {
  let last = 0;
  return a.map((v) => {
    if (Number.isFinite(v) && v !== null) last = v;
    return last;
  });
}

/** Baca respons /activity/{id}/streams dari Intervals.icu (array {type,data} atau objek per tipe). */
export function parseStreams(json: unknown): Streams | null {
  let list: { type: string; data: unknown }[] = [];
  if (Array.isArray(json)) {
    list = json.filter((x) => x && typeof x === "object") as { type: string; data: unknown }[];
  } else if (json && typeof json === "object") {
    list = Object.entries(json as Record<string, unknown>).map(([type, v]) => ({
      type,
      data: Array.isArray(v) ? v : (v as { data?: unknown } | null)?.data,
    }));
  }
  const get = (t: string) => {
    const s = list.find((x) => x.type === t);
    return s && Array.isArray(s.data) ? (s.data as unknown[]).map((v) => (v == null ? NaN : Number(v))) : null;
  };
  const dist0 = get("distance");
  if (!dist0 || dist0.length < 60) return null;
  const time0 = get("time");
  const time = time0 && time0.length === dist0.length ? fill(time0) : dist0.map((_, i) => i);
  const dist = fill(dist0);
  const hr0 = get("heartrate");
  const hr = hr0 && hr0.length === dist0.length ? hr0.map((v) => (Number.isFinite(v) && v > 30 ? v : 0)) : null;
  return { time, dist, hr };
}

/** Waktu tercepat (detik) menempuh `meters` secara beruntun. Null kalau tidak ada. */
export function bestWindow(time: number[], dist: number[], meters: number): number | null {
  const n = dist.length;
  if (n < 2 || dist[n - 1] - dist[0] < meters) return null;
  let best = Infinity;
  let j = 0;
  for (let i = 0; i < n; i++) {
    if (j < i) j = i;
    while (j < n && dist[j] - dist[i] < meters) j++;
    if (j >= n) break;
    const dd = dist[j] - dist[j - 1];
    const frac = dd > 0 ? (meters - (dist[j - 1] - dist[i])) / dd : 1;
    const t = time[j - 1] + frac * (time[j] - time[j - 1]) - time[i];
    if (t > 0 && t < best) best = t;
  }
  if (!Number.isFinite(best)) return null;
  // buang glitch GPS: lebih cepat dari 2:30/km tidak masuk akal untuk data ini
  if (best < meters * 0.15) return null;
  return best;
}

function resample(time: number[], v: number[], total: number) {
  const out = new Array<number>(total + 1);
  let k = 0;
  for (let s = 0; s <= total; s++) {
    while (k < time.length - 2 && time[k + 1] < s) k++;
    const t0 = time[k];
    const t1 = time[k + 1];
    const f = t1 > t0 ? Math.min(1, Math.max(0, (s - t0) / (t1 - t0))) : 0;
    out[s] = v[k] + f * (v[k + 1] - v[k]);
  }
  return out;
}

/**
 * Pasangan pace-HR dari bagian lari yang stabil.
 * Jendela pace 30 dtk, HR diambil 15 dtk setelahnya (HR telat bereaksi), per 10 dtk.
 * Hasil: [pace dtk/km (bin 15 dtk), HR rata-rata, jumlah detik].
 */
export function lactateBins(s: Streams): [number, number, number][] {
  if (!s.hr) return [];
  const total = Math.floor(s.time[s.time.length - 1]);
  if (!(total > 120)) return [];
  const d = resample(s.time, s.dist, total);
  const h = resample(s.time, s.hr, total);
  const acc = new Map<number, { hr: number; n: number }>();
  for (let t = 0; t + 45 <= total; t += 10) {
    const a = d[t + 15] - d[t];
    const b = d[t + 30] - d[t + 15];
    const all = d[t + 30] - d[t];
    if (a <= 0 || b <= 0 || all <= 0) continue;
    if (Math.abs(a - b) / (all / 2) > 0.15) continue; // tidak stabil (transisi, run-walk)
    const pace = 30000 / all;
    if (pace < 240 || pace > 570) continue; // lebih lambat dari 9:30/km dianggap jalan
    let sum = 0;
    let ok = true;
    for (let u = t + 15; u <= t + 45; u++) {
      if (!(h[u] > 30)) {
        ok = false;
        break;
      }
      sum += h[u];
    }
    if (!ok) continue;
    const key = Math.round(pace / 15) * 15;
    const cur = acc.get(key) || { hr: 0, n: 0 };
    cur.hr += sum / 31;
    cur.n += 1;
    acc.set(key, cur);
  }
  return [...acc.entries()].map(([k, v]) => [k, Math.round((v.hr / v.n) * 10) / 10, v.n * 10]);
}

function ols(p: { u: number; y: number; w: number }[]) {
  let sw = 0;
  let su = 0;
  let sy = 0;
  p.forEach((q) => {
    sw += q.w;
    su += q.w * q.u;
    sy += q.w * q.y;
  });
  const mu = su / sw;
  const my = sy / sw;
  let sxx = 0;
  let sxy = 0;
  p.forEach((q) => {
    sxx += q.w * (q.u - mu) ** 2;
    sxy += q.w * (q.u - mu) * (q.y - my);
  });
  const m = sxx > 0 ? sxy / sxx : 0;
  const b = my - m * mu;
  let sse = 0;
  p.forEach((q) => {
    sse += q.w * (q.y - (m * q.u + b)) ** 2;
  });
  return { m, b, sse };
}

/** Titik tekuk kurva HR terhadap pace = LT (pace & HR sekaligus). Tidak menebak kalau data kurang. */
export function fitLT(all: [number, number, number][]): LtFit {
  const merged = new Map<number, { h: number; w: number }>();
  all.forEach(([p, h, w]) => {
    if (!(w > 0)) return;
    const cur = merged.get(p) || { h: 0, w: 0 };
    cur.h += h * w;
    cur.w += w;
    merged.set(p, cur);
  });
  const pts = [...merged.entries()]
    .map(([x, v]) => ({ x, y: v.h / v.w, w: v.w }))
    .filter((p) => p.w >= 60)
    .sort((a, b) => b.x - a.x); // lambat → cepat
  if (pts.length < 5) {
    return { reason: "Belum cukup data. Butuh minimal 5 tingkat pace dengan data ≥1 menit tiap tingkat." };
  }
  const span = pts[0].x - pts[pts.length - 1].x;
  if (span < 60) {
    return { reason: "Rentang pace masih sempit. Butuh lari tempo atau progresif supaya titik tekuk terlihat." };
  }
  const base = pts[0].x;
  const P = pts.map((p) => ({ u: base - p.x, y: p.y, w: p.w }));
  let best: { k: number; L: ReturnType<typeof ols>; R: ReturnType<typeof ols>; sse: number } | null = null;
  for (let k = 2; k <= P.length - 3; k++) {
    const L = ols(P.slice(0, k + 1));
    const R = ols(P.slice(k));
    const sse = L.sse + R.sse;
    if (!best || sse < best.sse) best = { k, L, R, sse };
  }
  if (!best) return { reason: "Belum cukup data untuk mencari titik tekuk." };
  const { k, L, R } = best;
  if (!(L.m > 0 && R.m >= 1.4 * L.m && R.m - L.m >= 0.05)) {
    return { reason: "Belum terlihat titik tekuk (HR naik wajar di semua pace). Butuh lari tempo atau progresif." };
  }
  let u = (L.b - R.b) / (R.m - L.m);
  u = Math.min(P[k + 1].u, Math.max(P[k - 1].u, u));
  return { pace: base - u, hr: Math.round(L.m * u + L.b), bins: pts.length };
}

/** Potong data per detik sesuai langkah menu yang dikirim ke jam. */
export function segmentsOf(s: Streams, steps: PlanStep[]): SegStat[] | null {
  const planned = steps.reduce((a, st) => a + st.seconds, 0);
  const total = s.time[s.time.length - 1];
  if (!(planned > 0) || total < planned * 0.6) return null;
  let at = 0;
  const out: SegStat[] = [];
  const idx = (sec: number) => {
    let lo = 0;
    let hi = s.time.length - 1;
    while (lo < hi) {
      const mid = (lo + hi + 1) >> 1;
      if (s.time[mid] <= sec) lo = mid;
      else hi = mid - 1;
    }
    return lo;
  };
  for (const st of steps) {
    const i0 = idx(at);
    const i1 = idx(Math.min(at + st.seconds, total));
    at += st.seconds;
    const dd = s.dist[i1] - s.dist[i0];
    const dt = s.time[i1] - s.time[i0];
    let hr: number | null = null;
    if (s.hr) {
      let sum = 0;
      let n = 0;
      for (let i = i0; i <= i1; i++) {
        if (s.hr[i] > 30) {
          sum += s.hr[i];
          n++;
        }
      }
      if (n > 10) hr = Math.round(sum / n);
    }
    out.push({
      title: st.title,
      kind: st.kind,
      sec: st.seconds,
      pace: dd > 20 && dt > 0 ? (dt / dd) * 1000 : null,
      hr,
      intensity: st.intensity,
      lo: st.low,
      hi: st.high,
    });
  }
  return out;
}

/** Gabungkan langkah berurutan yang sama jenisnya (mis. pengulangan) jadi satu ringkasan per jenis. */
export function mergeSegs(segs: SegStat[]): SegStat[] {
  const out: SegStat[] = [];
  segs.forEach((g) => {
    const prev = out[out.length - 1];
    if (prev && prev.title === g.title && prev.kind === g.kind) {
      const w1 = prev.sec;
      const w2 = g.sec;
      prev.pace = prev.pace != null && g.pace != null ? (prev.pace * w1 + g.pace * w2) / (w1 + w2) : prev.pace ?? g.pace;
      prev.hr = prev.hr != null && g.hr != null ? Math.round((prev.hr * w1 + g.hr * w2) / (w1 + w2)) : prev.hr ?? g.hr;
      prev.sec += g.sec;
    } else out.push({ ...g });
  });
  return out;
}

/** Penilaian singkat satu segmen. Null kalau tidak ada yang perlu dikatakan. */
export function segVerdict(seg: SegStat, all: SegStat[]): { tone: "g" | "y"; text: string } | null {
  if (seg.pace == null) return null;
  if (seg.intensity === "pace" && seg.lo > 0 && seg.hi > 0) {
    if (seg.pace < seg.lo - 10) return { tone: "y", text: `lebih cepat ${Math.round(seg.lo - seg.pace)} dtk/km dari target` };
    if (seg.pace > seg.hi + 10) return { tone: "y", text: `lebih lambat ${Math.round(seg.pace - seg.hi)} dtk/km dari target` };
    return { tone: "g", text: "sesuai target pace" };
  }
  if (seg.kind === "warmup" || seg.kind === "cooldown") {
    const core = all.find((g) => g.kind === "run" && g.intensity === "pace" && g.lo > 0);
    if (core && seg.pace < core.lo) return { tone: "y", text: `terlalu cepat untuk ${seg.kind === "warmup" ? "pemanasan" : "pendinginan"}` };
    return { tone: "g", text: "terkendali" };
  }
  return null;
}

/** Olah satu aktivitas jadi ringkasan kecil yang aman disimpan di HP. */
export function analyzeActivity(id: string, s: Streams, steps: PlanStep[] | null): ActDetail {
  const bests: Record<string, number> = {};
  BEST_KM.forEach((k) => {
    const t = bestWindow(s.time, s.dist, k * 1000);
    if (t != null) bests[String(k)] = Math.round(t * 10) / 10;
  });
  const detail: ActDetail = { id, bests, bins: lactateBins(s) };
  if (steps && steps.length) {
    const segs = segmentsOf(s, steps);
    if (segs) detail.segs = segs;
  }
  return detail;
}
