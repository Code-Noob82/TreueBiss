/*
 * Baut die signierten Daten eines TSE-Belegs nach und prüft die Signatur.
 *
 * Der QR-Code enthält NICHT die signierten Daten selbst, sondern die Felder,
 * aus denen sie sich zusammensetzen. Die Struktur steht in BSI TR-03151,
 * Anhang A: eine Folge DER-kodierter ASN.1-Objekte, NICHT in eine SEQUENCE
 * gepackt. Ein einziges Byte an der falschen Stelle, und jede echte Signatur
 * gilt als ungültig - deshalb ist der Nachbau gegen zwei echte TSE-Signaturen
 * geprüft und nicht geraten.
 */
import { KURVEN, pruefeSignatur, zahl } from "./kurven.ts";

const OID_ZERTIFIZIERTE_DATEN = "0.4.0.127.0.7.3.7.1.1";
const OID_VERFAHREN: Record<string, string> = {
  "ecdsa-plain-SHA256": "0.4.0.127.0.7.1.1.4.1.3",
  "ecdsa-plain-SHA384": "0.4.0.127.0.7.1.1.4.1.4",
};
const HASH_NAME: Record<string, string> = {
  "ecdsa-plain-SHA256": "SHA-256",
  "ecdsa-plain-SHA384": "SHA-384",
};
// Aus der Länge des Schlüssels folgt die Größe der Kurve, aber nicht welche:
// Zwei 256-Bit-Kurven sind am rohen Punkt nicht zu unterscheiden. Also beide
// versuchen - wer auf keiner liegt, fällt ohnehin durch.
const KURVEN_JE_GROESSE: Record<number, string[]> = {
  32: ["secp256r1", "brainpoolP256r1"],
  48: ["secp384r1", "brainpoolP384r1"],
};

// ------------------------------------------------------------------- DER
const laenge = (n: number): number[] =>
  n < 0x80 ? [n] : n <= 0xff ? [0x81, n] : [0x82, (n >> 8) & 0xff, n & 0xff];

const tlv = (tag: number, inhalt: ArrayLike<number>): number[] =>
  [tag, ...laenge(inhalt.length), ...Array.from(inhalt)];

function derGanzzahl(wert: bigint | string | number): number[] {
  let x = BigInt(wert);
  if (x === 0n) return tlv(0x02, [0]);
  const b: number[] = [];
  while (x > 0n) { b.unshift(Number(x & 0xffn)); x >>= 8n; }
  if (b[0] & 0x80) b.unshift(0);
  return tlv(0x02, b);
}

function derOid(oid: string): number[] {
  const t = oid.split(".").map(Number);
  const b = [40 * t[0] + t[1]];
  for (const teil of t.slice(2)) {
    const s: number[] = [];
    let x = teil;
    do { s.unshift(x & 0x7f); x >>= 7; } while (x > 0);
    for (let i = 0; i < s.length - 1; i++) s[i] |= 0x80;
    b.push(...s);
  }
  return tlv(0x06, b);
}

/** Vorzeichenbehaftetes Big-Endian, wie Javas BigInteger.toByteArray(). */
function zahlBytes(dez: string): number[] {
  let x = BigInt(dez);
  if (x === 0n) return [0];
  const b: number[] = [];
  while (x > 0n) { b.unshift(Number(x & 0xffn)); x >>= 8n; }
  if (b[0] & 0x80) b.unshift(0);
  return b;
}

const text = (s: string) => Array.from(new TextEncoder().encode(s));

/* OIDs der Kurven, wie sie in einem SPKI stehen. */
const KURVE_JE_OID: Record<string, string> = {
  "1.2.840.10045.3.1.7": "secp256r1",
  "1.3.132.0.34": "secp384r1",
  "1.3.36.3.3.2.8.1.1.7": "brainpoolP256r1",
  "1.3.36.3.3.2.8.1.1.11": "brainpoolP384r1",
};

