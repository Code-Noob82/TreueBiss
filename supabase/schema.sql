-- TreueBiss - Datenbankschema (Supabase / Postgres)
--
-- ACHTUNG Reihenfolge: Das Skript laeuft von oben nach unten. Jede Anweisung
-- darf nur auf Objekte verweisen, die weiter oben stehen. Der Aufbau ist:
--
--   1. Tabellen (tenants zuerst, danach alles was darauf verweist)
--   2. Bestehende Installationen nachziehen (Spalten, Umzuege)
--   3. Row Level Security und Policies
--   4. Funktionen fuer Vergabe und Einloesen
--   5. Beispieldaten
--   6. Views fuer die Auswertung
--   7. Funktionen, die auf Views aufbauen
--
-- Eine an der falschen Stelle eingefuegte Anweisung faellt auf einer frischen
-- Datenbank sofort um - `supabase/test/run.sh` deckt genau das ab.
-- Pflege von tenants und offers laeuft ueber den Service-Role-Key oder das
-- Supabase-Studio, nicht aus der App. Genau hier dockt spaeter ein Admin-
-- Backend an, ohne dass der Rest angefasst werden muss.

-- pgcrypto liefert crypt()/gen_salt() fuer den Einloese-Code. In Supabase
-- ist die Erweiterung bereits im Schema "extensions" installiert; die beiden
-- Zeilen sind dort wirkungslos.
create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;

-- ---------------------------------------------------------------- Betriebe
create table if not exists public.tenants (
    id                    uuid primary key default gen_random_uuid(),
    -- Kurzname fuer Beitrittscodes und Deep-Links, z. B. "baeckerei-mustermann"
    slug                  text unique not null,
    name                  text not null,
    loyalty_points_title  text not null default 'Treuepunkte',
    vouchers_title        text not null default 'Gutscheine',
    daily_special_title   text not null default 'Angebot des Tages',
    -- Primaerfarbe als Hex-String, z. B. "#4CAF50"
    primary_color         text,
    logo_url              text,
    stamps_per_card       int  not null default 10 check (stamps_per_card between 1 and 50),
    voucher_validity_days int  not null default 90 check (voucher_validity_days > 0),
    is_active             boolean not null default true,
    -- Wenn true, muss beim Einloesen der Code eingegeben werden. Standard ist
    -- false: Der Kunde loest selbst ein, die App zeigt eine zeitgebundene
    -- Bestaetigung, auf die das Personal nur kurz schaut. Betriebe, die es
    -- strenger wollen, schalten das Flag ein.
    requires_redeem_code  boolean not null default false,
    created_at            timestamptz not null default now()
);

-- ------------------------------------------------- Geheimnisse des Betriebs
-- Getrennt von tenants, und zwar aus einem konkreten Grund: Die Lese-Policy
-- auf tenants gilt fuer die ganze Zeile. Solange der Hash dort lag, konnte
-- ihn jeder angemeldete App-Nutzer mitlesen - ein bcrypt-Hash ueber einen
-- kurzen Code ist offline in Sekunden geknackt. Der Code haette also genau
-- den nicht aufgehalten, gegen den er gerichtet ist.
--
-- Auf diese Tabelle kommt niemand: RLS an, keine einzige Policy. Nur die
-- security-definer-Funktionen unten lesen sie.
create table if not exists public.tenant_secrets (
    tenant_id        uuid primary key references public.tenants(id) on delete cascade,
    -- bcrypt-Hash des Einloese-Codes. Der Code selbst wird nie gespeichert.
    redeem_code_hash text not null,
    updated_at       timestamptz not null default now()
);

-- Schluessel fuer den rotierenden Tresen-QR. Liegt hier, weil diese Tabelle
-- keine Policy hat: Wer ihn lesen koennte, koennte sich beliebig viele
-- gueltige Codes ausrechnen.
alter table public.tenant_secrets
    add column if not exists counter_secret text;

/*
 * Ein Betrieb kann ein Geheimnis haben, ohne einen Einloese-Code zu haben:
 * Der Tresen-QR legt hier einen Schluessel an, lange bevor irgendjemand
 * einen Code setzt. Solange die Spalte `not null` war, musste dafuer ''
 * eingetragen werden - und '' ist fuer crypt() kein gueltiger Salt, sondern
 * ein Fehler. Beim naechsten Einspielen brach das Schema deshalb mit
 * "invalid salt" ab, sobald ein Betrieb den Tresen-QR eingeschaltet hatte.
 *
 * NULL ist hier auch inhaltlich das Richtige: "kein Code gesetzt" ist keine
 * leere Zeichenkette. crypt() ist strikt und gibt bei NULL NULL zurueck -
 * die Aufraeumschritte unten laufen daran vorbei, statt zu werfen.
 */
alter table public.tenant_secrets alter column redeem_code_hash drop not null;
update public.tenant_secrets set redeem_code_hash = null where redeem_code_hash = '';

-- Bestehende Projekte nachziehen: Das `create table if not exists` oben
-- laesst eine vorhandene tenants-Tabelle unveraendert, also fehlt dort die
-- Spalte. Muss vor allem stehen, was sie verwendet.
alter table public.tenants
    add column if not exists requires_redeem_code boolean not null default false;

/*
 * Aufbewahrung der Kaufnachweise.
 *
 * Art. 5 Abs. 1 lit. e DSGVO laesst eine Speicherung nur so lange zu, wie sie
 * fuer den Zweck erforderlich ist; Art. 17 Abs. 1 lit. a macht daraus eine
 * Loeschpflicht, sobald der Zweck entfaellt. Eine gesetzliche Aufbewahrungs-
 * pflicht steht dem nicht entgegen: § 147 AO verpflichtet den Steuer-
 * pflichtigen zu seinen eigenen Buchungsbelegen - das ist der Betrieb mit
 * seiner Kasse, nicht diese App mit ihrem Verweis darauf.
 *
 * Zwei Fristen, weil zwei verschiedene Zwecke ablaufen:
 *
 *   proof_detail_days     Wie lange Betrag und Kassennummer stehen bleiben.
 *                         Sie werden beim Stempeln geprueft und danach nur
 *                         noch gebraucht, um einen strittigen Stempel zu
 *                         erklaeren. Danach werden sie geleert, die Zeile
 *                         bleibt.
 *   proof_retention_days  Wie lange der Nachweis selbst steht. Er verhindert,
 *                         dass derselbe Beleg zweimal zaehlt.
 *
 * Die Vorgaben sind eine Abwaegung und keine Rechtsauskunft - deshalb stehen
 * sie als Spalte da und nicht als Konstante im Code. Was sich dagegen ableiten
 * laesst, ist die Untergrenze, und die steht als Bedingung darunter.
 */
alter table public.tenants
    add column if not exists proof_detail_days int not null default 30
        check (proof_detail_days between 1 and 365);
alter table public.tenants
    add column if not exists proof_retention_days int not null default 90
        check (proof_retention_days between 1 and 3650);

do $$
begin
    -- Erst leeren, dann loeschen. Andersherum ergaebe die kuerzere Frist keinen Sinn.
    alter table public.tenants add constraint tenants_detail_vor_retention
        check (proof_detail_days <= proof_retention_days);
exception when duplicate_object then null;
end
$$;

-- Regeln fuer die Pruefung des Kaufnachweises. Die Vorgaben sind bewusst
-- milde: Ein bestehendes Projekt darf durch das Einspielen nicht ploetzlich
-- Stempel ablehnen. Scharf stellt der Betrieb selbst in der Verwaltung.
alter table public.tenants
    -- Wie alt der Beleg hoechstens sein darf. Der entscheidende Wert gegen
    -- eingesammelte Bons: Was im Laden liegen bleibt, ist meist Stunden alt.
    add column if not exists proof_max_age_minutes int not null default 120
        check (proof_max_age_minutes between 1 and 43200),
    -- Mindestbetrag in Cent. 0 heisst: jeder Betrag zaehlt.
    add column if not exists proof_min_cents int not null default 0
        check (proof_min_cents >= 0),
    -- Hoechstzahl Stempel je Kunde und Tag. Die Vorgabe ist bewusst hoch:
    -- Sie ist ein Fangnetz gegen Massenmissbrauch, nicht das eigentliche
    -- Mittel - das sind Zeitfenster und Kassenliste. Ein echter Kunde kommt
    -- nie in die Naehe; ein bestehendes Projekt faellt nicht um. Wer scharf
    -- stellen will, setzt in der Verwaltung 2 oder 3.
    add column if not exists daily_stamp_limit int not null default 25
        check (daily_stamp_limit between 1 and 100),
    -- Nur Belege von Kassen, die der Betrieb eingetragen hat.
    add column if not exists require_known_register boolean not null default false,
    -- Duerfen auch Nachweise ohne Beleg-QR zaehlen? Solange der Scanner
    -- fehlt, muss das an bleiben - sonst faellt der Demo-Betrieb um. Wer
    -- produktiv scannt, schaltet es aus, sonst ist die ganze Pruefung
    -- umgehbar, indem man irgendeine Zeichenkette schickt.
    add column if not exists allow_opaque_proofs boolean not null default true,
    -- Verlangt eine geprüfte ECDSA-Signatur. Nur einschaltbar, wo die Edge
    -- Function `beleg-pruefen` ausgerollt ist - sonst kommt kein Stempel
    -- mehr durch.
    add column if not exists require_signed_proof boolean not null default false,
    -- Zweiter Vergabeweg: ein rotierender QR-Code am Tresen. Braucht keinen
    -- Beleg-QR und keine Kasse, die einen druckt.
    add column if not exists counter_qr_enabled boolean not null default false,
    -- Wie lange ein Tresen-Code gilt. Kurz genug, dass ein abfotografierter
    -- Code nichts nuetzt; lang genug, dass eine Warteschlange durchkommt.
    add column if not exists counter_qr_seconds int not null default 60
        check (counter_qr_seconds between 15 and 900);

