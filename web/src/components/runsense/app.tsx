import { useEffect, useMemo, useState, type ReactNode } from "react";
import {
  Bluetooth,
  BadgeCheck,
  Globe,
  House,
  History,
  Moon,
  RefreshCw,
  Settings,
  Sun,
  TrendingUp,
  Watch,
  X,
} from "lucide-react";
import { WatchFace } from "./watch-face";
import { useRun, syncNow, localDay, bytesToB64, b64ToBytes, type WatchMenu } from "@/lib/runsense/store";
import { derive, greeting, f1, band, durText, metsAt, paceLabel, planSeconds, STAGE_TIPS } from "@/lib/runsense/engine";
import { segVerdict } from "@/lib/runsense/streams";
import { encodeCourse, downloadCourse } from "@/lib/runsense/course";
import { bleSupported, pickHuaweiWatch } from "@/lib/runsense/watch-ble";
import { deliverSchedule, openScreen, watchBridgeReady, type NativeScreen } from "@/lib/runsense/watch-push";
import type { Goal, HistoryItem, Plan, Style } from "@/lib/runsense/model";
import { GOALS } from "@/lib/runsense/model";
import { useSession } from "@/lib/runsense/session";

const STYLES: { id: Style; title: string; sub: string }[] = [
  { id: "moderate", title: "Moderat", sub: "Seimbang, disarankan" },
  { id: "conservative", title: "Konservatif", sub: "Lebih banyak recovery" },
  { id: "aggressive", title: "Agresif", sub: "Lebih banyak sesi kualitas" },
];

function useDerived() {
  const goal = useRun((s) => s.goal);
  const style = useRun((s) => s.style);
  const name = useRun((s) => s.name);
  const rest = useRun((s) => s.rest);
  const max = useRun((s) => s.max);
  const age = useRun((s) => s.age);
  const stage = useRun((s) => s.stage);
  const acts = useRun((s) => s.acts);
  const wellness = useRun((s) => s.wellness);
  const details = useRun((s) => s.details);
  const skippedDay = useRun((s) => s.skippedDay);
  return useMemo(
    () =>
      derive({
        acts,
        wellness,
        goal,
        style,
        name,
        rest,
        max,
        age,
        prevStage: stage,
        skipped: skippedDay === localDay(),
        details,
      }),
    [acts, wellness, details, goal, style, name, rest, max, age, stage, skippedDay],
  );
}

function Logo({ className = "size-8" }: { className?: string }) {
  return (
    <svg viewBox="0 0 108 108" className={className} role="img" aria-label="RunSense">
      <rect width="108" height="108" rx="24" fill="#090c0a" />
      <path d="M74.68,36.64 A27,27 0 1 1 33.32,36.64" fill="none" stroke="#00d15c" strokeWidth="7" strokeLinecap="round" />
      <path d="M40,55 L47,55 L51,44 L57,67 L61,51 L68,51" fill="none" stroke="#00d15c" strokeWidth="5.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function Wordmark({ size = "text-2xl" }: { size?: string }) {
  return (
    <span className="flex items-center gap-2.5">
      <Logo />
      <span className={`${size} font-extrabold tracking-tight text-lime`}>RunSense</span>
    </span>
  );
}

export function RunSenseApp() {
  const ready = useRun((s) => s.ready);
  const theme = useRun((s) => s.theme);
  const apiKey = useRun((s) => s.apiKey);
  const setReady = useRun((s) => s.setReady);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    document.querySelector('meta[name="theme-color"]')?.setAttribute("content", theme === "light" ? "#F2F5EF" : "#090C0A");
  }, [theme]);

  useEffect(() => {
    const t = window.setTimeout(() => setReady(true), 500);
    return () => window.clearTimeout(t);
  }, [setReady]);

  useEffect(() => {
    if (!ready) return;
    const key = useRun.getState().apiKey;
    if (key && key !== "demo") void syncNow(true);
  }, [ready]);

  if (!ready) {
    return (
      <main className="grid min-h-dvh place-items-center">
        <Wordmark />
      </main>
    );
  }

  if (!apiKey) return <Setup />;
  return <Coach />;
}

