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

  let tenant_id: string;
  try {
    ({ tenant_id } = await req.json());
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

  try {
    const tokenHash = await sha256Hex(karte.card_token);
    const klasse = klasseBauen(issuerId, betrieb, logoUrl);
    const objekt = objektBauen(issuerId, betrieb, tokenHash, count ?? 0, umzugUrl);
    const claims = claimsBauen(saEmail, klasse, objekt, basis ? [new URL(basis).origin] : []);
    const token = await jwtBauen(claims, saKey);
    return antwort({ url: SPEICHERN_URL + token });
  } catch (e) {
    console.error("wallet-google", e);
    return antwort({ fehler: "Der Pass liess sich nicht erzeugen.", grund: String(e) }, 500);
  }
});
