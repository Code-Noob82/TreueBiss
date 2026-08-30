# Was der ITRK-Konfigurator nicht abfragt

Der Konfigurator arbeitet mit festen Antwortmöglichkeiten und bietet **kein
Freitextfeld**. Alles, wonach er nicht fragt, fehlt in der fertigen Erklärung.

Diese Punkte sind beim Durchlauf am 31.08.2026 aufgefallen und müssen der
IT-Recht Kanzlei **gesondert** mitgeteilt werden — über die Mandantenbetreuung
oder als Ergänzung zum erzeugten Text.

Sortiert danach, wie schwer das Fehlen wiegt.

---

## 1. Kaufdaten werden gespeichert

Der Konfigurator fragt nach Kundenkonto, Newsletter und Werbung — nicht danach,
**was die App im Betrieb erhebt**. Bei jedem Stempel über den Kassenbon landet
in `stamp_proofs`:

| Feld | Inhalt |
|---|---|
| `amount_cents` | **Bruttobetrag des Einkaufs** |
| `register_serial` | Seriennummer der Kasse |
| `proof_ref` | Kassennummer + Vorgangsnummer |
| `created_at` | Zeitpunkt |

Über die pseudonyme Nutzerkennung entsteht damit eine **Historie von
Einkaufsbeträgen und -zeitpunkten**. Das ist der wichtigste Punkt der ganzen
Liste: Ohne ihn beschreibt die Erklärung eine App, die weniger verarbeitet als
die echte.

## 2. Löschfristen

Zwei Stufen, je Betrieb einstellbar; die Werte unten sind die Vorgaben:

- nach **30 Tagen** werden Betrag und Kassennummer geleert, die Zeile bleibt
- nach **90 Tagen** wird der Nachweis gelöscht

Durchgesetzt durch einen täglichen Lauf um 01:20 GMT. Art. 13 Abs. 2 lit. a
DSGVO verlangt die Angabe der Dauer oder der Kriterien — der Konfigurator fragt
nicht danach.

## 3. Empfänger, die in der Auswahlliste fehlen

Beim Hosting standen nur Firebase, Supabase und Vercel zur Wahl. Tatsächlich
beteiligt sind:

| Dienst | Rolle |
|---|---|
| **Supabase** | Datenbank, `eu-central-1` (Frankfurt) — *war auswählbar* |
| **GitHub Pages** | liefert die Web-App aus; GitHub Inc., USA |
| **Cloudflare** | steht vor der Supabase-API — jeder Datenabruf läuft darüber |
| **esm.sh** | CDN für drei JavaScript-Bibliotheken, betreibende Stelle unklar |
| **Google** | speichert den Wallet-Pass — nur wenn der Kunde ihn hinzufügt |

## 4. Wallet-Pässe

Freiwillig, aber folgenreich: Wer den **Google**-Pass hinzufügt, hinterlegt bei
Google ein Objekt mit dem Stempelstand und der Karten-Adresse — und in der
steht der Kartenschlüssel im Klartext. Für TreueBiss bleibt der Kunde anonym,
für Google nicht; der Personenbezug entsteht über das Konto, mit dem
gespeichert wird.

Beim **Apple**-Pass fließt nichts an uns oder an Apple: Der Pass ist eine
Datei, die das Gerät erhält.

## 5. Speicherung auf dem Endgerät

Auf die Cookie-Frage lautet die Antwort „nein" — die App setzt keine. Sie legt
aber drei Einträge im `localStorage` ab, und § 25 TDDDG spricht nicht von
Cookies, sondern vom Speichern von Informationen auf dem Endgerät:

| Schlüssel | Zweck |
|---|---|
| `treuebiss-kunde` | Token der anonymen Anmeldung |
| `treuebiss:<betrieb>` | letzter Kartenstand für die Offline-Anzeige |
| `treuebiss:letzter-betrieb` | zuletzt geöffneter Betrieb |

Dazu ein Service-Worker-Cache mit Programmdateien, Schriften und Bildern —
keine Nutzerdaten. Alles nach hiesiger Einschätzung erforderlich im Sinne von
§ 25 Abs. 2 Nr. 2 TDDDG.

## 6. Kamerazugriff

Die App liest QR-Codes über `getUserMedia`. **Es werden keine Aufnahmen
erstellt, gespeichert oder übertragen** — der Kamerastrom wird im Browser
Bild für Bild ausgewertet und verworfen. Übermittelt wird nur die im Code
enthaltene Zeichenkette.

## 7. Der Kartenschlüssel

32 zufällige Bytes je Karte, die sie auf ein anderes Gerät übertragbar machen.
Enthält keine Personendaten, ist aber ein **Inhaberpapier** und steckt im
QR-Code der App sowie in beiden Wallet-Pässen.

## 8. Freies Nachweisfeld

Erlaubt der Betrieb Nachweise ohne Bon-QR, wird die eingegebene Zeichenkette
unverändert gespeichert. Sie wird nirgends angezeigt und dient nur der Prüfung
auf Mehrfachnutzung — ist aber formal eine Nutzereingabe.

---

## Zwei Fragen, die keine technischen sind

Beide gehören der Kanzlei vorgelegt, nicht im Konfigurator entschieden.

**Wer ist Verantwortlicher?** Der Betrieb bestimmt Zweck und Parameter
(Kartenregeln, Prämien, Angebote, Einlösecode) über die Verwaltung; byte &
Handwerk bestimmt die Mittel (anonyme Anmeldung, Datenumfang, Löschfristen,
Sicherheit). Das ist weder reine Auftragsverarbeitung noch alleinige
Verantwortlichkeit — es sieht nach **gemeinsamer Verantwortlichkeit nach
Art. 26 DSGVO** aus. Die Antwort entscheidet, ob ein AVV oder eine
Art.-26-Vereinbarung nötig ist. Beides fehlt bislang.

**Besteht ein Vertragsverhältnis zum Kunden?** Es gibt keinen Kauf, keine
Zahlung, keine Bestellung. Der Kunde erwirbt aber einen Prämienanspruch gegen
den Betrieb und erlischt ihn durch Einlösen. Davon hängt ab, ob Art. 6 Abs. 1
lit. b trägt oder auf lit. f auszuweichen ist.

---

*Die Einzelheiten mit Spaltennamen und Herleitung stehen in
[`verarbeitungsuebersicht.md`](verarbeitungsuebersicht.md).*
