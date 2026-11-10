# RunSense (Run Now Native)

Aplikasi Android **RunSense** yang menggabungkan antarmuka Run Now dengan integrasi perangkat wearable Huawei/Honor. Proyek ini menggunakan Android native (Kotlin) sebagai host aplikasi, sementara halaman Run Now dan pengaturannya dirender di WebView lokal.

> **Status proyek:** dalam pengembangan. Keberhasilan kompilasi bukan bukti bahwa seluruh fitur Bluetooth, sinkronisasi, dan notifikasi sudah teruji pada perangkat fisik.

## Gambaran arsitektur

```text
Android app (Kotlin)
├── MainActivity
│   └── WebView → app/src/main/assets/index.html
├── SettingsActivity
│   └── WebView → app/src/main/assets/pengaturan.html
├── RunNowBridge
│   └── RPC dari JavaScript ke Huawei core
└── huawei (Android library)
    └── koneksi Bluetooth LE, pengelolaan perangkat, dan sinkronisasi
```

WebView memuat aset lokal melalui **AndroidX WebKit WebViewAssetLoader** pada origin `https://runnow.local`. Cara ini mempertahankan perilaku origin HTTPS untuk penyimpanan web seperti `localStorage`, tanpa harus mengambil halaman utama dari server.

## Fitur dan integrasi

### Aplikasi RunSense
- Halaman utama berada di `app/src/main/assets/index.html`.
- Halaman pengaturan berada di `app/src/main/assets/pengaturan.html`.
- Navigasi layar native ditangani melalui `Routes.kt`.
- Pengingat rencana latihan dijadwalkan Android dan dapat dijadwalkan ulang setelah perangkat dimulai ulang.

### Integrasi Huawei/Honor
Modul `huawei/` menyediakan fondasi koneksi dan komunikasi dengan wearable. Jembatan `RunNowBridge.kt` mengekspos metode RPC yang diizinkan, antara lain:

- Koneksi perangkat, sinkronisasi aktivitas, sinkronisasi riwayat, dan pembaruan data latihan.
- Profil pengguna serta pengaturan zona detak jantung (metode HR maksimum dan HRR).
- Detak jantung langsung dan pengukuran HRV.
- Pengaturan sensor kesehatan, termasuk detak jantung otomatis, SpO₂, TruSleep, dan peringatan detak.
- Status dan pengaturan sinkronisasi latar belakang.
- Cuaca pada jam.
- Akses notifikasi ponsel, ringkasan pagi, fitur panggilan, dan Find My Phone.
- Laporan dukungan dan pengelolaan perangkat yang tersimpan.

Ketersediaan setiap fitur bergantung pada model jam, firmware, izin Android, dan dukungan protokol perangkat. Jangan menganggap semua fitur telah tervalidasi pada setiap model.

## Cara kerja JavaScript bridge

Halaman web menggunakan pola RPC yang sudah dipakai oleh kode Run Now:

1. JavaScript mengirim `postMessage({type: "rpc", id, method, args})` melalui `window.ReactNativeWebView`.
2. Android menerima pesan melalui `RunNowBridge`, memeriksa nama metode dan argumennya, lalu menjalankan fungsi yang terdaftar.
3. Hasil dikirim kembali ke halaman melalui `window.__rn(id, ok, value)`.

Objek JavaScript `Android` terpisah digunakan untuk fungsi Android tertentu, termasuk penjadwalan pengingat latihan. Jangan menambahkan metode bridge tanpa validasi argumen dan pembatasan metode yang boleh dipanggil.

## Teknologi

| Komponen | Versi / konfigurasi |
|---|---|
| Bahasa | Kotlin |
| Android Gradle Plugin | 8.9.1 |
| Kotlin | 2.0.20 |
| Gradle pada CI | 8.11.1 |
| Java target | 17 |
| Compile SDK | 36 |
| Target SDK | 34 |
| Minimum SDK | 29 (Android 10) |
| UI native | Android WebView dan AndroidX Compose Material 3 |
| WebView asset origin | `https://runnow.local` |

## Build APK

### GitHub Actions