do $$
begin
    /*
     * Die harte Untergrenze, und sie ist abgeleitet, nicht gewaehlt:
     *
     * Ein Beleg wird abgelehnt, sobald er aelter ist als
     * proof_max_age_minutes - die Pruefung steht vor dem Eindeutigkeits-
     * schluessel. Wird der Nachweis frueher geloescht als dieses Fenster
     * reicht, laesst sich derselbe Beleg innerhalb seiner Gueltigkeit erneut
     * einreichen. Ausserdem zaehlt das Tageslimit die Nachweise des laufenden
     * Tages; ein Tag ist deshalb das Minimum.
     */
    alter table public.tenants add constraint tenants_retention_deckt_pruefzeitfenster
        check (proof_retention_days * 1440 >= proof_max_age_minutes);
exception when duplicate_object then null;
end
$$;

-- Umzug des Einloese-Codes aus tenants. Laeuft nur, wo die alte Spalte noch
-- steht; auf einer frischen Datenbank passiert nichts.
do $$
begin
    if exists (
        select 1 from information_schema.columns
         where table_schema = 'public'
           and table_name   = 'tenants'
           and column_name  = 'redeem_code_hash'
    ) then
        insert into public.tenant_secrets (tenant_id, redeem_code_hash)
        select id, redeem_code_hash
          from public.tenants
         where redeem_code_hash is not null
           -- "1234" stand frueher im Repository und wurde von einer aelteren
           -- Fassung dieses Skripts jedem Betrieb ohne Code verpasst. Der
           -- zieht nicht mit um, er verschwindet.
           -- Erst pruefen, dass ueberhaupt ein Hash dasteht: crypt() wirft
           -- bei allem, was kein gueltiger Salt ist. Ein abgebrochenes
           -- Schema ist ein schlimmerer Ausgang als ein uebersehener Code.
           and redeem_code_hash like '$%'
           and extensions.crypt('1234', redeem_code_hash) <> redeem_code_hash
        on conflict (tenant_id) do nothing;

        alter table public.tenants drop column redeem_code_hash;
    end if;
end
$$;

-- ---------------------------------------------------------------- Angebote
create table if not exists public.offers (
    id          uuid primary key default gen_random_uuid(),
    tenant_id   uuid not null references public.tenants(id) on delete cascade,
    title       text not null,
    description text,
    image_url   text,
    valid_from  date,
    valid_to    date,
    created_at  timestamptz not null default now()
);
create index if not exists offers_tenant_idx on public.offers (tenant_id);

/*
 * Ein Angebot kann ein Aushang sein oder ein Coupon.
 *
 * `is_redeemable` steht mit Absicht auf false: Bestehende Angebote sind
 * Aushaenge und duerfen durch das Einspielen nicht ploetzlich einloesbar
 * werden. Der Betrieb schaltet das je Angebot ein.
 *
 * `redeem_limit` entscheidet, was eine Wiederholung ist - einmal ueber den
 * ganzen Zeitraum oder einmal je Tag. Beides kommt in einer Baeckerei vor:
 * der verbilligte Laib zur Einfuehrung gegen den Kaffee zum Broetchen.
 */
alter table public.offers
    add column if not exists is_redeemable boolean not null default false;
alter table public.offers
    add column if not exists redeem_limit text not null default 'einmal';

do $$
begin
    alter table public.offers add constraint offers_redeem_limit_check
        check (redeem_limit in ('einmal', 'taeglich'));
exception when duplicate_object then null;
end
$$;

/*
 * Verbrauchte Coupons. Eine Zeile je Einloesung.
 *
 * Warum ueberhaupt eine eigene Tabelle: Der Anspruch aus einem Coupon
 * gehoert dem Kunden, sein Verbrauch aber dem Betrieb. Laege der Status auf
 * dem Geraet, waere er nach einer Neuinstallation zurueck - genau die
 * Luecke, die in einer produktiven Baeckerei-App seit Jahren steht.
 */
create table if not exists public.offer_redemptions (
    id          uuid primary key default gen_random_uuid(),
    offer_id    uuid not null references public.offers(id)   on delete cascade,
    user_id     uuid not null references auth.users(id)      on delete cascade,
    tenant_id   uuid not null references public.tenants(id)  on delete cascade,
    redeemed_at timestamptz not null default now(),
    /*
     * Der Schluessel, der eine Wiederholung sperrt - nicht das Datum der
     * Einloesung, das steht in redeemed_at.
     *
     * Bei 'taeglich' ist es der Tag in Europe/Berlin, bei 'einmal' ein Wert,
     * der kein Tag sein kann. Dann kollidiert jede zweite Einloesung mit der
     * ersten, ganz gleich wann sie kommt. So steht die Regel im Index und
     * nicht bloss in der Funktion, die ihn fuellt.
     *
     * Stellt ein Betrieb mitten im Zeitraum von 'taeglich' auf 'einmal' um,
     * hat ein Kunde mit alten Tageszeilen noch eine Einloesung frei. Das ist
     * gewollt: Eine geaenderte Regel wirkt nach vorn, sie nimmt niemandem
     * rueckwirkend etwas weg.
     */
    sperre      date not null,
    unique (offer_id, user_id, sperre)
);
create index if not exists offer_redemptions_user_idx
    on public.offer_redemptions (user_id, offer_id);
create index if not exists offer_redemptions_tenant_idx
    on public.offer_redemptions (tenant_id, redeemed_at);

-- ----------------------------------------------------------- Mitgliedschaft
-- Haelt fest, bei welchen Betrieben ein Nutzer sammelt. Auch im Ein-Betrieb-
-- Build vorhanden, damit die RLS-Policies unten einheitlich greifen.
create table if not exists public.memberships (
    user_id   uuid not null references auth.users(id) on delete cascade,
    tenant_id uuid not null references public.tenants(id) on delete cascade,
    joined_at timestamptz not null default now(),
    primary key (user_id, tenant_id)
);

-- ==================================================== Personal des Betriebs

-- Wer fuer einen Betrieb an der Kasse arbeitet. Getrennt von memberships:
-- Das sind Kunden, die dort sammeln - hier geht es um Beschaeftigte.
-- Angelegt wird ueber das Supabase-Dashboard oder den Service-Role-Key,
-- nicht aus der Anwendung.
create table if not exists public.tenant_staff (
    user_id   uuid not null references auth.users(id) on delete cascade,
    tenant_id uuid not null references public.tenants(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_id, tenant_id)
);

alter table public.tenant_staff enable row level security;

drop policy if exists tenant_staff_select_own on public.tenant_staff;
-- Personal sieht nur die eigene Zuordnung; angelegt wird sie nicht hier.
create policy tenant_staff_select_own on public.tenant_staff
    for select to authenticated using (auth.uid() = user_id);

-- ==================================================== Kassen des Betriebs
-- Die Seriennummer steht im Beleg-QR und ist nicht geheim - sie steht auf
-- jedem Bon. Schuetzenswert ist sie trotzdem nicht wert, breit gestreut zu
-- werden: Kunden brauchen sie nicht, das Personal schon.
create table if not exists public.tenant_registers (
    tenant_id  uuid not null references public.tenants(id) on delete cascade,
    -- Client-Id der Kasse, wie sie im QR-Code steht (z. B. "AMA-2642").
    serial     text not null,
    label      text,
    created_at timestamptz not null default now(),
    primary key (tenant_id, serial)
);

alter table public.tenant_registers enable row level security;

drop policy if exists tenant_registers_read on public.tenant_registers;
drop policy if exists tenant_registers_owner_insert on public.tenant_registers;
drop policy if exists tenant_registers_owner_delete on public.tenant_registers;

-- Arbeitet der Aufrufer fuer diesen Betrieb?
create or replace function public.is_staff_of(target_tenant uuid)
returns boolean language sql stable security invoker as $$
    select exists (
        select 1 from public.tenant_staff s
        where s.user_id = auth.uid() and s.tenant_id = target_tenant
    );
$$;

-- ============================================ Einloesen (serverseitig)

/*
 * Loest einen Gutschein ein - aber nur gegen den Einloese-Code des Betriebs.
 *
 * Ohne diesen Schritt entscheidet das Kundengeraet allein, ob ein Gutschein
 * verbraucht ist. Der Code liegt beim Personal; getippt wird er an der Kasse
 * auf dem Kundengeraet.
 *
 * Laeuft als security definer, weil die App auf vouchers kein Schreibrecht
 * hat. search_path schliesst extensions ein, damit crypt() gefunden wird -
 * in Supabase liegt pgcrypto dort.
 *
 * Fehlercodes:
 *   42501  falscher Code
 *   22023  Gutschein unbekannt, bereits eingeloest oder abgelaufen
 */
create or replace function public.redeem_voucher(
    p_voucher_id uuid,
    p_code       text default null
)
returns table (voucher_id uuid, redeemed_at timestamptz)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user     uuid := auth.uid();
    v_tenant   uuid;
    v_redeemed boolean;
    v_expires  int8;
    v_hash     text;
    v_requires boolean;
    v_now      timestamptz := now();
    v_now_ms   int8 := (extract(epoch from now()) * 1000)::int8;
begin
    if v_user is null then
        raise exception 'not authenticated' using errcode = '28000';
    end if;

    select tenant_id, is_redeemed, expires_at
      into v_tenant, v_redeemed, v_expires
      from public.vouchers
     where id = p_voucher_id and user_id = v_user;

    if not found then
        raise exception 'voucher not found' using errcode = '22023';
    end if;
    if v_redeemed then
        raise exception 'voucher already redeemed' using errcode = '22023';
    end if;
    if v_expires < v_now_ms then
        raise exception 'voucher expired' using errcode = '22023';
    end if;

    select requires_redeem_code into v_requires
      from public.tenants where id = v_tenant and is_active;

    select redeem_code_hash into v_hash
      from public.tenant_secrets where tenant_id = v_tenant;

    -- Der Code ist nur Pflicht, wenn der Betrieb ihn verlangt. Sonst loest
    -- der Kunde selbst ein; das Personal prueft die Bestaetigung per Blick,
    -- oder scannt den QR an der Kasse.
    if v_requires then
        -- '' faengt hier mit ab: Aeltere Projekte koennen den leeren Hash
        -- noch stehen haben, und crypt() wuerfe damit einen Datenbankfehler
        -- statt einer Auskunft, die die Kasse anzeigen kann.
        if v_hash is null or v_hash = '' then
            raise exception 'no redeem code configured for this tenant' using errcode = '22023';
        end if;
        if p_code is null or crypt(p_code, v_hash) <> v_hash then
            raise exception 'invalid redeem code' using errcode = '42501';
        end if;
    end if;

    update public.vouchers
       set is_redeemed = true, redeemed_at = v_now
     where id = p_voucher_id;

    return query select p_voucher_id, v_now;
