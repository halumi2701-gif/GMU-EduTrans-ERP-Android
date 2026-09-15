-- GMU EduTrans v22.0 — Customer Account → CRM Prospect Layer
-- Goal: every public-web signup becomes a visible customer prospect before any booking exists.
-- Booking remains separate; customer_account_id links a real booking request back to the registered account.

create schema if not exists private;

create table if not exists public.customer_accounts (
  user_id uuid primary key references auth.users(id) on delete cascade,
  email text,
  full_name text,
  whatsapp text,
  institution_name text,
  city text,
  source text not null default 'PUBLIC_WEB_SIGNUP',
  account_status text not null default 'REGISTERED'
    check (account_status in ('REGISTERED','ACTIVE','NURTURE','CUSTOMER','INACTIVE')),
  lead_stage text not null default 'NEW'
    check (lead_stage in ('NEW','CONTACTED','INTERESTED','BOOKING_STARTED','BOOKED','NURTURE','LOST')),
  followup_consent boolean not null default false,
  assigned_sales uuid references public.profiles(id) on delete set null,
  last_interest_program text,
  last_interest_package text,
  first_registered_at timestamptz not null default now(),
  last_seen_at timestamptz,
  last_interest_at timestamptz,
  last_contact_at timestamptz,
  next_follow_up_at timestamptz,
  booking_request_count integer not null default 0 check (booking_request_count >= 0),
  last_booking_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists customer_accounts_email_idx on public.customer_accounts(lower(email));
create index if not exists customer_accounts_sales_stage_idx on public.customer_accounts(assigned_sales, lead_stage, next_follow_up_at);
create index if not exists customer_accounts_followup_idx on public.customer_accounts(followup_consent, next_follow_up_at) where followup_consent = true;
create index if not exists customer_accounts_registered_idx on public.customer_accounts(first_registered_at desc);

alter table public.booking_requests
  add column if not exists customer_account_id uuid references public.customer_accounts(user_id) on delete set null;
create index if not exists booking_requests_customer_account_idx on public.booking_requests(customer_account_id, created_at desc);

alter table public.customer_accounts enable row level security;

drop policy if exists customer_accounts_self_read on public.customer_accounts;
create policy customer_accounts_self_read
on public.customer_accounts for select to authenticated
using ((select auth.uid()) = user_id);

drop policy if exists customer_accounts_staff_read on public.customer_accounts;
create policy customer_accounts_staff_read
on public.customer_accounts for select to authenticated
using (
  exists (
    select 1 from public.profiles p
    where p.id = (select auth.uid())
      and p.is_active = true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans','Admin')
  )
  or (
    assigned_sales = (select auth.uid())
    and exists (
      select 1 from public.profiles p
      where p.id = (select auth.uid()) and p.is_active = true and p.role::text = 'Sales'
    )
  )
);

drop policy if exists customer_accounts_staff_update on public.customer_accounts;
create policy customer_accounts_staff_update
on public.customer_accounts for update to authenticated
using (
  exists (
    select 1 from public.profiles p
    where p.id = (select auth.uid())
      and p.is_active = true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans','Admin')
  )
  or (
    assigned_sales = (select auth.uid())
    and exists (
      select 1 from public.profiles p
      where p.id = (select auth.uid()) and p.is_active = true and p.role::text = 'Sales'
    )
  )
)
with check (
  exists (
    select 1 from public.profiles p
    where p.id = (select auth.uid())
      and p.is_active = true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans','Admin')
  )
  or (
    assigned_sales = (select auth.uid())
    and exists (
      select 1 from public.profiles p
      where p.id = (select auth.uid()) and p.is_active = true and p.role::text = 'Sales'
    )
  )
);

grant select, update on public.customer_accounts to authenticated;

create or replace function private.gmu_v220_capture_customer_signup()
returns trigger
language plpgsql
security definer
set search_path = public, private, pg_temp
as $$
declare
  v_meta jsonb := coalesce(new.raw_user_meta_data, '{}'::jsonb);
  v_followup boolean := case
    when lower(coalesce(v_meta->>'followup_consent','false')) in ('true','1','yes','ya') then true
    else false
  end;
begin
  insert into public.customer_accounts (
    user_id, email, full_name, whatsapp, institution_name, city, source,
    account_status, lead_stage, followup_consent, first_registered_at, last_seen_at
  ) values (
    new.id,
    new.email,
    nullif(trim(coalesce(v_meta->>'full_name','')),''),
    nullif(trim(coalesce(v_meta->>'whatsapp','')),''),
    nullif(trim(coalesce(v_meta->>'institution_name','')),''),
    nullif(trim(coalesce(v_meta->>'city','')),''),
    coalesce(nullif(trim(coalesce(v_meta->>'source','')),''),'PUBLIC_WEB_SIGNUP'),
    'REGISTERED',
    'NEW',
    v_followup,
    coalesce(new.created_at, now()),
    now()
  )
  on conflict (user_id) do update set
    email = excluded.email,
    full_name = coalesce(excluded.full_name, public.customer_accounts.full_name),
    whatsapp = coalesce(excluded.whatsapp, public.customer_accounts.whatsapp),
    institution_name = coalesce(excluded.institution_name, public.customer_accounts.institution_name),
    city = coalesce(excluded.city, public.customer_accounts.city),
    followup_consent = public.customer_accounts.followup_consent or excluded.followup_consent,
    last_seen_at = now(),
    updated_at = now();
  return new;
end;
$$;
revoke all on function private.gmu_v220_capture_customer_signup() from public, anon, authenticated;

drop trigger if exists trg_gmu_v220_customer_signup on auth.users;
create trigger trg_gmu_v220_customer_signup
after insert on auth.users
for each row execute function private.gmu_v220_capture_customer_signup();

create or replace function private.gmu_v220_touch_customer_account()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;
revoke all on function private.gmu_v220_touch_customer_account() from public, anon, authenticated;

drop trigger if exists trg_gmu_v220_customer_account_touch on public.customer_accounts;
create trigger trg_gmu_v220_customer_account_touch
before update on public.customer_accounts
for each row execute function private.gmu_v220_touch_customer_account();

-- Customer-safe RPC: the signed-in customer may record package/program interest,
-- but may not assign sales, alter CRM stage directly, or read other customers.
create or replace function public.gmu_customer_account_touch_interest(
  p_program text default null,
  p_package text default null
)
returns boolean
language plpgsql
security definer
set search_path = public, private, pg_temp
as $$
declare
  v_uid uuid := (select auth.uid());
begin
  if v_uid is null then
    raise exception 'Authentication required';
  end if;

  update public.customer_accounts
     set last_interest_program = left(nullif(trim(coalesce(p_program,'')),''), 160),
         last_interest_package = left(nullif(trim(coalesce(p_package,'')),''), 160),
         last_interest_at = now(),
         last_seen_at = now(),
         account_status = case when account_status = 'REGISTERED' then 'ACTIVE' else account_status end,
         lead_stage = case when lead_stage in ('BOOKED','LOST') then lead_stage else 'INTERESTED' end
   where user_id = v_uid;

  return found;
end;
$$;
revoke all on function public.gmu_customer_account_touch_interest(text,text) from public, anon;
grant execute on function public.gmu_customer_account_touch_interest(text,text) to authenticated;

create or replace function private.gmu_v220_sync_customer_from_booking()
returns trigger
language plpgsql
security definer
set search_path = public, private, pg_temp
as $$
begin
  if new.customer_account_id is null then
    return new;
  end if;

  if tg_op = 'INSERT' then
    update public.customer_accounts
       set account_status = 'ACTIVE',
           lead_stage = 'BOOKING_STARTED',
           booking_request_count = booking_request_count + 1,
           last_booking_at = coalesce(new.created_at, now()),
           last_seen_at = now()
     where user_id = new.customer_account_id;
  elsif new.converted_booking_id is not null and old.converted_booking_id is distinct from new.converted_booking_id then
    update public.customer_accounts
       set account_status = 'CUSTOMER',
           lead_stage = 'BOOKED',
           last_booking_at = coalesce(new.updated_at, now()),
           last_seen_at = now()
     where user_id = new.customer_account_id;
  end if;

  return new;
end;
$$;
revoke all on function private.gmu_v220_sync_customer_from_booking() from public, anon, authenticated;

drop trigger if exists trg_gmu_v220_customer_booking_insert on public.booking_requests;
create trigger trg_gmu_v220_customer_booking_insert
after insert on public.booking_requests
for each row execute function private.gmu_v220_sync_customer_from_booking();

drop trigger if exists trg_gmu_v220_customer_booking_update on public.booking_requests;
create trigger trg_gmu_v220_customer_booking_update
after update of converted_booking_id on public.booking_requests
for each row execute function private.gmu_v220_sync_customer_from_booking();

comment on table public.customer_accounts is
'Customer/public-web accounts captured at signup so ERP can manage prospects before a booking exists. Follow-up consent is explicit and separate from authentication.';
comment on column public.customer_accounts.followup_consent is
'Explicit consent to be contacted by GMU EduTrans about the selected program/booking. Authentication alone is not marketing consent.';
comment on column public.booking_requests.customer_account_id is
'Links a real booking request to the registered public-web customer account; null for legacy/manual bookings.';
