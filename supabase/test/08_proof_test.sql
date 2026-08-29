-- Tests für die Prüfung des Kaufnachweises.
--
-- Die Einmaligkeitsprüfung schützt nur gegen denselben Beleg zweimal. Der
-- tatsächliche Missbrauchsfall ist ein anderer: In einer Bäckerei bleiben die
-- Bons überwiegend im Laden liegen, wer sie einsammelt, hat lauter gültige
-- Nachweise in der Hand. Dagegen stehen Zeitfenster, Mindestbetrag,
-- Kassenliste und Tageslimit — und die werden hier geprüft.
--
-- Was hier NICHT geprüft wird, weil es die Datenbank nicht kann: die
-- ECDSA-Signatur des Belegs. Sie ist das einzige Mittel gegen einen selbst
-- gebauten QR-Code und braucht eine Edge Function.

\set ON_ERROR_STOP on

-- Baut einen Beleg-QR nach DSFinV-K Anhang I.
create or replace function test.bon(
    p_serial text default 'AMA-2642',
    p_tx     text default '13',
    p_ctr    text default '44131',
    p_zeit   timestamptz default now(),
    p_betrag text default '7.05'
) returns text language sql as $$
    select 'V0;' || p_serial || ';Kassenbeleg-V1;'
        || 'Beleg^' || p_betrag || '_0.00_0.00_0.00_0.00^' || p_betrag || ':Bar;'
        || p_tx || ';' || p_ctr || ';'
        || to_char(p_zeit at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') || ';'
        || to_char(p_zeit at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') || ';'
        || 'ecdsa-plain-SHA384;utcTime;K8zsZ6NjsBzo/…;BBXNYQErM4d9sk9Iy+0T6A4=';
$$;

-- ============================ Der Parser gegen das amtliche Beispiel
do $$
declare b record;
begin
    -- Wörtlich aus DSFinV-K 2.4, Anhang I. Er ist von 2019 und damit viel zu
    -- alt für einen Stempel - gelesen werden muss er trotzdem richtig.
    select * into b from public.parse_receipt_qr(
        'V0;AMA-2642;Kassenbeleg-V1;Beleg^4.05_3.00_0.00_0.00_0.00^7.05:Bar;13;44131;'
        || '2019-11-22T11:29:48.000Z;2019-11-22T11:29:49.000Z;ecdsa-plain-SHA384;'
        || 'unixTime;K8zsZ6NjsBzo/…;BBXNYQErM4d9sk9Iy+0T6A4sdTocijml5X78Gq/…==');
    call test.check(found, 'Das amtliche Beispiel wird als Beleg erkannt');
    call test.check(b.register_serial = 'AMA-2642', 'Die Kassennummer wird gelesen');
    call test.check(b.transaction_no = '13', 'Die Transaktionsnummer wird gelesen');
    call test.check(b.signature_ctr = '44131', 'Der Signaturzähler wird gelesen');
    call test.check(b.amount_cents = 705, 'Der Betrag wird in Cent gelesen');
    call test.check(b.payment = 'Bar', 'Die Zahlart wird gelesen');
    call test.check(b.log_time = '2019-11-22T11:29:49Z'::timestamptz, 'Die Belegzeit wird gelesen');

    -- Alles, was unklar ist, gilt als kein Beleg.
    select * into b from public.parse_receipt_qr('BON-4711');
    call test.check(not found, 'Eine freie Zeichenkette ist kein Beleg');
    select * into b from public.parse_receipt_qr('V0;AMA-2642;Kassenbeleg-V1');
    call test.check(not found, 'Zu wenige Felder sind kein Beleg');
    select * into b from public.parse_receipt_qr(test.bon(p_betrag => 'viel'));
    call test.check(not found, 'Ein unlesbarer Betrag ist kein Beleg');
    select * into b from public.parse_receipt_qr(replace(test.bon(), 'V0;', 'V9;'));
    call test.check(not found, 'Eine fremde QR-Version ist kein Beleg');
    select * into b from public.parse_receipt_qr(replace(test.bon(), ';AMA-2642;', ';;'));
    call test.check(not found, 'Ohne Kassennummer ist es kein Beleg');

    raise notice '--- Parser bestanden ---';
end
$$;

-- ============================ Die Regeln beim Vergeben
do $$
declare
    v_t     uuid;
    v_kunde uuid;
    v_ok    boolean;
    v_ref   text;
begin
    insert into public.tenants (id, slug, name, stamps_per_card)
    values (gen_random_uuid(), 'beleg-test', 'Belegprüfung', 50)
    returning id into v_t;

    insert into auth.users default values returning id into v_kunde;
    insert into public.memberships (user_id, tenant_id) values (v_kunde, v_t);
    call auth.become(v_kunde);

    -- ------------------------------------------ Frischer Beleg zählt
    perform public.issue_stamp(v_t, test.bon(p_tx => '1001'));
    select proof_ref into v_ref from public.stamp_proofs
     where tenant_id = v_t order by created_at desc limit 1;
    call test.check(v_ref = 'AMA-2642:1001:44131',
                    'Der Schlüssel entsteht aus Kasse, Transaktion und Zähler');
    select count(*) = 1 into v_ok from public.stamps where tenant_id = v_t;
    call test.check(v_ok, 'Ein frischer Beleg gibt einen Stempel');

    -- ------------------------------------------ Derselbe Beleg nicht nochmal
    begin
        perform public.issue_stamp(v_t, test.bon(p_tx => '1001'));
        v_ok := false;
    exception when unique_violation then
        v_ok := true;
    end;
    call test.check(v_ok, 'Derselbe Beleg zählt nicht zweimal');

    -- Auch nicht mit anderen Leerzeichen: Der Schlüssel ist kanonisch, nicht
    -- die rohe Zeichenkette.
    begin
        perform public.issue_stamp(v_t, replace(test.bon(p_tx => '1001'), 'AMA-2642', ' AMA-2642 '));
        v_ok := false;
    exception when unique_violation then
        v_ok := true;
    end;
    call test.check(v_ok, 'Zusätzliche Leerzeichen machen den Beleg nicht neu');

    -- ------------------------------------------ Zu alt
    begin
        perform public.issue_stamp(v_t, test.bon(p_tx => '1002', p_zeit => now() - interval '3 hours'));
        v_ok := false;
    exception when data_exception then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ein drei Stunden alter Beleg wird abgelehnt');

    -- ... und innerhalb des Fensters eben doch.
    perform public.issue_stamp(v_t, test.bon(p_tx => '1003', p_zeit => now() - interval '30 minutes'));
    call test.check(true, 'Ein halbstündiger Beleg zählt noch');

    -- ------------------------------------------ Aus der Zukunft
    begin
        perform public.issue_stamp(v_t, test.bon(p_tx => '1004', p_zeit => now() + interval '2 hours'));
        v_ok := false;
    exception when data_exception then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ein Beleg aus der Zukunft wird abgelehnt');

    -- ------------------------------------------ Mindestbetrag
    update public.tenants set proof_min_cents = 500 where id = v_t;
    begin
        perform public.issue_stamp(v_t, test.bon(p_tx => '1005', p_betrag => '2.50'));
        v_ok := false;
    exception when data_exception then
        v_ok := true;
    end;
    call test.check(v_ok, 'Unter dem Mindestbetrag gibt es keinen Stempel');
    perform public.issue_stamp(v_t, test.bon(p_tx => '1006', p_betrag => '5.00'));
    call test.check(true, 'Genau auf dem Mindestbetrag zählt der Beleg');
    update public.tenants set proof_min_cents = 0 where id = v_t;

    -- ------------------------------------------ Nur eingetragene Kassen
    update public.tenants set require_known_register = true where id = v_t;
    begin
        perform public.issue_stamp(v_t, test.bon(p_serial => 'FREMD-1', p_tx => '1007'));
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Eine fremde Kasse wird abgelehnt');

    insert into public.tenant_registers (tenant_id, serial, label)
    values (v_t, 'AMA-2642', 'Theke vorn');
    perform public.issue_stamp(v_t, test.bon(p_tx => '1008'));
    call test.check(true, 'Die eingetragene Kasse zählt');
    update public.tenants set require_known_register = false where id = v_t;

    -- ------------------------------------------ Freier Nachweis
    update public.tenants set allow_opaque_proofs = false where id = v_t;
    begin
        perform public.issue_stamp(v_t, 'IRGENDEINE-NUMMER');
        v_ok := false;
    exception when data_exception then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ohne Beleg-QR geht nichts, wenn der Betrieb das verlangt');

    update public.tenants set allow_opaque_proofs = true where id = v_t;
    perform public.issue_stamp(v_t, 'IRGENDEINE-NUMMER');
    call test.check(true, 'Erlaubt der Betrieb freie Nachweise, zählen sie weiter');

    raise notice '--- Belegregeln bestanden ---';
end
$$;

-- ============================ Tageslimit
do $$
declare
    v_t     uuid;
    v_kunde uuid;
    v_zwei  uuid;
    v_ok    boolean;
    v_zahl  int;
begin
    insert into public.tenants (id, slug, name, stamps_per_card, daily_stamp_limit)
    values (gen_random_uuid(), 'limit-test', 'Tageslimit', 50, 2)
    returning id into v_t;

    insert into auth.users default values returning id into v_kunde;
    insert into auth.users default values returning id into v_zwei;
    insert into public.memberships (user_id, tenant_id) values (v_kunde, v_t);
    insert into public.memberships (user_id, tenant_id) values (v_zwei, v_t);

    call auth.become(v_kunde);
    perform public.issue_stamp(v_t, test.bon(p_tx => '2001'));
    perform public.issue_stamp(v_t, test.bon(p_tx => '2002'));
    begin
        perform public.issue_stamp(v_t, test.bon(p_tx => '2003'));
        v_ok := false;
    exception when data_exception then
        v_ok := true;
    end;
    call test.check(v_ok, 'Über dem Tageslimit gibt es keinen Stempel mehr');

    select count(*) into v_zahl from public.stamps where tenant_id = v_t and user_id = v_kunde;
    call test.check(v_zahl = 2, 'Genau die erlaubten Stempel stehen auf der Karte');

    -- Das Limit gilt je Kunde, nicht je Betrieb.
    call auth.become(v_zwei);
    perform public.issue_stamp(v_t, test.bon(p_tx => '2004'));
    select count(*) into v_zahl from public.stamps where tenant_id = v_t and user_id = v_zwei;
    call test.check(v_zahl = 1, 'Das Limit trifft nur den einen Kunden');

    raise notice '--- Tageslimit bestanden ---';
end
$$;

-- ============================ Die Kassenliste ist nichts für Kunden
do $$
declare
    v_t     uuid;
    v_kunde uuid;
    v_zahl  int;
begin
    select id into v_t from public.tenants where slug = 'beleg-test';
    insert into auth.users default values returning id into v_kunde;
    insert into public.memberships (user_id, tenant_id) values (v_kunde, v_t);

    call auth.become(v_kunde);
    set local role authenticated;
    select count(*) into v_zahl from public.tenant_registers;
    reset role;
    call test.check(v_zahl = 0, 'Ein Kunde sieht die Kassen des Betriebs nicht');

    raise notice '--- Kassenliste bestanden ---';
end
$$;