end;
$$;

revoke all on function public.redeem_voucher(uuid, text) from public;
grant execute on function public.redeem_voucher(uuid, text) to authenticated;

/*
 * Loest ein Angebot ein, das der Betrieb als Coupon freigegeben hat.
 *
 * Der Unterschied zum Gutschein: Ein Gutschein ist erarbeitet und gehoert
 * genau einem Kunden, ein Coupon wird ausgegeben und steht allen offen. Was
 * beide teilen, ist die Stelle, an der ueber den Verbrauch entschieden wird -
 * hier, nicht auf dem Geraet.
 *
 * Laeuft als security definer: Die App hat auf offer_redemptions kein
 * Schreibrecht. Koennte sie schreiben, koennte sie auch loeschen.
 *
 * Fehlercodes:
 *   42501  falscher Einloese-Code
 *   23505  von diesem Kunden bereits eingeloest
 *   22023  unbekannt, nicht einloesbar, ausserhalb des Zeitraums,
 *          oder der Kunde gehoert nicht zu diesem Betrieb
 */
create or replace function public.redeem_offer(
    p_offer_id uuid,
    p_code     text default null
)
returns table (redemption_id uuid, redeemed_at timestamptz)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user     uuid := auth.uid();
    v_tenant   uuid;
    v_limit    text;
    v_von      date;
    v_bis      date;
    v_heute    date := (now() at time zone 'Europe/Berlin')::date;
    v_requires boolean;
    v_hash     text;
    v_sperre   date;
    v_id       uuid;
    v_zeit     timestamptz := now();
begin
    if v_user is null then
        raise exception 'not signed in' using errcode = '42501';
    end if;

    -- Der Betrieb muss aktiv sein, das Angebot einloesbar. Beides in einem
    -- Zug, damit ein abgeschalteter Betrieb nicht ueber seine alten Coupons
    -- weiterlaeuft.
    select o.tenant_id, o.redeem_limit, o.valid_from, o.valid_to
      into v_tenant, v_limit, v_von, v_bis
      from public.offers o
      join public.tenants t on t.id = o.tenant_id
     where o.id = p_offer_id and o.is_redeemable and t.is_active;

    if v_tenant is null then
        raise exception 'offer not redeemable' using errcode = '22023';
    end if;

    -- Derselbe Zeitraum wie in der Lese-Policy. Er steht hier ein zweites
    -- Mal, weil security definer an der Policy vorbeilaeuft: Sonst liesse
    -- sich ein abgelaufener Coupon ueber die Funktion noch einloesen.
    if (v_von is not null and v_von > v_heute)
       or (v_bis is not null and v_bis < v_heute) then
        raise exception 'offer not valid today' using errcode = '22023';
    end if;

    -- Nur wer zu diesem Betrieb gehoert. Sonst sammelte ein Fremder Coupons
    -- aus Betrieben ein, deren Karte er nie angefasst hat.
    if not exists (select 1 from public.memberships m
                    where m.user_id = v_user and m.tenant_id = v_tenant) then
        raise exception 'no membership for this tenant' using errcode = '22023';
    end if;

    -- Verlangt der Betrieb einen Code zum Einloesen, gilt das hier genauso
    -- wie beim Gutschein. Ein Kunde soll nicht zwei verschiedene Regeln
    -- erleben, je nachdem was er gerade einloest.
    select requires_redeem_code into v_requires
      from public.tenants where id = v_tenant;
    if v_requires then
        select redeem_code_hash into v_hash
          from public.tenant_secrets where tenant_id = v_tenant;
        if v_hash is null or v_hash = '' then
            raise exception 'no redeem code configured for this tenant' using errcode = '22023';
        end if;
        if p_code is null or crypt(p_code, v_hash) <> v_hash then
            raise exception 'invalid redeem code' using errcode = '42501';
        end if;
    end if;

    -- '-infinity' ist kein Tag und kann keiner werden. Bei 'einmal' stossen
    -- deshalb alle Einloesungen desselben Kunden auf denselben Schluessel.
    v_sperre := case when v_limit = 'taeglich' then v_heute else '-infinity'::date end;

    begin
        insert into public.offer_redemptions (offer_id, user_id, tenant_id, redeemed_at, sperre)
        values (p_offer_id, v_user, v_tenant, v_zeit, v_sperre)
        returning id into v_id;
    exception when unique_violation then
        raise exception 'offer already redeemed' using errcode = '23505';
    end;

    return query select v_id, v_zeit;
end;
$$;

revoke all on function public.redeem_offer(uuid, text) from public;
grant execute on function public.redeem_offer(uuid, text) to authenticated;

-- ================================================ Kassenseite des Betriebs

/*
 * Loest einen Gutschein ein - aufgerufen vom Personal, nicht vom Kunden.
 *
 * redeem_voucher() prueft user_id = auth.uid(); das Personal ist aber nicht
 * der Besitzer des Gutscheins. Hier ersetzt die Beschaeftigung des Aufrufers
 * beim Betrieb diesen Nachweis - und damit auch den Einloese-Code: Wer
 * scannt, ist der Betrieb.
 *
 * Fehlercodes:
 *   42501  Aufrufer arbeitet nicht fuer diesen Betrieb
 *   22023  Gutschein unbekannt, bereits eingeloest oder abgelaufen
 */
create or replace function public.staff_redeem_voucher(p_voucher_id uuid)
returns table (
    voucher_id  uuid,
    redeemed_at timestamptz,
    customer    uuid
)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_staff    uuid := auth.uid();
    v_tenant   uuid;
    v_owner    uuid;
    v_redeemed boolean;
    v_expires  int8;
    v_now      timestamptz := now();
begin
    if v_staff is null then
        raise exception 'not authenticated' using errcode = '28000';
    end if;

    select tenant_id, user_id, is_redeemed, expires_at
      into v_tenant, v_owner, v_redeemed, v_expires
      from public.vouchers where id = p_voucher_id;

    if not found then
        raise exception 'voucher not found' using errcode = '22023';
    end if;
    if not public.is_staff_of(v_tenant) then
        raise exception 'not staff of this tenant' using errcode = '42501';
    end if;
    if v_redeemed then
        raise exception 'voucher already redeemed' using errcode = '22023';
    end if;
    if v_expires < (extract(epoch from v_now) * 1000)::int8 then
        raise exception 'voucher expired' using errcode = '22023';
    end if;

    update public.vouchers
       set is_redeemed = true, redeemed_at = v_now
     where id = p_voucher_id;

    return query select p_voucher_id, v_now, v_owner;
end;
$$;

revoke all on function public.staff_redeem_voucher(uuid) from public;
grant execute on function public.staff_redeem_voucher(uuid) to authenticated;

-- ==================================================== Beispiel-Betrieb (Demo)
-- Die UUID muss mit TENANT_ID in local.properties uebereinstimmen.
--
-- Bewusst OHNE Einloese-Code: Ein Code, der im Repository steht, ist keiner.
-- Er bleibt null, und das ist ungefaehrlich, weil requires_redeem_code auf
-- false steht - der Kunde loest dann selbst ein, das Personal prueft die
-- Bestaetigung per Blick oder scannt den QR an der Kasse.
--
-- Wer einen Code will, setzt ihn einmal von Hand und schaltet ihn scharf:
--   insert into public.tenant_secrets (tenant_id, redeem_code_hash)
--   values ('...', extensions.crypt('DEIN_CODE', extensions.gen_salt('bf')))
--       on conflict (tenant_id)
--       do update set redeem_code_hash = excluded.redeem_code_hash,
--                     updated_at       = now();
--   update public.tenants set requires_redeem_code = true where id = '...';
-- Ohne Hash lehnt redeem_voucher mit Code-Pflicht ausdruecklich ab, statt
-- stillschweigend durchzulassen.
insert into public.tenants (
    id, slug, name, daily_special_title, primary_color
) values (
    '00000000-0000-4000-8000-000000000001',
    'baeckerei-mustermann',
    'Bäckerei Mustermann',
    'Schmankerl des Tages',
    '#4CAF50'
) on conflict (id) do nothing;

-- Sicherheitsnetz fuer den Fall, dass "1234" ueber einen anderen Weg in
-- tenant_secrets gelandet ist als ueber den Umzug oben. Nur Hashes, die
-- genau darauf passen; ein selbst gesetzter Code bleibt unangetastet.
delete from public.tenant_secrets
 where redeem_code_hash like '$%'
   and extensions.crypt('1234', redeem_code_hash) = redeem_code_hash;

-- ----------------------------------------------------------------- Stempel
create table if not exists public.stamps (
    id         uuid primary key,
    created_at timestamptz not null default now(),
    user_id    uuid not null references auth.users(id) on delete cascade,
    tenant_id  uuid not null references public.tenants(id) on delete cascade
);

-- --------------------------------------------------------------- Gutscheine
create table if not exists public.vouchers (
    id            uuid primary key,
    created_at    timestamptz not null default now(),
    creation_date int8 not null,
    expires_at    int8 not null,
    is_redeemed   boolean not null default false,
    -- Wann eingeloest wurde. Ohne das laesst sich die Einloesequote nicht
    -- ueber die Zeit betrachten, nur als Gesamtzahl.
    redeemed_at   timestamptz,
    user_id       uuid not null references auth.users(id) on delete cascade,
    tenant_id     uuid not null references public.tenants(id) on delete cascade
);

