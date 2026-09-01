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
        array['public.staff_pilot_summary()',                       'n', 'j', 'j'],
        array['public.staff_pilot_cohorts()',                       'n', 'j', 'j'],
        array['public.owner_set_redeem_code(uuid, text)',           'n', 'j', 'j'],
        array['public.owner_clear_redeem_code(uuid)',               'n', 'j', 'j'],
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
        array['public.issue_stamp_intern(uuid, uuid, text, text, boolean)',
                                                                      'n', 'n', 'j']
    ];
    v_zeile   text[];
    v_rollen  text[] := array['anon', 'authenticated', 'service_role'];
    i         int;
    v_darf    boolean;
    v_soll    boolean;
begin
    foreach v_zeile slice 1 in array v_erwartung loop
        for i in 1..3 loop
            v_soll := v_zeile[i + 1] = 'j';
            v_darf := has_function_privilege(v_rollen[i], v_zeile[1], 'EXECUTE');
            call test.check(
                v_darf = v_soll,
                format('%s: %s darf %s', v_zeile[1], v_rollen[i],
                       case when v_soll then 'ausfuehren' else 'NICHT ausfuehren' end));
        end loop;
    end loop;

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
