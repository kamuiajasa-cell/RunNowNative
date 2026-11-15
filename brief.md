# RunNowNative — Design Brief
## Arah visual: Clean Light Athletic

**Status:** Panduan desain untuk implementasi UI  
**Target:** RunNowNative / RunSense  
**File UI utama:** `app/src/main/assets/index.html`  
**File pengaturan:** `app/src/main/assets/pengaturan.html`

---

## 1. Tujuan desain

Membentuk RunNow menjadi aplikasi lari dan coaching yang terasa **bersih, ringan, tenang, modern, dan berorientasi pada data**. Referensi visual utamanya adalah dashboard fitness berlatar putih dengan kartu-kartu lapang, tipografi tegas, grafik sederhana, dan aksen biru.

Desain harus membantu pelari memahami tiga hal dengan cepat:

1. Apa kondisi tubuh dan kesiapan saya hari ini?
2. Apa latihan yang paling masuk akal dilakukan sekarang?
3. Bagaimana perkembangan latihan saya dari waktu ke waktu?

Aplikasi bukan social fitness feed, bukan game dengan badge berlebihan, dan bukan dashboard yang memamerkan semua metrik sekaligus. Prioritasnya adalah keputusan latihan yang jelas, keterbacaan, dan konsistensi.

## 2. Referensi visual

### Referensi utama — Fitness Mobile App UI UX
- Karya: Sourav Biswas, Dribbble.
- Halaman karya: https://dribbble.com/shots/26244077-Fitness-Mobile-App-UI-UX
- Gambar komposisi lengkap: https://cdn.dribbble.com/userupload/44005048/file/original-fa883263e41b00a4a91bc6a944864b0f.png?resize=2048x1446&vertical=center
- Palet yang ditampilkan pada halaman referensi: `#E8F0F6`, `#3589F5`, `#549FF4`, `#080B10`, `#A5C0D5`, `#1A6AEE`, `#454E57`.

**Yang diambil:** kanvas terang, kartu putih, ruang kosong yang cukup, angka statistik yang mudah dipindai, aksen biru, ikon sederhana, dan navigasi bawah yang konsisten.

**Yang tidak disalin:** identitas merek, susunan layar secara mentah, ilustrasi, foto, avatar, teks, atau aset milik desainer. Gunakan sebagai inspirasi visual, lalu susun ulang sesuai kebutuhan RunNow.

### Referensi sistem desain pendukung
- Appetite UI — Fitness screens: https://www.appetiteui.com/docs/screens-fitness
- Stride — Clean Athletic UI: https://dribbble.com/shots/27655765-Stride-Fitness-App-UI

Referensi pendukung berguna untuk pola dashboard, analytics, tab bar, kartu, kontrol periode, dan konsistensi light/dark theme. Jangan menyalin UI kit atau aset berlisensi tanpa izin.

## 3. Karakter visual

Gunakan karakter **Clean Light Athletic**:

- Terang dan lapang, bukan putih menyilaukan.
- Atletis dan presisi, bukan agresif atau terlalu futuristik.
- Data-first, tetapi tidak terasa seperti spreadsheet.
- Kartu sederhana dengan batas halus; bayangan hanya sebagai pemisah kedalaman.
- Biru sebagai aksen interaksi dan identitas, bukan warna latar untuk semua komponen.
- Grafik memakai warna seperlunya dan tetap mudah dibaca dalam kondisi buta warna.
- Hindari dekorasi yang tidak membawa informasi.

Kesan akhir: aplikasi kesehatan olahraga yang matang, ringan, dan dapat dipercaya.

## 4. Design tokens

Nilai berikut adalah **titik awal implementasi** yang boleh disempurnakan setelah diuji di perangkat.

### Light theme

| Token | Nilai | Penggunaan |
|---|---|---|
| `--bg` | `#F4F7FA` | Latar halaman |
| `--surface` | `#FFFFFF` | Kartu dan panel |
| `--surface-soft` | `#E8F0F6` | Area sekunder, chip pasif |
| `--text` | `#101820` | Teks utama |
| `--text-muted` | `#647383` | Label, caption, metadata |
| `--line` | `#DCE5EC` | Border dan divider |
| `--primary` | `#3589F5` | Aksi utama, tab aktif, link |
| `--primary-strong` | `#1A6AEE` | Aksi penting dan fokus |
| `--primary-soft` | `#E8F2FF` | Highlight biru |
| `--info` | `#549FF4` | Informasi dan seri grafik |
| `--success` | `#21865A` | Kondisi positif |
| `--warning` | `#C48625` | Perhatian |
| `--danger` | `#D94B4B` | Risiko, error, nyeri |