-- ================================ Upgrade bestehender Installationen
-- Projekte, die vor der Mandantenfaehigkeit eingerichtet wurden, haben stamps
-- und vouchers ohne tenant_id. Das `create table if not exists` oben laesst
-- solche Tabellen unveraendert - die Spalte muss hier nachgezogen werden,
-- sonst scheitert alles Folgende mit "column tenant_id does not exist".
--
-- Bestehende Zeilen werden dem Demo-Betrieb zugeordnet. Wenn deine Daten zu
-- einem anderen Betrieb gehoeren, passe v_default_tenant an.
alter table public.vouchers add column if not exists redeemed_at timestamptz;

do $$
declare
    v_default_tenant uuid := '00000000-0000-4000-8000-000000000001';
    v_table          text;
begin
    foreach v_table in array array['stamps', 'vouchers'] loop
        if to_regclass('public.' || v_table) is not null and not exists (
            select 1 from information_schema.columns
            where table_schema = 'public'
              and table_name = v_table
              and column_name = 'tenant_id'
        ) then
            execute format('alter table public.%I add column tenant_id uuid', v_table);
            execute format(
                'update public.%I set tenant_id = %L where tenant_id is null',
                v_table, v_default_tenant
            );
            execute format(
                'alter table public.%I alter column tenant_id set not null', v_table
            );
            execute format(
                'alter table public.%I add constraint %I foreign key (tenant_id) '
                || 'references public.tenants(id) on delete cascade',
                v_table, v_table || '_tenant_id_fkey'
            );
            raise notice 'Spalte tenant_id in % ergaenzt und auf den Demo-Betrieb gesetzt', v_table;
        end if;
    end loop;
end
$$;

create index if not exists stamps_user_tenant_idx on public.stamps (user_id, tenant_id);
create index if not exists vouchers_user_tenant_idx on public.vouchers (user_id, tenant_id);

-- ======================================================== Row Level Security
alter table public.tenants        enable row level security;
-- Bewusst ohne Policy: Wer keine Policy hat, sieht keine Zeile. Der revoke
-- kommt oben drauf, weil Supabase neuen Tabellen Rechte mitgibt.
alter table public.tenant_secrets enable row level security;
revoke all on public.tenant_secrets from anon, authenticated;
alter table public.offers      enable row level security;
alter table public.memberships enable row level security;
alter table public.stamps      enable row level security;
alter table public.vouchers    enable row level security;
alter table public.offer_redemptions enable row level security;

-- Bestehende Policies zuerst entfernen, damit dieses Skript auf einem schon
-- eingerichteten Projekt wiederholbar ist.
--
-- Besonders wichtig sind die letzten drei: Sie stammen aus einer frueheren
-- Fassung. Ohne das Loeschen duerfte die App weiterhin selbst in stamps und
-- vouchers schreiben - die serverseitige Vergabe waere dann wirkungslos.
drop policy if exists tenants_read             on public.tenants;
drop policy if exists offers_read              on public.offers;
drop policy if exists memberships_select_own   on public.memberships;
drop policy if exists memberships_insert_own   on public.memberships;
drop policy if exists memberships_delete_own   on public.memberships;
drop policy if exists stamps_select_own        on public.stamps;
drop policy if exists vouchers_select_own      on public.vouchers;
drop policy if exists vouchers_update_own      on public.vouchers;

drop policy if exists stamps_insert_own        on public.stamps;
drop policy if exists stamps_delete_own        on public.stamps;
drop policy if exists vouchers_insert_own      on public.vouchers;

drop policy if exists offer_redemptions_select_own  on public.offer_redemptions;
drop policy if exists offer_redemptions_staff_read  on public.offer_redemptions;

-- Betriebe und Angebote sind ein Katalog: lesbar fuer alle Angemeldeten,
-- aus der App nicht schreibbar.
create policy tenants_read on public.tenants
    for select to authenticated using (is_active);

-- Der Gueltigkeitszeitraum wird hier geprueft und nicht in der Abfrage der
-- App: Sonst entschiede das Geraet darueber, was es sehen darf, und ein
-- abgelaufenes Angebot liesse sich mit einem einzigen Aufruf wieder
-- hervorholen. Leer heisst offen - ohne `valid_from` gilt es seit jeher,
-- ohne `valid_to` bis auf Weiteres. Das Enddatum zaehlt mit: Ein Angebot
-- "bis 31.08." steht am 31.08. noch da, alles andere ueberrascht den Betrieb.
-- Die Tagesgrenze liegt in Europe/Berlin, sonst begaenne ein Angebot im
-- Sommer schon um 22 Uhr des Vortags.
-- Der Betrieb selbst sieht ueber `offers_owner_read` weiter alles.
create policy offers_read on public.offers
    for select to authenticated using (
        exists (select 1 from public.tenants t where t.id = tenant_id and t.is_active)
        and (valid_from is null
             or valid_from <= (now() at time zone 'Europe/Berlin')::date)
        and (valid_to is null
             or valid_to >= (now() at time zone 'Europe/Berlin')::date)
    );

/*
 * Eingeloeste Coupons: lesen ja, schreiben nein.
 *
 * Der Kunde muss sehen, was er verbraucht hat - sonst koennte die App den
 * Zustand nur raten. Schreiben darf nur `redeem_offer`; gaebe es hier eine
 * insert- oder delete-Policy, koennte die App eine Einloesung auch wieder
 * zuruecknehmen und der ganze Aufwand waere umsonst.
 */
create policy offer_redemptions_select_own on public.offer_redemptions
    for select to authenticated using (auth.uid() = user_id);

-- Das Personal sieht die Einloesungen seines Betriebs - fuer die Zahlen in
-- der Verwaltung und damit an der Kasse nachvollziehbar ist, was ein Kunde
-- gerade vorzeigt.
create policy offer_redemptions_staff_read on public.offer_redemptions
    for select to authenticated using (public.is_staff_of(tenant_id));

-- Mitgliedschaften gehoeren dem Nutzer.
create policy memberships_select_own on public.memberships
    for select to authenticated using (auth.uid() = user_id);
create policy memberships_insert_own on public.memberships
    for insert to authenticated with check (auth.uid() = user_id);
create policy memberships_delete_own on public.memberships
    for delete to authenticated using (auth.uid() = user_id);

-- Hilfsfunktion: Ist der aufrufende Nutzer Mitglied bei diesem Betrieb?
create or replace function public.is_member_of(target_tenant uuid)
returns boolean language sql stable security invoker as $$
    select exists (
        select 1 from public.memberships m
        where m.user_id = auth.uid() and m.tenant_id = target_tenant
    );
$$;

-- Stempel und Gutscheine darf die App LESEN, aber nicht anlegen.
-- Es gibt bewusst keine insert-Policy: Wer selbst schreiben kann, kann sich
-- beliebig viele Stempel und Gutscheine ausstellen. Das Anlegen laeuft
-- ausschliesslich ueber issue_stamp() weiter unten.
create policy stamps_select_own on public.stamps
    for select to authenticated using (auth.uid() = user_id);

create policy vouchers_select_own on public.vouchers
    for select to authenticated using (auth.uid() = user_id);
-- Bewusst keine update-Policy: Sonst koennte die App Gutscheine selbst als
-- eingeloest markieren - oder eben nicht, und sie beliebig oft vorzeigen.
-- Eingeloest wird ausschliesslich ueber redeem_voucher().

-- ============================================== Stempelvergabe (serverseitig)

-- Worauf ein Stempel beruht. Der Unique-Index ist die eigentliche Absicherung
-- gegen Mehrfachnutzung desselben Belegs - nicht Anwendungslogik.
create table if not exists public.stamp_proofs (
    id         uuid primary key default gen_random_uuid(),
    tenant_id  uuid not null references public.tenants(id) on delete cascade,
    user_id    uuid not null references auth.users(id) on delete cascade,
    -- Eindeutige Kennung des Nachweises, z. B. die TSE-Transaktionsnummer
    -- vom Kassenbon.
    proof_ref  text not null,
    -- Woher der Nachweis stammt: 'receipt' oder 'demo'.
    source     text not null default 'receipt',
    created_at timestamptz not null default now(),
    unique (tenant_id, proof_ref)
);

-- Was aus dem Beleg-QR gelesen wurde. Ohne diese beiden Spalten laesst sich
-- ein abgelehnter oder strittiger Stempel spaeter nicht nachvollziehen.
alter table public.stamp_proofs
    add column if not exists register_serial text,
    add column if not exists amount_cents    int,
    -- Wurde die ECDSA-Signatur des Belegs geprüft? Nur die Edge Function
    -- kann das setzen.
    add column if not exists signature_verified boolean not null default false;

alter table public.stamp_proofs enable row level security;
-- Nur lesen; geschrieben wird ausschliesslich in issue_stamp().
drop policy if exists stamp_proofs_select_own on public.stamp_proofs;
create policy stamp_proofs_select_own on public.stamp_proofs
    for select to authenticated using (auth.uid() = user_id);

/*
 * Zerlegt den QR-Code eines deutschen Kassenbelegs (DSFinV-K, Anhang I).
 *
 * Aufbau, zwoelf mit Semikolon verkettete Felder:
 *   V0;<kasse>;<processType>;<processData>;<transaktion>;<zaehler>;
 *   <start-zeit>;<log-time>;<sig-alg>;<log-time-format>;<signatur>;<key>
 *
 * Gibt KEINE Zeile zurueck, wenn der Text kein solcher QR-Code ist - der
 * Aufrufer unterscheidet daran den Beleg vom freien Nachweis. Alles, was
 * unklar ist, gilt als kein Beleg: Lieber einen echten Bon ablehnen als
 * eine erfundene Zeichenkette durchwinken.
 *
 * NICHT geprueft wird die ECDSA-Signatur - dafuer fehlt Postgres die
 * Kryptografie (pgcrypto kann kein ECDSA verifizieren). Gegen das Einsammeln
 * fremder Bons, den tatsaechlichen Missbrauchsfall, helfen Zeitfenster,
 * Mindestbetrag, Tageslimit und die Kassenliste. Gegen einen selbst
 * gebauten QR-Code hilft nur die Signatur; das braucht eine Edge Function.
 */
