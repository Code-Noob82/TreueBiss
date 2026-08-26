-- TreueBiss - Datenbankschema (Supabase / Postgres)
--
-- Reihenfolge beachten: tenants zuerst, danach alles was darauf verweist.
-- Pflege von tenants und offers laeuft ueber den Service-Role-Key oder das
-- Supabase-Studio, nicht aus der App. Genau hier dockt spaeter ein Admin-
-- Backend an, ohne dass der Rest angefasst werden muss.

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
    created_at            timestamptz not null default now()
);

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

-- ----------------------------------------------------------------- Stempel
create table if not exists public.stamps (
    id         uuid primary key,
    created_at timestamptz not null default now(),
    user_id    uuid not null references auth.users(id) on delete cascade,
    tenant_id  uuid not null references public.tenants(id) on delete cascade
);
create index if not exists stamps_user_tenant_idx on public.stamps (user_id, tenant_id);

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
create index if not exists vouchers_user_tenant_idx on public.vouchers (user_id, tenant_id);

-- ======================================================== Row Level Security
alter table public.tenants     enable row level security;
alter table public.offers      enable row level security;
alter table public.memberships enable row level security;
alter table public.stamps      enable row level security;
alter table public.vouchers    enable row level security;

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

-- Stempel und Gutscheine: eigene Zeilen, und nur bei Betrieben,
-- bei denen der Nutzer auch Mitglied ist.
create policy stamps_select_own on public.stamps
    for select to authenticated using (auth.uid() = user_id);
create policy stamps_insert_own on public.stamps
    for insert to authenticated
    with check (auth.uid() = user_id and public.is_member_of(tenant_id));
create policy stamps_delete_own on public.stamps
    for delete to authenticated using (auth.uid() = user_id);

create policy vouchers_select_own on public.vouchers
    for select to authenticated using (auth.uid() = user_id);
create policy vouchers_insert_own on public.vouchers
    for insert to authenticated
    with check (auth.uid() = user_id and public.is_member_of(tenant_id));
-- Einloesen: Das Update filtert clientseitig nur nach id - diese Policy ist
-- das Einzige, was verhindert, dass eine fremde Gutschein-ID getroffen wird.
create policy vouchers_update_own on public.vouchers
    for update to authenticated
    using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- ==================================================== Beispiel-Betrieb (Demo)
-- Die UUID muss mit TENANT_ID in local.properties uebereinstimmen.
insert into public.tenants (id, slug, name, daily_special_title, primary_color)
values (
    '00000000-0000-4000-8000-000000000001',
    'baeckerei-mustermann',
    'Bäckerei Mustermann',
    'Schmankerl des Tages',
    '#4CAF50'
) on conflict (id) do nothing;

insert into public.offers (tenant_id, title, description)
values (
    '00000000-0000-4000-8000-000000000001',
    'Unser Dinkel-Kracher',
    'Heute frisch aus dem Ofen, nur 3,50 €!'
) on conflict do nothing;
