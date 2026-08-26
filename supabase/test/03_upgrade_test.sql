-- Prüft das Upgrade eines Projekts, das vor der Mandantenfähigkeit
-- eingerichtet wurde. Läuft gegen eine Datenbank, in der zuerst
-- fixtures_legacy_schema.sql und danach schema.sql eingespielt wurde.

\set ON_ERROR_STOP on

do $$
declare
    v_tenant uuid := '00000000-0000-4000-8000-000000000001';
    v_count  int;
    v_notnull boolean;
begin
    -- ------------------------------------------------ Spalte nachgezogen
    select count(*) into v_count
      from information_schema.columns
     where table_schema = 'public' and table_name = 'stamps' and column_name = 'tenant_id';
    call test.check(v_count = 1, 'stamps hat nach dem Upgrade eine tenant_id');

    select count(*) into v_count
      from information_schema.columns
     where table_schema = 'public' and table_name = 'vouchers' and column_name = 'tenant_id';
    call test.check(v_count = 1, 'vouchers hat nach dem Upgrade eine tenant_id');

    -- ------------------------------------------------ Altdaten zugeordnet
    select count(*) into v_count from public.stamps where tenant_id = v_tenant;
    call test.check(v_count > 0, 'Bestehende Stempel sind dem Betrieb zugeordnet');

    select count(*) into v_count from public.stamps where tenant_id is null;
    call test.check(v_count = 0, 'Kein Stempel bleibt ohne Betrieb');

    -- ------------------------------------------------ Constraint gesetzt
    select attnotnull into v_notnull
      from pg_attribute
     where attrelid = 'public.stamps'::regclass and attname = 'tenant_id';
    call test.check(v_notnull, 'stamps.tenant_id ist not null');

    select count(*) into v_count from pg_constraint
     where conrelid = 'public.stamps'::regclass and contype = 'f'
       and conname = 'stamps_tenant_id_fkey';
    call test.check(v_count = 1, 'Fremdschlüssel auf tenants ist gesetzt');

    -- ------------------------- Die alten Schreibrechte sind verschwunden
    select count(*) into v_count from pg_policies
     where policyname in ('stamps_insert_own', 'vouchers_insert_own', 'stamps_delete_own');
    call test.check(
        v_count = 0,
        'Die alten insert-Policies sind entfernt - die App kann nicht mehr selbst schreiben'
    );

    raise notice '--- Upgrade-Tests bestanden ---';
end
$$;
