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
let einloesungen = [];   // eingeloeste Coupons dieses Kunden
let kartenSchluessel = null;  // identifiziert die Karte, nicht das Geraet
let scanLaeuft = false;
/*
 * Ist die Karte geloescht, darf nichts mehr nachladen.
 *
 * Frueher rief datenHolen activate_card und legte die Karte damit neu an -
 * die geloeschte kam zurueck, sobald der Kunde die App wechselte. Seit die
 * Karte erst mit dem ersten Stempel entsteht, kann das nicht mehr passieren.
 * Die Sperre bleibt trotzdem: Nach dem Loeschen gibt es nichts nachzuladen,
 * und ein Nachladen ins Leere zeigte kurz eine Vorschau statt "geloescht".
 */
let karteGeloescht = false;
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

/*
 * Der Tresen-Code aus der Adresse.
 *
 * Der QR an der Kasse fuehrt seit dem 02.09.2026 hierher statt nur den Token
 * zu tragen: .../app/?b=<betrieb>&tresen=<token>. Damit ist ein Scan am Tresen
 * der vollstaendige Einstieg - Karte anlegen und ersten Stempel holen in einem
 * Zug. Vorher konnte gerade der auffaelligste Code im Laden keine Karte
 * anlegen, weil er den Betrieb nicht kennt.
 */
function tresenAusAdresse() {
  const s = new URLSearchParams(location.search).get('tresen')?.trim();
  return /^[0-9a-z]{8,64}$/i.test(s ?? '') ? s : null;
}

