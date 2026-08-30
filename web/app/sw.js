/*
 * Service Worker fuer die Web-App.
 *
 * Aufgabe ist der Huellenspeicher, nicht die Daten: Die Karte soll auch ohne
 * Verbindung sofort dastehen. Der Stand selbst kommt aus localStorage, und
 * Sammeln und Einloesen laufen ausschliesslich ueber den Server - offline
 * gibt es sie nicht, und das soll auch niemand vortaeuschen.
 *
 * Beim Aendern der Dateien die VERSION hochzaehlen. Ohne das behalten
 * bestehende Installationen den alten Stand, bis der Speicher von selbst
 * ablaeuft - der klassische "beim Kunden ist es noch die alte Fassung".
 */
const VERSION = 'treuebiss-app-v18';

const HUELLE = [
  './',
  './index.html',
  './app.js',
  './manifest.webmanifest',
  './icon.svg',
  './icon-192.png',
  './icon-512.png',
  '../gemeinsam/basis.css',
  '../gemeinsam/palette.js',
  '../gemeinsam/fonts/familjen-grotesk-latin.woff2',
  '../gemeinsam/fonts/public-sans-latin.woff2',
];

self.addEventListener('install', (e) => {
  e.waitUntil((async () => {
    const speicher = await caches.open(VERSION);
    // Einzeln statt addAll: Faellt eine Datei aus, soll nicht die ganze
    // Installation scheitern. config.js gehoert bewusst nicht dazu - sie
    // ist je Installation anders und wird beim ersten Abruf aufgenommen.
    await Promise.all(HUELLE.map((pfad) =>
      speicher.add(pfad).catch((f) => console.warn('nicht vorgespeichert:', pfad, f))));
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', (e) => {
  e.waitUntil((async () => {
    const namen = await caches.keys();
    await Promise.all(namen.filter((n) => n !== VERSION).map((n) => caches.delete(n)));
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', (e) => {
  const anfrage = e.request;
  // Fremde Adressen nicht anfassen: Supabase und esm.sh gehoeren ins Netz,
  // nicht in den Speicher. Ein zwischengespeicherter Datenabruf waere
  // schlimmer als gar keiner.
  if (anfrage.method !== 'GET') return;
  if (new URL(anfrage.url).origin !== self.location.origin) return;

  // Seitenaufrufe zuerst aus dem Netz, damit eine neue Fassung ankommt;
  // faellt es aus, kommt die gespeicherte Seite.
  if (anfrage.mode === 'navigate') {
    e.respondWith((async () => {
      try {
        const antwort = await fetch(anfrage);
        const speicher = await caches.open(VERSION);
        speicher.put('./index.html', antwort.clone());
        return antwort;
      } catch {
        return (await caches.match('./index.html')) ?? Response.error();
      }
    })());
    return;
  }

  /*
   * Skripte und Stile zuerst aus dem Netz, mit dem Speicher als Rueckfall.
   *
   * Vorher galt auch fuer sie "erst aus dem Speicher, im Hintergrund
   * erneuern". Das heisst aber: Nach jeder Veroeffentlichung laeuft beim
   * Kunden noch einmal die alte Fassung, und der Fehler, den man gerade
   * behoben hat, ist beim Nachsehen immer noch da. Genau das hat in der
   * Entwicklung dreimal zu einer falschen Diagnose gefuehrt.
   *
   * Der Preis ist ein Netzaufruf beim Start - den braucht die Seite fuer die
   * Daten ohnehin. Offline traegt weiterhin der Speicher.
   */
  const istCode = /\.(js|css)$/.test(new URL(anfrage.url).pathname);
  if (istCode) {
    e.respondWith((async () => {
      const speicher = await caches.open(VERSION);
      try {
        const antwort = await fetch(anfrage);
        if (antwort.ok) speicher.put(anfrage, antwort.clone());
        return antwort;
      } catch {
        return (await speicher.match(anfrage)) ?? Response.error();
      }
    })());
    return;
  }

  // Alles Uebrige - Schriften, Bilder, Manifeste - aus dem Speicher und im
  // Hintergrund erneuern. Es aendert sich selten und ist gross.
  e.respondWith((async () => {
    const speicher = await caches.open(VERSION);
    const gespeichert = await speicher.match(anfrage);
    const ausDemNetz = fetch(anfrage).then((antwort) => {
      if (antwort.ok) speicher.put(anfrage, antwort.clone());
      return antwort;
    }).catch(() => null);
    return gespeichert ?? (await ausDemNetz) ?? Response.error();
  })());
});
