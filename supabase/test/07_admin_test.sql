-- Tests für die Verwaltung durch den Betrieb.
--
-- Die Kassenseite darf nichts ändern, die Betriebsleitung schon - aber auch
-- sie nur an den Stellen, die ihr gehören. Geprüft wird beides: dass der
-- Inhaber durchkommt und dass sonst niemand durchkommt.
--
-- Eigener Betrieb statt Demo-Betrieb: Diese Tests ändern Kartenregeln, und
-- ein veränderter Demo-Betrieb würde spätere Läufe stillschweigend anders
-- aussehen lassen.

\set ON_ERROR_STOP on

-- ============================ Stammdaten: wer darf, und wer nicht
do $$
declare
    v_tenant  uuid;
    v_fremd   uuid;
    v_chef    uuid;
    v_kasse   uuid;
    v_kunde   uuid;
    v_name    text;
    v_zahl    int;
    v_ok      boolean;
begin
    insert into public.tenants (id, slug, name)
    values (gen_random_uuid(), 'verwaltung-test', 'Testbetrieb')
    returning id into v_tenant;
    insert into public.tenants (id, slug, name)
    values (gen_random_uuid(), 'verwaltung-fremd', 'Fremdbetrieb')
    returning id into v_fremd;

    insert into auth.users default values returning id into v_chef;
    insert into auth.users default values returning id into v_kasse;
    insert into auth.users default values returning id into v_kunde;

    insert into public.tenant_staff (user_id, tenant_id, role)
    values (v_chef, v_tenant, 'owner');
    -- Ohne Rolle: Die Vorgabe muss "staff" sein, sonst waere jede bestehende
    -- Kassenkraft nach dem Upgrade ploetzlich Inhaber.
    insert into public.tenant_staff (user_id, tenant_id)
    values (v_kasse, v_tenant);
    insert into public.memberships (user_id, tenant_id) values (v_kunde, v_tenant);

    select role into v_name from public.tenant_staff
     where user_id = v_kasse and tenant_id = v_tenant;
    call test.check(v_name = 'staff', 'Personal ohne Rollenangabe bleibt Kasse');

    -- ------------------------------------------ Die Kasse darf nichts ändern
    call auth.become(v_kasse);
    call test.check(public.is_staff_of(v_tenant), 'Die Kasse ist Personal');
    call test.check(not public.is_owner_of(v_tenant), 'Die Kasse ist nicht Inhaber');
    begin
        perform * from public.owner_update_tenant(
            v_tenant, 'Gekapert', 'A', 'B', 'C', '#000000', null, 10, 90);
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Die Kasse kann die Stammdaten nicht ändern');

    -- ------------------------------------------ Der Kunde erst recht nicht
    call auth.become(v_kunde);
    begin
        perform * from public.owner_update_tenant(
            v_tenant, 'Gekapert', 'A', 'B', 'C', '#000000', null, 10, 90);
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ein Kunde kann die Stammdaten nicht ändern');

    select name into v_name from public.tenants where id = v_tenant;
    call test.check(v_name = 'Testbetrieb', 'Der Name ist nach den Versuchen unverändert');

    -- ------------------------------------------ Der Inhaber darf
    call auth.become(v_chef);
    call test.check(public.is_owner_of(v_tenant), 'Der Inhaber ist Inhaber');
    -- Wichtig fuer die Kassenseite: Der Inhaber muss dort weiter hineinkommen.
    call test.check(public.is_staff_of(v_tenant), 'Der Inhaber ist auch Personal');

    perform * from public.owner_update_tenant(
        v_tenant, 'Bäckerei Test', 'Brötchen', 'Prämien', 'Heute frisch',
        '#8A2BE2', 'https://example.org/logo.png', 8, 30);

    select name, stamps_per_card into v_name, v_zahl
      from public.tenants where id = v_tenant;
    call test.check(v_name = 'Bäckerei Test', 'Der Inhaber ändert den Namen');
    call test.check(v_zahl = 8, 'Der Inhaber ändert die Kartengröße');

    -- ------------------------------------------ Fremder Betrieb bleibt fremd
    begin
        perform * from public.owner_update_tenant(
            v_fremd, 'Gekapert', 'A', 'B', 'C', null, null, 10, 90);
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ein Inhaber kann keinen fremden Betrieb ändern');

    -- ------------------------------------------ Unsinn wird abgelehnt
    begin
        perform * from public.owner_update_tenant(
            v_tenant, 'X', 'A', 'B', 'C', 'blau', null, 10, 90);
        v_ok := false;
    exception when data_exception then
        v_ok := true;
    end;
    call test.check(v_ok, 'Eine Farbe ohne Hex-Schreibweise wird abgelehnt');

    begin
        perform * from public.owner_update_tenant(
            v_tenant, 'X', 'A', 'B', 'C', null, null, 0, 90);
        v_ok := false;
    exception when data_exception then
        v_ok := true;
    end;
    call test.check(v_ok, 'Null Stempel pro Karte werden abgelehnt');

    begin
        perform * from public.owner_update_tenant(
            v_tenant, '   ', 'A', 'B', 'C', null, null, 10, 90);
        v_ok := false;
    exception when data_exception then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ein leerer Name wird abgelehnt');

    select name, stamps_per_card into v_name, v_zahl
      from public.tenants where id = v_tenant;
    call test.check(v_name = 'Bäckerei Test' and v_zahl = 8,
                    'Nach den abgelehnten Versuchen steht der letzte gute Stand');

    raise notice '--- Stammdaten-Tests bestanden ---';
