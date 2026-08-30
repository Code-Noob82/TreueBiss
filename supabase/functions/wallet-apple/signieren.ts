/*
 * PKCS#7-Signatur ueber das Manifest.
 *
 * Anders als bei Google reicht Web Crypto hier nicht: Apple verlangt eine
 * abgetrennte PKCS#7-Signatur (CMS SignedData) mit Zertifikatskette, und CMS
 * kennt Web Crypto nicht. node-forge bringt das mit.
 *
 * Drei Teile gehoeren dazu:
 *   - das Pass Type ID-Zertifikat aus dem Apple Developer Program
 *   - der zugehoerige private Schluessel
 *   - das WWDR-Zwischenzertifikat, damit die Kette bis zu Apples Wurzel reicht
 *
 * Quelle: https://developer.apple.com/documentation/walletpasses/building-a-pass
 */
import forge from "https://esm.sh/node-forge@1.3.1";

export type Zertifikate = {
  zertifikatPem: string;
  schluesselPem: string;
  wwdrPem: string;
  /** Passwort des privaten Schluessels, falls er verschluesselt vorliegt. */
  passwort?: string;
};

/**
 * PEM einlesen und dabei die haeufigste Stolperstelle abfangen: In Secrets
 * landen Zeilenumbrueche oft als zwei Zeichen statt als Umbruch. Der Fehler
 * meldet sich sonst als "Invalid PEM formatted message" und sagt nicht, warum.
 */
function pem(roh: string): string {
  return roh.replace(/\\n/g, "\n").trim();
}

export function schluesselLesen(pemRoh: string, passwort?: string) {
  const text = pem(pemRoh);
  const schluessel = passwort
    ? forge.pki.decryptRsaPrivateKey(text, passwort)
    : forge.pki.privateKeyFromPem(text);
  if (!schluessel) {
    throw new Error(
      passwort
        ? "Der private Schluessel liess sich mit diesem Passwort nicht oeffnen."
        : "Der private Schluessel liess sich nicht lesen. Ist er passwortgeschuetzt?",
    );
  }
  return schluessel;
}

/**
 * Abgetrennte Signatur ueber den Manifest-Inhalt.
 *
 * `detached` ist entscheidend: Die Datei `signature` enthaelt nur die
 * Signatur, nicht noch einmal das Manifest. Ein eingebetteter Inhalt wird von
 * Wallet stillschweigend abgelehnt.
 */
export function manifestSignieren(manifestJson: string, z: Zertifikate): Uint8Array {
  const zertifikat = forge.pki.certificateFromPem(pem(z.zertifikatPem));
  const wwdr = forge.pki.certificateFromPem(pem(z.wwdrPem));
  const schluessel = schluesselLesen(z.schluesselPem, z.passwort);

  const p7 = forge.pkcs7.createSignedData();
  p7.content = forge.util.createBuffer(manifestJson, "utf8");
  p7.addCertificate(zertifikat);
  p7.addCertificate(wwdr);
  p7.addSigner({
    key: schluessel,
    certificate: zertifikat,
    digestAlgorithm: forge.pki.oids.sha256,
    authenticatedAttributes: [
      { type: forge.pki.oids.contentType, value: forge.pki.oids.data },
      { type: forge.pki.oids.messageDigest },
      { type: forge.pki.oids.signingTime },
    ],
  });
  p7.sign({ detached: true });

  const der = forge.asn1.toDer(p7.toAsn1()).getBytes();
  const bytes = new Uint8Array(der.length);
  for (let i = 0; i < der.length; i++) bytes[i] = der.charCodeAt(i) & 0xFF;
  return bytes;
}

/** Liest Ablaufdatum und Betreff des Zertifikats - fuer die Einrichtungspruefung. */
export function zertifikatPruefen(zertifikatPem: string) {
  const c = forge.pki.certificateFromPem(pem(zertifikatPem));
  const cn = c.subject.getField("CN");
  const ou = c.subject.getField("OU");
  return {
    betreff: cn?.value ?? null,
    team: ou?.value ?? null,
    gueltig_ab: c.validity.notBefore.toISOString(),
    gueltig_bis: c.validity.notAfter.toISOString(),
    abgelaufen: c.validity.notAfter.getTime() < Date.now(),
    aussteller: c.issuer.getField("CN")?.value ?? null,
  };
}
