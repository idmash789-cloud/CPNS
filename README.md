# SiapSKD — Latihan Soal CPNS Harian

Aplikasi Android latihan SKD CPNS (TWK / TIU / TKP) — gratis, ringan, fokus lolos passing grade.
Model monetisasi: AdMob (banner, native, interstitial, rewarded).

> Nama "SiapSKD" adalah nama kerja (working title). Nama rilis di Play Store bisa diganti sebelum publish.

## Konsep inti
- **Pilihan soal harian 5/10/15/20/25/30**, ditampilkan **per bagian 5 soal dalam satu layar** (scroll vertikal) + tombol "Lanjut" antar-bagian; jeda antar-bagian = titik interstitial (pagar 180 detik).
- Skor per subtes + meter passing grade (TWK ≥65, TIU ≥80, TKP ≥166 — KepmenPANRB 321/2024).
- Pembahasan lengkap + trik cepat dibuka dengan rewarded ads (1 iklan membuka 1 paket sesi = 5 pembahasan).
- Tryout mingguan 55 soal & Tryout Full CAT 110 soal / 100 menit.

## Desain iklan (ringkasan — detail di `docs/ADS_DESIGN.md`)
| Format | Penempatan | Catatan keamanan |
|---|---|---|
| Banner (anchored adaptive) | Semua layar, slot tetap di bawah | Slot dicadangkan (layout tidak bergeser), diberi jarak dari tombol navigasi/"Lanjut" agar tidak salah klik |
| Native | Di tengah daftar soal sesi (setelah soal ke-2), kartu terpisah dalam scroll | WAJIB badge "Ad/Iklan" + ikon AdChoices terlihat jelas, tidak boleh menyamar sebagai soal |
| Interstitial | Setelah sesi 5 soal selesai, sebelum halaman hasil | Batas: maks 1x per sesi DAN jeda minimum antar-interstitial (Remote Config, default 180 detik); tidak di tombol Back, tidak saat aplikasi dibuka/ditutup |
| Rewarded | Tombol "Buka Pembahasan Lengkap" (per paket sesi) + fitur tambahan (Tryout Full ekstra, unduh PDF hasil, paket bonus) | Selalu opt-in pengguna; hadiah hanya berlaku di dalam aplikasi |

Semua penempatan dikontrol `AdConfig` (Remote Config-ready) supaya frekuensi bisa diubah tanpa update aplikasi.

## Versi Web (PWA) — folder `docs/`
Versi web penuh dari aplikasi yang sama (PWA: bisa di-install ke homescreen, jalan offline):
`docs/index.html` + `style.css` + `app.js` + `manifest.webmanifest` + `sw.js`, membaca bank soal
`docs/questions.json` + `docs/questions_batch2_*.json` (salinan file-file di
`app/src/main/assets/questions/` — sumber kebenaran tetap file Android; salinan web diperbarui tiap rilis).

**Aktivasi GitHub Pages (langkah pemilik repo, sekali saja):**
Settings → Pages → Build and deployment → Source: *Deploy from a branch* → Branch: `main`, folder: `/docs` → Save.
Situs live di `https://idmash789-cloud.github.io/CPNS/` (domain khusus bisa dipasang belakangan).

Perbedaan monetisasi web: **bukan AdMob** (khusus aplikasi) melainkan **AdSense** — slot iklan di web
saat ini berupa placeholder berlabel "Ruang Iklan" yang diganti kode AdSense setelah disetujui.
Fungsi web: menangkap pencarian Google ("latihan soal CPNS" dll) + corong install aplikasi Android.

## Teknologi
- Kotlin + Jetpack Compose (native Android, dibuka langsung di Android Studio)
- Google Mobile Ads SDK (AdMob) — selama development SELALU pakai ad unit TEST Google
- Bank soal: JSON lokal (offline) di `app/src/main/assets/questions/`
- Min SDK 24, Target/Compile SDK 35

## Cara build (Android Studio)
Lihat `docs/BUILD_ANDROID_STUDIO.md`.

## Status
Struktur proyek, layar Home (grid tombol + pilihan jumlah soal 5–30 per bagian 5 soal)/Sesi/Hasil/Pembahasan/Tryout, manajer iklan (kerangka, test ads).
Bank soal: **3.000 soal original tervalidasi** (TWK 900, TIU 1.000, TKP 1.100) — 600 soal batch 1–3 (13 file) + 2.400 soal batch 4 yang digabung per subtes menjadi `batch4_twk.json`/`batch4_tiu.json`/`batch4_tkp.json` (daftar lengkap di `QuestionBank.BANK_FILES` & `QUESTION_FILES` web).
Belum build-terverifikasi — kompilasi pertama dilakukan di Android Studio (mesin ini tidak punya JDK/Android SDK).
