/* SiapSKD Web (PWA) — mesin latihan yang sama dengan versi Android:
   sesi 5 soal (scroll), native slot setelah soal ke-2, hasil + kunci gratis,
   pembahasan lengkap sebagai konten premium (di Android: rewarded; di web: slot AdSense nanti).
   Sumber soal: questions.json + questions_batch2_*.json — SALINAN file-file di app/src/main/assets/questions/
   (sumber kebenaran tetap file Android; salinan web diperbarui tiap rilis). */
'use strict';

const BATCH_SIZE = 5; // soal per layar — sesi panjang berjalan per bagian 5 soal + tombol Lanjut
const COUNT_OPTIONS = [5, 10, 15, 20, 25, 30]; // pilihan jumlah soal harian (keputusan Dwi, 22 Okt 2026)
const NATIVE_AFTER_QUESTION = 2; // tampilkan slot native setelah soal ke-N dalam satu bagian
const SECONDS_PER_QUESTION = 55; // ritme ujian asli: 110 soal / 100 menit
const PASSING_GRADE = { TWK: 65, TIU: 80, TKP: 166 };
const MAX_SCORE = { TWK: 150, TIU: 175, TKP: 225 };
const LS = { streak: 'siapskd_streak', history: 'siapskd_history', count: 'siapskd_count', wrong: 'siapskd_wrong', theme: 'siapskd_theme' };
const SKD_COMPOSITION = { TWK: 30, TIU: 35, TKP: 45 }; // format CAT resmi: 110 soal
const SKD_MINUTES = 100;

let allQuestions = [];
let sessionQuestions = [];
let sessionMode = 'latihan'; // 'latihan' | 'tryout'
let sessionSubtest = 'CAMPURAN';
let chosenCount = parseInt(localStorage.getItem(LS.count) || '5', 10);
if (!COUNT_OPTIONS.includes(chosenCount)) chosenCount = 5;
let batchIndex = 0; // bagian (halaman 5 soal) yang sedang ditampilkan
let answersMap = {}; // jawaban seluruh sesi, bertahan antar-bagian
let timerId = null;
let timeLeft = 0;

/* ---------- util ---------- */
const $ = (id) => document.getElementById(id);
const shuffle = (arr) => arr.map((v) => [Math.random(), v]).sort((a, b) => a[0] - b[0]).map((p) => p[1]);
const todayKey = () => new Date().toISOString().slice(0, 10);
function loadJSON(key, fallback) { try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; } }
function saveJSON(key, value) { localStorage.setItem(key, JSON.stringify(value)); }
function esc(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }

/* ---------- streak & riwayat ---------- */
function markTodayDone() {
  const s = loadJSON(LS.streak, { count: 0, last: null, prev: null });
  const today = todayKey();
  if (s.last === today) return s;
  const yesterday = new Date(Date.now() - 86400000).toISOString().slice(0, 10);
  s.count = (s.last === yesterday) ? s.count + 1 : 1;
  s.prev = s.last; s.last = today;
  saveJSON(LS.streak, s);
  return s;
}
function getHistory() { return loadJSON(LS.history, []); }
function perSubtestEstimates() {
  // Estimasi proporsional: rata-rata (skor diperoleh / skor maks sesi) per subtes, diskalakan ke maks SKD.
  const agg = {};
  getHistory().forEach((h) => {
    Object.entries(h.per || {}).forEach(([sub, v]) => {
      agg[sub] = agg[sub] || { got: 0, max: 0 };
      agg[sub].got += v.got; agg[sub].max += v.max;
    });
  });
  const out = {};
  Object.entries(agg).forEach(([sub, v]) => {
    if (v.max > 0) out[sub] = Math.round((v.got / v.max) * MAX_SCORE[sub]);
  });
  return out;
}

/* ---------- navigasi layar ---------- */
function show(screen) {
  ['home', 'session', 'result', 'explanation', 'stats'].forEach((s) => {
    $('screen-' + s).hidden = s !== screen;
  });
  window.scrollTo({ top: 0 });
}

