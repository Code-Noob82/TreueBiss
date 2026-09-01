// Kopiere diese Datei nach `config.js` und trage die Werte deines
// Supabase-Projekts ein (Project Settings -> API).
//
// `config.js` *ist* eingecheckt, anders als es hier frueher stand: Seit die
// Seiten ueber GitHub Pages ausgeliefert werden, laedt der Workflow schlicht
// `web/` hoch - ohne die Datei haette die ausgelieferte App keine Adresse.
// Geheim ist daran nichts; der Anon-Key steckt ohnehin in jedem Browser und
// in jeder APK, die Sicherheit haengt an den RLS-Policies. Begruendung in
// `.gitignore`, wo die Regel bewusst auskommentiert steht.
//
// Diese Beispieldatei bleibt, damit erkennbar ist, welche Werte gebraucht
// werden. Wer eine eigene Umgebung fahren will, ersetzt `config.js` im Build.
export const SUPABASE_URL = 'https://DEIN-PROJEKT.supabase.co';
export const SUPABASE_ANON_KEY = 'DEIN_ANON_KEY';
