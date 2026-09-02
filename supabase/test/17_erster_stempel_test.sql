-- ============================================================================
-- Der erste Stempel ist die Begruessung
--
-- Bis zum 02.09.2026 gab es hier zwei Stempel fuer einen Scan: einen
-- Willkommensstempel fuers Anlegen und den eigentlichen Tresen-Stempel. Das
-- verschenkte genau den Effekt, den es bewirken sollte. Wer den Laden betritt
-- und dafuer einen Punkt bekommt, besitzt etwas - und was man besitzt, gibt
-- man nicht auf. Ein zweiter Punkt daneben verdoppelt diesen Besitz nicht, er
-- verwaessert den Grund, ueberhaupt gekommen zu sein.
--
-- Seitdem gilt: Die Karte entsteht mit dem ersten Stempel, und der erste
-- Stempel ist genau einer.
-- ============================================================================
do $$
declare
    v_b      uuid;
    v_chef   uuid;
    v_kunde  uuid;
    v_zweit  uuid;
    v_token  text;
    v_anzahl int;
    v_erg    record;
begin
    insert into public.tenants (slug, name, stamps_per_card, allow_opaque_proofs)
         values ('erster', 'Erststempelbetrieb', 50, true) returning id into v_b;
    insert into auth.users default values returning id into v_chef;
    insert into auth.users default values returning id into v_kunde;
    insert into auth.users default values returning id into v_zweit;
    insert into public.tenant_staff (user_id, tenant_id, role) values (v_chef, v_b, 'owner');

    -- Tresen-Code einschalten; dabei entsteht der Schluessel.
    call auth.become(v_chef);
    perform public.owner_update_proof_rules(v_b, 120, 0, 25, false, true, false, true, 60);
    select token into v_token from public.staff_counter_token(v_b);

    -- ---------------------------------------------- vor dem ersten Stempel
    select count(*) into v_anzahl from public.memberships where tenant_id = v_b;
    call test.check(v_anzahl = 0, 'Ohne Stempel gibt es keine Karte');

    -- ------------------------------------------------ der Tresen-Stempel
    call auth.become(v_kunde);
    perform public.issue_stamp(v_b, 'tresen:' || v_token);
    reset role;

    select count(*) into v_anzahl from public.memberships
     where tenant_id = v_b and user_id = v_kunde;
    call test.check(v_anzahl = 1, 'Der Tresen-Stempel legt die Karte an');

    select count(*) into v_anzahl from public.stamps
     where tenant_id = v_b and user_id = v_kunde;
    call test.check(v_anzahl = 1, 'Und vergibt genau einen Punkt, nicht zwei');

    -- Kein Nachweis traegt mehr die Quelle 'aktivierung'.
    select count(*) into v_anzahl from public.stamp_proofs
     where tenant_id = v_b and source = 'aktivierung';
    call test.check(v_anzahl = 0, 'Es entsteht kein Aktivierungsnachweis mehr');

    select count(*) into v_anzahl from public.stamp_proofs
     where tenant_id = v_b and user_id = v_kunde and source = 'counter';
    call test.check(v_anzahl = 1, 'Der eine Nachweis ist der des Tresen-Codes');

    -- ---------------------------------- wer ueber einen Nachweis einsteigt
    call auth.become(v_zweit);
    perform public.issue_stamp(v_b, 'beleg-einstieg-4711');
    reset role;
    select count(*) into v_anzahl from public.stamps
     where tenant_id = v_b and user_id = v_zweit;
    call test.check(v_anzahl = 1, 'Auch ueber einen Nachweis gibt es genau einen');

    select count(*) into v_anzahl from public.memberships where tenant_id = v_b;
    call test.check(v_anzahl = 2, 'Zwei Karten, zwei Kunden');

    -- ------------------------------------------------ die Zahlen des Betriebs
    select stamps_issued, purchase_stamps into v_erg
      from public.pilot_summary where tenant_id = v_b;
    call test.check(v_erg.stamps_issued = 2, 'Zwei Stempel im Betrieb');
    call test.check(v_erg.purchase_stamps = 0,
                    'Keiner davon geht auf einen Beleg-QR zurueck');

    raise notice '--- Erster Stempel bestanden ---';
end;
$$;
