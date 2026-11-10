import { useEffect, useState } from "react";
import { Pause, Play, RotateCcw } from "lucide-react";
import type { WatchMenu } from "@/lib/runsense/store";
import { flattenBlocks, clock } from "@/lib/runsense/engine";
import { useSession } from "@/lib/runsense/session";

const KIND: Record<string, string> = {
  warmup: "Panas",
  run: "Lari",
  walk: "Jalan",
  rest: "Jeda",
  cooldown: "Dingin",
};

export function WatchFace({
  menu,
  deviceName,
  delivering,
}: {
  menu: WatchMenu | null;
  deviceName: string | null;
  delivering?: boolean;
}) {
  const session = useSession();
  const [, tick] = useState(0);
  useEffect(() => {
    if (!session.playing) return;
    const id = window.setInterval(() => tick((n) => n + 1), 250);
    return () => window.clearInterval(id);
  }, [session.playing]);

  const steps = menu ? flattenBlocks(menu.blocks) : [];
  const elapsed = menu && session.menuId === menu.id ? session.elapsed() : 0;
  let acc = 0;
  let index = 0;
  for (let i = 0; i < steps.length; i++) {
    if (elapsed < acc + steps[i].seconds) {
      index = i;
      break;
    }
    acc += steps[i].seconds;
    index = Math.min(i, steps.length - 1);
  }
  const step = steps[index];
  const into = Math.max(0, elapsed - acc);
  const left = step ? Math.max(0, step.seconds - into) : 0;
  const done = menu ? elapsed >= menu.totalSec && menu.totalSec > 0 : false;
  const progress = menu && menu.totalSec > 0 ? Math.min(1, elapsed / menu.totalSec) : 0;

  return (
    <div className="wf-bezel relative">
      <span className="wf-side" aria-hidden />
      <div className="wf-screen flex flex-col px-4 pt-4 pb-3">
        <div className="flex items-center justify-between text-xs tracking-wide text-lime">
          <span>FIT 2</span>
          <span>{deviceName ? "LINK" : "MENU"}</span>
        </div>
        {!menu ? (
          <div className="flex flex-1 flex-col items-start justify-center gap-2 pr-2">
            <p className="text-lg font-semibold leading-tight text-oled-fg">Belum ada menu</p>
            <p className="text-sm leading-relaxed text-oled-muted">
              Kirim latihan hari ini. Langkahnya muncul di sini seperti di Watch Fit 2.
            </p>
          </div>
        ) : done ? (
          <div className="flex flex-1 flex-col justify-center">
            <p className="text-xs uppercase tracking-widest text-lime">Selesai</p>
            <p className="mt-2 text-2xl font-semibold">{menu.name}</p>
            <p className="mt-2 text-sm text-oled-muted">{clock(menu.totalSec)} di jam</p>
          </div>
        ) : session.playing || elapsed > 0 ? (
          <div className="mt-6 flex flex-1 flex-col">
            <p className="text-xs uppercase tracking-widest text-oled-muted">
              {index + 1}/{steps.length} · {KIND[step?.kind] || "Langkah"}
            </p>
            <p className="mt-1 text-xl font-semibold leading-tight">{step?.title}</p>
            <p className="mt-4 font-semibold text-5xl tracking-tight text-lime">{clock(left)}</p>
            <p className="mt-2 text-sm text-oled-soft">{step?.detail}</p>
            <div className="mt-auto pt-4">
              <div className="h-1.5 overflow-hidden rounded-full bg-oled-chip">
                <div className="h-full rounded-full bg-lime" style={{ width: `${progress * 100}%` }} />
              </div>
              <p className="mt-2 text-xs text-oled-muted">
                Berikutnya · {steps[index + 1]?.title || "Finish"}
              </p>
            </div>
          </div>
        ) : (
          <div className={`mt-3 flex flex-1 flex-col ${delivering ? "rise" : ""}`}>
            <p className="text-xs uppercase tracking-widest text-lime">
              {menu.via === "watch" ? "Di jam" : "Jadwal hari ini"}
            </p>
            <p className="mt-1 text-2xl font-semibold leading-tight">{menu.name}</p>
            <p className="mt-1 text-sm text-oled-soft">
              {clock(menu.totalSec)} · {menu.pace}
            </p>
            {menu.note && <p className="mt-2 line-clamp-3 text-xs leading-relaxed text-oled-muted">{menu.note}</p>}
            <ul className="mt-4 space-y-2">
              {menu.blocks.slice(0, 4).map((b, i) => (
                <li key={i} className="flex items-baseline justify-between gap-3 text-sm">
                  <span className="text-oled-fg">
                    {b.repeat > 1 ? `${b.repeat}× ` : ""}
                    {b.steps.map((s) => s.title).join(" · ")}
                  </span>
                  <span className="shrink-0 text-oled-muted">
                    {Math.round(b.steps.reduce((n, s) => n + s.seconds, 0) / 60)}m
                  </span>
                </li>
              ))}
            </ul>
          </div>
        )}
        {menu && menu.blocks.length > 0 && (
          <div className="mt-3 flex gap-2">
            <button
              type="button"
              className="flex h-11 flex-1 items-center justify-center gap-2 rounded-full bg-lime text-sm font-semibold text-ink"
              onClick={() => {
                if (session.menuId !== menu.id || (elapsed === 0 && !session.playing)) session.start(menu.id);
                else session.toggle();
              }}
            >
              {session.playing && session.menuId === menu.id ? <Pause className="size-4" /> : <Play className="size-4" />}
              {session.playing && session.menuId === menu.id ? "Jeda" : elapsed > 0 && !done ? "Lanjut" : "Mulai"}
            </button>
            <button
              type="button"
              aria-label="Ulang sesi"
              className="grid size-11 place-items-center rounded-full bg-oled-chip text-oled-fg"
              onClick={() => session.reset(menu.id)}
            >
              <RotateCcw className="size-4" />
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
