# Desain Iklan SiapSKD — versi terkunci (Dwi, 22 Okt 2026) + pagar keamanan AdMob

Keputusan Dwi:
1. Banner hadir di semua sesi/layar — kecil di bawah, tidak mengganggu.
2. Interstitial per sesi — 1 sesi hanya 5 soal, jadi tiap selesai sesi adalah kesempatan jeda iklan.
3. Native di tengah soal — menyatu dengan scroll atas-bawah, soal tidak tertutupi.
4. Rewarded di pembahasan dan fitur tambahan.

Pagar keamanan (dari kebijakan resmi AdMob — wajib, bukan opsional):

## Banner
- Anchored adaptive banner di slot TETAP terbawah; ruang dicadangkan sejak awal agar konten tidak melompat saat iklan termuat (lompatan layout = klik tidak sengaja = invalid activity).
- Banner tidak boleh melayang menutupi konten, dan tidak boleh menempel pada elemen interaktif (tombol "Lanjut", menu navigasi) — kedekatan dengan tombol adalah penyebab utama klik tidak sengaja menurut panduan Google.
- Implementasi: urutan dari bawah = [slot banner] ← jarak 12dp → [konten / tombol aksi]. Navigasi utama memakai tab di atas atau di atas slot banner dengan pemisah jelas.
- Remote switch per layar (`banner_enabled_<screen>`) — kalau CTR satu layar tidak normal, banner layar itu bisa dimatikan tanpa update APK.

## Interstitial
- Google melarang interstitial setelah SETIAP aksi pengguna, di tombol Back, saat aplikasi dibuka/ditutup, dan berurutan tanpa jeda. Aturan praktis Google: maks 1 interstitial setelah setiap 2 aksi pengguna.
- Desain kita: 1x setelah pengguna MENYELESAIKAN sesi 5 soal (itu rangkaian banyak aksi, bukan 1 klik) dan SEBELUM halaman hasil tampil, didahului layar transisi singkat ("Menghitung skor…") agar tidak ada klik sisa yang mengenai iklan.
- Pagar tambahan: jeda minimum antar-interstitial 180 detik (Remote Config). Pengguna yang menyelesaikan sesi ke-2 dalam <180 detik tidak melihat interstitial lagi.
- Tidak ada interstitial di dalam Tryout Full CAT (simulasi harus bersih seperti ujian asli; banner tetap tampil sesuai keputusan, native & interstitial nonaktif selama tryout).

## Native
- Native BOLEH menyatu secara visual (font/warna senada), tapi DILARANG menyamar sebagai konten: wajib badge "Ad" (min. terlihat jelas) + ikon AdChoices, tidak boleh dikamuflasekan, dan kartu iklan harus terbedakan dari kartu soal (elevasi/bingkai berbeda + label "IKLAN" di dalam aplikasi berbahasa Indonesia).
- Penempatan: 1 kartu native per sesi, disisipkan setelah soal ke-2 dari 5 dalam daftar scroll. Tidak menutupi soal, tidak menggeser soal saat termuat (slot tinggi dicadangkan), area klik hanya pada elemen iklan resmi.
- Jangan jadikan native terlihat seperti soal ke-6 / pilihan jawaban — itu pelanggaran "ads disguised as content" dan bisa berujung pembatasan/penghentian monetisasi.

## Rewarded
- Selalu atas pilihan pengguna (opt-in) dengan label hadiah yang jelas sebelum iklan tampil: "Tonton iklan (±30 dtk) untuk membuka Pembahasan Lengkap sesi ini".
- Hadiah hanya berlaku di dalam aplikasi dan tidak bisa ditransfer/diubah jadi uang — sesuai kebijakan unit iklan berhadiah.
- Granularitas: 1 rewarded = membuka pembahasan lengkap 5 soal dalam 1 sesi (bukan 1 iklan per 1 soal — friksi terlalu tinggi, risiko review bintang 1).
- Kunci jawaban & skor tetap GRATIS tanpa iklan. Yang dikunci rewarded: pembahasan lengkap + trik cepat.
- Fitur tambahan ber-rewarded: Tryout Full ekstra di luar kuota mingguan, unduh PDF hasil tryout, paket soal bonus, ganti soal latihan (skip & replace).

## ID iklan
- Development: HANYA test ad unit resmi Google (sudah terpasang di `AdConfig.kt`).
- Production: buat 6 ad unit terpisah di AdMob (banner, native, interstitial, rewarded, + cadangan) agar performa tiap penempatan terbaca. App ID & ad unit production dipasang via `local.properties`/Remote Config, TIDAK di-hardcode di repo publik.
- JANGAN PERNAH mengklik iklan sendiri / meminta orang mengklik — invalid activity bisa membunuh akun AdMob permanen.
