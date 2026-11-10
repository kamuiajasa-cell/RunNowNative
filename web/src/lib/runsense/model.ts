export type Goal = "5K" | "10K" | "Half" | "Marathon" | "Umum";
export type Style = "moderate" | "conservative" | "aggressive";
export type ActKind = "run" | "walk" | "other";
export type StepKind = "warmup" | "run" | "walk" | "rest" | "cooldown";
export type Intensity = "none" | "pace" | "hrPercent";

export type Act = {
  t: number;
  kind: ActKind;
  min: number;
  km: number;
  hr: number;
  id: string;
  name: string;
};

export type Wellness = {
  id: string;
  restingHR: number | null;
  ctl: number | null;
  atl: number | null;
  tsb: number | null;
};

export type PlanStep = {
  kind: StepKind;
  title: string;
  detail: string;
  seconds: number;
  intensity: Intensity;
  low: number;
  high: number;
};

export type PlanBlock = { repeat: number; steps: PlanStep[] };

export type Plan = {
  tag: string;
  tone: "ok" | "warn" | "bad";
  status: "train" | "rest" | "done" | "skip";
  title: string;
  durationLabel: string;
  hr: string;
  pace: string;
  reason: string;
  blocks: PlanBlock[];
};

export type Athlete = {
  name: string;
  ctl: number | null;
  atl: number | null;
  tsb: number | null;
  lthr: number | null;
  ltPace: string | null;
  formLabel: string;
  formClass: "baik" | "sedang" | "lelah" | "kurang";
  mets: number | null;
  stage: number;
  stageName: string;
  estSrc: string;
  estConf: string;
  score: number | null;
};

export type GoalEst = { current: string; basis: string; confidence: string };

export type HistoryItem = {
  t: number;
  date: string;
  title: string;
  meta: string;
  load: number;
  type: string;
  why: string;
  km: number;
  min: number;
  hr: number;
  pace: string;
};

export type Derived = {
  athlete: Athlete;
  plan: Plan;
  goalEst: GoalEst;
  history: HistoryItem[];
  week: { name: string; km: number; today: boolean }[];
  weekKm: number;
  weekSessions: number;
  weekLoad: number;
  review: { tone: "g" | "y"; text: string }[];
};

export const GOALS: Goal[] = ["5K", "10K", "Half", "Marathon", "Umum"];
export const STAGE_NAMES = ["", "Fondasi: jalan cepat", "Run-walk", "Easy terbatas", "Terstruktur"];
