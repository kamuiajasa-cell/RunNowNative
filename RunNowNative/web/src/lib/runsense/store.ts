import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { Act, Goal, Style, Wellness } from "./model";
import { derive, rowFromIcu } from "./engine";
import type { WatchLink } from "./watch-ble";

export type WatchMenu = {
  id: string;
  name: string;
  sentAt: number;
  deviceName: string | null;
  fileName: string;
  byteLength: number;
  b64: string;
  pace: string;
  hr: string;
  reason: string;
  totalSec: number;
  blocks: import("./model").PlanBlock[];
  via?: "watch" | "screen";
  note?: string;
};

type State = {
  ready: boolean;
  goal: Goal;
  style: Style;
  theme: "dark" | "light";
  apiKey: string | null;
  name: string;
  rest: number;
  max: number;
  age: number;
  stage: number;
  acts: Act[];
  wellness: Wellness[];
  icuLast: number;
  syncNote: string;
  busy: boolean;
  error: string;
  skippedDay: string;
  link: WatchLink | null;
  menu: WatchMenu | null;
  screen: "home" | "watch" | "progress" | "history";
  authed: boolean;
  setReady: (v: boolean) => void;
  setScreen: (s: State["screen"]) => void;
  setGoal: (g: Goal) => void;
  setStyle: (s: Style) => void;
  setTheme: (t: "dark" | "light") => void;
  setError: (m: string) => void;
  setLink: (l: WatchLink | null) => void;
  setMenu: (m: WatchMenu | null) => void;
  skipToday: (day: string) => void;
  connectKey: (key: string) => void;
  applySync: (patch: Partial<Pick<State, "name" | "rest" | "max" | "age" | "stage" | "acts" | "wellness" | "icuLast" | "syncNote">>) => void;
  setBusy: (v: boolean) => void;
  enterDemo: () => void;
  logout: () => void;
};

const empty = {
  apiKey: null as string | null,
  name: "",
  rest: 0,
  max: 0,
  age: 0,
  stage: 0,
  acts: [] as Act[],
  wellness: [] as Wellness[],
  icuLast: 0,
  syncNote: "",
  skippedDay: "",
  link: null as WatchLink | null,
  menu: null as WatchMenu | null,
};

export const useRun = create<State>()(
  persist(
    (set) => ({
      ready: false,
      goal: "5K",
      style: "moderate",
      theme: "dark",
      busy: false,
      error: "",
      screen: "home",
      authed: false,
      ...empty,
      setReady: (ready) => set({ ready }),
      setScreen: (screen) => set({ screen }),
      setGoal: (goal) => set({ goal }),
      setStyle: (style) => set({ style }),
      setTheme: (theme) => set({ theme }),
      setError: (error) => set({ error }),
      setLink: (link) => set({ link }),
      setMenu: (menu) => set({ menu }),
      skipToday: (skippedDay) => set({ skippedDay }),
      connectKey: (apiKey) => set({ apiKey, authed: true }),
      applySync: (patch) => set(patch),
      setBusy: (busy) => set({ busy }),
      enterDemo: () => {
        const now = Date.now();
        const day = 864e5;
        set({
          apiKey: "demo",
          authed: true,
          name: "Demo Runner",
          rest: 52,
          max: 185,
          age: 32,
          stage: 3,
          icuLast: now,
          syncNote: "Demo · data contoh, bukan akunmu",
          acts: [
            { t: now - 1.1 * day, kind: "run", min: 42, km: 6.6, hr: 138, id: "d1", name: "Easy" },
            { t: now - 3.2 * day, kind: "run", min: 36, km: 5.8, hr: 141, id: "d2", name: "Easy" },
            { t: now - 5.2 * day, kind: "run", min: 55, km: 8.4, hr: 144, id: "d3", name: "Long" },
            { t: now - 8 * day, kind: "run", min: 40, km: 6.3, hr: 136, id: "d4", name: "Easy" },
            { t: now - 10 * day, kind: "run", min: 34, km: 5.4, hr: 150, id: "d5", name: "Steady" },
            { t: now - 12 * day, kind: "run", min: 48, km: 7.5, hr: 140, id: "d6", name: "Easy" },
            { t: now - 15 * day, kind: "run", min: 38, km: 6.0, hr: 139, id: "d7", name: "Easy" },
            { t: now - 18 * day, kind: "run", min: 44, km: 6.9, hr: 142, id: "d8", name: "Easy" },
          ],
          wellness: [{ id: "demo", ctl: 38, atl: 36, tsb: 2, restingHR: 52 }],
          screen: "home",
        });
      },
      logout: () =>
        set({
          ...empty,
          authed: false,
          screen: "home",
          busy: false,
          error: "",
        }),
    }),
    {
      name: "runsense-fit2",
      partialize: (s) =>
        s.apiKey === "demo"
          ? { goal: s.goal, style: s.style, theme: s.theme }
          : {
              goal: s.goal,
              style: s.style,
              theme: s.theme,
              apiKey: s.apiKey,
              name: s.name,
              rest: s.rest,
              max: s.max,
              age: s.age,
              stage: s.stage,
              acts: s.acts,
              wellness: s.wellness,
              icuLast: s.icuLast,
              syncNote: s.syncNote,
              skippedDay: s.skippedDay,
              link: s.link,
              menu: s.menu,
              authed: s.authed,
            },
      onRehydrateStorage: () => (state) => {
        state?.setReady(true);
      },
    },
  ),
);

