-- ============================================================================
-- Angebote nach Kartenstand
--
-- Segmentierung ohne Person: Der Server kennt den Stand der Karte, nicht den
-- Menschen dahinter. Geprueft wird beides - dass das Angebot bei falschem
-- Stand gar nicht erst in der Antwort steht, und dass es sich auch dann nicht
-- einloesen laesst, wenn jemand die ID kennt.
-- ============================================================================
do $$
declare
    v_tenant uuid;
    v_kunde  uuid;
    v_immer  uuid;
    v_ab7    uuid;
    v_bis2   uuid;
    v_band   uuid;
    v_sicht  uuid[];
    v_ok     boolean;
begin
    insert into public.tenants (slug, name, stamps_per_card)
         values ('stand-test', 'Standbetrieb', 10) returning id into v_tenant;
    insert into auth.users default values returning id into v_kunde;
    insert into public.memberships (user_id, tenant_id) values (v_kunde, v_tenant);

    insert into public.offers (tenant_id, title, is_redeemable)
         values (v_tenant, 'Fuer alle', true) returning id into v_immer;
    insert into public.offers (tenant_id, title, is_redeemable, min_stamps)
         values (v_tenant, 'Fast voll', true, 7) returning id into v_ab7;
    insert into public.offers (tenant_id, title, is_redeemable, max_stamps)
         values (v_tenant, 'Frisch dabei', true, 2) returning id into v_bis2;
    insert into public.offers (tenant_id, title, is_redeemable, min_stamps, max_stamps)
         values (v_tenant, 'Mittendrin', true, 3, 5) returning id into v_band;

    -- ---------------------------------------------------------- leere Karte
    call auth.become(v_kunde);
    set local role authenticated;

    select array_agg(id order by title) into v_sicht
      from public.offers where tenant_id = v_tenant;
    call test.check(v_sicht @> array[v_immer, v_bis2] and array_length(v_sicht, 1) = 2,
                    'Leere Karte sieht das offene und das Einsteiger-Angebot');
    call test.check(not (v_sicht @> array[v_ab7]), 'Und nicht das fuer fast volle Karten');

    -- Einloesen scheitert am Stand, nicht erst an der Sichtbarkeit.
    begin
        perform public.redeem_offer(v_ab7);
        v_ok := false;
    exception when others then
        v_ok := sqlerrm like '%outside offer range%' or sqlerrm like '%not redeemable%';
    end;
    call test.check(v_ok, 'Mit leerer Karte laesst sich das Angebot auch nicht einloesen');

    -- ------------------------------------------------------- vier Stempel
    reset role;
    insert into public.stamps (id, user_id, tenant_id)
         select gen_random_uuid(), v_kunde, v_tenant from generate_series(1, 4);
    call auth.become(v_kunde);
    set local role authenticated;

    select array_agg(id order by title) into v_sicht
      from public.offers where tenant_id = v_tenant;
    call test.check(v_sicht @> array[v_immer, v_band] and array_length(v_sicht, 1) = 2,
                    'Bei vier Stempeln erscheint das Band-Angebot');
    call test.check(not (v_sicht @> array[v_bis2]),
                    'Das Einsteiger-Angebot ist verschwunden');

    -- ------------------------------------------------------- acht Stempel
    reset role;
    insert into public.stamps (id, user_id, tenant_id)
         select gen_random_uuid(), v_kunde, v_tenant from generate_series(1, 4);
    call auth.become(v_kunde);
    set local role authenticated;

    select array_agg(id order by title) into v_sicht
      from public.offers where tenant_id = v_tenant;
    call test.check(v_sicht @> array[v_immer, v_ab7] and array_length(v_sicht, 1) = 2,
                    'Bei acht Stempeln erscheint das Angebot fuer fast volle Karten');

    perform public.redeem_offer(v_ab7);
    -- Unterabfragen sind in CALL-Argumenten nicht erlaubt; erst rechnen.
    select exists (select 1 from public.offer_redemptions
                    where offer_id = v_ab7 and user_id = v_kunde) into v_ok;
    call test.check(v_ok, 'Und jetzt laesst es sich einloesen');

    -- Das Band liegt nun hinter dem Kunden.
    begin
        perform public.redeem_offer(v_band);
        v_ok := false;
    exception when others then
        v_ok := sqlerrm like '%outside offer range%';
    end;
    call test.check(v_ok, 'Ein ueberschrittenes Band laesst sich nicht mehr einloesen');

    reset role;
    raise notice '--- Angebote nach Kartenstand bestanden ---';
end;
$$;

-- ---------------------------------------------------------------------------
-- Der Betrieb sieht seine Angebote unabhaengig vom Kartenstand - sonst
-- verschwaende ein Angebot aus der eigenen Verwaltung, sobald es nicht mehr
-- auf die eigene Karte passt.
-- ---------------------------------------------------------------------------
do $$
declare
    v_tenant uuid;
    v_chef   uuid;
    v_anzahl int;
    v_ok     boolean;
begin
    select id into v_tenant from public.tenants where slug = 'stand-test';
    insert into auth.users default values returning id into v_chef;
    insert into public.tenant_staff (user_id, tenant_id, role) values (v_chef, v_tenant, 'owner');

    call auth.become(v_chef);
    set local role authenticated;
    select count(*) into v_anzahl from public.offers where tenant_id = v_tenant;
    call test.check(v_anzahl = 4, 'Der Betrieb sieht alle vier Angebote');
    reset role;

    -- Eine Obergrenze unter der Untergrenze ist keine Einschraenkung, sondern
    -- ein Angebot, das nie erscheint. Das faengt die Datenbank ab.
    begin
        insert into public.offers (tenant_id, title, min_stamps, max_stamps)
             values (v_tenant, 'Unmoeglich', 8, 3);
        v_ok := false;
    exception when check_violation then
        v_ok := true;
    end;
    call test.check(v_ok, 'Obergrenze unter der Untergrenze wird abgewiesen');

    raise notice '--- Sicht des Betriebs bestanden ---';
end;
$$;
