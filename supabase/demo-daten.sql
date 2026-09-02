-- ============================================================================
-- Demodaten fuer den Schauzugang
--
-- Legt fuer einen Betrieb plausible Karten an: verschiedene Fuellstaende,
-- Stempel ueber mehrere Wochen, eingeloeste, offene und verfallene Praemien.
-- Ohne solche Daten zeigt die Demo lauter Nullen - und die Zahlen sind das
-- Argument, das die Wettbewerber ohne Kundenkonto nicht liefern koennen.
--
-- NICHT Teil von schema.sql und wird nirgends automatisch aufgerufen. Wer das
-- hier auf einem echten Betrieb laufen laesst, mischt erfundene Kunden unter
-- die richtigen.
--
-- Voraussetzungen:
--   1. schema.sql ist eingespielt (die Rolle `demo` muss erlaubt sein).
--   2. Das Demo-Konto existiert in Supabase unter der unten genannten
--      Adresse - anlegen ueber Authentication > Users > Add user.
--
-- Wiederholbar: Ein zweiter Lauf raeumt die erfundenen Kunden des vorigen
-- weg und legt sie neu an. Echte Kunden bleiben unberuehrt; erkennbar sind
-- die erfundenen an einer Markierung in den Metadaten.
-- ============================================================================
do $$
declare
    -- ---- anpassen -------------------------------------------------------
    c_slug  constant text := 'baeckerei-mustermann';
    c_mail  constant text := 'demo@treuebiss.de';
    c_karten constant int := 24;
    -- ---------------------------------------------------------------------
    v_tenant   uuid;
    v_demo     uuid;
    v_gross    int;
    v_user     uuid;
    v_stempel  int;
    v_tage     int;
    v_letzter  int;
    v_weg      int;
    v_leer     int;
    i          int;
    j          int;
    v_modern   boolean;
