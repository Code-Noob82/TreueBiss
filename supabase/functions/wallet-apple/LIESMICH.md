# Apple Wallet einrichten

Der Code steht und ist bis auf Apples eigene Annahme vollständig geprüft.
Was fehlt, sind Zertifikate — die gibt es nur mit einer Mitgliedschaft im
**Apple Developer Program**.

## 1. Pass Type ID anlegen

Im Apple Developer Portal unter **Certificates, Identifiers & Profiles →
Identifiers** eine neue **Pass Type ID** anlegen, etwa
`pass.de.byteundhandwerk.treuebiss`. Der Name ist frei, muss aber mit `pass.`
beginnen und später exakt im Pass stehen.

## 2. Zertifikat erzeugen

Zur Pass Type ID ein Zertifikat anfordern. Der Weg führt über eine
Zertifikatsanforderung aus der Schlüsselbundverwaltung; heraus kommt eine
`.cer`-Datei. Diese in PEM wandeln und den privaten Schlüssel dazu holen:

```bash
openssl x509 -inform DER -in passtype.cer -out zertifikat.pem
# Aus dem Schluesselbund als .p12 exportieren, dann:
openssl pkcs12 -in zertifikat.p12 -nocerts -nodes -out schluessel.pem -legacy
```

`-legacy` braucht es bei neueren OpenSSL-Versionen, weil der Schlüsselbund
noch mit RC2 verschlüsselt.

## 3. WWDR-Zwischenzertifikat

Ohne dieses Glied reicht die Kette nicht bis zu Apples Wurzel, und Wallet
lehnt den Pass ab — ohne zu sagen warum. Es steht bei Apple unter *Apple PKI*
zum Herunterladen und wird genauso gewandelt:

```bash
openssl x509 -inform DER -in AppleWWDRCAG4.cer -out wwdr.pem
```

## 4. Secrets setzen

```bash
supabase secrets set --project-ref <ref> \
  APPLE_PASS_TYPE_ID=pass.de.byteundhandwerk.treuebiss \
  APPLE_TEAM_ID=XXXXXXXXXX \
  APPLE_PASS_CERT="$(cat zertifikat.pem)" \
  APPLE_PASS_KEY="$(cat schluessel.pem)" \
  APPLE_WWDR_CERT="$(cat wwdr.pem)"
```

Die Team-ID steht im Developer-Portal oben rechts. Ist der private Schlüssel
passwortgeschützt, kommt `APPLE_PASS_KEY_PASSWORT` dazu.

## 5. Ausrollen und prüfen

```bash
supabase functions deploy wallet-apple --project-ref <ref>
```

Ob die Zertifikate taugen, sagt die Funktion ohne jedes Gerät:

```bash
curl -X POST "$SUPABASE_URL/functions/v1/wallet-apple" \
  -H "Authorization: Bearer <nutzer-jwt>" -H "Content-Type: application/json" \
  -d '{"pruefen":true}'
```

Zurück kommen Betreff, Team, Gültigkeit und Aussteller beider Zertifikate.
**Ein abgelaufenes Zertifikat fällt sonst erst dem Kunden auf** — als Pass,
den Wallet kommentarlos ablehnt.

## Was der Pass enthält

| Datei | Inhalt |
|---|---|
| `pass.json` | `storeCard` mit Stempelstand, Betrieb, Farben des Betriebs |
| `icon.png`, `icon@2x.png`, `logo.png` | aus der Web-App geladen |
| `manifest.json` | SHA-1 je Datei — Vorgabe des Formats, keine Wahl |
| `signature` | abgetrennte PKCS#7-Signatur über das Manifest |

Die **Seriennummer** ist ein gekürzter SHA-256 des Kartenschlüssels, nicht der
Schlüssel selbst: Sie steht im Pass und wäre sonst auf dem Sperrbildschirm
lesbar. Stabil, damit ein zweiter Abruf den Pass ersetzt statt einen weiteren
danebenzulegen.

Der **Strichcode** trägt den Umzugslink, wie beim Google-Pass und im QR der
App. Bei TreueBiss scannt der Kunde, nicht die Kasse.

Die **Schriftfarbe** folgt der WCAG-Helligkeit der Markenfarbe — dieselbe
Rechnung wie in der Web-App, damit der Pass nicht anders aussieht als die
Karte.

## Was geprüft ist, und was nicht

```bash
node --experimental-strip-types supabase/functions/wallet-apple/pass_test.ts
```

29 Prüfungen ohne Zertifikat: Pflichtfelder, Farbumrechnung, Schriftfarbe,
SHA-1 gegen den bekannten Prüfwert, CRC-32 gegen den bekannten Prüfwert, und
das fertige Archiv gegen das System-`unzip`.

Zusätzlich wurde die **Signatur gegen eine selbst erzeugte Zertifikatskette
geprüft** — `openssl smime -verify` bestätigt die abgetrennte Signatur über
das Manifest. Damit ist alles belegt außer der einen Frage, die nur Apple
beantwortet: ob ein Pass mit **echtem** Pass Type ID-Zertifikat angenommen
wird.

## Noch nicht gebaut

Der Pass trägt den Stempelstand vom Abruf und **aktualisiert sich nicht von
selbst**. Dafür bräuchte es einen Webdienst nach Apples `webServiceURL` samt
Push über APNs — ein eigenes Zertifikat und ein eigener Endpunkt. Bis dahin
holt der Kunde den Pass neu, oder die Karte in der App ist die Wahrheit.
