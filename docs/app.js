/* SiapSKD Web (PWA) — mesin latihan yang sama dengan versi Android:
   sesi 5 soal (scroll), native slot setelah soal ke-2, hasil + kunci gratis,
   pembahasan lengkap sebagai konten premium (di Android: rewarded; di web: slot AdSense nanti).
   Sumber soal: questions.json — SALINAN dari app/src/main/assets/questions/sample_questions.json
   (sumber kebenaran tetap file Android; salinan web diperbarui tiap rilis). */
'use strict';

const QUESTIONS_PER_SESSION = 5;
const NATIVE_AFTER_QUESTION = 2; // tampilkan slot native setelah soal ke-N
const SECONDS_PER_QUESTION = 55; // ritme ujian asli: 110 soal / 100 menit
const PASSING_GRADE = { TWK: 65, TIU: 80, TKP: 166 };
const MAX_SCORE = { TWK: 150, TIU: 175, TKP: 225 };
const LS = { streak: 'siapskd_streak', history: 'siapskd_history' };

let allQuestions = [];
let sessionQuestions = [];
let sessionMode = 'latihan'; // 'latihan' | 'tryout'
let sessionSubtest = 'CAMPURAN';
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

/* ---------- sesi ---------- */
function pickSession(subtest, count) {
  if (subtest === 'TRYOUT') return shuffle(allQuestions).slice(0, allQuestions.length);
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

function startSession(subtest) {
  sessionMode = subtest === 'TRYOUT' ? 'tryout' : 'latihan';
  sessionSubtest = subtest;
  sessionQuestions = pickSession(subtest, QUESTIONS_PER_SESSION);
  if (!sessionQuestions.length) { alert('Bank soal belum termuat. Coba muat ulang halaman.'); return; }
  $('session-title').textContent = sessionMode === 'tryout'
    ? `Tryout Mini — ${sessionQuestions.length} Soal`
    : (subtest === 'CAMPURAN' ? 'Latihan Hari Ini — Campuran' : `Latihan ${subtest}`);
  renderQuestions();
  show('session');
  if (sessionMode === 'tryout') startTimer(sessionQuestions.length * SECONDS_PER_QUESTION);
  else { $('session-timer').hidden = true; stopTimer(); }
  updateFinishState();
}

function renderQuestions() {
  const list = $('question-list');
  list.innerHTML = '';
  sessionQuestions.forEach((q, idx) => {
    const card = document.createElement('div');
    card.className = 'q-card';
    card.innerHTML = `
      <div class="q-meta">${esc(q.subtest)} • ${esc(q.topic)}</div>
      <div class="q-text">${idx + 1}. ${esc(q.question)}</div>
      <div class="opts">
        ${q.options.map((o, oi) => `
          <label class="opt" data-q="${q.id}" data-oi="${oi}">
            <input type="radio" name="${q.id}" value="${oi}">
            <span><strong>${'ABCDE'[oi]}.</strong> ${esc(o.text)}</span>
          </label>`).join('')}
      </div>`;
    list.appendChild(card);
    // Slot native di tengah daftar soal — di web ini slot AdSense in-article.
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
      document.querySelectorAll(`.opt[data-q="${input.name}"]`).forEach((el) => el.classList.remove('selected'));
      input.closest('.opt').classList.add('selected');
      updateFinishState();
    });
  });
}

function getAnswers() {
  const answers = {};
  sessionQuestions.forEach((q) => {
    const el = document.querySelector(`input[name="${q.id}"]:checked`);
    answers[q.id] = el ? parseInt(el.value, 10) : null;
  });
  return answers;
}
function updateFinishState() {
  const answers = getAnswers();
  $('btn-finish').disabled = !sessionQuestions.every((q) => answers[q.id] !== null);
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

  const hist = getHistory();
  hist.push({ date: new Date().toISOString(), mode: sessionMode, subtest: sessionSubtest, count: sessionQuestions.length, earned, max, per });
  saveJSON(LS.history, hist);
  markTodayDone();

  $('result-score').textContent = `${earned} / ${max}`;
  $('result-detail').innerHTML =
    (auto ? '<p class="muted">⏱ Waktu habis — sesi diserahkan otomatis.</p>' : '') +
    Object.entries(per).map(([sub, v]) => `<p><strong>${sub}:</strong> ${v.got} / ${v.max} poin sesi ini</p>`).join('') +
    '<p class="muted">Kunci jawaban per soal ada di Pembahasan. Skor sesi adalah poin mentah; estimasi SKD skala penuh tampil di Meter Passing Grade beranda.</p>';
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
$('btn-tryout').addEventListener('click', () => startSession('TRYOUT'));
$('btn-stats').addEventListener('click', () => { renderStats(); show('stats'); });
$('btn-finish').addEventListener('click', () => finishSession(false));
$('btn-quit').addEventListener('click', () => { stopTimer(); renderHome(); show('home'); });
$('btn-explanation').addEventListener('click', () => { renderExplanation(); show('explanation'); });
$('btn-again').addEventListener('click', () => startSession(sessionSubtest === 'TRYOUT' ? 'TRYOUT' : sessionSubtest));
$('btn-reset').addEventListener('click', () => {
  if (confirm('Hapus semua data latihan (streak, riwayat, statistik) di perangkat ini?')) {
    localStorage.removeItem(LS.streak); localStorage.removeItem(LS.history);
    renderHome(); show('home');
  }
});
document.querySelectorAll('[data-home]').forEach((b) => b.addEventListener('click', () => { renderHome(); show('home'); }));

/* ---------- muat soal ---------- */
fetch('./questions.json')
  .then((r) => r.json())
  .then((data) => { allQuestions = data.questions || []; renderHome(); })
  .catch(() => {
    $('pg-meter').innerHTML = '<p class="muted">Gagal memuat bank soal. Periksa koneksi lalu muat ulang halaman.</p>';
  });
