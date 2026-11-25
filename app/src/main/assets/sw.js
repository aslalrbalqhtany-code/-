const CACHE_NAME = 'prayer-times-pwa-v8';
const SCHEDULE_CACHE = 'prayer-schedule-cache-v8';
const ASSETS = [
  './',
  './index.html',
  './manifest.json',
  './icon.svg',
  './audio/adhan1.m4a',
  './audio/adhan2.m4a'
];

let bgTimer = null;
let bgState = {
  enabled: true,
  bgModeEnabled: false,
  adhanEnabled: true,
  cityName: 'تعز',
  times: [],
  azkarEnabled: true,
  morningTime: 360,
  eveningTime: 1020,
  notifiedKeys: {}
};

const PRAYER_NAMES = ['الفجر', 'الشروق', 'الظهر', 'العصر', 'المغرب', 'العشاء'];

async function persistSchedule(payload) {
  try {
    const cache = await caches.open(SCHEDULE_CACHE);
    await cache.put('./schedule.json', new Response(JSON.stringify(payload), {
      headers: { 'Content-Type': 'application/json' }
    }));
  } catch (e) {}
}

async function restoreSchedule() {
  try {
    const cache = await caches.open(SCHEDULE_CACHE);
    const resp = await cache.match('./schedule.json');
    if (resp) {
      const saved = await resp.json();
      if (saved && typeof saved === 'object') {
        bgState = Object.assign({}, bgState, saved);
      }
    }
  } catch (e) {}
}

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache =>
      Promise.allSettled(ASSETS.map(url => cache.add(url)))
    ).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', event => {
  event.waitUntil(
    Promise.all([
      caches.keys().then(keys =>
        Promise.all(
          keys.filter(k => k !== CACHE_NAME && k !== SCHEDULE_CACHE).map(k => caches.delete(k))
        )
      ),
      restoreSchedule(),
      self.clients.claim()
    ]).then(() => startBackgroundLoop())
  );
});

self.addEventListener('fetch', event => {
  if (event.request.method !== 'GET') return;
  event.respondWith(
    caches.match(event.request).then(cached => {
      if (cached) return cached;
      return fetch(event.request).then(resp => {
        if (!resp || resp.status !== 200 || resp.type !== 'basic') return resp;
        const clone = resp.clone();
        caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
        return resp;
      }).catch(() => caches.match('./index.html'));
    })
  );
});

self.addEventListener('message', event => {
  const data = event.data || {};
  if (data.type === 'SYNC_SCHEDULE') {
    const prevKeys = bgState.notifiedKeys || {};
    bgState = Object.assign({}, bgState, data.payload, { notifiedKeys: prevKeys });
    persistSchedule(bgState);
    startBackgroundLoop();
  } else if (data.type === 'TEST_NOTIFICATION') {
    showAppNotification(
      data.title || 'مواقيت الصلاة',
      data.body || 'إشعار تجريبي من التطبيق',
      data.tag || 'test-notif-' + Date.now(),
      data.actionType || 'prayer',
      data.prayerName || ''
    );
  }
});

self.addEventListener('periodicsync', event => {
  if (event.tag === 'prayer-times-check') {
    event.waitUntil(restoreSchedule().then(() => checkBackgroundSchedules()));
  }
});

function startBackgroundLoop() {
  if (bgTimer) clearInterval(bgTimer);
  bgTimer = setInterval(checkBackgroundSchedules, 10000);
  checkBackgroundSchedules();
}

function checkBackgroundSchedules() {
  if (!bgState.enabled) return;
  const now = new Date();
  const dayKey = now.toDateString();
  const currentMin = now.getHours() * 60 + now.getMinutes();

  if (Array.isArray(bgState.times) && bgState.times.length === 6) {
    bgState.times.forEach((m, idx) => {
      if (idx === 1) return;
      const key = `${dayKey}-prayer-${idx}`;
      if (m === currentMin && !bgState.notifiedKeys[key]) {
        bgState.notifiedKeys[key] = true;
        const pName = PRAYER_NAMES[idx];
        showAppNotification(
          `حان الآن موعد أذان ${pName}`,
          `مواقيت الصلاة — ${bgState.cityName}`,
          key,
          'prayer',
          pName
        );
        notifyOpenClients({ type: 'TRIGGER_ADHAN', prayerName: pName });
      }
    });
  }

  if (bgState.azkarEnabled) {
    const mKey = `${dayKey}-azkar-morning`;
    if (bgState.morningTime === currentMin && !bgState.notifiedKeys[mKey]) {
      bgState.notifiedKeys[mKey] = true;
      showAppNotification('أذكار الصباح', 'حان وقت أذكار الصباح — اضغط لقراءتها', mKey, 'morning');
      notifyOpenClients({ type: 'TRIGGER_AZKAR', period: 'morning' });
    }

    const eKey = `${dayKey}-azkar-evening`;
    if (bgState.eveningTime === currentMin && !bgState.notifiedKeys[eKey]) {
      bgState.notifiedKeys[eKey] = true;
      showAppNotification('أذكار المساء', 'حان وقت أذكار المساء — اضغط لقراءتها', eKey, 'evening');
      notifyOpenClients({ type: 'TRIGGER_AZKAR', period: 'evening' });
    }
  }
}

function showAppNotification(title, body, tag, actionType, prayerName = '') {
  if (!self.registration || !self.registration.showNotification) return Promise.resolve();
  return self.registration.showNotification(title, {
    body: body,
    icon: './icon.svg',
    badge: './icon.svg',
    tag: tag,
    renotify: true,
    requireInteraction: true,
    vibrate: [350, 150, 350, 150, 450],
    data: { actionType, prayerName }
  }).catch(() => {});
}

async function notifyOpenClients(msg) {
  const allClients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
  for (const client of allClients) {
    client.postMessage(msg);
  }
}

self.addEventListener('notificationclick', event => {
  event.notification.close();
  const data = event.notification.data || {};
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(clientList => {
      for (const client of clientList) {
        if ('focus' in client) {
          client.postMessage({ type: 'NOTIF_CLICKED', ...data });
          return client.focus();
        }
      }
      if (self.clients.openWindow) {
        const param = data.actionType === 'morning' ? '?azkar=morning'
                    : data.actionType === 'evening' ? '?azkar=evening'
                    : data.actionType === 'prayer' ? '?playAdhan=1&prayer=' + encodeURIComponent(data.prayerName || '') : '';
        return self.clients.openWindow('./index.html' + param);
      }
    })
  );
});
