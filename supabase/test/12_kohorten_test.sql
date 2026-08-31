-- ============================================================================
-- Kohortenzahlen
--
-- Die Zahlen beantworten "lohnt sich meine Karte", ohne einen Kunden zu
-- kennen. Genau deshalb muessen sie stimmen: Wenn niemand nachrechnen kann,
-- faellt ein Fehler nie auf.
--
-- Zwei Quellen mit unterschiedlicher Reichweite werden hier gegeneinander
-- geprueft. `stamps` haelt nur die laufende Karte - eine volle Karte wird zum
-- Gutschein und ihre Stempel verschwinden. `stamp_proofs` haelt die Historie,
-- aber nur so weit zurueck, wie die Aufbewahrung des Betriebs reicht. Kunde E
-- steht genau fuer diesen Fall: Praemie erreicht, Nachweise weg.
-- ============================================================================
do $$
declare
    v_tenant uuid;
    v_fremd  uuid;
    a uuid; b uuid; c uuid; d uuid; e uuid; f uuid;
    v record;
    v_ok boolean;
begin
    -- Neun Stempel je Karte: Die Drittel liegen dann auf ganzen Zahlen.
    -- Unteres Drittel 1-2, mittleres 3-5, oberes 6-8.
    insert into public.tenants (slug, name, stamps_per_card)
         values ('kohorten-test', 'Kohortenbetrieb', 9) returning id into v_tenant;
    insert into public.tenants (slug, name, stamps_per_card)
         values ('kohorten-fremd', 'Nachbarbetrieb', 9) returning id into v_fremd;

    insert into auth.users default values returning id into a;
    insert into auth.users default values returning id into b;
    insert into auth.users default values returning id into c;
    insert into auth.users default values returning id into d;
    insert into auth.users default values returning id into e;
    insert into auth.users default values returning id into f;

    insert into public.memberships (user_id, tenant_id)
         values (a, v_tenant), (b, v_tenant), (c, v_tenant),
                (d, v_tenant), (e, v_tenant);
    -- Der Fremdbetrieb bekommt eine Karte, damit Vermischung auffiele.
    insert into public.memberships (user_id, tenant_id) values (f, v_fremd);

    -- A: angelegt, nie gekauft. Der Fall, den ein Betrieb am wenigsten sieht
    --    und am meisten interessieren sollte.
    -- B: zwei Stempel an einem Tag -> unteres Drittel, keine Wiederkehr.
    insert into public.stamps (id, user_id, tenant_id)
         select gen_random_uuid(), b, v_tenant from generate_series(1, 2);
    insert into public.stamp_proofs (tenant_id, user_id, proof_ref, created_at)
         values (v_tenant, b, 'b-1', now()), (v_tenant, b, 'b-2', now());

    -- C: vier Stempel an zwei Tagen -> mittleres Drittel, Wiederkehr.
    insert into public.stamps (id, user_id, tenant_id)
         select gen_random_uuid(), c, v_tenant from generate_series(1, 4);
    insert into public.stamp_proofs (tenant_id, user_id, proof_ref, created_at)
         values (v_tenant, c, 'c-1', now()),
                (v_tenant, c, 'c-2', now() - interval '3 days');

    -- D: sieben Stempel an drei Tagen, alle aelter als 30 Tage -> oberes
    --    Drittel, Wiederkehr, aber nicht mehr aktiv. Dazu ein verfallener
    --    Gutschein aus einer frueheren vollen Karte.
    insert into public.stamps (id, user_id, tenant_id)
         select gen_random_uuid(), d, v_tenant from generate_series(1, 7);
    insert into public.stamp_proofs (tenant_id, user_id, proof_ref, created_at)
         values (v_tenant, d, 'd-1', now() - interval '40 days'),
                (v_tenant, d, 'd-2', now() - interval '45 days'),
                (v_tenant, d, 'd-3', now() - interval '50 days');
    insert into public.vouchers (id, creation_date, expires_at, is_redeemed, user_id, tenant_id)
         values (gen_random_uuid(),
                 (extract(epoch from now() - interval '60 days') * 1000)::int8,
                 (extract(epoch from now() - interval '30 days') * 1000)::int8,
                 false, d, v_tenant);

    -- E: keine laufenden Stempel, keine Nachweise mehr - aber ein offener
    --    Gutschein. So sieht eine Karte aus, deren Nachweise die Aufbewahrung
    --    aufgeraeumt hat.
    insert into public.vouchers (id, creation_date, expires_at, is_redeemed, user_id, tenant_id)
         values (gen_random_uuid(),
                 (extract(epoch from now()) * 1000)::int8,
                 (extract(epoch from now() + interval '30 days') * 1000)::int8,
                 false, e, v_tenant);

    select * into v from public.pilot_cohorts where tenant_id = v_tenant;

    call test.check(v.cards = 5, 'Fuenf Karten gezaehlt');

    -- Fuellstand: A und E ohne laufende Stempel, B unten, C mitte, D oben.
    call test.check(v.fill_none = 2, 'Zwei Karten ohne laufenden Stempel');
    call test.check(v.fill_low  = 1, 'Eine Karte im unteren Drittel');
    call test.check(v.fill_mid  = 1, 'Eine Karte im mittleren Drittel');
    call test.check(v.fill_high = 1, 'Eine Karte im oberen Drittel');
    call test.check(v.fill_none + v.fill_low + v.fill_mid + v.fill_high = v.cards,
                    'Die Drittel decken alle Karten ab, ohne Ueberschneidung');

    -- Historie: nur B, C und D haben Nachweise im Fenster.
    call test.check(v.cards_with_stamp = 3, 'Drei Karten mit Stempel im Nachweisfenster');
    call test.check(v.cards_returning  = 2, 'Zwei Karten an mindestens zwei Tagen');
    call test.check(v.return_rate_percent = 66.7, 'Wiederkehrquote 66,7 Prozent');
    call test.check(v.cards_active_30d = 2, 'Zwei Karten in den letzten 30 Tagen aktiv');

    -- Praemien: D und E haben je einen Gutschein - unabhaengig davon, ob ihre
    -- Nachweise noch da sind. Genau das soll die Quelle vouchers leisten.
    call test.check(v.cards_completed = 2, 'Zwei Karten haben je eine Praemie erreicht');
    call test.check(v.completion_rate_percent = 40.0, 'Durchlaufquote 40 Prozent');
    call test.check(v.vouchers_open    = 1, 'Ein offener Gutschein');
    call test.check(v.vouchers_expired = 1, 'Ein verfallener Gutschein');

    -- Das Fenster faehrt mit, damit die Anzeige keine Genauigkeit vortaeuscht.
    call test.check(v.history_days = 90, 'Die Fensterbreite steht in der Zeile');
    call test.check(v.stamps_per_card = 9, 'Die Kartengroesse steht in der Zeile');

    -- Der Nachbarbetrieb zaehlt getrennt.
    select * into v from public.pilot_cohorts where tenant_id = v_fremd;
    call test.check(v.cards = 1 and v.fill_none = 1 and v.cards_with_stamp = 0,
                    'Der Nachbarbetrieb wird nicht mitgezaehlt');

    raise notice '--- Kohortenzahlen bestanden ---';
