import { create } from "zustand";

type Session = {
  menuId: string;
  playing: boolean;
  /** Elapsed seconds captured whenever playback pauses. */
  base: number;
  origin: number;
  start: (menuId: string) => void;
  toggle: () => void;
  reset: (menuId?: string) => void;
  elapsed: () => number;
};

export const useSession = create<Session>((set, get) => ({
  menuId: "",
  playing: false,
  base: 0,
  origin: 0,
  start: (menuId) => set({ menuId, playing: true, base: 0, origin: Date.now() }),
  toggle: () => {
    const s = get();
    if (!s.menuId) return;
    if (s.playing) set({ playing: false, base: s.base + (Date.now() - s.origin) / 1000 });
    else set({ playing: true, origin: Date.now() });
  },
  reset: (menuId) => set({ menuId: menuId ?? get().menuId, playing: false, base: 0, origin: 0 }),
  elapsed: () => {
    const s = get();
    return s.base + (s.playing ? (Date.now() - s.origin) / 1000 : 0);
  },
}));