function Setup() {
  const goal = useRun((s) => s.goal);
  const style = useRun((s) => s.style);
  const setGoal = useRun((s) => s.setGoal);
  const setStyle = useRun((s) => s.setStyle);
  const connectKey = useRun((s) => s.connectKey);
  const enterDemo = useRun((s) => s.enterDemo);
  const logout = useRun((s) => s.logout);
  const error = useRun((s) => s.error);
  const setError = useRun((s) => s.setError);
  const busy = useRun((s) => s.busy);
  const [key, setKey] = useState("");

  async function connect() {
    const trimmed = key.trim();
    if (!trimmed) {
      setError("API key wajib diisi");
      return;
    }
    connectKey(trimmed);
    const ok = await syncNow(false);
    if (!ok) {
      const note = useRun.getState().syncNote;
      if (/ditolak|HTTP 401|HTTP 403/.test(note) || /ditolak/.test(useRun.getState().error)) logout();
    }
  }

  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-md flex-col px-6 py-8">
      <Wordmark size="text-3xl" />
      <p className="mt-1 text-base text-muted">Coach lari · menu langsung ke Watch Fit 2</p>
      <button
        type="button"
        onClick={enterDemo}
        className="mt-5 flex h-12 items-center justify-center rounded-full border border-line font-semibold"
      >
        Coba demo + jam
      </button>
      <label className="mt-8 text-xs font-bold tracking-widest text-muted">API KEY INTERVALS.ICU</label>
      <input
        type="password"
        value={key}
        autoComplete="off"
        placeholder="Tempel API key"
        onChange={(e) => setKey(e.target.value)}
        className="mt-2 h-14 rounded-2xl border border-line bg-surface px-4 text-base outline-none focus:border-lime"
      />
      <p className="mt-2 text-sm leading-relaxed text-muted">
        Settings → Developer Settings di Intervals.icu. Key hanya tersimpan di perangkat ini.
      </p>
      <p className="mt-6 text-xs font-bold tracking-widest text-muted">TARGET</p>
      <div className="mt-2 flex flex-wrap gap-2">
        {GOALS.map((g) => (
          <Chip key={g} on={goal === g} onClick={() => setGoal(g)}>
            {g}
          </Chip>
        ))}
      </div>
      <p className="mt-6 text-xs font-bold tracking-widest text-muted">GAYA</p>
      <div className="mt-2 space-y-2">
        {STYLES.map((item) => (
          <button
            key={item.id}
            type="button"
            onClick={() => setStyle(item.id)}
            className={`flex w-full items-center justify-between rounded-2xl border px-4 py-3 text-left ${
              style === item.id ? "border-lime bg-surface" : "border-transparent bg-surface"
            }`}
          >
            <span>
              <span className="block font-semibold">{item.title}</span>
              <span className="text-sm text-muted">{item.sub}</span>
            </span>
            <span className={`size-3 rounded-full ${style === item.id ? "bg-lime" : "bg-line"}`} />
          </button>
        ))}
      </div>
      {error && <p className="mt-4 text-sm text-rose">{error}</p>}
      <button
        type="button"
        disabled={busy}
        onClick={() => void connect()}
        className="mt-6 flex h-12 items-center justify-center rounded-full bg-lime font-bold text-ink disabled:opacity-50"
      >
        {busy ? "Menghubungkan…" : "Hubungkan & mulai"}
      </button>
    </main>
  );
}

function Coach() {
  const screen = useRun((s) => s.screen);
  const menu = useRun((s) => s.menu);
  const link = useRun((s) => s.link);
  const [delivering, setDelivering] = useState(false);
  const [settings, setSettings] = useState(false);
  const [picked, setPicked] = useState<HistoryItem | null>(null);

  return (
    <div className="mx-auto w-full max-w-5xl lg:grid lg:h-dvh lg:grid-cols-2 lg:gap-10 lg:overflow-hidden lg:px-8 lg:py-6">
      <div className="relative mx-auto flex min-h-dvh w-full max-w-md flex-col lg:h-full lg:min-h-0">
        <div className="flex-1 overflow-y-auto px-5 pt-5 pb-24 lg:pb-8">
          {screen === "home" && (
            <Home
              onSettings={() => setSettings(true)}
              onDeliver={() => setDelivering(true)}
              delivered={() => setDelivering(false)}
            />
          )}
          {screen === "watch" && (
            <WatchScreen
              delivering={delivering}
              onDeliver={() => setDelivering(true)}
              delivered={() => setDelivering(false)}
            />
          )}
          {screen === "progress" && <Progress />}
          {screen === "history" && <HistoryScreen onOpen={setPicked} />}
        </div>
        <nav className="safe-nav fixed inset-x-0 bottom-0 z-20 mx-auto flex w-full max-w-md justify-around border-t border-line bg-bg px-2 pt-2 lg:static lg:max-w-none">
          <Nav id="home" label="Beranda" icon={House} />
          <Nav id="watch" label="Jam" icon={Watch} />
          <Nav id="progress" label="Progress" icon={TrendingUp} />
          <Nav id="history" label="Riwayat" icon={History} />
        </nav>
      </div>
      <aside className="sticky top-8 hidden self-start lg:block">
        <p className="mb-3 text-xs font-bold tracking-widest text-muted">HUAWEI WATCH FIT 2</p>
        <WatchFace menu={menu} deviceName={link?.name ?? null} delivering={delivering} />
        <p className="mt-3 text-sm leading-relaxed text-muted">
          {link?.name ? `Terikat ke ${link.name}. ` : ""}
          Jadwal yang dikirim muncul di layar ini. Di aplikasi RunSense, file yang sama masuk ke latihan Watch Fit 2.
        </p>
      </aside>
      {settings && <SettingsSheet onClose={() => setSettings(false)} />}
      {picked && <ActSheet item={picked} onClose={() => setPicked(null)} />}
      <Toasts />
    </div>
  );
}

