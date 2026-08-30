/*
 * Schreibt ein Web-App-Manifest je Betrieb.
 *
 * Warum: `start_url` im Manifest ist statisch. Wer die Seite auf den
 * Startbildschirm legt, startet danach genau diese Adresse - ohne `?b=`, also
 * ohne Betrieb. Auf iOS ist das eine Sackgasse: Eine Web-App auf dem
 * Startbildschirm hat ihren eigenen Speicher, getrennt von Safari. Weder ein
 * gemerkter Betrieb noch eine Mitgliedschaft steht dort zur Verfuegung, also
 * kann die Seite sich nicht selbst behelfen.
 *
 * Ein Manifest je Betrieb loest das an der Wurzel - und nebenbei traegt die
 * installierte App dann den Namen des Betriebs statt "TreueBiss".
 *
 * Laeuft im Actions-Workflow vor dem Veroeffentlichen. Lokal:
 *   node scripts/manifeste-bauen.mjs
 */
import { mkdir, writeFile, readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const wurzel = join(dirname(fileURLToPath(import.meta.url)), '..');
const ziel = join(wurzel, 'web', 'app', 'm');

/** Liest URL und Schluessel aus der ausgelieferten config.js. */
async function konfig() {
  const text = await readFile(join(wurzel, 'web', 'app', 'config.js'), 'utf8');
  const hol = (name) => text.match(new RegExp(`${name}\\s*=\\s*'([^']*)'`))?.[1];
  return { url: hol('SUPABASE_URL'), key: hol('SUPABASE_ANON_KEY') };
}

const { url, key } = await konfig();
if (!url || url.includes('DEIN-PROJEKT')) {
  console.log('config.js nicht ausgefüllt - keine Manifeste erzeugt.');
  process.exit(0);
}

// tenants ist nur fuer Angemeldete lesbar, also kurz anonym anmelden.
const anmeldung = await fetch(`${url}/auth/v1/signup`, {
  method: 'POST',
  headers: { apikey: key, 'Content-Type': 'application/json' },
  body: '{}',
});
const { access_token } = await anmeldung.json();
if (!access_token) {
  console.error('Anonyme Anmeldung fehlgeschlagen - keine Manifeste erzeugt.');
  process.exit(1);
}

const antwort = await fetch(
  `${url}/rest/v1/tenants?select=slug,name,primary_color&is_active=eq.true`,
  { headers: { apikey: key, Authorization: `Bearer ${access_token}` } },
);
const betriebe = await antwort.json();
if (!Array.isArray(betriebe)) {
  console.error('Betriebe nicht lesbar:', betriebe);
  process.exit(1);
}

await mkdir(ziel, { recursive: true });
for (const b of betriebe) {
  // Slugs sind Bezeichner; alles andere gehoert nicht in einen Dateinamen.
  if (!/^[a-z0-9-]+$/.test(b.slug ?? '')) {
    console.warn('übersprungen, unerwarteter Slug:', b.slug);
    continue;
  }
  const farbe = /^#[0-9A-Fa-f]{6}$/.test(b.primary_color ?? '') ? b.primary_color : '#4CAF50';
  const manifest = {
    name: b.name,
    short_name: b.name,
    description: `Deine Stempelkarte bei ${b.name}.`,
    // Relativ zum Manifest, das in m/ liegt - deshalb eine Ebene hoch.
    start_url: `../?b=${b.slug}`,
    scope: '../',
    display: 'standalone',
    orientation: 'portrait',
    background_color: '#f4f4f5',
    theme_color: farbe,
    lang: 'de',
    icons: [
      { src: '../icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any maskable' },
      { src: '../icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any maskable' },
      { src: '../icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any' },
    ],
  };
  await writeFile(join(ziel, `${b.slug}.webmanifest`), JSON.stringify(manifest, null, 2) + '\n');
  console.log(`  ${b.slug}.webmanifest  (${b.name})`);
}
console.log(`${betriebe.length} Manifest(e) geschrieben nach web/app/m/`);
