-- ============================================================================
-- Verwaiste Karten
--
-- Eine Karte, deren Sitzung verloren ging - Browserdaten geloescht, Handy
-- gewechselt - erreicht niemand mehr. Sie zaehlt aber weiter als Teilnehmer.
-- Am 02.09.2026 beim Durchspielen genau so passiert.
--
-- Verwaist heisst seit dem 02.09.2026: seit der Frist keine Bewegung. Nicht
-- "kein Nachweis" - diese erste Fassung haette einem Stammkunden die Punkte
-- geloescht, sobald seine Nachweise die Aufbewahrungsfrist ueberschritten.
-- Der zweite Block unten haelt genau das fest.
--
-- Entscheidend ist, was der Hausputz NICHT anfasst.
-- ============================================================================
do $$
declare
    v_b         uuid;
    v_alt       uuid;   -- letzter Stempel lange her
    v_frisch    uuid;   -- war gerade da
    v_leer      uuid;   -- nie ein Stempel
    v_gut       uuid;   -- alter Stempel, aber gueltiger Gutschein
    v_verfallen uuid;   -- alter Stempel, Gutschein abgelaufen
    v_n         bigint;
    v_anzahl    int;
begin
    insert into public.tenants (slug, name, orphan_card_days)
         values ('verwaist', 'Verwaistbetrieb', 30) returning id into v_b;
    for i in 1..5 loop
        insert into auth.users default values;
    end loop;
    select id into v_alt       from auth.users order by id limit 1 offset 0;
    select id into v_frisch    from auth.users order by id limit 1 offset 1;
    select id into v_leer      from auth.users order by id limit 1 offset 2;
    select id into v_gut       from auth.users order by id limit 1 offset 3;
    select id into v_verfallen from auth.users order by id limit 1 offset 4;

    insert into public.memberships (user_id, tenant_id, joined_at) values
        (v_alt,       v_b, now() - interval '60 days'),
        (v_frisch,    v_b, now() - interval '60 days'),
        (v_leer,      v_b, now() - interval '60 days'),
        (v_gut,       v_b, now() - interval '60 days'),
        (v_verfallen, v_b, now() - interval '60 days');

    -- Bewegung: wann lag der letzte Stempel?
    insert into public.stamps (id, user_id, tenant_id, created_at) values
        (gen_random_uuid(), v_alt,       v_b, now() - interval '45 days'),
        (gen_random_uuid(), v_frisch,    v_b, now() - interval '5 days'),
        (gen_random_uuid(), v_gut,       v_b, now() - interval '45 days'),
        (gen_random_uuid(), v_verfallen, v_b, now() - interval '45 days');

    -- Ein gueltiger und ein abgelaufener Gutschein.
    insert into public.vouchers (id, creation_date, expires_at, is_redeemed, user_id, tenant_id)
         values (gen_random_uuid(), 1,
                 (extract(epoch from now() + interval '30 days') * 1000)::int8,
                 false, v_gut, v_b),
                (gen_random_uuid(), 1,
                 (extract(epoch from now() - interval '10 days') * 1000)::int8,
                 false, v_verfallen, v_b);

    select karten_entfernt into v_n from public.cleanup_orphan_cards();
    call test.check(v_n = 3, 'Drei Karten ohne Bewegung entfernt');

    select count(*) into v_anzahl from public.memberships where tenant_id = v_b and user_id = v_alt;
    call test.check(v_anzahl = 0, 'Die Karte ohne Bewegung ist weg');
    select count(*) into v_anzahl from public.stamps where tenant_id = v_b and user_id = v_alt;
    call test.check(v_anzahl = 0, 'Samt ihrem Stempel');

    select count(*) into v_anzahl from public.memberships where tenant_id = v_b and user_id = v_leer;
    call test.check(v_anzahl = 0, 'Die nie benutzte Karte ist weg');

    select count(*) into v_anzahl from public.memberships where tenant_id = v_b and user_id = v_verfallen;
    call test.check(v_anzahl = 0, 'Ein abgelaufener Gutschein haelt nichts fest');

    select count(*) into v_anzahl from public.memberships where tenant_id = v_b and user_id = v_frisch;
    call test.check(v_anzahl = 1, 'Wer neulich da war, behaelt seine Karte');

    select count(*) into v_anzahl from public.memberships where tenant_id = v_b and user_id = v_gut;
    call test.check(v_anzahl = 1, 'Ein gueltiger Gutschein haelt die Karte fest');

    -- Kein Eintrag im Loeschzaehler: Das war Hausputz, kein Kunde.
    select count(*) into v_anzahl from public.card_deletions where tenant_id = v_b;
    call test.check(v_anzahl = 0, 'Hausputz zaehlt nicht als Loeschung des Kunden');

    -- Ein zweiter Lauf findet nichts mehr.
    select karten_entfernt into v_n from public.cleanup_orphan_cards();
    call test.check(v_n = 0, 'Ein zweiter Lauf findet nichts');

    -- Die Frist gehoert dem Betrieb.
    update public.tenants set orphan_card_days = 1 where id = v_b;
    select karten_entfernt into v_n from public.cleanup_orphan_cards();
    call test.check(v_n = 1, 'Mit einem Tag Frist geht auch der frische Kunde');

    raise notice '--- Verwaiste Karten bestanden ---';