function Nav({ id, label, icon: Icon }: { id: "home" | "watch" | "progress" | "history"; label: string; icon: typeof House }) {
  const screen = useRun((s) => s.screen);
  const setScreen = useRun((s) => s.setScreen);
  const on = screen === id;
  return (
    <button
      type="button"
      onClick={() => setScreen(id)}
      className={`flex min-h-12 min-w-16 flex-col items-center justify-center gap-0.5 text-xs font-medium ${on ? "text-lime" : "text-muted"}`}
    >
      <Icon className="size-5" strokeWidth={on ? 2.4 : 2} />
      {label}
    </button>
  );
}

function Home({
  onSettings,
  onDeliver,
  delivered,
}: {
  onSettings: () => void;
  onDeliver: () => void;
  delivered: () => void;
}) {
  const view = useDerived();
  const theme = useRun((s) => s.theme);
  const setTheme = useRun((s) => s.setTheme);
  const syncNote = useRun((s) => s.syncNote);
  const busy = useRun((s) => s.busy);
  const goal = useRun((s) => s.goal);
  const a = view.athlete;
  const plan = view.plan;
  const form =
    a.formClass === "baik"
      ? "bg-lime-dim text-lime"
      : a.formClass === "lelah"
        ? "bg-rose-dim text-rose"
        : a.formClass === "kurang"
          ? "bg-surface-2 text-muted"
          : "bg-amber-dim text-amber";

  return (
    <div className="rise">
      <header className="flex items-center justify-between">
        <Wordmark />
        <div className="flex gap-2">
          <IconBtn label="Ganti tema" onClick={() => setTheme(theme === "dark" ? "light" : "dark")}>
            {theme === "dark" ? <Moon className="size-4" /> : <Sun className="size-4" />}
          </IconBtn>
          <IconBtn label="Pengaturan" onClick={onSettings}>
            <Settings className="size-4" />
          </IconBtn>
        </div>
      </header>
      <div className="mt-5 flex items-start justify-between gap-3">
        <div>
          <p className="text-sm text-muted">{greeting()}</p>
          <h1 className="text-2xl font-extrabold tracking-tight">{a.name}</h1>
        </div>
        <span className={`inline-flex items-center gap-2 rounded-full px-3 py-1.5 text-sm font-semibold ${form}`}>
          <i className="size-1.5 rounded-full bg-current" />
          {a.formLabel}
        </span>
      </div>
      <p className="mt-3 flex items-center gap-2 text-sm text-muted">
        <i className={`size-1.5 rounded-full ${busy ? "pulse-dot bg-amber" : "bg-lime"}`} />
        {syncNote || "Belum sinkron"}
      </p>

      <article className="mt-5 rounded-card border border-line bg-surface p-5">
        <p className={`text-xs font-bold tracking-widest ${plan.tone === "bad" ? "text-rose" : plan.tone === "warn" ? "text-amber" : "text-lime"}`}>
          {plan.tag}
        </p>
        <h2 className="mt-2 text-3xl font-extrabold tracking-tight">{plan.title}</h2>
        <p className="mt-1 text-base text-muted">{plan.durationLabel}</p>
        <p className="mt-3 text-sm">
          <span className="text-lime">Detak</span> · {plan.hr}
        </p>
        <p className="text-sm">
          <span className="text-lime">Pace</span> · {plan.pace}
        </p>
        <p className="mt-3 text-sm leading-relaxed text-muted">{plan.reason}</p>
        {plan.blocks.length > 0 && (
          <ul className="mt-4 divide-y divide-line">
            {plan.blocks.map((b, i) => (
              <li key={i} className="py-2 text-sm">
                <p className="font-semibold">
                  {b.repeat > 1 ? `${b.repeat}× ` : ""}
                  {b.steps.map((s) => s.title).join(" + ")}
                </p>
                <p className="text-muted">{b.steps.map((s) => s.detail).join(" · ")}</p>
              </li>
            ))}
          </ul>
        )}
        <div className="mt-4 flex gap-2">
          {plan.status === "train" || plan.status === "rest" ? (
            <button
              type="button"
              onClick={() => void pushMenu(plan, false, onDeliver, delivered)}
              className="flex min-h-12 flex-1 items-center justify-center whitespace-nowrap rounded-full bg-lime px-4 font-bold text-ink"
            >
              Kirim ke jam
            </button>
          ) : null}
          {plan.status === "train" && (
            <button
              type="button"
              onClick={() => useRun.getState().skipToday(localDay())}
              className="flex min-h-12 flex-none items-center justify-center rounded-full border border-line px-6 font-semibold"
            >
              Lewati
            </button>
          )}
          {plan.status === "skip" && (
            <button
              type="button"
              onClick={() => useRun.getState().skipToday("")}
              className="flex h-12 flex-1 items-center justify-center rounded-full border border-line font-semibold"
            >
              Batalkan skip
            </button>
          )}
        </div>
      </article>

      {view.review.length > 0 && (
        <div className="mt-3 space-y-2">
          {view.review.map((n, i) => (
            <p key={i} className={`rounded-2xl px-4 py-3 text-sm leading-relaxed ${n.tone === "g" ? "bg-lime-dim text-lime" : "bg-amber-dim text-amber"}`}>
              {n.text}
            </p>
          ))}
        </div>
      )}

      <article className="mt-3 rounded-card border border-line bg-surface p-5">
        {view.goalBest ? (
          <>
            <p className="text-xs font-bold tracking-widest text-muted">TERBAIK {view.goalBest.km}K</p>
            <p className="mt-1 text-4xl font-extrabold tracking-tight">{view.goalBest.time}</p>
            <p className="text-sm text-muted">
              {view.goalBest.pace} · {view.goalBest.date}
            </p>
            <p className="mt-2 text-sm text-muted">
              Estimasi {goal}: {view.goalEst.current} · {view.goalEst.confidence}
            </p>
          </>
        ) : (
          <>
            <p className="text-xs font-bold tracking-widest text-muted">ESTIMASI {goal}</p>
            <p className="mt-1 text-4xl font-extrabold tracking-tight">{view.goalEst.current}</p>
            <p className="text-sm text-muted">Perkiraan · {view.goalEst.confidence}</p>
          </>
        )}
      </article>

      <div className="mt-3 grid grid-cols-3 gap-2">
        <Stat k="CTL" v={a.ctl != null ? a.ctl.toFixed(0) : "—"} />
        <Stat k="ATL" v={a.atl != null ? a.atl.toFixed(0) : "—"} />
        <Stat k="TSB" v={a.tsb != null ? a.tsb.toFixed(0) : "—"} />
      </div>

      <article className="mt-3 rounded-card border border-line bg-surface p-5">
        <p className="text-xs font-bold tracking-widest text-muted">MINGGU INI</p>
        <div className="mt-3 grid grid-cols-3 gap-2 text-center">
          <Stat k="Jarak" v={`${f1(view.weekKm)} km`} plain />
          <Stat k="Sesi" v={String(view.weekSessions)} plain />
          <Stat k="Load" v={f1(view.weekLoad)} plain />
        </div>
        <div className="mt-4 grid grid-cols-7 gap-1">
          {view.week.map((d) => (
            <div
              key={d.name}
              className={`rounded-xl py-2 text-center ${d.today ? "bg-lime text-ink" : "bg-surface-2"}`}
            >
              <p className={`text-xs ${d.today ? "text-ink" : "text-muted"}`}>{d.name}</p>
              <p className="text-sm font-bold">{d.km > 0 ? f1(d.km) : "·"}</p>
            </div>
          ))}
        </div>
      </article>

      <article className="mt-3 rounded-card border border-line bg-surface p-5">
        <p className="text-xs font-bold tracking-widest text-muted">FONDASI AEROBIK</p>
        <dl className="mt-3 space-y-2 text-sm">
          <Row k="Max METs" v={a.mets ? `${f1(a.mets)} · ${band(a.mets)}` : "—"} />
          <Row k="Tahap" v={a.stageName} />
          <Row k="LTHR" v={a.lthr ? `${a.lthr} bpm` : "—"} />
          <Row k="LT pace" v={a.ltPace || "—"} />
        </dl>
        {a.ltNote && <p className="mt-2 text-xs leading-relaxed text-muted">{a.ltNote}</p>}
        <p className="mt-3 text-sm leading-relaxed text-muted">
          <span className="font-semibold text-lime">T{a.stage}. </span>
          {STAGE_TIPS[a.stage] || "Belum cukup data."}
          {a.estSrc ? ` Sumber: ${a.estSrc}.` : ""}
        </p>
      </article>

      <div className="mt-4 flex justify-center gap-4 text-sm text-muted">
        <button type="button" className="inline-flex items-center gap-1 underline" onClick={() => void syncNow(false)}>
          <RefreshCw className="size-3.5" /> Sinkron
        </button>
        <button type="button" className="underline" onClick={onSettings}>
          Pengaturan
        </button>
        <button type="button" className="underline" onClick={() => useRun.getState().logout()}>
          Keluar
        </button>
      </div>
    </div>
  );
}

