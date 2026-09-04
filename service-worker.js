// v3: CACHE_VERSION을 손으로 올릴 필요 없이,
// index.html "내용" 자체의 지문(해시)을 계산해서 캐시 이름으로 사용합니다.
// -> index.html이 한 글자라도 바뀌면 해시가 자동으로 달라져서
//    새 캐시로 인식되고, 이전 캐시는 자동으로 정리됩니다.

const CACHE_PREFIX = 'photo-';

// 아주 가벼운 문자열 해시 (버전 식별용, 보안용 아님)
function hashText(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = (hash * 31 + str.charCodeAt(i)) >>> 0;
  }
  return hash.toString(36);
}

async function computeCacheName() {
  try {
    // no-store: 브라우저 HTTP 캐시를 건너뛰고 항상 서버의 실제 최신 내용을 받아온다
    const res = await fetch('./index.html', { cache: 'no-store' });
    const text = await res.text();
    return CACHE_PREFIX + hashText(text);
  } catch (e) {
    // 최초 설치 시 오프라인 등으로 실패하면 고정 이름으로라도 동작은 하게 함
    return CACHE_PREFIX + 'fallback';
  }
}

let cacheNamePromise = null;
function getCacheName() {
  if (!cacheNamePromise) cacheNamePromise = computeCacheName();
  return cacheNamePromise;
}

const PRECACHE_URLS = [
  './',
  './index.html',
  './manifest.json'
];

// 설치: index.html 내용으로 캐시 이름을 정하고, 기본 파일을 저장
self.addEventListener('install', (e) => {
  self.skipWaiting();
  e.waitUntil(
    getCacheName().then((name) =>
      caches.open(name).then((cache) => cache.addAll(PRECACHE_URLS))
    )
  );
});

// 활성화: 지금 버전과 다른(=내용이 달라진) 이전 캐시를 전부 삭제
self.addEventListener('activate', (e) => {
  e.waitUntil(
    getCacheName()
      .then((name) =>
        caches.keys().then((keys) =>
          Promise.all(
            keys
              .filter((key) => key.startsWith(CACHE_PREFIX) && key !== name)
              .map((key) => caches.delete(key))
          )
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
          getCacheName().then((name) =>
            caches.open(name).then((cache) => cache.put(req, resClone))
          );
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
          getCacheName().then((name) =>
            caches.open(name).then((cache) => cache.put(req, resClone))
          );
          return res;
        })
        .catch(() => cached);
      return cached || fetchPromise;
    })
  );
});
