-- ============================================================================
-- Demozugang: sehen ja, anfassen nein
--
-- Ein Betrieb soll sich das Produkt ansehen koennen, bevor er es bestellt -
-- mit echten Daten in Kasse und Verwaltung. Sehen darf er alles, was das
-- Personal sieht. Aendern nichts, sonst steht der naechste Interessent vor
-- einer leergeraeumten Demo.
--
-- Die zweite Haelfte ist die wichtigere: dass normales Personal von der
-- Bremse nichts merkt.
-- ============================================================================
do $$
declare
    v_tenant   uuid;
    v_kunde    uuid;
    v_demo     uuid;
    v_personal uuid;
    v_chef     uuid;
    v_gutschein uuid;
    v_zweiter  uuid;
    v_anzahl   int;
    v_ok       boolean;
    v_token    text;
begin
    insert into public.tenants (slug, name, counter_qr_enabled)
         values ('demo-test', 'Demobetrieb', true) returning id into v_tenant;
    insert into public.tenant_secrets (tenant_id, counter_secret)
         values (v_tenant, encode(extensions.gen_random_bytes(32), 'hex'))
         on conflict (tenant_id) do update set counter_secret = excluded.counter_secret;

    insert into auth.users default values returning id into v_kunde;
    insert into auth.users default values returning id into v_demo;
    insert into auth.users default values returning id into v_personal;
    insert into auth.users default values returning id into v_chef;

    insert into public.tenant_staff (user_id, tenant_id, role) values
        (v_demo, v_tenant, 'demo'), (v_personal, v_tenant, 'staff'),
        (v_chef, v_tenant, 'owner');

    insert into public.memberships (user_id, tenant_id) values (v_kunde, v_tenant);
    insert into public.offers (tenant_id, title) values (v_tenant, 'Aushang');
    insert into public.vouchers (id, creation_date, expires_at, is_redeemed, user_id, tenant_id)
         values (gen_random_uuid(), (extract(epoch from now()) * 1000)::int8,
                 (extract(epoch from now() + interval '30 days') * 1000)::int8,
                 false, v_kunde, v_tenant)
         returning id into v_gutschein;
    insert into public.vouchers (id, creation_date, expires_at, is_redeemed, user_id, tenant_id)
         values (gen_random_uuid(), (extract(epoch from now()) * 1000)::int8,
                 (extract(epoch from now() + interval '30 days') * 1000)::int8,
                 false, v_kunde, v_tenant)
         returning id into v_zweiter;

    -- ------------------------------------------------------- Demo: sehen
    call auth.become(v_demo);
    set local role authenticated;

    select count(*) into v_anzahl from public.tenants where id = v_tenant;
    call test.check(v_anzahl = 1, 'Die Demo sieht den Betrieb');
    select count(*) into v_anzahl from public.offers where tenant_id = v_tenant;
    call test.check(v_anzahl = 1, 'Die Demo sieht die Angebote');
    select count(*) into v_anzahl from public.staff_pilot_summary();
    call test.check(v_anzahl = 1, 'Die Demo sieht die Zahlen');
    select count(*) into v_anzahl from public.staff_pilot_cohorts();
    call test.check(v_anzahl = 1, 'Die Demo sieht die Kohortenzahlen');

    -- ---------------------------------------------------- Demo: nichts tun
    begin
        perform public.staff_redeem_voucher(v_gutschein);
        v_ok := false;
    exception when others then
        v_ok := sqlerrm like '%read only%';
    end;
    call test.check(v_ok, 'Die Demo loest keinen Gutschein ein');

    -- Ohne Rolle nachsehen: Die Policy auf vouchers zeigt der Demo fremde
    -- Gutscheine gar nicht. Null Zeilen hiessen hier "unsichtbar", nicht
    -- "eingeloest" - die Pruefung haette nichts belegt.
    reset role;
    select count(*) into v_anzahl from public.vouchers
     where id = v_gutschein and not is_redeemed;
    call test.check(v_anzahl = 1, 'Der Gutschein ist unveraendert');
    call auth.become(v_demo);
    set local role authenticated;

    begin
        perform public.staff_counter_token(v_tenant);
        v_ok := false;
    exception when others then
        v_ok := sqlerrm like '%read only%';
    end;
    call test.check(v_ok, 'Die Demo bekommt den Tresen-Token nicht');

    begin
        perform public.owner_update_tenant(v_tenant, 'Umbenannt', null, null, null,
                                           null, null, null, null);
        v_ok := false;
    exception when others then
        v_ok := true;
    end;
    call test.check(v_ok, 'Die Demo aendert keine Stammdaten');

    -- Ein Verstoss gegen die Policy *wirft*, er liefert nicht null Zeilen.
    begin
        insert into public.offers (tenant_id, title) values (v_tenant, 'Geschmuggelt');
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Die Demo legt kein Angebot an');

    reset role;
    select count(*) into v_anzahl from public.offers where tenant_id = v_tenant;
    call test.check(v_anzahl = 1, 'Es blieb bei dem einen Angebot');

    -- ------------------------------------------ Personal bleibt unberuehrt
    call auth.become(v_personal);
    set local role authenticated;

    perform public.staff_redeem_voucher(v_zweiter);
    select token into v_token from public.staff_counter_token(v_tenant);
    reset role;

    -- Auch hier ohne Rolle nachsehen: Die Policy auf vouchers zeigt dem
    -- Personal den Gutschein eines Kunden nicht. Der Aufruf oben ist
    -- durchgelaufen; belegen laesst sich das nur von aussen.
    select count(*) into v_anzahl from public.vouchers
     where id = v_zweiter and is_redeemed;
    call test.check(v_anzahl = 1, 'Das Personal loest weiter ein');
    call test.check(v_token is not null and length(v_token) = 24,
                    'Und bekommt weiter den Tresen-Token');
    raise notice '--- Demozugang bestanden ---';
end;
$$;
