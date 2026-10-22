package id.siapskd.app.ads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Kartu Native untuk disisipkan di tengah daftar soal sesi.
 *
 * WAJIB sesuai kebijakan AdMob (docs/ADS_DESIGN.md):
 *  - Label "IKLAN" terlihat jelas + badge Ad & ikon AdChoices dari SDK tidak boleh dikamuflase.
 *  - Desain terbedakan dari kartu soal (elevasi/bingkai berbeda) — DILARANG menyamar sebagai soal.
 *  - Slot tinggi dicadangkan agar daftar soal tidak bergeser saat iklan termuat.
 *
 * Tahap scaffold: placeholder UI. Tahap 2: ikat ke NativeAdView (AdMob Native Advanced)
 * dengan template medium, headline + media + CTA sesuai aset yang diterima dari SDK.
 */
@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                Text(
                    text = "IKLAN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "AdChoices",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "[Slot Native Ad — konten iklan tampil di sini]",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