export function localDay(ms = Date.now()) {
  const d = new Date(ms);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

export function viewOf() {
  const s = useRun.getState();
  const skipped = s.skippedDay === localDay();
  return derive({
    acts: s.acts,
    wellness: s.wellness,
    goal: s.goal,
    style: s.style,
    name: s.name,
    rest: s.rest,
    max: s.max,
    age: s.age,
    prevStage: s.stage,
    skipped,
  });
}

function authHeader(key: string) {
  return "Basic " + btoa(`API_KEY:${key}`);
}

export async function syncNow(silent = false) {
  const s = useRun.getState();
  if (!s.apiKey || s.apiKey === "demo") return false;
  if (s.busy) return false;
  s.setBusy(true);
  s.applySync({ syncNote: "Menyinkronkan Intervals.icu…" });
  try {
    const headers = { Authorization: authHeader(s.apiKey) };
    try {
      const p = await fetch("/api/icu/athlete/0", { headers }).then((r) => {
        if (r.status === 401 || r.status === 403) throw new Error("API key ditolak");
        if (!r.ok) throw new Error("HTTP " + r.status);
        return r.json();
      });
      const a = p?.athlete || p;
      const patch: Partial<State> = {};
      if (a?.name || a?.firstname) {
        patch.name = String(a.name || [a.firstname, a.lastname].filter(Boolean).join(" ")).slice(0, 40);
      }
      if (!(s.max > 0) && a?.max_hr > 0) patch.max = a.max_hr;
      if (!(s.rest > 0) && a?.resting_hr > 0) patch.rest = a.resting_hr;
      if (!(s.age > 0) && a?.birthday) {
        const by = new Date(a.birthday);
        if (Number.isFinite(+by)) patch.age = Math.max(12, Math.min(90, new Date().getFullYear() - by.getFullYear()));
      }
      if (Object.keys(patch).length) s.applySync(patch);
    } catch (err) {
      if (err instanceof Error && err.message === "API key ditolak") throw err;
    }

    const now = Date.now();
    const fmt = (ms: number) => new Date(ms).toISOString().slice(0, 10);
    const old = fmt(now - (s.acts.length ? 120 : 400) * 864e5);
    const neu = fmt(now + 864e5);
    const [actsRes, wellRes] = await Promise.all([
      fetch(`/api/icu/athlete/0/activities?oldest=${old}&newest=${neu}`, { headers }),
      fetch(`/api/icu/athlete/0/wellness?oldest=${old}&newest=${neu}`, { headers }).catch(() => null),
    ]);
    if (actsRes.status === 401 || actsRes.status === 403) throw new Error("API key ditolak");
    if (!actsRes.ok) throw new Error("HTTP " + actsRes.status);
    const actsJson = (await actsRes.json()) as Record<string, unknown>[];
    const fresh = (Array.isArray(actsJson) ? actsJson : []).map(rowFromIcu).filter((x): x is Act => !!x);
    const map = new Map(s.acts.map((a) => [a.id, a]));
    fresh.forEach((a) => map.set(a.id, a));
    const acts = [...map.values()].sort((a, b) => a.t - b.t).slice(-500);

    let wellness = s.wellness;
    if (wellRes && wellRes.ok) {
      const well = (await wellRes.json()) as Record<string, unknown>[];
      const wmap = new Map(wellness.map((w) => [w.id, w]));
      if (Array.isArray(well)) {
        well.forEach((w) => {
          const id = String(w.id || w.date || "").slice(0, 10);
          if (!/^\d{4}-\d{2}-\d{2}$/.test(id)) return;
          wmap.set(id, {
            id,
            restingHR: Number(w.restingHR || w.resting_hr || 0) || null,
            ctl: w.ctl != null ? Number(w.ctl) : null,
            atl: w.atl != null ? Number(w.atl) : null,
            tsb: w.tsb != null ? Number(w.tsb) : null,
          });
        });
      }
      wellness = [...wmap.values()].sort((a, b) => a.id.localeCompare(b.id)).slice(-400);
    }

    const cur = useRun.getState();
    let rest = cur.rest;
    if (!(rest > 0)) {
      const r = [...wellness].reverse().find((w) => (w.restingHR ?? 0) > 30);
      if (r?.restingHR) rest = r.restingHR;
    }
    const note = `Sinkron · ${cur.name || "OK"} · ${new Date().toLocaleTimeString("id-ID", { hour: "2-digit", minute: "2-digit" })}`;
    const derived = derive({
      acts,
      wellness,
      goal: cur.goal,
      style: cur.style,
      name: cur.name,
      rest,
      max: cur.max,
      age: cur.age,
      prevStage: cur.stage,
      skipped: cur.skippedDay === localDay(),
    });
    useRun.getState().applySync({
      acts,
      wellness,
      rest,
      icuLast: Date.now(),
      syncNote: note,
      stage: derived.nextStage,
    });
    return true;
  } catch (err) {
    const msg = err instanceof Error ? err.message : "Gagal sinkron";
    useRun.getState().applySync({ syncNote: /fetch|network/i.test(msg) ? "Offline · data lokal" : msg });
    if (!silent) useRun.getState().setError(msg);
    return false;
  } finally {
    useRun.getState().setBusy(false);
  }
}

export function bytesToB64(bytes: Uint8Array) {
  let bin = "";
  bytes.forEach((b) => {
    bin += String.fromCharCode(b);
  });
  return btoa(bin);
}

export function b64ToBytes(b64: string) {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}
