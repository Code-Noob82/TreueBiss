-- Tests für die Stempelvergabe. Laufen gegen ein lokales Postgres, das mit
-- 00_supabase_stubs.sql und schema.sql vorbereitet wurde.
--
-- Jede Prüfung schlägt über `raise exception` fehl, damit psql mit
-- ON_ERROR_STOP abbricht und der Testlauf rot wird.

\set ON_ERROR_STOP on

do $$
declare
    v_tenant   uuid := '00000000-0000-4000-8000-000000000001';
    v_alice    uuid;
    v_bob      uuid;
    v_count    int;
    v_voucher  uuid;
    v_expires  int8;
    v_stamp    uuid;
    v_ok       boolean;
    v_per_card int;
begin
    -- ---------------------------------------------------------- Vorbereitung
    insert into auth.users default values returning id into v_alice;
    insert into auth.users default values returning id into v_bob;

    select stamps_per_card into v_per_card from public.tenants where id = v_tenant;
    call test.check(v_per_card = 10, 'Demo-Betrieb hat eine 10er-Karte');

    -- ------------------------------------------- Ohne Mitgliedschaft: Ablehnung
    call auth.become(v_alice);
    begin
        perform * from public.issue_stamp(v_tenant, 'bon-ohne-mitgliedschaft');
        v_ok := false;
    exception when others then
        v_ok := (sqlerrm like '%not a member%');
    end;
    call test.check(v_ok, 'Ohne Mitgliedschaft wird die Vergabe abgelehnt');

    -- ------------------------------------------------------ Mitglied werden
    insert into public.memberships (user_id, tenant_id) values (v_alice, v_tenant);
    insert into public.memberships (user_id, tenant_id) values (v_bob, v_tenant);

    -- ------------------------------------------------ Erster Stempel klappt
    select stamp_id, stamp_count, voucher_id
      into v_stamp, v_count, v_voucher
      from public.issue_stamp(v_tenant, 'bon-1');
    call test.check(v_count = 1, 'Erster Beleg ergibt genau einen Stempel');
    call test.check(v_voucher is null, 'Bei einem Stempel entsteht kein Gutschein');
    call test.check(v_stamp is not null, 'Die Funktion liefert eine Stempel-ID');

    -- ------------------------------------- Derselbe Beleg ein zweites Mal
    begin
        perform * from public.issue_stamp(v_tenant, 'bon-1');
        v_ok := false;
    exception when unique_violation then
        v_ok := true;
    end;
    call test.check(v_ok, 'Derselbe Beleg wird beim zweiten Mal abgelehnt (23505)');

    select count(*) into v_count from public.stamps where user_id = v_alice;
    call test.check(v_count = 1, 'Nach dem abgelehnten Versuch bleibt es bei einem Stempel');

    -- ------------------------- Derselbe Beleg bei einem anderen Nutzer
    call auth.become(v_bob);
    begin
        perform * from public.issue_stamp(v_tenant, 'bon-1');
        v_ok := false;
    exception when unique_violation then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ein fremder Nutzer kann denselben Beleg nicht verwenden');

    -- --------------------------------------------------- Leerer Nachweis
    call auth.become(v_alice);
    begin
        perform * from public.issue_stamp(v_tenant, '   ');
        v_ok := false;
    exception when others then
        v_ok := (sqlerrm like '%proof reference required%');
    end;
    call test.check(v_ok, 'Ein leerer Nachweis wird abgelehnt');

    -- ------------------------------------------- Karte vollmachen (Stempel 2..10)
    for i in 2..v_per_card loop
        select stamp_count, voucher_id, voucher_expires_at
          into v_count, v_voucher, v_expires
          from public.issue_stamp(v_tenant, 'bon-' || i);
    end loop;

    call test.check(v_count = v_per_card, 'Der zehnte Beleg meldet zehn Stempel');
    call test.check(v_voucher is not null, 'Bei voller Karte entsteht ein Gutschein');
    call test.check(
        v_expires > (extract(epoch from now()) * 1000)::int8,
        'Der Gutschein läuft in der Zukunft ab'
    );

    select count(*) into v_count from public.stamps
     where user_id = v_alice and tenant_id = v_tenant;
    call test.check(v_count = 0, 'Die Karte ist danach zurückgesetzt');

    select count(*) into v_count from public.vouchers
     where user_id = v_alice and tenant_id = v_tenant;
    call test.check(v_count = 1, 'Es gibt genau einen Gutschein');

    -- --------------------------------- Nach dem Reset zaehlt es wieder von vorn
    select stamp_count into v_count from public.issue_stamp(v_tenant, 'bon-11');
    call test.check(v_count = 1, 'Nach dem Reset beginnt die Zählung wieder bei eins');

    -- ------------------------------------------------ Unbekannter Betrieb
    begin
        perform * from public.issue_stamp(gen_random_uuid(), 'bon-fremd');
        v_ok := false;
    exception when others then
        v_ok := true;  -- scheitert bereits an der Mitgliedschaftspruefung
    end;
    call test.check(v_ok, 'Ein unbekannter Betrieb wird abgelehnt');

    raise notice '--- Funktionstests bestanden ---';
end
$$;
