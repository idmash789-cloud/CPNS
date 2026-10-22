package id.siapskd.app.data

import android.content.Context
import org.json.JSONObject

/** Satu opsi jawaban. Untuk TWK/TIU: benar = 5, salah = 0. Untuk TKP: gradasi 1–5. */
data class Option(val text: String, val score: Int)

/**
 * Soal SKD. Skema sama dengan file JSON di assets/questions/.
 * subtest: "TWK" | "TIU" | "TKP"; difficulty: 1–3.
 */
data class Question(
    val id: String,
    val subtest: String,
    val topic: String,
    val difficulty: Int,
    val question: String,
    val options: List<Option>,
    val explanation: String,
    val trick: String
) {
    val maxScore: Int get() = options.maxOf { it.score }
}

object QuestionBank {
    /** Muat bank soal dari assets/questions/sample_questions.json (tahap 2: gabung semua file di folder itu). */
    fun loadSample(context: Context): List<Question> {
        val raw = context.assets.open("questions/sample_questions.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(raw).getJSONArray("questions")
        val result = mutableListOf<Question>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val opts = o.getJSONArray("options")
            val options = (0 until opts.length()).map { j ->
                val op = opts.getJSONObject(j)
                Option(op.getString("text"), op.getInt("score"))
            }
            result += Question(
                id = o.getString("id"),
                subtest = o.getString("subtest"),
                topic = o.getString("topic"),
                difficulty = o.getInt("difficulty"),
                question = o.getString("question"),
                options = options,
                explanation = o.getString("explanation"),
                trick = o.getString("trick")
            )
        }
        return result
    }

    /** Ambil 1 sesi: [count] soal dari satu subtes (atau campuran bila subtest = "CAMPURAN"). */
    fun pickSession(all: List<Question>, subtest: String, count: Int = 5): List<Question> {
        val pool = if (subtest == "CAMPURAN") all else all.filter { it.subtest == subtest }
        return pool.shuffled().take(count)
    }

    /** Passing grade SKD (KepmenPANRB 321/2024). */
    val PASSING_GRADE = mapOf("TWK" to 65, "TIU" to 80, "TKP" to 166)
    val MAX_SCORE = mapOf("TWK" to 150, "TIU" to 175, "TKP" to 225)
}
