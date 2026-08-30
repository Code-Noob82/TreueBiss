/*
 * TreueBiss als Web-App.
 *
 * Warum es das neben der Android-App gibt: Ein Drittel der Kundschaft in
 * Deutschland ist auf iOS unterwegs, und der Wettbewerb liefert die Karte
 * ueberwiegend ganz ohne Installation aus. Diese Seite braucht keinen Store,
 * keinen Build je Betrieb und kein eigenes Entwicklerkonto.
 *
 * Der Betrieb steht in der Adresse: index.html?b=<slug>. Ein QR-Aufsteller
 * am Tresen oder ein Link auf dem Beleg fuehrt direkt hierher.
 */
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';
import { paletteSetzen } from '../gemeinsam/palette.js';

const $ = (id) => document.getElementById(id);

/** Fremder Text gehoert nie ungeprueft ins HTML. */
const h = (wert) => String(wert ?? '').replace(/[&<>"']/g, (z) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
})[z]);

let db = null;
let konfig = null;
let nutzerId = null;       // die anonyme Identitaet dieses Browsers
let betrieb = null;          // Zeile aus tenants
let stempel = 0;
let gutscheine = [];
let scanLaeuft = false;
let sammelnLaeuft = false;
/** Index des gerade gesetzten Stempels - nur der wird animiert. */
let frischerIndex = -1;

/*
 * Welcher Betrieb?
 *
 * Steht normalerweise in der Adresse (?b=slug). Beim Speichern auf dem
 * Startbildschirm geht er aber verloren: Der Launcher oeffnet die `start_url`
 * aus dem Manifest, und die ist statisch - eine Adresse je Betrieb liesse
 * sich nur mit einem Server ausliefern, der das Manifest erzeugt.
 *
 * Deshalb merkt sich die Seite den zuletzt benutzten Betrieb. Die Adresse
 * hat Vorrang; der gemerkte Wert springt nur ein, wenn sie nichts sagt.
 */
const LETZTER = 'treuebiss:letzter-betrieb';

function betriebAusAdresse() {
  return new URLSearchParams(location.search).get('b')?.trim() || null;
}

function betriebGemerkt() {
  try { return localStorage.getItem(LETZTER); } catch { return null; }
}

function betriebMerken(wert) {
  try { localStorage.setItem(LETZTER, wert); } catch { /* privater Modus */ }
}

const ausAdresse = betriebAusAdresse();
let slug = ausAdresse || betriebGemerkt();

// --------------------------------------------------------------- Meldungen

function melden(text, gut = false) {
  $('meldung').innerHTML = text
    ? `<div class="meldung ${gut ? 'gut' : 'schlecht'}">${text}</div>`
    : '';
}

/*
 * Waehrend eine Anfrage laeuft, muss der Knopf das zeigen. Ohne das steht
 * der Kunde an der Kasse vor einer Seite, die sich nicht ruehrt, und tippt
 * ein zweites Mal.
 */
function beschaeftigt(knopf, an, text) {
  if (!knopf) return;
  if (an) {
    knopf.dataset.ruhetext ??= knopf.textContent;
    knopf.textContent = text;
    knopf.disabled = true;
  } else {
    knopf.textContent = knopf.dataset.ruhetext ?? knopf.textContent;
    knopf.disabled = false;
  }
}

function band(text) {
  $('band').innerHTML = text ? `<div class="band">${text}</div>` : '';
}

/**
 * Erreichte die Anfrage den Server ueberhaupt? Ein Netzwerkfehler hat weder
 * SQLSTATE noch HTTP-Status. Wer nur auf `status` prueft, haelt jeden
 * Datenbankfehler faelschlich fuer einen Verbindungsabbruch.
 */
function istNetzfehler(fehler) {
  if (/fetch|network|Failed to send/i.test(fehler?.message ?? '')) return true;
  if (fehler?.name?.includes('Retryable')) return true;
  return !fehler?.code && !fehler?.status;
}