function WatchScreen({
  delivering,
  onDeliver,
  delivered,
}: {
  delivering: boolean;
  onDeliver: () => void;
  delivered: () => void;
}) {
  const menu = useRun((s) => s.menu);
  const link = useRun((s) => s.link);
  return (
    <div className="rise">
      <header className="mb-4 flex items-end justify-between">
        <div>
          <p className="text-2xl font-extrabold tracking-tight">Jam</p>
          <p className="text-sm text-muted">{link ? link.name : "Watch Fit 2 belum dipindai"}</p>
        </div>
        <ModeBadge />
      </header>
      <div className="lg:hidden">
        <WatchFace menu={menu} deviceName={link?.name ?? null} delivering={delivering} />
      </div>
      <div className="hidden lg:block">
        <p className="rounded-card border border-line bg-surface p-5 text-sm leading-relaxed text-muted">
          Jam besar ada di samping. Kirim menu dari sini, atau mulai sesi di layar jam.
        </p>
      </div>
      <WatchActions
        delivering={delivering}
        setDelivering={(v) => (v ? onDeliver() : delivered())}
      />
      {menu && (
        <article className="mt-4 rounded-card border border-line bg-surface p-5">
          <p className="text-xs font-bold tracking-widest text-muted">TERKIRIM</p>
          <h2 className="mt-1 text-xl font-bold">{menu.name}</h2>
          <p className="mt-1 text-sm text-muted">
            {new Date(menu.sentAt).toLocaleString("id-ID", { hour: "2-digit", minute: "2-digit", day: "numeric", month: "short" })}
            {" · "}
            {menu.byteLength} B · {menu.fileName}
          </p>
          <p className="mt-2 text-sm leading-relaxed text-muted">{menu.note || menu.reason}</p>
          <button
            type="button"
            className="mt-4 flex h-11 w-full items-center justify-center rounded-full border border-line font-semibold"
            onClick={() => {
              const bytes = b64ToBytes(menu.b64);
              downloadCourse({ id: menu.id, name: menu.name, fileName: menu.fileName, bytes, messageId: 0 });
            }}
          >
            Unduh courseInfo.bin
          </button>
        </article>
      )}
    </div>
  );
}