1. Buka tab **Actions** pada repository.
2. Pilih workflow **Build APK**.
3. Jalankan **Run workflow**, atau gunakan workflow yang berjalan otomatis saat ada push ke `main` atau `master`.
4. Setelah job berhasil, unduh artifact **RunNow-debug-apk**.
5. Pasang APK pada perangkat Android yang memenuhi minimum SDK.

Workflow menjalankan `gradle assembleDebug --no-daemon` dan mengunggah:

`app/build/outputs/apk/debug/app-debug.apk`

### GitHub Codespaces

1. Buka repository di Codespaces dengan lingkungan yang memiliki ruang dan memori memadai.
2. Tunggu proses `.devcontainer/setup.sh` menyiapkan Android SDK dan Gradle.
3. Jalankan:

```bash
gradle assembleDebug --no-daemon
```

APK debug akan tersedia di:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Build di Codespaces hanya memeriksa proses kompilasi. Pengujian Bluetooth LE, pairing, sinkronisasi, izin, dan perilaku latar belakang tetap harus dilakukan pada ponsel dan jam fisik.

## Pengujian perangkat yang perlu dilakukan

- Instalasi dan pembukaan aplikasi pada Android 10 atau lebih baru.
- Pairing dan koneksi ulang jam Huawei/Honor.
- Sinkronisasi aktivitas terbaru dan riwayat.
- Pengiriman detak jantung langsung dan pengukuran HRV.
- Pengaturan sensor, zona detak jantung, dan bahasa jam.
- Sinkronisasi latar belakang setelah izin diberikan.
- Notifikasi dan fitur telepon sesuai izin yang disetujui pengguna.
- Pengingat latihan saat aplikasi ditutup dan setelah ponsel dimulai ulang.
- Impor/ekspor berkas serta navigasi antarhalaman.

Catat model jam, versi firmware, versi Android, langkah reproduksi, dan log ketika melaporkan masalah.

## Struktur direktori

```text
.
├── .devcontainer/             # Konfigurasi lingkungan Codespaces
├── .github/workflows/         # Otomatisasi build APK
├── app/
│   └── src/main/
│       ├── assets/
│       │   ├── index.html     # Antarmuka utama Run Now
│       │   └── pengaturan.html
│       ├── java/com/runnow/app/
│       │   ├── MainActivity.kt
│       │   ├── BridgeActivity.kt
│       │   ├── AndroidBridge.kt
│       │   ├── PairingActivity.kt
│       │   ├── SettingsActivity.kt
│       │   ├── WatchScreen.kt
│       │   ├── PlanNotifier.kt
│       │   └── Routes.kt
│       └── AndroidManifest.xml
├── huawei/                    # Library integrasi wearable
│   ├── src/main/
│   ├── LICENSE
│   └── NOTICE
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Lisensi dan atribusi

Modul `huawei/` berasal dari proyek JoyoFit dan berlisensi **GNU Affero General Public License v3.0 atau yang lebih baru (AGPL-3.0-or-later)**, sebagai turunan Gadgetbridge. Baca `huawei/LICENSE` dan `huawei/NOTICE` untuk teks lisensi dan atribusi.

Distribusi aplikasi yang memuat modul tersebut harus mematuhi kewajiban AGPL yang berlaku, termasuk menyediakan source code yang sesuai kepada penerima distribusi. Tinjau juga lisensi dan kewajiban untuk setiap dependensi pihak ketiga.

## Keamanan distribusi

Jangan menyimpan keystore rilis, kata sandi signing, token, atau kredensial di repository. Karena repository ini bersifat publik dan riwayat Git dapat mempertahankan berkas yang pernah di-commit, setiap signing key yang pernah terekspos harus dianggap tidak rahasia: rotasi kunci dan pindahkan proses signing ke GitHub Actions Secrets atau penyimpanan aman sebelum menerbitkan rilis. Jangan gunakan konfigurasi signing pengembangan untuk distribusi produksi.

## Kontribusi dan pelaporan masalah

Sebelum mengirim perubahan, jalankan build debug dan uji alur yang terdampak pada perangkat fisik bila perubahan menyentuh Bluetooth, sensor, notifikasi, atau proses latar belakang. Sertakan ringkasan perubahan dan hasil pengujian pada pull request.
