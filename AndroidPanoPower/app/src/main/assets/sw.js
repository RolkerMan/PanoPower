// sw.js - Service Worker for PWA
const CACHE_NAME = 'pano-power-cache-v1';
const URLS_TO_CACHE = [
  // '/',
  '/index.html',
  '/manifest.webmanifest',
  '/web/piggy.jpg',
  '/web/piggyBase64.js',
  'https://cdn.jsdmirror.com/ajax/libs/font-awesome/6.4.0/css/all.min.css',
  'https://cdn.jsdmirror.com/ajax/libs/font-awesome/6.4.0/webfonts/fa-solid-900.woff2',
  'https://cdn.jsdmirror.com/ajax/libs/font-awesome/6.4.0/webfonts/fa-solid-900.ttf',
  'https://cdn.jsdmirror.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js',
  'https://cdn.jsdmirror.com/npm/modern-screenshot@4.6.7'
];

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(URLS_TO_CACHE))
  );
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k)))
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', event => {
  event.respondWith(
    caches.match(event.request).then(response => response || fetch(event.request))
  );
});
