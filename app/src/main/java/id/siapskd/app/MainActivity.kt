package id.siapskd.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
 * SiapSKD — scaffold tahap 1.
 * Alur: Home -> Sesi (5 soal, scroll, native setelah soal ke-2) -> Transisi skor
 *        -> (interstitial, jika lolos pagar frekuensi) -> Hasil -> Pembahasan (rewarded).
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
    var sessionQuestions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var explanationUnlocked by remember { mutableStateOf(false) }
    val answers = remember { mutableStateMapOf<String, Int>() }

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
                        onStart = { subtest ->
                            val all = QuestionBank.loadSample(context)
                            sessionQuestions = QuestionBank.pickSession(all, subtest, AdConfig.QUESTIONS_PER_SESSION)
                            answers.clear()
                            explanationUnlocked = false
                            screen = Screen.SESSION
                        }
                    )
                    Screen.SESSION -> SessionScreen(
                        questions = sessionQuestions,
                        answers = answers,
                        onAnswer = { qid, idx -> answers[qid] = idx },
                        onFinish = { screen = Screen.SCORING }
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

@Composable
private fun HomeScreen(onStart: (String) -> Unit) {
    Text("SiapSKD", style = MaterialTheme.typography.headlineMedium)
    Text("Latihan soal CPNS harian — fokus lolos passing grade SKD.")
    Spacer(Modifier.height(16.dp))
    Button(onClick = { onStart("CAMPURAN") }, modifier = Modifier.fillMaxWidth()) {
        Text("Mulai Latihan Hari Ini (5 soal campuran)")
    }
    Spacer(Modifier.height(8.dp))
    listOf("TWK", "TIU", "TKP").forEach { sub ->
        OutlinedButton(onClick = { onStart(sub) }, modifier = Modifier.fillMaxWidth()) {
            Text("Latihan $sub saja")
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
    questions: List<Question>,
    answers: Map<String, Int>,
    onAnswer: (String, Int) -> Unit,
    onFinish: () -> Unit
) {
    // Daftar soal SCROLL vertikal — native disisipkan setelah soal ke-2 (AdConfig.NATIVE_AFTER_QUESTION).
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(questions) { index, q ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${q.subtest} • ${q.topic}", style = MaterialTheme.typography.labelMedium)
                    Text("${index + 1}. ${q.question}", style = MaterialTheme.typography.bodyLarge)
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
        item {
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onFinish,
                enabled = questions.all { answers.containsKey(it.id) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Selesai & Lihat Skor") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RowAnswer(text: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
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
    Text("Skor sesi ini: $earned / $max")
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
