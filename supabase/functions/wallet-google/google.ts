/*
 * Zugriff auf die Google-Wallet-REST-API mit dem Dienstkonto.
 *
 * Der Save-Link braucht das nicht - dort reist die Klasse im JWT mit. Diese
 * Datei existiert fuer zwei andere Zwecke:
 *
 *   1. Pruefen, ob die Zugangsdaten ueberhaupt taugen. Ohne sie laesst sich
 *      ein fehlgeschlagenes Speichern nicht von einem falschen Schluessel
 *      unterscheiden - und der Demo-Modus verdeckt beides.
 *   2. Die Klasse einmal fest anlegen, damit Logo und Farbe sich aendern
 *      lassen, ohne dass jeder Kunde einen neuen Pass zieht.
 *
 * Quellen:
 *   Scope    https://developers.google.com/wallet/retail/loyalty-cards/use-cases/auth
 *   Endpunkt https://developers.google.com/wallet/reference/rest/v1/loyaltyclass/insert
 */
import { b64url } from "./signieren.ts";
import { jwtBauen } from "./signieren.ts";

const SCOPE = "https://www.googleapis.com/auth/wallet_object.issuer";
const TOKEN_ENDPUNKT = "https://oauth2.googleapis.com/token";
const BASIS = "https://walletobjects.googleapis.com/walletobjects/v1";

/**
 * Zugriffstoken per JWT-Bearer-Grant.
 *
 * Anders als das Save-JWT geht dieses an Google selbst: `aud` ist der
 * Token-Endpunkt, nicht "google", und es braucht ein Ablaufdatum.
 */
export async function zugriffstokenHolen(saEmail: string, saKey: string): Promise<string> {
  const jetzt = Math.floor(Date.now() / 1000);
  const behauptung = await jwtBauen({
    iss: saEmail,
    scope: SCOPE,
    aud: TOKEN_ENDPUNKT,
    iat: jetzt,
    exp: jetzt + 3600,
  }, saKey);

  const antwort = await fetch(TOKEN_ENDPUNKT, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: behauptung,
    }),
  });
  const daten = await antwort.json();
  if (!antwort.ok || !daten.access_token) {
    /*
     * Googles Fehler hier sind ungewoehnlich aussagekraeftig -
     * "invalid_grant" heisst fast immer, dass die Uhr oder der Schluessel
     * nicht stimmt. Deshalb unveraendert durchreichen.
     */
    throw new Error(`Kein Zugriffstoken: ${JSON.stringify(daten)}`);
  }
  return daten.access_token as string;
}

export type Ergebnis = { schritt: string; status: number; koerper: unknown };

/** Klasse abrufen; existiert sie nicht, anlegen. Beides wird berichtet. */
export async function klasseSicherstellen(
  token: string,
  klasse: { id: string },
): Promise<Ergebnis[]> {
  const spur: Ergebnis[] = [];

  const holen = await fetch(`${BASIS}/loyaltyClass/${encodeURIComponent(klasse.id)}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  spur.push({ schritt: "abrufen", status: holen.status, koerper: await holen.json() });
  if (holen.ok) return spur;

  const anlegen = await fetch(`${BASIS}/loyaltyClass`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(klasse),
  });
  spur.push({ schritt: "anlegen", status: anlegen.status, koerper: await anlegen.json() });
  return spur;
}

/**
 * Die Nachrichten der Klasse setzen - oder mit einer leeren Liste loeschen.
 *
 * PATCH statt des eigens dafuer vorgesehenen `addMessage`: Letzteres haengt
 * an, und der Betrieb haette nach einem halben Jahr eine Chronik im Pass
 * stehen. Hier gilt, was zuletzt gesetzt wurde.
 *
 * Endpunkt https://developers.google.com/wallet/reference/rest/v1/loyaltyclass/patch
 */
export async function klasseNachricht(
  token: string,
  klasseId: string,
  nachrichten: unknown[],
): Promise<Ergebnis[]> {
  const antwort = await fetch(`${BASIS}/loyaltyClass/${encodeURIComponent(klasseId)}`, {
    method: "PATCH",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ messages: nachrichten }),
  });
  return [{
    schritt: nachrichten.length ? "nachricht setzen" : "nachricht loeschen",
    status: antwort.status,
    koerper: await antwort.json(),
  }];
}

/**
 * Objekt anlegen und Google antworten lassen.
 *
 * Der Save-Link erzeugt das Objekt sonst erst beim Klick - und wenn dabei
 * etwas nicht stimmt, zeigt Google dem Kunden nur "Ein Problem ist
 * aufgetreten". Ueber die REST-API kommt der eigentliche Grund zurueck.
 */
export async function objektAnlegen(token: string, objekt: { id: string }): Promise<Ergebnis[]> {
  const spur: Ergebnis[] = [];
  const anlegen = await fetch(`${BASIS}/loyaltyObject`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(objekt),
  });
  spur.push({ schritt: "objekt anlegen", status: anlegen.status, koerper: await anlegen.json() });

  /*
   * Schon vorhanden: aktualisieren statt scheitern. Der Kunde hat seit dem
   * letzten Mal gesammelt, und der Pass soll den heutigen Stand zeigen.
   */
  if (anlegen.status === 409) {
    const aendern = await fetch(`${BASIS}/loyaltyObject/${encodeURIComponent(objekt.id)}`, {
      method: "PATCH",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify(objekt),
    });
    spur.push({ schritt: "objekt ändern", status: aendern.status, koerper: await aendern.json() });
  }
  return spur;
}
