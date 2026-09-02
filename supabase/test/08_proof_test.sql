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
    /*
     * Seit dem 02.09.2026 gehasht. Der Schlüssel entsteht weiterhin aus
     * Kasse, Transaktion und Zähler - er steht nur nicht mehr im Klartext in
     * der Tabelle, weil er dort die Löschfrist für die Kassennummer
     * überlebte. Geprüft wird deshalb gegen den Hash genau dieser drei
     * Bestandteile, nicht gegen irgendeinen Hash.
     */
    call test.check(
        v_ref = encode(extensions.digest('AMA-2642:1001:44131', 'sha256'), 'hex'),
        'Der Schlüssel ist der Hash aus Kasse, Transaktion und Zähler');
    call test.check(position('AMA-2642' in v_ref) = 0,
                    'Die Kassennummer steht nicht mehr im Schlüssel');
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

-- ============================ Signaturpflicht
do $$
declare
    v_t     uuid;
    v_kunde uuid;
    v_ok    boolean;
    v_zahl  int;
begin
    insert into public.tenants (id, slug, name, stamps_per_card)
    values (gen_random_uuid(), 'signatur-test', 'Signaturpflicht', 50)
    returning id into v_t;
    insert into auth.users default values returning id into v_kunde;
    insert into public.memberships (user_id, tenant_id) values (v_kunde, v_t);

    update public.tenants set require_signed_proof = true where id = v_t;

    -- ------------------------------------------ Der gewöhnliche Weg ist zu
    call auth.become(v_kunde);
    begin
        perform public.issue_stamp(v_t, test.bon(p_tx => '3001'));
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Mit Signaturpflicht geht der gewöhnliche Weg nicht mehr');

    -- Auch nicht mit einem freien Nachweis - sonst wäre die Pflicht umgehbar.
    begin
        perform public.issue_stamp(v_t, 'IRGENDEINE-NUMMER');
        v_ok := false;
    exception when data_exception then
        v_ok := true;
    end;
    call test.check(v_ok, 'Mit Signaturpflicht zählt auch kein freier Nachweis');

    -- ------------------------------------------ Der geprüfte Weg geht
    perform public.service_issue_stamp(v_kunde, v_t, test.bon(p_tx => '3002'));
    select count(*) into v_zahl from public.stamps where tenant_id = v_t;
    call test.check(v_zahl = 1, 'Über die geprüfte Vergabe entsteht der Stempel');

    select count(*) into v_zahl from public.stamp_proofs
     where tenant_id = v_t and signature_verified;
    call test.check(v_zahl = 1, 'Der Nachweis ist als signaturgeprüft vermerkt');

    -- ------------------------------------------ Die App darf das nicht
    set local role authenticated;
    begin
        perform public.service_issue_stamp(v_kunde, v_t, test.bon(p_tx => '3003'));
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    reset role;
    call test.check(v_ok, 'Die App kann sich die Prüfung nicht selbst bescheinigen');

    -- ------------------------------------------ Die Regeln gelten weiter
    begin
        perform public.service_issue_stamp(
            v_kunde, v_t, test.bon(p_tx => '3004', p_zeit => now() - interval '5 hours'));
        v_ok := false;
    exception when data_exception then
        v_ok := true;
    end;
    call test.check(v_ok, 'Auch ein signierter Beleg darf nicht zu alt sein');

    -- Auch der geprüfte Weg legt die Karte an, wenn es noch keine gibt -
    -- seit dem 02.09.2026 entsteht sie mit dem ersten Stempel, nicht beim
    -- Öffnen. Der Nutzer ist hier ein Parameter, deshalb geht das nur über
    -- den Helfer, der keine Sitzung braucht.
    declare v_fremd uuid;
    begin
        insert into auth.users default values returning id into v_fremd;
        perform public.service_issue_stamp(v_fremd, v_t, test.bon(p_tx => '3005'));
        select count(*) = 1 into v_ok from public.memberships
         where user_id = v_fremd and tenant_id = v_t;
        call test.check(v_ok, 'Auch der geprüfte Weg legt die Karte an');
    end;

    raise notice '--- Signaturpflicht bestanden ---';
end
$$;

-- ============================ Tresen-QR
do $$
declare
    v_t      uuid;
    v_chef   uuid;
    v_kasse  uuid;
    v_kunde  uuid;
    v_zwei   uuid;
    v_token  text;
    v_alt    text;
    v_ok     boolean;
    v_zahl   int;
