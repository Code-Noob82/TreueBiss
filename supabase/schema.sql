-- TreueBiss - Datenbankschema (Supabase / Postgres)
--
-- Reihenfolge beachten: tenants zuerst, danach alles was darauf verweist.
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
    -- bcrypt-Hash des Einloese-Codes, den das Personal an der Kasse eingibt.
    -- Der Code selbst wird nie gespeichert.
    redeem_code_hash      text,
    -- Wenn true, muss beim Einloesen der Code eingegeben werden. Standard ist
    -- false: Der Kunde loest selbst ein, die App zeigt eine zeitgebundene
    -- Bestaetigung, auf die das Personal nur kurz schaut. Betriebe, die es
    -- strenger wollen, schalten das Flag ein.
    requires_redeem_code  boolean not null default false,
    created_at            timestamptz not null default now()
);

-- Bestehende Projekte nachziehen: Das `create table if not exists` oben
-- laesst eine vorhandene tenants-Tabelle unveraendert, also fehlt dort die
-- Spalte. Muss vor allem stehen, was sie verwendet.
alter table public.tenants add column if not exists redeem_code_hash text;
alter table public.tenants
    add column if not exists requires_redeem_code boolean not null default false;

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

-- ----------------------------------------------------------- Mitgliedschaft
-- Haelt fest, bei welchen Betrieben ein Nutzer sammelt. Auch im Ein-Betrieb-
-- Build vorhanden, damit die RLS-Policies unten einheitlich greifen.
create table if not exists public.memberships (
    user_id   uuid not null references auth.users(id) on delete cascade,
    tenant_id uuid not null references public.tenants(id) on delete cascade,
    joined_at timestamptz not null default now(),
    primary key (user_id, tenant_id)
);

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

    select redeem_code_hash, requires_redeem_code
      into v_hash, v_requires
      from public.tenants where id = v_tenant and is_active;

    -- Der Code ist nur Pflicht, wenn der Betrieb ihn verlangt. Sonst loest
    -- der Kunde selbst ein; das Personal prueft die Bestaetigung per Blick,
    -- oder scannt den QR an der Kasse.
    if v_requires then
        if v_hash is null then
            raise exception 'no redeem code configured for this tenant' using errcode = '22023';
        end if;
        if p_code is null or crypt(p_code, v_hash) <> v_hash then
            raise exception 'invalid redeem code' using errcode = '42501';
        end if;
    end if;

    update public.vouchers set is_redeemed = true where id = p_voucher_id;

    return query select p_voucher_id, v_now;
end;
$$;

revoke all on function public.redeem_voucher(uuid, text) from public;
grant execute on function public.redeem_voucher(uuid, text) to authenticated;

-- ==================================================== Beispiel-Betrieb (Demo)
-- Die UUID muss mit TENANT_ID in local.properties uebereinstimmen.
-- Der Einloese-Code des Demo-Betriebs ist "1234". Vor einem Pilotbetrieb
-- unbedingt aendern:
--   update public.tenants
--      set redeem_code_hash = extensions.crypt('DEIN_CODE', extensions.gen_salt('bf'))
--    where id = '...';
insert into public.tenants (
    id, slug, name, daily_special_title, primary_color, redeem_code_hash
) values (
    '00000000-0000-4000-8000-000000000001',
    'baeckerei-mustermann',
    'Bäckerei Mustermann',
    'Schmankerl des Tages',
    '#4CAF50',
    extensions.crypt('1234', extensions.gen_salt('bf'))
) on conflict (id) do nothing;

-- Bestehende Betriebe ohne Code bekommen den Demo-Code, damit das Einloesen
-- nach dem Upgrade nicht stumm blockiert.
update public.tenants
   set redeem_code_hash = extensions.crypt('1234', extensions.gen_salt('bf'))
 where redeem_code_hash is null;

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
alter table public.tenants     enable row level security;
alter table public.offers      enable row level security;
alter table public.memberships enable row level security;
alter table public.stamps      enable row level security;
alter table public.vouchers    enable row level security;

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

-- Betriebe und Angebote sind ein Katalog: lesbar fuer alle Angemeldeten,
-- aus der App nicht schreibbar.
create policy tenants_read on public.tenants
    for select to authenticated using (is_active);

create policy offers_read on public.offers
    for select to authenticated using (
        exists (select 1 from public.tenants t where t.id = tenant_id and t.is_active)
    );

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

alter table public.stamp_proofs enable row level security;
-- Nur lesen; geschrieben wird ausschliesslich in issue_stamp().
drop policy if exists stamp_proofs_select_own on public.stamp_proofs;
create policy stamp_proofs_select_own on public.stamp_proofs
    for select to authenticated using (auth.uid() = user_id);

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
declare
    v_user      uuid := auth.uid();
    v_stamp_id  uuid := gen_random_uuid();
    v_count     int;
    v_tenant    public.tenants%rowtype;
    v_voucher   uuid := null;
    v_expires   int8 := null;
    v_now       timestamptz := now();
begin
    if v_user is null then
        raise exception 'not authenticated' using errcode = '28000';
    end if;
    if not public.is_member_of(p_tenant_id) then
        raise exception 'not a member of this tenant' using errcode = '42501';
    end if;
    if p_proof_ref is null or length(trim(p_proof_ref)) = 0 then
        raise exception 'proof reference required' using errcode = '22023';
    end if;

    select * into v_tenant from public.tenants where id = p_tenant_id and is_active;
    if not found then
        raise exception 'unknown or inactive tenant' using errcode = '22023';
    end if;

    -- Schlaegt bei einem schon verwendeten Beleg mit unique_violation fehl.
    insert into public.stamp_proofs (tenant_id, user_id, proof_ref, source)
    values (p_tenant_id, v_user, trim(p_proof_ref), p_source);

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

revoke all on function public.issue_stamp(uuid, text, text) from public;
grant execute on function public.issue_stamp(uuid, text, text) to authenticated;

-- Beispiel-Angebot fuer den Demo-Betrieb.
insert into public.offers (tenant_id, title, description)
select '00000000-0000-4000-8000-000000000001',
       'Unser Dinkel-Kracher',
       'Heute frisch aus dem Ofen, nur 3,50 €!'
where not exists (
    select 1 from public.offers
    where tenant_id = '00000000-0000-4000-8000-000000000001'
);
