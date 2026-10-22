package id.siapskd.app.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Banner anchored adaptive di slot tetap terbawah.
 * Aturan layout (docs/ADS_DESIGN.md):
 *  - Ruang banner DICADANGKAN oleh pemanggil (Scaffold bottomBar) agar konten tidak melompat.
 *  - Diberi jarak dari elemen interaktif (tombol/navigasi) untuk mencegah klik tidak sengaja.
 *  - Tidak melayang menutupi konten.
 */
@Composable
fun BannerAdSlot(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, AdSize.FULL_WIDTH))
                adUnitId = AdConfig.bannerUnitId
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
