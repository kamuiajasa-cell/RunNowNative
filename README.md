# RunNow Native Kotlin

Migrasi aplikasi RunSense ke aplikasi Android native Kotlin/Jetpack Compose. Nama produk tetap RunNow.

## Status migrasi
- `:domain` adalah engine Kotlin murni: kapasitas, beban, readiness/autonomic, Coach, rencana, review aktivitas dan ringkasan stream.
- Launcher native menggunakan Jetpack Compose dan empat tab: Hari Ini, Riwayat, Analisis, Profil.
- Data dibaca langsung dari database lokal Huawei Core melalui `WatchData`; tidak bergantung pada URL Cloudflare untuk UI utama.
- Aktivitas dipetakan ke model domain dan diproses oleh `Snapshot.processData()`; review aktivitas memakai `Snapshot.reviewRun()`.
- Layar pairing/pengaturan Huawei lama dipertahankan agar fungsi koneksi yang sudah ada tidak hilang.

## Batasan tahap ini
- Migrasi UI dan alur data utama sudah dimulai, tetapi belum semua halaman/detail dan seluruh grafik HTML dipindahkan.
- Stream GPS/HR aktivitas dipetakan dari titik workout Huawei menjadi `StreamData` dan diringkas oleh engine Kotlin untuk review (split pace, fade, decoupling, zona HR). Tampilan grafik detail stream masih perlu diperdalam.
- Data historis yang tersedia bergantung pada isi database Huawei; metrik yang tidak tersedia ditampilkan sebagai "—", bukan dibuat-buat.
- Profil atlet (umur, RHR manual, HRmax, zona) dan pengaturan Coach masih perlu layar editor native tersendiri.
- Build/test harus dikonfirmasi lewat GitHub Actions sebelum APK dipakai sebagai pengganti produksi.

## Build
```bash
gradle :domain:test assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`