/** Uebersetzt die Fehler der Datenbankfunktionen in Klartext. */
function fehlertext(fehler) {
  if (istNetzfehler(fehler)) return 'Gerade keine Verbindung. Bitte später noch einmal.';
  const t = (fehler?.message ?? '') + (fehler?.details ?? '');
  // Derselbe Beleg zaehlt nur einmal - das ist kein Fehler des Kunden,
  // deshalb hier auch kein Fehlerton.
  if (fehler?.code === '23505' || /duplicate key|stamp_proofs/i.test(t)) {
    return 'Dieser Beleg wurde schon gezählt.';
  }
  if (/proof reference required/i.test(t)) return 'Da war keine Belegnummer dabei.';
  if (/unknown or inactive tenant/i.test(t)) return 'Dieser Betrieb nimmt gerade nicht teil.';
  if (/no redeem code configured/i.test(t)) {
    return 'Einlösen ist hier gerade nicht eingerichtet. Bitte wende dich an das Personal.';
  }
  if (/signature check required|Signatur des Belegs/i.test(t)) {
    return 'Die Signatur auf dem Beleg stimmt nicht. Bitte wende dich an das Personal.';
  }
  if (/receipt qr required/i.test(t)) return 'Hier zählt nur der QR-Code vom Kassenbon.';
  if (/receipt too old/i.test(t)) return 'Dieser Beleg ist zu alt.';
  if (/receipt from the future/i.test(t)) return 'Die Uhrzeit auf dem Beleg passt nicht.';
  if (/amount below minimum/i.test(t)) return 'Für diesen Betrag gibt es keinen Stempel.';
  if (/unknown register/i.test(t)) return 'Dieser Beleg gehört nicht zu diesem Betrieb.';
  if (/daily limit reached/i.test(t)) return 'Für heute ist die Karte voll genug. Bis morgen!';
  if (/invalid redeem code/i.test(t)) return 'Falscher Einlöse-Code.';
  if (/already redeemed/i.test(t)) return 'Dieser Gutschein wurde bereits eingelöst.';
  if (/expired/i.test(t)) return 'Dieser Gutschein ist abgelaufen.';
  if (/not found/i.test(t)) return 'Diesen Gutschein gibt es nicht.';
  if (fehler?.code === '42703' || fehler?.code === 'PGRST202'
      || /column .* does not exist|schema cache/i.test(t)) {
    // Dem Kunden nützt kein SQL-Befehl. Er soll nur wissen, dass es nicht
    // an ihm liegt und dass Wiederholen nichts bringt.
    return 'Hier ist gerade etwas nicht eingerichtet. Bitte sag dem Personal Bescheid.';
  }
  return 'Das hat nicht geklappt. Bitte noch einmal versuchen.';
}

// ------------------------------------------------------- Lokaler Zwischenstand

/*
 * Der letzte bekannte Stand liegt im Browser, damit die Karte auch ohne
 * Verbindung sofort dasteht - dieselbe Idee wie Room in der Android-App.
 * Gesammelt und eingeloest wird ausschliesslich auf dem Server.
 */
const schluessel = () => `treuebiss:${slug}`;

function standSichern() {
  try {
    localStorage.setItem(schluessel(), JSON.stringify({
      betrieb, stempel, gutscheine, stand: Date.now(),
    }));
  } catch { /* privater Modus, voller Speicher: dann eben ohne */ }
}

function standLaden() {
  try {
    const roh = localStorage.getItem(schluessel());
    if (!roh) return false;
    const d = JSON.parse(roh);
    betrieb = d.betrieb; stempel = d.stempel ?? 0; gutscheine = d.gutscheine ?? [];
    return !!betrieb;
  } catch {
    return false;
  }
}

// ------------------------------------------------------------------ Anzeige

