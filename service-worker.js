// 배포할 때마다 이 숫자만 올려주면(v2 -> v3 ...) 옛날 캐시가 자동으로 정리됩니다.
const CACHE_VERSION = 'v2';
const CACHE_NAME = `photo-${CACHE_VERSION}`;

const PRECACHE_URLS = [
  './',
  './index.html',
  './manifest.json'
];

// 설치: 기본 파일을 캐시에 저장하고, 기다리지 않고 즉시 활성화 준비
self.addEventListener('install', (e) => {
  self.skipWaiting();
  e.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(PRECACHE_URLS))
  );
});

// 활성화: 이전 버전 캐시를 전부 삭제하고, 열려있는 화면도 즉시 새 버전이 제어하도록 함
self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((key) => key !== CACHE_NAME)
            .map((key) => caches.delete(key))
        )
      )
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  const req = e.request;

  // 화면(HTML) 요청: 온라인이면 항상 서버 최신 버전을 먼저 받아온다.
  // 오프라인일 때만 마지막으로 저장해둔 버전을 보여준다.
  if (req.mode === 'navigate' || req.destination === 'document') {
    e.respondWith(
      fetch(req)
        .then((res) => {
          const resClone = res.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(req, resClone));
          return res;
        })
        .catch(() =>
          caches.match(req).then((r) => r || caches.match('./index.html'))
        )
    );
    return;
  }

  // 그 외 파일(이미지, manifest 등): 캐시를 먼저 보여주되, 뒤에서 최신 버전으로 갱신
  e.respondWith(
    caches.match(req).then((cached) => {
      const fetchPromise = fetch(req)
        .then((res) => {
          const resClone = res.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(req, resClone));
          return res;
        })
        .catch(() => cached);
      return cached || fetchPromise;
    })
  );
});
