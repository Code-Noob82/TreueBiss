/*
 * Baut Klasse, Objekt und die JWT-Claims fuer "Zu Google Wallet hinzufuegen".
 *
 * Bewusst ohne Krypto und ohne Netz: Was hier entsteht, ist reines JSON und
 * laesst sich deshalb ohne Dienstkonto pruefen. Das Signieren steht in
 * signieren.ts, der Zusammenbau in index.ts.
 *
 * Quellen:
 *   JWT-Aufbau   https://developers.google.com/wallet/retail/loyalty-cards/use-cases/jwt
 *   LoyaltyClass https://developers.google.com/wallet/reference/rest/v1/loyaltyclass
 *   LoyaltyObject https://developers.google.com/wallet/reference/rest/v1/loyaltyobject
 */

export type Betrieb = {
  slug: string;
  name: string;
  primary_color: string | null;
  stamps_per_card: number;
};

/** Nur Zeichen, die Google in Kennungen zulaesst: alphanumerisch, . _ - */
export function kennungSaeubern(roh: string): string {
  return roh.toLowerCase().replace(/[^a-z0-9._-]/g, "-").replace(/-+/g, "-");
}

/**
 * Farbe auf #rrggbb bringen. Google nimmt auch #rgb, aber die Betriebe
 * pflegen Vollformat - und ein leeres Feld darf den Pass nicht kippen.
 */
export function farbe(roh: string | null | undefined): string {
  const w = (roh ?? "").trim();
  if (/^#[0-9a-fA-F]{6}$/.test(w)) return w.toLowerCase();
  if (/^#[0-9a-fA-F]{3}$/.test(w)) {
    return ("#" + w.slice(1).split("").map((z) => z + z).join("")).toLowerCase();
  }
  return "#4c4c4c";
}

export function klasseId(issuerId: string, betrieb: Betrieb): string {
  return `${issuerId}.treuebiss-${kennungSaeubern(betrieb.slug)}`;
}

/**
 * Die Objektkennung darf den Kartenschluessel nicht enthalten - sie taucht in
 * Google-Konten und Protokollen auf. Deshalb ein Hash davon, gekuerzt: Er ist
 * stabil (derselbe Kunde bekommt beim zweiten Hinzufuegen dasselbe Objekt
 * statt eines zweiten Passes) und verraet den Schluessel nicht.
 */
export function objektId(issuerId: string, betrieb: Betrieb, tokenHashHex: string): string {
  return `${issuerId}.${kennungSaeubern(betrieb.slug)}-${tokenHashHex.slice(0, 32)}`;
}

export function klasseBauen(issuerId: string, betrieb: Betrieb, logoUrl: string) {
  return {
    id: klasseId(issuerId, betrieb),
    issuerName: betrieb.name.slice(0, 20),
    programName: betrieb.name.slice(0, 20),
    programLogo: { sourceUri: { uri: logoUrl } },
    /*
     * `draft` waere das Naheliegende, kann aber laut Referenz keine Objekte
     * erzeugen - der Pass entstuende schlicht nicht. `underReview` ist der
     * Zustand, in dem sich entwickeln laesst.
     */
    reviewStatus: "underReview",
    hexBackgroundColor: farbe(betrieb.primary_color),
  };
}

export function objektBauen(
  issuerId: string,
  betrieb: Betrieb,
  tokenHashHex: string,
  stempel: number,
  umzugUrl: string,
) {
  return {
    id: objektId(issuerId, betrieb, tokenHashHex),
    classId: klasseId(issuerId, betrieb),
    state: "ACTIVE",
    loyaltyPoints: {
      // Beide Felder sind laengenbegrenzt: Label 9, Balance 7 Zeichen.
      label: "Stempel",
      balance: { string: `${stempel}/${betrieb.stamps_per_card}` },
    },
    /*
     * Der Strichcode traegt den Umzugslink, nicht bloss den Schluessel.
     *
     * Bei TreueBiss scannt der Kunde, nicht die Kasse - es gibt keine Stelle,
     * die einen Kundencode einliest. Nuetzlich ist der Code deshalb genau
     * dort, wo der Pass ohnehin hin soll: auf ein weiteres Geraet. Wer ihn
     * scannt, holt die Karte zu sich.
     */
    barcode: { type: "QR_CODE", value: umzugUrl, alternateText: "Karte mitnehmen" },
  };
}

/**
 * Die Claims des Save-JWT. `aud` und `typ` sind von Google fest vorgegeben.
 * `origins` begrenzt, von welchen Seiten aus gespeichert werden darf.
 */
export function claimsBauen(
  dienstkontoEmail: string,
  klasse: unknown | null,
  objekt: unknown,
  origins: string[],
) {
  return {
    iss: dienstkontoEmail,
    aud: "google",
    typ: "savetowallet",
    iat: Math.floor(Date.now() / 1000),
    origins,
    // Ist die Klasse null, steht sie schon bei Google und gehoert nicht ins
    // JWT - jedes Feld weniger ist Laenge, die der Browser nicht abschneidet.
    payload: klasse === null
      ? { loyaltyObjects: [objekt] }
      : { loyaltyClasses: [klasse], loyaltyObjects: [objekt] },
  };
}

export const SPEICHERN_URL = "https://pay.google.com/gp/v/save/";
