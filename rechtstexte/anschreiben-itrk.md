# Anschreiben an die IT-Recht Kanzlei

*Vorlage zum Weiterleiten. Anrede und Mandantennummer ergänzen, dann als
E-Mail oder über das Support-Formular senden.*

---

Sehr geehrte Damen und Herren,

über den Konfigurator habe ich eine Datenschutzerklärung für meine App
erstellen lassen. Der erzeugte Text enthält Passagen, die auf meine App nicht
zutreffen, und es fehlen wesentliche Verarbeitungen, nach denen der
Konfigurator nicht fragt. Ein Freitextfeld gab es nicht, sodass ich das dort
nicht ergänzen konnte.

Ich schildere Ihnen nachstehend, was die App tatsächlich tut, welche Passagen
zu streichen sind und was aufzunehmen wäre.

## Was die App ist

**TreueBiss** ist eine digitale Stempelkarte für Bäckereien, Metzgereien und
ähnliche Betriebe. Anbieter bin ich, byte & Handwerk; die Betriebe sind meine
Kunden, deren Endkunden nutzen die Karte.

Technisch ist es eine **Web-App (Progressive Web App)**, die über einen Browser
aufgerufen wird. Es gibt **keinen Vertrieb über einen App-Store**, keinen
Download und keine Installation im herkömmlichen Sinn. Daneben existiert eine
Android-App, die bislang nicht veröffentlicht ist.

Der Endkunde **legt kein Konto an**. Die Anmeldung erfolgt anonym; es entsteht
eine zufällige Kennung ohne Namen, ohne E-Mail-Adresse und ohne Passwort.

## 1. Passagen, die nicht zutreffen

### 1.1 Geräte- und Mobilfunkkennungen (Abschnitt 2, letzter Absatz)

> „Weiterhin benötigen wir Ihre eindeutige Nummer des Endgeräts (IMEI =
> International Mobile Equipment Identity), eindeutige Nummer des
> Netzteilnehmers (IMSI = International Mobile Subscriber Identity),
> Mobilfunknummer (MSISDN), evtl. MAC-Adresse für die WLAN-Nutzung und den
> Namen Ihres mobilen Endgerätes."

**Keine dieser Angaben wird erhoben.** Eine Web-App hat auf IMEI, IMSI,
MSISDN und MAC-Adresse technisch keinen Zugriff. Die Android-App fordert als
einzige Berechtigung `android.permission.INTERNET` an; insbesondere fordert
sie nicht `AD_ID` an.

Diese Passage bitte ersatzlos streichen.

### 1.2 Vertrieb über einen App-Store (Abschnitt 2, erster Absatz)

> „Wenn Sie unsere mobile App über einen App-Store herunterladen, werden die
> erforderlichen Informationen an den App Store übertragen, also insbesondere
> Nutzername, E-Mail-Adresse und Kundennummer Ihres Accounts, Zeitpunkt des
> Downloads, Zahlungsinformationen und die individuelle Gerätekennziffer."

Es findet **kein Vertrieb über einen App-Store** statt. Die App wird über eine
Webadresse aufgerufen. Es gibt keinen Download, kein Nutzerkonto beim
Anbieter und keine Zahlungsinformationen.

Diese Passage bitte ersatzlos streichen.

### 1.3 Kontaktformular (Abschnitt 4)

> „Welche Daten im Falle der Nutzung eines Kontaktformulars erhoben werden,
> ist aus dem jeweiligen Kontaktformular in der App ersichtlich."

Die App enthält **kein Kontaktformular** und keine Kontaktfunktion.

### 1.4 Auftragsverarbeitungsverträge mit Cloudflare und Fastly (Abschnitte 3.2, 3.3)

> „Wir haben mit dem Anbieter einen Auftragsverarbeitungsvertrag geschlossen…"

Ich habe mit **Cloudflare und Fastly keine direkte Vertragsbeziehung**. Beide
sind Unterauftragnehmer meiner Dienstleister: Cloudflare steht vor der
Programmierschnittstelle von Supabase, Fastly vor GitHub Pages. Bitte prüfen
Sie, wie das korrekt darzustellen ist.

Ebenso bitte ich um Prüfung der Aussage zu Supabase in Abschnitt 3.1; einen
Auftragsverarbeitungsvertrag werde ich abschließen, er liegt derzeit noch
nicht vor.

## 2. Was fehlt

Der Konfigurator fragt nicht nach dem Umfang der Verarbeitung. Die folgenden
Punkte sind daher im erzeugten Text nicht enthalten.

### 2.1 Die eigentliche Verarbeitung

| Was | Wann |
|---|---|
| zufällige Nutzerkennung (anonyme Anmeldung) | bei der ersten Nutzung |
| Zugehörigkeit zu einem Betrieb | beim Öffnen der Karte |
| Stempel mit Zeitpunkt | bei jedem Stempel |
| Gutscheine: Ausstellung, Ablauf, Einlösung | bei voller Karte |
| eingelöste Coupons | beim Einlösen |
| Kaufnachweise | bei jedem Stempel |

### 2.2 Kaufdaten — der wichtigste Punkt

Sammelt der Kunde über den QR-Code auf dem Kassenbon, werden gespeichert:

- **Bruttobetrag des Einkaufs**
- Seriennummer der Kasse
- Vorgangsnummer
- Zeitpunkt

Über die pseudonyme Nutzerkennung entsteht damit eine Historie von
Einkaufsbeträgen und -zeitpunkten bei dem jeweiligen Betrieb. Der QR-Code wird
nicht vollständig gespeichert; einzelne Artikel sind darin ohnehin nicht
enthalten.