create or replace function public.parse_receipt_qr(p_qr text)
returns table (
    register_serial text,
    transaction_no  text,
    signature_ctr   text,
    log_time        timestamptz,
    amount_cents    int,
    payment         text
)
language plpgsql
stable
as $$
declare
    f      text[];
    daten  text[];
    betrag text;
    roh    text;
begin
    if p_qr is null then return; end if;
    f := string_to_array(p_qr, ';');
    if coalesce(array_length(f, 1), 0) < 12 then return; end if;
    if trim(f[1]) <> 'V0' then return; end if;
    if trim(f[3]) not like 'Kassenbeleg%' then return; end if;

    register_serial := trim(f[2]);
    transaction_no  := trim(f[5]);
    signature_ctr   := trim(f[6]);
    if register_serial = '' or transaction_no = '' then return; end if;

    -- processData: Beleg^<Umsaetze je Steuersatz>^<Betrag>:<Zahlart>
    daten := string_to_array(f[4], '^');
    if coalesce(array_length(daten, 1), 0) < 3 then return; end if;
    betrag  := trim(split_part(daten[3], ':', 1));
    payment := nullif(trim(split_part(daten[3], ':', 2)), '');
    if betrag !~ '^[0-9]+([.,][0-9]{1,2})?$' then return; end if;
    amount_cents := round(replace(betrag, ',', '.')::numeric * 100);

    -- log-time: je nach Feld 10 Unix-Sekunden oder ISO-8601.
    roh := trim(f[8]);
    begin
        if roh ~ '^[0-9]+$' then
            log_time := to_timestamp(roh::bigint);
        else
            log_time := roh::timestamptz;
        end if;
    exception when others then
        return;
    end;
    if log_time is null then return; end if;

    return next;
end;
$$;

revoke all on function public.parse_receipt_qr(text) from public;

/*
 * Vergibt einen Stempel gegen einen Kaufnachweis.
 *
 * Laeuft als security definer, damit sie schreiben darf, obwohl die
 * aufrufende App es nicht darf. Ist die Karte danach voll, entsteht in
 * derselben Transaktion ein Gutschein und die Karte wird zurueckgesetzt -
 * so kann die App weder Stempel noch Gutscheine faelschen.
 *
 * Wirft bei bereits verwendetem Nachweis (unique_violation, SQLSTATE 23505).
 */
create or replace function public.issue_stamp_intern(
    p_user_id   uuid,
    p_tenant_id uuid,
    p_proof_ref text,
    p_source    text,
    p_signiert  boolean
)
returns table (
    stamp_id           uuid,
    stamp_count        int,
    voucher_id         uuid,
    voucher_expires_at int8
)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user      uuid := p_user_id;
    v_stamp_id  uuid := gen_random_uuid();
    v_count     int;
    v_tenant    public.tenants%rowtype;
    v_voucher   uuid := null;
    v_expires   int8 := null;
    v_now       timestamptz := now();
    v_beleg     record;
    v_ref       text;
    v_token     text;
    v_heute     int;
begin
    if v_user is null then
        raise exception 'not authenticated' using errcode = '28000';
    end if;
    -- Nicht is_member_of: Die Funktion fragt auth.uid(), und beim Aufruf aus
    -- der Edge Function heraus steht der Nutzer als Parameter da.
    if not exists (
        select 1 from public.memberships m
         where m.user_id = p_user_id and m.tenant_id = p_tenant_id
    ) then
        raise exception 'not a member of this tenant' using errcode = '42501';
    end if;
    if p_proof_ref is null or length(trim(p_proof_ref)) = 0 then
        raise exception 'proof reference required' using errcode = '22023';
    end if;

    select * into v_tenant from public.tenants where id = p_tenant_id and is_active;
    if not found then
        raise exception 'unknown or inactive tenant' using errcode = '22023';
    end if;

    /*
     * Beleg-QR oder freier Nachweis?
     *
     * Die Einmaligkeitspruefung allein schuetzt nur gegen denselben Beleg
     * zweimal. Sie schuetzt nicht gegen das Einsammeln fremder Bons - und
     * genau die liegen in einer Baeckerei herum, weil die Kundschaft sie
     * ueberwiegend nicht mitnimmt. Dagegen stehen die Regeln unten.
     */
    select * into v_beleg from public.parse_receipt_qr(p_proof_ref);

    if found then
        -- Verlangt der Betrieb eine geprüfte Signatur, kommt der Beleg nur
        -- über die Edge Function herein. Die Datenbank kann ECDSA nicht
        -- selbst prüfen; sie kann aber verlangen, dass es jemand getan hat.
        if v_tenant.require_signed_proof and not p_signiert then
            raise exception 'signature check required' using errcode = '42501';
        end if;

        if v_tenant.require_known_register and not exists (
            select 1 from public.tenant_registers r
             where r.tenant_id = p_tenant_id and r.serial = v_beleg.register_serial
        ) then
            raise exception 'unknown register' using errcode = '42501';
        end if;

        -- Das schaerfste Mittel gegen liegengebliebene Bons: Was im Laden
        -- eingesammelt wird, ist in aller Regel Stunden alt.
        if v_beleg.log_time < v_now - make_interval(mins => v_tenant.proof_max_age_minutes) then
            raise exception 'receipt too old' using errcode = '22023';
        end if;
        -- Kassenuhren gehen vor; mehr als eine Viertelstunde ist keine Drift.
        if v_beleg.log_time > v_now + interval '15 minutes' then
            raise exception 'receipt from the future' using errcode = '22023';
        end if;
        if v_beleg.amount_cents < v_tenant.proof_min_cents then
            raise exception 'amount below minimum' using errcode = '22023';
        end if;

        -- Kanonischer Schluessel statt der rohen Zeichenkette: Sonst zaehlt
        -- derselbe Bon erneut, sobald ein Leerzeichen anders steht.
        v_ref := v_beleg.register_serial || ':' || v_beleg.transaction_no
                 || ':' || v_beleg.signature_ctr;
    elsif trim(p_proof_ref) like 'tresen:%' then
        /*
         * Der Tresen-Code. Beweist Anwesenheit, nicht Kauf - deshalb muss
         * der Betrieb ihn ausdruecklich einschalten.
         *
         * Zwei Zeitfenster gelten: das laufende und das eben abgelaufene.
         * Ohne diese Nachfrist verliert genau der Kunde seinen Stempel, der
         * in dem Moment scannt, in dem der Code wechselt.
         */
        if not v_tenant.counter_qr_enabled then
            raise exception 'counter qr not enabled' using errcode = '22023';
        end if;
        v_token := substr(trim(p_proof_ref), 8);
        if v_token is distinct from public.counter_token(p_tenant_id, 0)
           and v_token is distinct from public.counter_token(p_tenant_id, -1) then
            raise exception 'counter token expired' using errcode = '22023';
        end if;

        -- Der Schluessel traegt den Nutzer mit: Sonst bekaeme in einer
        -- Warteschlange nur der erste Kunde seinen Stempel, weil der
        -- Nachweis je Betrieb nur einmal vorkommen darf.
        v_ref := 'tresen:' || v_token || ':' || v_user::text;
    else
        -- Kein Beleg-QR. Solange der Betrieb das erlaubt, zaehlt der freie
        -- Nachweis weiter - sonst waere die Pruefung ohnehin umgehbar,
        -- indem man irgendeine Zeichenkette schickt.
        if not v_tenant.allow_opaque_proofs or v_tenant.require_signed_proof then
            raise exception 'receipt qr required' using errcode = '22023';
        end if;
        v_ref := trim(p_proof_ref);
    end if;

    select count(*) into v_heute
      from public.stamp_proofs
     where tenant_id = p_tenant_id and user_id = v_user
       and (created_at at time zone 'Europe/Berlin')::date
           = (v_now at time zone 'Europe/Berlin')::date;
    if v_heute >= v_tenant.daily_stamp_limit then
        raise exception 'daily limit reached' using errcode = '22023';
    end if;

    -- Schlaegt bei einem schon verwendeten Beleg mit unique_violation fehl.
    insert into public.stamp_proofs (
        tenant_id, user_id, proof_ref, source, register_serial, amount_cents,
        signature_verified
    ) values (
        p_tenant_id, v_user,
        v_ref,
        case when v_ref like 'tresen:%' then 'counter' else p_source end,
        v_beleg.register_serial, v_beleg.amount_cents,
        coalesce(p_signiert, false)
    );

    insert into public.stamps (id, created_at, user_id, tenant_id)
    values (v_stamp_id, v_now, v_user, p_tenant_id);

    select count(*) into v_count
    from public.stamps
    where user_id = v_user and tenant_id = p_tenant_id;

    if v_count >= v_tenant.stamps_per_card then
        v_voucher := gen_random_uuid();
        v_expires := (extract(epoch from v_now) * 1000)::int8
                     + (v_tenant.voucher_validity_days::int8 * 86400000);

        insert into public.vouchers (
            id, created_at, creation_date, expires_at, is_redeemed, user_id, tenant_id
        ) values (
            v_voucher, v_now, (extract(epoch from v_now) * 1000)::int8,
            v_expires, false, v_user, p_tenant_id
        );

        -- Karte zuruecksetzen, in derselben Transaktion wie die Vergabe.
        delete from public.stamps where user_id = v_user and tenant_id = p_tenant_id;
    end if;

    return query select v_stamp_id, v_count, v_voucher, v_expires;
end;
$$;

revoke all on function public.issue_stamp_intern(uuid, uuid, text, text, boolean) from public;

/*
 * Der rotierende Code fuer den Tresen.
 *
 * Nicht gespeichert, sondern gerechnet: HMAC ueber Betrieb und Zeitfenster
 * mit einem Schluessel, der in tenant_secrets liegt. Dadurch entsteht kein
 * Schreibvorgang je Rotation, und es gibt nichts aufzuraeumen.
 *
 * Der Code beweist Anwesenheit am Tresen, NICHT einen Kauf - anders als der
 * Beleg-QR. Das ist der Preis dafuer, dass er ueberhaupt ohne mitspielende
 * Kasse funktioniert. Dagegen stehen die kurze Gueltigkeit und das
 * Tageslimit; wer den Code abfotografiert, kann ihn hoechstens Sekunden
 * spaeter noch benutzen und dann erst wieder am naechsten Tag.
 */