/* ---------- home ---------- */
function renderHome() {
  const s = loadJSON(LS.streak, { count: 0, last: null });
  const hist = getHistory();
  $('stat-streak').textContent = s.last ? s.count : 0;
  $('stat-sessions').textContent = hist.length;
  $('stat-questions').textContent = hist.reduce((a, h) => a + h.count, 0);
  const wrongCount = loadJSON(LS.wrong, []).length;
  $('wrong-sub').textContent = wrongCount ? `${wrongCount} soal perlu diulang` : 'Belum ada — bagus!';

  const est = perSubtestEstimates();
  const meter = $('pg-meter');
  if (!Object.keys(est).length) {
    meter.innerHTML = '<p class="muted">Belum ada data. Selesaikan 1 sesi untuk melihat estimasi skormu.</p>';
    return;
  }
  meter.innerHTML = ['TWK', 'TIU', 'TKP'].map((sub) => {
    if (est[sub] == null) return `<div class="pg-row"><div class="pg-head"><span>${sub}</span><span class="muted">belum ada data</span></div></div>`;
    const pg = PASSING_GRADE[sub];
    const pct = Math.min(100, Math.round((est[sub] / MAX_SCORE[sub]) * 100));
    const status = est[sub] >= pg ? 'AMAN' : (est[sub] >= pg * 0.85 ? 'TIPIS' : 'BAHAYA');
    const cls = status === 'AMAN' ? '' : (status === 'TIPIS' ? 'warn' : 'bad');
    return `<div class="pg-row">
      <div class="pg-head"><span><strong>${sub}</strong> — estimasi ${est[sub]} / ${MAX_SCORE[sub]} (PG ${pg})</span><span class="pg-tag ${cls}">${status}</span></div>
      <div class="pg-bar"><div class="pg-fill ${cls}" style="width:${pct}%"></div></div>
    </div>`;
  }).join('');
}

/* ---------- pemilih jumlah soal ---------- */
function poolSizeFor(subtest) {
  if (subtest === 'CAMPURAN') return allQuestions.length;
  return allQuestions.filter((q) => q.subtest === subtest).length;
}
function renderCountPicker() {
  document.querySelectorAll('.count-chip').forEach((chip) => {
    chip.classList.toggle('active', parseInt(chip.dataset.count, 10) === chosenCount);
  });
  const total = poolSizeFor('CAMPURAN');
  $('count-note').textContent = chosenCount > total && total > 0
    ? `Bank soal saat ini berisi ${total} soal contoh — sesi akan memakai ${total} soal unik yang tersedia. Pilihan ${chosenCount} soal berlaku penuh setelah bank soal lengkap (600 soal) masuk.`
    : '';
}

/* ---------- sesi ---------- */
function pickSession(subtest, count) {
  if (subtest === 'TRYOUT') return shuffle(allQuestions).slice(0, 150); // sampel acak 150 dari bank (bank kini 3.000 soal — semua soal sekaligus tidak realistis dikerjakan)
  if (subtest === 'TRYOUT_SKD') {
    // Komposisi resmi CAT BKN: TWK 30, TIU 35, TKP 45 — acak di dalam subtes, urutan subtes tetap.
    return Object.entries(SKD_COMPOSITION).flatMap(([sub, n]) =>
      shuffle(allQuestions.filter((q) => q.subtest === sub)).slice(0, n));
  }
  if (subtest === 'SALAH') {
    const wrong = new Set(loadJSON(LS.wrong, []));
    return shuffle(allQuestions.filter((q) => wrong.has(q.id))).slice(0, 30);
  }
  if (subtest === 'CAMPURAN') {
    // campuran seimbang antar subtes yang tersedia
    const groups = ['TWK', 'TIU', 'TKP'].map((s) => shuffle(allQuestions.filter((q) => q.subtest === s)));
    const picked = [];
    let i = 0;
    while (picked.length < count && groups.some((g) => g.length)) {
      const g = groups[i % groups.length];
      if (g.length) picked.push(g.pop());
      i++;
    }
    return shuffle(picked);
  }
  return shuffle(allQuestions.filter((q) => q.subtest === subtest)).slice(0, count);
}

