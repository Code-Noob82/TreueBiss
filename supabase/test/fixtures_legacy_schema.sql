-- Bildet den Stand nach, den ein Projekt hat, das vor der Mandantenfähigkeit
-- eingerichtet wurde: stamps und vouchers ohne tenant_id.
-- Dient dazu, das Upgrade zu testen statt nur die Neuinstallation.

create table public.stamps (
    id         uuid primary key,
    created_at timestamptz not null default now(),
    user_id    uuid not null references auth.users(id) on delete cascade
);

create table public.vouchers (
    id            uuid primary key,
    created_at    timestamptz not null default now(),
    creation_date int8 not null,
    expires_at    int8 not null,
    is_redeemed   boolean not null default false,
    user_id       uuid not null references auth.users(id) on delete cascade
);

alter table public.stamps   enable row level security;
alter table public.vouchers enable row level security;

-- Die alten Policies, die der App das direkte Schreiben erlaubten.
create policy stamps_select_own on public.stamps
    for select to authenticated using (auth.uid() = user_id);
create policy stamps_insert_own on public.stamps
    for insert to authenticated with check (auth.uid() = user_id);
create policy vouchers_select_own on public.vouchers
    for select to authenticated using (auth.uid() = user_id);
create policy vouchers_insert_own on public.vouchers
    for insert to authenticated with check (auth.uid() = user_id);
