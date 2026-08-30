/*
 * Liefert einen "Zu Google Wallet hinzufuegen"-Link fuer die Karte des
 * aufrufenden Kunden.
 *
 * Warum serverseitig: Das JWT wird mit dem privaten Schluessel eines
 * Google-Cloud-Dienstkontos signiert. Der darf nicht in den Browser.
 *
 * Warum nicht ueber die REST-API: Das Save-JWT darf Klasse und Objekt
 * zugleich tragen; beide entstehen beim Speichern durch den Kunden. Damit
 * entfaellt ein zweiter Weg mit Zugriffstoken, und es gibt eine Stelle
 * weniger, die scheitern kann.
 *
 * Ausrollen:
 *   supabase functions deploy wallet-google
 *   supabase secrets set GOOGLE_WALLET_ISSUER_ID=...
 *   supabase secrets set GOOGLE_WALLET_SA_EMAIL=...
 *   supabase secrets set GOOGLE_WALLET_SA_KEY="-----BEGIN PRIVATE KEY-----..."
 *   supabase secrets set APP_BASIS_URL=https://code-noob82.github.io/TreueBiss/app/
 */
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { claimsBauen, klasseBauen, objektBauen, SPEICHERN_URL, type Betrieb } from "./pass.ts";
import { jwtBauen, sha256Hex } from "./signieren.ts";
import { klasseSicherstellen, objektAnlegen, zugriffstokenHolen } from "./google.ts";

const KOPF = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};

