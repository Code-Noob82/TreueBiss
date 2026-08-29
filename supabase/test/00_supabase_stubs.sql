-- Minimaler Ersatz für die Teile von Supabase, die schema.sql voraussetzt.
-- Nur für lokale Tests - in einem echten Supabase-Projekt existiert das alles.

create schema if not exists auth;
-- Schema fuer die Testhilfen selbst.
create schema if not exists test;

create table if not exists auth.users (
    id uuid primary key default gen_random_uuid()
);

-- Supabase liest die User-ID aus den JWT-Claims der aktuellen Anfrage.
-- Lokal setzen wir denselben Parameter direkt.
create or replace function auth.uid()
returns uuid
language sql
stable
as $$
    select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid;
$$;

-- PostgREST legt diese Rollen an; die Policies verweisen darauf.
do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'anon') then
        create role anon nologin;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'authenticated') then
        create role authenticated nologin;
    end if;
    -- Die Rolle, unter der die Edge Function arbeitet.
    if not exists (select 1 from pg_roles where rolname = 'service_role') then
        create role service_role nologin;
    end if;
end
$$;

grant usage on schema public to anon, authenticated, service_role;

-- Wichtig fuer aussagekraeftige Tests: In einem echten Supabase-Projekt haben
-- anon und authenticated Tabellenrechte auf public - eingeschraenkt wird
-- ausschliesslich ueber RLS. Ohne diese Rechte wuerde hier schon die
-- Berechtigungspruefung greifen und die Policies blieben ungetestet.
alter default privileges in schema public
    grant select, insert, update, delete on tables to anon, authenticated;
alter default privileges in schema public
    grant usage, select on sequences to anon, authenticated;
grant usage on schema auth to anon, authenticated;
grant select on auth.users to authenticated;

-- Hilfsfunktion für die Tests: als bestimmter Nutzer auftreten.
create or replace procedure auth.become(p_user uuid)
language plpgsql
as $$
begin
    perform set_config('request.jwt.claim.sub', p_user::text, false);
end;
$$;

-- Die Testhilfen muessen auch aus der eingeschraenkten Rolle heraus
-- aufrufbar sein, sonst laesst sich RLS nicht pruefen.
grant usage on schema test to anon, authenticated, service_role;

create or replace procedure test.check(p_condition boolean, p_label text)
language plpgsql
security definer
as $$
begin
    if p_condition then
        raise notice 'OK    %', p_label;
    else
        raise exception 'FEHLGESCHLAGEN: %', p_label;
    end if;
end;
$$;

grant execute on procedure test.check(boolean, text) to anon, authenticated;
grant execute on procedure auth.become(uuid) to anon, authenticated;
