/*
 * Prüfung des Beleg-Prüfers gegen echte TSE-Signaturen.
 *
 * Die beiden Belege stammen aus der Testsammlung von
 * github.com/berohndo/tse_signature_verification (Apache-2.0) und sind von
 * echten Kassen signiert - einer über secp256r1, einer über brainpoolP384r1.
 * Selbst erzeugte Testdaten wären hier wertlos: Sie würden nur beweisen,
 * dass mein Nachbau zu sich selbst passt.
 *
 * Laufen lassen mit:
 *   deno test supabase/functions/beleg-pruefen/
 *   node --experimental-strip-types --test supabase/functions/beleg-pruefen/
 */
import { belegPruefen } from "./beleg.ts";
import { KURVEN, kurvePruefen } from "./kurven.ts";

const QR_256 =
  "V0;ERS 8cb8e2de-4052-481b-945b-118022951944;Kassenbeleg-V1;" +
  "Beleg^21.42_0.00_0.00_0.00_0.00^21.42:Unbar;1;31;" +
  "2021-08-23T14:36:27.000Z;2021-08-23T14:36:33.000Z;ecdsa-plain-SHA256;unixTime;" +
  "TGnWiq3ZW7gi4Vs+DxLGsJZj9v271dHmhQAcb057F3oWkdKJ61UW2LLVTZQhW673yLa53Mm6oPeMU1Ns3ZOH7w==;" +
  "BGFKQP7EENf3s5hTDXvlh+xyJ1Q9BNIa9LyYbYK+pTAKAGQ2fmI40p5QOrpHpvb+UuOrNQJdhzggHNfyyyDyf/g=";

const QR_384 =
  "V0;AMA-6200;KassenBeleg-V1;Beleg^2.90_0.00_0.00_0.00_2.10^5.00:Unbar;160504;344413;" +
  "2021-07-23T10:12:54.000Z;2021-07-23T10:12:55.000Z;ecdsa-plain-SHA384;unixTime;" +
  "NgqBkWMvhLmCKa9cJJ8JodfAdlkfcFFyW0J7Ks9lTz9I4QFKhLzyGF/02kWsCRg0LoiJtkq+0Ak9GovodNFLOBG00ewEj40/GbI9zLtNt9j90w4Sz3GcxTSrr3rqhIhN;" +
  "BCd1vZvSJsJwBTqshgDVsrG4Gg+oN3jeeFEgjGiKs9ELd170vy/jO3iMMF6tAVUfjyYn9jRXng8Z4qWXaqoJ53+y+OFeSY/lQsdZWCODkzhlhkJxJw2k9Z1A4gFX6Riotw==";

const pruefungen: Array<[string, () => Promise<void> | void]> = [];
const test = (name: string, f: () => Promise<void> | void) => pruefungen.push([name, f]);
const gleich = (ist: unknown, soll: unknown, was: string) => {
  if (ist !== soll) throw new Error(`${was}: ${JSON.stringify(ist)} statt ${JSON.stringify(soll)}`);
};

test("Die Kurvenparameter stimmen", () => {
  for (const name of Object.keys(KURVEN)) {
    const fehler = kurvePruefen(name);
    if (fehler) throw new Error(fehler);
  }
});

test("Ein echter secp256r1-Beleg wird als gültig erkannt", async () => {
  const e = await belegPruefen(QR_256);
  gleich(e.gueltig, true, "gueltig");
  gleich(e.kurve, "secp256r1", "Kurve");
});

test("Ein echter brainpoolP384r1-Beleg wird als gültig erkannt", async () => {
  const e = await belegPruefen(QR_384);
  gleich(e.gueltig, true, "gueltig");
  gleich(e.kurve, "brainpoolP384r1", "Kurve");
});

// Jede dieser Änderungen muss die Signatur brechen. Fällt eine davon aus,
// prüft der Prüfer nicht das, wofür er da ist.
const manipulationen: Array<[string, string]> = [
  ["Betrag geändert", QR_384.replace("5.00:Unbar", "9.00:Unbar")],
  ["Kassennummer geändert", QR_384.replace("AMA-6200", "AMA-6201")],
  ["Belegzeit geändert", QR_384.replace("10:12:55", "10:13:55")],
  ["Transaktionsnummer geändert", QR_384.replace(";160504;", ";160505;")],
  ["Signaturzähler geändert", QR_384.replace(";344413;", ";344414;")],
  ["Signatur um ein Zeichen geändert", QR_384.replace("NgqBkWMv", "NgqBkWMw")],
  ["Steuersätze geändert", QR_384.replace("2.90_0.00", "2.80_0.10")],
];
for (const [was, qr] of manipulationen) {
  test(`Abgelehnt: ${was}`, async () => {
    gleich((await belegPruefen(qr)).gueltig, false, was);
  });
}