begin
    select id, stamps_per_card into v_tenant, v_gross
      from public.tenants where slug = c_slug;
    if v_tenant is null then
        raise exception 'Betrieb % gibt es nicht.', c_slug;
    end if;

    select id into v_demo from auth.users where email = c_mail;
    if v_demo is null then
        raise exception
            'Das Demo-Konto % fehlt. Erst in Supabase anlegen (Authentication > Users > Add user).',
            c_mail;
    end if;

    -- Zugang setzen. `demo` darf sehen und nichts aendern; abgesichert ist
    -- das in is_demo_of, nicht hier.
    insert into public.tenant_staff (user_id, tenant_id, role)
         values (v_demo, v_tenant, 'demo')
    on conflict (user_id, tenant_id) do update set role = 'demo';

    -- Erfundene Kunden des letzten Laufs entfernen. Der Rest haengt an
    -- `on delete cascade`, es bleibt also nichts liegen.
    delete from auth.users
     where raw_user_meta_data ->> 'treuebiss_demo' = c_slug;
    get diagnostics v_weg = row_count;

    /*
     * Nie benutzte Karten aus Tests wegraeumen.
     *
     * Beim Erproben entstehen anonyme Karten, die nie einen Stempel bekommen
     * haben. In der Demo zaehlen sie als Teilnehmer mit und druecken die
     * Durchlaufquote - der Betrieb saehe eine Zahl, die nichts bedeutet.
     *
     * Entfernt wird nur die *Mitgliedschaft* und nur bei diesem Betrieb, und
     * nur wenn an ihr nachweislich nichts haengt: kein Stempel, kein
     * Nachweis, kein Gutschein, keine Coupon-Einloesung. Das Konto selbst
     * bleibt - es koennte an einem anderen Betrieb eine echte Karte haben.
     */
    delete from public.memberships m
     where m.tenant_id = v_tenant
       and not exists (select 1 from public.stamps s
                        where s.user_id = m.user_id and s.tenant_id = m.tenant_id)
       and not exists (select 1 from public.stamp_proofs p
                        where p.user_id = m.user_id and p.tenant_id = m.tenant_id)
       and not exists (select 1 from public.vouchers v
                        where v.user_id = m.user_id and v.tenant_id = m.tenant_id)
       and not exists (select 1 from public.offer_redemptions o
                        where o.user_id = m.user_id and o.tenant_id = m.tenant_id);
    get diagnostics v_leer = row_count;

    -- Supabase fuehrt in auth.users mehr Spalten als die lokale Testablage.
    -- Beide Wege stehen hier, damit sich das Skript auch gegen die Suite
    -- pruefen laesst statt nur gegen die Produktion.
    select exists (
        select 1 from information_schema.columns
         where table_schema = 'auth' and table_name = 'users'
           and column_name = 'is_anonymous'
    ) into v_modern;

    for i in 1 .. c_karten loop
        if v_modern then
            insert into auth.users (
                instance_id, id, aud, role, email_confirmed_at,
                created_at, updated_at, raw_app_meta_data, raw_user_meta_data,
                is_anonymous)
            values (
                '00000000-0000-0000-0000-000000000000', gen_random_uuid(),
                'authenticated', 'authenticated', now(),
                now(), now(), '{"provider":"anonymous","providers":["anonymous"]}',
                jsonb_build_object('treuebiss_demo', c_slug), true)
            returning id into v_user;
        else
            insert into auth.users default values returning id into v_user;
            update auth.users
               set raw_user_meta_data = jsonb_build_object('treuebiss_demo', c_slug)
             where id = v_user;
        end if;

        insert into public.memberships (user_id, tenant_id) values (v_user, v_tenant);

        /*
         * Die Verteilung ist der Punkt. Eine Demo, in der alle Karten gleich
         * voll sind, zeigt keinen Fuellstand - und genau der beantwortet
         * "wo bleiben meine Kunden haengen".
         *
         *   jede 6. Karte: angelegt, nie gekauft
         *   sonst:         1 bis stamps_per_card-1 Stempel, breit gestreut
         */
        v_stempel := case when i % 6 = 0 then 0
                          else 1 + ((i * 7) % greatest(v_gross - 1, 1)) end;
        v_tage    := least(v_stempel, 1 + (i % 5));
        v_letzter := case when i % 5 = 0 then 40 + (i % 20) else i % 25 end;

        for j in 1 .. v_stempel loop
            insert into public.stamps (id, user_id, tenant_id)
                 values (gen_random_uuid(), v_user, v_tenant);
            insert into public.stamp_proofs
                   (tenant_id, user_id, proof_ref, source, created_at, amount_cents)
                 values (v_tenant, v_user,
                         -- Gehasht wie ueberall sonst; sonst legt jeder
                         -- Lauf wieder Klartext an, den das Schema beim
                         -- naechsten Einspielen umschreiben muesste.
                         encode(extensions.digest('demo-' || i || '-' || j,
                                                  'sha256'), 'hex'), 'demo',
                         now() - make_interval(days => v_letzter + (j % greatest(v_tage,1)) * 3),
                         250 + ((i * j * 37) % 900));
        end loop;

        -- Jede dritte Karte war schon einmal voll. Davon ist ein Teil
        -- eingeloest, einer offen, einer verfallen - sonst stuende die
        -- Einloesequote bei 100 Prozent und saehe erfunden aus.
        if i % 3 = 0 then
            insert into public.vouchers
                   (id, creation_date, expires_at, is_redeemed, redeemed_at, user_id, tenant_id)
                 values (
                    gen_random_uuid(),
                    (extract(epoch from now() - make_interval(days => 30 + i)) * 1000)::int8,
                    (extract(epoch from now() + make_interval(days => case when i % 9 = 0 then -5 else 60 end)) * 1000)::int8,
                    i % 9 <> 0 and i % 2 = 0,
                    case when i % 9 <> 0 and i % 2 = 0
                         then now() - make_interval(days => 5 + (i % 10)) end,
                    v_user, v_tenant);
        end if;
    end loop;

    raise notice 'Demo eingerichtet: % Karten angelegt, % aus dem vorigen Lauf entfernt,
        % nie benutzte Karte(n) aufgeraeumt.', c_karten, v_weg, v_leer;
    raise notice 'Zugang: % mit der Rolle demo auf %.', c_mail, c_slug;
end;
$$;
