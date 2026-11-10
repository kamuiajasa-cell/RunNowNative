import type { Plan, PlanStep } from "./model";

/**
 * Today's schedule as Huawei Health's running course.
 * Same shape RunSense accepts on `pushRunningCourse` (RunNowBridge COURSE rule /
 * CourseProtobuf): time targets, pace in seconds per km, heart rate as % max.
 */
export type CourseStepWire = {
  kind: PlanStep["kind"];
  target: "time";
  targetValue: number;
  intensity: "none" | "pace" | "hrPercent";
  low: number;
  high: number;
};

export type CourseWire = {
  name: string;
  blocks: { repeat: number; steps: CourseStepWire[] }[];
};

export type Delivery = {
  via: "watch" | "screen";
  ok: boolean;
  note: string;
};

type Bridge = { postMessage: (body: string) => void };

type Pending = {
  res: (value: unknown) => void;
  rej: (error: Error) => void;
  timer: number;
};

function bridge(): Bridge | null {
  if (typeof window === "undefined") return null;
  const slot = (window as Window & { ReactNativeWebView?: Bridge }).ReactNativeWebView;
  return slot && typeof slot.postMessage === "function" ? slot : null;
}

export function watchBridgeReady() {
  return bridge() != null;
}

let seq = 0;

function rpc<T>(method: string, args: unknown[], ms: number, timeoutMsg = "Watch Fit 2 tidak menjawab. Pastikan jam tersambung di RunSense."): Promise<T> {
  const link = bridge();
  if (!link) return Promise.reject(new Error("Jembatan jam tidak ada."));
  const w = window as Window & {
    __rn?: (id: number, ok: boolean, value: unknown) => void;
    __rsPending?: Record<number, Pending>;
  };
  return new Promise<T>((resolve, reject) => {
    const id = (seq += 1);
    w.__rsPending = w.__rsPending || {};
    const timer = window.setTimeout(() => {
      delete w.__rsPending?.[id];
      reject(new Error(timeoutMsg));
    }, ms);
    w.__rsPending[id] = {
      timer,
      res: (value) => resolve(value as T),
      rej: reject,
    };
    if (!w.__rn) {
      w.__rn = (rid, ok, value) => {
        const pending = w.__rsPending?.[rid];
        if (!pending) return;
        delete w.__rsPending![rid];
        window.clearTimeout(pending.timer);
        if (ok) pending.res(value);
        else pending.rej(new Error(typeof value === "string" && value ? value : "Jam menolak jadwal."));
      };
    }
    link.postMessage(JSON.stringify({ type: "rpc", id, method, args }));
  });
}

function clamp(n: number, lo: number, hi: number) {
  return Math.min(hi, Math.max(lo, Math.round(n)));
}

function wireStep(step: PlanStep): CourseStepWire {
  const targetValue = clamp(step.seconds || 1, 1, 86_400);
  if (step.intensity === "pace") {
    let low = clamp(step.low, 60, 900);
    let high = clamp(step.high, 60, 900);
    if (low > high) [low, high] = [high, low];
    return { kind: step.kind, target: "time", targetValue, intensity: "pace", low, high };
  }
  if (step.intensity === "hrPercent") {
    let low = clamp(step.low, 20, 100);
    let high = clamp(step.high, 20, 100);
    if (low > high) [low, high] = [high, low];
    return { kind: step.kind, target: "time", targetValue, intensity: "hrPercent", low, high };
  }
  return { kind: step.kind, target: "time", targetValue, intensity: "none", low: 0, high: 0 };
}

/** Payload for `pushRunningCourse`. Name stays inside the 64-byte watch limit. */
export function planToCourse(plan: Plan): CourseWire {
  const raw = `Hari ini · ${plan.title}`.trim();
  let name = raw;
  while (new TextEncoder().encode(name).length > 60 && name.length > 8) name = name.slice(0, -1);
  const blocks = plan.blocks
    .map((block) => ({
      repeat: clamp(block.repeat || 1, 1, 99),
      steps: block.steps.map(wireStep),
    }))
    .filter((block) => block.steps.length > 0)
    .slice(0, 40);
  return { name: name || "Jadwal hari ini", blocks };
}

/**
 * Send today's plan to a paired Huawei Watch Fit 2.
 * Inside the RunSense app this is the real P2P course (`in.huawei.fitness`).
 * A normal browser cannot open that encrypted channel, so the schedule stays
 * on the on-screen watch until the page runs in RunSense.
 */
export async function deliverSchedule(plan: Plan): Promise<Delivery> {
  const course = planToCourse(plan);
  if (!course.blocks.length) {
    return { via: "screen", ok: false, note: "Jadwal hari ini tidak punya langkah." };
  }
  if (!watchBridgeReady()) {
    return {
      via: "screen",
      ok: true,
      note: "Jadwal hari ini tampil di jam layar. Dari aplikasi RunSense (jam sudah tersambung), tombol yang sama mengirim program ini ke Watch Fit 2.",
    };
  }
  try {
    await rpc("setCoursesEnabled", [true], 12_000);
    const res = await rpc<Record<string, unknown>>("pushRunningCourse", [course], 50_000);
    const file = typeof res?.fileName === "string" ? res.fileName : "courseInfo.bin";
    const answered = res?.answered === true;
    return {
      via: "watch",
      ok: true,
      note: answered
        ? `Watch Fit 2 menerima ${file}. Buka latihan di jam.`
        : `${file} terkirim ke jam. Cek menu latihan di Watch Fit 2.`,
    };
  } catch (err) {
    const message = err instanceof Error ? err.message : "Jam menolak jadwal.";
    return { via: "screen", ok: false, note: message };
  }
}

export type NativeScreen = "/settings/koneksi" | "/settings";

/**
 * Opens a native RunSense screen: "/settings/koneksi" is the watch scan/pair/connect
 * screen, "/settings" is pengaturan.html (all watch settings). Only works inside the app.
 */
export async function openScreen(route: NativeScreen): Promise<void> {
  await rpc<null>("openScreen", [route], 10_000, "Layar tidak terbuka. Coba lagi.");
}