end
$$;

-- ============================ Angebote: Policies statt Funktion
do $$
declare
    v_tenant  uuid;
    v_fremd   uuid;
    v_chef    uuid;
    v_kasse   uuid;
    v_angebot uuid;
    v_zeilen  int;
begin
    select id into v_tenant from public.tenants where slug = 'verwaltung-test';
    select id into v_fremd  from public.tenants where slug = 'verwaltung-fremd';
    select s.user_id into v_chef  from public.tenant_staff s
     where s.tenant_id = v_tenant and s.role = 'owner';
    select s.user_id into v_kasse from public.tenant_staff s
     where s.tenant_id = v_tenant and s.role = 'staff';

    -- ------------------------------------------ Der Inhaber pflegt Angebote
    call auth.become(v_chef);
    set local role authenticated;

    insert into public.offers (tenant_id, title, description)
    values (v_tenant, 'Dinkel-Kracher', 'Heute frisch')
    returning id into v_angebot;
    call test.check(v_angebot is not null, 'Der Inhaber legt ein Angebot an');

    update public.offers set title = 'Dinkel-Kracher XL' where id = v_angebot;
    select count(*) into v_zeilen from public.offers
     where id = v_angebot and title = 'Dinkel-Kracher XL';
    call test.check(v_zeilen = 1, 'Der Inhaber ändert ein Angebot');

    -- Postgres prueft beim update auch die neue Zeile gegen die Policy.
    -- Der Test haelt das fest, damit ein spaeter ergaenztes, laxeres
    -- `with check` nicht unbemerkt ein Schlupfloch aufmacht.
    begin
        update public.offers set tenant_id = v_fremd where id = v_angebot;
        select count(*) into v_zeilen from public.offers
         where id = v_angebot and tenant_id = v_fremd;
    exception when insufficient_privilege then
        v_zeilen := 0;
    end;
    call test.check(v_zeilen = 0, 'Ein Angebot lässt sich nicht umhängen');

    begin
        insert into public.offers (tenant_id, title) values (v_fremd, 'Fremd');
        select count(*) into v_zeilen from public.offers where tenant_id = v_fremd;
    exception when insufficient_privilege then
        v_zeilen := 0;
    end;
    call test.check(v_zeilen = 0, 'Ein Inhaber legt keine fremden Angebote an');

    reset role;

    -- ------------------------------------------ Die Kasse pflegt nicht
    call auth.become(v_kasse);
    set local role authenticated;

    -- Ohne insert-Policy trifft das Statement keine Zeile bzw. wirft;
    -- beides ist ein Bestehen, nur ein angelegtes Angebot waere ein Fehler.
    begin
        insert into public.offers (tenant_id, title) values (v_tenant, 'Von der Kasse');
    exception when insufficient_privilege then
        null;
    end;
    select count(*) into v_zeilen from public.offers where title = 'Von der Kasse';
    call test.check(v_zeilen = 0, 'Die Kasse legt kein Angebot an');

    begin
        update public.offers set title = 'Von der Kasse geändert' where id = v_angebot;
    exception when insufficient_privilege then
        null;
    end;
    select count(*) into v_zeilen from public.offers
     where id = v_angebot and title = 'Dinkel-Kracher XL';
    call test.check(v_zeilen = 1, 'Die Kasse ändert kein Angebot');

    begin
        delete from public.offers where id = v_angebot;
    exception when insufficient_privilege then
        null;
    end;
    select count(*) into v_zeilen from public.offers where id = v_angebot;
    call test.check(v_zeilen = 1, 'Die Kasse löscht kein Angebot');

    reset role;

    -- ------------------------------------------ Löschen darf der Inhaber
    call auth.become(v_chef);
    set local role authenticated;
    delete from public.offers where id = v_angebot;
    select count(*) into v_zeilen from public.offers where id = v_angebot;
    reset role;
    call test.check(v_zeilen = 0, 'Der Inhaber löscht ein Angebot');

    raise notice '--- Angebots-Tests bestanden ---';
