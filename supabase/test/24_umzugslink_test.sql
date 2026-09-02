-- ============================================================================
-- Ein Umzugslink ist kein Kaufnachweis
--
-- Der QR auf dem Wallet-Pass traegt den Kartenschluessel. Am 02.09.2026 beim
-- Durchspielen gescannt - und der Stempel-Scanner schickte ihn als freien
-- Nachweis los. Es entstand eine zweite Karte mit einem Stempel, die
-- urspruengliche behielt ihre Punkte, und weil das neue Geraet jetzt Stempel
-- hatte, verweigerte adopt_card den Umzug: "device already has a card here".
--
-- Der Code zerstoerte damit genau das, wofuer er gedacht war.
-- ============================================================================
do $$
declare
    v_b      uuid;
    v_alt    uuid;
    v_neu    uuid;
    v_token  text;
    v_link   text;
    v_ok     boolean;
    v_anzahl int;
    v_erg    record;
begin
    insert into public.tenants (slug, name, stamps_per_card, allow_opaque_proofs)
         values ('umzug', 'Umzugsbetrieb', 10, true) returning id into v_b;
    insert into auth.users default values returning id into v_alt;
    insert into auth.users default values returning id into v_neu;

    -- Die erste Karte sammelt drei Stempel.
    perform public.issue_stamp_intern(v_alt, v_b, 'beleg-' || i, null, null)
       from generate_series(1, 3) as g(i);
    select card_token into v_token from public.memberships
     where user_id = v_alt and tenant_id = v_b;
    call test.check(length(v_token) = 64, 'Die Karte hat einen Umzugsschluessel');

    v_link := 'https://byte-und-handwerk.github.io/TreueBiss/app/?b=umzug&karte='
              || v_token;

    -- ----------------------------------- Der Link zaehlt nicht als Nachweis
    begin
        perform public.issue_stamp_intern(v_neu, v_b, v_link, null, null);
        v_ok := false;
    exception when others then
        v_ok := sqlerrm like '%card link is not a proof%';
    end;
    call test.check(v_ok, 'Der Umzugslink wird als Nachweis abgewiesen');

    select count(*) into v_anzahl from public.memberships where tenant_id = v_b;
    call test.check(v_anzahl = 1, 'Und legt keine zweite Karte an');

    -- Auch der nackte Schluessel ohne Adresse drumherum.
    begin
        perform public.issue_stamp_intern(v_neu, v_b, v_token, null, null);
        v_ok := false;
    exception when others then
        v_ok := sqlerrm like '%card link is not a proof%';
    end;
    call test.check(v_ok, 'Der nackte Schluessel ebenso');

    -- ------------------------------------------- Der Umzug geht weiterhin
    call auth.become(v_neu);
    select * into v_erg from public.adopt_card(v_token);
    reset role;
    call test.check(v_erg.stamps = 3, 'Der Umzug bringt alle drei Stempel mit');

    select count(*) into v_anzahl from public.stamps
     where tenant_id = v_b and user_id = v_neu;
    call test.check(v_anzahl = 3, 'Sie liegen jetzt auf dem neuen Geraet');
    select count(*) into v_anzahl from public.stamps
     where tenant_id = v_b and user_id = v_alt;
    call test.check(v_anzahl = 0, 'Und nicht mehr auf dem alten');
    select count(*) into v_anzahl from public.memberships where tenant_id = v_b;
    call test.check(v_anzahl = 1, 'Es bleibt bei einer Karte');

    -- ------------------------------- Ein echter Beleg geht weiterhin durch
    perform public.issue_stamp_intern(v_neu, v_b, 'ganz-normaler-beleg', null, null);
    select count(*) into v_anzahl from public.stamps
     where tenant_id = v_b and user_id = v_neu;
    call test.check(v_anzahl = 4, 'Ein gewoehnlicher Nachweis zaehlt weiterhin');

    raise notice '--- Umzugslink bestanden ---';
end;
$$;
