-- Tests für die Kassenseite des Betriebs.
--
-- Das Personal löst Gutscheine ein, die ihm nicht gehören - die reguläre
-- Funktion prüft dagegen auf Besitz. Hier ersetzt die Beschäftigung beim
-- Betrieb diesen Nachweis.

\set ON_ERROR_STOP on

do $$
declare
    v_tenant   uuid := '00000000-0000-4000-8000-000000000001';
    v_other    uuid;
    v_kunde    uuid;
    v_personal uuid;
    v_fremd    uuid;
    v_voucher  uuid;
    v_owner    uuid;
    v_when     timestamptz;
    v_ok       boolean;
begin
    insert into public.tenants (id, slug, name)
    values (gen_random_uuid(), 'anderer-betrieb', 'Anderer Betrieb')
    returning id into v_other;

    insert into auth.users default values returning id into v_kunde;
    insert into auth.users default values returning id into v_personal;
    insert into auth.users default values returning id into v_fremd;
    insert into public.memberships (user_id, tenant_id) values (v_kunde, v_tenant);
    insert into public.tenant_staff (user_id, tenant_id) values (v_personal, v_tenant);
    insert into public.tenant_staff (user_id, tenant_id) values (v_fremd, v_other);

    call test.fill_card(v_kunde, v_tenant, 'staff', v_voucher);
    call test.check(v_voucher is not null, 'Gutschein für den Test vorhanden');

    -- ------------------------------------ Personal eines anderen Betriebs
    call auth.become(v_fremd);
    begin
        perform * from public.staff_redeem_voucher(v_voucher);
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Personal eines anderen Betriebs kann nicht einlösen');

    -- ------------------------------------ Kunde ist kein Personal
    call auth.become(v_kunde);
    begin
        perform * from public.staff_redeem_voucher(v_voucher);
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ein Kunde kann die Kassenfunktion nicht nutzen');

    -- ------------------------------------ Eigenes Personal löst ein
    call auth.become(v_personal);
    select voucher_id, redeemed_at, customer
      into v_voucher, v_when, v_owner
      from public.staff_redeem_voucher(v_voucher);
    call test.check(v_when is not null, 'Personal löst einen fremden Gutschein ein');
    call test.check(v_owner = v_kunde, 'Die Funktion nennt den Kunden, dem er gehörte');

    select is_redeemed into v_ok from public.vouchers where id = v_voucher;
    call test.check(v_ok, 'Der Gutschein ist danach eingelöst');

    -- ------------------------------------ Kein Code nötig
    -- Wer scannt, ist der Betrieb - der Einlöse-Code entfällt dabei.
    update public.tenants set requires_redeem_code = true where id = v_tenant;
    call auth.become(v_kunde);
    call test.fill_card(v_kunde, v_tenant, 'staff2', v_voucher);
    call auth.become(v_personal);
    perform * from public.staff_redeem_voucher(v_voucher);
    select is_redeemed into v_ok from public.vouchers where id = v_voucher;
    call test.check(v_ok, 'Auch bei erzwungenem Code löst das Personal ohne ihn ein');
    update public.tenants set requires_redeem_code = false where id = v_tenant;

    -- ------------------------------------ Zweites Einlösen
    begin
        perform * from public.staff_redeem_voucher(v_voucher);
        v_ok := false;
    exception when others then
        v_ok := (sqlerrm like '%already redeemed%');
    end;
    call test.check(v_ok, 'Ein eingelöster Gutschein lässt sich nicht erneut einlösen');

    raise notice '--- Kassen-Einlösen bestanden ---';
end
$$;

-- Die Zahlen des eigenen Betriebs - und nur die.
--
-- Seit dem 02.09.2026 sieht sie nur die Betriebsleitung. Vorher stand hier
-- eine Zuordnung ohne Rolle, die damit 'staff' bekam - und der Test bestand,
-- weil is_staff_of fuer jede Rolle wahr ist. Die Kasse soll die Zahlen des
-- Betriebs nicht sehen; sie loest ein und zeigt den Tresen-Code.
do $$
declare
    v_tenant   uuid := '00000000-0000-4000-8000-000000000001';
    v_personal uuid;
    v_kunde    uuid;
    v_n        int;
begin
    insert into auth.users default values returning id into v_personal;
    insert into auth.users default values returning id into v_kunde;
    insert into public.tenant_staff (user_id, tenant_id, role)
         values (v_personal, v_tenant, 'owner');

    call auth.become(v_personal);
    set local role authenticated;

    select count(*) into v_n from public.staff_pilot_summary();
    call test.check(v_n = 1, 'Die Betriebsleitung sieht genau den eigenen Betrieb');

    select count(*) into v_n from public.staff_pilot_summary() where tenant_id = v_tenant;
    call test.check(v_n = 1, 'Und zwar den richtigen');

    reset role;
    call auth.become(v_kunde);
    set local role authenticated;
    select count(*) into v_n from public.staff_pilot_summary();
    call test.check(v_n = 0, 'Ein Kunde sieht keine Zahlen');
    reset role;

    raise notice '--- Kassen-Zahlen bestanden ---';
end
$$;
