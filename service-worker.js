// জীববিজ্ঞান স্টাডি সেন্টার: অফলাইনে চালানোর জন্য ক্যাশ
// নতুন ভার্সন আপলোড করলে CACHE নামের শেষের সংখ্যা বাড়িয়ে দিন।
const CACHE = 'bsc-app-v2027-10';
const CORE = ['./', './index.html', './manifest.json', './icon.png', './icon-192.png', './icon-512.png', './icon-maskable-192.png', './icon-maskable-512.png', './apple-touch-icon.png', './logo.png', './fonts/baloo-da-2-bengali-600-normal.woff2', './fonts/baloo-da-2-bengali-700-normal.woff2', './fonts/hind-siliguri-bengali-400-normal.woff2', './fonts/hind-siliguri-bengali-500-normal.woff2'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(CORE)));
  self.skipWaiting();
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)))));
  self.clients.claim();
});

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  const isPage = req.mode === 'navigate' || url.pathname.endsWith('/index.html') || url.pathname.endsWith('/');
  if (isPage && url.origin === location.origin) {
    // অ্যাপের পাতা: আগে নেট থেকে নতুনটা, নেট না থাকলে ক্যাশ থেকে
    e.respondWith(fetch(req).then(res => {
      const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); return res;
    }).catch(() => caches.match(req).then(r => r || caches.match('./index.html'))));
    return;
  }
  // বাকি সব (ফন্ট, আইকন): আগে ক্যাশ, না পেলে নেট থেকে এনে ক্যাশে রাখা
  if (url.origin !== location.origin && !url.hostname.endsWith('fonts.googleapis.com') && !url.hostname.endsWith('fonts.gstatic.com')) return;
  e.respondWith(caches.match(req).then(hit => hit || fetch(req).then(res => {
    if (res && (res.status === 200 || res.type === 'opaque')) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
    return res;
  })));
});
