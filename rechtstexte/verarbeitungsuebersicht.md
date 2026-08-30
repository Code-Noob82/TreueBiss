# Was die TreueBiss-App tatsächlich verarbeitet

Zuarbeit für die Konfiguration der Datenschutzerklärung bei der IT-Recht
Kanzlei. **Kein Rechtstext** — eine Bestandsaufnahme aus dem Quellcode, damit
die Angaben im Konfigurator stimmen.

Stand: 30.08.2026, geprüft gegen `supabase/schema.sql` und `web/app/app.js`.

---

## Die Kurzfassung für den Konfigurator

- **Kein Kundenkonto.** Keine Registrierung, kein Name, keine E-Mail-Adresse,
  kein Passwort. Die Anmeldung ist anonym (Supabase *Anonymous Sign-In*); es
  entsteht eine zufällige Kennung ohne Bezug zu einer Person.
- **Keine Werbung, kein Tracking, keine Analyse.** Die App bindet weder
  Analysedienste noch Werbenetzwerke ein. Schriften liegen auf dem eigenen
  Server, nicht bei Google.
- **Aber: Kaufdaten entstehen trotzdem.** Wer über den Kassenbon sammelt,
  hinterlässt Kassennummer, Vorgangsnummer, **Betrag** und Zeitpunkt. Siehe
  unten — das ist der Punkt, der in der Erklärung am genauesten stehen muss.

---

## 1. Verarbeitete Daten

| Was | Wo | Wann |
|---|---|---|
| Zufällige Nutzerkennung (UUID) | `auth.users` | bei der ersten Nutzung |
| Zugehörigkeit zu einem Betrieb | `memberships` | beim Öffnen der Karte |
| Stempel: Zeitpunkt, Betrieb | `stamps` | bei jedem Stempel |
| Gutscheine: Ausstellung, Ablauf, Einlösung | `vouchers` | bei voller Karte |
| Eingelöste Coupons: Angebot, Zeitpunkt | `offer_redemptions` | beim Einlösen |
| **Kaufnachweise** | `stamp_proofs` | bei jedem Stempel |

### Was genau im Kaufnachweis steht

Das ist der heikelste Teil und der einzige, bei dem Kaufverhalten anfällt.

**Beim Kassenbon** (erkannter DSFinV-K-QR-Code):

| Feld | Inhalt |
|---|---|
| `proof_ref` | `<Kassenseriennummer>:<Vorgangsnummer>` |
| `register_serial` | Seriennummer der Kasse |
| `amount_cents` | **Bruttobetrag des Einkaufs in Cent** |
| `signature_verified` | ob die TSE-Signatur geprüft wurde |
| `created_at` | Zeitpunkt |

Der QR-Code wird **nicht vollständig gespeichert** — nur die oben genannten
Felder werden herausgelöst. Der Warenkorb ist im Bon-QR ohnehin nicht
enthalten, es steht kein einziger Artikel darin.

**Beim Tresen-Code:** `tresen:<rotierender Code>:<Nutzerkennung>`, kein Betrag,
keine Kassendaten. Er belegt Anwesenheit, nicht Kauf.

**Bei freiem Nachweis** (wenn der Betrieb das erlaubt): die gescannte
Zeichenkette unverändert. Was darin steht, hängt davon ab, was gescannt wurde.

> **Für die Erklärung wichtig:** Über `amount_cents` und `created_at` entsteht
> je Nutzerkennung eine Historie von Einkaufsbeträgen und -zeitpunkten bei
> diesem Betrieb. Das ist pseudonym, aber es ist Kaufverhalten. Die Aussage
> „wir speichern keine Kaufdaten" wäre falsch.

## 2. Was **nicht** verarbeitet wird

- Name, Anschrift, E-Mail, Telefonnummer, Geburtsdatum
- Standortdaten
- Gerätekennungen, Werbe-IDs, Fingerprinting
- Einzelne gekaufte Artikel
- Zahlungsdaten

## 3. Speicherung auf dem Gerät

Kein Cookie. Im `localStorage` des Browsers:

