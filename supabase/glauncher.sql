-- ============================================================================
-- GLauncher · Esquema completo de Supabase (un solo script)
-- ----------------------------------------------------------------------------
-- Pégalo tal cual en Supabase → SQL Editor → Run. Es idempotente: puedes
-- ejecutarlo tantas veces como quieras sin romper nada ni duplicar datos.
--
-- Qué crea:
--   1. public.profiles             → perfil del jugador (nivel, GCoins, sesión…)
--   2. public.streaks              → racha diaria (una fila por usuario)
--   3. public.streak_check_ins     → historial de firmas diarias
--   4. public.notifications        → avisos que publica el equipo
--   5. public.notification_reads   → leído / aceptado / descartado por usuario
--   6. public.launcher_releases    → versiones publicadas del launcher
--   7. Datos de ejemplo (dos filas; puedes borrarlas)
--
-- Llamadas desde la app (todas con apikey: {ANON_KEY} y, si hay sesión,
-- Authorization: Bearer {JWT_DEL_USUARIO}):
--   POST /rest/v1/rpc/claim_daily_streak          { "p_timezone": "America/Bogota" }
--   POST /rest/v1/rpc/get_my_streak
--   POST /rest/v1/rpc/get_my_profile
--   POST /rest/v1/rpc/update_my_profile           { "p_description": "…" }
--   POST /rest/v1/rpc/list_my_notifications
--   POST /rest/v1/rpc/count_unread_notifications
--   POST /rest/v1/rpc/mark_notification_read      { "p_id": "<uuid>" }
--   POST /rest/v1/rpc/accept_notification         { "p_id": "<uuid>" }
--   POST /rest/v1/rpc/dismiss_notification        { "p_id": "<uuid>" }
--   POST /rest/v1/rpc/latest_release              { "p_channel": "stable" }
--
-- Lecturas públicas con la clave anon (sin sesión): la tabla
-- public.launcher_releases y los avisos con audience = 'all'.
-- ============================================================================


-- ============================================================================
-- 1. PERFILES
-- ============================================================================

create table if not exists public.profiles (
    user_id       uuid primary key references auth.users (id) on delete cascade,
    username      text,
    display_name  text,
    avatar_url    text,
    description   text        not null default '',
    level         integer     not null default 1 check (level >= 1),
    gcoins        integer     not null default 0 check (gcoins >= 0),
    session_mode  text        not null default 'offline' check (session_mode in ('online', 'offline')),
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);

comment on table public.profiles is
    'Perfil del jugador en el launcher. Una fila por usuario de auth.users.';
comment on column public.profiles.session_mode is
    'Modo de sesión que muestra la ficha MiCuenta: online (Microsoft) u offline.';

create unique index if not exists profiles_username_key
    on public.profiles (lower(username))
    where username is not null;

alter table public.profiles enable row level security;

drop policy if exists profiles_select_own on public.profiles;
create policy profiles_select_own on public.profiles
    for select using (auth.uid() = user_id);

drop policy if exists profiles_update_own on public.profiles;
create policy profiles_update_own on public.profiles
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- El perfil se crea solo al registrarse.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles (user_id, username, display_name)
    values (
        new.id,
        coalesce(new.raw_user_meta_data ->> 'username', split_part(new.email, '@', 1)),
        coalesce(new.raw_user_meta_data ->> 'display_name', split_part(new.email, '@', 1))
    )
    on conflict (user_id) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists profiles_touch_updated_at on public.profiles;
create trigger profiles_touch_updated_at
    before update on public.profiles
    for each row execute function public.touch_updated_at();

create or replace function public.get_my_profile()
returns table (
    user_id      uuid,
    username     text,
    display_name text,
    avatar_url   text,
    description  text,
    level        integer,
    gcoins       integer,
    session_mode text,
    created_at   timestamptz
)
language sql
security definer
set search_path = public
as $$
    select p.user_id, p.username, p.display_name, p.avatar_url,
           p.description, p.level, p.gcoins, p.session_mode, p.created_at
    from public.profiles p
    where p.user_id = auth.uid();
$$;

