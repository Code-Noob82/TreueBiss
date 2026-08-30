-- ============================================================================
-- Einlösbare Angebote (Coupons)
--
-- Ein Gutschein ist erarbeitet und gehört einem Kunden. Ein Coupon wird
-- ausgegeben und steht allen offen. Was beide teilen muss, ist die Stelle,
-- an der über den Verbrauch entschieden wird: auf dem Server.
-- ============================================================================
do $$
declare
    v_tenant  uuid;
    v_fremd   uuid;
    v_kunde   uuid;
    v_zweit   uuid;
    v_aushang uuid;
    v_einmal  uuid;
    v_taegl   uuid;
    v_alt     uuid;
    v_kuenft  uuid;
    v_heute   date := (now() at time zone 'Europe/Berlin')::date;
    v_zeilen  int;
    v_sperre  date;
    v_ok      boolean;
begin
    insert into public.tenants (slug, name) values ('coupon-test', 'Couponbetrieb')
    returning id into v_tenant;
    insert into public.tenants (slug, name) values ('coupon-fremd', 'Nachbarbetrieb')
    returning id into v_fremd;

    insert into auth.users default values returning id into v_kunde;
    insert into auth.users default values returning id into v_zweit;
    insert into public.memberships (user_id, tenant_id) values (v_kunde, v_tenant);
    insert into public.memberships (user_id, tenant_id) values (v_zweit, v_tenant);

    insert into public.offers (tenant_id, title, is_redeemable, redeem_limit)
    values (v_tenant, 'Nur ein Aushang', false, 'einmal') returning id into v_aushang;
    insert into public.offers (tenant_id, title, is_redeemable, redeem_limit)
    values (v_tenant, 'Dinkel-Kracher', true, 'einmal') returning id into v_einmal;
    insert into public.offers (tenant_id, title, is_redeemable, redeem_limit)
    values (v_tenant, 'Kaffee zum Brötchen', true, 'taeglich') returning id into v_taegl;
    insert into public.offers (tenant_id, title, is_redeemable, redeem_limit, valid_to)
    values (v_tenant, 'Abgelaufen', true, 'einmal', v_heute - 1) returning id into v_alt;
    insert into public.offers (tenant_id, title, is_redeemable, redeem_limit, valid_from)
    values (v_tenant, 'Kommt erst', true, 'einmal', v_heute + 1) returning id into v_kuenft;

    call auth.become(v_kunde);
    set local role authenticated;

    -- ---------------------------------------------- Der gewöhnliche Weg
    perform * from public.redeem_offer(v_einmal);
    select count(*) into v_zeilen from public.offer_redemptions
     where offer_id = v_einmal and user_id = v_kunde;
    call test.check(v_zeilen = 1, 'Ein freigegebener Coupon lässt sich einlösen');

    -- ---------------------------------------------- Und nur einmal
    begin
        perform * from public.redeem_offer(v_einmal);
        v_ok := false;
    exception when unique_violation then
        v_ok := true;
    end;
    call test.check(v_ok, 'Derselbe Kunde löst denselben Coupon nicht zweimal ein');

    select count(*) into v_zeilen from public.offer_redemptions where offer_id = v_einmal;
    call test.check(v_zeilen = 1, 'Der abgewiesene Versuch hinterlässt keine Zeile');

    -- ---------------------------------------------- Täglich heisst täglich
    perform * from public.redeem_offer(v_taegl);
    begin
        perform * from public.redeem_offer(v_taegl);
        v_ok := false;
    exception when unique_violation then
        v_ok := true;
    end;
    call test.check(v_ok, 'Auch ein täglicher Coupon geht nur einmal am Tag');

    /*
     * Gestern nachgestellt. Das geht nur ohne die eingeschraenkte Rolle: Auf
     * offer_redemptions gibt es keine update-Policy, der Kunde koennte seinen
     * eigenen Sperrschluessel also gar nicht verstellen. Genau so soll es
     * sein - hier hilft es beim Testen, im Betrieb schuetzt es.
     */
    reset role;
    update public.offer_redemptions set sperre = v_heute - 1
     where offer_id = v_taegl and user_id = v_kunde;
    set local role authenticated;
    perform * from public.redeem_offer(v_taegl);
    select count(*) into v_zeilen from public.offer_redemptions
     where offer_id = v_taegl and user_id = v_kunde;
    call test.check(v_zeilen = 2, 'Am nächsten Tag ist der tägliche Coupon wieder frei');

    /*
     * Beim einmaligen Coupon laesst sich ein Tageswechsel nicht nachstellen -
     * es gibt keinen Tag, der ihn freigaebe. Genau das ist die Zusicherung,
     * und pruefbar ist sie am Sperrschluessel: '-infinity' ist kein Datum und
     * kann keines werden, also kollidiert jede weitere Einloesung desselben
     * Kunden mit der ersten, ganz gleich wie viel Zeit vergeht.
     */
    reset role;
    select sperre into v_sperre from public.offer_redemptions
     where offer_id = v_einmal and user_id = v_kunde;
    set local role authenticated;
    call test.check(v_sperre = '-infinity'::date,
        'Ein einmaliger Coupon sperrt mit einem Schlüssel, der kein Tag ist');

    -- Und beim taeglichen ist es der Tag selbst.
    reset role;
    select sperre into v_sperre from public.offer_redemptions
     where offer_id = v_taegl and user_id = v_kunde and sperre <> v_heute - 1;
    set local role authenticated;
    call test.check(v_sperre = v_heute, 'Ein täglicher Coupon sperrt mit dem Tag');

    -- ---------------------------------------------- Was nicht geht
    begin
        perform * from public.redeem_offer(v_aushang);
        v_ok := false;
    exception when sqlstate '22023' then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ein reiner Aushang lässt sich nicht einlösen');

    begin
        perform * from public.redeem_offer(v_alt);
        v_ok := false;
    exception when sqlstate '22023' then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ein abgelaufener Coupon lässt sich nicht einlösen');

    begin
        perform * from public.redeem_offer(v_kuenft);
        v_ok := false;
    exception when sqlstate '22023' then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ein künftiger Coupon lässt sich noch nicht einlösen');

    -- ---------------------------------------------- Die App darf nicht selbst schreiben
    begin
        insert into public.offer_redemptions (offer_id, user_id, tenant_id, sperre)
        values (v_einmal, v_kunde, v_tenant, v_heute + 5);
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Die App trägt keine Einlösung selbst ein');

    begin
        delete from public.offer_redemptions where offer_id = v_einmal;
    exception when insufficient_privilege then
        null;
    end;
    select count(*) into v_zeilen from public.offer_redemptions where offer_id = v_einmal;
    call test.check(v_zeilen = 1, 'Die App nimmt eine Einlösung nicht zurück');

    reset role;

    -- ---------------------------------------------- Ein Fremder ohne Mitgliedschaft
    delete from public.memberships where user_id = v_zweit and tenant_id = v_tenant;
    call auth.become(v_zweit);
    set local role authenticated;
    begin
        perform * from public.redeem_offer(v_einmal);
        v_ok := false;
    exception when sqlstate '22023' then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ohne Mitgliedschaft gibt es keinen Coupon');

    -- Und er sieht die Einloesungen des anderen nicht.
    select count(*) into v_zeilen from public.offer_redemptions;
    call test.check(v_zeilen = 0, 'Ein Kunde sieht fremde Einlösungen nicht');
    reset role;

    -- ---------------------------------------------- Der zweite Kunde ist frei
    insert into public.memberships (user_id, tenant_id) values (v_zweit, v_tenant);
    call auth.become(v_zweit);
    set local role authenticated;
    perform * from public.redeem_offer(v_einmal);
    reset role;
    select count(*) into v_zeilen from public.offer_redemptions where offer_id = v_einmal;
    call test.check(v_zeilen = 2, 'Ein anderer Kunde löst denselben Coupon ein');

    -- ---------------------------------------------- Abgeschalteter Betrieb
    update public.tenants set is_active = false where id = v_tenant;
    call auth.become(v_kunde);
    set local role authenticated;
    begin
        perform * from public.redeem_offer(v_taegl);
        v_ok := false;
    exception when sqlstate '22023' then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ein abgeschalteter Betrieb gibt keine Coupons mehr aus');
    reset role;
    update public.tenants set is_active = true where id = v_tenant;

    -- ---------------------------------------------- Aufräumen
    delete from public.offer_redemptions where tenant_id = v_tenant;
    delete from public.offers where tenant_id in (v_tenant, v_fremd);
    delete from public.memberships where tenant_id = v_tenant;
    delete from public.tenants where id in (v_tenant, v_fremd);
    delete from auth.users where id in (v_kunde, v_zweit);

    raise notice '--- Einlösbare Angebote bestanden ---';
end
$$;
