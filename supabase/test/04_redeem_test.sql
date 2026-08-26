-- Tests für das serverseitige Einlösen.

\set ON_ERROR_STOP on

do $$
declare
    v_tenant  uuid := '00000000-0000-4000-8000-000000000001';
    v_alice   uuid;
    v_mallory uuid;
    v_voucher uuid;
    v_ok      boolean;
    v_count   int;
    v_per_card int;
begin
    insert into auth.users default values returning id into v_alice;
    insert into auth.users default values returning id into v_mallory;
    insert into public.memberships (user_id, tenant_id) values (v_alice, v_tenant);
    insert into public.memberships (user_id, tenant_id) values (v_mallory, v_tenant);

    select stamps_per_card into v_per_card from public.tenants where id = v_tenant;

    -- Karte vollmachen, um an einen Gutschein zu kommen
    call auth.become(v_alice);
    for i in 1..v_per_card loop
        select voucher_id into v_voucher from public.issue_stamp(v_tenant, 'redeem-bon-' || i);
    end loop;
    call test.check(v_voucher is not null, 'Gutschein für den Test vorhanden');

    -- --------------------------------------------- Falscher Code
    begin
        perform * from public.redeem_voucher(v_voucher, '9999');
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Falscher Code löst nicht ein');

    select is_redeemed into v_ok from public.vouchers where id = v_voucher;
    call test.check(not v_ok, 'Nach falschem Code ist der Gutschein unverändert');

    -- --------------------------------------------- Fremder Gutschein
    call auth.become(v_mallory);
    begin
        perform * from public.redeem_voucher(v_voucher, '1234');
        v_ok := false;
    exception when others then
        v_ok := (sqlerrm like '%not found%');
    end;
    call test.check(v_ok, 'Ein fremder Gutschein ist nicht einlösbar - auch mit richtigem Code');

    -- --------------------------------------------- Richtiger Code
    call auth.become(v_alice);
    perform * from public.redeem_voucher(v_voucher, '1234');
    select is_redeemed into v_ok from public.vouchers where id = v_voucher;
    call test.check(v_ok, 'Mit richtigem Code wird eingelöst');

    -- --------------------------------------------- Zweites Einlösen
    begin
        perform * from public.redeem_voucher(v_voucher, '1234');
        v_ok := false;
    exception when others then
        v_ok := (sqlerrm like '%already redeemed%');
    end;
    call test.check(v_ok, 'Ein eingelöster Gutschein lässt sich nicht erneut einlösen');

    -- --------------------------------------------- Abgelaufener Gutschein
    for i in 1..v_per_card loop
        select voucher_id into v_voucher from public.issue_stamp(v_tenant, 'expired-bon-' || i);
    end loop;
    update public.vouchers set expires_at = 1 where id = v_voucher;
    begin
        perform * from public.redeem_voucher(v_voucher, '1234');
        v_ok := false;
    exception when others then
        v_ok := (sqlerrm like '%expired%');
    end;
    call test.check(v_ok, 'Ein abgelaufener Gutschein lässt sich nicht einlösen');

    -- --------------------------------------------- Unbekannter Gutschein
    begin
        perform * from public.redeem_voucher(gen_random_uuid(), '1234');
        v_ok := false;
    exception when others then
        v_ok := (sqlerrm like '%not found%');
    end;
    call test.check(v_ok, 'Ein unbekannter Gutschein wird abgelehnt');

    raise notice '--- Einlöse-Tests bestanden ---';
end
$$;

-- Der Client darf Gutscheine nicht mehr selbst aktualisieren.
do $$
declare
    v_tenant  uuid := '00000000-0000-4000-8000-000000000001';
    v_bob     uuid;
    v_voucher uuid;
    v_ok      boolean;
    v_per_card int;
begin
    insert into auth.users default values returning id into v_bob;
    insert into public.memberships (user_id, tenant_id) values (v_bob, v_tenant);
    select stamps_per_card into v_per_card from public.tenants where id = v_tenant;

    call auth.become(v_bob);
    for i in 1..v_per_card loop
        select voucher_id into v_voucher from public.issue_stamp(v_tenant, 'bob-bon-' || i);
    end loop;

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
