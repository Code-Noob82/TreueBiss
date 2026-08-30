-- ============================================================================
-- Aufbewahrung und Löschung der Kaufnachweise
--
-- Art. 5 Abs. 1 lit. e DSGVO lässt eine Speicherung nur so lange zu, wie der
-- Zweck sie erfordert. Diese Prüfungen halten fest, dass die Fristen wirken —
-- und dass sie nicht kürzer werden können als das, was die Stempelregeln
-- technisch noch brauchen.
-- ============================================================================
do $$
declare
    v_tenant  uuid;
    v_fremd   uuid;
    v_kunde   uuid;
    v_zeilen  int;
    v_ok      boolean;
    v_betrag  int;
    v_kasse   text;
begin
    insert into public.tenants (slug, name, proof_detail_days, proof_retention_days)
    values ('loesch-test', 'Aufräumbetrieb', 30, 90)
    returning id into v_tenant;
    insert into auth.users default values returning id into v_kunde;
    insert into public.memberships (user_id, tenant_id) values (v_kunde, v_tenant);

    -- ---------------------------------------------- Die abgeleitete Untergrenze
    /*
     * Die Aufbewahrung darf nie kuerzer sein als das Pruefzeitfenster: Ein
     * Beleg wird zwar abgelehnt, sobald er aelter ist als
     * proof_max_age_minutes - aber innerhalb dieses Fensters haelt ihn allein
     * der Nachweis vom zweiten Stempel ab. Waere er da schon geloescht,
     * zaehlte derselbe Bon erneut.
     */
    /*
     * proof_detail_days wandert mit auf 1, sonst schlaegt die andere
     * Bedingung zuerst zu (detail <= retention) und der Test bestuende, ohne
     * die Untergrenze je zu beruehren. Genau so ist er beim ersten Versuch
     * falsch gruen gewesen.
     */
    begin
        update public.tenants
           set proof_max_age_minutes = 43200,   -- 30 Tage Pruefzeitfenster
               proof_detail_days      = 1,
               proof_retention_days   = 1       -- deckt nur 1440 Minuten
         where id = v_tenant;
        v_ok := false;
    exception when check_violation then
        v_ok := true;
    end;
    call test.check(v_ok, 'Die Aufbewahrung kann das Prüfzeitfenster nicht unterschreiten');

    -- Gegenstueck: Reicht die Frist, geht dieselbe Aenderung durch.
    update public.tenants
       set proof_max_age_minutes = 1440, proof_detail_days = 1, proof_retention_days = 1
     where id = v_tenant;
    call test.check(true, 'Deckt die Frist das Fenster, ist dieselbe Änderung erlaubt');
    update public.tenants
       set proof_max_age_minutes = 120, proof_detail_days = 30, proof_retention_days = 90
     where id = v_tenant;

    begin
        update public.tenants set proof_detail_days = 120, proof_retention_days = 90
         where id = v_tenant;
        v_ok := false;
    exception when check_violation then
        v_ok := true;
    end;
    call test.check(v_ok, 'Details lassen sich nicht länger halten als der Nachweis selbst');

    -- ---------------------------------------------- Stufe 1: Details leeren
    insert into public.stamp_proofs
        (tenant_id, user_id, proof_ref, source, register_serial, amount_cents, created_at)
    values
        (v_tenant, v_kunde, 'KASSE1:0001', 'receipt', 'KASSE1', 1250, now() - interval '31 days'),
        (v_tenant, v_kunde, 'KASSE1:0002', 'receipt', 'KASSE1',  980, now() - interval '10 days');

    perform * from public.cleanup_expired_proofs();

    select register_serial, amount_cents into v_kasse, v_betrag
      from public.stamp_proofs where proof_ref = 'KASSE1:0001';
    call test.check(v_kasse is null and v_betrag is null,
        'Nach 30 Tagen sind Betrag und Kassennummer geleert');

    select count(*) into v_zeilen from public.stamp_proofs where proof_ref = 'KASSE1:0001';
    call test.check(v_zeilen = 1, 'Der Nachweis selbst bleibt zunächst stehen');

    select amount_cents into v_betrag from public.stamp_proofs where proof_ref = 'KASSE1:0002';
    call test.check(v_betrag = 980, 'Ein junger Nachweis behält seine Angaben');

    -- ---------------------------------------------- Stufe 2: Nachweis löschen
    insert into public.stamp_proofs
        (tenant_id, user_id, proof_ref, source, created_at)
    values (v_tenant, v_kunde, 'KASSE1:0003', 'receipt', now() - interval '91 days');

    perform * from public.cleanup_expired_proofs();
    select count(*) into v_zeilen from public.stamp_proofs where proof_ref = 'KASSE1:0003';
    call test.check(v_zeilen = 0, 'Nach 90 Tagen ist der Nachweis weg');

    select count(*) into v_zeilen from public.stamp_proofs where proof_ref = 'KASSE1:0002';
    call test.check(v_zeilen = 1, 'Der junge Nachweis bleibt unangetastet');

    -- ---------------------------------------------- Wiederholter Lauf
    -- Muss folgenlos sein: Die Funktion soll nach Zeitplan laufen duerfen.
    perform * from public.cleanup_expired_proofs();
    select count(*) into v_zeilen from public.stamp_proofs where tenant_id = v_tenant;
    call test.check(v_zeilen = 2, 'Ein zweiter Lauf ändert nichts mehr');

    -- ---------------------------------------------- Nicht aus der App aufrufbar
    call auth.become(v_kunde);
    set local role authenticated;
    begin
        perform * from public.cleanup_expired_proofs();
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Die App kann das Aufräumen nicht selbst auslösen');
    reset role;

    -- ---------------------------------------------- Jeder Betrieb, seine Frist
    /*
     * Die Funktion darf nicht mit einer globalen Frist rechnen: Ein Betrieb,
     * der laenger aufbewahrt, muss seinen Nachweis behalten, waehrend derselbe
     * Zeitpunkt beim Nachbarn schon abgeraeumt wird.
     */
    insert into public.tenants (slug, name, proof_detail_days, proof_retention_days)
    values ('loesch-lang', 'Langzeitbetrieb', 300, 400) returning id into v_fremd;
    insert into public.memberships (user_id, tenant_id) values (v_kunde, v_fremd);
    insert into public.stamp_proofs
        (tenant_id, user_id, proof_ref, source, register_serial, amount_cents, created_at)
    values (v_fremd, v_kunde, 'KASSE9:0001', 'receipt', 'KASSE9', 4200,
            now() - interval '91 days');

    perform * from public.cleanup_expired_proofs();

    select count(*) into v_zeilen from public.stamp_proofs where proof_ref = 'KASSE9:0001';
    call test.check(v_zeilen = 1,
        'Ein Betrieb mit längerer Frist behält denselben Zeitpunkt');
    select amount_cents into v_betrag from public.stamp_proofs where proof_ref = 'KASSE9:0001';
    call test.check(v_betrag = 4200, 'Und auch dessen Angaben, weil seine Detailfrist länger ist');

    delete from public.stamp_proofs where tenant_id = v_fremd;
    delete from public.memberships where tenant_id = v_fremd;
    delete from public.tenants where id = v_fremd;

    delete from public.stamp_proofs where tenant_id = v_tenant;
    delete from public.memberships where tenant_id = v_tenant;
    delete from public.tenants where id = v_tenant;
    delete from auth.users where id = v_kunde;

    raise notice '--- Aufbewahrung und Löschung bestanden ---';
end
$$;
