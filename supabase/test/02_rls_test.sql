-- Tests für die Row Level Security. Prüfen das, worauf sich die App verlässt:
-- Die App darf Stempel und Gutscheine nicht selbst anlegen, und sie sieht
-- ausschließlich ihre eigenen Zeilen.

\set ON_ERROR_STOP on

do $$
declare
    v_tenant uuid := '00000000-0000-4000-8000-000000000001';
    v_alice  uuid;
    v_bob    uuid;
    v_count  int;
    v_ok     boolean;
begin
    insert into auth.users default values returning id into v_alice;
    insert into auth.users default values returning id into v_bob;
    insert into public.memberships (user_id, tenant_id) values (v_alice, v_tenant);
    insert into public.memberships (user_id, tenant_id) values (v_bob, v_tenant);

    -- Ab hier als eingeschraenkte Rolle arbeiten, sonst greift RLS nicht:
    -- Der Tabelleneigentuemer umgeht sie grundsaetzlich.
    set local role authenticated;
    call auth.become(v_alice);

    -- ------------------------------ Direktes Einfuegen muss verboten sein
    begin
        insert into public.stamps (id, user_id, tenant_id)
        values (gen_random_uuid(), v_alice, v_tenant);
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Die App kann keinen Stempel direkt einfügen');

    begin
        insert into public.vouchers (id, creation_date, expires_at, user_id, tenant_id)
        values (gen_random_uuid(), 0, 1, v_alice, v_tenant);
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Die App kann keinen Gutschein direkt einfügen');

    -- ------------------------------ Ueber die Funktion geht es dagegen
    perform * from public.issue_stamp(v_tenant, 'rls-bon-alice');
    select count(*) into v_count from public.stamps;
    call test.check(v_count = 1, 'Über issue_stamp entsteht der Stempel');

    -- ------------------------------ Fremde Zeilen bleiben unsichtbar
    reset role;
    call auth.become(v_bob);
    set local role authenticated;
    select count(*) into v_count from public.stamps;
    call test.check(v_count = 0, 'Ein anderer Nutzer sieht Alices Stempel nicht');

    /*
     * Mitgliedschaft: gar nicht mehr aus dem Browser.
     *
     * Hier stand bis zum 02.09.2026, dass ein upsert mit DO NOTHING
     * durchgehen muss - die App legte die Karte selbst an. Seit
     * activate_card das uebernimmt (und der Willkommensstempel eine
     * Entscheidung daran haengt), schreibt der Browser diese Tabelle nicht
     * mehr, und das Recht dazu ist entzogen.
     *
     * Der Entzug ist Teil des Riegels vom selben Tag: Auf stamps und
     * vouchers standen fremde Policies aus dem Dashboard, die jedem das
     * Eintragen erlaubten. Eine Policy allein reicht nicht - das Recht muss
     * weg. Fuer memberships gilt dasselbe, obwohl dort keine fremde Policy
     * lag: Was der Browser nicht braucht, soll er nicht koennen.
     */
    reset role;
    call auth.become(v_alice);
    set local role authenticated;

    begin
        insert into public.memberships (user_id, tenant_id)
        values (v_alice, v_tenant)
        on conflict (user_id, tenant_id) do nothing;
        v_ok := false;
    exception when others then
        v_ok := true;
    end;
    call test.check(v_ok, 'Der Browser legt keine Mitgliedschaft mehr selbst an');

    -- Ueber activate_card geht es weiterhin - sonst haette kein Kunde je eine
    -- Karte. Die Funktion laeuft als definer und bringt das Recht mit.
    reset role;
    call auth.become(v_bob);
    set local role authenticated;
    perform public.activate_card(v_tenant);
    reset role;
    select count(*) into v_count from public.memberships
     where user_id = v_bob and tenant_id = v_tenant;
    call test.check(v_count = 1, 'Ueber activate_card entsteht sie');

    reset role;
    raise notice '--- RLS-Tests bestanden ---';
end
$$;
