// Kopiere diese Datei nach `config.js` und trage die Werte deines
// Supabase-Projekts ein (Project Settings -> API).
//
// `config.js` ist bewusst nicht eingecheckt: Die Datei gehoert zur
// Installation, nicht zum Quelltext.
export const SUPABASE_URL = 'https://DEIN-PROJEKT.supabase.co';
export const SUPABASE_ANON_KEY = 'DEIN_ANON_KEY';

// Rechtliche Seiten, zentral von byte & Handwerk betrieben. Leer lassen,
// solange die Seite nicht existiert - der Eintrag erscheint dann gar nicht.
//
// ACHTUNG: Dieselben Adressen stehen fuer die Android-App in
// `app/src/main/res/values/legal.xml`. Beide Stellen muessen zusammenpassen;
// eine gemeinsame Quelle gibt es nicht, weil Android kein JavaScript liest.
export const IMPRESSUM_URL = '';
export const DATENSCHUTZ_URL = '';
export const APP_DATENSCHUTZ_URL = '';
export const AGB_URL = '';
