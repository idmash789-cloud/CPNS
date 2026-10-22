package id.siapskd.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.ads.MobileAds
import id.siapskd.app.ads.AdConfig
import id.siapskd.app.ads.BannerAdSlot
import id.siapskd.app.ads.InterstitialManager
import id.siapskd.app.ads.NativeAdCard
import id.siapskd.app.ads.RewardedManager
import id.siapskd.app.data.Question
import id.siapskd.app.data.QuestionBank
import kotlinx.coroutines.delay

/**
 * SiapSKD — scaffold tahap 1 (diperbarui: pilihan jumlah soal + bagian per 5 soal).
 * Alur: Home (pilih jumlah soal 5/10/15/20/25/30) -> Sesi per BAGIAN 5 soal + tombol "Lanjut"
 *        (jeda antar-bagian = titik interstitial, dibatasi pagar 180 detik)
 *        -> Transisi skor -> (interstitial bila lolos pagar) -> Hasil -> Pembahasan (rewarded).
 * Banner tampil di SEMUA layar lewat Scaffold.bottomBar (slot tetap, lihat docs/ADS_DESIGN.md).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this) {}
        InterstitialManager.preload(this)
        RewardedManager.preload(this)
        setContent { SiapSkdApp() }
    }
}

private enum class Screen { HOME, SESSION, SCORING, RESULT, EXPLANATION }

/** Bar atas bermerek — mengeklik logo/nama selalu kembali ke Home (keputusan Dwi, 22 Okt 2026). */
@Composable
private fun BrandBar(onHome: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onHome() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text("S", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("SiapSKD", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Latihan Soal CPNS Harian", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SiapSkdApp() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    var screen by remember { mutableStateOf(Screen.HOME) }
    var selectedCount by remember { mutableStateOf(AdConfig.DEFAULT_QUESTION_COUNT) }
    var sessionQuestions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var sessionSubtest by remember { mutableStateOf("CAMPURAN") }
    var sessionMode by remember { mutableStateOf("latihan") } // "latihan" | "tryout"
    var timeLeftSec by remember { mutableStateOf(0) }
    var batchIndex by remember { mutableStateOf(0) }
    var explanationUnlocked by remember { mutableStateOf(false) }
    val answers = remember { mutableStateMapOf<String, Int>() }

    val batchCount = if (sessionQuestions.isEmpty()) 1
        else (sessionQuestions.size + AdConfig.BATCH_SIZE - 1) / AdConfig.BATCH_SIZE
    val batchQuestions = sessionQuestions.drop(batchIndex * AdConfig.BATCH_SIZE).take(AdConfig.BATCH_SIZE)
    val isLastBatch = batchIndex >= batchCount - 1

    LaunchedEffect(screen) {
        if (screen == Screen.SCORING) {
            delay(1200) // layar transisi "Menghitung skor…" — mencegah klik sisa mengenai interstitial
            InterstitialManager.showAfterSession(activity) { screen = Screen.RESULT }
        }
    }

    // Timer tryout: hitung mundur; waktu habis = serahkan sesi otomatis.
    LaunchedEffect(screen == Screen.SESSION && sessionMode == "tryout") {
        if (screen == Screen.SESSION && sessionMode == "tryout") {
            while (timeLeftSec > 0) { delay(1000); timeLeftSec-- }
            if (timeLeftSec <= 0) screen = Screen.SCORING
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = { BrandBar(onHome = { screen = Screen.HOME }) }, // logo = tombol Home
            bottomBar = { BannerAdSlot() } // banner di semua sesi/layar, slot tetap di bawah
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        selectedCount = selectedCount,
                        onCountChange = { selectedCount = it },
                        onStart = { subtest ->
                            val all = QuestionBank.loadAll(context)
                            sessionSubtest = subtest
                            if (subtest == "TRYOUT_SKD") {
                                sessionMode = "tryout"
                                sessionQuestions = QuestionBank.pickTryoutSKD(all)
                                timeLeftSec = 100 * 60
                            } else {
                                sessionMode = "latihan"
                                sessionQuestions = QuestionBank.pickSession(all, subtest, selectedCount)
                            }
                            batchIndex = 0
                            answers.clear()
                            explanationUnlocked = false
                            screen = Screen.SESSION
                        }
                    )
                    Screen.SESSION -> SessionScreen(
                        batchQuestions = batchQuestions,
                        globalOffset = batchIndex * AdConfig.BATCH_SIZE,
                        batchIndex = batchIndex,
                        batchCount = batchCount,
                        totalQuestions = sessionQuestions.size,
                        requestedCount = if (sessionMode == "tryout") sessionQuestions.size else selectedCount,
                        answers = answers,
                        onAnswer = { qid, idx -> answers[qid] = idx },
                        isLastBatch = isLastBatch,
                        isTryout = sessionMode == "tryout",
                        timeLeftSec = timeLeftSec,
                        onPrimaryAction = {
                            if (isLastBatch) {
                                screen = Screen.SCORING
                            } else if (sessionMode == "tryout") {
                                batchIndex++ // tryout: tanpa interstitial di tengah ujian
                            } else {
                                // Jeda antar-bagian: titik interstitial (pagar 180 dtk di InterstitialManager)
                                InterstitialManager.showAfterSession(activity) { batchIndex++ }
                            }
                        }
                    )
                    Screen.SCORING -> Text("Menghitung skor…", style = MaterialTheme.typography.headlineSmall)
                    Screen.RESULT -> ResultScreen(
                        questions = sessionQuestions,
                        answers = answers,
                        isTryoutSKD = sessionSubtest == "TRYOUT_SKD",
                        onOpenExplanation = {
                            if (explanationUnlocked) {
                                screen = Screen.EXPLANATION
                            } else {
                                RewardedManager.show(
                                    activity,
                                    onReward = { explanationUnlocked = true; screen = Screen.EXPLANATION },
                                    onUnavailable = { /* iklan belum siap: beri tahu pengguna / fallback tahap 2 */ }
                                )
                            }
                        },
                        onHome = { screen = Screen.HOME }
                    )
                    Screen.EXPLANATION -> ExplanationScreen(
                        questions = sessionQuestions,
                        answers = answers,
                        onHome = { screen = Screen.HOME }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(
    selectedCount: Int,
    onCountChange: (Int) -> Unit,
    onStart: (String) -> Unit
) {
    // Home profesional & ringkas: pemilih target + kotak tombol (grid menu), minim teks.
    Column(modifier = Modifier.fillMaxSize()) {
    Text("SiapSKD", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(12.dp))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Target Hari Ini", style = MaterialTheme.typography.titleMedium)
                Text("per ${AdConfig.BATCH_SIZE} soal / layar", style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                AdConfig.COUNT_OPTIONS.forEach { count ->
                    FilterChip(
                        selected = selectedCount == count,
                        onClick = { onCountChange(count) },
                        label = { Text("$count") }
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.weight(1f)
    ) {
        item(span = { GridItemSpan(2) }) {
            MenuTile(
                icon = "▶️",
                title = "Latihan Campuran",
                subtitle = "TWK • TIU • TKP — $selectedCount soal",
                primary = true,
                onClick = { onStart("CAMPURAN") }
            )
        }
        item {
            MenuTile(icon = "🏛️", title = "TWK", subtitle = "Wawasan Kebangsaan", onClick = { onStart("TWK") })
        }
        item {
            MenuTile(icon = "🧮", title = "TIU", subtitle = "Intelegensi Umum", onClick = { onStart("TIU") })
        }
        item {
            MenuTile(icon = "🤝", title = "TKP", subtitle = "Karakteristik Pribadi", onClick = { onStart("TKP") })
        }
        item(span = { GridItemSpan(2) }) {
            MenuTile(
                icon = "🏆",
                title = "Tryout SKD Resmi",
                subtitle = "110 soal • 100 menit • format CAT: TWK 30, TIU 35, TKP 45",
                onClick = { onStart("TRYOUT_SKD") }
            )
        }
        item(span = { GridItemSpan(2) }) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Passing Grade — TWK 65 • TIU 80 • TKP 166",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
    }
}

@Composable
private fun MenuTile(
    icon: String,
    title: String,
    subtitle: String,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (primary) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(icon, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SessionScreen(
    batchQuestions: List<Question>,
    globalOffset: Int,
    batchIndex: Int,
    batchCount: Int,
    totalQuestions: Int,
    requestedCount: Int,
    answers: Map<String, Int>,
    onAnswer: (String, Int) -> Unit,
    isLastBatch: Boolean,
    isTryout: Boolean = false,
    timeLeftSec: Int = 0,
    onPrimaryAction: () -> Unit
) {
    // Satu layar = satu bagian (AdConfig.BATCH_SIZE soal) dalam daftar scroll.
    // Native disisipkan setelah soal ke-2 SETIAP bagian (hanya mode latihan; tryout tanpa iklan di tengah).
    Column(modifier = Modifier.fillMaxSize()) {
        if (isTryout) {
            Text(
                "⏱ Sisa waktu: ${timeLeftSec / 60}:${(timeLeftSec % 60).toString().padStart(2, '0')}",
                style = MaterialTheme.typography.titleMedium
            )
        }
        if (batchCount > 1) {
            Text(
                "Bagian ${batchIndex + 1} dari $batchCount • Soal ${globalOffset + 1}–${globalOffset + batchQuestions.size} dari $totalQuestions",
                style = MaterialTheme.typography.titleSmall
            )
        }
        if (totalQuestions < requestedCount) {
            Text(
                "Catatan: bank soal contoh baru menyediakan $totalQuestions soal unik — sesi memakai jumlah itu. Pilihan $requestedCount soal berlaku penuh setelah bank soal lengkap masuk.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(batchQuestions) { index, q ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${q.subtest} • ${q.topic}", style = MaterialTheme.typography.labelMedium)
                        Text("${globalOffset + index + 1}. ${q.question}", style = MaterialTheme.typography.bodyLarge)
                        q.options.forEachIndexed { optIdx, opt ->
                            RowAnswer(
                                text = "${'A' + optIdx}. ${opt.text}",
                                selected = answers[q.id] == optIdx,
                                onClick = { onAnswer(q.id, optIdx) }
                            )
                        }
                    }
                }
                if (!isTryout && index + 1 == AdConfig.NATIVE_AFTER_QUESTION) {
                    NativeAdCard()
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onPrimaryAction,
            enabled = batchQuestions.all { answers.containsKey(it.id) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLastBatch) "Selesai & Lihat Skor" else "Lanjut — ${AdConfig.BATCH_SIZE} Soal Berikutnya")
        }
    }
}

@Composable
private fun RowAnswer(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ResultScreen(
    questions: List<Question>,
    answers: Map<String, Int>,
    isTryoutSKD: Boolean = false,
    onOpenExplanation: () -> Unit,
    onHome: () -> Unit
) {
    val context = LocalContext.current
    val earned = questions.sumOf { q -> answers[q.id]?.let { q.options[it].score } ?: 0 }
    val max = questions.sumOf { it.maxScore }
    Text("Hasil Sesi", style = MaterialTheme.typography.headlineMedium)
    Text("Skor sesi ini: $earned / $max (${questions.size} soal)")
    if (isTryoutSKD) {
        // Komposisi resmi: poin per subtes sudah skala SKD asli — bandingkan langsung ke passing grade.
        var allPass = true
        listOf("TWK", "TIU", "TKP").forEach { sub ->
            val got = questions.filter { it.subtest == sub }
                .sumOf { q -> answers[q.id]?.let { q.options[it].score } ?: 0 }
            val pg = QuestionBank.PASSING_GRADE[sub] ?: 0
            if (got < pg) allPass = false
            Text("$sub: $got (PG $pg) — ${if (got >= pg) "✅ LOLOS PG" else "❌ di bawah PG"}")
        }
        Text(if (allPass) "🏆 Ketiga subtes lolos passing grade!" else "Belum lolos PG di semua subtes — kejar subtes bertanda ❌.")
    }
    questions.forEach { q ->
        val idx = answers[q.id]
        val got = idx?.let { q.options[it].score } ?: 0
        Text("• ${q.id}: kunci ${'A' + q.options.indexOfFirst { it.score == q.maxScore }} — kamu $got poin")
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = onOpenExplanation, modifier = Modifier.fillMaxWidth()) {
        Text("Tonton Iklan — Buka Pembahasan Lengkap + Trik")
    }
    OutlinedButton(
        onClick = {
            val text = "Latihan SKD di SiapSKD: skorku $earned/$max (${questions.size} soal). Latihan soal CPNS gratis tiap hari di https://cpns.techo.web.id/"
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, text)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Bagikan hasil"))
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("📤 Bagikan Hasil") }
    OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Kembali ke Beranda") }
}

@Composable
private fun ExplanationScreen(
    questions: List<Question>,
    answers: Map<String, Int>,
    onHome: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(questions) { _, q ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${q.id} — ${q.topic}", style = MaterialTheme.typography.labelMedium)
                    Text(q.question, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                    Text("Pembahasan: ${q.explanation}")
                    Text("Trik Cepat: ${q.trick}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Button(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Selesai") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