create or replace function public.counter_token(p_tenant_id uuid, p_fenster int)
returns text
language plpgsql
stable
security definer
set search_path = public, extensions
as $$
declare
    v_secret text;
    v_dauer  int;
begin
    select s.counter_secret, t.counter_qr_seconds
      into v_secret, v_dauer
      from public.tenants t
      left join public.tenant_secrets s on s.tenant_id = t.id
     where t.id = p_tenant_id and t.is_active;
    if v_secret is null then return null; end if;

    return substr(encode(hmac(
        p_tenant_id::text || ':' ||
        (floor(extract(epoch from now()) / v_dauer)::bigint + p_fenster)::text,
        v_secret, 'sha256'), 'hex'), 1, 24);
end;
$$;

revoke all on function public.counter_token(uuid, int) from public;

/*
 * Liefert dem Personal den aktuellen Tresen-Code samt Restlaufzeit.
 *
 * Nur fuer das Personal des Betriebs: Kunden duerfen den Code nicht abrufen,
 * sondern muessen ihn am Tresen scannen - sonst waere die Anwesenheit, die
 * er belegen soll, nicht mehr noetig.
 */
create or replace function public.staff_counter_token(p_tenant_id uuid)
returns table (token text, gueltig_bis timestamptz, sekunden int)
language plpgsql
stable
security definer
set search_path = public, extensions
as $$
declare
    v_dauer int;
    v_an    boolean;
begin
    if not public.is_staff_of(p_tenant_id) then
        raise exception 'not staff of this tenant' using errcode = '42501';
    end if;
    select counter_qr_seconds, counter_qr_enabled into v_dauer, v_an
      from public.tenants where id = p_tenant_id and is_active;
    if not coalesce(v_an, false) then
        raise exception 'counter qr not enabled' using errcode = '22023';
    end if;

    return query select
        public.counter_token(p_tenant_id, 0),
        to_timestamp((floor(extract(epoch from now()) / v_dauer) + 1) * v_dauer),
        v_dauer;
end;
$$;

revoke all on function public.staff_counter_token(uuid) from public;
grant execute on function public.staff_counter_token(uuid) to authenticated;

/*
 * Der gewoehnliche Weg: Die App ruft das mit ihrer eigenen Anmeldung auf.
 * Eine geprüfte Signatur kann sie dabei nicht behaupten - das entscheidet
 * nicht der Aufrufer.
 */
create or replace function public.issue_stamp(
    p_tenant_id uuid,
    p_proof_ref text,
    p_source    text default 'receipt'
)
returns table (
    stamp_id           uuid,
    stamp_count        int,
    voucher_id         uuid,
    voucher_expires_at int8
)
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is null then
        raise exception 'not authenticated' using errcode = '28000';
    end if;
    return query select * from public.issue_stamp_intern(
        auth.uid(), p_tenant_id, p_proof_ref, p_source, false);
end;
$$;

revoke all on function public.issue_stamp(uuid, text, text) from public;
grant execute on function public.issue_stamp(uuid, text, text) to authenticated;

/*
 * Der Weg ueber die Edge Function, die die ECDSA-Signatur des Belegs geprueft
 * hat. Nur fuer service_role - haette die App dieses Recht, koennte sie sich
 * die Pruefung selbst bescheinigen und der ganze Aufwand waere umsonst.
 *
 * Der Nutzer steht als Parameter da, weil die Funktion mit dem Service-Key
 * arbeitet und auth.uid() dort leer ist. Sie prueft die Mitgliedschaft
 * genauso wie der gewoehnliche Weg.
 */
create or replace function public.service_issue_stamp(
    p_user_id   uuid,
    p_tenant_id uuid,
    p_proof_ref text,
    p_source    text default 'receipt'
)
returns table (
    stamp_id           uuid,
    stamp_count        int,
    voucher_id         uuid,
    voucher_expires_at int8
)
language plpgsql
security definer
set search_path = public
as $$
begin
    if p_user_id is null then
        raise exception 'user required' using errcode = '22023';
    end if;
    return query select * from public.issue_stamp_intern(
        p_user_id, p_tenant_id, p_proof_ref, p_source, true);
end;
$$;

revoke all on function public.service_issue_stamp(uuid, uuid, text, text) from public;
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'service_role') then
        execute 'grant execute on function public.service_issue_stamp(uuid, uuid, text, text) to service_role';
    end if;
end
$$;

-- Beispiel-Angebot fuer den Demo-Betrieb.
insert into public.offers (tenant_id, title, description)
select '00000000-0000-4000-8000-000000000001',
       'Unser Dinkel-Kracher',
       'Heute frisch aus dem Ofen, nur 3,50 €!'
where not exists (
    select 1 from public.offers
    where tenant_id = '00000000-0000-4000-8000-000000000001'
);

-- ================================================== Aufbewahrung und Loeschung

/*
 * Raeumt abgelaufene Kaufnachweise ab.
 *
 * Rechtlicher Anlass: Art. 5 Abs. 1 lit. e DSGVO erlaubt eine Speicherung nur
 * so lange, wie der Zweck sie erfordert; Art. 17 Abs. 1 lit. a macht daraus
 * eine Loeschpflicht, sobald er entfaellt. Eine gesetzliche Aufbewahrungs-
 * pflicht steht nicht entgegen - § 147 AO trifft den Steuerpflichtigen und
 * seine eigenen Buchungsbelege, also den Betrieb mit seiner Kasse. Der
 * Verweis darauf in dieser Tabelle ist kein Buchungsbeleg.
 *
 * Zwei Stufen, weil zwei Zwecke zu verschiedenen Zeiten enden:
 *
 *   1. Betrag und Kassennummer werden geleert. Beide werden beim Stempeln
 *      geprueft und danach nur noch gebraucht, um einen strittigen Stempel zu
 *      erklaeren. Damit verschwindet die Einkaufshistorie, waehrend der
 *      Nachweis selbst weiterarbeitet.
 *   2. Der Nachweis wird geloescht. Er verhindert, dass derselbe Beleg
 *      zweimal zaehlt - dieser Zweck endet mit der Aufbewahrungsfrist des
 *      Betriebs, die nie kuerzer sein kann als sein Pruefzeitfenster.
 *
 * Warum nicht auch stamps, vouchers und memberships: Das ist die Leistung
 * selbst. Wer sie loescht, nimmt dem Kunden seine Karte. Wann das passieren
 * soll, ist eine Entscheidung des Betriebs und keine, die hier stillschweigend
 * getroffen wird.
 *
 * Laeuft als security definer und ist nur fuer service_role freigegeben:
 * Ein Aufruf aus der App koennte sonst fremde Nachweise abraeumen und damit
 * die Mehrfachnutzung wieder oeffnen.
 */
create or replace function public.cleanup_expired_proofs()
returns table (details_geleert bigint, nachweise_geloescht bigint)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_geleert  bigint := 0;
    v_geloescht bigint := 0;
begin
    with betroffen as (
        update public.stamp_proofs p
           set register_serial = null, amount_cents = null
          from public.tenants t
         where t.id = p.tenant_id
           and (p.register_serial is not null or p.amount_cents is not null)
           and p.created_at < now() - make_interval(days => t.proof_detail_days)
        returning 1
    )
    select count(*) into v_geleert from betroffen;

    with betroffen as (
        delete from public.stamp_proofs p
         using public.tenants t
         where t.id = p.tenant_id
           and p.created_at < now() - make_interval(days => t.proof_retention_days)
        returning 1
    )
    select count(*) into v_geloescht from betroffen;

    return query select v_geleert, v_geloescht;
end;
$$;

revoke all on function public.cleanup_expired_proofs() from public;
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'service_role') then
        execute 'grant execute on function public.cleanup_expired_proofs() to service_role';
    end if;
end
$$;

/*
 * Zeitplan fuer das Aufraeumen.
 *
 * Ohne Ausloeser bleibt die Loeschfrist Papier. Supabase Cron setzt auf
 * pg_cron auf; die Erweiterung legt ein Schema `cron` an, und Jobs stehen
 * danach in `cron.job`, ihre Laeufe in `cron.job_run_details`.
 * (https://supabase.com/docs/guides/cron)
 *
 * Taeglich, weil die Fristen in Tagen zaehlen. 01:20 GMT liegt in Berlin bei
 * 02:20 im Winter und 03:20 im Sommer - vor jeder Baeckerei-Oeffnung.
 * pg_cron plant in GMT, die Sommerzeit verschiebt den Lauf also um eine
 * Stunde. Fuer einen naechtlichen Aufraeumlauf ist das ohne Belang; erwaehnt,
 * damit sich niemand ueber die Uhrzeit in job_run_details wundert.
 *
 * Alles ist eingeklammert, weil pg_cron nur auf Supabase existiert. Im
 * Testpostgres gibt es die Erweiterung nicht, und das Schema muss dort
 * trotzdem durchlaufen.
 */
do $$
begin
    if exists (select 1 from pg_extension where extname = 'pg_cron') then
        return;
    end if;
    begin
        create extension if not exists pg_cron;
    exception when others then
        -- Fehlende Rechte oder Erweiterung: kein Grund, das Schema abzubrechen.
        raise notice 'pg_cron nicht verfuegbar (%). Zeitplan wird uebersprungen.', sqlerrm;
    end;
end
$$;

do $$
declare
    v_name text := 'treuebiss-nachweise-aufraeumen';
