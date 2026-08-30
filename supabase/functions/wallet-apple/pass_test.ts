/*
 * Prüfungen für den Apple-Wallet-Pass.
 *
 * Läuft ohne Zertifikat: pass.json, Manifest und ZIP sind alles, was sich
 * ohne Apple prüfen lässt — und zusammen sind sie der Pass bis auf die
 * Signatur. Das Archiv wird am Ende gegen `unzip` gehalten.
 *
 *   node --experimental-strip-types supabase/functions/wallet-apple/pass_test.ts
 */
import { leuchtkraft, manifestBauen, passBauen, rgb, type Betrieb } from "./pass.ts";
import { crc32, zipBauen } from "./zip.ts";
import { execFileSync } from "node:child_process";
import { writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

let fehler = 0;
function pruefe(b: boolean, t: string) {
  console.log(`${b ? "OK   " : "FEHLGESCHLAGEN:"} ${t}`);
  if (!b) fehler++;
}

const BETRIEB: Betrieb = {
  slug: "baeckerei-mustermann",
  name: "Bäckerei Meier",
  primary_color: "#4CAF50",
  stamps_per_card: 10,
};
const UMZUG = "https://example.test/app/?b=baeckerei-mustermann&karte=" + "a".repeat(64);

// ------------------------------------------------------------------ Farben
pruefe(rgb("#4CAF50") === "rgb(76, 175, 80)", "Hex wird zu rgb() — Apple nimmt kein Hex");
pruefe(rgb("#fc0") === "rgb(255, 204, 0)", "Kurzform wird ausgeschrieben");
pruefe(rgb(null) === "rgb(76, 76, 76)", "Ohne Farbe gibt es einen Ersatzwert");
pruefe(rgb("grün") === "rgb(76, 76, 76)", "Unbrauchbares kippt den Pass nicht");

pruefe(leuchtkraft("#ffffff") > 0.9, "Weiß ist hell");
pruefe(leuchtkraft("#000000") < 0.01, "Schwarz ist dunkel");

// ------------------------------------------------------------------ pass.json
const p = passBauen({
  passTypeId: "pass.de.byteundhandwerk.treuebiss",
  teamId: "ABCDE12345",
  seriennummer: "0123456789abcdef",
  betrieb: BETRIEB, stempel: 3, umzugUrl: UMZUG,
});

for (const k of ["formatVersion", "passTypeIdentifier", "teamIdentifier",
                 "serialNumber", "organizationName", "description"]) {
  pruefe((p as Record<string, unknown>)[k] !== undefined, `Pflichtfeld ${k} ist gesetzt`);
}
pruefe(p.formatVersion === 1, "formatVersion ist 1");
pruefe(!!p.storeCard, "Der Stil ist storeCard — die Bauform für Treuekarten");
pruefe(p.storeCard.primaryFields[0].value === "3 von 10", "Der Stempelstand steht vorn");
pruefe(p.barcodes[0].format === "PKBarcodeFormatQR", "Der Strichcode ist ein QR");
pruefe(p.barcodes[0].message === UMZUG, "Er trägt den Umzugslink");
pruefe(p.barcodes[0].messageEncoding === "iso-8859-1", "Mit der von Apple erwarteten Kodierung");
pruefe(!JSON.stringify(p).includes("a".repeat(64)) === false,
  "Der Kartenschlüssel steckt im Link — das ist gewollt");
pruefe(!p.serialNumber.includes("a".repeat(64)),
  "Aber nicht in der Seriennummer, die auf dem Sperrbildschirm steht");

// Helle Marke -> schwarze Schrift, dunkle -> weisse.
const hell = passBauen({ ...{ passTypeId: "x", teamId: "y", seriennummer: "z" },
  betrieb: { ...BETRIEB, primary_color: "#FFF9C4" }, stempel: 0, umzugUrl: UMZUG });
pruefe(hell.foregroundColor === "rgb(0, 0, 0)", "Auf heller Marke steht schwarze Schrift");
const dunkel = passBauen({ ...{ passTypeId: "x", teamId: "y", seriennummer: "z" },
  betrieb: { ...BETRIEB, primary_color: "#1B3A21" }, stempel: 0, umzugUrl: UMZUG });
pruefe(dunkel.foregroundColor === "rgb(255, 255, 255)", "Auf dunkler Marke weiße");

// ------------------------------------------------------------------ Manifest
const k = new TextEncoder();
const dateien: Record<string, Uint8Array> = {
  "pass.json": k.encode(JSON.stringify(p, null, 2)),
  "icon.png": new Uint8Array([0x89, 0x50, 0x4E, 0x47, 13, 10, 26, 10]),
};
const manifest = await manifestBauen(dateien);
pruefe(Object.keys(manifest).length === 2, "Das Manifest führt jede Datei");
pruefe(/^[0-9a-f]{40}$/.test(manifest["pass.json"]), "Die Hashes sind SHA-1, wie das Format verlangt");
// Bekannter SHA-1 des leeren Strings
const leer = await manifestBauen({ "x": new Uint8Array(0) });
pruefe(leer["x"] === "da39a3ee5e6b4b0d3255bfef95601890afd80709",
  "SHA-1 trifft den bekannten Prüfwert");

// ------------------------------------------------------------------ ZIP
pruefe(crc32(k.encode("123456789")) === 0xCBF43926, "CRC-32 trifft den bekannten Prüfwert");

dateien["manifest.json"] = k.encode(JSON.stringify(manifest, null, 2));
dateien["signature"] = new Uint8Array([0x30, 0x82, 0x01, 0x00]);
const paket = zipBauen(dateien);

const ordner = mkdtempSync(join(tmpdir(), "pkpass-"));
const pfad = join(ordner, "test.pkpass");
writeFileSync(pfad, paket);

try {
  execFileSync("unzip", ["-t", pfad], { stdio: "pipe" });
  pruefe(true, "Das Archiv besteht die Integritätsprüfung von unzip");
} catch {
  pruefe(false, "Das Archiv besteht die Integritätsprüfung von unzip");
}
const liste = execFileSync("unzip", ["-Z1", pfad], { encoding: "utf8" }).trim().split("\n");
pruefe(liste.includes("pass.json") && liste.includes("manifest.json")
  && liste.includes("signature") && liste.includes("icon.png"),
  "Alle vier Pflichtbestandteile liegen im Archiv");
const zurueck = execFileSync("unzip", ["-p", pfad, "pass.json"], { encoding: "utf8" });
pruefe(JSON.parse(zurueck).serialNumber === "0123456789abcdef",
  "pass.json kommt unverändert wieder heraus");

console.log(fehler === 0
  ? "\n--- Apple-Wallet-Pass bestanden ---"
  : `\n${fehler} Prüfung(en) fehlgeschlagen`);
process.exitCode = fehler === 0 ? 0 : 1;
