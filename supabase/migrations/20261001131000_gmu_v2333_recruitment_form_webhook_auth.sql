-- GMU EduTrans v23.3.3 — secure recruitment form webhook auth

create table if not exists private.recruitment_form_webhook_auth (
  id smallint primary key default 1 check (id=1),
  secret_hash text not null,
  active boolean not null default true,
  rotated_at timestamptz not null default now(),
  note text
);

create or replace function public.gmu_validate_recruitment_form_secret(p_secret text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from private.recruitment_form_webhook_auth a
    where a.id=1
      and a.active=true
      and a.secret_hash = encode(extensions.digest(p_secret,'sha256'),'hex')
  );
$$;

revoke all on function public.gmu_validate_recruitment_form_secret(text) from public, anon, authenticated;
grant execute on function public.gmu_validate_recruitment_form_secret(text) to service_role;

comment on function public.gmu_validate_recruitment_form_secret(text) is
  'Server-only shared-secret validator for GMU EduTrans recruitment form webhook.';
