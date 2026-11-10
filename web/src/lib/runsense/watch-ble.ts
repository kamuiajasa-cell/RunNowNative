/** Huawei LE service used by Watch Fit 2 (GATT 0xFE86, write FE01 / notify FE02). */
export const HUAWEI_SERVICE = "0000fe86-0000-1000-8000-00805f9b34fb";

export type WatchLink = {
  name: string;
  id: string;
  gatt: boolean;
  at: number;
};

type BleDevice = {
  id: string;
  name?: string;
  gatt?: { connect: () => Promise<{ getPrimaryService: (u: string) => Promise<unknown> }> };
};

type BleNavigator = {
  requestDevice: (opts: {
    filters: ({ namePrefix?: string; services?: string[] })[];
    optionalServices?: string[];
  }) => Promise<BleDevice>;
  getDevices?: () => Promise<BleDevice[]>;
};

export function bleSupported() {
  return typeof navigator !== "undefined" && "bluetooth" in navigator;
}

function ble(): BleNavigator | null {
  if (!bleSupported()) return null;
  return (navigator as Navigator & { bluetooth?: BleNavigator }).bluetooth ?? null;
}

/**
 * Opens the system picker for a Huawei watch and connects GATT.
 * The encrypted P2P course channel is not available in a browser, so this
 * confirms the watch is present and returns its name. The course file itself
 * is built by encodeCourse and shown on the on-screen Watch Fit 2.
 */
export async function pickHuaweiWatch(): Promise<WatchLink> {
  const api = ble();
  if (!api) {
    throw new Error("Bluetooth tidak ada di browser ini. Menu tetap tampil di jam layar.");
  }
  let device: BleDevice;
  try {
    device = await api.requestDevice({
      filters: [
        { namePrefix: "HUAWEI" },
        { namePrefix: "Huawei" },
        { namePrefix: "Watch Fit" },
        { services: [HUAWEI_SERVICE] },
      ],
      optionalServices: [HUAWEI_SERVICE],
    });
  } catch (err) {
    const name = err instanceof Error ? err.name : "";
    if (name === "NotFoundError") throw new Error("Pemilihan jam dibatalkan.");
    throw new Error("Tidak bisa membuka pemilih Bluetooth.");
  }
  let gatt = false;
  try {
    const server = await device.gatt?.connect();
    if (server) {
      await server.getPrimaryService(HUAWEI_SERVICE);
      gatt = true;
    }
  } catch {
    gatt = false;
  }
  const name = device.name?.trim() || "Huawei Watch";
  return { name, id: device.id, gatt, at: Date.now() };
}