begin
    if not exists (select 1 from pg_extension where extname = 'pg_cron') then
        raise notice 'Ohne pg_cron laeuft cleanup_expired_proofs() nicht von selbst.';
        return;
    end if;

    /*
     * Erst abraeumen, dann neu anlegen. Was pg_cron bei einem schon
     * vergebenen Jobnamen tut, ist nicht dokumentiert - darauf zu bauen
     * hiesse, den Zeitplan dem Zufall zu ueberlassen.
     *
     * `execute` statt direktem Aufruf, damit die Namen erst zur Laufzeit
     * aufgeloest werden: Auf einer Datenbank ohne `cron`-Schema wuerde ein
     * direkter Aufruf schon beim Uebersetzen scheitern.
     */
    if exists (select 1 from cron.job where jobname = v_name) then
        execute format('select cron.unschedule(%L)', v_name);
    end if;

    execute format(
        'select cron.schedule(%L, %L, %L)',
        v_name, '20 1 * * *', 'select public.cleanup_expired_proofs();'
    );
    raise notice 'Zeitplan gesetzt: % taeglich 01:20 GMT', v_name;
end
$$;

-- ================================================ Auswertung fuer den Piloten
--
-- Die Zahlen stammen aus den Tabellen, die ohnehin gefuehrt werden - es gibt
-- bewusst kein zusaetzliches Event-Log. Die Views sind fuer den Betreiber
-- gedacht (Supabase-Studio oder Service-Role), nicht fuer die App: Sie
-- aggregieren ueber alle Nutzer eines Betriebs.
--
-- Was die App NICHT messen kann: wie viele Menschen den Flyer gesehen haben.
-- Der Nenner der Installationsrate muss vom Betrieb kommen.
--
-- Die Tagesgrenze liegt in Europe/Berlin, nicht in UTC. Ohne die Umrechnung
-- haengt das Ergebnis an der Zeitzone der lesenden Sitzung - in Supabase UTC.
-- Abendliche Vergaben nach 22 Uhr Ortszeit landeten dann auf dem Folgetag,
-- und niemand haette gemerkt, dass die Tageszahlen verschoben sind.

-- Neue Teilnehmer pro Tag - Zaehler der Installationsrate.
create or replace view public.pilot_daily_signups as
select
    tenant_id,
    (joined_at at time zone 'Europe/Berlin')::date as day,
    count(*)        as new_members
from public.memberships
group by tenant_id, (joined_at at time zone 'Europe/Berlin')::date;

-- Vergebene Stempel pro Tag. Faellt die Zahl nach Woche zwei ab, ist der
-- Kassenablauf das Problem, nicht die App.
create or replace view public.pilot_daily_stamps as
select
    tenant_id,
    (created_at at time zone 'Europe/Berlin')::date         as day,
    count(*)                 as stamps,
    count(distinct user_id)  as customers
from public.stamp_proofs
group by tenant_id, (created_at at time zone 'Europe/Berlin')::date;

-- Einloesungen pro Tag.
create or replace view public.pilot_daily_redemptions as
select
    tenant_id,
    (redeemed_at at time zone 'Europe/Berlin')::date as day,
    count(*)          as redemptions
from public.vouchers
where is_redeemed and redeemed_at is not null
group by tenant_id, (redeemed_at at time zone 'Europe/Berlin')::date;

-- Gesamtbild pro Betrieb.
create or replace view public.pilot_summary as
select
    t.id   as tenant_id,
    t.name as tenant,
    (select count(*) from public.memberships m where m.tenant_id = t.id)
        as members,
    (select count(*) from public.stamp_proofs s where s.tenant_id = t.id)
        as stamps_issued,
    (select count(distinct (s.created_at at time zone 'Europe/Berlin')::date)
       from public.stamp_proofs s where s.tenant_id = t.id)
        as active_days,
    round(
        (select count(*) from public.stamp_proofs s where s.tenant_id = t.id)::numeric
        / nullif((select count(distinct (s.created_at at time zone 'Europe/Berlin')::date)
                    from public.stamp_proofs s where s.tenant_id = t.id), 0),
        1
    ) as stamps_per_active_day,
    (select count(*) from public.vouchers v where v.tenant_id = t.id)
        as vouchers_created,
    (select count(*) from public.vouchers v where v.tenant_id = t.id and v.is_redeemed)
        as vouchers_redeemed,
    round(
        100.0 * (select count(*) from public.vouchers v
                  where v.tenant_id = t.id and v.is_redeemed)
        / nullif((select count(*) from public.vouchers v where v.tenant_id = t.id), 0),
        1
    ) as redemption_rate_percent,
    -- Einloesungen aus der Zeit vor der Spalte redeemed_at. Sie zaehlen oben
    -- mit, tauchen in pilot_daily_redemptions aber nicht auf - ohne diese
    -- Spalte sieht die Differenz nach einem Rechenfehler aus. Nachtragen
    -- laesst sie sich nicht: Wann eingeloest wurde, weiss niemand mehr.
    (select count(*) from public.vouchers v
      where v.tenant_id = t.id and v.is_redeemed and v.redeemed_at is null)
        as redemptions_without_timestamp
from public.tenants t;

-- Die Views gehoeren dem Betreiber, nicht der App. Ohne diesen Entzug waeren
-- sie ueber die Standardrechte fuer authenticated lesbar - und damit
-- aggregierte Fremddaten fuer jeden Kunden.
revoke all on public.pilot_daily_signups     from anon, authenticated;
revoke all on public.pilot_daily_stamps      from anon, authenticated;
revoke all on public.pilot_daily_redemptions from anon, authenticated;
revoke all on public.pilot_summary           from anon, authenticated;

/*
 * Die Pilot-Zahlen des eigenen Betriebs.
 *
 * pilot_summary selbst bleibt gesperrt - sie enthaelt alle Betriebe. Diese
 * Funktion gibt nur die Zeilen zurueck, fuer die der Aufrufer arbeitet.
 */
create or replace function public.staff_pilot_summary()
returns setof public.pilot_summary
language sql
stable
security definer
set search_path = public
as $$
    select * from public.pilot_summary
     where public.is_staff_of(tenant_id);
$$;

revoke all on function public.staff_pilot_summary() from public;
grant execute on function public.staff_pilot_summary() to authenticated;

-- ============================================ Verwaltung durch den Betrieb
-- Bis hierher pflegt der Anbieter jeden Betrieb von Hand per SQL. Das
-- skaliert nicht und laesst sich nicht verkaufen: Jede Farbaenderung waere
-- eine Anbieterleistung. Ab hier macht der Betrieb es selbst.
--
-- Zwei Rollen im Personal:
--   staff - Kasse: Gutscheine einloesen, Zahlen sehen.
--   owner - zusaetzlich Stammdaten, Kartenregeln, Angebote, Einloese-Code.
--
-- Was der Betrieb ausdruecklich NICHT selbst kann, bleibt beim Anbieter:
-- is_active, slug und die Zuordnung von Personal. Ein Betrieb, der sich
-- selbst abschaltet oder seinen Bezeichner aendert, ist ein Supportfall -
-- der Build der App haengt an beidem.
alter table public.tenant_staff
    add column if not exists role text not null default 'staff';

-- `add constraint if not exists` gibt es nicht; auf einem schon
-- eingerichteten Projekt liefe das Skript sonst beim zweiten Lauf auf.
do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'tenant_staff_role_check'
    ) then
        alter table public.tenant_staff
            add constraint tenant_staff_role_check check (role in ('staff', 'owner'));
    end if;
end
$$;

-- Darf der Aufrufer diesen Betrieb verwalten?
create or replace function public.is_owner_of(target_tenant uuid)
returns boolean language sql stable security invoker as $$
    select exists (
        select 1 from public.tenant_staff s
        where s.user_id = auth.uid()
          and s.tenant_id = target_tenant
          and s.role = 'owner'
    );
$$;

-- ------------------------------------------------------------- Angebote
-- Angebote tragen nichts Schuetzenswertes, deshalb reichen hier Policies;
-- fuer tenants braucht es weiter unten eine Funktion, weil dort einzelne
-- Spalten tabu bleiben muessen.
drop policy if exists offers_owner_read   on public.offers;
drop policy if exists offers_owner_insert on public.offers;
drop policy if exists offers_owner_update on public.offers;
drop policy if exists offers_owner_delete on public.offers;

-- Die Verwaltung braucht die ganze Liste, auch das abgelaufene und das noch
-- nicht begonnene Angebot - sonst verschwaende ein Angebot am Enddatum aus
-- der Verwaltung und liesse sich weder verlaengern noch loeschen.
-- Zwei select-Policies auf derselben Tabelle werden mit ODER verknuepft: Der
-- Kunde sieht das laufende Fenster, der Betrieb seinen ganzen Bestand.
-- Sie steht hier und nicht oben bei `offers_read`, weil `is_owner_of` erst
-- in diesem Abschnitt entsteht.
create policy offers_owner_read on public.offers
    for select to authenticated
    using (public.is_owner_of(tenant_id));

create policy offers_owner_insert on public.offers
    for insert to authenticated
    with check (public.is_owner_of(tenant_id));

-- Das `with check` steht hier ausgeschrieben, obwohl Postgres ohne es
-- denselben Ausdruck verwendet: Es haelt fest, dass auch die neue Zeile
-- geprueft wird - ein Angebot laesst sich also nicht in einen fremden
-- Betrieb umhaengen.
create policy offers_owner_update on public.offers
    for update to authenticated
    using (public.is_owner_of(tenant_id))
    with check (public.is_owner_of(tenant_id));

create policy offers_owner_delete on public.offers
    for delete to authenticated
    using (public.is_owner_of(tenant_id));

-- ------------------------------------------------------------- Kassen
create policy tenant_registers_read on public.tenant_registers
    for select to authenticated using (public.is_staff_of(tenant_id));
create policy tenant_registers_owner_insert on public.tenant_registers
    for insert to authenticated with check (public.is_owner_of(tenant_id));
create policy tenant_registers_owner_delete on public.tenant_registers
    for delete to authenticated using (public.is_owner_of(tenant_id));

-- ----------------------------------------------------------- Stammdaten
/*
 * Aendert die Angaben, die der Betrieb selbst verantwortet.
 *
 * Bewusst eine Funktion statt einer update-Policy: PostgREST wuerde sonst
 * die ganze Zeile freigeben. Spaltenrechte gaebe es zwar, sie waeren aber
 * still - eine neue Spalte an tenants waere ohne Zutun mitfreigegeben.
 * Hier steht ausbuchstabiert, was aenderbar ist.
 */
