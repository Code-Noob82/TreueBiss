/*
 * Prüfungen für den Google-Wallet-Pass.
 *
 * Läuft ohne Dienstkonto und ohne Netz: Der Zusammenbau ist reines JSON, und
 * signiert wird gegen einen hier erzeugten RSA-Schlüssel. Damit lässt sich
 * alles prüfen außer der Frage, ob Google den Pass am Ende annimmt — die
 * beantwortet erst ein echter Issuer.
 *
 *   node --experimental-strip-types supabase/functions/wallet-google/pass_test.ts
 */
import {
  claimsBauen, farbe, kennungSaeubern, klasseBauen, klasseId, objektBauen, objektId,
  SPEICHERN_URL, type Betrieb,
} from "./pass.ts";
import { b64url, jwtBauen, pemZuBytes, sha256Hex } from "./signieren.ts";

let fehler = 0;
function pruefe(bedingung: boolean, text: string) {
  console.log(`${bedingung ? "OK   " : "FEHLGESCHLAGEN:"} ${text}`);
  if (!bedingung) fehler++;
}

const BETRIEB: Betrieb = {
  slug: "baeckerei-mustermann",
  name: "Bäckerei Meier",
  primary_color: "#4CAF50",
  stamps_per_card: 10,
};
const ISSUER = "3388000000012345678";

// ------------------------------------------------------------------ Kennungen
pruefe(kennungSaeubern("Bäckerei Müller & Co") === "b-ckerei-m-ller-co",
  "Sonderzeichen werden zu einem einzelnen Bindestrich");
pruefe(/^[a-z0-9._-]+$/.test(kennungSaeubern("Ä Ö Ü ß / \\ ?")),
  "Übrig bleiben nur erlaubte Zeichen");

pruefe(klasseId(ISSUER, BETRIEB) === `${ISSUER}.treuebiss-baeckerei-mustermann`,
  "Die Klassenkennung trägt Issuer und Betrieb");

const hash = await sha256Hex("geheim");
const oid = objektId(ISSUER, BETRIEB, hash);
pruefe(oid.startsWith(`${ISSUER}.`), "Die Objektkennung beginnt mit der Issuer-ID");
pruefe(!oid.includes("geheim"), "Die Objektkennung enthält den Kartenschlüssel nicht");
pruefe(objektId(ISSUER, BETRIEB, hash) === oid,
  "Derselbe Kunde bekommt dieselbe Objektkennung — kein zweiter Pass");
pruefe(objektId(ISSUER, BETRIEB, await sha256Hex("anders")) !== oid,
  "Ein anderer Kunde bekommt eine andere");

// ------------------------------------------------------------------ Farbe
pruefe(farbe("#4CAF50") === "#4caf50", "Vollformat wird übernommen");
pruefe(farbe("#fc0") === "#ffcc00", "Kurzform wird ausgeschrieben");
pruefe(farbe(null) === "#4c4c4c", "Ohne Farbe kippt der Pass nicht");
pruefe(farbe("grün") === "#4c4c4c", "Unbrauchbares fällt auf den Ersatzwert zurück");

// ------------------------------------------------------------------ Klasse
const klasse = klasseBauen(ISSUER, BETRIEB, "https://example.test/icon-512.png");
pruefe(klasse.reviewStatus === "underReview",
  "reviewStatus ist underReview — draft könnte keine Objekte erzeugen");
pruefe(klasse.issuerName.length <= 20 && klasse.programName.length <= 20,
  "Issuer- und Programmname bleiben in der Längengrenze");
pruefe(!!klasse.programLogo?.sourceUri?.uri, "Das Programmlogo ist gesetzt");
pruefe(klasse.hexBackgroundColor === "#4caf50", "Die Farbe des Betriebs steht im Pass");

const lang = klasseBauen(ISSUER,
  { ...BETRIEB, name: "Bäckerei mit einem sehr langen Namen GmbH" },
  "https://example.test/i.png");
pruefe(lang.issuerName.length === 20, "Ein langer Name wird gekürzt statt abgelehnt");

