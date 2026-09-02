-- ============================================================================
-- Karte auf ein anderes Gerät holen
--
-- Bisher lebte eine Karte nur in der anonymen Sitzung im localStorage. Ein
-- Symbol auf dem iOS-Startbildschirm hat einen eigenen Speicher — und war
-- damit ein zweiter Kunde mit leerer Karte. Der Kartenschlüssel löst das.
-- ============================================================================
do $$
declare
    v_tenant  uuid;
    v_fremd   uuid;
    v_alt     uuid;
    v_neu     uuid;
    v_dritt   uuid;
    v_token   text;
    v_token2  text;
    v_voucher uuid;
    v_zahl    int;
    v_zeilen  int;
    v_ok      boolean;
    v_slug    text;
begin
    insert into public.tenants (slug, name) values ('token-test', 'Umzugsbetrieb')
    returning id into v_tenant;
    insert into public.tenants (slug, name) values ('token-fremd', 'Nachbarbetrieb')
    returning id into v_fremd;

    insert into auth.users default values returning id into v_alt;
    insert into auth.users default values returning id into v_neu;
    insert into auth.users default values returning id into v_dritt;

    insert into public.memberships (user_id, tenant_id) values (v_alt, v_tenant);

    -- ---------------------------------------------- Jede Karte bekommt einen Schlüssel
    select card_token into v_token from public.memberships
     where user_id = v_alt and tenant_id = v_tenant;
    call test.check(v_token is not null and length(v_token) = 64,
        'Eine neue Karte bekommt einen 64-stelligen Schlüssel');

    insert into public.memberships (user_id, tenant_id) values (v_alt, v_fremd);
    select card_token into v_token2 from public.memberships
     where user_id = v_alt and tenant_id = v_fremd;
    call test.check(v_token2 <> v_token, 'Jede Karte hat ihren eigenen Schlüssel');

    /*
     * Die alte Karte bekommt Inhalt - aber bewusst keine volle Karte:
     * Beim letzten Stempel entsteht der Gutschein und die Stempel werden
     * abgeraeumt. Der Umzug soll beides tragen, also erst ein paar Stempel,
     * dann eine volle Runde fuer den Gutschein.
     */
    call test.fill_card(v_alt, v_tenant, 'token-voll', v_voucher);
    call auth.become(v_alt);
    perform public.issue_stamp(v_tenant, 'token-rest-1');
    perform public.issue_stamp(v_tenant, 'token-rest-2');
    perform public.issue_stamp(v_tenant, 'token-rest-3');

    select count(*) into v_zahl from public.stamps
     where user_id = v_alt and tenant_id = v_tenant;
    call test.check(v_zahl = 3, 'Die alte Karte trägt drei Stempel');
    select count(*) into v_zeilen from public.vouchers
     where user_id = v_alt and tenant_id = v_tenant;
    call test.check(v_zeilen = 1, 'Und einen Gutschein aus der vollen Runde davor');

    -- ---------------------------------------------- Der Umzug
    -- Die Mitgliedschaft ohne Rolle anlegen: Seit dem 02.09.2026 darf der
    -- Browser diese Tabelle nicht mehr beschreiben. Im Betrieb entsteht sie
    -- ueber activate_card, hier reicht der Aufbau ohne Rolle.
    insert into public.memberships (user_id, tenant_id) values (v_neu, v_tenant);

    call auth.become(v_neu);
    set local role authenticated;
    select tenant_slug into v_slug from public.adopt_card(v_token);
    reset role;
    call test.check(v_slug = 'token-test', 'Der Umzug nennt den Betrieb zurück');

    select count(*) into v_zahl from public.stamps
     where user_id = v_neu and tenant_id = v_tenant;
    call test.check(v_zahl = 3, 'Die drei Stempel sind auf dem neuen Gerät');

    select count(*) into v_zeilen from public.stamps
     where user_id = v_alt and tenant_id = v_tenant;
    call test.check(v_zeilen = 0, 'Das alte Gerät hat sie nicht mehr — Umzug, kein Duplikat');

    select count(*) into v_zeilen from public.vouchers
     where user_id = v_neu and tenant_id = v_tenant;
    call test.check(v_zeilen = 1, 'Der Gutschein ist mitgewandert');

    select count(*) into v_zeilen from public.stamp_proofs
     where user_id = v_neu and tenant_id = v_tenant;
    call test.check(v_zeilen > 0, 'Die Nachweise sind mitgewandert');

    select count(*) into v_zeilen from public.memberships
     where user_id = v_neu and tenant_id = v_tenant;
    call test.check(v_zeilen = 1, 'Es bleibt genau eine Mitgliedschaft');

    -- Der Schlüssel bleibt derselbe: Ein Wallet-Pass trägt ihn und darf
    -- durch den Umzug nicht ungültig werden.
    select card_token into v_token2 from public.memberships
     where user_id = v_neu and tenant_id = v_tenant;
    call test.check(v_token2 = v_token, 'Der Schlüssel überlebt den Umzug');

    -- ---------------------------------------------- Zweimal derselbe Aufruf
    call auth.become(v_neu);
    set local role authenticated;
    select tenant_slug into v_slug from public.adopt_card(v_token);
    reset role;
    call test.check(v_slug = 'token-test', 'Ein zweiter Aufruf auf demselben Gerät ist folgenlos');
    select count(*) into v_zahl from public.stamps
     where user_id = v_neu and tenant_id = v_tenant;
    call test.check(v_zahl > 0, 'Und lässt die Stempel stehen');

    -- ---------------------------------------------- Was nicht geht
    call auth.become(v_dritt);
    set local role authenticated;

    begin
        perform * from public.adopt_card('zu-kurz');
        v_ok := false;
    exception when sqlstate '22023' then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ein zu kurzer Schlüssel wird abgewiesen');

    begin
        perform * from public.adopt_card(repeat('a', 64));
        v_ok := false;
    exception when sqlstate '22023' then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ein erfundener Schlüssel findet keine Karte');

    -- Ein Gerät, das hier selbst gesammelt hat, verliert nichts stillschweigend.
    -- Ohne Rolle anlegen, siehe oben.
    reset role;
    insert into public.memberships (user_id, tenant_id) values (v_dritt, v_tenant);
    call auth.become(v_dritt);
    perform public.issue_stamp(v_tenant, 'token-dritt-1');
    call auth.become(v_dritt);
    set local role authenticated;
    begin
        perform * from public.adopt_card(v_token);
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ein Gerät mit eigener Karte übernimmt nicht einfach eine fremde');

    select count(*) into v_zahl from public.stamps
     where user_id = v_dritt and tenant_id = v_tenant;
    call test.check(v_zahl > 0, 'Und behält dabei seinen eigenen Bestand');
    reset role;

    -- ---------------------------------------------- Fremde Schlüssel bleiben verborgen
    call auth.become(v_dritt);
    set local role authenticated;
    select count(*) into v_zeilen from public.memberships
     where tenant_id = v_tenant and user_id <> v_dritt;
    call test.check(v_zeilen = 0, 'Ein Kunde sieht fremde Kartenschlüssel nicht');
    reset role;

    -- ---------------------------------------------- Abgeschalteter Betrieb
    reset role;
    update public.tenants set is_active = false where id = v_fremd;
    insert into public.memberships (user_id, tenant_id) values (v_neu, v_fremd)
        on conflict do nothing;
    call auth.become(v_dritt);
    set local role authenticated;
    begin
        perform * from public.adopt_card(
            (select card_token from public.memberships
              where user_id = v_alt and tenant_id = v_fremd));
        v_ok := false;
    exception when sqlstate '22023' then
        v_ok := true;
    end;
    call test.check(v_ok, 'Bei einem abgeschalteten Betrieb zieht keine Karte um');
    reset role;
    update public.tenants set is_active = true where id = v_fremd;

    -- ---------------------------------------------- Aufräumen
    delete from public.stamp_proofs where tenant_id in (v_tenant, v_fremd);
    delete from public.vouchers      where tenant_id in (v_tenant, v_fremd);
    delete from public.stamps        where tenant_id in (v_tenant, v_fremd);
    delete from public.memberships   where tenant_id in (v_tenant, v_fremd);
    delete from public.tenants       where id in (v_tenant, v_fremd);
    delete from auth.users           where id in (v_alt, v_neu, v_dritt);

    raise notice '--- Kartenschlüssel bestanden ---';
end
$$;
