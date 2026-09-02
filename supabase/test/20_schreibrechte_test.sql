-- ============================================================================
-- Schreibrechte auf den Kartentabellen
--
-- Am 02.09.2026 von aussen nachgewiesen: Jeder anonym Angemeldete konnte sich
-- Stempel und Gutscheine unmittelbar eintragen - ohne Kaufnachweis, ohne
-- issue_stamp. Damit fiel die Zusage, auf der das Produkt steht.
--
-- Die Ursache waren zwei Policies mit Namen aus dem Supabase-Dashboard
-- ("Allow individual insert access"), die das Schema nicht kannte und deshalb
-- nicht wegraeumte. Lokal fiel nichts auf, weil es dort keine Handanlage gab.
--
-- Dieser Test prueft deshalb beides und aus beiden Richtungen:
--   die Rechte, unabhaengig von jeder Policy
--   die Policies selbst, gegen eine abschliessende Liste
--
-- Die zweite Haelfte ist die wichtigere. Eine Policy, die niemand erwartet,
-- faellt sonst erst auf, wenn jemand von aussen danach sucht.
-- ============================================================================
do $$
declare
    v_tabellen text[] := array['stamps', 'vouchers', 'stamp_proofs',
                               'offer_redemptions', 'memberships'];
    -- Dieselbe Liste wie im Schema. Die Dopplung ist Absicht: Wer eine Policy
    -- anlegt und nur eine Seite pflegt, faellt hier auf.
    v_alle_policies text[] := array[
        'tenants_read',
        'tenant_staff_select_own',
        'tenant_registers_read', 'tenant_registers_owner_insert',
        'tenant_registers_owner_delete',
        'offers_read', 'offers_owner_read', 'offers_owner_insert',
        'offers_owner_update', 'offers_owner_delete',
        'memberships_select_own', 'memberships_insert_own', 'memberships_delete_own',
        'stamps_select_own', 'vouchers_select_own', 'stamp_proofs_select_own',
        'offer_redemptions_select_own', 'offer_redemptions_staff_read'
    ];
    v_tab      text;
    v_recht    text;
    v_rolle    text;
    v_fremd    text;
    v_anzahl   int;
begin
    -- ------------------------------------------------------------- Rechte
    foreach v_tab in array v_tabellen loop
        foreach v_rolle in array array['anon', 'authenticated'] loop
            foreach v_recht in array array['INSERT', 'UPDATE', 'DELETE'] loop
                call test.check(
                    not has_table_privilege(v_rolle, 'public.' || v_tab, v_recht),
                    format('%s: %s darf nicht %s', v_tab, v_rolle, lower(v_recht)));
            end loop;
        end loop;
    end loop;

    /*
     * service_role behaelt alles. Die Edge Functions schreiben damit - ein
     * Entzug haette die Belegpruefung stillgelegt, und zwar erst im Betrieb.
     */
    call test.check(has_table_privilege('service_role', 'public.stamps', 'INSERT'),
                    'service_role schreibt weiterhin');

    /*
     * Keine fremde Policy - auf keiner Tabelle.
     *
     * Anfangs galt diese Probe nur fuer die fuenf Tabellen des Kartenwegs.
     * Der Fehler vom 02.09. lag zwar dort, haette aber genauso auf tenants
     * oder offers liegen koennen: Ein Klick im Dashboard trifft jede Tabelle.
     */
    select string_agg(c.relname || '.' || p.polname, ', ')
      into v_fremd
      from pg_policy p
      join pg_class c on c.oid = p.polrelid
     where c.relnamespace = 'public'::regnamespace
       and not (p.polname = any (v_alle_policies));
    call test.check(v_fremd is null,
                    coalesce('Fremde Policy gefunden: ' || v_fremd,
                             'Keine Policy, die hier niemand angelegt hat'));

    /*
     * Und die Gegenrichtung, damit die Liste keine Abstellkammer wird.
     *
     * Ein Name, den niemand mehr anlegt, bliebe sonst ewig erlaubt - und
     * erlaubte damit still, dass jemand genau diesen Namen von Hand vergibt.
     */
    select string_agg(n, ', ') into v_fremd
      from unnest(v_alle_policies) as n
     where not exists (select 1 from pg_policy where polname = n);
    call test.check(v_fremd is null,
                    coalesce('Erlaubt, aber nirgends angelegt: ' || v_fremd,
                             'Jeder erlaubte Name entspricht einer echten Policy'));

    -- RLS muss ueberall an sein - ohne sie waeren die Lesepolicies wirkungslos.
    -- Auch das gilt fuer alle Tabellen, nicht nur die des Kartenwegs.
    foreach v_tab in array array['tenants', 'tenant_secrets', 'tenant_staff',
                                 'tenant_registers', 'offers', 'card_deletions',
                                 'stamps', 'vouchers', 'stamp_proofs',
                                 'offer_redemptions', 'memberships'] loop
        select count(*) into v_anzahl from pg_class c
         where c.relnamespace = 'public'::regnamespace
           and c.relname = v_tab and c.relrowsecurity;
        call test.check(v_anzahl = 1, format('%s: RLS ist an', v_tab));
    end loop;

    raise notice '--- Schreibrechte bestanden ---';
end;
$$;

-- ---------------------------------------------------------------------------
-- Gegenprobe am lebenden Objekt: Der Weg, den ein Angreifer genommen hat.
-- ---------------------------------------------------------------------------
do $$
declare
    v_b     uuid;
    v_wer   uuid;
    v_ok    boolean;
begin
    insert into public.tenants (slug, name) values ('schreib-test', 'Schreibbetrieb')
    returning id into v_b;
    insert into auth.users default values returning id into v_wer;
    insert into public.memberships (user_id, tenant_id) values (v_wer, v_b);

    call auth.become(v_wer);
    set local role authenticated;

    begin
        insert into public.stamps (id, user_id, tenant_id)
             values (gen_random_uuid(), v_wer, v_b);
        v_ok := false;
    exception when others then v_ok := true;
    end;
    call test.check(v_ok, 'Ein Kunde traegt sich keinen Stempel ein');

    begin
        insert into public.vouchers (id, creation_date, expires_at, is_redeemed, user_id, tenant_id)
             values (gen_random_uuid(), 1, 99999999999999, false, v_wer, v_b);
        v_ok := false;
    exception when others then v_ok := true;
    end;
    call test.check(v_ok, 'Und keinen Gutschein');

    begin
        insert into public.stamp_proofs (tenant_id, user_id, proof_ref, source)
             values (v_b, v_wer, 'erfunden', 'receipt');
        v_ok := false;
    exception when others then v_ok := true;
    end;
    call test.check(v_ok, 'Und keinen Nachweis');

    reset role;
    raise notice '--- Kein Weg an issue_stamp vorbei ---';
end;
$$;
