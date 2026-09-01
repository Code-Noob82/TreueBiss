/*
 * RS256-Signatur fuer das Save-JWT.
 *
 * Google verlangt RSA256; der Schluessel ist der private Schluessel eines
 * Google-Cloud-Dienstkontos und kommt als PEM im PKCS#8-Format.
 * (https://developers.google.com/wallet/retail/loyalty-cards/use-cases/jwt)
 *
 * Web Crypto reicht dafuer aus - keine Bibliothek noetig, und eine weniger,
 * die den privaten Schluessel zu sehen bekommt.
 */

/** base64url ohne Auffuellzeichen, so wie JWT es verlangt. */
export function b64url(daten: Uint8Array | string): string {
  const bytes = typeof daten === "string" ? new TextEncoder().encode(daten) : daten;
  let roh = "";
  for (const b of bytes) roh += String.fromCharCode(b);
  return btoa(roh).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/**
 * PEM in rohe Bytes.
 *
 * Dienstkontoschluessel stehen in der JSON-Datei mit `\n` als zwei Zeichen.
 * Wird das beim Ablegen in den Secrets nicht aufgeloest, schlaegt der Import
 * mit einer nichtssagenden Meldung fehl - deshalb hier beides behandeln.
 */
/*
 * `new ArrayBuffer(n)` statt `new Uint8Array(n)`.
 *
 * Seit TypeScript 5.7 ist Uint8Array ueber seinen Puffer parametrisiert. Wer
 * eine Laenge uebergibt, bekommt `Uint8Array<ArrayBufferLike>` - und das
 * schliesst SharedArrayBuffer ein, den WebCrypto und fetch nicht annehmen.
 * Der Puffer ausdruecklich angelegt, ist der Typ `Uint8Array<ArrayBuffer>`
 * und passt. Zur Laufzeit aendert sich nichts.
 */
export function pemZuBytes(pem: string): Uint8Array<ArrayBuffer> {
  const koerper = pem
    .replace(/\\n/g, "\n")
    .replace(/-----BEGIN [^-]+-----/, "")
    .replace(/-----END [^-]+-----/, "")
    .replace(/\s+/g, "");
  if (!koerper) throw new Error("Der Schluessel ist leer.");
  const roh = atob(koerper);
  const bytes = new Uint8Array(new ArrayBuffer(roh.length));
  for (let i = 0; i < roh.length; i++) bytes[i] = roh.charCodeAt(i);
  return bytes;
}

export async function schluesselLaden(pem: string): Promise<CryptoKey> {
  return await crypto.subtle.importKey(
    "pkcs8",
    pemZuBytes(pem),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

/** Baut das vollstaendige JWT: header.claims.signatur */
export async function jwtBauen(claims: unknown, pem: string): Promise<string> {
  const kopf = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const koerper = b64url(JSON.stringify(claims));
  const zuSignieren = `${kopf}.${koerper}`;

  const schluessel = await schluesselLaden(pem);
  const signatur = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    schluessel,
    new TextEncoder().encode(zuSignieren),
  );
  return `${zuSignieren}.${b64url(new Uint8Array(signatur))}`;
}

/** SHA-256 als Hex - fuer die Objektkennung aus dem Kartenschluessel. */
export async function sha256Hex(text: string): Promise<string> {
  const puffer = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(puffer))
    .map((b) => b.toString(16).padStart(2, "0")).join("");
}
