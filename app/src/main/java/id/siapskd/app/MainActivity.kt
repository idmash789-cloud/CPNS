package id.siapskd.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun SiapSkdApp() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    var screen by remember { mutableStateOf(Screen.HOME) }
    var selectedCount by remember { mutableStateOf(AdConfig.DEFAULT_QUESTION_COUNT) }
    var sessionQuestions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var sessionSubtest by remember { mutableStateOf("CAMPURAN") }
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

    MaterialTheme {
        Scaffold(
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
                            val all = QuestionBank.loadSample(context)
                            sessionSubtest = subtest
                            sessionQuestions = QuestionBank.pickSession(all, subtest, selectedCount)
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
                        requestedCount = selectedCount,
                        answers = answers,
                        onAnswer = { qid, idx -> answers[qid] = idx },
                        isLastBatch = isLastBatch,
                        onPrimaryAction = {
                            if (isLastBatch) {
                                screen = Screen.SCORING
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
    Text("SiapSKD", style = MaterialTheme.typography.headlineMedium)
    Text("Latihan soal CPNS harian — fokus lolos passing grade SKD.")
    Spacer(Modifier.height(16.dp))

    Text("Mau belajar berapa soal hari ini?", style = MaterialTheme.typography.titleMedium)
    Text(
        "Soal ditampilkan per ${AdConfig.BATCH_SIZE} soal dalam satu layar — selesai satu bagian, ketuk Lanjut.",
        style = MaterialTheme.typography.bodySmall
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        AdConfig.COUNT_OPTIONS.forEach { count ->
            FilterChip(
                selected = selectedCount == count,
                onClick = { onCountChange(count) },
                label = { Text("$count soal") }
            )
        }
    }

    Button(onClick = { onStart("CAMPURAN") }, modifier = Modifier.fillMaxWidth()) {
        Text("Mulai Latihan Hari Ini ($selectedCount soal campuran)")
    }
    Spacer(Modifier.height(8.dp))
    listOf("TWK", "TIU", "TKP").forEach { sub ->
        OutlinedButton(onClick = { onStart(sub) }, modifier = Modifier.fillMaxWidth()) {
            Text("Latihan $sub saja ($selectedCount soal)")
        }
        Spacer(Modifier.height(8.dp))
    }
    Text(
        "Passing grade: TWK ≥ 65 • TIU ≥ 80 • TKP ≥ 166 (maks 550)",
        style = MaterialTheme.typography.bodySmall
    )
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
    onPrimaryAction: () -> Unit
) {
    // Satu layar = satu bagian (AdConfig.BATCH_SIZE soal) dalam daftar scroll.
    // Native disisipkan setelah soal ke-2 SETIAP bagian.
    Column(modifier = Modifier.fillMaxSize()) {
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
                if (index + 1 == AdConfig.NATIVE_AFTER_QUESTION) {
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
    onOpenExplanation: () -> Unit,
    onHome: () -> Unit
) {
    val earned = questions.sumOf { q -> answers[q.id]?.let { q.options[it].score } ?: 0 }
    val max = questions.sumOf { it.maxScore }
    Text("Hasil Sesi", style = MaterialTheme.typography.headlineMedium)
    Text("Skor sesi ini: $earned / $max (${questions.size} soal)")
    questions.forEach { q ->
        val idx = answers[q.id]
        val got = idx?.let { q.options[it].score } ?: 0
        Text("• ${q.id}: kunci ${'A' + q.options.indexOfFirst { it.score == q.maxScore }} — kamu $got poin")
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = onOpenExplanation, modifier = Modifier.fillMaxWidth()) {
        Text("Tonton Iklan — Buka Pembahasan Lengkap + Trik")
    }
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