| Schlüssel | Inhalt | Zweck |
|---|---|---|
| `treuebiss:<betrieb>` | zuletzt gesehener Stand: Betrieb, Stempelzahl, Gutscheine | Karte auch ohne Verbindung anzeigen |
| `treuebiss:letzter-betrieb` | Kürzel des zuletzt geöffneten Betriebs | beim nächsten Aufruf denselben Betrieb zeigen |

Dazu die Anmeldung der Supabase-Bibliothek (Token) im selben Speicher. Ein
Service Worker legt Programmdateien, Schriften und Bilder in den Browsercache —
**keine Nutzerdaten**.

Alles davon verschwindet, wenn die Browserdaten gelöscht werden. Damit ist auch
die anonyme Anmeldung weg und die Karte nicht mehr erreichbar.

## 4. Empfänger und Drittland

| Dienst | Rolle | Ort |
|---|---|---|
| **Supabase** | Datenbank, Anmeldung, Serverfunktionen | Projekt in `eu-central-1` (Frankfurt) |
| **GitHub Pages** | Auslieferung der Web-App | GitHub Inc., USA |
| **esm.sh** | CDN, liefert die Supabase-Bibliothek | Drittanbieter-CDN |

> **Zwei Punkte, die in der Erklärung stehen müssen und leicht übersehen
> werden:** GitHub Pages und esm.sh bekommen bei jedem Aufruf die IP-Adresse
> des Geräts. Beide sind keine Auftragsverarbeiter im engeren Sinne, aber es
> sind Datenflüsse an Dritte. Für Supabase ist ein AVV nötig; die
> Rechtsgrundlage für die Drittlandsübermittlung ist zu klären.

## 5. Löschung — offener Punkt

**Es gibt derzeit keine automatische Löschung.** Weder für `stamps` noch für
`stamp_proofs`, `vouchers` oder `offer_redemptions`. Es gibt auch keine
Funktion, mit der ein Kunde seine Daten selbst löscht.

Technisch räumt `on delete cascade` alles ab, sobald die Zeile in `auth.users`
verschwindet — es löscht sie nur niemand.

Vor dem Pilotbetrieb zu entscheiden:

1. **Aufbewahrungsdauer** für `stamp_proofs`. Fachlich nötig ist der Eintrag
   nur, damit derselbe Bon nicht zweimal zählt. Nach Ablauf der Kartenlaufzeit
   hat er keinen Zweck mehr.
2. **Löschweg für den Kunden.** Ohne Konto gibt es niemanden, der einen Antrag
   stellen kann — außer über die App selbst, solange sie installiert ist.
3. **Automatische Bereinigung durch Supabase.** Anonyme Nutzer werden nicht von
   selbst entfernt; das ist Absicht, weil sonst Karten verschwänden.

## 6. Rechtsgrundlagen — Vorschlag zur Prüfung

| Verarbeitung | Vorschlag |
|---|---|
| Karte führen, Stempel, Gutscheine | Art. 6 Abs. 1 lit. b DSGVO — der Kunde will genau das |
| Kaufnachweis gegen Mehrfachnutzung | Art. 6 Abs. 1 lit. f — Schutz vor Missbrauch |
| Betrag und Kassennummer aufbewahren | **zu klären**, ob lit. b trägt oder lit. f |
| Speicherung auf dem Gerät | § 25 Abs. 2 Nr. 2 TDDDG — für den Dienst erforderlich |

*Das ist eine Einschätzung aus der Technik, keine Rechtsberatung. Die Zeile mit
dem Betrag ist die, die geprüft gehört.*

---

## Was daraus für den Konfigurator folgt

Wenn die IT-Recht Kanzlei nach „Kundenkonto", „Newsletter", „Tracking" oder
„Zahlungsdaten" fragt: durchweg **nein**. Wenn nach verarbeiteten Daten
gefragt wird, ist die ehrliche Antwort: *pseudonyme Kennung, Zeitpunkte,
Kassen- und Vorgangsnummer sowie der Einkaufsbetrag.*
