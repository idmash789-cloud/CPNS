package id.siapskd.app.ads

/**
 * Konfigurasi iklan SiapSKD.
 * Selama development: TEST ad unit resmi Google (aman, tidak menghasilkan uang).
 * Production: ganti dengan ad unit asli dari konsol AdMob — jangan commit ID asli ke repo publik,
 * idealnya dibaca dari BuildConfig/Remote Config.
 *
 * Pagar frekuensi juga tinggal di sini (nanti dipindah ke Firebase Remote Config):
 *  - interstitialMinIntervalMs: jeda minimum antar-interstitial.
 *  - Semua penempatan bisa dimatikan per layar tanpa update APK (saat Remote Config aktif).
 */
object AdConfig {
    // TEST units (Google official sample IDs)
    const val BANNER_TEST = "ca-app-pub-3940256099942544/6300978111"
    const val NATIVE_TEST = "ca-app-pub-3940256099942544/2247696110"
    const val INTERSTITIAL_TEST = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED_TEST = "ca-app-pub-3940256099942544/5224354917"

    var bannerUnitId: String = BANNER_TEST
    var nativeUnitId: String = NATIVE_TEST
    var interstitialUnitId: String = INTERSTITIAL_TEST
    var rewardedUnitId: String = REWARDED_TEST

    /** Jeda minimum antar-interstitial: 180 detik (keputusan desain, lihat docs/ADS_DESIGN.md). */
    const val INTERSTITIAL_MIN_INTERVAL_MS: Long = 180_000L

    /** Native disisipkan setelah soal ke-N dalam daftar sesi (1-based). */
    const val NATIVE_AFTER_QUESTION: Int = 2

    /** Jumlah soal per sesi latihan. */
    const val QUESTIONS_PER_SESSION: Int = 5
}
