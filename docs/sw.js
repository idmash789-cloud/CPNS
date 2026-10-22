/* SiapSKD service worker — offline-first untuk aset aplikasi,
   network-first untuk bank soal agar soal baru cepat sampai. */
const CACHE = 'siapskd-v7';
const PRECACHE = [
  './',
  './index.html',
  './style.css',
  './app.js',
  './manifest.webmanifest',
  './icons/icon.svg',
  './questions.json',
  './questions_batch2_twk.json',
  './questions_batch2_tiu.json',
  './questions_batch2_tkp.json',
  './questions_batch3_twk_a.json',
  './questions_batch3_twk_b.json',
  './questions_batch3_twk_c.json',
  './questions_batch3_tiu_d.json',
  './questions_batch3_tiu_e.json',
  './questions_batch3_tiu_f.json',
  './questions_batch3_tkp_g.json',
  './questions_batch3_tkp_h.json',
  './questions_batch3_tkp_i.json'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE).then((cache) => cache.addAll(PRECACHE)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET') return;

  // Bank soal: utamakan jaringan (soal baru), jatuh ke cache saat offline.
  if (/questions(_batch\d+(_\w+)?)?\.json$/.test(url.pathname)) {
    event.respondWith(
      fetch(event.request)
        .then((res) => {
          const copy = res.clone();
          caches.open(CACHE).then((cache) => cache.put(event.request, copy));
          return res;
        })
        .catch(() => caches.match(event.request))
    );
    return;
  }

  // Aset lain: cache-first.
  event.respondWith(
    caches.match(event.request).then((hit) => hit || fetch(event.request))
  );
});
