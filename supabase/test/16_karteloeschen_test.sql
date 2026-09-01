-- ============================================================================
-- Karte loeschen - Art. 17 ohne Konto
--
-- Anonymitaet gilt als datenschutzfreundlich, macht dieses Betroffenenrecht
-- aber schwerer ausuebbar statt leichter: Es gibt keine Adresse, unter der
-- ein Kunde sich melden koennte, und der Betrieb kann ihn nicht heraussuchen.
-- Das Geraet ist der einzige Weg - also muss dieser Weg vollstaendig sein.
--
-- "Vollstaendig" heisst hier: dieselben fuenf Tabellen, die adopt_card
-- umzieht. Bleibt eine liegen, behauptet die App eine Loeschung, die nicht
-- stattgefunden hat - schlimmer als gar kein Knopf.
--
-- Die zweite Haelfte prueft die Gegenrichtung: dass nur die eine Karte faellt
-- und nicht die des Nachbarn oder die desselben Kunden im anderen Betrieb.
-- ============================================================================
do $$
declare
    v_a        uuid;   -- Betrieb, in dem geloescht wird
    v_b        uuid;   -- zweiter Betrieb desselben Kunden
    v_kunde    uuid;
    v_fremd    uuid;   -- anderer Kunde im selben Betrieb
    v_demo     uuid;
    v_angebot  uuid;
    v_anzahl   int;
    v_ok       boolean;
    v_erg      record;
