-- ============================================================================
-- Verlaufszahlen
--
-- Die drei Tagesansichten gab es seit Langem, lesen konnte sie niemand: fuer
-- anon und authenticated gesperrt, ohne staff_-Funktion davor. Der Betrieb sah
-- ausschliesslich Staende.
--
-- Drei Dinge muessen stimmen, sonst zeigt die Linie einen Verlauf, den es
-- nicht gab: Luecken sind Nullen, nicht ausgelassene Tage. Tage jenseits der
-- Aufbewahrung sind unbekannt, nicht null. Und sehen darf das nur, wer auch
-- die uebrigen Zahlen sieht.
-- ============================================================================
do $$
declare
    v_b      uuid;
    v_chef   uuid;
    v_kasse  uuid;
    v_demo   uuid;
    v_kunde  uuid;
    v_anzahl int;
    v_wert   int;
begin
    -- Die Detailfrist muss in die Aufbewahrung passen, das prueft eine
    -- Bedingung an der Tabelle. Zehn Tage Aufbewahrung heisst also auch eine
    -- kuerzere Detailfrist.
    insert into public.tenants (slug, name, proof_detail_days, proof_retention_days)
         values ('verlauf', 'Verlaufsbetrieb', 5, 10) returning id into v_b;
    insert into auth.users default values returning id into v_chef;
    insert into auth.users default values returning id into v_kasse;
    insert into auth.users default values returning id into v_demo;
    insert into auth.users default values returning id into v_kunde;
    insert into public.tenant_staff (user_id, tenant_id, role) values
        (v_chef, v_b, 'owner'), (v_kasse, v_b, 'staff'), (v_demo, v_b, 'demo');

    insert into public.memberships (user_id, tenant_id) values (v_kunde, v_b);
    -- Zwei Stempel heute, einer vorgestern, keiner gestern.
    insert into public.stamp_proofs (tenant_id, user_id, proof_ref, source, created_at)
         values (v_b, v_kunde, 'v-1', 'receipt', now()),
                (v_b, v_kunde, 'v-2', 'receipt', now()),
                (v_b, v_kunde, 'v-3', 'receipt', now() - interval '2 days'),
                -- und einer jenseits der Aufbewahrung von zehn Tagen
                (v_b, v_kunde, 'v-4', 'receipt', now() - interval '20 days');

    -- --------------------------------------------------- Wer darf das sehen?
    call auth.become(v_kasse);
    set local role authenticated;
    select count(*) into v_anzahl from public.staff_pilot_daily(7);
    call test.check(v_anzahl = 0, 'Die Kasse sieht keinen Verlauf');
    reset role;

    call auth.become(v_kunde);
    set local role authenticated;
    select count(*) into v_anzahl from public.staff_pilot_daily(7);
    call test.check(v_anzahl = 0, 'Ein Kunde auch nicht');
    reset role;

    call auth.become(v_demo);
    set local role authenticated;
    select count(*) into v_anzahl from public.staff_pilot_daily(7);
    call test.check(v_anzahl = 7, 'Der Demozugang sieht ihn');
    reset role;

    -- ------------------------------------------------------- Die Zahlen
    call auth.become(v_chef);
    set local role authenticated;

    select count(*) into v_anzahl from public.staff_pilot_daily(7);
    call test.check(v_anzahl = 7, 'Sieben angefragte Tage ergeben sieben Zeilen');

    /*
     * Der eigentliche Punkt: Ein Tag ohne Eintrag muss dastehen, mit null.
     * Wer die Luecken auslaesst, zeichnet eine Linie, die es nicht gab.
     */
    select stempel into v_wert from public.staff_pilot_daily(7)
     where tag = current_date;
    call test.check(v_wert = 2, 'Heute zwei Stempel');
    select stempel into v_wert from public.staff_pilot_daily(7)
     where tag = current_date - 1;
    call test.check(v_wert = 0, 'Gestern null - und der Tag steht trotzdem da');
    select stempel into v_wert from public.staff_pilot_daily(7)
     where tag = current_date - 2;
    call test.check(v_wert = 1, 'Vorgestern einer');

    select neue_karten into v_wert from public.staff_pilot_daily(7)
     where tag = current_date;
    call test.check(v_wert = 1, 'Und eine neue Karte heute');

    /*
     * Jenseits der Aufbewahrung: unbekannt, nicht null. Eine Null hiesse "an
     * dem Tag war nichts" - und das weiss hier niemand mehr.
     */
    select stempel into v_wert from public.staff_pilot_daily(30)
     where tag = current_date - 20;
    call test.check(v_wert is null, 'Jenseits der Aufbewahrung: unbekannt statt null');
    select count(*) into v_anzahl from public.staff_pilot_daily(30)
     where stempel is null;
    call test.check(v_anzahl = 19, 'Genau die Tage vor der Frist sind unbekannt');

    -- Neue Karten und Einloesungen bleiben dauerhaft - dort gibt es kein
    -- Unbekannt, nur Nullen.
    select count(*) into v_anzahl from public.staff_pilot_daily(30)
     where neue_karten is null or einloesungen is null;
    call test.check(v_anzahl = 0, 'Karten und Einloesungen reichen weiter zurueck');

    -- ------------------------------------------------------------ Grenzen
    select count(*) into v_anzahl from public.staff_pilot_daily(0);
    call test.check(v_anzahl = 1, 'Null Tage werden auf einen gehoben');
    select count(*) into v_anzahl from public.staff_pilot_daily(9999);
    call test.check(v_anzahl = 365, 'Und mehr als ein Jahr gibt es nicht');
    select count(*) into v_anzahl from public.staff_pilot_daily(null);
    call test.check(v_anzahl = 30, 'Ohne Angabe sind es dreissig');

    reset role;
    raise notice '--- Verlaufszahlen bestanden ---';
end;
$$;
