# SiapSKD — Latihan Soal CPNS Harian

Aplikasi Android latihan SKD CPNS (TWK / TIU / TKP) — gratis, ringan, fokus lolos passing grade.
Model monetisasi: AdMob (banner, native, interstitial, rewarded).

> Nama "SiapSKD" adalah nama kerja (working title). Nama rilis di Play Store bisa diganti sebelum publish.

## Konsep inti
- **Sesi latihan = 5 soal** (scroll vertikal), selesai ±5 menit.
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

## Teknologi
- Kotlin + Jetpack Compose (native Android, dibuka langsung di Android Studio)
- Google Mobile Ads SDK (AdMob) — selama development SELALU pakai ad unit TEST Google
- Bank soal: JSON lokal (offline) di `app/src/main/assets/questions/`
- Min SDK 24, Target/Compile SDK 35

## Cara build (Android Studio)
Lihat `docs/BUILD_ANDROID_STUDIO.md`.

## Status
Scaffold tahap 1: struktur proyek, layar Home/Sesi/Hasil (kerangka), manajer iklan (kerangka, test ads),
dan 15 soal contoh original (5 TWK, 5 TIU, 5 TKP) di `sample_questions.json`.
Belum build-terverifikasi — kompilasi pertama dilakukan di Android Studio (mesin ini tidak punya JDK/Android SDK).