create or replace function public.owner_update_tenant(
    p_tenant_id             uuid,
    p_name                  text,
    p_loyalty_points_title  text,
    p_vouchers_title        text,
    p_daily_special_title   text,
    p_primary_color         text,
    p_logo_url              text,
    p_stamps_per_card       int,
    p_voucher_validity_days int
)
returns setof public.tenants
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is null then
        raise exception 'not authenticated' using errcode = '28000';
    end if;
    if not public.is_owner_of(p_tenant_id) then
        raise exception 'not owner of this tenant' using errcode = '42501';
    end if;

    -- Die drei Bezeichnungen stehen in der App als Ueberschriften. Leer
    -- waeren sie dort ein Loch, deshalb hier abgelehnt statt stumm auf
    -- den Vorgabewert zurueckgesetzt.
    if coalesce(length(trim(p_name)), 0) = 0
       or coalesce(length(trim(p_loyalty_points_title)), 0) = 0
       or coalesce(length(trim(p_vouchers_title)), 0) = 0
       or coalesce(length(trim(p_daily_special_title)), 0) = 0 then
        raise exception 'name and titles required' using errcode = '22023';
    end if;
    if p_primary_color is not null and p_primary_color !~ '^#[0-9A-Fa-f]{6}$' then
        raise exception 'invalid primary color' using errcode = '22023';
    end if;
    if p_stamps_per_card is null or p_stamps_per_card < 1 or p_stamps_per_card > 50 then
        raise exception 'stamps per card out of range' using errcode = '22023';
    end if;
    if p_voucher_validity_days is null or p_voucher_validity_days < 1 then
        raise exception 'validity days out of range' using errcode = '22023';
    end if;

    update public.tenants
       set name                  = trim(p_name),
           loyalty_points_title  = trim(p_loyalty_points_title),
           vouchers_title        = trim(p_vouchers_title),
           daily_special_title   = trim(p_daily_special_title),
           primary_color         = p_primary_color,
           logo_url              = nullif(trim(coalesce(p_logo_url, '')), ''),
           stamps_per_card       = p_stamps_per_card,
           voucher_validity_days = p_voucher_validity_days
     where id = p_tenant_id;

    return query select * from public.tenants where id = p_tenant_id;
end;
$$;

revoke all on function public.owner_update_tenant(
    uuid, text, text, text, text, text, text, int, int) from public;
grant execute on function public.owner_update_tenant(
    uuid, text, text, text, text, text, text, int, int) to authenticated;

-- ------------------------------------------------------ Belegpruefung
/*
 * Aendert die Regeln, nach denen ein Kaufnachweis geprueft wird.
 *
 * Bewusst getrennt von owner_update_tenant: Das eine ist Erscheinungsbild
 * und Kartenregel, das andere Missbrauchsschutz. Wer die Farbe aendert,
 * soll nicht aus Versehen das Zeitfenster verstellen.
 */
-- Die Signaturpflicht ist spaeter dazugekommen. Ohne dieses drop staende
-- die alte sechsstellige Fassung daneben, und der Aufruf waere mehrdeutig.
drop function if exists public.owner_update_proof_rules(uuid, int, int, int, boolean, boolean);
drop function if exists public.owner_update_proof_rules(uuid, int, int, int, boolean, boolean, boolean);
drop function if exists public.owner_update_proof_rules(uuid, int, int, int, boolean, boolean, boolean, boolean, int);

create or replace function public.owner_update_proof_rules(
    p_tenant_id       uuid,
    p_max_age_minutes int,
    p_min_cents       int,
    p_daily_limit     int,
    p_require_known   boolean,
    p_allow_opaque    boolean,
    p_require_signed  boolean default false,
    p_counter_enabled boolean default false,
    p_counter_seconds int default 60,
    -- Aufbewahrung. Vorgaben wie in der Tabelle, damit ein bestehender
    -- Aufruf ohne diese beiden Werte nichts verstellt.
    p_detail_days     int default 30,
    p_retention_days  int default 90
)
returns setof public.tenants
language plpgsql
security definer
set search_path = public
as $$
begin
    if not public.is_owner_of(p_tenant_id) then
        raise exception 'not owner of this tenant' using errcode = '42501';
    end if;
    if p_max_age_minutes is null or p_max_age_minutes < 1 or p_max_age_minutes > 43200 then
        raise exception 'max age out of range' using errcode = '22023';
    end if;
    if p_min_cents is null or p_min_cents < 0 then
        raise exception 'min amount out of range' using errcode = '22023';
    end if;
    if p_daily_limit is null or p_daily_limit < 1 or p_daily_limit > 100 then
        raise exception 'daily limit out of range' using errcode = '22023';
    end if;

    -- Nur Belege zulassen, ohne eine einzige Kasse eingetragen zu haben,
    -- waere das sichere Abschalten der Stempelvergabe.
    if p_counter_seconds is null or p_counter_seconds < 15 or p_counter_seconds > 900 then
        raise exception 'counter seconds out of range' using errcode = '22023';
    end if;

    if p_require_known and not exists (
        select 1 from public.tenant_registers r where r.tenant_id = p_tenant_id
    ) then
        raise exception 'no register configured' using errcode = '22023';
    end if;

    update public.tenants
       set proof_max_age_minutes  = p_max_age_minutes,
           proof_min_cents        = p_min_cents,
           daily_stamp_limit      = p_daily_limit,
           require_known_register = coalesce(p_require_known, false),
           allow_opaque_proofs    = coalesce(p_allow_opaque, true),
           require_signed_proof   = coalesce(p_require_signed, false),
           counter_qr_enabled     = coalesce(p_counter_enabled, false),
           counter_qr_seconds     = p_counter_seconds,
           proof_detail_days      = p_detail_days,
           proof_retention_days   = p_retention_days
     where id = p_tenant_id;

    -- Beim Einschalten einen Schluessel anlegen, falls noch keiner da ist.
    -- Ohne ihn liefert counter_token null und der Betrieb stuende vor einem
    -- Schalter, der nichts tut.
    /*
     * Die Untergrenze faengt zwar die Bedingung an der Tabelle ab, aber ein
     * check_violation ist fuer die Verwaltung eine unlesbare Meldung. Hier
     * steht, was der Betrieb tun kann.
     */
    if p_retention_days * 1440 < p_max_age_minutes then
        raise exception 'retention shorter than proof window' using errcode = '22023';
    end if;
    if p_detail_days > p_retention_days then
        raise exception 'detail period longer than retention' using errcode = '22023';
    end if;

    if coalesce(p_counter_enabled, false) then
        insert into public.tenant_secrets (tenant_id, redeem_code_hash, counter_secret)
        values (p_tenant_id, null, encode(extensions.gen_random_bytes(32), 'hex'))
            on conflict (tenant_id) do update
            set counter_secret = coalesce(public.tenant_secrets.counter_secret,
                                          encode(extensions.gen_random_bytes(32), 'hex'));
    end if;

    return query select * from public.tenants where id = p_tenant_id;
end;
$$;

revoke all on function public.owner_update_proof_rules(uuid, int, int, int, boolean, boolean, boolean, boolean, int, int, int) from public;
grant execute on function public.owner_update_proof_rules(uuid, int, int, int, boolean, boolean, boolean, boolean, int, int, int) to authenticated;

-- -------------------------------------------------------- Einloese-Code
/*
 * Setzt den Einloese-Code. Muss eine Funktion sein: tenant_secrets hat
 * keine Policy und ist von aussen unerreichbar - genau das ist der Sinn.
 */
create or replace function public.owner_set_redeem_code(
    p_tenant_id uuid,
    p_code      text
)
returns void
language plpgsql
security definer
set search_path = public, extensions
as $$
begin
    if not public.is_owner_of(p_tenant_id) then
        raise exception 'not owner of this tenant' using errcode = '42501';
    end if;
    -- Untergrenze mit Absicht: Ein vierstelliger Code ist der Fall, den wir
    -- gerade erst aus dem Schema entfernt haben. Er haelt den Kunden nicht
    -- auf, der zu Hause selbst einloesen will.
    if p_code is null or length(trim(p_code)) < 6 then
        raise exception 'redeem code too short' using errcode = '22023';
    end if;

    insert into public.tenant_secrets (tenant_id, redeem_code_hash)
    values (p_tenant_id, crypt(trim(p_code), gen_salt('bf')))
        on conflict (tenant_id) do update
        set redeem_code_hash = excluded.redeem_code_hash,
            updated_at       = now();

    update public.tenants set requires_redeem_code = true where id = p_tenant_id;
end;
$$;

revoke all on function public.owner_set_redeem_code(uuid, text) from public;
grant execute on function public.owner_set_redeem_code(uuid, text) to authenticated;

/*
 * Nimmt den Code wieder zurueck. Beides in einem Schritt: Ein Betrieb, der
 * requires_redeem_code stehen liesse und den Hash loeschte, koennte gar
 * nicht mehr einloesen.
 */
create or replace function public.owner_clear_redeem_code(p_tenant_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if not public.is_owner_of(p_tenant_id) then
        raise exception 'not owner of this tenant' using errcode = '42501';
    end if;
    update public.tenants set requires_redeem_code = false where id = p_tenant_id;

    -- Nur den Code entfernen, nicht die Zeile: In derselben Zeile liegt der
    -- Schluessel des Tresen-QR. Ihn mitzuloeschen machte jeden gerade
    -- gezeigten Code ungueltig - und zwar lautlos, denn der Schalter bliebe
    -- an und counter_token() gaebe einfach nichts mehr zurueck.
    update public.tenant_secrets
       set redeem_code_hash = null, updated_at = now()
     where tenant_id = p_tenant_id;

    -- Bleibt nichts uebrig, kann die Zeile weg.
    delete from public.tenant_secrets
     where tenant_id = p_tenant_id and counter_secret is null;
end;
$$;

revoke all on function public.owner_clear_redeem_code(uuid) from public;
grant execute on function public.owner_clear_redeem_code(uuid) to authenticated;
