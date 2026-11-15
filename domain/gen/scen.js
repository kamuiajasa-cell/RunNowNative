module.exports = [
  { name: "A_lengkap_hrv_turun", seed: 11, days: 70, skip: 0.25, hr: true, icuLoad: true, wdays: 40, hrvDrop: 0.2, rhrUp: 4, sleepCut: 1.2, stressUp: 20, rest: 54, max: 188, age: 35, afS: 0 },
  { name: "B_tanpa_hr_tanpa_wellness", seed: 22, days: 40, skip: 0.4, hr: false, icuLoad: false, wdays: 4, hrvDrop: 0, rhrUp: 0, sleepCut: 0, stressUp: 0, rest: 0, max: 0, age: 0, afS: 0 },
  { name: "C_umur_saja", seed: 33, days: 60, skip: 0.3, hr: true, icuLoad: false, wdays: 35, hrvDrop: 0.1, rhrUp: 2, sleepCut: 0, stressUp: 0, rest: 58, max: 0, age: 42, afS: 3 },
  { name: "D_hrv_tak_berubah", seed: 44, days: 60, skip: 0.2, hr: true, icuLoad: true, wdays: 35, hrvDrop: 0, rhrUp: 0, sleepCut: 0, stressUp: 0, rest: 50, max: 190, age: 30, afS: 0 },
  { name: "E_pemula_lambat_tahap1", seed: 55, days: 60, skip: 0.35, hr: true, icuLoad: false, wdays: 30, hrvDrop: 0, rhrUp: 0, sleepCut: 0, stressUp: 0, rest: 66, max: 185, age: 45, afS: 0, paceBase: 9.5 },
  { name: "F_histeresis_naik_tertahan", seed: 66, days: 60, skip: 0.35, hr: true, icuLoad: false, wdays: 30, hrvDrop: 0, rhrUp: 0, sleepCut: 0, stressUp: 0, rest: 62, max: 185, age: 40, afS: 2, paceBase: 8.5 },
  { name: "I_histeresis_turun_tertahan", seed: 66, days: 60, skip: 0.35, hr: true, icuLoad: false, wdays: 30, hrvDrop: 0, rhrUp: 0, sleepCut: 0, stressUp: 0, rest: 62, max: 185, age: 40, afS: 3, paceBase: 8.6 },
  { name: "G_menengah_tahap3", seed: 77, days: 60, skip: 0.3, hr: true, icuLoad: false, wdays: 30, hrvDrop: 0, rhrUp: 0, sleepCut: 0, stressUp: 0, rest: 60, max: 185, age: 40, afS: 4, paceBase: 6.3 },
  { name: "H_kosong", seed: 88, days: 3, skip: 1.0, hr: false, icuLoad: false, wdays: 0, hrvDrop: 0, rhrUp: 0, sleepCut: 0, stressUp: 0, rest: 0, max: 0, age: 0, afS: 0 }
];