test("Fremder Schlüssel wird abgelehnt", async () => {
  const f = QR_384.split(";");
  f[11] = QR_256.split(";")[11];
  gleich((await belegPruefen(f.join(";"))).gueltig, false, "fremder Schlüssel");
});

/*
 * Ein echter Bon aus dem Lebensmitteleinzelhandel, August 2026. Er ist aus
 * zwei Gründen hier:
 *
 * 1. Sein Schlüssel steht als DER-SPKI im QR, nicht als roher Punkt - eine
 *    Form, die beide Referenzvektoren nicht haben und die der Prüfer vorher
 *    mit "Schlüssel hat keine bekannte Form" abgewiesen hätte.
 * 2. Er lässt sich mit dem bekannten Aufbau NICHT verifizieren, obwohl der
 *    Schlüssel gültig ist (Punkt liegt auf secp384r1, r und s im Bereich).
 *    480 Abwandlungen des Aufbaus wurden durchprobiert - keine passt. Der
 *    Test hält diesen Stand fest: Der Prüfer muss so einen Beleg als
 *    ungültig melden und dabei die Kurve richtig benennen, statt zu
 *    behaupten, er könne den Schlüssel nicht lesen.
 *
 * Fällt dieser Test eines Tages um, weil "gueltig" true wird, ist das eine
 * gute Nachricht - dann stimmt der Aufbau doch und die Erwartung gehört
 * umgedreht.
 */
const QR_LEH_SPKI =
  "V0;International.D.039.073.07;Kassenbeleg-V1;" +
  "Beleg^17.67_38.71_0.00_0.00_0.00^56.38:Unbar;829153;1659992;" +
  "2026-08-07T09:20:51.000Z;2026-08-07T09:21:55.000Z;ecdsa-plain-SHA384;unixTime;" +
  "R29intMaHRAQE/aSM/Z+R/3XS4umfJBWTJXZv4Fr+PNQrjZCHat6CkGINcaPCIP8BkfsFMtc5u7QWoJQg3DA1eAQzJxhqesO57HNosOztOWC+eaXh0Z5/5oVVPrpRJZF;" +
  "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEJc9CPoY7NA3CIVx1NxKZjMQac4AVq3+N+moSPHDw202KjVFv5QeTS/JMIcH34pxu8vjUb0kKwx3Qcagxq4vdL2KwQFo3AbEEfvBW299+yODBuDGfBKKpgxTi+v3eZYl+";

test("Ein SPKI-Schlüssel wird gelesen und die Kurve benannt", async () => {
  const e = await belegPruefen(QR_LEH_SPKI);
  gleich(e.gueltig, false, "gueltig");
  gleich(e.kurve, "secp384r1", "Kurve aus dem SPKI");
  gleich(e.grund, "Signatur passt nicht zum Schlüssel (secp384r1)", "Grund");
});

test("Eine freie Zeichenkette ist kein Beleg", async () => {
  gleich((await belegPruefen("BON-4711")).gueltig, false, "freie Zeichenkette");
});

test("Ein unbekanntes Zeitformat wird abgelehnt, nicht geraten", async () => {
  gleich((await belegPruefen(QR_384.replace(";unixTime;", ";utcTime;"))).gueltig, false, "utcTime");
});

const laeuft = async () => {
  let fehler = 0;
  for (const [name, f] of pruefungen) {
    try {
      await f();
      console.log(`OK    ${name}`);
    } catch (e) {
      console.log(`FEHLGESCHLAGEN: ${name} - ${(e as Error).message}`);
      fehler++;
    }
  }
  console.log(fehler ? `\n${fehler} von ${pruefungen.length} fehlgeschlagen.`
                     : `\nAlle ${pruefungen.length} Prüfungen bestanden.`);
  return fehler;
};

const code = await laeuft();
if (code > 0) {
  const g = globalThis as { Deno?: { exit(c: number): void }; process?: { exit(c: number): void } };
  (g.Deno?.exit ?? g.process?.exit)?.(1);
}