### Dark theme

Dark theme harus menjadi pasangan dari light theme, bukan sekadar membalik warna.

| Token | Nilai | Penggunaan |
|---|---|---|
| `--bg` | `#0D141B` | Latar halaman |
| `--surface` | `#151F29` | Kartu |
| `--surface-soft` | `#1D2A36` | Panel sekunder |
| `--text` | `#F1F6FA` | Teks utama |
| `--text-muted` | `#A2B1BE` | Label |
| `--line` | `#2B3A47` | Border |
| `--primary` | `#65A9FF` | Aksi dan fokus |
| `--primary-strong` | `#3589F5` | Aksi utama |
| `--primary-soft` | `#1A3552` | Highlight |
| `--success` | `#55C58A` | Kondisi positif |
| `--warning` | `#F0BD61` | Perhatian |
| `--danger` | `#FF7777` | Risiko dan error |

**Aturan:** semua warna komponen harus merujuk token. Jangan menanam warna hex berulang di setiap komponen. Pastikan kontras teks memenuhi keterbacaan WCAG AA sejauh memungkinkan.

## 5. Tipografi

- Font utama: **Inter**; fallback: system-ui, sans-serif.
- Angka statistik menggunakan font numerik yang jelas dan tabular numerals.
- Judul halaman: 22–26 px, semibold/bold.
- Judul bagian: 15–17 px, semibold.
- Angka utama: 28–36 px, bold.
- Isi: 14–16 px, regular.
- Label dan metadata: 11–13 px.
- Gunakan sentence case; hindari huruf kapital semua untuk label panjang.
- Jangan memakai terlalu banyak variasi ukuran atau ketebalan dalam satu kartu.

Angka seperti jarak, pace, HR, durasi, beban, dan readiness harus mudah dibandingkan. Gunakan `font-variant-numeric: tabular-nums`.

## 6. Layout dan spacing

### Mobile
- Prioritaskan lebar layar 360–430 px.
- Pertahankan satu kolom sebagai alur utama.
- Padding halaman: 16–20 px.
- Jarak antarbagian: 20–28 px.
- Jarak antarkartu: 12–16 px.
- Kartu memakai radius 16–20 px.
- Hindari kartu bertumpuk di dalam kartu kecuali ada alasan hierarki yang kuat.
- Sediakan ruang bawah agar konten terakhir tidak tertutup navigasi.

### Tablet / desktop
- Gunakan breakpoint yang sudah ada sebagai dasar; jangan merusak layout responsif saat ini.
- Maksimalkan lebar konten secara terukur, bukan meregangkan kartu statistik hingga terlalu lebar.
- Dashboard dan grafik boleh menggunakan grid dua kolom saat ruang cukup.
- Detail sesi dan formulir tetap memiliki lebar baca yang nyaman.

### Elevation
- Gunakan border tipis sebagai pemisah utama.
- Shadow sangat lembut dan konsisten.
- Jangan gunakan glassmorphism, gradient besar, atau glow neon sebagai gaya umum.

## 7. Komponen inti

### Header
- Nama/identitas RunNow di kiri.
- Tanggal atau konteks hari sebagai teks sekunder.
- Aksi penting seperti Journal dan Settings di kanan.
- Tinggi ringkas, tidak mengambil terlalu banyak ruang vertikal.

### Card
- Surface putih pada light theme.
- Border halus.
- Radius konsisten.
- Padding 16–20 px.
- Judul dan konten memiliki hierarki jelas.
- Jangan semua kartu diberi warna berbeda.

### Metric tile
- Angka menjadi fokus pertama.
- Label menjelaskan metrik dan satuan.
- Perubahan/tren menjadi teks sekunder, bukan bersaing dengan angka.
- Hindari menampilkan terlalu banyak angka pada satu layar.

### Button
- Primary: biru solid, teks putih.
- Secondary: surface-soft atau outline.
- Tertiary: teks/link tanpa kontainer.
- Tinggi target sentuh minimal 48 dp pada Android.
- Status disabled, loading, pressed, dan focus harus terlihat.

### Chip / segmented control
- Gunakan untuk filter dan pilihan periode.
- Pilihan aktif memakai aksen biru dengan kontras kuat.
- Jangan menggunakan chip sebagai dekorasi.

