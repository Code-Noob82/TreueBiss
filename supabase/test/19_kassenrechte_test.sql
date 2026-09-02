-- ============================================================================
-- Wer sieht die Zahlen, wer bedient die Kasse
--
-- Zwei Regeln, am 02.09.2026 beim Durchspielen entschieden:
--
--   Die Kasse sieht die Zahlen des Betriebs nicht.
--   Die Betriebsleitung kann die Kasse bedienen.
--
-- Die erste war bis dahin nur eine Sperre in der Oberflaeche: Die Verwaltung
-- filterte auf owner und demo, die Funktion dahinter fragte aber is_staff_of -
-- und das ist fuer jede Rolle wahr. Wer sie unmittelbar aufrief, bekam die
-- Zahlen trotzdem.
--
-- Die zweite ist der Grund, warum staff_redeem_voucher und
-- staff_counter_token weiterhin is_staff_of fragen. Sie mitzuverschaerfen
-- haette dem Inhaber die eigene Kasse abgeschlossen.
-- ============================================================================
do $$
declare
    v_b       uuid;
    v_chef    uuid;
    v_kasse   uuid;
    v_demo    uuid;
    v_kunde   uuid;
    v_gut     uuid;
    v_anzahl  int;
    v_token   text;
    v_ok      boolean;
begin
    insert into public.tenants (slug, name, counter_qr_enabled)
         values ('rechte-kasse', 'Rechtebetrieb', true) returning id into v_b;
    insert into public.tenant_secrets (tenant_id, counter_secret)
         values (v_b, encode(extensions.gen_random_bytes(32), 'hex'));

    insert into auth.users default values returning id into v_chef;
    insert into auth.users default values returning id into v_kasse;
    insert into auth.users default values returning id into v_demo;
    insert into auth.users default values returning id into v_kunde;
    insert into public.tenant_staff (user_id, tenant_id, role) values
        (v_chef, v_b, 'owner'), (v_kasse, v_b, 'staff'), (v_demo, v_b, 'demo');

    insert into public.memberships (user_id, tenant_id) values (v_kunde, v_b);
    insert into public.vouchers (id, creation_date, expires_at, is_redeemed, user_id, tenant_id)
         values (gen_random_uuid(), (extract(epoch from now()) * 1000)::int8,
                 (extract(epoch from now() + interval '30 days') * 1000)::int8,
                 false, v_kunde, v_b)
    returning id into v_gut;

    -- ------------------------------------------ Die Kasse sieht keine Zahlen
    call auth.become(v_kasse);
    set local role authenticated;
    select count(*) into v_anzahl from public.staff_pilot_summary();
    call test.check(v_anzahl = 0, 'Die Kasse bekommt keine Kennzahlen');
    select count(*) into v_anzahl from public.staff_pilot_cohorts();
    call test.check(v_anzahl = 0, 'Und auch keine Kohortenzahlen');

    -- ------------------------------------------ ... bedient aber die Kasse
    perform public.staff_redeem_voucher(v_gut);
    select token into v_token from public.staff_counter_token(v_b);
    reset role;

    select count(*) into v_anzahl from public.vouchers where id = v_gut and is_redeemed;
    call test.check(v_anzahl = 1, 'Die Kasse loest weiterhin ein');
    call test.check(v_token is not null, 'Und bekommt weiterhin den Tresen-Token');

    -- ------------------------------ Die Betriebsleitung sieht alles ...
    call auth.become(v_chef);
    set local role authenticated;
    select count(*) into v_anzahl from public.staff_pilot_summary();
    call test.check(v_anzahl = 1, 'Die Betriebsleitung sieht die Kennzahlen');
    select count(*) into v_anzahl from public.staff_pilot_cohorts();
    call test.check(v_anzahl = 1, 'Und die Kohortenzahlen');

    -- ------------------------------ ... und kann die Kasse bedienen
    /*
     * Der Grund, warum staff_redeem_voucher weiterhin is_staff_of fragt. In
     * einem kleinen Betrieb steht der Inhaber selbst hinter der Theke; ihn
     * von der eigenen Kasse auszusperren waere die Regel falsch herum.
     */
    -- Ohne Rolle anlegen: Die Policy auf vouchers laesst niemanden fremde
    -- Gutscheine schreiben, auch den Inhaber nicht.
    reset role;
    insert into public.vouchers (id, creation_date, expires_at, is_redeemed, user_id, tenant_id)
         values (gen_random_uuid(), (extract(epoch from now()) * 1000)::int8,
                 (extract(epoch from now() + interval '30 days') * 1000)::int8,
                 false, v_kunde, v_b)
    returning id into v_gut;
    call auth.become(v_chef);
    set local role authenticated;
    perform public.staff_redeem_voucher(v_gut);
    select token into v_token from public.staff_counter_token(v_b);
    reset role;

    select count(*) into v_anzahl from public.vouchers where id = v_gut and is_redeemed;
    call test.check(v_anzahl = 1, 'Die Betriebsleitung loest an der Kasse ein');
    call test.check(v_token is not null, 'Und bekommt den Tresen-Token');

    -- ------------------------------------------------- Der Demozugang bleibt
    call auth.become(v_demo);
    set local role authenticated;
    select count(*) into v_anzahl from public.staff_pilot_summary();
    call test.check(v_anzahl = 1, 'Der Demozugang sieht die Zahlen weiterhin');
    select count(*) into v_anzahl from public.staff_pilot_cohorts();
    call test.check(v_anzahl = 1, 'Und die Kohortenzahlen weiterhin');
    reset role;

    -- --------------------------------------------- Ein Fremder sieht nichts
    call auth.become(v_kunde);
    set local role authenticated;
    select count(*) into v_anzahl from public.staff_pilot_summary();
    call test.check(v_anzahl = 0, 'Ein Kunde sieht keine Zahlen');
    reset role;

    raise notice '--- Kassenrechte bestanden ---';
end;
$$;