const antwort = (koerper: unknown, status = 200) =>
  new Response(JSON.stringify(koerper), { status, headers: KOPF });

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: KOPF });
  if (req.method !== "POST") return antwort({ fehler: "Nur POST." }, 405);

  const jwt = req.headers.get("Authorization")?.replace(/^Bearer\s+/i, "");
  if (!jwt) return antwort({ fehler: "Keine Anmeldung." }, 401);

  const issuerId = Deno.env.get("GOOGLE_WALLET_ISSUER_ID");
  const saEmail = Deno.env.get("GOOGLE_WALLET_SA_EMAIL");
  const saKey = Deno.env.get("GOOGLE_WALLET_SA_KEY");
  if (!issuerId || !saEmail || !saKey) {
    // Klartext statt 500: Das ist ein Einrichtungsfehler, kein Laufzeitfehler.
    return antwort({ fehler: "Google Wallet ist noch nicht eingerichtet." }, 503);
  }
  const basis = Deno.env.get("APP_BASIS_URL") ?? "";

  let tenant_id: string, pruefen = false;
  try {
    ({ tenant_id, pruefen = false } = await req.json());
  } catch {
    return antwort({ fehler: "Anfrage nicht lesbar." }, 400);
  }
  if (!tenant_id) return antwort({ fehler: "tenant_id fehlt." }, 400);

  const url = Deno.env.get("SUPABASE_URL")!;

  // Wer fragt? Mit dem Token des Aufrufers - sonst liesse sich ein Pass fuer
  // eine fremde Karte erzeugen.
  const alsNutzer = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: `Bearer ${jwt}` } },
  });
  const { data: { user }, error: authFehler } = await alsNutzer.auth.getUser();
  if (authFehler || !user) return antwort({ fehler: "Anmeldung ungültig." }, 401);

  /*
   * Der Kartenschluessel steht in memberships und ist durch die Policy nur
   * fuer den Eigentuemer lesbar. Deshalb hier ausdruecklich mit dem Token des
   * Kunden lesen und nicht mit dem Dienstschluessel: Findet die Abfrage
   * nichts, hat der Aufrufer hier keine Karte - und bekommt auch keinen Pass.
   */
  const { data: karte } = await alsNutzer
    .from("memberships").select("card_token")
    .eq("tenant_id", tenant_id).maybeSingle();
  if (!karte?.card_token) {
    return antwort({ fehler: "Für diesen Betrieb gibt es hier keine Karte." }, 404);
  }

  const { data: betriebe } = await alsNutzer
    .from("tenants").select("slug, name, primary_color, stamps_per_card")
    .eq("id", tenant_id).limit(1);
  const betrieb = betriebe?.[0] as Betrieb | undefined;
  if (!betrieb) return antwort({ fehler: "Betrieb unbekannt." }, 404);

  const { count } = await alsNutzer
    .from("stamps").select("id", { count: "exact", head: true })
    .eq("tenant_id", tenant_id);

  const umzugUrl = `${basis}?b=${encodeURIComponent(betrieb.slug)}`
    + `&karte=${encodeURIComponent(karte.card_token)}`;
  const logoUrl = `${basis}icon-512.png`;

  /*
   * Nur nachsehen, ob Google die Zugangsdaten annimmt.
   *
   * Scheitert das Speichern auf dem Geraet, gibt es dafuer mehrere moegliche
   * Gruende - falscher Schluessel, Dienstkonto nicht freigegeben, Demo-Modus,
   * unpassendes Geraet. Dieser Weg beantwortet die erste Haelfte davon ohne
   * jedes Geraet: Er holt ein Zugriffstoken und legt die Klasse an.
   */
  if (pruefen) {
    try {
      const token = await zugriffstokenHolen(saEmail, saKey);
      const klasse = klasseBauen(issuerId, betrieb, logoUrl);
      const spur = await klasseSicherstellen(token, klasse);
      const tokenHash = await sha256Hex(karte.card_token);
      const objekt = objektBauen(issuerId, betrieb, tokenHash, count ?? 0, umzugUrl);
      spur.push(...await objektAnlegen(token, objekt));
      return antwort({ zugangsdaten: "angenommen", klasse: klasse.id, objekt: objekt.id, spur });
    } catch (e) {
      return antwort({ zugangsdaten: "abgelehnt", grund: String(e) }, 200);
    }
  }

  try {
    const tokenHash = await sha256Hex(karte.card_token);
    const klasse = klasseBauen(issuerId, betrieb, logoUrl);
    const objekt = objektBauen(issuerId, betrieb, tokenHash, count ?? 0, umzugUrl);

    /*
     * Klasse und Objekt zuerst ueber die REST-API anlegen, dann im Link nur
     * noch darauf verweisen.
     *
     * Der kuerzere Weg waere, beide im JWT mitzuschicken - Google legt sie
     * dann beim Speichern an. Das schlug beim Kunden mit "Ein Problem ist
     * aufgetreten" fehl, ohne Grund. Ueber die REST-API antwortet Google
     * dagegen im Klartext, und das JWT schrumpft von rund 1600 auf wenige
     * hundert Zeichen - damit ist auch die dokumentierte Grenze von 1800
     * Zeichen kein Thema mehr.
     *
     * Nebenwirkung, die wir wollen: Das Objekt traegt bei jedem Aufruf den
     * aktuellen Stempelstand, weil es aktualisiert statt nur angelegt wird.
     */
    const dienstToken = await zugriffstokenHolen(saEmail, saKey);
    const spur = [
      ...await klasseSicherstellen(dienstToken, klasse),
      ...await objektAnlegen(dienstToken, objekt),
    ];
    /*
     * Die Antworten auswerten statt wegwerfen. Verweist der Link auf ein
     * Objekt, das gar nicht entstanden ist, zeigt Google dem Kunden nur
     * "Ein Problem ist aufgetreten" - und wir haetten hier nichts, woran
     * sich das erkennen liesse.
     */
    const letzte = spur[spur.length - 1];
    if (!letzte || letzte.status >= 400) {
      return antwort({ fehler: "Der Pass liess sich bei Google nicht anlegen.", spur }, 502);
    }
    /*
     * `origins` bleibt leer.
     *
     * Das Feld begrenzt, von welchen Seiten aus gespeichert werden darf - das
     * passt zum eingebetteten Javascript-Knopf. Unser Link wird aber auch
     * direkt geoeffnet, aus der App heraus in einem neuen Fenster oder als
     * kopierte Adresse; dann gibt es keinen passenden Ursprung, und das
     * Speichern scheiterte mit "Ein Problem ist aufgetreten". Das Beispiel in
     * Googles JWT-Dokumentation zeigt das Feld ebenfalls leer.
     */
    // Nur der Verweis - Klasse und Objekt stehen schon bei Google.
    const claims = claimsBauen(saEmail, null, { id: objekt.id }, []);
    const token = await jwtBauen(claims, saKey);
    return antwort({ url: SPEICHERN_URL + token, spur });
  } catch (e) {
    console.error("wallet-google", e);
    return antwort({ fehler: "Der Pass liess sich nicht erzeugen.", grund: String(e) }, 500);
  }
});
