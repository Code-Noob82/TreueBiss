/*
 * Liefert einen Apple-Wallet-Pass (.pkpass) fuer die Karte des aufrufenden
 * Kunden.
 *
 * Anders als bei Google gibt es hier keinen Link, den Apple aufloest: Der Pass
 * ist eine Datei, die der Browser oeffnet. Deshalb antwortet diese Funktion
 * nicht mit JSON, sondern mit dem Paket selbst.
 *
 * Ausrollen:
 *   supabase functions deploy wallet-apple
 *   supabase secrets set APPLE_PASS_TYPE_ID=pass.de.byteundhandwerk.treuebiss
 *   supabase secrets set APPLE_TEAM_ID=XXXXXXXXXX
 *   supabase secrets set APPLE_PASS_CERT="$(cat zertifikat.pem)"
 *   supabase secrets set APPLE_PASS_KEY="$(cat schluessel.pem)"
 *   supabase secrets set APPLE_WWDR_CERT="$(cat wwdr.pem)"
 */
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { manifestBauen, passBauen, type Betrieb } from "./pass.ts";
import { manifestSignieren, zertifikatPruefen } from "./signieren.ts";
import { zipBauen } from "./zip.ts";

const KOPF = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const JSON_KOPF = { ...KOPF, "Content-Type": "application/json" };

const antwort = (koerper: unknown, status = 200) =>
  new Response(JSON.stringify(koerper), { status, headers: JSON_KOPF });

async function bild(url: string): Promise<Uint8Array | null> {
  try {
    const a = await fetch(url);
    if (!a.ok) return null;
    return new Uint8Array(await a.arrayBuffer());
  } catch {
    return null;
  }
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: KOPF });
  if (req.method !== "POST") return antwort({ fehler: "Nur POST." }, 405);

  const jwt = req.headers.get("Authorization")?.replace(/^Bearer\s+/i, "");
  if (!jwt) return antwort({ fehler: "Keine Anmeldung." }, 401);

  const passTypeId = Deno.env.get("APPLE_PASS_TYPE_ID");
  const teamId = Deno.env.get("APPLE_TEAM_ID");
  const zertifikatPem = Deno.env.get("APPLE_PASS_CERT");
  const schluesselPem = Deno.env.get("APPLE_PASS_KEY");
  const wwdrPem = Deno.env.get("APPLE_WWDR_CERT");
  if (!passTypeId || !teamId || !zertifikatPem || !schluesselPem || !wwdrPem) {
    return antwort({ fehler: "Apple Wallet ist noch nicht eingerichtet." }, 503);
  }
  const basis = Deno.env.get("APP_BASIS_URL") ?? "";

  let tenant_id: string, pruefen = false;
  try {
    ({ tenant_id, pruefen = false } = await req.json());
  } catch {
    return antwort({ fehler: "Anfrage nicht lesbar." }, 400);
  }

  /*
   * Nur nachsehen, ob die Zertifikate taugen. Ein abgelaufenes Zertifikat
   * faellt sonst erst dem Kunden auf, und zwar als Pass, den Wallet
   * kommentarlos ablehnt.
   */
  if (pruefen) {
    try {
      return antwort({
        zertifikat: zertifikatPruefen(zertifikatPem),
        wwdr: zertifikatPruefen(wwdrPem),
        pass_type_id: passTypeId,
        team_id: teamId,
      });
    } catch (e) {
      return antwort({ fehler: "Zertifikate nicht lesbar.", grund: String(e) }, 200);
    }
  }

  if (!tenant_id) return antwort({ fehler: "tenant_id fehlt." }, 400);

  const url = Deno.env.get("SUPABASE_URL")!;
  const alsNutzer = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: `Bearer ${jwt}` } },
  });
  const { data: { user }, error: authFehler } = await alsNutzer.auth.getUser();
  if (authFehler || !user) return antwort({ fehler: "Anmeldung ungültig." }, 401);

  // Der Kartenschluessel ist durch die Policy nur fuer den Eigentuemer lesbar.
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

  try {
    /*
     * Die Seriennummer haengt am Kartenschluessel, ist aber nicht er selbst -
     * sie steht im Pass und waere sonst der Schluessel im Klartext auf dem
     * Sperrbildschirm.
     */
    const h = await crypto.subtle.digest(
      "SHA-256", new TextEncoder().encode(karte.card_token));
    const seriennummer = Array.from(new Uint8Array(h))
      .map((b) => b.toString(16).padStart(2, "0")).join("").slice(0, 32);

    const passJson = JSON.stringify(passBauen({
      passTypeId, teamId, seriennummer, betrieb, stempel: count ?? 0, umzugUrl,
    }), null, 2);

    const koder = new TextEncoder();
    const dateien: Record<string, Uint8Array> = { "pass.json": koder.encode(passJson) };

    /*
     * Ohne icon.png zeigt Wallet den Pass nicht an. Die Bilder liegen bei der
     * Web-App; faellt der Abruf aus, ist ein Pass ohne Bild besser als gar
     * keiner - Wallet verlangt allerdings mindestens das Symbol.
     */
    const symbol = await bild(`${basis}icon-192.png`);
    if (!symbol) {
      return antwort({ fehler: "Das Symbol für den Pass ist nicht erreichbar." }, 502);
    }
    dateien["icon.png"] = symbol;
    dateien["icon@2x.png"] = symbol;
    dateien["logo.png"] = symbol;

    const manifest = await manifestBauen(dateien);
    const manifestJson = JSON.stringify(manifest, null, 2);
    dateien["manifest.json"] = koder.encode(manifestJson);
    dateien["signature"] = manifestSignieren(manifestJson, {
      zertifikatPem, schluesselPem, wwdrPem,
      passwort: Deno.env.get("APPLE_PASS_KEY_PASSWORT") || undefined,
    });

    const paket = zipBauen(dateien);
    return new Response(paket, {
      headers: {
        ...KOPF,
        "Content-Type": "application/vnd.apple.pkpass",
        "Content-Disposition": `attachment; filename="${betrieb.slug}.pkpass"`,
      },
    });
  } catch (e) {
    console.error("wallet-apple", e);
    return antwort({ fehler: "Der Pass liess sich nicht erzeugen.", grund: String(e) }, 500);
  }
});