end;
$$;

-- ============================================================================
-- Was passiert, wenn die Nachweise verfallen sind?
--
-- cleanup_expired_proofs loescht Nachweise nach proof_retention_days. Eine
-- Karte, die vor einem Vierteljahr gesammelt hat, steht danach ohne Beleg da -
-- die Stempel liegen weiter auf ihr, der Beweis ihrer Herkunft ist weg.
--
-- Die erste Fassung der Verwaist-Regel fragte nach Nachweisen und hielt so
-- eine Karte fuer leer. Sie haette einem Stammkunden die Punkte geloescht,
-- und zwar nicht als Randfall, sondern bei jedem, der laenger als die
-- Aufbewahrungsfrist dabei ist. Deshalb fragt sie jetzt nach Bewegung.
-- ============================================================================
do $$
declare
    v_b      uuid;
    v_kunde  uuid;
    v_n      bigint;
    v_anzahl int;
begin
    insert into public.tenants (slug, name, orphan_card_days, proof_retention_days)
         values ('verwaist-lang', 'Langzeitbetrieb', 365, 90) returning id into v_b;
    insert into auth.users default values returning id into v_kunde;

    insert into public.memberships (user_id, tenant_id, joined_at)
         values (v_kunde, v_b, now() - interval '120 days');
    for i in 1..3 loop
        insert into public.stamps (id, user_id, tenant_id, created_at)
             values (gen_random_uuid(), v_kunde, v_b, now() - interval '100 days');
        insert into public.stamp_proofs (tenant_id, user_id, proof_ref, source, created_at)
             values (v_b, v_kunde, 'lang-' || i, 'receipt', now() - interval '100 days');
    end loop;

    -- Die Nacht, in der die Aufbewahrungsfrist zuschlaegt.
    perform public.cleanup_expired_proofs();
    select count(*) into v_anzahl from public.stamp_proofs where tenant_id = v_b;
    call test.check(v_anzahl = 0, 'Die alten Nachweise sind verfallen');
    select count(*) into v_anzahl from public.stamps where tenant_id = v_b;
    call test.check(v_anzahl = 3, 'Die Stempel liegen weiter auf der Karte');

    -- Zwanzig Minuten spaeter der Hausputz.
    select karten_entfernt into v_n from public.cleanup_orphan_cards();
    call test.check(v_n = 0, 'Der Hausputz fasst eine Karte mit Stempeln nicht an');

    select count(*) into v_anzahl from public.memberships where tenant_id = v_b;
    call test.check(v_anzahl = 1, 'Die Karte des Stammkunden steht noch');
    select count(*) into v_anzahl from public.stamps where tenant_id = v_b;
    call test.check(v_anzahl = 3, 'Und ihre drei Stempel auch');

    raise notice '--- Verfallene Nachweise bestanden ---';
end;
$$;