end
$$;

-- ============================ Einlöse-Code aus der Verwaltung heraus
do $$
declare
    v_tenant  uuid;
    v_chef    uuid;
    v_kasse   uuid;
    v_kunde   uuid;
    v_voucher uuid;
    v_zeilen  int;
    v_ok      boolean;
begin
    select id into v_tenant from public.tenants where slug = 'verwaltung-test';
    select s.user_id into v_chef  from public.tenant_staff s
     where s.tenant_id = v_tenant and s.role = 'owner';
    select s.user_id into v_kasse from public.tenant_staff s
     where s.tenant_id = v_tenant and s.role = 'staff';

    -- ------------------------------------------ Die Kasse setzt keinen Code
    call auth.become(v_kasse);
    begin
        perform public.owner_set_redeem_code(v_tenant, 'geheim-4711');
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Die Kasse setzt keinen Einlöse-Code');

    call auth.become(v_chef);

    -- ------------------------------------------ Zu kurz wird abgelehnt
    begin
        perform public.owner_set_redeem_code(v_tenant, '1234');
        v_ok := false;
    exception when data_exception then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ein vierstelliger Code wird abgelehnt');

    select count(*) into v_zeilen from public.tenant_secrets where tenant_id = v_tenant;
    call test.check(v_zeilen = 0, 'Nach dem abgelehnten Versuch steht kein Code');

    -- ------------------------------------------ Setzen wirkt bis ins Einlösen
    perform public.owner_set_redeem_code(v_tenant, 'sonntagsbroetchen');
    select requires_redeem_code into v_ok from public.tenants where id = v_tenant;
    call test.check(v_ok, 'Mit dem Code wird die Code-Pflicht eingeschaltet');

    insert into auth.users default values returning id into v_kunde;
    insert into public.memberships (user_id, tenant_id) values (v_kunde, v_tenant);
    call test.fill_card(v_kunde, v_tenant, 'admin', v_voucher);

    call auth.become(v_kunde);
    begin
        perform * from public.redeem_voucher(v_voucher, 'falsch-genug');
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Der falsche Code löst nicht ein');

    perform * from public.redeem_voucher(v_voucher, 'sonntagsbroetchen');
    select is_redeemed into v_ok from public.vouchers where id = v_voucher;
    call test.check(v_ok, 'Der in der Verwaltung gesetzte Code löst ein');

    -- ------------------------------------------ Zurücknehmen räumt beides ab
    call auth.become(v_chef);
    perform public.owner_clear_redeem_code(v_tenant);
    select requires_redeem_code into v_ok from public.tenants where id = v_tenant;
    call test.check(not v_ok, 'Ohne Code ist die Code-Pflicht wieder aus');
    select count(*) into v_zeilen from public.tenant_secrets where tenant_id = v_tenant;
    call test.check(v_zeilen = 0, 'Der Hash ist mit weg');

    raise notice '--- Einlöse-Code aus der Verwaltung bestanden ---';
end
$$;