/** Der Kartenschluessel aus einem Umzugslink oder einem Wallet-Pass. */
function schluesselAusAdresse() {
  const k = new URLSearchParams(location.search).get('karte')?.trim();
  return /^[0-9a-f]{64}$/.test(k ?? '') ? k : null;
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
/** "1 Stempel" oder "5 Stempel" - der Singular faellt sonst jedem auf. */
function stempelWort(n) {
  return n === 1 ? '1 Stempel' : `${n} Stempel`;
}

function fehlertext(fehler) {
  if (istNetzfehler(fehler)) return 'Gerade keine Verbindung. Bitte später noch einmal.';
  const t = (fehler?.message ?? '') + (fehler?.details ?? '');
  // Muss vor der allgemeinen 23505-Pruefung stehen: Ein bereits eingeloester
  // Coupon meldet denselben Code, ist aber kein doppelter Beleg.
  if (/offer already redeemed/i.test(t)) return 'Dieses Angebot hast du schon eingelöst.';
  // Derselbe Beleg zaehlt nur einmal - das ist kein Fehler des Kunden,
  // deshalb hier auch kein Fehlerton.
  if (fehler?.code === '23505' || /duplicate key|stamp_proofs/i.test(t)) {
    return 'Dieser Beleg wurde schon gezählt.';
  }
  // Der QR vom Wallet-Pass, in den Stempel-Scanner gehalten. Er sieht aus wie
  // ein Code zum Sammeln, ist aber der zum Mitnehmen.
  if (/card link is not a proof/i.test(t)) {
    return 'Das ist der Code zum Mitnehmen deiner Karte, kein Kassenbon. '
         + 'Öffne ihn auf dem neuen Gerät, dann zieht die Karte dorthin um.';
  }
  /*
   * Der haeufigste Grund ist nicht ein Tippfehler, sondern eine Karte, die es
   * nicht mehr gibt: geloescht, oder der Link stammt von einem Geraet, das
   * seine Karte schon weitergegeben hat. "Gibt es hier nicht" laesst den
   * Kunden damit allein - er steht vor einer Vorschau und weiss nicht, ob er
   * etwas falsch gemacht hat.
   */
  if (/card not found/i.test(t)) {
    return 'Diese Karte gibt es nicht mehr — gelöscht, oder sie ist schon '
         + 'umgezogen. Hier anzufangen geht trotzdem: Der erste Stempel legt '
         + 'eine neue Karte an.';
  }
  if (/invalid card token/i.test(t)) return 'Der Kartenschlüssel ist unvollständig.';
  if (/device already has a card here/i.test(t)) {
    /*
     * Die Zahlen stehen in `details`, die adopt_card mitgibt. Ohne sie waere
     * die Meldung eine Sackgasse: Sie nennt, was nicht geht, und verschweigt,
     * was ginge. Fehlen sie doch einmal, bleibt der Satz trotzdem richtig.
     */
    const z = /hier=(\d+) dort=(\d+)/.exec(t);
    const staende = z
      ? `Hier liegen ${stempelWort(+z[1])}, die andere Karte bringt `
        + `${stempelWort(+z[2])} mit. `
      : '';
    return staende
         + 'Zwei Karten lassen sich nicht zusammenlegen. Wenn du die Karte auf '
         + 'diesem Gerät weiter unten löschst, zieht die andere ein.';
  }
  if (/offer not redeemable/i.test(t)) return 'Dieses Angebot lässt sich nicht einlösen.';
  if (/offer not valid today/i.test(t)) return 'Dieses Angebot gilt heute nicht.';
  if (/no membership for this tenant/i.test(t)) {
    return 'Dafür brauchst du erst eine Karte bei diesem Betrieb.';
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

/*
 * Haengt das Manifest dieses Betriebs ein.
 *
 * Das statische Manifest hat `start_url: "./"` - ohne Betrieb. Wer die Seite
 * so auf den Startbildschirm legt, startet spaeter ins Leere, und auf iOS
 * gibt es keinen Ausweg: Eine Web-App auf dem Startbildschirm hat ihren
 * eigenen Speicher, in dem weder ein gemerkter Betrieb noch eine
 * Mitgliedschaft liegt. Das Manifest je Betrieb traegt den Betrieb in der
 * `start_url` - und nebenbei den Namen des Betriebs statt "TreueBiss".
 */
/*
 * Zurueck auf das allgemeine Manifest.
 *
 * Das Schnipsel im head setzt den Verweis blind aus der Adresse - es kann
 * nicht wissen, ob es den Betrieb gibt. Laesst sich keiner laden, bliebe
 * sonst ein Verweis ins Leere stehen, und die Seite haette gar kein
 * Manifest mehr.
 */
function manifestZuruecksetzen() {
  document.querySelector('link[rel="manifest"]')
    ?.setAttribute('href', './manifest.webmanifest');
}

async function manifestSetzen() {
  const pfad = `./m/${betrieb.slug}.webmanifest`;
  const verweis = document.querySelector('link[rel="manifest"]');
  try {
    // Der Verweis steht schon: Das Schnipsel im head hat ihn synchron aus der
    // Adresse gesetzt, weil ein Tausch von hier aus zu spaet kaeme. Hier wird
    // nur noch nachgesehen, ob es die Datei ueberhaupt gibt - ohne den
    // Bauschritt waere ein Verweis ins Leere schlechter als das allgemeine
    // Manifest.
    const da = await fetch(pfad, { method: 'HEAD' });
    if (!da.ok) {
      verweis?.setAttribute('href', './manifest.webmanifest');
      return;
    }
  } catch {
    verweis?.setAttribute('href', './manifest.webmanifest');
    return;
  }
  verweis?.setAttribute('href', pfad);
}

/*
 * Aeltere iOS-Fassungen lesen kein Manifest, aber diesen Titel - und manche
 * Browser nehmen ihn auch fuer die Verknuepfung, die sie beim Teilen anlegen.
 *
 * Bewusst nicht in manifestSetzen(): Der Titel haengt nicht davon ab, ob es
 * die Manifest-Datei gibt. Dort stand er vorher und wurde dadurch in Faellen
 * gar nicht gesetzt, in denen er haette gesetzt werden muessen.
 */
function appleTitelSetzen(name) {
  let meta = document.querySelector('meta[name="apple-mobile-web-app-title"]');
  if (!meta) {
    meta = document.createElement('meta');
    meta.setAttribute('name', 'apple-mobile-web-app-title');
    document.head.appendChild(meta);
  }
  meta.setAttribute('content', name);
}

function allesZeichnen() {
  $('betrieb').textContent = betrieb.name;
  document.title = betrieb.name + ' – TreueBiss';
  appleTitelSetzen(betrieb.name);
  manifestSetzen();
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
    ? (kartenSchluessel
        ? 'Noch kein Stempel auf der Karte.'
        // Noch keine Karte: Sie entsteht mit dem ersten Stempel.
        : 'Deine Karte entsteht mit dem ersten Stempel.')
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

/*
 * Heute in Europe/Berlin als YYYY-MM-DD, damit sich die Datumsfelder aus der
 * Datenbank direkt vergleichen lassen - ISO-Datumstexte sind zeichenweise in
 * derselben Reihenfolge wie zeitlich. 'sv-SE' ist der kuerzeste Weg zu diesem
 * Format, das Land spielt dabei keine Rolle.
 */
const heute = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'Europe/Berlin' });

/*
 * Den Zeitraum prueft eigentlich die Policy `offers_read`. Hier steht er ein
 * zweites Mal, weil der Betriebsinhaber ueber `offers_owner_read` auch
 * abgelaufene Angebote lesen darf: Ohne diesen Filter saehe ausgerechnet er
 * in der Kundenansicht etwas anderes als seine Kunden - und wuerde beim
 * Nachsehen glauben, alles sei in Ordnung.
 */
function laeuft(a, tag = heute()) {
  return (!a.valid_from || a.valid_from <= tag) && (!a.valid_to || a.valid_to >= tag);
}

/*
 * Ist dieser Coupon fuer heute verbraucht?
 *
 * Dieselbe Rechnung wie in `redeem_offer`: Bei 'taeglich' sperrt der Tag, bei
 * 'einmal' ein Wert, der kein Tag ist. Was hier herauskommt, ist nur die
 * Anzeige - die Sperre selbst steht im Eindeutigkeitsschluessel der Tabelle
 * und gilt auch dann, wenn dieser Code luegt.
 */
function verbraucht(a) {
  const schluessel = a.redeem_limit === 'taeglich' ? heute() : '-infinity';
  return einloesungen.some((e) => e.offer_id === a.id && e.sperre === schluessel);
}

function angeboteZeichnen(liste) {
  liste = liste?.filter((a) => laeuft(a));
  if (!liste?.length) { $('angebote-bereich').classList.add('verborgen'); return; }
  $('angebote').innerHTML = liste.map((a) => {
    if (!a.is_redeemable) {
      return `<div class="angebot">
        <b>${h(a.title)}</b>
        ${a.description ? `<span>${h(a.description)}</span>` : ''}
      </div>`;
    }
    const weg = verbraucht(a);
    // Beim taeglichen Coupon ist "heute" die ganze Auskunft: Morgen steht er
    // wieder da, und das soll der Kunde auch lesen koennen.
    const vermerk = weg
      ? (a.redeem_limit === 'taeglich' ? 'Heute schon eingelöst' : 'Eingelöst')
      : '';
    return `<div class="angebot${weg ? ' verbraucht' : ''}">
      <b>${h(a.title)}</b>
      ${a.description ? `<span>${h(a.description)}</span>` : ''}
      ${weg ? `<p class="vermerk">${vermerk}</p>` : `
        <div id="acode-${h(a.id)}" class="verborgen">
          <label for="acode-feld-${h(a.id)}">Einlöse-Code</label>
          <input id="acode-feld-${h(a.id)}" autocapitalize="off" spellcheck="false"
                 placeholder="Die Verkaufskraft tippt ihn ein">
        </div>
        <button class="still schmal" data-angebot="${h(a.id)}">Einlösen</button>`}
    </div>`;
  }).join('');

  $('angebote').querySelectorAll('[data-angebot]').forEach((k) => {
    k.onclick = () => angebotEinloesen(k.dataset.angebot);
  });
  $('angebote-bereich').classList.remove('verborgen');
}

// -------------------------------------------------------------------- Daten

async function datenHolen() {
  const { data: reihen, error } = await db
    .from('tenants').select('*').eq('slug', slug).limit(1);
  if (error) throw error;
  if (!reihen?.length) return 'unbekannt';
  betrieb = reihen[0];

  /*
   * Die Karte entsteht in der Datenbank, nicht im Browser - und erst mit dem
   * ersten Stempel.
   *
   * Bis zum 02.09.2026 stand hier ein upsert auf memberships, danach ein
   * Aufruf von activate_card. Beides legte die Karte beim blossen Oeffnen des
   * Links an: "Teilnehmer" zaehlte jeden, der einmal hingeschaut hat. Jetzt
   * entsteht sie in issue_stamp. Wer nur schaut, hinterlaesst nichts, und die
   * Seite zeigt bis dahin eine Vorschau.
   */

  const [{ count }, { data: gs }, { data: an }, { data: el }] = await Promise.all([
    db.from('stamps').select('id', { count: 'exact', head: true }).eq('tenant_id', betrieb.id),
    db.from('vouchers').select('*').eq('tenant_id', betrieb.id).eq('is_redeemed', false),
    db.from('offers').select('*').eq('tenant_id', betrieb.id).order('created_at'),
    // Die Policy laesst nur die eigenen durch, ein Filter auf den Nutzer
    // waere hier bloss Zierde.
    db.from('offer_redemptions').select('offer_id, sperre').eq('tenant_id', betrieb.id),
  ]);

  // Der eigene Kartenschluessel. Die Policy laesst nur die eigene Zeile durch.
  const { data: mine } = await db.from('memberships')
    .select('card_token').eq('tenant_id', betrieb.id).maybeSingle();
  kartenSchluessel = mine?.card_token ?? null;

  stempel = count ?? 0;
  gutscheine = gs ?? [];
  einloesungen = el ?? [];
  standSichern();
  allesZeichnen();
  angeboteZeichnen(an);
  $('umzug-bereich').classList.toggle('verborgen', !kartenSchluessel);
  $('loeschen-bereich').classList.toggle('verborgen', !kartenSchluessel);

  walletKnoepfeZeigen();
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
  // War das der erste Stempel, ist die Karte eben erst entstanden.
  const ersterStempel = !kartenSchluessel;
  await datenHolen();
  melden(neu?.voucher_id
    ? 'Karte voll! Dein Gutschein liegt bereit.'
    : ersterStempel
      ? 'Deine Karte ist angelegt, der erste Stempel ist drauf.'
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

/*
 * Coupon einloesen. Bewusst derselbe Ablauf wie beim Gutschein: Verlangt der
 * Betrieb einen Code, erscheint erst das Feld und dann wird gesendet. Ein
 * Kunde soll nicht zwei verschiedene Regeln erleben, je nachdem was er
 * gerade einloest.
 */
async function angebotEinloesen(angebotId) {
  const feld = $(`acode-feld-${angebotId}`);
  const huelle = $(`acode-${angebotId}`);

  if (betrieb.requires_redeem_code && huelle?.classList.contains('verborgen')) {
    huelle.classList.remove('verborgen');
    feld?.focus();
    melden('Bitte lass die Verkaufskraft den Einlöse-Code eingeben.');
    return;
  }
  const code = betrieb.requires_redeem_code ? (feld?.value.trim() || null) : null;
  if (betrieb.requires_redeem_code && !code) { melden('Es fehlt der Einlöse-Code.'); return; }

  melden('');
  const knopf = $('angebote').querySelector(`[data-angebot="${angebotId}"]`);
  beschaeftigt(knopf, true, 'Wird eingelöst …');
  const { error } = await db.rpc('redeem_offer', { p_offer_id: angebotId, p_code: code });
  beschaeftigt(knopf, false);
  if (error) { console.error('redeem_offer', error); melden(fehlertext(error)); return; }
  await datenHolen();
  melden('Eingelöst. Guten Appetit!', true);
}

/*
 * Den Umzugscode zeigen.
 *
 * Erst auf Knopfdruck: Ein dauerhaft sichtbarer Code waere ein Inhaberpapier,
 * das bei jedem Blick aufs Handy mitgelesen werden koennte.
 *
 * Der Code traegt denselben Link, den spaeter ein Wallet-Pass tragen wird.
 */
async function umzugZeigen() {
  if (!kartenSchluessel) { melden('Für diese Karte gibt es noch keinen Code.'); return; }
  const huelle = $('umzug-code');
  if (!huelle.classList.contains('verborgen')) { huelle.classList.add('verborgen'); return; }

  const ziel = new URL(location.href);
  ziel.search = '';
  ziel.searchParams.set('b', slug);
  ziel.searchParams.set('karte', kartenSchluessel);

  const knopf = $('umzug-zeigen');
  beschaeftigt(knopf, true, 'Wird erzeugt …');
  try {
    const { default: QRCode } = await import('https://esm.sh/qrcode@1.5.4');
    await QRCode.toCanvas($('umzug-qr'), ziel.toString(), { width: 240, margin: 1 });
    huelle.classList.remove('verborgen');
  } catch (e) {
    console.error('umzug-qr', e);
    melden('Der Code liess sich nicht erzeugen. Gibt es gerade Verbindung?');
  } finally {
    beschaeftigt(knopf, false);
  }
}

/*
 * Karte loeschen - der einzige Weg, auf dem ein Kunde ohne Konto seine Daten
 * wieder loswird. Es gibt keine Adresse, unter der er einen Loeschantrag
 * stellen koennte, und der Betrieb kann ihn nicht heraussuchen.
 *
 * Danach wird nicht neu geladen. datenHolen() legt bei jedem Start eine
 * Mitgliedschaft an - ein Neuladen erzeugte also sofort wieder eine leere
 * Karte, und der Kunde saehe aus wie eine fehlgeschlagene Loeschung.
 */
async function karteLoeschen() {
  const knopf = $('loeschen-ja');
  beschaeftigt(knopf, true, 'Wird gelöscht …');
  try {
    /*
     * Zuerst den Google-Pass stilllegen, dann erst loeschen. Umgekehrt ginge
     * es nicht: Die Objektkennung leitet sich aus dem Kartenschluessel ab,
     * und der steht in der Mitgliedschaft, die es gleich nicht mehr gibt.
     *
     * Scheitert das, wird trotzdem geloescht. Das Recht auf Loeschung darf
     * nicht daran haengen, ob Google gerade antwortet - der Pass bliebe dann
     * als Karteileiche stehen, aber die Daten sind weg. Umgekehrt waere es
     * schlimmer.
     */
    let passStillgelegt = false;
    if (betrieb?.id) {
      try {
        const { data: w } = await db.functions.invoke('wallet-google', {
          body: { tenant_id: betrieb.id, aktion: 'stilllegen' },
        });
        passStillgelegt = !!w?.stillgelegt;
      } catch (e) {
        console.warn('wallet-google stilllegen', e);
      }
    }

    const { data, error } = await db.rpc('delete_card', { p_slug: slug });
    if (error) throw error;
    const erg = Array.isArray(data) ? data[0] : data;

    karteGeloescht = true;
    try {
      localStorage.removeItem(schluessel());
      if (betriebGemerkt() === slug) localStorage.removeItem(LETZTER);
    } catch { /* privater Modus: dann bleibt nur der Server-Stand, und der ist weg */ }

    /*
     * Den Betrieb aus der Adresse nehmen.
     *
     * Bleibt er stehen, legt ein Neuladen die Karte sofort wieder an - und der
     * Kunde, der gerade geloescht hat, steht vor einer neuen Karte mit einem
     * Stempel darauf. Ohne den Betrieb landet er auf der Auswahl, und das ist
     * die ehrliche Antwort: Es gibt hier nichts mehr.
     */
    const u = new URL(location.href);
    for (const k of ['b', 'karte', 'tresen']) u.searchParams.delete(k);
    history.replaceState(null, '', u);

    /*
     * Bleibt keine Karte, ist das anonyme Konto eine Kennung ohne eine
     * einzige Zeile daran. Abmelden gibt sie endgueltig auf - beim naechsten
     * Start entsteht eine neue. Mit weiteren Karten waere das ein Verlust:
     * Wer sich abmeldet, kommt an eine anonyme Sitzung nie wieder heran.
     */
    if ((erg?.cards_left ?? 0) === 0) await db.auth.signOut();

    for (const id of ['karte-bereich', 'gutscheine-bereich', 'angebote-bereich',
                      'umzug-bereich', 'loeschen-bereich']) {
      $(id).classList.add('verborgen');
    }
    band('');
    $('betrieb').textContent = erg?.tenant_name ?? 'Karte gelöscht';
    $('kopf-zeile').textContent = 'Karte gelöscht';
    melden('Deine Karte ist weg — mit allen Stempeln, Gutscheinen und '
         + 'Kaufnachweisen. '
         + (passStillgelegt
             ? 'Der Google-Pass ist als abgelaufen markiert; löschen kannst '
             + 'nur du ihn, er liegt in deinem Konto. '
             : '')
         + 'Ein Pass in Apple Wallet bleibt auf dem Handy stehen — den '
         + 'entfernst du dort selbst.'
         + ((erg?.cards_left ?? 0) > 0
            ? ' Deine Karten in anderen Betrieben sind unberührt.'
            : ''), true);
  } catch (e) {
    console.error('delete_card', e);
    melden(fehlertext(e));
  } finally {
    beschaeftigt(knopf, false);
  }
}

/*
 * Welche Wallet gehoert auf dieses Geraet?
 *
 * Beide Knoepfe ueberall zu zeigen ist bestenfalls verwirrend: Ein
 * Google-Pass nuetzt auf dem iPhone wenig, ein .pkpass auf Android gar nichts.
 *
 * iPadOS gibt sich seit Version 13 als Macintosh aus - das ist der Grund fuer
 * die zweite Bedingung. Ein Mac mit Mausbedienung hat keine Beruehrungspunkte,
 * ein iPad schon.
 *
 * Am Rechner bleiben beide stehen: Dort ist keines der beiden falsch. Ein
 * .pkpass laesst sich in der Vorschau oeffnen und per AirDrop aufs iPhone
 * schicken, ein Google-Pass landet im Konto und damit auf dem Telefon.
 */
function plattform() {
  const ua = navigator.userAgent || '';
  if (/Android/i.test(ua)) return 'android';
  if (/iPhone|iPad|iPod/i.test(ua)) return 'ios';
  if (/Macintosh/.test(ua) && (navigator.maxTouchPoints ?? 0) > 1) return 'ios';
  return 'sonst';
}

/** Zeigt nur den Knopf, der auf diesem Geraet etwas bewirkt. */
function walletKnoepfeZeigen() {
  const p = plattform();
  const g = $('wallet-google');
  const a = $('wallet-apple');
  g.classList.toggle('verborgen', p === 'ios');
  a.classList.toggle('verborgen', p === 'android');

  /*
   * Mit ?diagnose=1 steht das Ergebnis auf der Seite.
   *
   * Auf einem iPhone gibt es keine Entwicklerkonsole zur Hand, und die
   * Emulation am Rechner beantwortet nicht, was das echte Geraet meldet.
   * Ohne diese Anzeige bleibt nur Raten - und davon hatten wir genug.
   */
  if (new URLSearchParams(location.search).get('diagnose') !== '1') return;
  const feld = $('plattform-diagnose');
  if (!feld) return;
  feld.textContent =
      `erkannt: ${p}\n`
    + `maxTouchPoints: ${navigator.maxTouchPoints ?? 'undefiniert'}\n`
    + `Google verborgen: ${g.classList.contains('verborgen')} / sichtbar: ${g.offsetParent !== null}\n`
    + `Apple verborgen: ${a.classList.contains('verborgen')} / sichtbar: ${a.offsetParent !== null}\n`
    + `Kennung: ${navigator.userAgent}`;
  feld.classList.remove('verborgen');
}

/*
 * "Zu Google Wallet hinzufuegen".
 *
 * Das Save-JWT wird mit dem privaten Schluessel eines Google-Dienstkontos
 * signiert - das kann nur der Server. Die Edge Function liefert den fertigen
 * Link, geoeffnet wird er hier.
 *
 * Das Fenster wird vor dem Warten geoeffnet und danach umgelenkt: Safari
 * blockiert ein window.open, das erst nach einem await kommt, weil es dann
 * nicht mehr als Folge des Klicks gilt.
 */
async function zuGoogleWallet() {
  if (!betrieb?.id) return;
  const knopf = $('wallet-google');
  const fenster = window.open('', '_blank');
  beschaeftigt(knopf, true, 'Wird vorbereitet …');
  try {
    const { data, error } = await db.functions.invoke('wallet-google', {
      body: { tenant_id: betrieb.id },
    });
    if (error || !data?.url) {
      fenster?.close();
      const grund = data?.fehler ?? error?.message ?? '';
      melden(/eingerichtet/i.test(grund)
        ? 'Google Wallet ist für TreueBiss noch nicht eingerichtet.'
        : 'Der Wallet-Pass liess sich nicht erzeugen.');
      console.error('wallet-google', error ?? data);
      return;
    }
    if (fenster) fenster.location = data.url; else location.href = data.url;
  } catch (e) {
    fenster?.close();
    console.error('wallet-google', e);
    melden(istNetzfehler(e) ? 'Gerade keine Verbindung.' : 'Der Wallet-Pass liess sich nicht erzeugen.');
  } finally {
    beschaeftigt(knopf, false);
  }
}

/*
 * "Zu Apple Wallet hinzufuegen".
 *
 * Anders als bei Google gibt es keinen Link, den Apple aufloest: Der Pass ist
 * eine Datei. Die Funktion liefert sie, und das Geraet oeffnet sie - unter iOS
 * uebernimmt Wallet dann selbst.
 *
 * Deshalb hier kein window.open, sondern ein Objekt-URL: Ein neues Fenster
 * mit einem Dateidownload bleibt sonst leer stehen.
 */
async function zuAppleWallet() {
  if (!betrieb?.id) return;
  const knopf = $('wallet-apple');
  beschaeftigt(knopf, true, 'Wird erzeugt …');
  try {
    const { data: sitzung } = await db.auth.getSession();
    const antwort = await fetch(`${konfig.SUPABASE_URL}/functions/v1/wallet-apple`, {
      method: 'POST',
      headers: {
        apikey: konfig.SUPABASE_ANON_KEY,
        Authorization: `Bearer ${sitzung.session?.access_token ?? ''}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ tenant_id: betrieb.id }),
    });
    if (!antwort.ok) {
      const grund = await antwort.json().catch(() => ({}));
      melden(/eingerichtet/i.test(grund?.fehler ?? '')
        ? 'Apple Wallet ist für TreueBiss noch nicht eingerichtet.'
        : 'Der Wallet-Pass liess sich nicht erzeugen.');
      console.error('wallet-apple', grund);
      return;
    }
    const paket = await antwort.blob();
    const ziel = URL.createObjectURL(paket);
    // Ein Anker statt location: Safari behandelt den Pass sonst als
    // Seitenwechsel und zeigt eine leere Seite.
    const a = document.createElement('a');
    a.href = ziel;
    a.download = `${slug}.pkpass`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(ziel), 30000);
  } catch (e) {
    console.error('wallet-apple', e);
    melden(istNetzfehler(e) ? 'Gerade keine Verbindung.' : 'Der Wallet-Pass liess sich nicht erzeugen.');
  } finally {
    beschaeftigt(knopf, false);
  }
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
    /*
     * Dem Kunden nutzt "https" nichts - er kann die Adresse nicht aendern,
     * und ueber den Aufsteller kommt er ohnehin richtig an. Was er tun kann,
     * steht deshalb zuerst.
     */
    melden('Die Kamera lässt sich hier nicht öffnen. Tippe die Nummer vom '
         + 'Kassenbon ein — das geht genauso.');
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

/*
 * Die teilnehmenden Betriebe zur Auswahl.
 *
 * TreueBiss ist der Einstiegspunkt fuer den Kunden - wer die Adresse ohne
 * Betrieb oeffnet, muss den seinen finden koennen. Der Aufsteller bleibt der
 * schnellere Weg, aber er setzt voraus, dass der Kunde gerade im Laden steht.
 *
 * Gelesen wird aus betriebe_oeffentlich: zwei Spalten, ohne Anmeldung. Ein
 * anonymes Konto entsteht erst, wenn wirklich eine Karte angelegt wird.
 */
let betriebeAlle = [];

async function verzeichnisZeigen() {
  const feld = $('betriebsliste'), suche = $('betrieb-suche');
  feld.innerHTML = '<p class="leer-text">Betriebe werden geladen …</p>';

  const { data, error } = await db.from('betriebe_oeffentlich')
    .select('slug, name').order('name');
  if (error) {
    console.error('betriebe_oeffentlich', error);
    feld.innerHTML = '<p class="leer-text">Die Liste liess sich nicht laden. '
                   + 'Scanne den Aufsteller am Tresen.</p>';
    return;
  }
  betriebeAlle = data ?? [];
  if (!betriebeAlle.length) {
    feld.innerHTML = '<p class="leer-text">Hier ist noch kein Betrieb eingetragen.</p>';
    return;
  }

  // Ein Suchfeld erst, wenn die Liste laenger wird als der Bildschirm.
  const brauchtSuche = betriebeAlle.length > 6;
  suche.classList.toggle('verborgen', !brauchtSuche);
  $('suche-schild').classList.toggle('verborgen', !brauchtSuche);
  suche.oninput = () => listeZeichnen(suche.value);
  listeZeichnen('');
}

function listeZeichnen(filter) {
  const feld = $('betriebsliste');
  const wort = filter.trim().toLowerCase();
  const treffer = wort
    ? betriebeAlle.filter((b) => b.name.toLowerCase().includes(wort))
    : betriebeAlle;

  if (!treffer.length) {
    feld.innerHTML = '<p class="leer-text">Kein Betrieb mit diesem Namen.</p>';
    return;
  }
  feld.innerHTML = treffer
    .map((b) => `<button data-betrieb="${h(b.slug)}">${h(b.name)}</button>`).join('');
  feld.querySelectorAll('[data-betrieb]').forEach((k) => {
    k.onclick = () => zumBetrieb(k.dataset.betrieb);
  });
}

/*
 * Den Aufsteller lesen, wenn noch keine Karte da ist.
 *
 * Eigene Schleife statt scannerOeffnen: Dort haengt alles am Kartenbereich -
 * das Belegfeld, der Abbrechen-Knopf, das Ergebnis geht an stempelHolen. Hier
 * gibt es keine Karte, an die ein Stempel gehen koennte; gesucht wird der
 * Betrieb.
 */
async function aufstellerLesen() {
  const knopf = $('aufsteller-scannen'), video = $('einstieg-video');
  melden('');

  if (!window.isSecureContext) {
    melden('Die Kamera lässt sich hier nicht öffnen. Scanne den Aufsteller '
         + 'mit der Kamera deines Handys — der Code führt direkt hierher.');
    return;
  }
  let strom;
  try {
    strom = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
  } catch {
    melden('Kein Zugriff auf die Kamera. Scanne den Aufsteller mit der '
         + 'Kamera deines Handys — der Code führt direkt hierher.');
    return;
  }

  const lesen = await leserBauen();
  if (!lesen) {
    strom.getTracks().forEach((s) => s.stop());
    melden('Dieser Browser kann keine QR-Codes lesen. Scanne den Aufsteller '
         + 'mit der Kamera deines Handys.');
    return;
  }

  $('einstieg-scanner').classList.remove('verborgen');
  $('einstieg-abbrechen').classList.remove('verborgen');
  knopf.classList.add('verborgen');
  video.srcObject = strom;
  await video.play();
  scanLaeuft = true;

  const schliessen = () => {
    scanLaeuft = false;
    strom.getTracks().forEach((s) => s.stop());
    video.srcObject = null;
    $('einstieg-scanner').classList.add('verborgen');
    $('einstieg-abbrechen').classList.add('verborgen');
    knopf.classList.remove('verborgen');
  };
  $('einstieg-abbrechen').onclick = () => { schliessen(); melden(''); };

  const leinwand = document.createElement('canvas');
  const suchen = async () => {
    if (!scanLaeuft) return;
    const wert = await lesen(video, leinwand);
    if (!wert) { requestAnimationFrame(suchen); return; }

    const slug = slugAusCode(wert);
    if (slug) {
      /*
       * Gegen das Verzeichnis halten, statt blind zu laden. Ein Code mit
       * einem unbekannten Betrieb fuehrte sonst auf eine Karte, die es nicht
       * gibt - und die Meldung dort sagte "Stimmt die Adresse?", obwohl der
       * Kunde nichts getippt hat.
       */
      if (betriebeAlle.length && !betriebeAlle.some((b) => b.slug === slug)) {
        melden('Dieser Betrieb nimmt bei TreueBiss nicht (mehr) teil.');
        requestAnimationFrame(suchen);
        return;
      }
      let mit = null;
      try { mit = new URL(wert, location.href).searchParams.get('tresen'); } catch { /* egal */ }
      schliessen(); zumBetrieb(slug, mit); return;
    }

    /*
     * Weiterlesen statt abbrechen: Im Bild kann noch etwas anderes liegen,
     * und ein Abbruch bei jedem falschen Code machte das Scannen im Laden
     * unbrauchbar.
     */
    /*
     * Die beiden Codes, die im Laden ausserdem herumliegen, beim Namen
     * nennen. "Gehoert nicht zu TreueBiss" waere bei beiden schlicht falsch -
     * sie gehoeren dazu, nur an eine andere Stelle.
     *
     * Der Tresen-Code ist der wahrscheinlichste Fehlgriff: Er haengt gross
     * auf einem Bildschirm direkt vor dem Kunden. Er traegt aber keinen
     * Betrieb, deshalb laesst sich daraus keine Karte anlegen.
     */
    melden(
      /^tresen:/.test(wert)
        ? 'Das ist der Stempel-Code der Kasse. Er gilt erst, wenn du eine '
          + 'Karte hast — wähle zuerst deinen Betrieb aus der Liste.'
      : /^V0;/.test(wert)
        ? 'Das ist ein Kassenbon. Den brauchst du erst, wenn du eine Karte '
          + 'hast — wähle zuerst deinen Betrieb aus der Liste.'
        : 'Dieser Code gehört nicht zu TreueBiss.');
    requestAnimationFrame(suchen);
  };
  requestAnimationFrame(suchen);
}

/*
 * Zieht den Betrieb aus dem Aufsteller-Code, oder null.
 *
 * Aus dem Code wird ausschliesslich der Parameter `b` genommen, und zumBetrieb
 * baut die Zieladresse aus location.href. Ein untergeschobener Code kann damit
 * hoechstens einen anderen Betrieb waehlen - wegleiten kann er nicht. Die
 * Form ist auf Kleinbuchstaben, Ziffern und Bindestrich begrenzt, damit auch
 * kein Pfad darin steckt.
 */
function slugAusCode(wert) {
  try {
    const u = new URL(wert, location.href);
    const b = u.searchParams.get('b');
    if (b && /^[a-z0-9-]{1,64}$/.test(b)) return b;
  } catch { /* kein URL - dann eben nicht */ }
  return null;
}

function zumBetrieb(slug, tresen) {
  const u = new URL(location.href);
  u.searchParams.set('b', slug);
  // Der Tresen-Code reist mit, wenn er im gescannten Bild stand: Dann ist der
  // eine Scan Anmeldung und Stempel zugleich.
  if (tresen) u.searchParams.set('tresen', tresen);
  else u.searchParams.delete('tresen');
  location.assign(u);
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
$('aufsteller-scannen').onclick = aufstellerLesen;
$('umzug-zeigen').onclick = umzugZeigen;
$('wallet-google').onclick = zuGoogleWallet;
$('wallet-apple').onclick = zuAppleWallet;
$('loeschen-fragen').onclick = () =>
  $('loeschen-nachfrage').classList.toggle('verborgen');
$('loeschen-nein').onclick = () =>
  $('loeschen-nachfrage').classList.add('verborgen');
$('loeschen-ja').onclick = karteLoeschen;

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
    manifestZuruecksetzen();
    // Fuer einen Baeckereikunden geschrieben, nicht fuer den Entwickler:
    // Was `?b=` bedeutet, hilft ihm nicht weiter.
    /*
     * Kein Satz mehr, sondern ein Weg.
     *
     * Hier stand "scanne den QR-Code am Tresen" - ohne Scanner - und "oeffne
     * den Link, den dein Betrieb dir gegeben hat". Das zweite war schlicht
     * falsch: Der Betrieb kennt keine einzige Adresse seiner Kunden, er kann
     * niemandem etwas schicken. Genau das ist der Punkt dieses Produkts.
     */
    $('einstieg').classList.remove('verborgen');
    await verzeichnisZeigen();
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

    /*
     * Traegt die Adresse einen Kartenschluessel, gehoert die Karte hierher
     * geholt - noch vor dem ersten Laden, sonst zeigte die Seite kurz eine
     * leere Karte und danach die volle.
     *
     * Fehler sind hier kein Abbruch: Der Link kann alt sein, und dann ist
     * die eigene Karte immer noch die richtige Antwort.
     */
    const mitgebracht = schluesselAusAdresse();
    if (mitgebracht) {
      const { error: uFehler } = await db.rpc('adopt_card', { p_token: mitgebracht });
      if (uFehler) {
        console.warn('adopt_card', uFehler);
        melden(fehlertext(uFehler));
      }
      // Den Schluessel aus der Adresse nehmen: Er gehoert nicht in den
      // Verlauf und nicht in eine geteilte Adresszeile.
      const u = new URL(location.href);
      u.searchParams.delete('karte');
      history.replaceState(null, '', u);
    }

    const stand = await datenHolen();
    if (stand === 'ok') betriebMerken(slug);

    /*
     * Nachladen, wenn die App wieder in den Vordergrund kommt.
     *
     * Ein Symbol auf dem Startbildschirm wird selten neu gestartet - es
     * bleibt im Hintergrund liegen und zeigt beim naechsten Blick den Stand
     * von gestern. Aendert der Betrieb ein Angebot, kam das bisher erst mit
     * dem naechsten Kaltstart an.
     *
     * Nur bei sichtbarer Seite und ohne laufende Aktion: Waehrend gesammelt
     * oder eingeloest wird, holt der Vorgang selbst nach.
     */
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState !== 'visible') return;
      if (scanLaeuft || sammelnLaeuft || karteGeloescht) return;
      datenHolen().catch((e) => console.warn('Nachladen fehlgeschlagen', e));
    });
    if (stand === 'unbekannt') {
      manifestZuruecksetzen();
      melden('Diesen Betrieb gibt es hier nicht. Stimmt die Adresse?');
      return;
    }

    /*
     * Kam der Kunde ueber den Tresen-Code, gehoert der Stempel jetzt dazu -
     * nach dem Laden, damit die Karte schon dasteht und der Stempel darauf
     * sichtbar faellt.
     *
     * Erst aus der Adresse nehmen, dann einloesen: Ein Neuladen duerfte den
     * Code sonst ein zweites Mal schicken. Abgewiesen wuerde er ohnehin, aber
     * der Kunde saehe eine Fehlermeldung fuer etwas, das er nicht getan hat.
     */
    const tresen = tresenAusAdresse();
    if (tresen) {
      const u = new URL(location.href);
      u.searchParams.delete('tresen');
      history.replaceState(null, '', u);
      await stempelHolen('tresen:' + tresen);
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
  /*
   * Einmal neu laden, wenn ein neuer Worker uebernimmt.
   *
   * Der Worker ruft skipWaiting und clients.claim - er uebernimmt also sofort.
   * Aber die Seite, die das Update angestossen hat, kam schon aus dem alten
   * Cache: Der erste Aufruf nach jedem Rollout laeuft mit der alten App, erst
   * der zweite bekommt die neue. Am 02.09.2026 hat genau dieser erste alte
   * Lauf auf dem Smartphone noch activate_card gerufen und eine Karte
   * angelegt, die es nach der neuen Regel gar nicht mehr geben durfte.
   *
   * Nur bei einem Wechsel, nicht bei der ersten Installation: Ohne Controller
   * vorher gab es nichts Altes, das laufen konnte. Und nur einmal je Sitzung,
   * sonst laeuft ein kaputter Worker in eine Schleife.
   */
  const hatteController = !!navigator.serviceWorker.controller;
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (!hatteController) return;
    try {
      if (sessionStorage.getItem('treuebiss:neu-geladen')) return;
      sessionStorage.setItem('treuebiss:neu-geladen', '1');
    } catch { /* ohne Speicher lieber gar nicht neu laden als endlos */ return; }
    if (scanLaeuft || sammelnLaeuft) return;   // nicht mitten im Vorgang
    location.reload();
  });
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('./sw.js').catch((e) => console.error('sw', e));
  });
}

starten();
