-- ============================================================================
-- Verwaiste Karten
--
-- Eine Karte, deren Sitzung verloren ging - Browserdaten geloescht, Handy
-- gewechselt - erreicht niemand mehr. Sie zaehlt aber weiter als Teilnehmer.
-- Am 02.09.2026 beim Durchspielen genau so passiert: Karte auf dem Handy weg,
-- in der Verwaltung noch da.
--
-- Der Zeitplan raeumt sie weg. Entscheidend ist, was er NICHT anfasst: Eine
-- Karte mit auch nur einem echten Stempel bleibt, eine mit Gutschein bleibt,
-- und eine frische bleibt, bis die Frist des Betriebs um ist.
-- ============================================================================
do $$
declare
    v_b       uuid;
    v_alt     uuid;   -- verwaist, alt genug
    v_frisch  uuid;   -- verwaist, aber noch in der Frist
    v_echt    uuid;   -- hat einen Beleg
    v_gut     uuid;   -- hat einen Gutschein
    v_leer    uuid;   -- ganz ohne Stempel, alt
    v_n       bigint;
    v_anzahl  int;
begin
    insert into public.tenants (slug, name, welcome_stamp_enabled, orphan_card_days)
         values ('verwaist', 'Verwaistbetrieb', true, 30) returning id into v_b;
    for i in 1..5 loop
        insert into auth.users default values;
    end loop;
    select id into v_alt    from auth.users order by id limit 1 offset 0;
    select id into v_frisch from auth.users order by id limit 1 offset 1;
    select id into v_echt   from auth.users order by id limit 1 offset 2;
    select id into v_gut    from auth.users order by id limit 1 offset 3;
    select id into v_leer   from auth.users order by id limit 1 offset 4;

    -- Alle fuenf bekommen eine Karte mit Willkommensstempel.
    perform public.karte_anlegen_intern(v_alt,    v_b);
    perform public.karte_anlegen_intern(v_frisch, v_b);
    perform public.karte_anlegen_intern(v_echt,   v_b);
    perform public.karte_anlegen_intern(v_gut,    v_b);
    insert into public.memberships (user_id, tenant_id) values (v_leer, v_b);

    -- Drei davon sind alt.
    update public.memberships set joined_at = now() - interval '45 days'
     where tenant_id = v_b and user_id in (v_alt, v_echt, v_gut, v_leer);
    -- Einer hat einen echten Beleg, einer einen Gutschein.
    insert into public.stamp_proofs (tenant_id, user_id, proof_ref, source)
         values (v_b, v_echt, 'echt-1', 'receipt');
    insert into public.vouchers (id, creation_date, expires_at, is_redeemed, user_id, tenant_id)
         values (gen_random_uuid(), 1, 99999999999999, false, v_gut, v_b);

    select karten_entfernt into v_n from public.cleanup_orphan_cards();
    call test.check(v_n = 2, 'Zwei verwaiste Karten entfernt: die alte und die leere');

    select count(*) into v_anzahl from public.memberships where tenant_id = v_b and user_id = v_alt;
    call test.check(v_anzahl = 0, 'Die alte Karte mit nur Willkommensstempel ist weg');
    select count(*) into v_anzahl from public.stamps where tenant_id = v_b and user_id = v_alt;
    call test.check(v_anzahl = 0, 'Samt ihrem Stempel');
    select count(*) into v_anzahl from public.stamp_proofs where tenant_id = v_b and user_id = v_alt;
    call test.check(v_anzahl = 0, 'Und dem Nachweis der Aktivierung');
    select count(*) into v_anzahl from public.memberships where tenant_id = v_b and user_id = v_leer;
    call test.check(v_anzahl = 0, 'Die ganz leere alte Karte ist weg');

    select count(*) into v_anzahl from public.memberships where tenant_id = v_b and user_id = v_frisch;
    call test.check(v_anzahl = 1, 'Die frische bleibt - die Frist ist nicht um');
    select count(*) into v_anzahl from public.memberships where tenant_id = v_b and user_id = v_echt;
    call test.check(v_anzahl = 1, 'Die mit echtem Beleg bleibt');
    select count(*) into v_anzahl from public.memberships where tenant_id = v_b and user_id = v_gut;
    call test.check(v_anzahl = 1, 'Die mit Gutschein bleibt');

    -- Kein Eintrag im Loeschzaehler: Das war Hausputz, kein Kunde.
    select count(*) into v_anzahl from public.card_deletions where tenant_id = v_b;
    call test.check(v_anzahl = 0, 'Hausputz zaehlt nicht als Loeschung des Kunden');

    -- Ein zweiter Lauf findet nichts mehr.
    select karten_entfernt into v_n from public.cleanup_orphan_cards();
    call test.check(v_n = 0, 'Ein zweiter Lauf findet nichts');

    -- Die Frist gehoert dem Betrieb.
    update public.tenants set orphan_card_days = 1 where id = v_b;
    update public.memberships set joined_at = now() - interval '2 days'
     where tenant_id = v_b and user_id = v_frisch;
    select karten_entfernt into v_n from public.cleanup_orphan_cards();
    call test.check(v_n = 1, 'Mit kuerzerer Frist geht auch die frische');

    raise notice '--- Verwaiste Karten bestanden ---';
end;
$$;