end;
$$;

-- ---------------------------------------------------------------------------
-- Zugriff: Die Sicht gehoert dem Betreiber. Das Personal kommt nur ueber die
-- Funktion daran, und nur an den eigenen Betrieb.
-- ---------------------------------------------------------------------------
do $$
declare
    v_tenant uuid;
    v_fremd  uuid;
    v_chef   uuid;
    v_zeilen int;
    v_ok     boolean;
begin
    select id into v_tenant from public.tenants where slug = 'kohorten-test';
    select id into v_fremd  from public.tenants where slug = 'kohorten-fremd';

    insert into auth.users default values returning id into v_chef;
    insert into public.tenant_staff (user_id, tenant_id, role)
         values (v_chef, v_tenant, 'owner');

    call auth.become(v_chef);
    set local role authenticated;

    -- Direkt lesen darf niemand ausser dem Betreiber.
    begin
        perform 1 from public.pilot_cohorts;
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Die Sicht ist fuer authenticated gesperrt');

    select count(*) into v_zeilen from public.staff_pilot_cohorts();
    call test.check(v_zeilen = 1, 'Das Personal sieht genau seinen Betrieb');

    select count(*) into v_zeilen
      from public.staff_pilot_cohorts() where tenant_id = v_fremd;
    call test.check(v_zeilen = 0, 'Der fremde Betrieb bleibt unsichtbar');

    reset role;
    raise notice '--- Zugriff auf die Kohortenzahlen bestanden ---';
end;
$$;