function batchCount() { return Math.ceil(sessionQuestions.length / BATCH_SIZE); }
function currentBatchQuestions() {
  if (sessionMode === 'tryout') return sessionQuestions; // tryout: semua soal satu layar + timer
  const start = batchIndex * BATCH_SIZE;
  return sessionQuestions.slice(start, start + BATCH_SIZE);
}
function isLastBatch() { return sessionMode === 'tryout' || batchIndex >= batchCount() - 1; }

let lastStart = 'CAMPURAN';
function startSession(subtest) {
  sessionMode = (subtest === 'TRYOUT' || subtest === 'TRYOUT_SKD') ? 'tryout' : 'latihan';
  sessionSubtest = subtest;
  lastStart = subtest;
  sessionQuestions = pickSession(subtest, chosenCount);
  batchIndex = 0;
  answersMap = {};
  if (!sessionQuestions.length) {
    alert(subtest === 'SALAH'
      ? 'Belum ada soal yang perlu diulang. Soal yang kamu jawab belum sempurna akan terkumpul di sini setelah sesi latihan.'
      : 'Bank soal belum termuat. Coba muat ulang halaman.');
    return;
  }
  $('session-title').textContent =
    subtest === 'TRYOUT_SKD' ? `Tryout SKD Resmi — ${sessionQuestions.length} Soal • ${SKD_MINUTES} Menit`
    : sessionMode === 'tryout' ? `Tryout Mini — ${sessionQuestions.length} Soal`
    : subtest === 'SALAH' ? `Ulangi yang Salah (${sessionQuestions.length} soal)`
    : (subtest === 'CAMPURAN' ? `Latihan Hari Ini — Campuran (${sessionQuestions.length} soal)` : `Latihan ${subtest} (${sessionQuestions.length} soal)`);
  renderBatch();
  show('session');
  if (sessionMode === 'tryout') startTimer(subtest === 'TRYOUT_SKD' ? SKD_MINUTES * 60 : sessionQuestions.length * SECONDS_PER_QUESTION);
  else { $('session-timer').hidden = true; stopTimer(); }
}

function renderBatch() {
  const list = $('question-list');
  list.innerHTML = '';
  const batchQs = currentBatchQuestions();
  const offset = sessionMode === 'tryout' ? 0 : batchIndex * BATCH_SIZE;

  if (sessionMode === 'tryout') {
    $('session-progress').textContent = `Jawab semua ${sessionQuestions.length} soal sebelum waktu habis.`;
  } else if (batchCount() > 1) {
    const capped = sessionQuestions.length < chosenCount
      ? ` (pilihan ${chosenCount} soal — bank soal contoh baru menyediakan ${sessionQuestions.length} soal unik)` : '';
    $('session-progress').textContent =
      `Bagian ${batchIndex + 1} dari ${batchCount()} • Soal ${offset + 1}–${offset + batchQs.length} dari ${sessionQuestions.length}${capped}. Selesaikan bagian ini, lalu ketuk Lanjut.`;
  } else {
    $('session-progress').textContent = 'Jawab semua soal, lalu ketuk Selesai.';
  }

  batchQs.forEach((q, idx) => {
    const picked = answersMap[q.id];
    const card = document.createElement('div');
    card.className = 'q-card';
    card.innerHTML = `
      <div class="q-meta">${esc(q.subtest)} • ${esc(q.topic)}</div>
      <div class="q-text">${offset + idx + 1}. ${esc(q.question)}</div>
      <div class="opts">
        ${q.options.map((o, oi) => `
          <label class="opt ${picked === oi ? 'selected' : ''}" data-q="${q.id}" data-oi="${oi}">
            <input type="radio" name="${q.id}" value="${oi}" ${picked === oi ? 'checked' : ''}>
            <span><strong>${'ABCDE'[oi]}.</strong> ${esc(o.text)}</span>
          </label>`).join('')}
      </div>`;
    list.appendChild(card);
    // Slot native di tengah daftar soal bagian ini — di web ini slot AdSense in-article.
    // WAJIB berlabel jelas "IKLAN" (kebijakan iklan native: dilarang menyamar sebagai konten).
    if (sessionMode === 'latihan' && idx + 1 === NATIVE_AFTER_QUESTION) {
      const ad = document.createElement('div');
      ad.className = 'native-slot';
      ad.setAttribute('data-ad-format', 'native');
      ad.innerHTML = '<span class="ad-label">IKLAN</span><div>Ruang Iklan Native — tampil di antara soal, tidak menutupi soal.</div>';
      list.appendChild(ad);
    }
  });
  list.querySelectorAll('input[type=radio]').forEach((input) => {
    input.addEventListener('change', () => {
      answersMap[input.name] = parseInt(input.value, 10);
      document.querySelectorAll(`.opt[data-q="${input.name}"]`).forEach((el) => el.classList.remove('selected'));
      input.closest('.opt').classList.add('selected');
      updateFinishState();
    });
  });

  // Tombol bawah: "Lanjut" antar-bagian, "Selesai" di bagian terakhir.
  $('btn-finish').textContent = isLastBatch()
    ? 'Selesai & Lihat Skor'
    : `Lanjut — ${BATCH_SIZE} Soal Berikutnya »`;
  updateFinishState();
  window.scrollTo({ top: 0 });
}

