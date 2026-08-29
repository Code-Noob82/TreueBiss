-- Tests für das serverseitige Einlösen.
--
-- Zwei Betriebsarten: Standardmäßig löst der Kunde selbst ein (die App zeigt
-- eine zeitgebundene Bestätigung, das Personal schaut kurz drauf). Betriebe,
-- die es strenger wollen, schalten requires_redeem_code ein.

\set ON_ERROR_STOP on

-- Hilfsprozedur: Karte vollmachen und die Gutschein-ID liefern
create or replace procedure test.fill_card(
    p_user uuid, p_tenant uuid, p_prefix text, inout p_voucher uuid
)
language plpgsql as $$
declare v_per_card int;
begin
    select stamps_per_card into v_per_card from public.tenants where id = p_tenant;
    call auth.become(p_user);
    for i in 1..v_per_card loop
        select voucher_id into p_voucher
          from public.issue_stamp(p_tenant, p_prefix || '-' || i);
    end loop;
end;
$$;

-- ============================ Der Einloese-Code liegt sicher
do $$
declare v_treffer int;
begin
    -- Frueher hat schema.sql jedem Betrieb ohne Code das oeffentlich
    -- bekannte "1234" verpasst. Der Waechter faellt, sobald das zurueckkommt.
    select count(*) into v_treffer
      from public.tenant_secrets
     where extensions.crypt('1234', redeem_code_hash) = redeem_code_hash;
    call test.check(v_treffer = 0, 'Kein Betrieb traegt den Demo-Code "1234"');

    -- Der Hash darf nicht mehr an tenants haengen: Die Lese-Policy dort gilt
    -- fuer die ganze Zeile, jeder App-Nutzer haette ihn mitgelesen.
    select count(*) into v_treffer
      from information_schema.columns
     where table_schema = 'public' and table_name = 'tenants'
       and column_name = 'redeem_code_hash';
    call test.check(v_treffer = 0, 'tenants traegt keinen Code-Hash mehr');
end
$$;

-- ============================ Der Client kommt an die Geheimnisse nicht heran
do $$
declare
    v_user uuid;
    v_zeilen int;
begin
    insert into auth.users default values returning id into v_user;
    insert into public.tenant_secrets (tenant_id, redeem_code_hash)
    values ('00000000-0000-4000-8000-000000000001',
            extensions.crypt('geheim-4711', extensions.gen_salt('bf')))
        on conflict (tenant_id) do update set redeem_code_hash = excluded.redeem_code_hash;

    call auth.become(v_user);
    set local role authenticated;
    begin
        select count(*) into v_zeilen from public.tenant_secrets;
    exception when insufficient_privilege then
        -- Noch besser als null Zeilen: Der revoke greift schon vor der
        -- Policy. Beides ist ein Bestehen, die Meldung darf sich aendern.
        v_zeilen := 0;
    end;
    reset role;
    call test.check(v_zeilen = 0, 'Ein App-Nutzer sieht keinen Code-Hash');

    delete from public.tenant_secrets
     where tenant_id = '00000000-0000-4000-8000-000000000001';
end
$$;

-- ============================ Standard: ohne Code einlösbar
do $$
declare
    v_tenant  uuid := '00000000-0000-4000-8000-000000000001';
    v_alice   uuid;
    v_mallory uuid;
    v_voucher uuid;
    v_when    timestamptz;
    v_ok      boolean;
