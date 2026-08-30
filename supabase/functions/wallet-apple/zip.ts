/*
 * Ein sehr kleiner ZIP-Schreiber.
 *
 * Ein .pkpass ist ein ZIP-Archiv. Es muss nicht komprimiert sein - die
 * Speichermethode 0 ("stored") ist gueltiges ZIP, und die Dateien in einem
 * Pass sind klein. Das spart eine Bibliothek, die sonst den privaten
 * Schluessel im selben Prozess saehe.
 *
 * Aufbau nach APPNOTE.TXT: je Datei ein lokaler Kopf mit den Daten, danach
 * ein zentrales Verzeichnis und ein Abschluss.
 */

/** CRC-32, wie ZIP es verlangt. Tabelle einmalig, dann Byte fuer Byte. */
const TABELLE = (() => {
  const t = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
    t[i] = c >>> 0;
  }
  return t;
})();

export function crc32(daten: Uint8Array): number {
  let c = 0xFFFFFFFF;
  for (const b of daten) c = TABELLE[(c ^ b) & 0xFF] ^ (c >>> 8);
  return (c ^ 0xFFFFFFFF) >>> 0;
}

function zahlen(werte: [number, number][]): Uint8Array {
  // [Wert, Breite in Bytes] - alles little endian, wie im Format vorgesehen.
  const gesamt = werte.reduce((n, [, b]) => n + b, 0);
  const aus = new Uint8Array(gesamt);
  let i = 0;
  for (const [wert, breite] of werte) {
    for (let b = 0; b < breite; b++) aus[i++] = (wert >>> (b * 8)) & 0xFF;
  }
  return aus;
}

function verketten(teile: Uint8Array[]): Uint8Array {
  const gesamt = teile.reduce((n, t) => n + t.length, 0);
  const aus = new Uint8Array(gesamt);
  let i = 0;
  for (const t of teile) { aus.set(t, i); i += t.length; }
  return aus;
}

/** Packt die Dateien in ein ZIP. Reihenfolge bleibt wie uebergeben. */
export function zipBauen(dateien: Record<string, Uint8Array>): Uint8Array {
  const koder = new TextEncoder();
  const stuecke: Uint8Array[] = [];
  const verzeichnis: Uint8Array[] = [];
  let versatz = 0;

  for (const [name, inhalt] of Object.entries(dateien)) {
    const nameBytes = koder.encode(name);
    const summe = crc32(inhalt);

    const lokal = verketten([
      zahlen([[0x04034b50, 4], [20, 2], [0, 2], [0, 2], [0, 2], [0, 2]]),
      zahlen([[summe, 4], [inhalt.length, 4], [inhalt.length, 4]]),
      zahlen([[nameBytes.length, 2], [0, 2]]),
      nameBytes,
      inhalt,
    ]);
    stuecke.push(lokal);

    verzeichnis.push(verketten([
      zahlen([[0x02014b50, 4], [20, 2], [20, 2], [0, 2], [0, 2], [0, 2], [0, 2]]),
      zahlen([[summe, 4], [inhalt.length, 4], [inhalt.length, 4]]),
      zahlen([[nameBytes.length, 2], [0, 2], [0, 2], [0, 2], [0, 2], [0, 4], [versatz, 4]]),
      nameBytes,
    ]));

    versatz += lokal.length;
  }

  const verz = verketten(verzeichnis);
  const anzahl = Object.keys(dateien).length;
  return verketten([
    ...stuecke,
    verz,
    zahlen([[0x06054b50, 4], [0, 2], [0, 2], [anzahl, 2], [anzahl, 2],
            [verz.length, 4], [versatz, 4], [0, 2]]),
  ]);
}
