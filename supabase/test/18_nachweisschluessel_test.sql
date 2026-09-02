-- ============================================================================
-- Nachweisschluessel und Herkunft
--
-- Zwei Fehler, die am 02.09.2026 beim Durchspielen eines erfundenen Betriebs
-- aufgefallen sind - keinem Test, weil beide Funktionen fuer sich richtig
-- arbeiteten.
--
-- 1. Der Schluessel trug die Kassennummer im Klartext. cleanup_expired_proofs
--    leert nach proof_detail_days register_serial und amount_cents, den
--    Schluessel aber nicht - die Nummer stand also neben user_id und
--    created_at weiter bis proof_retention_days in der Tabelle.
--
-- 2. Der freie Nachweis wurde als 'receipt' gefuehrt, weil p_source
--    unveraendert durchlief. Damit zaehlte die Kachel "davon fuer Kaeufe"
--    Stempel mit, hinter denen kein Kauf steht.
-- ============================================================================
do $$
declare
    v_b      uuid;
    v_kunde  uuid;
    v_ref    text;
    v_anzahl int;
    v_qr     text;
begin
    insert into public.tenants (slug, name, counter_qr_enabled, allow_opaque_proofs)
         values ('schluessel-test', 'Schluesselbetrieb', true, true) returning id into v_b;
    insert into public.tenant_secrets (tenant_id, counter_secret)
         values (v_b, encode(extensions.gen_random_bytes(32), 'hex'));
    insert into auth.users default values returning id into v_kunde;
    insert into public.memberships (user_id, tenant_id) values (v_kunde, v_b);

    v_qr := 'V0;AMA-2642;Kassenbeleg-V1;Beleg^4.05_0.00_0.00_0.00_0.00^4.05:Bar;77;44131;'
         || to_char(now() at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') || ';'
         || to_char(now() at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') || ';'
         || 'ecdsa-plain-SHA384;utcTime;K8zsZ6NjsBzo/…;BBXNYQErM4d9sk9Iy+0T6A4=';

    call auth.become(v_kunde);
    set local role authenticated;
    perform public.issue_stamp(v_b, v_qr);
    reset role;

    -- ------------------------------------------- Der Schluessel ist ein Hash
    select proof_ref into v_ref from public.stamp_proofs where tenant_id = v_b;
    call test.check(v_ref ~ '^[0-9a-f]{64}$',
                    'Der Nachweisschluessel ist ein Hash, kein Klartext');
    call test.check(position('AMA-2642' in v_ref) = 0,
                    'Die Kassennummer steht nicht darin');

    -- Sie steht weiter in ihrer eigenen Spalte - dort loescht die Frist sie.
    select count(*) into v_anzahl from public.stamp_proofs
     where tenant_id = v_b and register_serial = 'AMA-2642';
    call test.check(v_anzahl = 1, 'In register_serial steht sie weiterhin');

    /*
     * Der Schutz gegen doppelte Belege haengt an dieser Bedingung. Ein Hash
     * ist deterministisch - derselbe Bon ergibt denselben Wert und kollidiert
     * wie vorher. Ohne diese Pruefung waere der Datenschutz auf Kosten der
     * eigentlichen Aufgabe erkauft.
     */
    call auth.become(v_kunde);
    set local role authenticated;
    begin
        perform public.issue_stamp(v_b, v_qr);
        v_anzahl := 0;
    exception when others then
        v_anzahl := 1;
    end;
    reset role;
    call test.check(v_anzahl = 1, 'Derselbe Beleg zaehlt weiterhin nur einmal');

    select count(*) into v_anzahl from public.stamps
     where tenant_id = v_b and user_id = v_kunde;
    call test.check(v_anzahl = 1, 'Und hinterlaesst genau einen Stempel');

    -- ------------------------------------------------- Herkunft der Stempel
    call auth.become(v_kunde);
    set local role authenticated;
    perform public.issue_stamp(v_b, 'irgendeine-zeichenkette');
    reset role;

    select count(*) into v_anzahl from public.stamp_proofs
     where tenant_id = v_b and source = 'opaque';
    call test.check(v_anzahl = 1, 'Ein freier Nachweis heisst opaque, nicht receipt');

    select purchase_stamps into v_anzahl from public.pilot_summary where tenant_id = v_b;
    call test.check(v_anzahl = 1, 'Als Kauf zaehlt nur der Beleg-QR');
    select stamps_issued into v_anzahl from public.pilot_summary where tenant_id = v_b;
    call test.check(v_anzahl = 2, 'Insgesamt sind es zwei Stempel');

    -- ------------------------------------------------ Nachtraegliches Hashen
    -- Eine Zeile aus der Zeit davor, wie sie in laufenden Projekten liegt.
    insert into public.stamp_proofs (tenant_id, user_id, proof_ref, source)
         values (v_b, v_kunde, 'KASSE-9:0001:12345', 'receipt');
    update public.stamp_proofs
       set proof_ref = encode(extensions.digest(proof_ref, 'sha256'), 'hex')
     where proof_ref !~ '^[0-9a-f]{64}$';
    select count(*) into v_anzahl from public.stamp_proofs
     where tenant_id = v_b and proof_ref !~ '^[0-9a-f]{64}$';
    call test.check(v_anzahl = 0, 'Alte Klartext-Schluessel werden nachgezogen');

    raise notice '--- Nachweisschluessel bestanden ---';
end;
$$;