function allesZeichnen() {
  $('betrieb').textContent = betrieb.name;
  document.title = betrieb.name + ' – TreueBiss';
  $('theme-farbe').setAttribute('content', paletteSetzen(betrieb.primary_color));

  if (betrieb.logo_url) {
    $('logo').src = betrieb.logo_url;
    // Der Name steht direkt daneben in der Ueberschrift; als alt-Text waere
    // er die zweite Ansage derselben Sache.
    $('logo').alt = '';
    $('logo').classList.remove('verborgen');
  }

  $('karte-titel').textContent = betrieb.loyalty_points_title ?? 'Treuepunkte';
  $('gutscheine-titel').textContent = betrieb.vouchers_title ?? 'Gutscheine';
  $('angebote-titel').textContent = betrieb.daily_special_title ?? 'Angebot des Tages';

  karteZeichnen();
  gutscheineZeichnen();
  $('karte-bereich').classList.remove('verborgen');
  $('gutscheine-bereich').classList.remove('verborgen');
}

function karteZeichnen() {
  const proKarte = betrieb.stamps_per_card ?? 10;
  const rest = Math.max(proKarte - stempel, 0);

  // "Noch drei" treibt an, "7 von 10" ist Buchhaltung. Beides zeigen, aber
  // in dieser Reihenfolge.
  $('stand-text').innerHTML = stempel === 0
    ? 'Noch kein Stempel auf der Karte.'
    : rest === 1
      ? 'Nur noch <b>ein</b> Stempel bis zur vollen Karte.'
      : `Noch <b>${rest}</b> Stempel bis zur vollen Karte.`;
  $('stand-zahl').textContent = `${stempel} von ${proKarte} gesammelt`;

  $('punkte').innerHTML = Array.from({ length: proKarte }, (_, i) => {
    // Die Drehung haengt am Index, nicht am Zufall - sonst wackelte die
    // Karte bei jedem Neuzeichnen.
    const dreh = (i * 37) % 15 - 7;
    const klassen = ['punkt'];
    if (i < stempel) klassen.push('voll');
    if (i === frischerIndex) klassen.push('frisch');
    return `<div class="${klassen.join(' ')}" style="--dreh:${dreh}deg"></div>`;
  }).join('');
  frischerIndex = -1;
}

function gutscheineZeichnen() {
  const offen = gutscheine.filter((g) => !g.is_redeemed);
  if (!offen.length) {
    $('gutscheine').innerHTML =
      '<p class="leer-text">Noch keiner da. Volle Karte, voller Gutschein.</p>';
    return;
  }
  $('gutscheine').innerHTML = offen.map((g) => {
    const verfallen = g.expires_at < Date.now();
    const bis = new Date(g.expires_at).toLocaleDateString('de-DE',
      { day: '2-digit', month: 'long', year: 'numeric' });
    // Ohne Knopf gibt es auch nichts abzureissen: Dann entfallen Naht und
    // Kerben, sonst sieht ein verfallener Gutschein aus wie ein gueltiger.
    return `<div class="abriss${verfallen ? ' verfallen ohne-naht' : ''}">
      <p class="wert">Ein Gutschein</p>
      <p class="frist">${verfallen ? 'Verfallen am ' : 'Gültig bis '}${h(bis)}</p>
      ${verfallen ? '' : `
        <hr class="naht">
        <div id="code-${h(g.id)}" class="verborgen">
          <label for="code-feld-${h(g.id)}">Einlöse-Code</label>
          <input id="code-feld-${h(g.id)}" autocapitalize="off" spellcheck="false"
                 placeholder="Die Verkaufskraft tippt ihn ein">
        </div>
        <button data-einloesen="${h(g.id)}">Einlösen</button>`}
    </div>`;
  }).join('');

  $('gutscheine').querySelectorAll('[data-einloesen]').forEach((k) => {
    k.onclick = () => einloesen(k.dataset.einloesen);
  });
}