function ModeBadge() {
  const inApp = watchBridgeReady();
  return inApp ? (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-lime-dim px-3 py-1 text-xs font-bold text-lime">
      <BadgeCheck className="size-3.5" />
      Aplikasi
    </span>
  ) : (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-dim px-3 py-1 text-xs font-bold text-amber">
      <Globe className="size-3.5" />
      Browser
    </span>
  );
}

async function openNative(route: NativeScreen) {
  const store = useRun.getState();
  try {
    await openScreen(route);
  } catch (err) {
    store.setError(err instanceof Error ? err.message : "Layar tidak terbuka.");
  }
}

function WatchActions({ delivering, setDelivering }: { delivering: boolean; setDelivering: (v: boolean) => void }) {
  const view = useDerived();
  const link = useRun((s) => s.link);
  const plan = view.plan;
  const canSend = plan.blocks.length > 0 && (plan.status === "train" || plan.status === "rest");
  const inApp = watchBridgeReady();
  const outline =
    "flex h-12 w-full items-center justify-center gap-2 rounded-full border border-line font-semibold disabled:opacity-40";
  return (
    <div className="mt-4 space-y-2">
      <button
        type="button"
        disabled={!canSend || delivering}
        onClick={() => void pushMenu(plan, false, () => setDelivering(true), () => setDelivering(false))}
        className="flex h-12 w-full items-center justify-center rounded-full bg-lime font-bold text-ink disabled:opacity-40"
      >
        {delivering ? "Mengirim ke jam…" : canSend ? "Kirim ke jam" : "Tidak ada jadwal untuk dikirim"}
      </button>
      {inApp ? (
        <button type="button" onClick={() => void openNative("/settings/koneksi")} className={outline}>
          <Bluetooth className="size-4" />
          Hubungkan jam (pindai &amp; ikat)
        </button>
      ) : bleSupported() ? (
        <button
          type="button"
          onClick={() => void pushMenu(plan, true, () => setDelivering(true), () => setDelivering(false))}
          disabled={!canSend}
          className={outline}
        >
          <Bluetooth className="size-4" />
          Pindai Watch Fit 2, lalu kirim
        </button>
      ) : (
        <button type="button" disabled className={outline}>
          <Bluetooth className="size-4" />
          Bluetooth hanya lewat aplikasi RunSense
        </button>
      )}
      <button
        type="button"
        onClick={() => (inApp ? void openNative("/settings") : window.location.assign("pengaturan.html"))}
        className={outline}
      >
        <Settings className="size-4" />
        Pengaturan jam
      </button>
      <p className="text-sm leading-relaxed text-muted">
        {link
          ? `${link.name}${link.gatt ? " menjawab layanan Huawei" : " terlihat"}. `
          : inApp
            ? "Belum tersambung? Pakai Hubungkan jam, lalu kembali ke sini dan kirim jadwal. "
            : "Tombol hijau menyiapkan jadwal hari ini. "}
        Di aplikasi RunSense, jadwal masuk sebagai program lari di Watch Fit 2. Di browser, jadwal tampil di jam layar karena saluran jam terenkripsi.
      </p>
    </div>
  );
}

