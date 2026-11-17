# :data (Kotlin murni)

Lapisan data RunSense: semua yang di `runsense.html` menyentuh `localStorage`, `fetch`, dan data jam. Bergantung pada `:domain`.
Tidak memakai Android; bagian Android ada di `app/src/main/java/com/runnow/app/data/` lewat antarmuka di `Ports.kt`.

| HTML | Kotlin |
|---|---|
| `kind`, `row`, `mergeActs`, `matchIcu`, `dedupeActs` | `Ingest` |
| bagian `athlete/0` dan wellness di `sync()` | `Ingest.applyAthlete`, `Ingest.mergeIcuWellness` |
| `ingestWatch` | `Ingest.ingestWatch` (+ `RunSenseRepository.ingestWatch`) |
| `persist`, `restore`, `clSave` | `Codec` (format JSON sama dengan localStorage: `runsense_auto_v2`, `runsense_clim`) |
| `icuGet` | `IcuHttp` (Basic auth, 401/403 = "API key ditolak") |
| `sync`, `ensureStream`, `pullWatch`, `pullProfile`, `logout` | `RunSenseRepository` |
| `wxGet`, `clGpsIcu`, `clMatch`, `clLoc`, `autoHeat` | `RunSenseRepository` + `OpenMeteo` |
| parser stream/GPS Intervals | `Codec.parseStream`, `Codec.parseLatLng` |

`AppData` = isi `state` yang disimpan, tidak berubah di tempat. `RunSenseRepository.process()` memanggil `processData` dari `:domain`.

## Tes
- `DataGoldenTest` + `golden_data.txt`: 54 skenario (sync 16, jam 14, stream 12, cuaca/GPS 12) dari kode asli HTML dengan
  `fetch` dan `localStorage` palsu; membandingkan isi simpanan akhir. Buat ulang: `cd domain/gen && RUNSENSE_HTML=... node gen_data.js`.
- `JsonTest`: parser JSON, bilangan, `toFixed`, dan tanggal ISO.

## Yang BELUM diuji di perangkat (kamu uji di HP)
`FileKeyValueStore`, `UrlConnectionHttpClient`, `HuaweiWatchSource` (memanggil `WatchImport` di modul huawei),
`AndroidLocator`, dan perakitannya di `RunSenseGraph`. `WatchProfile` dari jam belum dihubungkan ke `WearRuntime`
(`HuaweiWatchSource(profileProvider)`); itu bagian Langkah 7/10.

## Beda yang disengaja dari HTML
- Kotlin menghitung ulang ambang pribadi (`clPers`) tiap lari saat `climateMatch`; HTML memakai cache berdasar jumlah konteks.
- `RunSenseRepository.climateMatch()` memproses satu putaran (<= 10 lari) dan mengembalikan jumlahnya; panggil lagi selama > 0
  (HTML menjadwalkan ulang dirinya 800 ms kemudian).
- Tanggal ISO yang dikenali: tanggal saja (UTC), tanggal-jam tanpa zona (zona perangkat), dengan Z atau offset. Format lain ditolak.
