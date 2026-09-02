-- ============================================================================
-- Durchspielen: ein erfundener Betrieb ueber fuenf Wochen
--
-- Kein Test mit Ja/Nein, sondern ein Bericht. Die Testsuite prueft jede
-- Funktion einzeln und beantwortet damit "geht das?". Sie beantwortet nicht
-- "was sieht der Betrieb nach vier Wochen, und reicht ihm das?".
--
-- Genau dort sitzen die Luecken: eine Kennzahl, die es nicht gibt, faellt
-- keinem Test auf. Sie faellt auf, wenn jemand vor der Verwaltung sitzt und
-- eine Frage nicht beantworten kann.
--
-- Der Ablauf wird mit den echten Funktionen gefahren - issue_stamp,
-- redeem_voucher, activate_card, delete_card. Nur die Zeit wird nachtraeglich
-- verschoben: Stempel entstehen mit now() und werden danach auf den
-- gewuenschten Tag zurueckdatiert. Anders liesse sich ein Monat nicht in einer
-- Sekunde spielen, und die Regeln der Funktionen sollen dabei gelten.
-- ============================================================================

\set ON_ERROR_STOP on

create schema if not exists spiel;

-- Ein Beleg-QR nach DSFinV-K, mit fortlaufender Transaktionsnummer.
create or replace function spiel.beleg(p_nr int, p_betrag text default '4.20')
returns text language sql as $$
    select 'V0;KASSE-1;Kassenbeleg-V1;'
        || 'Beleg^' || p_betrag || '_0.00_0.00_0.00_0.00^' || p_betrag || ':Bar;'
        || p_nr::text || ';' || (40000 + p_nr)::text || ';'
        || to_char(now() at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') || ';'
        || to_char(now() at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') || ';'
        || 'ecdsa-plain-SHA384;utcTime;K8zsZ6NjsBzo/…;BBXNYQErM4d9sk9Iy+0T6A4=';
$$;

/*
 * Ein Einkauf an Tag N. Vergibt den Stempel ueber die echte Funktion und
 * datiert Nachweis und Stempel danach zurueck.
 *
 * Die Rueckdatierung ist der einzige Kunstgriff. Sie faelscht keine Regel:
 * Doppelte Belege, Tageslimit und das Vollwerden der Karte entscheidet
 * weiterhin issue_stamp.
 */
create or replace procedure spiel.einkauf(
    p_tenant uuid, p_user uuid, p_tag int, p_nr int
) language plpgsql as $$
declare
    v_zeit timestamptz := date_trunc('day', now()) - make_interval(days => 35 - p_tag)
                          + make_interval(hours => 7 + (p_nr % 9));
    v_stamp uuid;
    v_qr    text;
begin
    -- Den Beleg vor dem Rollenwechsel bauen. Als Argument geschrieben wuerde
    -- er unter `authenticated` ausgewertet, und die Rolle kommt an das
    -- Hilfsschema nicht heran.
    v_qr := spiel.beleg(p_nr);
    call auth.become(p_user);
    set local role authenticated;
    select stamp_id into v_stamp from public.issue_stamp(p_tenant, v_qr);
    reset role;

    update public.stamps set created_at = v_zeit where id = v_stamp;
    update public.stamp_proofs set created_at = v_zeit
     where tenant_id = p_tenant and user_id = p_user
       and created_at > now() - interval '1 minute';
exception when others then
    reset role;
    raise notice '    Einkauf abgelehnt (Tag %, Kunde %): %', p_tag, left(p_user::text, 8), sqlerrm;
end;
$$;

-- Die Zahlen, die die Verwaltung zeigt - in derselben Reihenfolge und mit
-- derselben Beschriftung wie dort.
create or replace procedure spiel.bericht(p_tenant uuid, p_titel text)
language plpgsql as $$
declare z record; k record;
begin
    select * into z from public.pilot_summary where tenant_id = p_tenant;
    select * into k from public.pilot_cohorts where tenant_id = p_tenant;
    raise notice '';
    raise notice '  ── % ──', p_titel;
    raise notice '    Teilnehmer % · Stempel gesamt % · Stempel pro Tag % · Gutscheine %/%',
        z.members, z.stamps_issued, coalesce(z.stamps_per_active_day::text, '–'),
        z.vouchers_redeemed, z.vouchers_created;
    if z.welcome_stamps > 0 then
        raise notice '    davon fuer Kaeufe: %', z.stamps_issued - z.welcome_stamps;
    end if;
    raise notice '    kommen wieder % · erreichen die Praemie % · Praemien eingeloest % · aktiv 30 Tage %',
        coalesce(k.return_rate_percent::text || ' %', '–'),
        coalesce(k.completion_rate_percent::text || ' %', '–'),
        coalesce(z.redemption_rate_percent::text || ' %', '–'),
        k.cards_active_30d;
    raise notice '    Fuellstand  ohne % · unteres % · mittleres % · oberes %  (Summe %, Karten %)',
        k.fill_none, k.fill_low, k.fill_mid, k.fill_high,
        k.fill_none + k.fill_low + k.fill_mid + k.fill_high, k.cards;
    raise notice '    % offen, % verfallen, % geloescht (% in 30 Tagen)',
        k.vouchers_open, k.vouchers_expired, k.cards_deleted, k.cards_deleted_30d;
end;
$$;

-- ============================================================================
-- Landbaeckerei Sonnfeld, fuenf Wochen
-- ============================================================================
do $$
declare
    v_b       uuid;   -- Betrieb
    v_chef    uuid;
    v_kasse   uuid;
    v_kunden  uuid[];
    v_u       uuid;
    v_i       int;
    v_tag     int;
    v_nr      int := 0;
    v_gut     uuid;
    v_angebot_alle uuid;
    v_angebot_fast uuid;
    v_anzahl  int;
    v_voll    int;
    v_tage    numeric;
begin
    raise notice '';
    raise notice '════ Landbaeckerei Sonnfeld ════';

    -- ---------------------------------------------------------- Einrichtung
    insert into public.tenants (slug, name, stamps_per_card, welcome_stamp_enabled,
                                counter_qr_enabled, voucher_validity_days)
         values ('sonnfeld', 'Landbaeckerei Sonnfeld', 10, true, true, 30)
    returning id into v_b;
    insert into public.tenant_secrets (tenant_id, counter_secret)
         values (v_b, encode(extensions.gen_random_bytes(32), 'hex'));

    insert into auth.users default values returning id into v_chef;
    insert into auth.users default values returning id into v_kasse;
    insert into public.tenant_staff (user_id, tenant_id, role)
         values (v_chef, v_b, 'owner'), (v_kasse, v_b, 'staff');

    raise notice '  Betrieb angelegt: 10er Karte, Willkommensstempel an,';
    raise notice '  Tresen-Code an, Gutschein 30 Tage gueltig.';

    -- ------------------------------------------------- Woche 1: Eroeffnung
    -- 14 Kunden scannen den Aufsteller. Jeder bekommt den Willkommensstempel.
    for v_i in 1..14 loop
        insert into auth.users default values returning id into v_u;
        v_kunden := array_append(v_kunden, v_u);
        call auth.become(v_u);
        set local role authenticated;
        perform public.activate_card(v_b);
        reset role;
        -- Die Aktivierung faellt auf den Tag des ersten Besuchs.
        update public.stamps
           set created_at = date_trunc('day', now()) - make_interval(days => 34)
                            + make_interval(hours => 8, mins => v_i * 3)
         where user_id = v_u and tenant_id = v_b;
        update public.stamp_proofs
           set created_at = date_trunc('day', now()) - make_interval(days => 34)
                            + make_interval(hours => 8, mins => v_i * 3)
         where user_id = v_u and tenant_id = v_b;
    end loop;
    call spiel.bericht(v_b, 'Woche 1: Eroeffnung, 14 Karten ausgegeben');

    -- ------------------------------------------- Woche 2 bis 4: der Alltag
    /*
     * Nicht alle kommen gleich oft. Die ersten vier sind Stammkunden und
     * kommen fast taeglich, die naechsten fuenf gelegentlich, die letzten
     * fuenf gar nicht mehr - so sieht ein Kartenstapel nach einem Monat aus.
     */
    for v_tag in 8..33 loop
        for v_i in 1..4 loop            -- Stammkunden
            if (v_tag + v_i) % 2 = 0 then
                v_nr := v_nr + 1;
                call spiel.einkauf(v_b, v_kunden[v_i], v_tag, v_nr);
            end if;
        end loop;
        for v_i in 5..9 loop            -- Gelegentliche
            if (v_tag * v_i) % 7 = 0 then
                v_nr := v_nr + 1;
                call spiel.einkauf(v_b, v_kunden[v_i], v_tag, v_nr);
            end if;
        end loop;
    end loop;
    call spiel.bericht(v_b, 'Woche 4: nach einem Monat Betrieb');

    -- ------------------------------------------------ Die Kasse loest ein
    -- Zwei Kunden holen ihre Praemie, einer laesst sie liegen.
    v_i := 0;
    for v_gut in select id from public.vouchers
                  where tenant_id = v_b and not is_redeemed order by created_at loop
        v_i := v_i + 1;
        exit when v_i > 2;
        call auth.become(v_kasse);
        set local role authenticated;
        perform public.staff_redeem_voucher(v_gut);
        reset role;
    end loop;

    -- Einer verfaellt: Ausgabedatum und Frist zurueckdatieren.
    update public.vouchers
       set expires_at = (extract(epoch from now() - interval '2 days') * 1000)::int8
     where id = (select id from public.vouchers
                  where tenant_id = v_b and not is_redeemed order by created_at limit 1);

    -- ---------------------------------------------------------- Angebote
    insert into public.offers (tenant_id, title, description, is_redeemable)
         values (v_b, 'Schmankerl des Tages', 'Dinkel-Kracher, 3,50 EUR', true)
    returning id into v_angebot_alle;
    insert into public.offers (tenant_id, title, description, is_redeemable, min_stamps)
         values (v_b, 'Fast geschafft', 'Ab sieben Stempeln: Kaffee dazu', true, 7)
    returning id into v_angebot_fast;

    -- Wer einloesen darf, haengt am Kartenstand.
    v_anzahl := 0;
    foreach v_u in array v_kunden loop
        call auth.become(v_u);
        set local role authenticated;
        begin
            perform public.redeem_offer(v_angebot_fast);
            v_anzahl := v_anzahl + 1;
        exception when others then null;
        end;
        reset role;
    end loop;
    raise notice '';
    raise notice '    Angebot "Fast geschafft" (ab 7 Stempeln): % von % Karten konnten einloesen',
        v_anzahl, array_length(v_kunden, 1);

    -- ------------------------------------------------ Eine Karte wird geloescht
    call auth.become(v_kunden[10]);
    set local role authenticated;
    perform public.delete_card('sonnfeld');
    reset role;

    call spiel.bericht(v_b, 'Woche 5: nach Einloesungen, Verfall und einer Loeschung');

    -- ==================================================================
    -- Was der Betrieb NICHT sehen kann
    -- ==================================================================
    raise notice '';
    raise notice '  ════ Fragen, die die Verwaltung heute nicht beantwortet ════';

    select count(*) into v_voll from public.vouchers where tenant_id = v_b;
    select count(distinct user_id) into v_anzahl from public.vouchers where tenant_id = v_b;
    raise notice '';
    raise notice '  "Wie viele Karten hat ein Kunde schon vollgemacht?"';
    raise notice '    % Gutscheine auf % Karten - also mehrfach. Die Verwaltung zeigt',
        v_voll, v_anzahl;
    raise notice '    nur "erreichen die Praemie" als Anteil, nicht wie oft.';

    select count(*) into v_anzahl from public.stamp_proofs
     where tenant_id = v_b and source = 'counter';
    raise notice '';
    raise notice '  "Kommen die Stempel vom Bon oder vom Tresenaufsteller?"';
    raise notice '    % ueber den Tresen-Code, % ueber den Bon, % ueber Aktivierung -',
        v_anzahl,
        (select count(*) from public.stamp_proofs where tenant_id = v_b and source = 'receipt'),
        (select count(*) from public.stamp_proofs where tenant_id = v_b and source = 'aktivierung');
    raise notice '    steht in stamp_proofs.source, wird aber nirgends gezeigt.';

    raise notice '';
    raise notice '  "An welchem Wochentag und zu welcher Uhrzeit wird gestempelt?"';
    for v_i in 0..6 loop
        select count(*) into v_anzahl from public.stamp_proofs
         where tenant_id = v_b
           and extract(dow from created_at at time zone 'Europe/Berlin') = v_i;
        if v_anzahl > 0 then
            raise notice '    %: % Stempel',
                (array['So','Mo','Di','Mi','Do','Fr','Sa'])[v_i + 1], v_anzahl;
        end if;
    end loop;
    raise notice '    Berechenbar aus created_at, wird aber nirgends gezeigt.';

    select count(*) into v_anzahl from public.vouchers
     where tenant_id = v_b and is_redeemed and redeemed_at is not null;
    raise notice '';
    raise notice '  "Wie lange liegt eine Praemie, bis sie geholt wird?"';
    if v_anzahl > 0 then
        -- RAISE kennt kein FROM; erst rechnen, dann melden.
        select round(avg(extract(epoch from (v.redeemed_at - v.created_at)) / 86400)::numeric, 1)
          into v_tage
          from public.vouchers v
         where v.tenant_id = v_b and v.is_redeemed and v.redeemed_at is not null;
        raise notice '    Im Schnitt % Tage (aus % Einloesungen).', v_tage, v_anzahl;
    end if;
    raise notice '    Berechenbar aus created_at und redeemed_at, wird nirgends gezeigt.';

    raise notice '';
    raise notice '  "Wie viele Karten sind eingeschlafen?"';
    select count(*) into v_anzahl
      from public.memberships m
     where m.tenant_id = v_b
       and not exists (select 1 from public.stamp_proofs s
                        where s.tenant_id = v_b and s.user_id = m.user_id
                          and s.created_at > now() - interval '14 days');
    raise notice '    % von % Karten ohne Stempel in den letzten 14 Tagen.',
        v_anzahl, (select count(*) from public.memberships where tenant_id = v_b);
    raise notice '    "aktiv 30 Tage" zeigt die Gegenzahl, aber nur fuer 30 Tage.';
end;
$$;