export interface Schluessel {
  punkt: Uint8Array;
  /** Aus dem SPKI gelesen; bei einem rohen Punkt unbekannt. */
  kurve: string | null;
}

/*
 * Der Schlüssel steht im QR entweder als roher unkomprimierter Punkt
 * (0x04 || X || Y) oder als DER-kodiertes SPKI. Beides kommt in freier
 * Wildbahn vor: Die Testbelege tragen rohe Punkte, ein Bon aus dem
 * Lebensmitteleinzelhandel ein SPKI. Das SPKI ist dabei die bessere Auskunft,
 * weil es die Kurve mitnennt statt sie raten zu lassen.
 */
export function schluesselLesen(roh: Uint8Array): Schluessel | null {
  if (roh[0] === 0x04 && (roh.length - 1) % 2 === 0) {
    return { punkt: roh, kurve: null };
  }
  if (roh[0] !== 0x30) return null;

  let i = 0;
  const lies = (b: Uint8Array, pos: { i: number }) => {
    const tag = b[pos.i++];
    let len = b[pos.i++];
    if (len & 0x80) {
      const n = len & 0x7f;
      len = 0;
      for (let k = 0; k < n; k++) len = (len << 8) | b[pos.i++];
    }
    const wert = b.slice(pos.i, pos.i + len);
    pos.i += len;
    return { tag, wert };
  };
  try {
    const aussen = lies(roh, { i });
    if (aussen.tag !== 0x30) return null;
    const pos = { i: 0 };
    const alg = lies(aussen.wert, pos);
    const bits = lies(aussen.wert, pos);
    if (alg.tag !== 0x30 || bits.tag !== 0x03) return null;

    let kurve: string | null = null;
    const ap = { i: 0 };
    while (ap.i < alg.wert.length) {
      const o = lies(alg.wert, ap);
      if (o.tag !== 0x06) continue;
      const t = [Math.floor(o.wert[0] / 40), o.wert[0] % 40];
      let x = 0;
      for (const byte of o.wert.slice(1)) {
        x = (x << 7) | (byte & 0x7f);
        if (!(byte & 0x80)) { t.push(x); x = 0; }
      }
      const name = KURVE_JE_OID[t.join(".")];
      if (name) kurve = name;
    }
    // Erstes Byte des BIT STRING zählt die ungenutzten Bits.
    const punkt = bits.wert.slice(1);
    if (punkt[0] !== 0x04) return null;
    return { punkt, kurve };
  } catch {
    return null;
  }
}

/*
 * `new ArrayBuffer(n)` statt `new Uint8Array(n)`.
 *
 * Seit TypeScript 5.7 ist Uint8Array ueber seinen Puffer parametrisiert. Wer
 * eine Laenge uebergibt, bekommt `Uint8Array<ArrayBufferLike>` - und das
 * schliesst SharedArrayBuffer ein, den WebCrypto und fetch nicht annehmen.
 * Der Puffer ausdruecklich angelegt, ist der Typ `Uint8Array<ArrayBuffer>`
 * und passt. Zur Laufzeit aendert sich nichts.
 */
export function ausBase64(s: string): Uint8Array<ArrayBuffer> {
  const roh = atob(s);
  const b = new Uint8Array(new ArrayBuffer(roh.length));
  for (let i = 0; i < roh.length; i++) b[i] = roh.charCodeAt(i);
  return b;
}

