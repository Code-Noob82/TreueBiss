/*
 * Prüft die ECDSA-Signatur eines Kassenbelegs und vergibt dann den Stempel.
 *
 * Warum überhaupt eine Edge Function: Postgres kann kein ECDSA - `pgcrypto`
 * verifiziert keine Signaturen. Ohne diesen Schritt schützen die Regeln in
 * `issue_stamp` nur gegen eingesammelte fremde Bons, nicht gegen einen selbst
 * gebauten QR-Code.
 *
 * Warum die Vergabe gleich mit hier passiert: Sonst müsste die App der
 * Datenbank sagen "ich habe prüfen lassen", und genau das darf sie nicht
 * behaupten können. `service_issue_stamp` ist deshalb nur für service_role
 * freigegeben, und der Schlüssel dafür liegt hier, nicht im Browser.
 *
 * Ausrollen:
 *   supabase functions deploy beleg-pruefen
 * SUPABASE_URL, SUPABASE_ANON_KEY und SUPABASE_SERVICE_ROLE_KEY stellt die
 * Laufzeit selbst bereit.
 */
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { belegPruefen } from "./beleg.ts";

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

  let tenant_id: string, qr: string, nur_pruefen: boolean;
  try {
    ({ tenant_id, qr, nur_pruefen = false } = await req.json());
  } catch {
    return antwort({ fehler: "Anfrage nicht lesbar." }, 400);
  }
  if (!qr) return antwort({ fehler: "qr fehlt." }, 400);
  if (!nur_pruefen && !tenant_id) return antwort({ fehler: "tenant_id fehlt." }, 400);

  const url = Deno.env.get("SUPABASE_URL")!;

  // Wer fragt? Mit dem Token des Aufrufers, nicht mit dem Service-Key -
  // sonst könnte jeder Stempel auf ein fremdes Konto buchen.
  const alsNutzer = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: `Bearer ${jwt}` } },
  });
  const { data: { user }, error: authFehler } = await alsNutzer.auth.getUser();
  if (authFehler || !user) return antwort({ fehler: "Anmeldung ungültig." }, 401);

  const geprueft = await belegPruefen(qr);

  /*
   * Nur nachsehen, nichts stempeln.
   *
   * Der Weg, mit dem ein Betrieb überhaupt erst herausfindet, ob seine Kasse
   * einen brauchbaren QR-Code druckt - das muss vor dem Vertrag geklärt sein,
   * nicht drei Wochen danach. Antwortet auch bei ungültiger Signatur mit 200:
   * Die Auskunft ist das Ergebnis, nicht der Fehler.
   */
  if (nur_pruefen) {
    const f = qr.split(";");
    return antwort({
      gueltig: geprueft.gueltig,
      kurve: geprueft.kurve ?? null,
      grund: geprueft.grund ?? null,
      gelesen: geprueft.gueltig || f.length >= 12
        ? {
          kasse: f[1],
          vorgang: f[2],
          daten: f[3],
          transaktion: f[4],
          zaehler: f[5],
          belegzeit: f[7],
          verfahren: f[8],
          zeitformat: f[9],
        }
        : null,
    });
  }

  if (!geprueft.gueltig) {
    // 422, nicht 400: Die Anfrage war in Ordnung, der Beleg ist es nicht.
    return antwort({ fehler: "Die Signatur des Belegs stimmt nicht.", grund: geprueft.grund }, 422);
  }

  const alsDienst = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false },
  });
  const { data, error } = await alsDienst.rpc("service_issue_stamp", {
    p_user_id: user.id,
    p_tenant_id: tenant_id,
    p_proof_ref: qr,
    p_source: "receipt",
  });

  if (error) {
    // Die Fehler der Datenbank unverändert durchreichen: Die App übersetzt
    // sie bereits in Klartext, und hier neu zu formulieren hiesse, dieselbe
    // Übersetzung an zwei Stellen zu pflegen.
    console.error("service_issue_stamp", error);
    const status = error.code === "23505" ? 409 : error.code === "42501" ? 403 : 400;
    return antwort({ fehler: error.message, code: error.code }, status);
  }

  return antwort({ ...(data?.[0] ?? {}), kurve: geprueft.kurve });
});
