# Blueprint MVP SiapSKD

## Layar
1. **Home** — streak harian, meter passing grade terakhir, tombol "Latihan Hari Ini", menu per subtes (TWK/TIU/TKP), Tryout.
2. **Sesi Latihan** — pengguna memilih jumlah soal harian: **5 / 10 / 15 / 20 / 25 / 30** (keputusan Dwi, 22 Okt 2026). Soal ditampilkan **per bagian: 5 soal dalam 1 layar** scroll vertikal; kartu native setelah soal ke-2 setiap bagian; di akhir bagian ada tombol **"Lanjut — 5 Soal Berikutnya"**, di bagian terakhir tombol "Selesai & Lihat Skor"; banner tetap di bawah. Jeda antar-bagian adalah titik interstitial (pagar jeda 180 detik tetap berlaku). Jika bank soal yang tersedia lebih sedikit dari pilihan, sesi memakai soal unik yang ada dan menampilkannya secara jujur kepada pengguna.
3. **Transisi skor** — layar "Menghitung skor…" ±1,2 detik → interstitial (jika lolos pagar frekuensi) → Hasil.
4. **Hasil Sesi** — skor per subtes, kunci jawaban gratis, tombol rewarded "Buka Pembahasan Lengkap".
5. **Pembahasan** — per soal: pembahasan 3–5 kalimat + kotak "Trik Cepat".
6. **Tryout** — Mingguan 55 soal; Full CAT 110 soal/100 menit (timer, tanpa native/interstitial di dalam, 1 rewarded untuk kuota ekstra).
7. **Profil/Statistik** — grafik skor per subtes, riwayat sesi, target formasi (opsional v1.1).

## Mesin skor (meniru SKD asli)
- TWK: 30 soal, benar 5 / salah-kosong 0, maks 150, PG 65.
- TIU: 35 soal, benar 5 / salah-kosong 0, maks 175, PG 80.
- TKP: 45 soal, tiap opsi bernilai 1–5 / kosong 0, maks 225, PG 166.
- Total maks 550. Lolos = ketiga PG terpenuhi sekaligus.
- Sesi 5 soal: skor ditampilkan sebagai estimasi proporsional per subtes yang dilatih.

## Bank soal
- Rilis awal: 600 soal original (TWK 180, TIU 200, TKP 220), format `questions/*.json`.
- Skema per soal: id, subtest, topic, difficulty (1–3), question, options[{text, score}], explanation, trick.
  - TWK/TIU: satu opsi score 5 (benar), sisanya 0.
  - TKP: kelima opsi score 1–5 (gradasi).
- Pipeline produksi: draft AI → verifikasi (TIU numerik dihitung ulang; TWK dicek sumber Pancasila/UUD 1945/kisi-kisi; TKP pola skor ditinjau) → masuk repo.
- Contoh format & kualitas: `app/src/main/assets/questions/sample_questions.json` (15 soal).

## Retensi
- Streak harian + pengingat lokal jam 19.30 (izin notifikasi runtime Android 13+).
- "Latihan Hari Ini" selalu campuran 3 subtes; sesi per subtes untuk mengejar kelemahan.
- Meter PG: label subtes terlemah "BAHAYA / TIPIS / AMAN".

## Roadmap build
- Tahap 1 (scaffold ini): proyek Android Studio, 3 layar kerangka, AdManager kerangka (test ads), 15 soal contoh.
- Tahap 2: mesin sesi + skor penuh, bank soal 150 pertama, hasil + pembahasan rewarded.
- Tahap 3: tryout engine + timer, streak + notifikasi, statistik.
- Tahap 4: bank soal menuju 600, ad unit production, closed testing Play Console, ASO (judul: "SiapSKD — Latihan Soal CPNS: TWK TIU TKP & Tryout SKD Gratis"), rilis.
- Ekspansi anti-musiman: paket PPPK, Sekolah Kedinasan, BUMN (mesin sama).