### 2.3 Löschfristen

Zwei Stufen, je Betrieb einstellbar. Die Vorgaben:

- nach **30 Tagen** werden Betrag und Kassennummer gelöscht, der Nachweis
  bleibt bestehen
- nach **90 Tagen** wird der Nachweis gelöscht

Durchgesetzt durch einen automatischen täglichen Lauf. Eine gesetzliche
Aufbewahrungspflicht besteht für diese Daten nach meiner Einschätzung nicht:
§ 147 AO trifft den Steuerpflichtigen und dessen eigene Buchungsbelege, also
den Betrieb mit seiner Kasse, nicht meine Anwendung mit ihrem Verweis darauf.

### 2.4 Speicherung auf dem Endgerät

Es werden **keine Cookies** gesetzt. Die App speichert drei Einträge im
`localStorage` des Browsers:

- das Token der anonymen Anmeldung
- den zuletzt bekannten Kartenstand zur Anzeige ohne Verbindung
- das Kürzel des zuletzt geöffneten Betriebs

Ein Service Worker legt zusätzlich Programmdateien, Schriften und Bilder im
Browsercache ab; Nutzerdaten sind davon nicht betroffen. Nach meiner
Einschätzung sind alle Einträge für die Erbringung des Dienstes erforderlich
im Sinne von § 25 Abs. 2 Nr. 2 TDDDG. Eine Verwendung für Werbung,
Reichweitenmessung oder websiteübergreifende Wiedererkennung findet nicht
statt.

### 2.5 Kamerazugriff

Die App liest QR-Codes über die Kamera. **Es werden keine Aufnahmen erstellt,
gespeichert oder übertragen.** Der Kamerastrom wird ausschließlich im Browser
ausgewertet; übermittelt wird allein die im Code enthaltene Zeichenkette.

### 2.6 Weitere Empfänger

Im erzeugten Text sind Supabase, Cloudflare und Fastly genannt. Es fehlen:

| Dienst | Rolle |
|---|---|
| **GitHub Pages** (GitHub Inc., USA) | liefert die Web-App aus |
| **esm.sh** | Content-Delivery-Network für drei Programmbibliotheken |
| **Google** | speichert den Wallet-Pass, siehe 2.7 |

### 2.7 Wallet-Pässe

Der Kunde kann seine Karte freiwillig in Apple Wallet oder Google Wallet
ablegen. Ohne diese Handlung fließt dorthin nichts.

Bei **Google** wird ein Objekt auf Googles Servern gespeichert: der
Stempelstand sowie die Adresse der Karte. In dieser Adresse ist der
Kartenschlüssel enthalten. Für mich bleibt der Kunde anonym; bei Google
entsteht ein Personenbezug über das Konto, mit dem gespeichert wird.

Bei **Apple** erhält lediglich das Gerät des Kunden eine Datei. An mich oder
an Apple werden von mir keine Daten übermittelt.

### 2.8 Kartenschlüssel

Zu jeder Karte gehören 32 zufällige Bytes, die sie auf ein anderes Gerät
übertragbar machen. Sie enthalten keine Personendaten, wirken aber wie ein
Inhaberpapier: Wer sie hat, hat die Karte. Sie sind im QR-Code der App und in
beiden Wallet-Pässen enthalten.

## 3. Zwei Fragen, um deren Beurteilung ich bitte

### 3.1 Wer ist Verantwortlicher?

Abschnitt 1.2 des erzeugten Textes nennt mich als alleinigen Verantwortlichen.
Ich bin unsicher, ob das zutrifft:

| Entscheidung | trifft |
|---|---|
| Zweck „Kundenbindung", Kartenregeln, Prämien, Angebote, Einlösecode | **der Betrieb**, über ein Verwaltungsdashboard |
| anonyme Anmeldung, Umfang der Daten, Löschfristen, Sicherheit | **byte & Handwerk** |
| Einsicht in die Zahlen der eigenen Kunden | der Betrieb |

Das erscheint mir weder als reine Auftragsverarbeitung noch als alleinige
Verantwortlichkeit. Sollte eine gemeinsame Verantwortlichkeit nach Art. 26
DSGVO vorliegen, wäre Abschnitt 1.2 anzupassen, und ich benötigte statt eines
Auftragsverarbeitungsvertrags eine Vereinbarung nach Art. 26.

### 3.2 Besteht ein Vertragsverhältnis zum Endkunden?

Über die App werden keine Waren verkauft, es gibt keine Bestell- und keine
Zahlungsfunktion. Der Kunde erwirbt mit einer vollen Karte jedoch einen
Prämienanspruch, der sich gegen den teilnehmenden Betrieb richtet, und löst
ihn durch eine Handlung in der App ein.

Davon hängt ab, ob sich die Verarbeitung von Karte, Stempeln und Gutscheinen
auf Art. 6 Abs. 1 lit. b DSGVO stützen lässt oder auf lit. f auszuweichen ist.

## 4. Weiterer Bedarf

Für den Vertrieb an Betriebe benötige ich zusätzlich einen
**Auftragsverarbeitungsvertrag** beziehungsweise eine **Art.-26-Vereinbarung**
sowie eine Darstellung der **technischen und organisatorischen Maßnahmen**.
Ich bin für einen Hinweis dankbar, ob Sie hierfür Muster bereitstellen.

Mit freundlichen Grüßen