### Navigation
- Mobile: bottom navigation yang stabil, maksimal 5–6 tujuan utama.
- Ikon sederhana dan label pendek.
- Tab aktif menggunakan biru; tab pasif memakai warna muted.
- Desktop: boleh beralih ke sidebar bila breakpoint dan struktur saat ini mendukung.
- Navigasi harus konsisten di seluruh halaman utama.

### Chart
- Latar chart transparan atau menyatu dengan kartu.
- Gridline tipis.
- Label sumbu ringkas dan tidak bertumpuk.
- Warna seri konsisten antarhalaman.
- Tooltip/detail muncul saat pengguna memilih titik atau batang.
- Selalu sediakan ringkasan angka atau legenda; jangan mengandalkan warna saja.

## 8. Arahan desain per halaman RunNow

### Beranda
Urutan prioritas:
1. Sapaan singkat dan tanggal.
2. **Kondisi hari ini / readiness** dengan interpretasi bahasa manusia.
3. Rekomendasi sesi hari ini: jenis latihan, durasi/jarak, target pace atau HR bila tersedia.
4. Alasan rekomendasi dan batasan penting.
5. Ringkasan beban/jarak minggu berjalan.
6. Aktivitas terakhir.
7. Akses cepat ke check-in atau Journal.

Jangan menjadikan readiness ring besar sebagai satu-satunya fokus. Rekomendasi latihan harus lebih mudah dipahami daripada skor mentahnya.

### Rencana
- Tampilkan kalender atau ringkasan pekan.
- Sesi latihan disusun sebagai daftar/kartu yang mudah dipindai.
- Bedakan easy, quality, long run, recovery, dan rest dengan label serta warna status yang konsisten.
- Detail sesi berisi tujuan, pemanasan, bagian inti, pendinginan, target, dan alternatif.
- Jangan membuat kalender terlalu padat dengan dekorasi.

### Aktivitas
- Ringkasan jarak, durasi, pace, HR, dan tanggal.
- Daftar aktivitas dengan hierarki yang jelas.
- Detail aktivitas memprioritaskan peta/rute bila tersedia, lalu metrik inti, lap, zona, dan catatan.
- Filter aktivitas menggunakan chip atau segmented control.

### Grafik / Analisis
- Pilihan periode: 7, 14, 28, 90, 180, 365 hari, semua data, dan rentang khusus.
- Ringkasan metrik berada sebelum grafik.
- Grafik utama: volume/jarak, pace, HR, kebugaran/kelelahan, dan beban.
- Beri ruang cukup untuk grafik; hindari grid chart mini yang tidak terbaca.
- Penjelasan insight harus memisahkan fakta, estimasi, dan interpretasi coach.

### Tubuh / Wellness
- Check-in tidur, kelelahan, pegal, motivasi, nyeri, dan indikator lain yang memang didukung.
- Gunakan skala yang mudah dipahami.
- Tampilkan tren dan konteks baseline pribadi.
- Status risiko tidak boleh disampaikan sebagai diagnosis medis.
- Warna warning/danger hanya digunakan ketika ada alasan yang relevan.

### Pengaturan
- Kelompokkan berdasarkan: profil, zona HR/pace, sumber data, perangkat, sinkronisasi, notifikasi, tampilan, privasi, dan informasi aplikasi.
- Gunakan list row yang rapi dengan judul, deskripsi pendek, dan nilai/status di sisi kanan.
- Pengaturan teknis tingkat lanjut dapat berada di accordion/detail.

## 9. Data visualization dan bahasa coach

- Gunakan warna biru untuk data utama atau seri default.
- Hijau = positif/selesai; amber = perhatian; merah = risiko/error.
- Jangan menyimpulkan “fit” hanya dari satu metrik.
- Jangan membuat warna sebagai satu-satunya pembeda status.
- Tampilkan satuan dan periode secara eksplisit.
- Jika data kosong, tampilkan empty state yang menjelaskan tindakan berikutnya.
- Jika data tidak sinkron, tampilkan waktu sinkronisasi terakhir.
- Coach harus menjelaskan **apa yang disarankan, mengapa, dan apa alternatifnya**.
- Hindari bahasa menghakimi seperti “gagal” atau “malas”.

## 10. Motion dan feedback

