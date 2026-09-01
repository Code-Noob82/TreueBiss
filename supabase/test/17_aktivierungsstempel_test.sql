-- ============================================================================
-- Aktivierungsstempel
--
-- Ein Stempel fuers Mitmachen, ohne Kauf. Das ist kein Bruch mit dem
-- Grundsatz dieses Produkts, sondern derselbe Fall wie der Tresen-Code: Er
-- belegt Anwesenheit, nicht Kauf - und steht deshalb unter demselben
-- Vorbehalt, einem Schalter des Betriebs.
--
-- Drei Dinge muessen stimmen, sonst ist er ein Rabatt ohne Gegenleistung:
-- genau einmal je Karte, nur bei eingeschaltetem Schalter, und in den Zahlen
-- getrennt von den Stempeln, die auf einen Kauf zurueckgehen.
-- ============================================================================
do $$
declare
    v_mit    uuid;   -- Betrieb mit Willkommensstempel
    v_ohne   uuid;   -- Betrieb ohne
    v_kunde  uuid;
    v_zweit  uuid;
    v_anzahl int;
    v_ok     boolean;
    v_erg    record;
begin
    insert into public.tenants (slug, name, welcome_stamp_enabled)
         values ('akt-mit', 'Mit Willkommen', true) returning id into v_mit;
    insert into public.tenants (slug, name, welcome_stamp_enabled)
         values ('akt-ohne', 'Ohne Willkommen', false) returning id into v_ohne;

    insert into auth.users default values returning id into v_kunde;
    insert into auth.users default values returning id into v_zweit;

    -- ------------------------------------------------------ ohne Anmeldung
    set local role anon;
    begin
        perform public.activate_card(v_mit);
        v_ok := false;
    exception when others then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ohne Anmeldung legt niemand eine Karte an');

    -- --------------------------------------------------- erster Aufruf
    call auth.become(v_kunde);
    set local role authenticated;
    select * into v_erg from public.activate_card(v_mit);
    call test.check(v_erg.welcome_stamp, 'Die neue Karte bekommt den Willkommensstempel');
    call test.check(v_erg.stamps = 1,    'Und steht damit bei einem Stempel');

    reset role;
    select count(*) into v_anzahl from public.memberships
     where user_id = v_kunde and tenant_id = v_mit;
    call test.check(v_anzahl = 1, 'Die Mitgliedschaft steht');
    select count(*) into v_anzahl from public.stamp_proofs
     where user_id = v_kunde and tenant_id = v_mit and source = 'aktivierung';
    call test.check(v_anzahl = 1, 'Der Nachweis ist als Aktivierung gekennzeichnet');

    -- ------------------------------------------- zweiter Aufruf, dieselbe Karte
    call auth.become(v_kunde);
    set local role authenticated;
    select * into v_erg from public.activate_card(v_mit);
    call test.check(not v_erg.welcome_stamp, 'Beim zweiten Oeffnen faellt kein zweiter');
    call test.check(v_erg.stamps = 1,        'Es bleibt bei einem Stempel');

    /*
     * Der Aufruf passiert bei jedem Start der App. Bliebe die Sperre nur in
     * einer if-Abfrage, faenden zwei gleichzeitige Starts - zwei Reiter, ein
     * Doppeltipp - beide "noch keiner" vor und legten je einen an. Die
     * Bedingung unique (tenant_id, proof_ref) verhindert das im Index.
     */
    reset role;
    select count(*) into v_anzahl from public.stamps
     where user_id = v_kunde and tenant_id = v_mit;
    call test.check(v_anzahl = 1, 'Auch in der Tabelle steht genau einer');

    -- ------------------------------------------------ Betrieb ohne Schalter
    call auth.become(v_zweit);
    set local role authenticated;
    select * into v_erg from public.activate_card(v_ohne);
    call test.check(not v_erg.welcome_stamp, 'Ohne Schalter gibt es keinen Stempel');
    call test.check(v_erg.stamps = 0,        'Die Karte startet bei null');

    reset role;
    select count(*) into v_anzahl from public.memberships
     where user_id = v_zweit and tenant_id = v_ohne;
    call test.check(v_anzahl = 1, 'Die Karte entsteht trotzdem');

    -- --------------------------------------------- eine zweite, eigene Karte
    call auth.become(v_zweit);
    set local role authenticated;
    select * into v_erg from public.activate_card(v_mit);
    call test.check(v_erg.welcome_stamp, 'Ein anderer Kunde bekommt seinen eigenen');

    -- --------------------------------------------------- abgeschalteter Betrieb
    reset role;
    update public.tenants set is_active = false where id = v_ohne;
    call auth.become(v_kunde);
    set local role authenticated;
    begin
        perform public.activate_card(v_ohne);
        v_ok := false;
    exception when others then
        v_ok := sqlerrm like '%not active%';
    end;
    call test.check(v_ok, 'Bei einem abgeschalteten Betrieb entsteht keine Karte');

    begin
        perform public.activate_card(gen_random_uuid());
        v_ok := false;
    exception when others then
        v_ok := sqlerrm like '%tenant not found%';
    end;
    call test.check(v_ok, 'Ein unbekannter Betrieb ist ein Fehler');

    -- ------------------------------------------------- getrennt in den Zahlen
    reset role;
    select welcome_stamps into v_anzahl from public.pilot_summary where tenant_id = v_mit;
    call test.check(v_anzahl = 2, 'Die Zahlen weisen beide Willkommensstempel aus');
    select stamps_issued into v_anzahl from public.pilot_summary where tenant_id = v_mit;
    call test.check(v_anzahl = 2, 'Und zaehlen sie in der Gesamtzahl mit');
    select welcome_stamps into v_anzahl from public.pilot_summary where tenant_id = v_ohne;
    call test.check(v_anzahl = 0, 'Beim Betrieb ohne Schalter steht null');

    /*
     * Die eigentliche Frage hinter der getrennten Spalte: Wie viele Stempel
     * gehen auf einen Kauf zurueck? Ohne sie waere die Antwort nicht zu
     * berechnen, und "Stempel pro Tag" stiege mit jedem neuen Kunden.
     */
    select stamps_issued - welcome_stamps into v_anzahl
      from public.pilot_summary where tenant_id = v_mit;
    call test.check(v_anzahl = 0, 'Auf einen Kauf geht bisher keiner zurueck');

    raise notice '--- Aktivierungsstempel bestanden ---';
end;
$$;
