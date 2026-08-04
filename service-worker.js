// জীববিজ্ঞান অ্যাপ — অফলাইন ক্যাশিং সার্ভিস ওয়ার্কার
const CACHE_NAME = 'biology-app-cache-v4';
const ASSETS_TO_CACHE = [
  './',
  './index.html',
  './manifest.json',
  './icon-192.png',
  './icon-512.png',
  './icon-192-maskable.png',
  './icon-512-maskable.png'
];

// ইনস্টল হওয়ার সময় সব ফাইল ক্যাশে রাখা হচ্ছে
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(ASSETS_TO_CACHE))
  );
  self.skipWaiting();
});

// পুরনো ক্যাশ মুছে ফেলা (নতুন ভার্সন এলে)
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(
        keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))
      )
    )
  );
  self.clients.claim();
});

// cache-first কৌশল: আগে ক্যাশ থেকে দেখাবে, না পেলে নেট থেকে আনবে এবং ক্যাশে রাখবে
self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return;
  event.respondWith(
    caches.match(event.request).then((cached) => {
      if (cached) return cached;
      return fetch(event.request)
        .then((response) => {
          if (response && response.status === 200) {
            const clone = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
          }
          return response;
        })
        .catch(() => cached);
    })
  );
});
