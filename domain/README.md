# runnow-domain

Port Kotlin murni dari logika inti `runsense.html` (baris 797–1130). Tanpa Android, tanpa state global.

## Pasang ke proyek

1. (Sudah dilakukan lewat upload.)
2. `settings.gradle.kts`: tambahkan `include(":domain")`.
3. Root `build.gradle.kts`: tambahkan
   `id("org.jetbrains.kotlin.jvm") version "2.0.20" apply false`
4. `app/build.gradle.kts`: tambahkan `implementation(project(":domain"))`.
5. Jalankan tes: `gradle :domain:test`
   (opsional: tambahkan langkah itu di `.github/workflows/build.yml` sebelum `assembleDebug`).

## Yang sudah diport

| HTML | Kotlin |
|---|---|
| `metsAt/metsOf/paceOf/pctAt` | `Mets.kt` |
| `f1/mp/formatPace/formatTime/fmtM/fmtHm/durText/band` | `Util.kt` |
| `restHr/maxHr/rhr28/estMax/stage/qualityEligible` | `Capacity.kt` |
| `loadOf/loadDays` | `Load.kt` |
| `autonomic/readiness` | `Autonomic.kt` |
| `detectType` | `SessionType.kt` |
| `wp` (run-walk A/B/C) | `WalkRun.kt` |

Semua fungsi engine adalah extension di `Snapshot` (profil + aktivitas + wellness + waktu). Perbedaan dari HTML:
`stage()` tidak mengubah state; simpan `Stage.afS` ke `Profile.afS` sendiri.

## Tes

`GoldenTest.kt` dibuat otomatis: kode asli HTML dijalankan di Node (TZ=UTC, waktu tetap) pada 8 skenario
(tahap 2/3/4, histeresis naik dan turun, tanpa HR, tanpa data, HRV turun/tidak), lalu hasilnya jadi nilai acuan.
Buat ulang: `cd domain/gen && RUNSENSE_HTML=/path/ke/runsense.html node gen_tests.js`

## Langkah 2 (rencana dan hari ini)

`Plan.kt` (planOf, planWeekKm, cutbackDue, racePhase, goalPace, flag sakit, panas, easyBase/easyRange) dan
`DesignToday.kt` (designToday, decorate, designTodayCore). Tes: `DesignTodayGoldenTest` + `golden_design.txt`
(dibuat oleh `gen/gen_design.js`).

## Langkah 3 (pengolahan data)

| HTML | Kotlin |
|---|---|
| `processData` | `Snapshot.processData(SyncInfo)` di `Process.kt`, mengembalikan `ProcessResult` |
| `state.athlete` | `ProcessResult.athlete` (`AthleteSummary`: CTL/ATL/TSB, LTHR, LTPace, bentuk, kesiapan, tahap) |
| `state.goalEst` | `ProcessResult.goalEst` |
| `state.activities` | `ProcessResult.activities` (25 terbaru, `ActivityItem`) |
| `state.recommendation` | `ProcessResult.recommendation` |
| `state.rest`, `state.afS` | `ProcessResult.profile` (RHR otomatis dan histeresis tahap; simpan ke penyimpanan) |
| `coachLine`, `intensityLine` | fungsi top-level di `Process.kt`; `rested`/`walk` adalah keputusan UI, jadi jadi argumen |
| `syncLine`, `hhmm` | `Snapshot.syncLine(SyncInfo)`, `Snapshot.hhmm` |

Input baru di model: `Profile.restManual/lthrC/ltpC`, `Settings.goal`, dan `SyncInfo(icuName, icuLast, hwLast)`.
Satu hal yang mudah terlewat: `processData` menimpa `state.athlete` dengan LTHR dan LTPace hasil hitung SEBELUM memanggil
`designToday`, jadi Kotlin juga mengisi `Settings.athlete` dari hasil itu sebelum memanggilnya.

Tes: `ProcessGoldenTest` + `golden_process.txt` (dibuat oleh `gen/gen_process.js`, 216 kasus dari 72 dataset, termasuk kasus tepi:
tanpa wellness, tanpa lari, max HR < 140, rentang HR < 40, LTHR/LTPace custom, RHR manual).
Buat ulang: `cd domain/gen && RUNSENSE_HTML=/path/ke/runsense.html node gen_process.js`.
Semua generator kini mencari awal engine lewat penanda `const clamp`, jadi tahan terhadap pergeseran nomor baris HTML.

## Belum diport (langkah berikutnya)

`reviewRun`, `reviewHeadline`, `summarizeStream`, `hwSummary`, `runBacktest` (Langkah 4) dan modul cuaca `cl*` (Langkah 5). Semuanya memakai `Snapshot` yang sama.

## Catatan

Kode ini belum pernah dikompilasi di lingkungan pembuatnya (tidak ada compiler Kotlin di sana).
Jalankan `gradle :domain:test` dulu; kalau ada error kompilasi atau tes gagal, kirim lognya.