function getAnswers() {
  const answers = {};
  sessionQuestions.forEach((q) => { answers[q.id] = (answersMap[q.id] ?? null); });
  return answers;
}
function updateFinishState() {
  const batchQs = currentBatchQuestions();
  $('btn-finish').disabled = !batchQs.every((q) => answersMap[q.id] !== null && answersMap[q.id] !== undefined);
}
function onFinishButton() {
  if (!isLastBatch()) {
    batchIndex++;
    renderBatch();
    return;
  }
  finishSession(false);
}

function startTimer(seconds) {
  stopTimer();
  timeLeft = seconds;
  const el = $('session-timer');
  el.hidden = false;
  const tick = () => {
    const m = String(Math.floor(timeLeft / 60)).padStart(2, '0');
    const s = String(timeLeft % 60).padStart(2, '0');
    el.textContent = `${m}:${s}`;
    if (timeLeft <= 0) { stopTimer(); finishSession(true); return; }
    timeLeft--;
  };
  tick();
  timerId = setInterval(tick, 1000);
}
function stopTimer() { if (timerId) { clearInterval(timerId); timerId = null; } }

/* ---------- hasil & pembahasan ---------- */
let lastResult = null;
function finishSession(auto = false) {
  stopTimer();
  const answers = getAnswers();
  const per = {};
  let earned = 0, max = 0;
  sessionQuestions.forEach((q) => {
    const got = answers[q.id] !== null && answers[q.id] !== undefined ? q.options[answers[q.id]].score : 0;
    const qmax = Math.max(...q.options.map((o) => o.score));
    earned += got; max += qmax;
    per[q.subtest] = per[q.subtest] || { got: 0, max: 0 };
    per[q.subtest].got += got; per[q.subtest].max += qmax;
  });
  lastResult = { answers, per, earned, max };

  // Kumpulkan soal yang belum dijawab sempurna untuk mode "Ulangi yang Salah";
  // soal yang kali ini dijawab sempurna otomatis keluar dari daftar.
  const wrong = new Set(loadJSON(LS.wrong, []));
  sessionQuestions.forEach((q) => {
    const got = answers[q.id] !== null && answers[q.id] !== undefined ? q.options[answers[q.id]].score : 0;
    const qmax = Math.max(...q.options.map((o) => o.score));
    if (got >= qmax) wrong.delete(q.id); else wrong.add(q.id);
  });
  saveJSON(LS.wrong, [...wrong]);

  const hist = getHistory();
  hist.push({ date: new Date().toISOString(), mode: sessionMode, subtest: sessionSubtest, count: sessionQuestions.length, earned, max, per });
  saveJSON(LS.history, hist);
  markTodayDone();

  $('result-score').textContent = `${earned} / ${max}`;
  let detail =
    (auto ? '<p class="muted">⏱ Waktu habis — sesi diserahkan otomatis.</p>' : '') +
    Object.entries(per).map(([sub, v]) => `<p><strong>${sub}:</strong> ${v.got} / ${v.max} poin sesi ini</p>`).join('');
  if (sessionSubtest === 'TRYOUT_SKD') {
    // Pada komposisi resmi, poin per subtes SUDAH skala SKD asli (maks 150/175/225).
    const verdicts = ['TWK', 'TIU', 'TKP'].map((sub) => {
      const got = per[sub] ? per[sub].got : 0;
      return `<p><strong>${sub}:</strong> ${got} / ${MAX_SCORE[sub]} (PG ${PASSING_GRADE[sub]}) — ${got >= PASSING_GRADE[sub] ? '✅ LOLOS PG' : '❌ di bawah PG'}</p>`;
    }).join('');
    const lolos = ['TWK', 'TIU', 'TKP'].every((sub) => (per[sub] ? per[sub].got : 0) >= PASSING_GRADE[sub]);
    detail += '<hr>' + verdicts +
      `<p><strong>${lolos ? '🏆 Selamat! Ketiga subtes lolos passing grade pada tryout ini.' : 'Belum lolos passing grade di semua subtes — cek subtes bertanda ❌, lalu kejar lewat latihan per subtes + Ulangi yang Salah.'}</strong></p>`;
  } else {
    detail += '<p class="muted">Kunci jawaban per soal ada di Pembahasan. Skor sesi adalah poin mentah; estimasi SKD skala penuh tampil di Meter Passing Grade beranda.</p>';
  }
  $('result-detail').innerHTML = detail;
  show('result');
}

