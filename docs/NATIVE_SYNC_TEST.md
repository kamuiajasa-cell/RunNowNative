# Uji sinkronisasi native RunNow

Dokumen ini dipakai setelah APK dari commit terbaru lulus GitHub Actions. Build yang lulus hanya membuktikan kompilasi dan unit test; sinkronisasi nyata tetap harus diverifikasi di ponsel dan Huawei Watch.

## Persiapan
1. Unduh artifact `RunNow-debug-apk` dari workflow **Build APK** pada commit yang dinyatakan siap diuji.
2. Pastikan APK dapat dipasang. Bila Android menolak pembaruan karena tanda tangan berbeda, jangan hapus aplikasi lama sebelum data penting diamankan; laporkan pesan penolakan dan versi APK yang terpasang.
3. Buka RunNow dan selesaikan onboarding.

## Uji Huawei Watch
1. Buka **Profil → Perangkat & sinkronisasi → Kelola koneksi jam**.
2. Pastikan jam tersambung melalui Huawei Core, lalu jalankan sinkronisasi riwayat pada layar pairing/perangkat.
3. Kembali ke RunNow dan tekan **Perbarui**.
4. Periksa **Profil → Perangkat & sinkronisasi**: nama/model perangkat, jumlah aktivitas, sampel HR, dan HRV.
5. Buka **Riwayat**. Pastikan aktivitas terbaru, tanggal, jarak, durasi, pace, dan HR cocok dengan data jam.
6. Buka satu aktivitas lari. Jika jam merekam stream, periksa grafik HR/pace, split per km, zona HRR, cadence, dan elevasi yang memang tersedia.
7. Tekan **Perbarui** lagi, lalu lakukan sinkronisasi jam sekali lagi. Pastikan aktivitas yang sama tidak bertambah menjadi baris duplikat.

## Uji ketahanan dan hasil
- Tutup dan buka ulang aplikasi; riwayat dan pengaturan harus tetap ada.
- Jika stream tidak tersedia, ringkasan aktivitas tetap harus muncul dan aplikasi tidak boleh crash.
- Jika ada kegagalan, catat model ponsel, versi Android, model/firmware jam, langkah terakhir, serta teks error yang tampil.
- Jangan menganggap angka yang tidak disediakan jam sebagai nol; tampilan harus tetap kosong (—).

## Batas uji
- Intervals.icu adalah sumber tambahan opsional. Sinkronisasi Huawei dapat diuji tanpa API key Intervals.icu.
- Cuaca historis memerlukan koordinat GPS aktivitas dan koneksi internet.
- Uji perangkat fisik tidak dapat digantikan oleh build CI.