begin
    insert into public.tenants (id, slug, name, stamps_per_card)
    values (gen_random_uuid(), 'tresen-test', 'Tresen', 50)
    returning id into v_t;
    insert into auth.users default values returning id into v_chef;
    insert into auth.users default values returning id into v_kasse;
    insert into auth.users default values returning id into v_kunde;
    insert into auth.users default values returning id into v_zwei;
    insert into public.tenant_staff (user_id, tenant_id, role) values (v_chef, v_t, 'owner');
    insert into public.tenant_staff (user_id, tenant_id) values (v_kasse, v_t);
    insert into public.memberships (user_id, tenant_id) values (v_kunde, v_t);
    insert into public.memberships (user_id, tenant_id) values (v_zwei, v_t);

    -- ------------------------------------------ Ausgeschaltet zählt nichts
    call auth.become(v_kunde);
    begin
        perform public.issue_stamp(v_t, 'tresen:egal');
        v_ok := false;
    exception when data_exception then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ohne eingeschalteten Tresen-QR zählt kein Tresen-Code');

    -- ------------------------------------------ Einschalten legt einen Schlüssel an
    call auth.become(v_chef);
    perform public.owner_update_proof_rules(v_t, 120, 0, 25, false, true, false, true, 60);
    select count(*) into v_zahl from public.tenant_secrets
     where tenant_id = v_t and counter_secret is not null;
    call test.check(v_zahl = 1, 'Beim Einschalten entsteht ein Schlüssel');

    -- ------------------------------------------ Das Personal bekommt den Code
    call auth.become(v_kasse);
    select token into v_token from public.staff_counter_token(v_t);
    call test.check(v_token is not null and length(v_token) = 24, 'Die Kasse bekommt einen Code');

    -- ------------------------------------------ Der Kunde nicht
    call auth.become(v_kunde);
    begin
        perform * from public.staff_counter_token(v_t);
        v_ok := false;
    exception when insufficient_privilege then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ein Kunde kann den Code nicht abrufen');

    -- ------------------------------------------ Scannen gibt einen Stempel
    perform public.issue_stamp(v_t, 'tresen:' || v_token);
    select count(*) into v_zahl from public.stamps where tenant_id = v_t and user_id = v_kunde;
    call test.check(v_zahl = 1, 'Der gescannte Tresen-Code gibt einen Stempel');

    select count(*) into v_zahl from public.stamp_proofs
     where tenant_id = v_t and source = 'counter';
    call test.check(v_zahl = 1, 'Der Nachweis ist als Tresen-Vergabe vermerkt');

    -- ------------------------------------------ Nicht zweimal derselbe Kunde
    begin
        perform public.issue_stamp(v_t, 'tresen:' || v_token);
        v_ok := false;
    exception when unique_violation then
        v_ok := true;
    end;
    call test.check(v_ok, 'Derselbe Kunde bekommt für denselben Code nur einen Stempel');

    -- ------------------------------------------ Aber die Warteschlange kommt durch
    call auth.become(v_zwei);
    perform public.issue_stamp(v_t, 'tresen:' || v_token);
    select count(*) into v_zahl from public.stamps where tenant_id = v_t and user_id = v_zwei;
    call test.check(v_zahl = 1, 'Der nächste Kunde bekommt mit demselben Code seinen Stempel');

    -- ------------------------------------------ Ein erfundener Code nicht
    begin
        perform public.issue_stamp(v_t, 'tresen:000000000000000000000000');
        v_ok := false;
    exception when data_exception then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ein erfundener Tresen-Code wird abgelehnt');

    -- ------------------------------------------ Ein Code von vorgestern auch nicht
    select public.counter_token(v_t, -50) into v_alt;
    call test.check(v_alt is distinct from v_token, 'Ein älteres Zeitfenster ergibt einen anderen Code');
    begin
        perform public.issue_stamp(v_t, 'tresen:' || v_alt);
        v_ok := false;
    exception when data_exception then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ein abgelaufener Tresen-Code wird abgelehnt');

    -- ------------------------------------------ Nachfrist für den Wechselmoment
    call auth.become(v_kunde);
    perform public.issue_stamp(v_t, 'tresen:' || public.counter_token(v_t, -1));
    call test.check(true, 'Der eben abgelaufene Code zählt noch');

    -- ------------------------------------------ Fremder Betrieb, fremder Code
    declare v_fremd uuid;
    begin
        insert into public.tenants (id, slug, name) values (gen_random_uuid(), 'tresen-fremd', 'Fremd')
        returning id into v_fremd;
        call test.check(public.counter_token(v_fremd, 0) is null,
                        'Ohne Schlüssel gibt es keinen Code');
    end;

    raise notice '--- Tresen-QR bestanden ---';
end
$$;