function renderExplanation() {
  const list = $('explanation-list');
  list.innerHTML = '';
  sessionQuestions.forEach((q, idx) => {
    const picked = lastResult ? lastResult.answers[q.id] : null;
    const keyIdx = q.options.findIndex((o) => o.score === Math.max(...q.options.map((x) => x.score)));
    const card = document.createElement('div');
    card.className = 'exp-card';
    card.innerHTML = `
      <div class="q-meta">${esc(q.subtest)} • ${esc(q.topic)}</div>
      <div class="q-text">${idx + 1}. ${esc(q.question)}</div>
      ${q.options.map((o, oi) => {
        const cls = oi === keyIdx ? 'correct' : (oi === picked ? 'wrongpick' : '');
        const note = q.subtest === 'TKP' ? ` <em>(${o.score} poin)</em>` : '';
        return `<div class="opt ${cls}"><span><strong>${'ABCDE'[oi]}.</strong> ${esc(o.text)}${note}</span></div>`;
      }).join('')}
      <p><strong>Pembahasan:</strong> ${esc(q.explanation)}</p>
      <div class="trick">⚡ <strong>Trik Cepat:</strong> ${esc(q.trick)}</div>`;
    list.appendChild(card);
  });
}

/* ---------- statistik ---------- */
function renderStats() {
  const hist = getHistory();
  const body = $('stats-body');
  if (!hist.length) { body.innerHTML = '<p class="muted">Belum ada data. Selesaikan sesi pertamamu dulu.</p>'; return; }
  const est = perSubtestEstimates();
  body.innerHTML =
    `<p><strong>Total sesi:</strong> ${hist.length} • <strong>Total soal:</strong> ${hist.reduce((a, h) => a + h.count, 0)}</p>` +
    ['TWK', 'TIU', 'TKP'].map((sub) => est[sub] == null
      ? `<p><strong>${sub}:</strong> belum ada data</p>`
      : `<p><strong>${sub}:</strong> estimasi ${est[sub]} / ${MAX_SCORE[sub]} (PG ${PASSING_GRADE[sub]}) — ${est[sub] >= PASSING_GRADE[sub] ? 'di atas passing grade ✅' : 'di bawah passing grade ⚠️'}</p>`).join('');
}

/* ---------- PWA: service worker + install ---------- */
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => navigator.serviceWorker.register('./sw.js').catch(() => {}));
}
let deferredInstall = null;
window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  deferredInstall = e;
  $('btn-install').hidden = false;
});
$('btn-install').addEventListener('click', async () => {
  if (!deferredInstall) return;
  deferredInstall.prompt();
  await deferredInstall.userChoice;
  deferredInstall = null;
  $('btn-install').hidden = true;
});

