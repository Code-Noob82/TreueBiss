-- ============================================================================
-- Landbaeckerei Sonnfeld anlegen
--
-- Ein zweiter Betrieb im laufenden Projekt, zum Durchspielen der Oberflaeche.
-- Nicht die Demo: Die zeigt einem Interessenten fertige Zahlen und ist
-- schreibgeschuetzt. Dieser hier ist leer und laesst alles zu - er
-- beantwortet die Frage "wie fuehlt sich das an, wenn man bei null anfaengt".
--
-- Bewusst ohne erfundene Daten. Wer jede Zahl selbst erzeugt, sieht auch,
-- welche sich dabei nicht bewegt - und genau das ist der Zweck.
--
-- Alle Schalter stehen an, damit nichts unbetretbar bleibt: Tresen-Code,
-- Willkommensstempel, freie Nachweise. Was davon im Alltag taugt, entscheidet
-- sich beim Durchklicken, nicht hier.
--
-- Wiederholbar: Ein zweiter Lauf legt nichts doppelt an und loescht nichts.
--
-- VORHER in Supabase anlegen (Authentication > Users > Add user):
--   sonnfeld-chef@treuebiss.de    - sieht und aendert alles
--   sonnfeld-kasse@treuebiss.de   - nur die Kasse
-- ============================================================================
do $$
declare
    -- ---- anpassen -----------------------------------------------------
    c_slug  constant text := 'sonnfeld';
    c_name  constant text := 'Landbäckerei Sonnfeld';
    c_chef  constant text := 'sonnfeld-chef@treuebiss.de';
    c_kasse constant text := 'sonnfeld-kasse@treuebiss.de';
    -- -------------------------------------------------------------------
    v_tenant uuid;
    v_chef   uuid;
    v_kassa  uuid;
    v_karten int;
begin
    select id into v_chef  from auth.users where email = c_chef;
    select id into v_kassa from auth.users where email = c_kasse;
    if v_chef is null or v_kassa is null then
        raise exception
            'Konten fehlen. Erst in Supabase anlegen (Authentication > Users > Add user): % und %',
            c_chef, c_kasse;
    end if;

    -- Betrieb. Beim zweiten Lauf bleiben Kartengroesse und Praemie stehen -
    -- wer sie in der Verwaltung geaendert hat, soll sie nicht verlieren.
    insert into public.tenants (slug, name, stamps_per_card, voucher_validity_days,
                                counter_qr_enabled, counter_qr_seconds,
                                welcome_stamp_enabled, allow_opaque_proofs,
                                daily_stamp_limit, primary_color)
         values (c_slug, c_name, 10, 30, true, 60, true, true, 25, '#7A9A3B')
    on conflict (slug) do nothing;

    select id into v_tenant from public.tenants where slug = c_slug;

    -- Schluessel fuer den Tresen-Code. Ohne ihn steht der Schalter an und
    -- counter_token gibt nichts zurueck - ein Knopf, der nichts tut.
    insert into public.tenant_secrets (tenant_id, counter_secret)
         values (v_tenant, encode(extensions.gen_random_bytes(32), 'hex'))
    on conflict (tenant_id) do update
       set counter_secret = coalesce(public.tenant_secrets.counter_secret,
                                     excluded.counter_secret);

    -- Zugaenge.
    insert into public.tenant_staff (user_id, tenant_id, role)
         values (v_chef, v_tenant, 'owner'), (v_kassa, v_tenant, 'staff')
    on conflict (user_id, tenant_id) do update set role = excluded.role;

    select count(*) into v_karten from public.memberships where tenant_id = v_tenant;

    raise notice '';
    raise notice 'Landbäckerei Sonnfeld steht.';
    raise notice '  Karte:      10 Stempel, Gutschein 30 Tage gültig';
    raise notice '  Angeschaltet: Tresen-Code (60 s), Willkommensstempel, freie Nachweise';
    raise notice '  Karten im Umlauf: %', v_karten;
    raise notice '';
    raise notice 'Kundenkarte  https://byte-und-handwerk.github.io/TreueBiss/app/?b=%', c_slug;
    raise notice 'Kasse        https://byte-und-handwerk.github.io/TreueBiss/kasse/';
    raise notice 'Verwaltung   https://byte-und-handwerk.github.io/TreueBiss/verwaltung/';
    raise notice '';
    raise notice 'Ein Einlösecode ist nicht gesetzt. Prämien lassen sich damit ohne';
    raise notice 'Code einlösen - setzen lässt er sich in der Verwaltung.';
end;
$$;