begin
    insert into public.tenants (slug, name) values ('loesch-a', 'Betrieb A')
    returning id into v_a;
    insert into public.tenants (slug, name) values ('loesch-b', 'Betrieb B')
    returning id into v_b;

    insert into auth.users default values returning id into v_kunde;
    insert into auth.users default values returning id into v_fremd;
    insert into auth.users default values returning id into v_demo;

    insert into public.tenant_staff (user_id, tenant_id, role)
         values (v_demo, v_a, 'demo');

    insert into public.offers (tenant_id, title, is_redeemable)
         values (v_a, 'Angebot', true) returning id into v_angebot;

    -- Der Kunde hat in A alles, was eine Karte ausmachen kann.
    insert into public.memberships (user_id, tenant_id) values (v_kunde, v_a);
    insert into public.stamps (id, user_id, tenant_id)
         select gen_random_uuid(), v_kunde, v_a from generate_series(1, 3);
    insert into public.stamp_proofs (user_id, tenant_id, proof_ref, source)
         values (v_kunde, v_a, 'beleg-1', 'receipt'), (v_kunde, v_a, 'beleg-2', 'receipt');
    insert into public.vouchers (id, creation_date, expires_at, is_redeemed, user_id, tenant_id)
         values (gen_random_uuid(), (extract(epoch from now()) * 1000)::int8,
                 (extract(epoch from now() + interval '30 days') * 1000)::int8,
                 false, v_kunde, v_a);
    insert into public.offer_redemptions (offer_id, user_id, tenant_id, sperre)
         values (v_angebot, v_kunde, v_a, current_date);

    -- Derselbe Kunde in B, und ein fremder Kunde in A. Beide sind die
    -- eigentliche Pruefung: Sie duerfen nichts verlieren.
    insert into public.memberships (user_id, tenant_id) values (v_kunde, v_b);
    insert into public.stamps (id, user_id, tenant_id)
         values (gen_random_uuid(), v_kunde, v_b);
    insert into public.memberships (user_id, tenant_id) values (v_fremd, v_a);
    insert into public.stamps (id, user_id, tenant_id)
         values (gen_random_uuid(), v_fremd, v_a);

    -- --------------------------------------------------- ohne Anmeldung
    set local role anon;
    begin
        perform public.delete_card('loesch-a');
        v_ok := false;
    exception when others then
        v_ok := true;
    end;
    call test.check(v_ok, 'Ohne Anmeldung loescht niemand etwas');

    -- ------------------------------------------------------- Demozugang
    call auth.become(v_demo);
    set local role authenticated;
    begin
        perform public.delete_card('loesch-a');
        v_ok := false;
    exception when others then
        v_ok := sqlerrm like '%read only%';
    end;
    call test.check(v_ok, 'Die Demo loescht keine Karte');

    -- --------------------------------------------------- unbekannter Slug
    call auth.become(v_kunde);
    set local role authenticated;
    begin
        perform public.delete_card('gibt-es-nicht');
        v_ok := false;
    exception when others then
        v_ok := sqlerrm like '%tenant not found%';
    end;
    call test.check(v_ok, 'Ein unbekannter Betrieb ist ein Fehler, kein Erfolg');

    -- ----------------------------------------------- fremder Kunde ohne Karte
    call auth.become(v_fremd);
    set local role authenticated;
    begin
        perform public.delete_card('loesch-b');
        v_ok := false;
    exception when others then
        v_ok := sqlerrm like '%no card%';
    end;
    call test.check(v_ok, 'Ohne Karte im Betrieb gibt es nichts zu loeschen');

    -- ------------------------------------------------------------ loeschen
    call auth.become(v_kunde);
    set local role authenticated;
    select * into v_erg from public.delete_card('loesch-a');

    call test.check(v_erg.tenant_name = 'Betrieb A', 'Die Antwort nennt den Betrieb');
    call test.check(v_erg.stamps_deleted = 3,   'Drei Stempel geloescht');
    call test.check(v_erg.proofs_deleted = 2,   'Zwei Nachweise geloescht');
    call test.check(v_erg.vouchers_deleted = 1, 'Ein Gutschein geloescht');
    call test.check(v_erg.offers_deleted = 1,   'Eine Angebotseinloesung geloescht');
    call test.check(v_erg.cards_left = 1,       'Eine Karte bleibt - die in Betrieb B');

    /*
     * Der Strich an der Wand. Er ist die einzige Spur, die eine Loeschung
     * hinterlassen darf - und er darf keinen Personenbezug tragen, sonst
     * waere der Zaehler selbst wieder das, was geloescht werden sollte.
     */
    reset role;
    select count(*) into v_anzahl from public.card_deletions where tenant_id = v_a;
    call test.check(v_anzahl = 1, 'Die Loeschung ist als Kennzahl vermerkt');
    select count(*) into v_anzahl from public.card_deletions
     where tenant_id = v_a and stamps_at_deletion = 3;
    call test.check(v_anzahl = 1, 'Mit dem Fuellstand von drei Stempeln');
    select count(*) into v_anzahl
      from information_schema.columns
     where table_schema = 'public' and table_name = 'card_deletions'
       and column_name = 'user_id';
    call test.check(v_anzahl = 0, 'Und ohne jeden Verweis auf die Person');

    select count(*) into v_anzahl from public.card_deletions where tenant_id = v_b;
    call test.check(v_anzahl = 0, 'Betrieb B hat keine Loeschung');

    call auth.become(v_kunde);
    set local role authenticated;

    /*
     * Ohne Rolle nachsehen. Unter `authenticated` zeigt die Policy dem Kunden
     * ohnehin nur eigene Zeilen - null Zeilen hiessen dort "unsichtbar", nicht
     * "geloescht". Genau diese Verwechslung hat in diesem Projekt schon
     * zweimal einen Test gruen gemacht, der nichts belegte.
     */
    reset role;

    select count(*) into v_anzahl from public.stamps
     where user_id = v_kunde and tenant_id = v_a;
    call test.check(v_anzahl = 0, 'Keine Stempel mehr in A');
    select count(*) into v_anzahl from public.stamp_proofs
     where user_id = v_kunde and tenant_id = v_a;
    call test.check(v_anzahl = 0, 'Keine Nachweise mehr in A');
    select count(*) into v_anzahl from public.vouchers
     where user_id = v_kunde and tenant_id = v_a;
    call test.check(v_anzahl = 0, 'Keine Gutscheine mehr in A');
    select count(*) into v_anzahl from public.offer_redemptions
     where user_id = v_kunde and tenant_id = v_a;
    call test.check(v_anzahl = 0, 'Keine Angebotseinloesungen mehr in A');
    select count(*) into v_anzahl from public.memberships
     where user_id = v_kunde and tenant_id = v_a;
    call test.check(v_anzahl = 0, 'Keine Mitgliedschaft mehr in A');

    -- ------------------------------------------------ was bleiben musste
    select count(*) into v_anzahl from public.memberships
     where user_id = v_kunde and tenant_id = v_b;
    call test.check(v_anzahl = 1, 'Die Karte in Betrieb B ist unberuehrt');
    select count(*) into v_anzahl from public.stamps
     where user_id = v_kunde and tenant_id = v_b;
    call test.check(v_anzahl = 1, 'Ihr Stempel auch');
    select count(*) into v_anzahl from public.memberships
     where user_id = v_fremd and tenant_id = v_a;
    call test.check(v_anzahl = 1, 'Die Karte des anderen Kunden ist unberuehrt');
    select count(*) into v_anzahl from public.stamps
     where user_id = v_fremd and tenant_id = v_a;
    call test.check(v_anzahl = 1, 'Sein Stempel auch');

    -- Das Angebot selbst gehoert dem Betrieb, nicht dem Kunden.
    select count(*) into v_anzahl from public.offers where id = v_angebot;
    call test.check(v_anzahl = 1, 'Das Angebot des Betriebs bleibt bestehen');

    -- --------------------------------------------- in der Sicht des Betriebs
    reset role;
    select cards_deleted into v_anzahl from public.pilot_cohorts where tenant_id = v_a;
    call test.check(v_anzahl = 1, 'Die Kohortensicht zaehlt die geloeschte Karte');
    select cards_deleted_30d into v_anzahl from public.pilot_cohorts where tenant_id = v_a;
    call test.check(v_anzahl = 1, 'Und zwar auch im Fenster der letzten 30 Tage');
    select cards from public.pilot_cohorts where tenant_id = v_a into v_anzahl;
    call test.check(v_anzahl = 1, 'Gezaehlt wird nur noch die verbliebene Karte');

    call auth.become(v_kunde);
    set local role authenticated;

    -- ------------------------------------------------------- zweiter Aufruf
    call auth.become(v_kunde);
    set local role authenticated;
    begin
        perform public.delete_card('loesch-a');
        v_ok := false;
    exception when others then
        v_ok := sqlerrm like '%no card%';
    end;
    call test.check(v_ok, 'Ein zweiter Aufruf faellt auf - die Karte ist weg');

    /*
     * Die Zaehlzeile ist eine Betriebszahl, keine Kundenzahl. Aus dem Browser
     * darf sie niemand lesen - der Betrieb sieht sie ueber pilot_cohorts,
     * und dorthin kommt nur, wer staff_pilot_cohorts() aufrufen darf.
     */
    call test.check(
        not has_table_privilege('anon', 'public.card_deletions', 'SELECT'),
        'anon liest die Loeschzahlen nicht');
    call test.check(
        not has_table_privilege('authenticated', 'public.card_deletions', 'SELECT'),
        'Auch ein angemeldeter Kunde liest sie nicht');
    call test.check(
        not has_table_privilege('authenticated', 'public.card_deletions', 'INSERT'),
        'Und schreibt sie erst recht nicht');

    reset role;
    raise notice '--- Karte loeschen bestanden ---';
end;
$$;
