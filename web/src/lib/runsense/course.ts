import type { Plan, PlanBlock, PlanStep } from "./model";

/** Minimal protobuf writer matching Huawei CourseProtobuf (proto2 required fields). */
class Pb {
  private bytes: number[] = [];

  private varint(value: number) {
    let v = value >>> 0;
    while (v > 0x7f) {
      this.bytes.push((v & 0x7f) | 0x80);
      v >>>= 7;
    }
    this.bytes.push(v & 0x7f);
  }

  private tag(field: number, wire: number) {
    this.varint((field << 3) | wire);
  }

  int(field: number, value: number) {
    this.tag(field, 0);
    this.varint(value);
    return this;
  }

  str(field: number, value: string) {
    const raw = new TextEncoder().encode(value);
    this.tag(field, 2);
    this.varint(raw.length);
    raw.forEach((b) => this.bytes.push(b));
    return this;
  }

  raw(field: number, value: Uint8Array) {
    this.tag(field, 2);
    this.varint(value.length);
    value.forEach((b) => this.bytes.push(b));
    return this;
  }

  out() {
    return new Uint8Array(this.bytes);
  }
}

const ACTION: Record<PlanStep["kind"], string> = {
  warmup: "RD001",
  run: "RD002",
  walk: "RD003",
  rest: "RD004",
  cooldown: "RD005",
};

const TARGET_TIME = 1;
const INTENSITY: Record<PlanStep["intensity"], number> = { none: 255, pace: 15, hrPercent: 1 };

function encodeAction(step: PlanStep) {
  let low = 0;
  let high = 0;
  if (step.intensity === "pace") {
    low = step.low * 1000;
    high = step.high * 1000;
  } else if (step.intensity === "hrPercent") {
    low = step.low;
    high = step.high;
  }
  return new Pb()
    .str(1, ACTION[step.kind])
    .int(2, TARGET_TIME)
    .int(3, step.seconds)
    .int(4, INTENSITY[step.intensity])
    .int(5, high)
    .int(6, low)
    .out();
}

function encodeCombine(block: PlanBlock) {
  const w = new Pb().int(1, block.repeat).int(2, block.steps.length);
  for (const step of block.steps) w.raw(3, encodeAction(step));
  return w.out();
}

export type CourseFile = {
  id: string;
  name: string;
  fileName: string;
  bytes: Uint8Array;
  messageId: number;
};

let seq = Math.floor(Math.random() * 800) + 20;

/** Build the `<messageId>courseInfo.bin` Huawei Health pushes to in.huawei.fitness. */
export function encodeCourse(plan: Plan, name?: string): CourseFile {
  const id = "RS" + Date.now().toString(36).toUpperCase();
  const title = (name || plan.title).trim().slice(0, 40) || "RunSense";
  const messageId = (seq = (seq + 1) % 1000000) || 1;
  const update = Math.floor(Date.now() / 1000);
  const actionCount = plan.blocks.reduce((s, b) => s + b.steps.length, 0);
  const total = plan.blocks.reduce((s, b) => s + b.repeat * b.steps.reduce((x, st) => x + st.seconds, 0), 0);
  const w = new Pb().str(1, id);
  const nameBytes = new TextEncoder().encode(title);
  for (const b of nameBytes) w.int(2, b);
  w.int(3, 1)
    .int(4, total)
    .str(5, "2.0")
    .int(6, 0)
    .int(7, 0)
    .int(8, update)
    .int(9, 1)
    .int(10, 4)
    .int(11, actionCount);
  for (const block of plan.blocks) w.raw(12, encodeCombine(block));
  const payload = w.out();
  const header = new ArrayBuffer(36);
  const view = new DataView(header);
  const totalLength = 36 + payload.length;
  view.setInt32(0, 3, true);
  view.setInt32(4, 1, true);
  view.setInt32(8, totalLength, true);
  view.setInt32(12, 0, true);
  view.setInt32(16, messageId, true);
  const file = new Uint8Array(totalLength);
  file.set(new Uint8Array(header), 0);
  file.set(payload, 36);
  return { id, name: title, fileName: `${messageId}courseInfo.bin`, bytes: file, messageId };
}

export function downloadCourse(file: CourseFile) {
  const blob = new Blob([file.bytes.buffer.slice(file.bytes.byteOffset, file.bytes.byteOffset + file.bytes.byteLength) as ArrayBuffer], {
    type: "application/octet-stream",
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = file.fileName;
  a.click();
  URL.revokeObjectURL(url);
}
