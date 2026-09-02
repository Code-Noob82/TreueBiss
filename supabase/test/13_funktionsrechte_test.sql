-- ============================================================================
-- Ausfuehrungsrechte der Funktionen
--
-- `revoke ... from public` nimmt nur das implizite Recht weg, das eine
-- Funktion bei ihrer Erstellung traegt. Supabase vergibt EXECUTE zusaetzlich
-- ausdruecklich an anon, authenticated und service_role - und eine
-- ausdrueckliche Vergabe ueberlebt den Entzug von public.
--
-- Am 31.08.2026 war deshalb jede Funktion dieses Schemas fuer anon aufrufbar.
-- Bei counter_token liess sich das im laufenden Projekt belegen: HTTP 200.
--
-- Diese Datei fragt Rechte ab, statt Funktionen aufzurufen. has_function_-
-- privilege hat keine Nebenwirkung - ein Test, der cleanup_expired_proofs
-- probeweise ausfuehrt, um zu sehen ob er darf, loescht im Zweifel Daten.
--
-- Die Liste unten ist von Hand gepflegt, und eine von Hand gepflegte Liste
-- vergisst. Genau deshalb steht am Ende des ersten Blocks die Gegenprobe: Sie
-- haelt jede Funktion aus pg_proc gegen die Liste und schlaegt fehl, sobald
-- eine fehlt. Ohne sie waere eine neu hinzugefuegte Funktion nie geprueft
-- worden - schweigend, und mit derselben Vorgabe im Ruecken, die am 31.08.
-- counter_token fuer anon geoeffnet hat.
-- ============================================================================
do $$
declare
    v_erwartung text[][] := array[
        -- Funktion mit Signatur                                    anon  auth  service
        array['public.redeem_voucher(uuid, text)',                  'n', 'j', 'j'],
        array['public.redeem_offer(uuid, text)',                    'n', 'j', 'j'],
        array['public.staff_redeem_voucher(uuid)',                  'n', 'j', 'j'],
        array['public.staff_counter_token(uuid)',                   'n', 'j', 'j'],
        array['public.issue_stamp(uuid, text, text)',               'n', 'j', 'j'],
        array['public.adopt_card(text)',                            'n', 'j', 'j'],
        array['public.delete_card(text)',                           'n', 'j', 'j'],
        array['public.activate_card(uuid)',                         'n', 'j', 'j'],
        array['public.staff_pilot_summary()',                       'n', 'j', 'j'],
        array['public.staff_pilot_cohorts()',                       'n', 'j', 'j'],
        array['public.staff_pilot_daily(int)',                      'n', 'j', 'j'],
        array['public.owner_set_redeem_code(uuid, text)',           'n', 'j', 'j'],
        array['public.owner_clear_redeem_code(uuid)',               'n', 'j', 'j'],
        array['public.owner_update_tenant(uuid, text, text, text, text, text,'
              ' text, int, int)',                                   'n', 'j', 'j'],
        array['public.owner_update_proof_rules(uuid, int, int, int, boolean,'
              ' boolean, boolean, boolean, int, int, int, boolean)','n', 'j', 'j'],
        -- Die Frage "arbeitet der hier?" steht in Policies auf
        -- offer_redemptions, offers und tenant_registers, und die gelten fuer
        -- authenticated. Der Ausdruck einer Policy laeuft mit den Rechten
        -- dessen, der die Abfrage stellt - ohne EXECUTE bricht das Lesen und
        -- Schreiben dieser Tabellen ab. Deshalb bleiben diese beiden offen,
        -- anders als der Rest der Innereien.
        --
        -- anon braucht sie trotzdem nicht: ohne Anmeldung ist auth.uid() leer
        -- und die Antwort immer "nein". Aus dem Browser aufrufbar waren sie
        -- bis hierher nur, weil Supabase jeder neuen Funktion diese Freigabe
        -- mitgibt.
        array['public.is_staff_of(uuid)',                            'n', 'j', 'j'],
        array['public.is_owner_of(uuid)',                            'n', 'j', 'j'],
        -- Nur die Edge Function und der Zeitplan, niemand aus dem Browser.
        array['public.service_issue_stamp(uuid, uuid, text, text)',  'n', 'n', 'j'],
        array['public.cleanup_expired_proofs()',                     'n', 'n', 'j'],
        -- Innereien: werden ausschliesslich von anderen Funktionen gerufen,
        -- die als definer laufen. Aus dem Browser erreichbar sein duerfen sie
        -- nicht - counter_token gibt den Tresen-Token heraus.
        --
        -- service_role behaelt sie. Das ist kein Versehen: Wer diesen
        -- Schluessel hat, liest tenant_secrets ohnehin unmittelbar und
        -- rechnet sich den Token selbst aus. Ein Entzug waere Theater, und
        -- der Schluessel erreicht nie einen Browser.
        array['public.counter_token(uuid, int)',                      'n', 'n', 'j'],
        array['public.parse_receipt_qr(text)',                        'n', 'n', 'j'],
        array['public.karte_anlegen_intern(uuid, uuid)',              'n', 'n', 'j'],
        array['public.issue_stamp_intern(uuid, uuid, text, text, boolean)',
                                                                      'n', 'n', 'j'],
        -- is_demo_of wird nur aus staff_redeem_voucher und
        -- staff_counter_token gerufen, und beide laufen als definer. Sie steht
        -- in keiner Policy. is_member_of ruft ueberhaupt niemand: Die App
        -- fragt sie nicht, und issue_stamp_intern sieht die Mitgliedschaft
        -- selbst nach, weil der Nutzer dort als Parameter kommt.
        array['public.is_demo_of(uuid)',                               'n', 'n', 'j'],
        array['public.is_member_of(uuid)',                             'n', 'n', 'j']
    ];
    v_zeile   text[];
    v_rollen  text[] := array['anon', 'authenticated', 'service_role'];
    i         int;
    v_darf    boolean;
    v_soll    boolean;
    v_oid     oid;
    v_gelistet oid[] := '{}';
    v_fehlt   text;
