# Build di Android Studio

1. Clone repo ini, buka folder proyek di Android Studio (versi Ladybug atau lebih baru disarankan).
2. Android Studio akan menawarkan upgrade Android Gradle Plugin — terima jika diminta.
3. Buat file `local.properties` (otomatis dibuat Android Studio) — JANGAN commit file ini.
4. Jalankan di emulator / HP via USB debugging. Selama development aplikasi memakai
   App ID + ad unit TEST resmi Google (lihat `AdConfig.kt`), jadi iklan contoh aman diklik untuk uji UI.
5. Build APK debug: menu **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
6. Build rilis Play Store: **Build → Generate Signed App Bundle** (.aab) dengan keystore milik sendiri.
   Simpan keystore + password di tempat aman (kehilangan keystore = tidak bisa update aplikasi).

## Sebelum rilis (production)
- Ganti App ID AdMob di `AndroidManifest.xml` dengan App ID production.
- Isi ad unit production di `AdConfig.kt` dari konstanta production (jangan commit ID asli ke repo publik — pakai `local.properties` / BuildConfig field).
- Tambahkan halaman Privacy Policy (wajib Play Store untuk aplikasi beriklan) + formulir Data Safety.
- Uji di perangkat asli: banner tidak menutupi tombol, interstitial tidak muncul <180 detik dua kali, native berlabel "Ad" jelas.
