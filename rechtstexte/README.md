# Rechtstexte

Hüllen für die Rechtsseiten von TreueBiss. Sie gehören **nicht** in dieses
Projekt, sondern nach `byteundhandwerk.de` — Anbieter der App ist byte &
Handwerk, TreueBiss ist das Produkt, die Betriebe sind Kunden. Deshalb laufen
die Seiten zentral und nicht je Betrieb.

Der Pages-Workflow veröffentlicht ausschließlich `web/`. Was hier liegt, geht
also nicht versehentlich unter der TreueBiss-Adresse online.

## Was hier liegt

| Datei | Zweck |
|---|---|
| `_vorlage.html` | Die Hülle. Kopieren, drei Stellen anpassen, fertig. |
| `datenschutz-app.html` | Fertig angepasst für `/datenschutz-app`. |
| `verarbeitungsuebersicht.md` | Was die App wirklich verarbeitet — Zuarbeit für den ITRK-Konfigurator. Kein Rechtstext. |

## Eine neue Seite anlegen

`_vorlage.html` kopieren und drei Stellen ändern:

1. `<title>` und `<meta name="description">`
2. `<link rel="canonical">` auf den echten Pfad
3. `data-itrk-legaltext-url` auf die URL aus dem ITRK-Mandantenportal

Danach den Platzhalter-Block löschen, der oben in `<main>` steht — er ist als
solcher gekennzeichnet.

**Die ITRK-URL ist je Rechtstext eine andere.** Wird die der
Website-Datenschutzerklärung wiederverwendet, steht auf der Seite der falsche
Text, und zwar unauffällig.

## Der Stand der vier Verweise in der App

Die App zeigt einen Eintrag nur, wenn die zugehörige URL gefüllt ist. Eine
leere URL heißt: kein Link, kein toter Verweis.

| Eintrag in der App | Ziel | Stand |
|---|---|---|
| Impressum | `byteundhandwerk.de/impressum` | **gesetzt** — Seite existiert |
| Datenschutz | `byteundhandwerk.de/datenschutz` | **gesetzt** — Seite existiert |
| Datenschutz in der App | `byteundhandwerk.de/datenschutz-app` | leer, bis der Text steht |
| AGB | `byteundhandwerk.de/agb` | leer — siehe unten |

Gepflegt an zwei Stellen, die zusammenpassen müssen:
`web/app/config.js` und `app/src/main/res/values/legal.xml`.

### Warum AGB leer bleibt

Die Seite `byteundhandwerk.de/agb` existiert, aber es ist ungeprüft, ob sie den
Betrieb einer Stempelkarten-App gegenüber **Verbrauchern** abdeckt oder die
Leistungen von byte & Handwerk gegenüber **Geschäftskunden**. Das sind zwei
verschiedene Verträge mit zwei verschiedenen Parteien. Bis das geklärt ist,
ist kein Link besser als der falsche.

### Was für Betriebe zusätzlich fehlt

Nicht in der App verlinkt, aber im Verkaufsgespräch verlangt: **AVV und TOM**.
Treuli liefert beides mit und schreibt es auf die Preisseite. Solange es das
nicht gibt, kann kein Betrieb TreueBiss datenschutzkonform einsetzen — er wäre
Verantwortlicher ohne Auftragsverarbeitungsvertrag.

## Vor dem Livegang

- [ ] ITRK-URL eingesetzt, Platzhalter-Block gelöscht
- [ ] Seite unter der kanonischen Adresse erreichbar
- [ ] Verweis in der Fußzeile der **übrigen** Seiten ergänzt — die Hüllen hier
      führen `Datenschutz in der App` bereits, die bestehenden Seiten nicht
- [ ] URL in `config.js` **und** `legal.xml` eingetragen
- [ ] `verarbeitungsuebersicht.md` gegen den dann aktuellen Code geprüft