- Animasi pendek dan fungsional, umumnya 150–250 ms.
- Gunakan transisi untuk perubahan tab, expand/collapse, dan update chart.
- Jangan menganimasikan semua angka secara terus-menerus.
- Beri feedback jelas untuk sinkronisasi, impor, penyimpanan, dan koneksi wearable.
- Hormati preferensi reduced motion jika tersedia.
- Hindari animasi yang mengganggu saat pengguna sedang membaca data.

## 11. Aksesibilitas dan ergonomi

- Target sentuh minimal 48 × 48 dp untuk kontrol utama Android.
- Kontras teks normal minimal 4.5:1 sebagai sasaran.
- Jangan mengandalkan warna saja untuk zona, status, atau peringatan.
- Label ikon aksi harus tersedia untuk screen reader.
- Input harus memiliki label, error, dan bantuan yang jelas.
- Hindari teks terlalu kecil pada grafik.
- Periksa layout dengan ukuran font sistem yang diperbesar.
- Pastikan safe area dan bottom navigation tidak menutupi konten.

## 12. Batasan implementasi pada repository

1. Pertahankan arsitektur Android Kotlin + WebView lokal. Brief ini mengatur tampilan, bukan mengganti arsitektur.
2. Pertahankan seluruh fitur, alur data, perhitungan, integrasi Huawei/Honor, Intervals.icu, penyimpanan, dan JavaScript bridge.
3. Jangan menghapus atau mengganti fungsi JavaScript hanya demi merapikan tampilan.
4. Jangan mengubah nama RPC, skema data, key localStorage, atau kontrak Android bridge tanpa kebutuhan dan audit terpisah.
5. Lakukan refactor CSS/HTML bertahap; hindari rewrite satu file besar tanpa regression check.
6. Pastikan `index.html` dan `pengaturan.html` memakai token, tipografi, radius, spacing, dan komponen yang seragam.
7. Pertahankan semua tema yang sudah tersedia sebagai pilihan alternatif jika masih digunakan. Clean Light Athletic menjadi default visual baru; jangan menghapus theme system.
8. Pastikan perubahan tampilan tidak mengubah hasil analisis latihan atau rekomendasi coach.
9. Uji pada WebView Android, bukan hanya browser desktop.
10. Jangan menambahkan gambar eksternal yang wajib dimuat agar UI inti berfungsi.

## 13. Rencana implementasi

### Tahap 1 — Foundation
- Audit CSS yang sudah ada.
- Definisikan token light/dark.
- Satukan tipografi, spacing, radius, border, shadow, dan komponen dasar.
- Pastikan theme switching tetap berfungsi.

### Tahap 2 — Navigation dan shell
- Rapikan header, bottom navigation, sidebar desktop, safe area, dan kontainer halaman.
- Pastikan state tab dan tombol back tetap bekerja.

### Tahap 3 — Halaman prioritas
1. Beranda.
2. Aktivitas dan detail aktivitas.
3. Grafik/Analisis.
4. Rencana.
5. Tubuh/Wellness.
6. Pengaturan.

### Tahap 4 — Polish dan validasi
- Empty/loading/error states.
- Konsistensi ikon.
- Kontras dan ukuran sentuh.
- Dark theme.
- Responsiveness.
- Uji integrasi wearable dan navigasi Android.

## 14. Definition of Done

Desain dianggap selesai jika:

- Semua halaman memiliki bahasa visual yang konsisten.
- Beranda menjawab kondisi, rekomendasi, dan alasan dengan cepat.
- Angka penting terbaca dalam sekali lihat.
- Grafik tetap terbaca pada layar kecil.
- Light dan dark theme sama-sama matang.
- Bottom navigation dan tombol back Android bekerja.
- Tidak ada fitur, data, atau bridge yang rusak akibat perubahan visual.
- Tidak ada overflow horizontal pada ukuran layar ponsel umum.
- Status loading, kosong, error, offline, dan sinkronisasi memiliki tampilan yang jelas.
- Build APK berhasil dan alur utama telah diuji di perangkat Android.

---

## Ringkasan arahan untuk implementer

**Bangun ulang presentasi visual RunNow menjadi Clean Light Athletic: kanvas abu-biru yang sangat muda, kartu putih, tipografi Inter yang tegas, angka besar yang mudah dibaca, aksen biru yang hemat, grafik bersih, dan navigasi yang konsisten.** Terapkan sebagai design system lintas halaman, bukan skin yang hanya mempercantik Beranda. Pertahankan seluruh logika, data, integrasi, dan fitur RunNowNative.