function angeboteZeichnen(liste) {
  if (!liste?.length) { $('angebote-bereich').classList.add('verborgen'); return; }
  $('angebote').innerHTML = liste.map((a) => `<div class="angebot">
      <b>${h(a.title)}</b>
      ${a.description ? `<span>${h(a.description)}</span>` : ''}
    </div>`).join('');
  $('angebote-bereich').classList.remove('verborgen');
}

// -------------------------------------------------------------------- Daten

async function datenHolen() {
  const { data: reihen, error } = await db
    .from('tenants').select('*').eq('slug', slug).limit(1);
  if (error) throw error;
  if (!reihen?.length) return 'unbekannt';
  betrieb = reihen[0];

  // Ohne Mitgliedschaft lehnen die Policies spaetere Schreibvorgaenge ab.
  // user_id muss mit: Die insert-Policy vergleicht sie mit auth.uid(), und
  // eine Vorgabe hat die Spalte nicht. ignoreDuplicates, sonst macht
  // PostgREST daraus ein Update - und dafuer gibt es bewusst keine Policy.
  const { error: mFehler } = await db.from('memberships').upsert(
    { user_id: nutzerId, tenant_id: betrieb.id },
    { ignoreDuplicates: true, onConflict: 'user_id,tenant_id' });
  if (mFehler) throw mFehler;

  const [{ count }, { data: gs }, { data: an }] = await Promise.all([
    db.from('stamps').select('id', { count: 'exact', head: true }).eq('tenant_id', betrieb.id),
    db.from('vouchers').select('*').eq('tenant_id', betrieb.id).eq('is_redeemed', false),
    db.from('offers').select('*').eq('tenant_id', betrieb.id).order('created_at'),
  ]);

  stempel = count ?? 0;
  gutscheine = gs ?? [];
  standSichern();
  allesZeichnen();
  angeboteZeichnen(an);
  return 'ok';
}

// ------------------------------------------------------------------ Sammeln

/*
 * Verlangt der Betrieb eine geprüfte Signatur, geht der Beleg über die Edge
 * Function - die Datenbank kann ECDSA nicht selbst prüfen. Sonst weiterhin
 * geradeaus über die Datenbankfunktion.
 */
