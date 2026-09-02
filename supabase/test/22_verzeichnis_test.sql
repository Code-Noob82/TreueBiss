-- ============================================================================
-- Das oeffentliche Betriebsverzeichnis
--
-- TreueBiss ist der Einstiegspunkt fuer den Kunden. Wer die Adresse ohne
-- Betrieb oeffnet, muss den seinen finden koennen - bis zum 02.09.2026 ging
-- das nicht, weil tenants nur fuer Angemeldete lesbar ist und der leere
-- Einstieg sich mit Absicht nicht anmeldet.
--
-- Die Sicht loest das, indem sie die Policy umgeht. Genau deshalb muss
-- geprueft werden, was sie *nicht* herausgibt: Ein abgeschalteter Betrieb
-- darf nicht darin stehen, und mehr als Name und Kurzname darf sie nicht
-- kennen.
-- ============================================================================
do $$
declare
    v_an   uuid;
    v_aus  uuid;
    v_n    int;
    v_txt  text;
begin
    insert into public.tenants (slug, name, primary_color, stamps_per_card)
         values ('verzeichnis-an', 'Teilnehmender Betrieb', '#123456', 7)
    returning id into v_an;
    insert into public.tenants (slug, name, is_active)
         values ('verzeichnis-aus', 'Abgeschalteter Betrieb', false)
    returning id into v_aus;

    -- ------------------------------------------------ Ohne Anmeldung lesbar
    set local role anon;
    select count(*) into v_n from public.betriebe_oeffentlich
     where slug = 'verzeichnis-an';
    call test.check(v_n = 1, 'Ohne Anmeldung steht der Betrieb im Verzeichnis');

    select count(*) into v_n from public.betriebe_oeffentlich
     where slug = 'verzeichnis-aus';
    call test.check(v_n = 0, 'Ein abgeschalteter Betrieb steht nicht darin');
    reset role;

    -- Und die Tabelle dahinter bleibt zu, wie vorher.
    set local role anon;
    select count(*) into v_n from public.tenants;
    call test.check(v_n = 0, 'Die Tabelle selbst bleibt fuer anon leer');
    reset role;

    /*
     * Die Sicht darf nicht mehr wissen als noetig. Farbe, Kartengroesse und
     * Einstellungen gehen den Kunden nichts an, solange er die Karte nicht
     * hat - und stuenden hier fuer jeden im Netz.
     */
    select string_agg(column_name, ', ' order by ordinal_position) into v_txt
      from information_schema.columns
     where table_schema = 'public' and table_name = 'betriebe_oeffentlich';
    call test.check(v_txt = 'slug, name',
                    coalesce('Genau zwei Spalten, mehr nicht: ' || v_txt, 'keine Sicht'));

    raise notice '--- Betriebsverzeichnis bestanden ---';
end;
$$;
