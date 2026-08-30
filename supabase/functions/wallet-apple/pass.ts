/*
 * Baut `pass.json` und das Manifest fuer einen Apple-Wallet-Pass.
 *
 * Bewusst ohne Krypto, ohne Netz und ohne Bibliothek: Was hier entsteht, ist
 * JSON und laesst sich deshalb ohne Zertifikat pruefen. Das Signieren steht in
 * signieren.ts, das Packen in zip.ts.
 *
 * Quelle: https://developer.apple.com/documentation/walletpasses/pass
 */

export type Betrieb = {
  slug: string;
  name: string;
  primary_color: string | null;
  stamps_per_card: number;
};

/**
 * Apple erwartet Farben als `rgb(r, g, b)`. Ein Hexwert wird abgewiesen -
 * der Pass entsteht dann zwar, sieht aber falsch aus.
 */
export function rgb(hex: string | null | undefined, ersatz = "rgb(76, 76, 76)"): string {
  const w = (hex ?? "").trim();
  const voll = /^#[0-9a-fA-F]{3}$/.test(w)
    ? "#" + w.slice(1).split("").map((z) => z + z).join("")
    : w;
  if (!/^#[0-9a-fA-F]{6}$/.test(voll)) return ersatz;
  const n = parseInt(voll.slice(1), 16);
  return `rgb(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255})`;
}

/**
 * Helligkeit nach der WCAG-Formel. Entscheidet, ob die Schrift auf der
 * Markenfarbe schwarz oder weiss sein muss - dieselbe Rechnung wie in der
 * Web-App, damit der Pass nicht anders aussieht als die Karte.
 */
export function leuchtkraft(hex: string | null | undefined): number {
  const w = (hex ?? "#4c4c4c").trim();
  if (!/^#[0-9a-fA-F]{6}$/.test(w)) return 0;
  const teil = (i: number) => {
    const c = parseInt(w.slice(1 + i * 2, 3 + i * 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * teil(0) + 0.7152 * teil(1) + 0.0722 * teil(2);
}

export function passBauen(opts: {
  passTypeId: string;
  teamId: string;
  seriennummer: string;
  betrieb: Betrieb;
  stempel: number;
  umzugUrl: string;
}) {
  const { passTypeId, teamId, seriennummer, betrieb, stempel, umzugUrl } = opts;
  // Ueber der WCAG-Schwelle ist die Flaeche hell genug fuer schwarze Schrift.
  const hell = leuchtkraft(betrieb.primary_color) > 0.179;
  const schrift = hell ? "rgb(0, 0, 0)" : "rgb(255, 255, 255)";

  return {
    formatVersion: 1,
    passTypeIdentifier: passTypeId,
    teamIdentifier: teamId,
    // Stabil je Karte: Ein zweiter Abruf ersetzt den Pass, statt einen
    // weiteren daneben zu legen.
    serialNumber: seriennummer,
    organizationName: betrieb.name,
    description: `Stempelkarte ${betrieb.name}`,
    backgroundColor: rgb(betrieb.primary_color),
    foregroundColor: schrift,
    labelColor: schrift,
    logoText: betrieb.name,
    storeCard: {
      primaryFields: [{
        key: "stempel",
        label: "Gesammelt",
        value: `${stempel} von ${betrieb.stamps_per_card}`,
      }],
      secondaryFields: [{
        key: "betrieb",
        label: "Betrieb",
        value: betrieb.name,
      }],
      backFields: [{
        key: "hinweis",
        label: "Karte mitnehmen",
        value: "Diesen Pass auf einem anderen Gerät scannen, und die Karte "
             + "zieht mit um. Auf dem alten Gerät ist sie danach leer — wie "
             + "eine Papierkarte, die man weitergibt.",
      }, {
        key: "sammeln",
        label: "Stempel sammeln",
        value: umzugUrl,
      }],
    },
    /*
     * Derselbe Inhalt wie beim Google-Pass und wie im QR der App: der
     * Umzugslink. Bei TreueBiss scannt der Kunde, nicht die Kasse - ein
     * Kundencode haette hier niemanden, der ihn einliest.
     */
    barcodes: [{
      format: "PKBarcodeFormatQR",
      message: umzugUrl,
      messageEncoding: "iso-8859-1",
      altText: "Karte mitnehmen",
    }],
  };
}

/**
 * Das Manifest: SHA-1 je Datei im Paket.
 *
 * SHA-1 ist hier keine Wahl, sondern Vorgabe des Formats - die Signatur
 * darunter deckt das Manifest ab, nicht die einzelnen Dateien.
 */
export async function manifestBauen(
  dateien: Record<string, Uint8Array>,
): Promise<Record<string, string>> {
  const manifest: Record<string, string> = {};
  for (const [name, inhalt] of Object.entries(dateien)) {
    const h = await crypto.subtle.digest("SHA-1", inhalt as BufferSource);
    manifest[name] = Array.from(new Uint8Array(h))
      .map((b) => b.toString(16).padStart(2, "0")).join("");
  }
  return manifest;
}