/* ---------- wiring ---------- */
document.querySelectorAll('[data-start]').forEach((b) => b.addEventListener('click', () => startSession(b.dataset.start)));
document.querySelectorAll('.count-chip').forEach((chip) => chip.addEventListener('click', () => {
  chosenCount = parseInt(chip.dataset.count, 10);
  localStorage.setItem(LS.count, String(chosenCount));
  renderCountPicker();
}));
$('btn-tryout').addEventListener('click', () => startSession('TRYOUT'));
$('btn-tryout-skd').addEventListener('click', () => startSession('TRYOUT_SKD'));
$('btn-wrong').addEventListener('click', () => startSession('SALAH'));
$('btn-share').addEventListener('click', async () => {
  if (!lastResult) return;
  const perTxt = Object.entries(lastResult.per).map(([sub, v]) => `${sub} ${v.got}/${v.max}`).join(' • ');
  const text = `Latihan SKD di SiapSKD: skorku ${lastResult.earned}/${lastResult.max} (${perTxt}). Latihan soal CPNS gratis tiap hari di https://cpns.techo.web.id/`;
  if (navigator.share) { try { await navigator.share({ title: 'SiapSKD', text }); } catch { /* dibatalkan pengguna */ } }
  else if (navigator.clipboard) { try { await navigator.clipboard.writeText(text); alert('Hasil tersalin — tempel di mana saja untuk membagikan.'); } catch { alert(text); } }
  else alert(text);
});
$('btn-about').addEventListener('click', () => {
  const about = $('about-section');
  about.open = true;
  about.scrollIntoView({ behavior: 'smooth', block: 'start' });
});
$('btn-stats').addEventListener('click', () => { renderStats(); show('stats'); });
$('btn-finish').addEventListener('click', onFinishButton);
function goHome() { stopTimer(); renderHome(); show('home'); }
$('btn-quit').addEventListener('click', goHome);
$('brand-home').addEventListener('click', goHome); // logo = tombol Home dari layar mana pun
$('btn-explanation').addEventListener('click', () => { renderExplanation(); show('explanation'); });
$('btn-again').addEventListener('click', () => startSession(lastStart));
$('btn-reset').addEventListener('click', () => {
  if (confirm('Hapus semua data latihan (streak, riwayat, statistik) di perangkat ini?')) {
    localStorage.removeItem(LS.streak); localStorage.removeItem(LS.history); localStorage.removeItem(LS.wrong);
    renderHome(); show('home');
  }
});

/* ---------- mode gelap ---------- */
function applyTheme() {
  const dark = localStorage.getItem(LS.theme) === 'dark';
  document.body.classList.toggle('dark', dark);
  $('btn-theme').textContent = dark ? '☀️' : '🌙';
}
$('btn-theme').addEventListener('click', () => {
  const dark = !document.body.classList.contains('dark');
  localStorage.setItem(LS.theme, dark ? 'dark' : 'light');
  applyTheme();
});
applyTheme();
document.querySelectorAll('[data-home]').forEach((b) => b.addEventListener('click', goHome));

/* ---------- muat soal ----------
   Semua file bank soal digabung. Tambahkan file batch baru ke daftar ini setiap rilis.
   Total saat ini: 3.000 soal (600 batch 1–3 + 2.400 batch 4). */
const QUESTION_FILES = ['./questions.json',
  './questions_batch2_twk.json', './questions_batch2_tiu.json', './questions_batch2_tkp.json',
  './questions_batch3_twk_a.json', './questions_batch3_twk_b.json', './questions_batch3_twk_c.json',
  './questions_batch3_tiu_d.json', './questions_batch3_tiu_e.json', './questions_batch3_tiu_f.json',
  './questions_batch3_tkp_g.json', './questions_batch3_tkp_h.json', './questions_batch3_tkp_i.json',
  './questions_batch4_twk.json', './questions_batch4_tiu.json', './questions_batch4_tkp.json'];
Promise.all(QUESTION_FILES.map((f) => fetch(f).then((r) => r.json())))
  .then((parts) => { allQuestions = parts.flatMap((d) => d.questions || []); renderCountPicker(); renderHome(); })
  .catch(() => {
    $('pg-meter').innerHTML = '<p class="muted">Gagal memuat bank soal. Periksa koneksi lalu muat ulang halaman.</p>';
  });
