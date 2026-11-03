# Run Now (native)

APK Android native: `index.html` (Run Now) di WebView, dengan Huawei core dari JoyoFit/PolarRun di belakangnya.

## Isi
- `huawei/` modul koneksi jam (dari PolarRun). Baru: `RunNowBridge.kt`, 25 metode pengaturan untuk index.html.
- `app/` `MainActivity` (WebView), `PairingActivity` (layar koneksi jam), `PlanNotifier` (pengingat latihan).
- `app/src/main/assets/index.html` halaman Run Now. Dimuat dari `https://runnow.local/index.html`.

## Cara kerja jembatan
index.html memanggil `window.ReactNativeWebView.postMessage({type:"rpc", ...})`. Jembatan didaftarkan ke WebView
dengan nama itu, jadi index.html tidak perlu diubah. Jawaban dikirim balik lewat `window.__rn(id, ok, nilai)`.
Pengingat latihan memakai `Android.setPlan` dan `Android.test`.

## Build
- GitHub Actions: tab Actions > "Build APK" > unduh artifact `RunNow-debug-apk`.
- Codespaces: buka repo di Codespaces (mesin 4 core), tunggu `.devcontainer/setup.sh` selesai, lalu `gradle assembleDebug`.
  APK ada di `app/build/outputs/apk/debug/app-debug.apk`. Bluetooth tidak bisa diuji di Codespaces: unduh APK dan pasang di HP.

## Belum ada
- Pengiriman data jam (aktivitas, wellness) ke halaman (`window.nativeImport`). Di JoyoFit itu dikerjakan `bridge.ts` dan `wellness.ts`.
- Layar alarm jam, tampilan jam, impor ekspor Huawei, dan pilihan aplikasi notifikasi.
- Ikon aplikasi (memakai ikon bawaan Android).

## Lisensi
Modul `huawei/` berasal dari JoyoFit (AGPL-3.0-or-later, turunan Gadgetbridge); lihat `huawei/LICENSE` dan `huawei/NOTICE`.
APK yang memuatnya juga harus AGPL: sumbernya harus tersedia bagi penerima APK.

## Status
Belum pernah dikompilasi. Kalau build gagal, kirim baris `error:` pertama dari log.