async function pushMenu(plan: Plan, scan: boolean, onDeliver: () => void, delivered: () => void) {
  if (!plan.blocks.length) return;
  const store = useRun.getState();
  onDeliver();
  try {
    let deviceName = store.link?.name ?? null;
    if (scan) {
      const link = await pickHuaweiWatch();
      store.setLink(link);
      deviceName = link.name;
    }
    const file = encodeCourse(plan, `Hari ini ${plan.title}`);
    const sent = await deliverSchedule(plan);
    const menu: WatchMenu = {
      id: file.id,
      name: plan.title,
      sentAt: Date.now(),
      deviceName,
      fileName: file.fileName,
      byteLength: file.bytes.length,
      b64: bytesToB64(file.bytes),
      pace: plan.pace,
      hr: plan.hr,
      reason: plan.reason,
      totalSec: planSeconds(plan.blocks),
      blocks: plan.blocks,
      via: sent.via,
      note: sent.note,
    };
    store.setMenu(menu);
    store.setScreen("watch");
    useSession.getState().reset(menu.id);
    if (!sent.ok) store.setError(sent.note);
    window.setTimeout(delivered, sent.via === "watch" ? 400 : 700);
  } catch (err) {
    delivered();
    store.setError(err instanceof Error ? err.message : "Gagal mengirim");
  }
}

