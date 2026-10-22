package id.siapskd.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Rewarded SiapSKD — selalu opt-in pengguna.
 * Hadiah yang sah (hanya berlaku di dalam aplikasi):
 *  - Membuka Pembahasan Lengkap 1 paket sesi (5 soal).
 *  - Kuota Tryout Full ekstra, unduh PDF hasil, paket soal bonus.
 */
object RewardedManager {
    private var rewardedAd: RewardedAd? = null

    fun preload(context: Context) {
        RewardedAd.load(
            context,
            AdConfig.rewardedUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
                override fun onAdFailedToLoad(error: LoadAdError) { rewardedAd = null }
            }
        )
    }

    fun isReady(): Boolean = rewardedAd != null

    /** Tampilkan rewarded; onReward hanya dipanggil jika pengguna menonton sampai hadiah earned. */
    fun show(activity: Activity, onReward: () -> Unit, onUnavailable: () -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            preload(activity)
            onUnavailable()
            return
        }
        rewardedAd = null
        ad.show(activity) { _ -> onReward() }
        preload(activity)
    }
}
