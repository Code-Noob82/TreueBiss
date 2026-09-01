# Google Wallet einrichten

Der Code steht vollständig. Was fehlt, sind Zugangsdaten — die kann nur der
Anbieter beschaffen.

## Die vier Schritte, die wirklich nötig sind

Beim ersten Einrichten am 30.08.2026 hat jeder einzelne davon gefehlt, und
jeder äußerte sich anders. In dieser Reihenfolge abarbeiten:

| # | Schritt | Fehlt er, meldet Google |
|---|---|---|
| 1 | Issuer-Konto anlegen, Aussteller-ID notieren | — |
| 2 | **Wallet API im Cloud-Projekt aktivieren** | `403 accessNotConfigured` |
| 3 | **Dienstkonto in der Wallet Console unter *Users* einladen** (Developer) | `403 Permission denied` |
| 4 | Secrets setzen, Funktion ausrollen | `503 noch nicht eingerichtet` |

Schritt 2 wird leicht übersehen: Das Signieren des Save-JWT braucht die API
nicht — es passiert lokal. Google braucht sie aber, sobald Klasse oder Objekt
entstehen sollen.

Prüfen lässt sich alles auf einmal, ohne Gerät:

```bash
curl -X POST "$SUPABASE_URL/functions/v1/wallet-google" \
  -H "Authorization: Bearer <nutzer-jwt>" -H "Content-Type: application/json" \
  -d '{"tenant_id":"...","pruefen":true}'
```

Die Antwort enthält eine Spur mit jedem REST-Aufruf und Googles Fehlertext im
Klartext.

> **Und eine Falle, die nichts mit der Einrichtung zu tun hat:** Wird der
> Save-Link unvollständig in die Adresszeile eingefügt — abgeschnitten, mit
> Zeilenumbruch, aus einem Codeblock kopiert — meldet Google „Ein Problem ist
> aufgetreten". Dieselbe Meldung wie bei jedem echten Fehler. Deshalb gehört
> der Link hinter einen Knopf und nie in die Zwischenablage.

## 1. Issuer-Konto

In der [Google Pay & Wallet Console](https://pay.google.com/business/console)
mit einem Google-Konto anmelden und ein **Google Wallet API Issuer-Konto**
anlegen. Kostenlos.

**Neue Konten starten im Demo-Modus.** Pässe lassen sich erzeugen, aber nur an
Konten ausgeben, die als Admin, Developer oder Testkonto eingetragen sind.
Zum Ausprobieren reicht das; vor dem Pilotbetrieb ist die Freigabe zu
beantragen.
([Doku](https://developers.google.com/wallet/retail/loyalty-cards/getting-started/issuer-onboarding))

Die **Issuer-ID** notieren — eine lange Zahl.

## 2. Dienstkonto

Im Google-Cloud-Projekt ein Dienstkonto anlegen, einen Schlüssel als JSON
herunterladen und dem Dienstkonto in der Wallet Console Zugriff geben.
Aus der JSON-Datei werden zwei Werte gebraucht: `client_email` und
`private_key`.

## 3. Secrets setzen

```bash
supabase secrets set GOOGLE_WALLET_ISSUER_ID=3388000000012345678
supabase secrets set GOOGLE_WALLET_SA_EMAIL=dienst@projekt.iam.gserviceaccount.com
supabase secrets set GOOGLE_WALLET_SA_KEY="$(python3 -c "import json,sys; print(json.load(open('schluessel.json'))['private_key'])")"
supabase secrets set APP_BASIS_URL=https://byte-und-handwerk.github.io/TreueBiss/app/
```

Der Schlüssel steht in der JSON-Datei mit `\n` als **zwei Zeichen**. Das
Auslesen oben löst das auf; `signieren.ts` kommt aber mit beiden Formen klar,
weil dieser Fehler sonst mit einer nichtssagenden Meldung endet.

## 4. Ausrollen

```bash
supabase functions deploy wallet-google
```

## Prüfen

Ohne Zugangsdaten antwortet die Funktion mit **503** und im Klartext, dass
noch nichts eingerichtet ist — das ist ein Einrichtungsfehler und kein
Laufzeitfehler, deshalb kein 500.

Der Zusammenbau selbst läuft ohne Google:

```bash
node --experimental-strip-types supabase/functions/wallet-google/pass_test.ts
```

35 Prüfungen, darunter eine echte RS256-Signatur gegen einen im Test erzeugten
Schlüssel. Was sie **nicht** beantworten: ob Google den Pass annimmt. Das
zeigt erst der erste Klick mit echtem Issuer.

## Was im Pass steht

| Feld | Inhalt |
|---|---|
| Klasse | je Betrieb, mit dessen Name und Primärfarbe |
| `reviewStatus` | `underReview` — `draft` **kann keine Objekte erzeugen** |
| `loyaltyPoints` | der Stempelstand, etwa `3/10` |
| `barcode` | der Umzugslink mit dem Kartenschlüssel |

Der Strichcode trägt bewusst den Umzugslink und nicht bloß den Schlüssel: Bei
TreueBiss scannt der Kunde, nicht die Kasse. Nützlich ist der Code deshalb
genau dort, wo der Pass ohnehin hin soll — auf ein weiteres Gerät.

Die Objektkennung ist ein **gekürzter SHA-256 des Kartenschlüssels**, nicht der
Schlüssel selbst: Sie taucht in Google-Konten und Protokollen auf. Sie ist
stabil, damit ein zweiter Klick denselben Pass aktualisiert statt einen zweiten
anzulegen.

## Wie der Pass entsteht

Klasse und Objekt werden **serverseitig über die REST-API** angelegt, das JWT
verweist nur noch darauf:

```json
{"loyaltyObjects": [{"id": "<issuer>.<slug>-<hash>"}]}
```

Drei Gründe, statt beides ins JWT zu packen:

1. Google antwortet über die REST-API **im Klartext**. Beim Speichern sieht
   der Kunde nur „Ein Problem ist aufgetreten" — das ist zum Suchen unbrauchbar.
2. Das JWT schrumpft von rund 1600 auf **700 Zeichen**, weit unter der
   dokumentierten Grenze von 1800.
3. Das Objekt wird bei jedem Aufruf **aktualisiert**. Der Pass trägt damit den
   aktuellen Stempelstand, sobald der Kunde ihn neu holt.

Schlägt das Anlegen fehl, antwortet die Funktion mit **502 und der Spur** —
statt einen Link zu liefern, der beim Kunden ins Leere läuft.

## Noch nicht gebaut

Der Pass aktualisiert sich, wenn der Kunde ihn erneut anfordert — aber **nicht
von selbst**, wenn ein Stempel dazukommt. Dafür müsste `issue_stamp` das
Wallet-Objekt nachziehen. Bis dahin ist die Karte in der App die Wahrheit.
