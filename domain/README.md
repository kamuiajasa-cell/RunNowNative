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

## Belum diport (langkah berikutnya)

`designTodayCore` (rencana hari ini), `processData`, `reviewRun`, rencana mingguan/race/cutback,
`summarizeStream`, dan modul cuaca (`cl*`). Semuanya memakai `Snapshot` yang sama.

## Catatan

Kode ini belum pernah dikompilasi di lingkungan pembuatnya (tidak ada compiler Kotlin di sana).
Jalankan `gradle :domain:test` dulu; kalau ada error kompilasi atau tes gagal, kirim lognya.
