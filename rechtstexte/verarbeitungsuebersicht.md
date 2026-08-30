# Was die TreueBiss-App tatsächlich verarbeitet

Zuarbeit für die Konfiguration der Datenschutzerklärung bei der IT-Recht
Kanzlei. **Kein Rechtstext** — eine Bestandsaufnahme aus dem Quellcode, damit
die Angaben im Konfigurator stimmen.

Stand: 31.08.2026, geprüft gegen `supabase/schema.sql`, `web/app/app.js` und
die beiden Wallet-Funktionen.

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
| **Kartenschlüssel** (32 zufällige Bytes) | `memberships.card_token` | beim Öffnen der Karte |

### Der Kartenschlüssel

Er identifiziert **die Karte**, nicht das Gerät, und macht sie damit auf ein
anderes Gerät übertragbar. Er enthält keine Personendaten — es sind zufällige
Bytes — ist aber ein **Inhaberpapier**: Wer ihn hat, hat die Karte. Er steckt
im QR-Code der App und in beiden Wallet-Pässen.

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
| **Google** | speichert den Wallet-Pass — **nur wenn der Kunde ihn hinzufügt** | Google LLC, USA |
| **Apple** | Wallet-Pass auf dem Gerät, Abgleich über iCloud | Apple Inc., USA |

### Was die Wallet-Pässe übertragen

Das ist neu und im Konfigurator leicht zu übersehen — **es ist freiwillig**:
Ohne Tippen auf „Zu Google Wallet" oder „Zu Apple Wallet" fließt dorthin
nichts.

**Google** bekommt und *speichert auf seinen Servern* ein Objekt je Karte:

| Feld | Inhalt |
|---|---|
| Objektkennung | gekürzter SHA-256 des Kartenschlüssels — **nicht** der Schlüssel |
| Stempelstand | etwa `3/10` |
| Strichcode | die Adresse der Karte — **darin steht der Kartenschlüssel im Klartext** |
| Klasse | Name und Farbe des Betriebs |

**Apple** bekommt nichts von uns: Der Pass ist eine Datei, die das Gerät
erhält. Was danach passiert — Anzeige, Abgleich über iCloud zwischen den
Geräten des Kunden — liegt bei Apple und dem Kunden.

> **Der Punkt, der in die Erklärung gehört:** Wer den Google-Pass hinzufügt,
> hinterlegt bei Google seinen Stempelstand und die Karten-Adresse. Ein
> Personenbezug entsteht dort über das Google-Konto, mit dem gespeichert wird
> — für TreueBiss bleibt der Kunde anonym, für Google nicht. Rechtsgrundlage
> ist die Handlung des Kunden selbst; ob lit. b oder lit. a trägt, gehört
> geprüft.

> **Zwei Punkte, die in der Erklärung stehen müssen und leicht übersehen
> werden:** GitHub Pages und esm.sh bekommen bei jedem Aufruf die IP-Adresse
> des Geräts. Beide sind keine Auftragsverarbeiter im engeren Sinne, aber es
> sind Datenflüsse an Dritte. Für Supabase ist ein AVV nötig; die
> Rechtsgrundlage für die Drittlandsübermittlung ist zu klären.

## 5. Löschung der Kaufnachweise

**Zwei Fristen, weil zwei Zwecke zu verschiedenen Zeiten enden.** Beide sind je
Betrieb einstellbar; die Zahlen unten sind die Vorgaben.

| Stufe | Vorgabe | Was passiert |
|---|---|---|
| `proof_detail_days` | 30 Tage | **Betrag und Kassennummer werden geleert.** Die Zeile bleibt. |
| `proof_retention_days` | 90 Tage | Der Nachweis wird gelöscht. |

Nach Stufe 1 ist die Einkaufshistorie weg, während der Nachweis weiter
verhindert, dass derselbe Bon zweimal zählt.

**Für die Erklärung ist die Frist des jeweiligen Betriebs maßgeblich, nicht die
Vorgabe.** Art. 13 Abs. 2 lit. a DSGVO erlaubt ausdrücklich, statt einer festen
Dauer die Kriterien zu nennen — hier also: *bis der Nachweis für die Prüfung
auf Mehrfachnutzung nicht mehr gebraucht wird.*

### Warum diese Fristen und keine gesetzlichen

Es gibt für diese Daten **keine gesetzliche Aufbewahrungspflicht.** § 147 AO
verpflichtet den Steuerpflichtigen zu seinen eigenen Buchungsbelegen — das ist
der Betrieb mit seiner Kasse und seiner TSE, nicht diese App mit ihrem Verweis
darauf. Damit greift ungebremst Art. 5 Abs. 1 lit. e DSGVO: nur so lange wie
nötig.

### Die Untergrenze ist abgeleitet, nicht gewählt

Die Aufbewahrung kann nicht beliebig kurz sein. Ein Beleg wird abgelehnt,
sobald er älter ist als `proof_max_age_minutes` — diese Prüfung steht **vor**
dem Eindeutigkeitsschlüssel. Innerhalb dieses Fensters hält allein der
gespeicherte Nachweis den zweiten Stempel ab. Die Datenbank erzwingt deshalb:

    proof_retention_days * 1440 >= proof_max_age_minutes

Ebenso zählt das Tageslimit die Nachweise des laufenden Tages; ein Tag ist das
Minimum.

### Was noch offen ist

1. **`stamps`, `vouchers`, `memberships`** werden nicht gelöscht. Das ist die
   Leistung selbst — wer sie löscht, nimmt dem Kunden seine Karte. Wann das
   geschehen soll, ist eine Entscheidung des Betriebs.
2. **Löschweg für den Kunden** (Art. 17). Ohne Konto gibt es niemanden, der
   einen Antrag stellen kann — außer über die App selbst, solange sie
   installiert ist. Eine Funktion „Karte löschen" fehlt.
3. ~~Der Zeitplan.~~ **Erledigt:** Supabase Cron ruft
   `cleanup_expired_proofs()` täglich um 01:20 GMT auf. Die Läufe stehen in
   `cron.job_run_details` — dort lässt sich im Zweifel belegen, dass gelöscht
   wurde.

## 6. Rechtsgrundlagen — Vorschlag zur Prüfung

| Verarbeitung | Vorschlag |
|---|---|
| Karte führen, Stempel, Gutscheine | Art. 6 Abs. 1 lit. b DSGVO — der Kunde will genau das |
| Kaufnachweis gegen Mehrfachnutzung | Art. 6 Abs. 1 lit. f — Schutz vor Missbrauch |
| Betrag und Kassennummer aufbewahren | **zu klären**, ob lit. b trägt oder lit. f |
| Speicherung auf dem Gerät | § 25 Abs. 2 Nr. 2 TDDDG — für den Dienst erforderlich |
| Wallet-Pass erzeugen und übertragen | **zu klären** — der Kunde stößt es an, aber es fließt an einen Dritten |

*Das ist eine Einschätzung aus der Technik, keine Rechtsberatung. Die Zeile mit
dem Betrag ist die, die geprüft gehört.*

---

## Was daraus für den Konfigurator folgt

Wenn die IT-Recht Kanzlei nach „Kundenkonto", „Newsletter", „Tracking" oder
„Zahlungsdaten" fragt: durchweg **nein**. Wenn nach verarbeiteten Daten
gefragt wird, ist die ehrliche Antwort: *pseudonyme Kennung, Zeitpunkte,
Kassen- und Vorgangsnummer sowie der Einkaufsbetrag.*
