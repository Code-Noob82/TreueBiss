-- Tests für die Pilot-Auswertungen.
--
-- Prüft, dass die Views die Zahlen liefern, auf die eine Entscheidung
-- gestützt werden soll - und dass sie für die App nicht lesbar sind.

\set ON_ERROR_STOP on

do $$
declare
    v_tenant   uuid := '00000000-0000-4000-8000-000000000001';
    v_dana     uuid;
    v_erik     uuid;
    v_voucher  uuid;
    v_per_card int;
    v_n        int;
    v_num      numeric;
begin
    select stamps_per_card into v_per_card from public.tenants where id = v_tenant;

    insert into auth.users default values returning id into v_dana;
    insert into auth.users default values returning id into v_erik;
    insert into public.memberships (user_id, tenant_id) values (v_dana, v_tenant);
    insert into public.memberships (user_id, tenant_id) values (v_erik, v_tenant);

    -- Dana macht eine Karte voll und löst ein, Erik sammelt nur drei Stempel.
    call test.fill_card(v_dana, v_tenant, 'tele-dana', v_voucher);
    perform * from public.redeem_voucher(v_voucher);

    call auth.become(v_erik);
    for i in 1..3 loop
        perform * from public.issue_stamp(v_tenant, 'tele-erik-' || i);
    end loop;

    -- ------------------------------------------------ Anmeldungen
    select sum(new_members) into v_n
      from public.pilot_daily_signups where tenant_id = v_tenant;
    call test.check(v_n >= 2, 'Die Anmeldungen pro Tag zählen die neuen Teilnehmer');

    -- ------------------------------------------------ Stempel
    select sum(stamps), max(customers) into v_n, v_num
      from public.pilot_daily_stamps where tenant_id = v_tenant;
    call test.check(
        v_n >= v_per_card + 3,
        'Die Stempel pro Tag zählen jede Vergabe, auch nach dem Kartenreset'
    );
    call test.check(v_num >= 2, 'Die Stempel pro Tag zählen die Kunden getrennt');

    -- ------------------------------------------------ Einlösungen
    select sum(redemptions) into v_n
      from public.pilot_daily_redemptions where tenant_id = v_tenant;
    call test.check(v_n >= 1, 'Die Einlösungen pro Tag erscheinen mit Zeitstempel');

    select count(*) into v_n from public.vouchers
     where tenant_id = v_tenant and is_redeemed and redeemed_at is null;
    call test.check(v_n = 0, 'Jeder eingelöste Gutschein trägt einen Zeitstempel');

    -- ------------------------------------------------ Gesamtbild
    select stamps_per_active_day, redemption_rate_percent into v_num, v_n
      from public.pilot_summary where tenant_id = v_tenant;
    call test.check(v_num > 0, 'Das Gesamtbild liefert Stempel pro aktivem Tag');
    call test.check(
        v_n between 0 and 100,
        'Die Einlösequote liegt zwischen null und hundert Prozent'
    );

    -- ------------------------------- Altlasten ohne Zeitstempel
    -- Vor der Spalte redeemed_at eingeloeste Gutscheine zaehlen in der
    -- Zusammenfassung mit, fehlen aber in der Tagesansicht. Die Differenz
    -- muss ablesbar sein, sonst wirkt sie wie ein Rechenfehler.
    update public.vouchers set is_redeemed = true, redeemed_at = null
     where tenant_id = v_tenant and not is_redeemed
       and id = (select id from public.vouchers
                  where tenant_id = v_tenant and not is_redeemed limit 1);

    select redemptions_without_timestamp into v_n
      from public.pilot_summary where tenant_id = v_tenant;
    call test.check(
        v_n >= 1,
        'Einlösungen ohne Zeitstempel werden getrennt ausgewiesen'
    );

    raise notice '--- Auswertungs-Tests bestanden ---';
end
$$;

-- Ein Betrieb ohne jede Aktivität darf die Auswertung nicht zum Absturz bringen.
do $$
declare v_leer uuid; v_num numeric;
begin
    insert into public.tenants (id, slug, name)
    values (gen_random_uuid(), 'leerer-betrieb', 'Ohne Aktivität')
    returning id into v_leer;

    select stamps_per_active_day into v_num
      from public.pilot_summary where tenant_id = v_leer;
    call test.check(v_num is null, 'Ohne Stempel gibt es keine Division durch null');

    raise notice '--- Leerfall bestanden ---';
end
$$;

-- Die Auswertungen sind nichts für die App.
do $$
declare v_ok boolean;
begin
    set local role authenticated;
    begin
        perform * from public.pilot_summary;
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Die App kann die Auswertungen nicht lesen');
    reset role;

    raise notice '--- Auswertungs-Rechte bestanden ---';
end
$$;

-- ============================ Tagesgrenze in Ortszeit, nicht UTC
do $$
declare
    v_tenant uuid := '00000000-0000-4000-8000-000000000001';
    v_frank  uuid;
    v_n      int;
begin
    insert into auth.users default values returning id into v_frank;
    insert into public.memberships (user_id, tenant_id) values (v_frank, v_tenant);

    -- 22:30 UTC am 1. Juni ist 00:30 Ortszeit am 2. Juni (Sommerzeit).
    -- Ohne Umrechnung landete die Vergabe auf dem falschen Tag.
    insert into public.stamp_proofs (tenant_id, user_id, proof_ref, source, created_at)
    values (v_tenant, v_frank, 'tz-spaetabends', 'demo',
            timestamptz '2026-06-01 22:30:00+00');

    select count(*) into v_n
      from public.pilot_daily_stamps
     where tenant_id = v_tenant and day = date '2026-06-02';
    call test.check(v_n = 1, 'Eine Vergabe um 22:30 UTC zählt zum Folgetag in Ortszeit');

    select count(*) into v_n
      from public.pilot_daily_stamps
     where tenant_id = v_tenant and day = date '2026-06-01';
    call test.check(v_n = 0, 'Sie erscheint nicht am UTC-Tag');

    raise notice '--- Tagesgrenze bestanden ---';
end
$$;