async function stempelVergeben(ref) {
  if (!betrieb.require_signed_proof) {
    return await db.rpc('issue_stamp', {
      p_tenant_id: betrieb.id, p_proof_ref: ref, p_source: 'receipt',
    });
  }
  const { data: sitzung } = await db.auth.getSession();
  const antwort = await fetch(`${konfig.SUPABASE_URL}/functions/v1/beleg-pruefen`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${sitzung?.session?.access_token ?? ''}`,
      apikey: konfig.SUPABASE_ANON_KEY,
    },
    body: JSON.stringify({ tenant_id: betrieb.id, qr: ref }),
  });
  const koerper = await antwort.json().catch(() => ({}));
  if (antwort.ok) return { data: [koerper], error: null };
  // In dieselbe Form bringen wie ein PostgrestError, damit fehlertext()
  // nicht zweimal geschrieben werden muss.
  return { data: null, error: { message: koerper.fehler ?? 'Prüfung fehlgeschlagen', code: koerper.code ?? String(antwort.status) } };
}

async function stempelHolen(belegRef) {
  const ref = (belegRef ?? '').trim();
  if (!ref) { melden('Da war keine Belegnummer dabei.'); return; }
  if (sammelnLaeuft) return;
  sammelnLaeuft = true;
  melden('');

  beschaeftigt($('beleg-senden'), true, 'Einen Moment …');
  const { data, error } = await stempelVergeben(ref);
  sammelnLaeuft = false;
  beschaeftigt($('beleg-senden'), false);
  if (error) { console.error('issue_stamp', error); melden(fehlertext(error)); return; }

  scannerSchliessen();
  const neu = data?.[0];
  // Die Zaehlung kommt aus der Funktion und gilt vor dem Kartenreset.
  frischerIndex = neu?.voucher_id ? -1 : (neu?.stamp_count ?? 1) - 1;
  await datenHolen();
  melden(neu?.voucher_id
    ? 'Karte voll! Dein Gutschein liegt bereit.'
    : 'Stempel gesammelt.', true);
}

// ----------------------------------------------------------------- Einlösen

async function einloesen(gutscheinId) {
  const feld = $(`code-feld-${gutscheinId}`);
  const huelle = $(`code-${gutscheinId}`);

  // Verlangt der Betrieb einen Code, erst das Feld zeigen - und nur, wenn
  // wirklich etwas drinsteht, absenden.
  if (betrieb.requires_redeem_code && huelle?.classList.contains('verborgen')) {
    huelle.classList.remove('verborgen');
    feld?.focus();
    melden('Bitte lass die Verkaufskraft den Einlöse-Code eingeben.');
    return;
  }
  const code = betrieb.requires_redeem_code ? (feld?.value.trim() || null) : null;
  if (betrieb.requires_redeem_code && !code) { melden('Es fehlt der Einlöse-Code.'); return; }

  melden('');
  const knopf = $('gutscheine').querySelector(`[data-einloesen="${gutscheinId}"]`);
  beschaeftigt(knopf, true, 'Wird eingelöst …');
  const { error } = await db.rpc('redeem_voucher', {
    p_voucher_id: gutscheinId, p_code: code,
  });
  beschaeftigt(knopf, false);
  if (error) { console.error('redeem_voucher', error); melden(fehlertext(error)); return; }
  await datenHolen();
  melden('Eingelöst. Guten Appetit!', true);
}

// ------------------------------------------------------------------ Scanner

/*
 * BarcodeDetector gibt es in Chrome und auf Android, nicht in Safari - und
 * Safari ist genau der Grund, warum es diese Web-App gibt. Deshalb faellt
 * der Scanner auf jsQR zurueck, statt auf dem iPhone gar nicht zu gehen.
 */
async function scannerOeffnen() {
  melden('');
  $('scanner').classList.remove('verborgen');
  $('sammeln').classList.add('verborgen');

  if (!window.isSecureContext) {
    $('video').classList.add('verborgen');
    melden('Die Kamera braucht eine <code>https</code>-Adresse. '
         + 'Tippe die Nummer vom Kassenbon ein.');
    return;
  }
  let strom;
  try {
    strom = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
  } catch {
    $('video').classList.add('verborgen');
    melden('Kein Zugriff auf die Kamera. Tippe die Nummer vom Kassenbon ein.');
    return;
  }

  const video = $('video');
  video.classList.remove('verborgen');
  video.srcObject = strom;
  await video.play();
  scanLaeuft = true;

  const lesen = await leserBauen();
  if (!lesen) {
    melden('Dieser Browser kann keine QR-Codes lesen. '
         + 'Tippe die Nummer vom Kassenbon ein.');
    return;
  }

  const leinwand = document.createElement('canvas');
  const suchen = async () => {
    if (!scanLaeuft) return;
    const wert = await lesen(video, leinwand);
    if (wert) { await stempelHolen(wert); return; }
    requestAnimationFrame(suchen);
  };
  requestAnimationFrame(suchen);
}

/**
 * Liefert eine Lesefunktion oder null, wenn der Browser gar nicht kann.
 * Erst der eingebaute Detektor, dann jsQR - das spart auf Android den
 * Download und deckt auf iOS ueberhaupt erst den Fall ab.
 */
async function leserBauen() {
  if ('BarcodeDetector' in window) {
    const detektor = new BarcodeDetector({ formats: ['qr_code'] });
    return async (video) => {
      try {
        const treffer = await detektor.detect(video);
        return treffer[0]?.rawValue ?? null;
      } catch {
        return null;  // einzelne Bilder duerfen fehlschlagen
      }
    };
  }
  try {
    const { default: jsQR } = await import('https://esm.sh/jsqr@1.4.0');
    return (video, leinwand) => {
      if (!video.videoWidth) return null;
      leinwand.width = video.videoWidth;
      leinwand.height = video.videoHeight;
      const ctx = leinwand.getContext('2d', { willReadFrequently: true });
      ctx.drawImage(video, 0, 0);
      const bild = ctx.getImageData(0, 0, leinwand.width, leinwand.height);
      return jsQR(bild.data, bild.width, bild.height)?.data ?? null;
    };
  } catch (e) {
    console.error('jsQR laden', e);
    return null;
  }
}

function scannerSchliessen() {
  scanLaeuft = false;
  const video = $('video');
  video.srcObject?.getTracks().forEach((t) => t.stop());
  video.srcObject = null;
  $('scanner').classList.add('verborgen');
  $('sammeln').classList.remove('verborgen');
  $('beleg').value = '';
}

// -------------------------------------------------------------------- Start

$('sammeln').onclick = scannerOeffnen;
$('scan-abbrechen').onclick = () => { scannerSchliessen(); melden(''); };
$('beleg-senden').onclick = () => stempelHolen($('beleg').value);
$('beleg').onkeydown = (e) => { if (e.key === 'Enter') stempelHolen($('beleg').value); };

function rechtlichesZeichnen() {
  const eintraege = [
    [konfig.DATENSCHUTZ_URL, 'Datenschutz'],
    [konfig.APP_DATENSCHUTZ_URL, 'Datenschutz in der App'],
    [konfig.AGB_URL, 'AGB'],
    [konfig.IMPRESSUM_URL, 'Impressum'],
  ].filter(([url]) => url);
  $('rechtliches').innerHTML = eintraege
    .map(([url, text]) => `<a href="${h(url)}" target="_blank" rel="noopener">${h(text)}</a>`)
    .join(' · ');
}

/*
 * Eigener Speicherschlüssel je Oberfläche.
 *
 * Ohne ihn teilen sich alle drei Seiten unter derselben Adresse EINE
 * Anmeldung: Der Supabase-Client legt sein Token unter `sb-<projekt>-auth-
 * token` ab, für alle gleich. Die Folgen sind still und übel - eine
 * Verkaufskraft, die auf dem Kassengerät die Kundenseite öffnet, sammelt
 * Stempel auf den Kassenzugang, weil dort schon eine Sitzung liegt und gar
 * keine anonyme Anmeldung mehr stattfindet. Umgekehrt steht die Kasse
 * plötzlich mit einer anonymen Sitzung da und meldet, der Zugang gehöre zu
 * keinem Betrieb.
 */
/** Betriebe, bei denen dieser Browser schon sammelt. */
async function betriebeDesBrowsers() {
  const { data: m, error } = await db.from('memberships').select('tenant_id');
  if (error || !m?.length) { if (error) console.error('memberships', error); return []; }
  const { data: t } = await db.from('tenants')
    .select('slug, name').in('id', m.map((x) => x.tenant_id)).order('name');
  return t ?? [];
}

/** Mehr als einer: Der Kunde soll sagen, wo er gerade steht. */
function betriebeZurWahl(liste) {
  $('betrieb').textContent = 'Wo bist du gerade?';
  $('kopf-zeile').textContent = 'Deine Stempelkarten';
  $('meldung').innerHTML = `<div class="karte">
      <p class="marke">Deine Betriebe</p>
      ${liste.map((b) => `<button data-betrieb="${h(b.slug)}"
         style="margin-bottom:var(--s2)">${h(b.name)}</button>`).join('')}
    </div>`;
  $('meldung').querySelectorAll('[data-betrieb]').forEach((k) => {
    k.onclick = () => {
      const u = new URL(location.href);
      u.searchParams.set('b', k.dataset.betrieb);
      location.replace(u);
    };
  });
}

async function starten() {
  try {
    konfig = await import('./config.js');
  } catch {
    melden('Es fehlt <code>web/app/config.js</code>. Kopiere '
         + '<code>config.example.js</code> und trage die Werte deines '
         + 'Supabase-Projekts ein.');
    return;
  }
  if (!konfig.SUPABASE_URL || konfig.SUPABASE_URL.includes('DEIN-PROJEKT')) {
    melden('<code>web/app/config.js</code> ist nicht ausgefüllt.');
    return;
  }
  rechtlichesZeichnen();

  db = createClient(konfig.SUPABASE_URL, konfig.SUPABASE_ANON_KEY,
    { auth: { storageKey: 'treuebiss-kunde' } });
  $('fuss').textContent = 'Deine Karte liegt in diesem Browser. '
    + 'Ohne Konto lässt sie sich nicht auf ein anderes Gerät mitnehmen.';

  /*
   * Steht in der Adresse und im Gedaechtnis nichts, fragen wir den Server:
   * Wo sammelt dieser Browser schon? Das ist die bessere Quelle - sie
   * ueberlebt geloeschte Browserdaten, und sie greift auch beim allerersten
   * Start vom Startbildschirm, wo das Gedaechtnis noch leer ist.
   *
   * Nur mit vorhandener Sitzung: Wer die nackte Adresse ohne Anmeldung
   * oeffnet, hat ohnehin nichts nachzuschlagen - und dafuer eigens ein
   * anonymes Konto anzulegen waere Datensammeln ohne Zweck.
   */
  if (!slug) {
    const { data } = await db.auth.getSession();
    if (data.session) {
      const gefunden = await betriebeDesBrowsers();
      if (gefunden.length === 1) {
        slug = gefunden[0].slug;
      } else if (gefunden.length > 1) {
        betriebeZurWahl(gefunden);
        return;
      }
    }
  }

  if (!slug) {
    // Fuer einen Baeckereikunden geschrieben, nicht fuer den Entwickler:
    // Was `?b=` bedeutet, hilft ihm nicht weiter.
    melden('Diese Seite gehört noch zu keinem Betrieb. Scanne den QR-Code '
         + 'am Tresen oder öffne den Link, den dein Betrieb dir gegeben hat.');
    return;
  }

  // Kam der Betrieb nicht aus der Adresse, gehoert er dort hinein - dann
  // traegt ihn auch ein Lesezeichen, das jetzt gesetzt wird.
  if (slug !== ausAdresse) {
    const u = new URL(location.href);
    u.searchParams.set('b', slug);
    history.replaceState(null, '', u);
  }

  // Erst den letzten bekannten Stand zeigen, dann nachladen. Ohne das
  // starrt der Kunde an der Kasse auf eine leere Seite.
  const hatteStand = standLaden();
  if (hatteStand) allesZeichnen();

  try {
    const { data } = await db.auth.getSession();
    nutzerId = data.session?.user?.id ?? null;
    if (!nutzerId) {
      const { data: neu, error } = await db.auth.signInAnonymously();
      if (error) throw error;
      nutzerId = neu.user?.id ?? null;
    }
    if (!nutzerId) throw new Error('Keine Anmeldung zustande gekommen.');

    const stand = await datenHolen();
    if (stand === 'ok') betriebMerken(slug);
    if (stand === 'unbekannt') {
      melden('Diesen Betrieb gibt es hier nicht. Stimmt die Adresse?');
      return;
    }
    band('');
  } catch (e) {
    console.error('start', e);
    if (hatteStand) {
      band('Gerade offline. Angezeigt wird der letzte bekannte Stand; '
         + 'Sammeln und Einlösen gehen erst wieder mit Verbindung.');
      $('sammeln').disabled = true;
    } else {
      melden(fehlertext(e));
    }
  }
}

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('./sw.js').catch((e) => console.error('sw', e));
  });
}

starten();
