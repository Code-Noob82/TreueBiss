# Rechtstexte

Werkstatt für die Rechtsseiten von TreueBiss.

**Seit dem 03.09.2026 liegen die Seiten selbst unter `treuebiss.de`** — als
`web/impressum/`, `web/datenschutz/`, `web/datenschutz-app/` und `web/agb/`.
Bis dahin sollten sie nach `byteundhandwerk.de`, weil TreueBiss dort nur ein
Unterpunkt war. Mit der eigenen Domain wäre das falsch: Wer auf treuebiss.de
landet, muss das Impressum dieser Seite finden, nicht das einer anderen.

Anbieter bleibt byte & Handwerk, Inhaber Dominik Baki. Die Betriebe sind
Kunden, nicht Anbieter — die Seiten laufen zentral und nicht je Betrieb.

Was hier liegt, ist Zuarbeit und geht nicht online: Der Pages-Workflow
veröffentlicht ausschließlich `web/`.

## Was hier liegt

| Datei | Zweck |
|---|---|
| `_vorlage.html` | Alte Hülle im Stil von byteundhandwerk.de. Für treuebiss.de nicht mehr benutzen — dort steht die eigene Hülle in `web/<pfad>/index.html`. |
| `datenschutz-app.html` | Dieselbe alte Hülle, angepasst. Überholt. |
| `verarbeitungsuebersicht.md` | Was die App wirklich verarbeitet — Zuarbeit für den ITRK-Konfigurator. Kein Rechtstext. |
| `konfigurator-luecken.md` | Was der Konfigurator **nicht** abfragt und deshalb gesondert an die Kanzlei muss. |
| `anschreiben-itrk.md` | Fertiges Anschreiben an die Kanzlei: falsche Passagen wörtlich zitiert, Fehlendes ergänzt. |

## Eine Seite scharf schalten

Die vier Hüllen unter `web/` stehen bereits, mit leerem
`data-itrk-legaltext-url` und sichtbarem Platzhalter. Scharf wird eine Seite
in zwei Schritten:

1. `data-itrk-legaltext-url` auf die Adresse aus dem ITRK-Mandantenportal
   setzen
2. den Platzhalter-Block darüber löschen — er ist als solcher gekennzeichnet

Erst danach gehört die Seite in die Fußzeile der Produktseite und in
`config.js`.

**Die ITRK-URL ist je Rechtstext eine andere.** Wird die der
Website-Datenschutzerklärung wiederverwendet, steht auf der Seite der falsche
Text, und zwar unauffällig.

## Der Stand der vier Verweise in der App

Die App zeigt einen Eintrag nur, wenn die zugehörige URL gefüllt ist. Eine
leere URL heißt: kein Link, kein toter Verweis.

| Eintrag in der App | Ziel | Stand |
|---|---|---|
| Impressum | `treuebiss.de/impressum/` | Hülle steht, Text fehlt |
| Datenschutz | `treuebiss.de/datenschutz/` | Hülle steht, Text fehlt |
| Datenschutz in der App | `treuebiss.de/datenschutz-app/` | Hülle steht, Text fehlt |
| AGB | `treuebiss.de/agb/` | Hülle steht, Text fehlt |

Die vier Adressen auf byteundhandwerk.de bleiben bestehen und gelten für den
Anbieter selbst. Für TreueBiss zählen ab jetzt die eigenen.

Gepflegt an zwei Stellen, die zusammenpassen müssen:
`web/app/config.js` und `app/src/main/res/values/legal.xml`.

### Die AGB-Frage ist beantwortet

Offen war, ob die AGB den Betrieb einer Stempelkarten-App gegenüber
**Verbrauchern** abdecken oder die Leistungen von byte & Handwerk gegenüber
**Geschäftskunden**. Am 03.09.2026 hat Dominik dafür das Paket
**Software-as-a-Service DE** der IT-Recht Kanzlei gebucht: Vertragspartner
sind die Betriebe, also Geschäftskunden. Die Endkunden der Betriebe schließen
mit niemandem einen Vertrag — sie legen eine anonyme Karte an.

Im Mandantenportal muss dafür `treuebiss.de` als Domain hinterlegt sein, sonst
tragen die Texte die falsche Adresse.

### Was für Betriebe zusätzlich fehlt

Nicht in der App verlinkt, aber im Verkaufsgespräch verlangt: **AVV und TOM**.
Treuli liefert beides mit und schreibt es auf die Preisseite. Solange es das
nicht gibt, kann kein Betrieb TreueBiss datenschutzkonform einsetzen — er wäre
Verantwortlicher ohne Auftragsverarbeitungsvertrag.

**Das ist kein Detail mehr, sondern das Tor zum ersten Praxis-Zeitraum.** Ein
Betrieb, der die Karte einsetzt, entscheidet über Zwecke und Mittel und ist
damit Verantwortlicher nach Art. 4 Nr. 7 DSGVO; byte & Handwerk verarbeitet in
seinem Auftrag. Ohne Vertrag nach Art. 28 Abs. 3 DSGVO fehlt beiden Seiten die
Grundlage. Beim gebuchten SaaS-Paket ist deshalb als Erstes zu prüfen, ob AVV
und TOM enthalten sind — und falls nicht, gehören sie nachbestellt, bevor der
erste Brief rausgeht.

## Vor dem Livegang

- [ ] `treuebiss.de` im ITRK-Mandantenportal als Domain hinterlegt
- [ ] ITRK-Adresse je Seite eingesetzt, Platzhalter-Block gelöscht
- [ ] Seite unter der kanonischen Adresse erreichbar
- [ ] Verweis in der Fußzeile der Produktseite ergänzt
- [ ] URL in `web/app/config.js` **und** `app/src/main/res/values/legal.xml`
      eingetragen
- [ ] `verarbeitungsuebersicht.md` gegen den dann aktuellen Code geprüft
- [ ] AVV und TOM geklärt — ohne sie kein Praxis-Zeitraum

**Impressum und Datenschutz sind die Sperre für den Livegang der Domain.**
Eine erreichbare Seite ohne Anbieterkennzeichnung ist ein Verstoß gegen § 5
DDG, und zwar ab dem ersten Aufruf. Die übrigen beiden dürfen kurz nachlaufen,
solange nichts auf sie verlinkt.
