package id.siapskd.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Interstitial SiapSKD — hanya tampil:
 *  1. Di jeda antar-bagian (pengguna mengetuk "Lanjut" setelah menyelesaikan 5 soal)
 *     dan setelah bagian terakhir, sebelum halaman hasil.
 *  2. Jika jeda sejak interstitial terakhir >= AdConfig.INTERSTITIAL_MIN_INTERVAL_MS
 *     (pagar ini yang membatasi frekuensi nyata meski sesi panjang punya banyak jeda).
 * Tidak pernah: saat app dibuka/ditutup, di tombol Back, atau berurutan.
 */
object InterstitialManager {
    private var interstitialAd: InterstitialAd? = null
    private var lastShownAtMs: Long = 0L

    fun preload(context: Context) {
        InterstitialAd.load(
            context,
            AdConfig.interstitialUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitialAd = ad }
                override fun onAdFailedToLoad(error: LoadAdError) { interstitialAd = null }
            }
        )
    }

    /** @return true jika iklan ditampilkan; panggil onContinue SELALU (iklan tampil ataupun tidak). */
    fun showAfterSession(activity: Activity, onContinue: () -> Unit) {
        val now = System.currentTimeMillis()
        val ad = interstitialAd
        if (ad == null || now - lastShownAtMs < AdConfig.INTERSTITIAL_MIN_INTERVAL_MS) {
            onContinue()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                lastShownAtMs = System.currentTimeMillis()
                preload(activity)
                onContinue()
            }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                onContinue()
            }
        }
        interstitialAd = null
        ad.show(activity)
    }
}