function Progress() {
  const view = useDerived();
  const goal = useRun((s) => s.goal);
  const a = view.athlete;
  const refs = [
    ["Jalan 10:40", 4.8],
    ["Run-walk", 6],
    ["Pace 8:20", 7.6],
    ["Pace 7:20", 8.6],
    ["Pace 6:00", 9.3],
  ] as const;
  return (
    <div className="rise space-y-3">
      <h1 className="text-2xl font-extrabold tracking-tight">Progress</h1>
      <article className="rounded-card border border-line bg-surface p-5">
        <p className="text-xs font-bold tracking-widest text-muted">REKOR TERBAIK</p>
        {a.bests.some((b) => b.time) ? (
          <ul className="mt-3 space-y-2 text-sm">
            {a.bests.map((b) => (
              <li key={b.km} className="flex justify-between gap-3">
                <span className="text-muted">{b.km}K</span>
                <span className={b.time ? "font-semibold" : "text-muted"}>
                  {b.time ? `${b.time} · ${b.pace} · ${b.date}` : "—"}
                </span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-3 text-sm text-muted">Belum ada data per detik. Rekor muncul setelah lari tersinkron dari Intervals.icu.</p>
        )}
      </article>
      <article className="rounded-card border border-line bg-surface p-5">
        <p className="text-xs font-bold tracking-widest text-muted">ESTIMASI {goal}</p>
        <p className="mt-1 text-4xl font-extrabold tracking-tight">{view.goalEst.current}</p>
        <p className="mt-1 text-sm text-lime">{view.goalEst.basis}</p>
        <p className="text-sm text-muted">{view.goalEst.confidence}</p>
      </article>
      <article className="rounded-card border border-line bg-surface p-5">
        <p className="text-xs font-bold tracking-widest text-muted">KAPASITAS</p>
        <dl className="mt-3 space-y-2 text-sm">
          <Row k="LTHR" v={a.lthr ? `${a.lthr} bpm` : "—"} />
          <Row k="LT pace" v={a.ltPace || "—"} />
          <Row k="Max METs" v={a.mets ? `${f1(a.mets)} · ${band(a.mets)}` : "—"} />
          <Row k="Tahap" v={a.stageName} />
          <Row k="CTL" v={a.ctl != null ? a.ctl.toFixed(1) : "—"} />
          <Row k="TSB" v={a.tsb != null ? a.tsb.toFixed(1) : "—"} />
        </dl>
        {a.ltNote && <p className="mt-3 text-xs leading-relaxed text-muted">{a.ltNote}</p>}
      </article>
      <article className="rounded-card border border-line bg-surface p-5">
        <p className="text-xs font-bold tracking-widest text-muted">REFERENSI</p>
        {a.mets ? (
          <ul className="mt-3 space-y-2 text-sm">
            {refs.map(([name, m]) => {
              const p = m / (a.mets || 1);
              const col = p <= 0.68 ? "text-lime" : p <= 0.85 ? "text-amber" : "text-rose";
              return (
                <li key={name} className="flex justify-between gap-3">
                  <span className="text-muted">{name}</span>
                  <span className={col}>
                    {Math.round(p * 100)}% · {durText(p)}
                  </span>
                </li>
              );
            })}
          </ul>
        ) : (
          <p className="mt-3 text-sm text-muted">Butuh beberapa lari dulu untuk membaca kapasitas.</p>
        )}
      </article>
    </div>
  );
}

function HistoryScreen({ onOpen }: { onOpen: (item: HistoryItem) => void }) {
  const view = useDerived();
  return (
    <div className="rise">
      <h1 className="mb-4 text-2xl font-extrabold tracking-tight">Riwayat</h1>
      {view.history.length === 0 ? (
        <p className="mt-10 text-center text-sm text-muted">Belum ada aktivitas.</p>
      ) : (
        <ul className="space-y-2">
          {view.history.map((item) => (
            <li key={item.t}>
              <button
                type="button"
                onClick={() => onOpen(item)}
                className="flex w-full items-center justify-between gap-3 rounded-2xl border border-line bg-surface px-4 py-3 text-left"
              >
                <span>
                  <span className="text-xs text-muted">{item.date}</span>
                  <span className="mt-0.5 block font-semibold">
                    <span className="mr-2 text-lime">{item.type}</span>
                    {item.title}
                  </span>
                  <span className="text-sm text-muted">{item.meta}</span>
                </span>
                <span className="rounded-xl bg-lime-dim px-2 py-1 text-sm font-bold text-lime">TL {item.load}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function ActSheet({ item, onClose }: { item: HistoryItem; onClose: () => void }) {
  const view = useDerived();
  const pct = view.athlete.mets && item.km > 0 ? Math.round((metsAt(item.min / item.km) / view.athlete.mets) * 100) : null;
  return (
    <div className="scrim fixed inset-0 z-40 flex items-end justify-center" onClick={onClose}>
      <div
        className="max-h-[85dvh] w-full max-w-md overflow-y-auto rounded-t-3xl bg-surface p-5 pb-8"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-3 flex items-start justify-between">
          <div>
            <h2 className="text-xl font-extrabold">
              {item.title} · {f1(item.km)} km
            </h2>
            <p className="text-sm text-muted">
              {item.date} · {item.pace}
              {item.hr ? ` · HR ${Math.round(item.hr)}` : ""}
            </p>
          </div>
          <button type="button" aria-label="Tutup" onClick={onClose} className="grid size-10 place-items-center rounded-full bg-surface-2">
            <X className="size-4" />
          </button>
        </div>
        <p className="text-sm text-muted">{item.why}</p>
        {pct != null && (
          <p className="mt-3 rounded-2xl bg-surface-2 px-3 py-3 text-sm">
            Intensitas ≈ <span className="font-semibold text-lime">{pct}%</span> kapasitas · tahan {durText(pct / 100)}
          </p>
        )}
        {item.segs && item.segs.length > 0 && (
          <div className="mt-4 space-y-2">
            <p className="text-xs font-bold tracking-widest text-muted">PER SEGMEN</p>
            {item.segs.map((g, i) => {
              const v = segVerdict(g, item.segs || []);
              return (
                <div key={i} className="rounded-2xl bg-surface-2 px-3 py-3 text-sm">
                  <div className="flex justify-between gap-3">
                    <span className="font-semibold">{g.title}</span>
                    <span>
                      {g.pace != null ? `${paceLabel(g.pace / 60)} /km` : "—"}
                      {g.hr ? ` · HR ${g.hr}` : ""}
                    </span>
                  </div>
                  {v && <p className={`mt-1 ${v.tone === "g" ? "text-lime" : "text-amber"}`}>{v.text}</p>}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

function SettingsSheet({ onClose }: { onClose: () => void }) {
  const goal = useRun((s) => s.goal);
  const style = useRun((s) => s.style);
  const theme = useRun((s) => s.theme);
  const setGoal = useRun((s) => s.setGoal);
  const setStyle = useRun((s) => s.setStyle);
  const setTheme = useRun((s) => s.setTheme);
  return (
    <div className="scrim fixed inset-0 z-40 flex items-end justify-center lg:items-center" onClick={onClose}>
      <div className="max-h-[85dvh] w-full max-w-md overflow-y-auto rounded-t-3xl bg-surface p-5 pb-8 lg:rounded-3xl" onClick={(e) => e.stopPropagation()}>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-xl font-extrabold">Pengaturan</h2>
          <button type="button" aria-label="Tutup" onClick={onClose} className="grid size-10 place-items-center rounded-full bg-surface-2">
            <X className="size-4" />
          </button>
        </div>
        <p className="text-xs font-bold tracking-widest text-muted">TARGET</p>
        <div className="mt-2 flex flex-wrap gap-2">
          {GOALS.map((g) => (
            <Chip key={g} on={goal === g} onClick={() => setGoal(g as Goal)}>
              {g}
            </Chip>
          ))}
        </div>
        <p className="mt-5 text-xs font-bold tracking-widest text-muted">GAYA</p>
        <div className="mt-2 space-y-2">
          {STYLES.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => setStyle(item.id)}
              className={`flex h-14 w-full items-center justify-between rounded-2xl border px-4 ${style === item.id ? "border-lime" : "border-line"}`}
            >
              <span className="font-semibold">{item.title}</span>
              <span className="text-sm text-muted">{item.sub}</span>
            </button>
          ))}
        </div>
        <p className="mt-5 text-xs font-bold tracking-widest text-muted">TEMA</p>
        <div className="mt-2 flex gap-2">
          <Chip on={theme === "dark"} onClick={() => setTheme("dark")}>
            Gelap
          </Chip>
          <Chip on={theme === "light"} onClick={() => setTheme("light")}>
            Terang
          </Chip>
        </div>
        <button
          type="button"
          onClick={() => {
            useRun.getState().logout();
            onClose();
          }}
          className="mt-6 flex h-12 w-full items-center justify-center rounded-full border border-line font-semibold"
        >
          Ganti API key
        </button>
        <button type="button" onClick={onClose} className="mt-2 flex h-12 w-full items-center justify-center rounded-full bg-lime font-bold text-ink">
          Selesai
        </button>
      </div>
    </div>
  );
}

function Toasts() {
  const error = useRun((s) => s.error);
  const setError = useRun((s) => s.setError);
  useEffect(() => {
    if (!error) return;
    const t = window.setTimeout(() => setError(""), 4200);
    return () => window.clearTimeout(t);
  }, [error, setError]);
  if (!error) return null;
  return (
    <p className="fixed bottom-24 left-1/2 z-50 w-11/12 max-w-sm -translate-x-1/2 rounded-2xl bg-rose px-4 py-3 text-center text-sm font-medium text-oled-fg">
      {error}
    </p>
  );
}

function Chip({ on, onClick, children }: { on: boolean; onClick: () => void; children: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`h-10 rounded-full px-4 text-sm font-semibold ${on ? "bg-lime text-ink" : "bg-surface-2 text-fg"}`}
    >
      {children}
    </button>
  );
}

function IconBtn({ label, onClick, children }: { label: string; onClick: () => void; children: ReactNode }) {
  return (
    <button type="button" aria-label={label} title={label} onClick={onClick} className="grid size-10 place-items-center rounded-full border border-line bg-surface">
      {children}
    </button>
  );
}

function Stat({ k, v, plain }: { k: string; v: string; plain?: boolean }) {
  return (
    <div className={plain ? "" : "rounded-2xl border border-line bg-surface py-3 text-center"}>
      <p className="text-xs font-semibold text-muted">{k}</p>
      <p className="mt-0.5 text-lg font-bold">{v}</p>
    </div>
  );
}

function Row({ k, v }: { k: string; v: string }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-muted">{k}</dt>
      <dd className="font-semibold">{v}</dd>
    </div>
  );
}