begin
    insert into auth.users default values returning id into v_alice;
    insert into auth.users default values returning id into v_mallory;
    insert into public.memberships (user_id, tenant_id) values (v_alice, v_tenant);
    insert into public.memberships (user_id, tenant_id) values (v_mallory, v_tenant);

    select requires_redeem_code into v_ok from public.tenants where id = v_tenant;
    call test.check(not v_ok, 'Standardmäßig ist kein Code nötig');

    call test.fill_card(v_alice, v_tenant, 'plain', v_voucher);
    call test.check(v_voucher is not null, 'Gutschein für den Test vorhanden');

    -- ------------------------------------------ Einlösen ohne Code
    select redeemed_at into v_when from public.redeem_voucher(v_voucher);
    select is_redeemed into v_ok from public.vouchers where id = v_voucher;
    call test.check(v_ok, 'Ohne Code wird eingelöst, wenn der Betrieb ihn nicht verlangt');
    call test.check(
        v_when is not null and v_when <= now(),
        'Die Funktion liefert den Einlöse-Zeitpunkt zurück'
    );

    -- ------------------------------------------ Zweites Einlösen
    begin
        perform * from public.redeem_voucher(v_voucher);
        v_ok := false;
    exception when others then
        v_ok := (sqlerrm like '%already redeemed%');
    end;
    call test.check(v_ok, 'Ein eingelöster Gutschein lässt sich nicht erneut einlösen');

    -- ------------------------------------------ Fremder Gutschein
    call test.fill_card(v_alice, v_tenant, 'foreign', v_voucher);
    call auth.become(v_mallory);
    begin
        perform * from public.redeem_voucher(v_voucher);
        v_ok := false;
    exception when others then
        v_ok := (sqlerrm like '%not found%');
    end;
    call test.check(v_ok, 'Ein fremder Gutschein ist nicht einlösbar');

    -- ------------------------------------------ Abgelaufen
    call auth.become(v_alice);
    update public.vouchers set expires_at = 1 where id = v_voucher;
    begin
        perform * from public.redeem_voucher(v_voucher);
        v_ok := false;
    exception when others then
        v_ok := (sqlerrm like '%expired%');
    end;
    call test.check(v_ok, 'Ein abgelaufener Gutschein lässt sich nicht einlösen');

    -- ------------------------------------------ Unbekannt
    begin
        perform * from public.redeem_voucher(gen_random_uuid());
        v_ok := false;
    exception when others then
        v_ok := (sqlerrm like '%not found%');
    end;
    call test.check(v_ok, 'Ein unbekannter Gutschein wird abgelehnt');

    raise notice '--- Einlöse-Tests (ohne Code) bestanden ---';
end
$$;

-- ============================ Strenger Betrieb: Code verlangt
do $$
declare
    v_tenant  uuid := '00000000-0000-4000-8000-000000000001';
    v_bob     uuid;
    v_voucher uuid;
    v_ok      boolean;
begin
    -- Den Code hier selbst setzen: Ein Test, der einen Code aus den
    -- Beispieldaten braucht, haelt genau den Fehler am Leben, den er
    -- pruefen soll.
    update public.tenants set requires_redeem_code = true where id = v_tenant;
    insert into public.tenant_secrets (tenant_id, redeem_code_hash)
    values (v_tenant, extensions.crypt('pruef-4711', extensions.gen_salt('bf')))
        on conflict (tenant_id) do update set redeem_code_hash = excluded.redeem_code_hash;

    insert into auth.users default values returning id into v_bob;
    insert into public.memberships (user_id, tenant_id) values (v_bob, v_tenant);
    call test.fill_card(v_bob, v_tenant, 'strict', v_voucher);

    -- ------------------------------------------ Ohne Code
    begin
        perform * from public.redeem_voucher(v_voucher);
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Verlangt der Betrieb einen Code, geht es ohne nicht');

    -- ------------------------------------------ Falscher Code
    begin
        perform * from public.redeem_voucher(v_voucher, '9999');
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Falscher Code löst nicht ein');

    select is_redeemed into v_ok from public.vouchers where id = v_voucher;
    call test.check(not v_ok, 'Nach den Fehlversuchen ist der Gutschein unverändert');

    -- ------------------------------------------ Richtiger Code
    perform * from public.redeem_voucher(v_voucher, 'pruef-4711');
    select is_redeemed into v_ok from public.vouchers where id = v_voucher;
    call test.check(v_ok, 'Mit richtigem Code wird eingelöst');

    update public.tenants set requires_redeem_code = false where id = v_tenant;
    delete from public.tenant_secrets where tenant_id = v_tenant;
    raise notice '--- Einlöse-Tests (mit Code) bestanden ---';
end
$$;

-- ============================ Der Client darf nicht selbst schreiben
do $$
declare
    v_tenant  uuid := '00000000-0000-4000-8000-000000000001';
    v_carol   uuid;
    v_voucher uuid;
    v_ok      boolean;
begin
    insert into auth.users default values returning id into v_carol;
    insert into public.memberships (user_id, tenant_id) values (v_carol, v_tenant);
    call test.fill_card(v_carol, v_tenant, 'rls', v_voucher);

    set local role authenticated;
    begin
        update public.vouchers set is_redeemed = true where id = v_voucher;
        -- Ohne update-Policy trifft das Update null Zeilen statt zu werfen.
        v_ok := not found;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Die App kann einen Gutschein nicht selbst als eingelöst markieren');

    reset role;
    select is_redeemed into v_ok from public.vouchers where id = v_voucher;
    call test.check(not v_ok, 'Der Gutschein ist danach weiterhin offen');

    raise notice '--- Einlöse-RLS bestanden ---';
end
$$;