/** Baut die Bytefolge, über die die TSE signiert hat. */
export function signierteDaten(
  f: string[],
  schluessel: Uint8Array<ArrayBuffer>,
  hashDesSchluessels: Uint8Array<ArrayBuffer>,
): Uint8Array<ArrayBuffer> {
  const zeit = Math.floor(Date.parse(f[7]) / 1000);
  if (!Number.isFinite(zeit)) throw new Error("log-time unlesbar");
  if (f[9] !== "unixTime") {
    // utcTime und generalizedTime kommen in der Praxis kaum vor; sie hier
    // stillschweigend wie unixTime zu behandeln wäre falsch.
    throw new Error(`log-time-format ${f[9]} wird nicht unterstützt`);
  }
  const oid = OID_VERFAHREN[f[8]];
  if (!oid) throw new Error(`Verfahren ${f[8]} unbekannt`);

  return Uint8Array.from([
    ...derGanzzahl(2),                                   // version
    ...derOid(OID_ZERTIFIZIERTE_DATEN),
    ...tlv(0x80, text("FinishTransaction")),             // [0] operationType
    ...tlv(0x81, text(f[1])),                            // [1] clientId
    ...tlv(0x82, text(f[3])),                            // [2] processData
    ...tlv(0x83, text(f[2])),                            // [3] processType
    ...tlv(0x85, zahlBytes(f[4])),                       // [5] transactionNumber
    ...tlv(0x04, hashDesSchluessels),                    //     serialNumber
    ...tlv(0x30, derOid(oid)),                           //     signatureAlgorithm
    ...derGanzzahl(f[5]),                                //     signatureCounter
    ...derGanzzahl(zeit),                                //     logTime
  ]);
}

export interface Ergebnis {
  gueltig: boolean;
  kurve?: string;
  grund?: string;
}

/** Prüft die Signatur eines Beleg-QR. */
export async function belegPruefen(qr: string): Promise<Ergebnis> {
  const f = qr.split(";");
  if (f.length < 12) return { gueltig: false, grund: "Kein Beleg-QR: zu wenige Felder" };
  if (f[0].trim() !== "V0") return { gueltig: false, grund: "Fremde QR-Version" };

  let schluessel: Uint8Array<ArrayBuffer>, signatur: Uint8Array<ArrayBuffer>;
  try {
    schluessel = ausBase64(f[11]);
    signatur = ausBase64(f[10]);
  } catch {
    return { gueltig: false, grund: "Signatur oder Schlüssel nicht lesbar" };
  }

  const gelesen = schluesselLesen(schluessel);
  if (!gelesen) return { gueltig: false, grund: "Schlüssel hat keine bekannte Form" };

  const gr = (gelesen.punkt.length - 1) / 2;
  // Nennt das SPKI die Kurve, wird nicht geraten. Sonst bleibt die Größe -
  // zwei 256-Bit-Kurven sind am rohen Punkt nicht zu unterscheiden.
  const kandidaten = gelesen.kurve ? [gelesen.kurve] : KURVEN_JE_GROESSE[gr];
  if (!kandidaten) return { gueltig: false, grund: "Kurvengröße unbekannt" };
  if (signatur.length !== gr * 2) {
    return { gueltig: false, grund: "Signaturlänge passt nicht zum Schlüssel" };
  }

  const hashName = HASH_NAME[f[8]];
  if (!hashName) return { gueltig: false, grund: `Verfahren ${f[8]} unbekannt` };

  let daten: Uint8Array<ArrayBuffer>;
  try {
    const keyHash = new Uint8Array(await crypto.subtle.digest("SHA-256", schluessel));
    daten = signierteDaten(f, schluessel, keyHash);
  } catch (e) {
    return { gueltig: false, grund: (e as Error).message };
  }

  const hash = new Uint8Array(await crypto.subtle.digest(hashName, daten));
  const r = zahl(signatur.slice(0, gr));
  const s = zahl(signatur.slice(gr));

  for (const kurve of kandidaten) {
    if (pruefeSignatur(kurve, gelesen.punkt, r, s, hash)) return { gueltig: true, kurve };
  }
  return {
    gueltig: false,
    kurve: gelesen.kurve ?? undefined,
    grund: gelesen.kurve
      ? `Signatur passt nicht zum Schlüssel (${gelesen.kurve})`
      : "Signatur passt zu keiner bekannten Kurve",
  };
}

export { KURVEN };