create or replace function public.update_my_profile(
    p_display_name text default null,
    p_description  text default null,
    p_avatar_url   text default null,
    p_session_mode text default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is null then
        raise exception 'sin sesión';
    end if;

    update public.profiles
       set display_name = coalesce(p_display_name, display_name),
           description  = coalesce(p_description, description),
           avatar_url   = coalesce(p_avatar_url, avatar_url),
           session_mode = coalesce(p_session_mode, session_mode)
     where user_id = auth.uid();
end;
$$;


-- ============================================================================
-- 2. RACHAS
-- ----------------------------------------------------------------------------
-- La escritura NO se hace con INSERT/UPDATE desde el cliente: se llama a
-- claim_daily_streak(), que suma el día de forma atómica y decide si la racha
-- sube, se mantiene o se reinicia.
-- ============================================================================

create table if not exists public.streaks (
    user_id        uuid primary key references auth.users (id) on delete cascade,
    current_streak integer     not null default 0 check (current_streak >= 0),
    longest_streak integer     not null default 0 check (longest_streak >= 0),
    total_check_ins integer    not null default 0 check (total_check_ins >= 0),
    last_check_in  date,
    timezone       text        not null default 'UTC',
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now()
);

comment on table public.streaks is
    'Racha diaria de cada usuario. Una fila por usuario; se escribe solo con claim_daily_streak().';

create table if not exists public.streak_check_ins (
    id             bigint generated always as identity primary key,
    user_id        uuid        not null references auth.users (id) on delete cascade,
    checked_in_on  date        not null,
    streak_day     integer     not null check (streak_day >= 1),
    created_at     timestamptz not null default now(),
    constraint streak_check_ins_user_day_key unique (user_id, checked_in_on)
);

comment on table public.streak_check_ins is
    'Historial de firmas diarias: una fila por usuario y fecha.';

create index if not exists streak_check_ins_user_day_idx
    on public.streak_check_ins (user_id, checked_in_on desc);

create index if not exists streaks_longest_idx
    on public.streaks (longest_streak desc, current_streak desc);

create or replace function public.streaks_touch_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

drop trigger if exists streaks_touch_updated_at on public.streaks;
create trigger streaks_touch_updated_at
    before update on public.streaks
    for each row execute function public.streaks_touch_updated_at();

create or replace function public.claim_daily_streak(p_timezone text default 'UTC')
returns table (
    current_streak  integer,
    longest_streak  integer,
    total_check_ins integer,
    last_check_in   date,
    already_claimed boolean,
    reward_gcoins   integer,
    next_milestone  integer
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_uid     uuid := auth.uid();
    v_today   date;
    v_last    date;
    v_current integer := 0;
    v_reward  integer := 0;
begin
    if v_uid is null then
        raise exception 'not_authenticated' using errcode = '28000';
    end if;

    begin
        v_today := (now() at time zone coalesce(nullif(trim(p_timezone), ''), 'UTC'))::date;
    exception when others then
        v_today := (now() at time zone 'UTC')::date;
    end;

    insert into public.streaks as s (user_id, timezone)
    values (v_uid, coalesce(nullif(trim(p_timezone), ''), 'UTC'))
    on conflict (user_id) do nothing;

    select s.last_check_in, s.current_streak
      into v_last, v_current
      from public.streaks s
     where s.user_id = v_uid
       for update;

    if v_last = v_today then
        return query
            select s.current_streak, s.longest_streak, s.total_check_ins,
                   s.last_check_in, true, 0,
                   (s.current_streak / 7 + 1) * 7
              from public.streaks s
             where s.user_id = v_uid;
        return;
    end if;

    if v_last = v_today - 1 then
        v_current := v_current + 1;
    else
        v_current := 1;
    end if;

    insert into public.streak_check_ins (user_id, checked_in_on, streak_day)
    values (v_uid, v_today, v_current)
    on conflict (user_id, checked_in_on) do nothing;

    update public.streaks s
       set current_streak = v_current,
           longest_streak = greatest(s.longest_streak, v_current),
           total_check_ins = s.total_check_ins + 1,
           last_check_in = v_today,
           timezone = coalesce(nullif(trim(p_timezone), ''), 'UTC')
     where s.user_id = v_uid;

    -- Recompensa: 5 GCoins al firmar y 50 cada 7 días de racha.
    v_reward := case when v_current % 7 = 0 then 50 else 5 end;

    return query
        select s.current_streak, s.longest_streak, s.total_check_ins,
               s.last_check_in, false, v_reward,
               (s.current_streak / 7 + 1) * 7
          from public.streaks s
         where s.user_id = v_uid;
end;
$$;

comment on function public.claim_daily_streak(text) is
    'Firma el día actual una sola vez. Devuelve la racha, el récord y los GCoins sugeridos.';

create or replace function public.get_my_streak()
returns table (
    current_streak  integer,
    longest_streak  integer,
    total_check_ins integer,
    last_check_in   date,
    claimed_today   boolean,
    next_milestone  integer
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then
        raise exception 'not_authenticated' using errcode = '28000';
    end if;

    return query
        select coalesce(s.current_streak, 0),
               coalesce(s.longest_streak, 0),
               coalesce(s.total_check_ins, 0),
               s.last_check_in,
               coalesce(s.last_check_in = (now() at time zone coalesce(s.timezone, 'UTC'))::date, false),
               (coalesce(s.current_streak, 0) / 7 + 1) * 7
          from (select 1) dummy
          left join public.streaks s on s.user_id = v_uid;
end;
$$;

alter table public.streaks enable row level security;
alter table public.streak_check_ins enable row level security;

drop policy if exists "streaks: leer la propia" on public.streaks;
create policy "streaks: leer la propia"
    on public.streaks for select
    to authenticated
    using (auth.uid() = user_id);

drop policy if exists "checkins: leer los propios" on public.streak_check_ins;
create policy "checkins: leer los propios"
    on public.streak_check_ins for select
    to authenticated
    using (auth.uid() = user_id);

-- Sin políticas de INSERT/UPDATE/DELETE: solo las RPC (security definer)
-- pueden escribir, y siempre con auth.uid() del que llama.
revoke insert, update, delete on public.streaks from anon, authenticated;
revoke insert, update, delete on public.streak_check_ins from anon, authenticated;

revoke all on function public.claim_daily_streak(text) from public, anon;
revoke all on function public.get_my_streak() from public, anon;
grant execute on function public.claim_daily_streak(text) to authenticated;
grant execute on function public.get_my_streak() to authenticated;

-- Ranking público de rachas (opcional, para un top en la web/app).
create or replace function public.top_streaks(p_limit integer default 20)
returns table (
    user_id         uuid,
    current_streak  integer,
    longest_streak  integer
)
language sql
security definer
set search_path = public, pg_temp
as $$
    select s.user_id, s.current_streak, s.longest_streak
      from public.streaks s
     order by s.current_streak desc, s.longest_streak desc
     limit least(greatest(p_limit, 1), 100);
$$;

revoke all on function public.top_streaks(integer) from public, anon;
grant execute on function public.top_streaks(integer) to authenticated;


-- ============================================================================
-- 3. NOTIFICACIONES
-- ============================================================================

create table if not exists public.notifications (
    id           uuid primary key default gen_random_uuid(),
    title        text        not null,
    body         text        not null default '',
    kind         text        not null default 'info'
                 check (kind in ('info', 'update', 'event', 'warning')),
    audience     text        not null default 'all'
                 check (audience in ('all', 'beta', 'admins')),
    action_url   text,
    action_label text,
    published_at timestamptz not null default now(),
    expires_at   timestamptz,
    created_at   timestamptz not null default now()
);

comment on table public.notifications is
    'Avisos del launcher. Los lee cualquier usuario autenticado; solo los escribe el equipo (service_role).';

create index if not exists notifications_published_idx
    on public.notifications (published_at desc);

alter table public.notifications enable row level security;

drop policy if exists notifications_select_active on public.notifications;
create policy notifications_select_active on public.notifications
    for select using (
        auth.uid() is not null
        and published_at <= now()
        and (expires_at is null or expires_at > now())
    );

-- Lectura pública de los avisos generales: la app consulta con la clave anon
-- antes de que exista sesión de Supabase Auth. Solo abre audience = 'all'.
drop policy if exists notifications_select_public on public.notifications;
create policy notifications_select_public on public.notifications
    for select to anon
    using (
        audience = 'all'
        and published_at <= now()
        and (expires_at is null or expires_at > now())
    );

create table if not exists public.notification_reads (
    user_id         uuid not null references auth.users (id) on delete cascade,
    notification_id uuid not null references public.notifications (id) on delete cascade,
    read_at         timestamptz not null default now(),
    accepted_at     timestamptz,
    dismissed_at    timestamptz,
    primary key (user_id, notification_id)
);

comment on table public.notification_reads is
    'Estado por usuario de cada aviso: leído, aceptado (acción) o descartado (papelera).';

alter table public.notification_reads enable row level security;

drop policy if exists notification_reads_select_own on public.notification_reads;
create policy notification_reads_select_own on public.notification_reads
    for select using (auth.uid() = user_id);

drop policy if exists notification_reads_insert_own on public.notification_reads;
create policy notification_reads_insert_own on public.notification_reads
    for insert with check (auth.uid() = user_id);

drop policy if exists notification_reads_update_own on public.notification_reads;
create policy notification_reads_update_own on public.notification_reads
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists notification_reads_delete_own on public.notification_reads;
create policy notification_reads_delete_own on public.notification_reads
    for delete using (auth.uid() = user_id);

create or replace function public.list_my_notifications(p_limit integer default 40)
returns table (
    id         uuid,
    title      text,
    body       text,
    kind       text,
    action_url text,
    action_label text,
    published_at timestamptz,
    read       boolean,
    accepted   boolean,
    dismissed  boolean
)
language sql
security definer
set search_path = public
as $$
    select n.id, n.title, n.body, n.kind, n.action_url, n.action_label,
           n.published_at,
           (r.read_at is not null)      as read,
           (r.accepted_at is not null)  as accepted,
           (r.dismissed_at is not null) as dismissed
    from public.notifications n
    left join public.notification_reads r
           on r.notification_id = n.id and r.user_id = auth.uid()
    where auth.uid() is not null
      and n.published_at <= now()
      and (n.expires_at is null or n.expires_at > now())
      and r.dismissed_at is null
    order by n.published_at desc
    limit greatest(1, least(coalesce(p_limit, 40), 200));
$$;

create or replace function public.count_unread_notifications()
returns integer
language sql
security definer
set search_path = public
as $$
    select count(*)::integer
    from public.notifications n
    left join public.notification_reads r
           on r.notification_id = n.id and r.user_id = auth.uid()
    where auth.uid() is not null
      and n.published_at <= now()
      and (n.expires_at is null or n.expires_at > now())
      and r.read_at is null
      and r.dismissed_at is null;
$$;

create or replace function public.mark_notification_read(p_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is null then
        raise exception 'sin sesión';
    end if;

    insert into public.notification_reads (user_id, notification_id, read_at)
    values (auth.uid(), p_id, now())
    on conflict (user_id, notification_id)
    do update set read_at = coalesce(public.notification_reads.read_at, now());
end;
$$;

create or replace function public.accept_notification(p_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is null then
        raise exception 'sin sesión';
    end if;

    insert into public.notification_reads (user_id, notification_id, read_at, accepted_at)
    values (auth.uid(), p_id, now(), now())
    on conflict (user_id, notification_id)
    do update set read_at = coalesce(public.notification_reads.read_at, now()),
                  accepted_at = now();
end;
$$;

create or replace function public.dismiss_notification(p_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is null then
        raise exception 'sin sesión';
    end if;

    insert into public.notification_reads (user_id, notification_id, read_at, dismissed_at)
    values (auth.uid(), p_id, now(), now())
    on conflict (user_id, notification_id)
    do update set read_at = coalesce(public.notification_reads.read_at, now()),
                  dismissed_at = now();
end;
$$;


-- ============================================================================
-- 4. VERSIONES DEL LAUNCHER (modal Actualización)
-- ============================================================================

create table if not exists public.launcher_releases (
    version       text primary key,
    channel       text        not null default 'stable' check (channel in ('stable', 'beta')),
    title         text,
    notes         text        not null default '',
    download_url  text,
    mandatory     boolean     not null default false,
    published_at  timestamptz not null default now()
);

comment on table public.launcher_releases is
    'Versiones del launcher. La app compara su versionName con el mayor de su canal.';

alter table public.launcher_releases enable row level security;

drop policy if exists releases_select_all on public.launcher_releases;
create policy releases_select_all on public.launcher_releases
    for select using (true);

create or replace function public.latest_release(p_channel text default 'stable')
returns table (
    version      text,
    channel      text,
    title        text,
    notes        text,
    download_url text,
    mandatory    boolean,
    published_at timestamptz
)
language sql
security definer
set search_path = public
as $$
    select r.version, r.channel, r.title, r.notes, r.download_url, r.mandatory, r.published_at
    from public.launcher_releases r
    where r.channel = coalesce(p_channel, 'stable')
      and r.published_at <= now()
    order by r.published_at desc
    limit 1;
$$;


-- ============================================================================
-- 5. DATOS DE EJEMPLO (opcional: borra este bloque si no lo quieres)
-- ============================================================================

insert into public.launcher_releases (version, channel, title, notes, mandatory)
values
    ('1.1', 'stable', 'GLauncher 1.1',
     'Inicio nuevo con menú superior, campana de notificaciones y panel de novedades.', false)
on conflict (version) do nothing;

insert into public.notifications (title, body, kind, audience)
select 'Bienvenido a GLauncher',
       'Este es el primer aviso del canal. Aquí publicaremos versiones y eventos.',
       'info', 'all'
where not exists (
    select 1 from public.notifications where title = 'Bienvenido a GLauncher'
);


-- ============================================================================
-- Fin. Comprobaciones rápidas después de ejecutarlo:
--   select * from public.get_my_streak();         -- una fila con 0s (con sesión)
--   select public.claim_daily_streak('UTC');      -- firma el día (una vez)
--   select * from public.launcher_releases;       -- versiones publicadas
--   select title, audience from public.notifications;
--   select * from public.latest_release('stable');
-- ============================================================================