begin
    foreach v_zeile slice 1 in array v_erwartung loop
        -- Ueber die OID, nicht ueber den Text: 'counter_token(uuid, int)' und
        -- 'counter_token(p_tenant_id uuid, p_fenster integer)' meinen dieselbe
        -- Funktion, und die Gegenprobe unten soll das nicht auseinanderhalten
        -- muessen.
        v_oid := to_regprocedure(v_zeile[1]);
        if v_oid is null then
            call test.check(false, format(
                '%s steht in der Liste, gibt es aber nicht', v_zeile[1]));
        end if;
        v_gelistet := v_gelistet || v_oid;

        for i in 1..3 loop
            v_soll := v_zeile[i + 1] = 'j';
            v_darf := has_function_privilege(v_rollen[i], v_zeile[1], 'EXECUTE');
            call test.check(
                v_darf = v_soll,
                format('%s: %s darf %s', v_zeile[1], v_rollen[i],
                       case when v_soll then 'ausfuehren' else 'NICHT ausfuehren' end));
        end loop;
    end loop;

    /*
     * Die Gegenprobe. Ohne sie prueft diese Datei genau das, woran jemand
     * gedacht hat - und eine Funktion, an die niemand gedacht hat, behaelt
     * still die Freigabe, die Supabase ihr mitgibt: anon darf.
     *
     * Wer hier eine Funktion nachtraegt, muss sich fuer jede Rolle
     * entscheiden. Das ist der Zweck: Die Entscheidung soll erzwungen sein,
     * nicht der Ist-Zustand festgeschrieben.
     */
    select string_agg(q.signatur, ', ' order by q.signatur) into v_fehlt
      from (select format('public.%s(%s)', p.proname,
                          pg_get_function_identity_arguments(p.oid)) as signatur
              from pg_proc p
              join pg_namespace n on n.oid = p.pronamespace
             where n.nspname = 'public'
               and not (p.oid = any (v_gelistet))) q;

    call test.check(
        v_fehlt is null,
        coalesce('Ohne geprueftes Recht im Schema public: ' || v_fehlt,
                 'Jede Funktion in public steht in der Rechteliste'));

    raise notice '--- Ausfuehrungsrechte bestanden ---';
end;
$$;

-- ---------------------------------------------------------------------------
-- Gegenprobe am lebenden Objekt: Der Tresen-Token darf sich aus einer
-- anonymen Sitzung nicht holen lassen. Genau das ging im echten Projekt.
-- ---------------------------------------------------------------------------
do $$
declare
    v_tenant uuid;
    v_ok     boolean;
begin
    insert into public.tenants (slug, name) values ('rechte-test', 'Rechtebetrieb')
    returning id into v_tenant;

    set local role anon;
    begin
        perform public.counter_token(v_tenant, 0);
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'anon kommt nicht an den Tresen-Token');

    set local role authenticated;
    begin
        perform public.counter_token(v_tenant, 0);
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Auch ein angemeldeter Kunde kommt nicht daran');

    reset role;
    raise notice '--- Tresen-Token bleibt verschlossen ---';
end;
$$;