// ------------------------------------------------------------------ Objekt
const umzug = "https://example.test/app/?b=baeckerei-mustermann&karte=" + "a".repeat(64);
const objekt = objektBauen(ISSUER, BETRIEB, hash, 3, umzug);
pruefe(objekt.classId === klasse.id, "Das Objekt zeigt auf seine Klasse");
pruefe(objekt.state === "ACTIVE", "Der Pass ist aktiv");
pruefe(objekt.loyaltyPoints.balance.string === "3/10", "Der Stempelstand steht drauf");
pruefe(objekt.loyaltyPoints.label.length <= 9, "Das Label bleibt in der Längengrenze");
pruefe(objekt.barcode.type === "QR_CODE" && objekt.barcode.value === umzug,
  "Der Strichcode trägt den Umzugslink");

// ------------------------------------------------------------------ Claims
const claims = claimsBauen("dienst@projekt.iam.gserviceaccount.com", klasse, objekt,
  ["https://example.test"]);
pruefe(claims.aud === "google", "aud ist google");
pruefe(claims.typ === "savetowallet", "typ ist savetowallet");
pruefe(claims.iss.includes("@"), "iss ist die Dienstkonto-Adresse");
pruefe(Number.isInteger(claims.iat) && claims.iat > 1_700_000_000,
  "iat ist eine plausible Unix-Zeit in Sekunden");
pruefe(claims.payload.loyaltyClasses.length === 1 && claims.payload.loyaltyObjects.length === 1,
  "Klasse und Objekt reisen zusammen — beide entstehen beim Speichern");

// ------------------------------------------------------------------ Signatur
pruefe(b64url("a?b") === "YT9i", "base64url ohne Auffüllzeichen");
pruefe(!b64url(new Uint8Array([251, 255])).includes("+"), "Kein + im base64url");
pruefe(!b64url(new Uint8Array([251, 255])).includes("/"), "Kein / im base64url");

const paar = await crypto.subtle.generateKey(
  { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]),
    hash: "SHA-256" }, true, ["sign", "verify"]);
const pkcs8 = new Uint8Array(await crypto.subtle.exportKey("pkcs8", paar.privateKey));
let roh = ""; for (const b of pkcs8) roh += String.fromCharCode(b);
const pem = `-----BEGIN PRIVATE KEY-----\n${btoa(roh).replace(/(.{64})/g, "$1\n")}\n-----END PRIVATE KEY-----`;

const token = await jwtBauen(claims, pem);
const teile = token.split(".");
pruefe(teile.length === 3, "Das JWT hat drei Teile");

const kopf = JSON.parse(atob(teile[0].replace(/-/g, "+").replace(/_/g, "/")));
pruefe(kopf.alg === "RS256", "Der Header nennt RS256");

const gueltig = await crypto.subtle.verify(
  "RSASSA-PKCS1-v1_5", paar.publicKey,
  Uint8Array.from(atob(teile[2].replace(/-/g, "+").replace(/_/g, "/")), (z) => z.charCodeAt(0)),
  new TextEncoder().encode(`${teile[0]}.${teile[1]}`));
pruefe(gueltig, "Die Signatur prüft gegen den öffentlichen Schlüssel");

/*
 * Dienstkontoschlüssel stehen in der JSON-Datei mit \n als zwei Zeichen.
 * Wird das beim Ablegen in den Secrets nicht aufgelöst, scheiterte der Import
 * früher mit einer nichtssagenden Meldung.
 */
const einzeilig = pem.replace(/\n/g, "\\n");
pruefe(pemZuBytes(einzeilig).length === pkcs8.length,
  "Ein PEM mit maskierten Zeilenumbrüchen wird genauso gelesen");
const token2 = await jwtBauen(claims, einzeilig);
pruefe(token2.split(".").length === 3, "Und ergibt ebenfalls ein gültiges JWT");

pruefe(SPEICHERN_URL === "https://pay.google.com/gp/v/save/",
  "Die Save-URL entspricht der Dokumentation");

console.log(fehler === 0
  ? "\n--- Google-Wallet-Pass bestanden ---"
  : `\n${fehler} Prüfung(en) fehlgeschlagen`);
process.exitCode = fehler === 0 ? 0 : 1;
