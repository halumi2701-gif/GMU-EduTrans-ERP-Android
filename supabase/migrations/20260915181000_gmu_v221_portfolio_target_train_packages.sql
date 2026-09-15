-- GMU EduTrans v22.1 — Cross-program Sales target + final Train Education package scheme
-- Business rule: Sales target 200 paid pax/month is PORTFOLIO-WIDE across all GMU EduTrans programs.
-- A booking contributes paid pax once: in the month of its first verified positive payment.
-- Public package price is separated from internal HPP/fee economics.

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------------------
-- 1) Portfolio-wide Sales target (NOT program-specific)
-- ---------------------------------------------------------------------------
create table if not exists public.sales_portfolio_targets (
  id uuid primary key default gen_random_uuid(),
  period_month date not null,
  target_key text not null default 'ALL_PROGRAMS',
  target_name text not null default 'GMU EduTrans — Seluruh Program',
  bep_paid_pax integer not null default 60 check (bep_paid_pax >= 0),
  productive_paid_pax integer not null default 100 check (productive_paid_pax >= 0),
  target_paid_pax integer not null default 200 check (target_paid_pax > 0),
  stretch_paid_pax integer not null default 300 check (stretch_paid_pax >= target_paid_pax),
  outstanding_paid_pax integer not null default 400 check (outstanding_paid_pax >= stretch_paid_pax),
  sales_retainer numeric(16,2) not null default 600000 check (sales_retainer >= 0),
  sales_fee_per_paid_pax numeric(16,2) not null default 2500 check (sales_fee_per_paid_pax >= 0),
  target_bonus numeric(16,2) not null default 250000 check (target_bonus >= 0),
  status text not null default 'ACTIVE' check (status in ('ACTIVE','INACTIVE')),
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(period_month, target_key)
);

insert into public.sales_portfolio_targets(
  period_month,target_key,target_name,bep_paid_pax,productive_paid_pax,target_paid_pax,
  stretch_paid_pax,outstanding_paid_pax,sales_retainer,sales_fee_per_paid_pax,target_bonus,status,notes
)
values (
  date '2026-09-01','ALL_PROGRAMS','GMU EduTrans — Seluruh Program',60,100,200,300,400,
  600000,2500,250000,'ACTIVE',
  'Target Sales 200 paid pax/bulan dihitung dari gabungan seluruh program. Contoh booking 38+37+20+60+99+27+20 = 301 pax setelah booking memiliki pembayaran positif terverifikasi.'
)
on conflict (period_month,target_key) do update set
  target_name=excluded.target_name,
  bep_paid_pax=excluded.bep_paid_pax,
  productive_paid_pax=excluded.productive_paid_pax,
  target_paid_pax=excluded.target_paid_pax,
  stretch_paid_pax=excluded.stretch_paid_pax,
  outstanding_paid_pax=excluded.outstanding_paid_pax,
  sales_retainer=excluded.sales_retainer,
  sales_fee_per_paid_pax=excluded.sales_fee_per_paid_pax,
  target_bonus=excluded.target_bonus,
  status='ACTIVE',
  notes=excluded.notes,
  updated_at=now();

alter table public.sales_portfolio_targets enable row level security;
drop policy if exists sales_portfolio_targets_read on public.sales_portfolio_targets;
create policy sales_portfolio_targets_read on public.sales_portfolio_targets
for select to authenticated using (
  exists (
    select 1 from public.profiles p
    where p.id=(select auth.uid()) and p.is_active=true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans','Sales')
  )
);
grant select on public.sales_portfolio_targets to authenticated;

-- A booking becomes paid-pax exactly once: first positive verified payment.
create or replace view public.v_sales_paid_booking_attribution
with (security_invoker=true)
as
with payment_events as (
  select
    p.booking_id,
    p.amount,
    coalesce(p.verified_at, p.payment_date::timestamptz, p.created_at) as paid_at
  from public.payments p
  where coalesce(p.amount,0) > 0
    and (p.verified_at is not null or p.verified_by is not null)
), first_paid as (
  select booking_id, min(paid_at) as first_paid_at
  from payment_events
  where paid_at is not null
  group by booking_id
)
select
  b.id as booking_id,
  b.booking_no,
  b.sales_id,
  b.program_name,
  greatest(coalesce(b.pax,0),0)::integer as paid_pax,
  coalesce(b.price_per_pax,0)::numeric as price_per_pax,
  (greatest(coalesce(b.pax,0),0) * coalesce(b.price_per_pax,0))::numeric as booking_value,
  fp.first_paid_at,
  date_trunc('month',fp.first_paid_at)::date as period_month
from public.bookings b
join first_paid fp on fp.booking_id=b.id;

grant select on public.v_sales_paid_booking_attribution to authenticated;

create or replace view public.v_sales_portfolio_program_monthly
with (security_invoker=true)
as
select
  period_month,
  coalesce(nullif(program_name,''),'Program Lain') as program_name,
  count(*)::integer as paid_bookings,
  coalesce(sum(paid_pax),0)::integer as paid_pax,
  coalesce(sum(booking_value),0)::numeric as booked_value
from public.v_sales_paid_booking_attribution
group by period_month, coalesce(nullif(program_name,''),'Program Lain');

grant select on public.v_sales_portfolio_program_monthly to authenticated;

create or replace view public.v_sales_portfolio_monthly
with (security_invoker=true)
as
with paid_activity as (
  select
    period_month,
    count(*)::integer as paid_bookings,
    coalesce(sum(paid_pax),0)::integer as paid_pax,
    coalesce(sum(booking_value),0)::numeric as booked_value
  from public.v_sales_paid_booking_attribution
  group by period_month
), cash_activity as (
  select
    date_trunc('month',coalesce(p.verified_at,p.payment_date::timestamptz,p.created_at))::date as period_month,
    coalesce(sum(p.amount),0)::numeric as cash_in
  from public.payments p
  where coalesce(p.amount,0) > 0
    and (p.verified_at is not null or p.verified_by is not null)
    and coalesce(p.verified_at,p.payment_date::timestamptz,p.created_at) is not null
  group by date_trunc('month',coalesce(p.verified_at,p.payment_date::timestamptz,p.created_at))::date
)
select
  t.period_month,
  t.target_key,
  t.target_name,
  coalesce(a.paid_bookings,0)::integer as paid_bookings,
  coalesce(a.paid_pax,0)::integer as paid_pax,
  coalesce(a.booked_value,0)::numeric as booked_value,
  coalesce(c.cash_in,0)::numeric as cash_in,
  t.bep_paid_pax,
  t.productive_paid_pax,
  t.target_paid_pax,
  t.stretch_paid_pax,
  t.outstanding_paid_pax,
  round((coalesce(a.paid_pax,0)::numeric / nullif(t.target_paid_pax,0)) * 100,2) as achievement_pct,
  greatest(coalesce(a.paid_pax,0)-t.target_paid_pax,0)::integer as pax_above_target,
  t.sales_retainer,
  t.sales_fee_per_paid_pax,
  (coalesce(a.paid_pax,0) * t.sales_fee_per_paid_pax)::numeric as variable_sales_fee,
  case when coalesce(a.paid_pax,0) >= t.target_paid_pax then t.target_bonus else 0 end::numeric as target_bonus_earned,
  (t.sales_retainer
    + coalesce(a.paid_pax,0) * t.sales_fee_per_paid_pax
    + case when coalesce(a.paid_pax,0) >= t.target_paid_pax then t.target_bonus else 0 end)::numeric as modeled_sales_income,
  case
    when coalesce(a.paid_pax,0) >= t.outstanding_paid_pax then 'OUTSTANDING'
    when coalesce(a.paid_pax,0) >= t.stretch_paid_pax then 'STRETCH'
    when coalesce(a.paid_pax,0) >= t.target_paid_pax then 'TARGET_TERCAPAI'
    when coalesce(a.paid_pax,0) >= t.productive_paid_pax then 'PRODUKTIF'
    when coalesce(a.paid_pax,0) >= t.bep_paid_pax then 'BEP'
    else 'DI_BAWAH_BEP'
  end as achievement_level
from public.sales_portfolio_targets t
left join paid_activity a on a.period_month=t.period_month
left join cash_activity c on c.period_month=t.period_month
where t.status='ACTIVE';

grant select on public.v_sales_portfolio_monthly to authenticated;

-- Optional per-Sales breakdown. Team target remains one portfolio target; this view is for accountability.
create or replace view public.v_sales_portfolio_by_sales_monthly
with (security_invoker=true)
as
select
  period_month,
  sales_id,
  count(*)::integer as paid_bookings,
  coalesce(sum(paid_pax),0)::integer as paid_pax,
  coalesce(sum(booking_value),0)::numeric as booked_value
from public.v_sales_paid_booking_attribution
where sales_id is not null
group by period_month,sales_id;

grant select on public.v_sales_portfolio_by_sales_monthly to authenticated;

-- ---------------------------------------------------------------------------
-- 2) Final public master packages — Edukasi di Atas Kereta
-- Price already includes the ticket that is part of the program.
-- ---------------------------------------------------------------------------
do $$
declare
  v_program_id uuid;
begin
  select id into v_program_id from public.programs where slug='edukasi-di-atas-kereta' limit 1;
  if v_program_id is null then
    raise exception 'Program edukasi-di-atas-kereta belum tersedia';
  end if;

  update public.program_packages set
    program_id=v_program_id,
    name='Paket Hemat',
    description='Edukasi di Atas Kereta — Paket Hemat. Harga sudah termasuk tiket yang menjadi bagian program.',
    price_per_pax=39000,min_pax=20,status='ACTIVE',is_active=true,sort_order=10
  where package_code='TRAIN-HEMAT-39';
  if not found then
    insert into public.program_packages(package_code,program_id,name,description,price_per_pax,min_pax,status,is_active,sort_order)
    values('TRAIN-HEMAT-39',v_program_id,'Paket Hemat','Edukasi di Atas Kereta — Paket Hemat. Harga sudah termasuk tiket yang menjadi bagian program.',39000,20,'ACTIVE',true,10);
  end if;

  update public.program_packages set
    program_id=v_program_id,
    name='Paket Reguler',
    description='Edukasi di Atas Kereta — Paket Reguler / Best Seller. Harga sudah termasuk tiket yang menjadi bagian program.',
    price_per_pax=60000,min_pax=20,status='ACTIVE',is_active=true,sort_order=20
  where package_code='TRAIN-REGULER-60';
  if not found then
    insert into public.program_packages(package_code,program_id,name,description,price_per_pax,min_pax,status,is_active,sort_order)
    values('TRAIN-REGULER-60',v_program_id,'Paket Reguler','Edukasi di Atas Kereta — Paket Reguler / Best Seller. Harga sudah termasuk tiket yang menjadi bagian program.',60000,20,'ACTIVE',true,20);
  end if;

  update public.program_packages set
    program_id=v_program_id,
    name='Paket Lengkap',
    description='Edukasi di Atas Kereta — Paket Lengkap. Harga sudah termasuk tiket yang menjadi bagian program.',
    price_per_pax=75000,min_pax=20,status='ACTIVE',is_active=true,sort_order=30
  where package_code='TRAIN-LENGKAP-75';
  if not found then
    insert into public.program_packages(package_code,program_id,name,description,price_per_pax,min_pax,status,is_active,sort_order)
    values('TRAIN-LENGKAP-75',v_program_id,'Paket Lengkap','Edukasi di Atas Kereta — Paket Lengkap. Harga sudah termasuk tiket yang menjadi bagian program.',75000,20,'ACTIVE',true,30);
  end if;
end $$;

-- Internal economics are intentionally separate from public program_packages.
create table if not exists public.program_package_economics (
  package_code text primary key,
  program_slug text not null,
  package_name text not null,
  baseline_pax integer not null default 20,
  facility_hpp_locked numeric(16,2) not null,
  sales_fee_baseline numeric(16,2) not null,
  partner_fee_baseline numeric(16,2) not null,
  manager_fee_baseline numeric(16,2) not null,
  tl_tutor_fee_baseline numeric(16,2) not null,
  ops_documentation_fee_baseline numeric(16,2) not null,
  total_cost_baseline numeric(16,2) not null,
  trip_contribution_baseline numeric(16,2) not null,
  contribution_margin_pct numeric(8,3) not null,
  facility_hpp_is_locked boolean not null default true,
  is_best_seller boolean not null default false,
  crew_scaling_policy text not null default 'TIER_MANUAL_ABOVE_BASELINE',
  notes text,
  updated_at timestamptz not null default now()
);

insert into public.program_package_economics values
('TRAIN-HEMAT-39','edukasi-di-atas-kereta','Paket Hemat',20,230000,50000,50000,100000,80000,50000,560000,220000,28.205,true,false,'TIER_MANUAL_ABOVE_BASELINE','HPP fasilitas LOCK. Sales dan Mitra terpisah. Margin adalah kontribusi trip sebelum overhead tetap perusahaan.'),
('TRAIN-REGULER-60','edukasi-di-atas-kereta','Paket Reguler',20,410000,50000,50000,120000,100000,55000,785000,415000,34.583,true,true,'TIER_MANUAL_ABOVE_BASELINE','Paket utama / Best Seller. HPP fasilitas LOCK. Sales dan Mitra terpisah.'),
('TRAIN-LENGKAP-75','edukasi-di-atas-kereta','Paket Lengkap',20,570000,50000,50000,130000,110000,55000,965000,535000,35.667,true,false,'TIER_MANUAL_ABOVE_BASELINE','HPP fasilitas LOCK. Sales dan Mitra terpisah.')
on conflict (package_code) do update set
  program_slug=excluded.program_slug,
  package_name=excluded.package_name,
  baseline_pax=excluded.baseline_pax,
  facility_hpp_locked=excluded.facility_hpp_locked,
  sales_fee_baseline=excluded.sales_fee_baseline,
  partner_fee_baseline=excluded.partner_fee_baseline,
  manager_fee_baseline=excluded.manager_fee_baseline,
  tl_tutor_fee_baseline=excluded.tl_tutor_fee_baseline,
  ops_documentation_fee_baseline=excluded.ops_documentation_fee_baseline,
  total_cost_baseline=excluded.total_cost_baseline,
  trip_contribution_baseline=excluded.trip_contribution_baseline,
  contribution_margin_pct=excluded.contribution_margin_pct,
  facility_hpp_is_locked=true,
  is_best_seller=excluded.is_best_seller,
  crew_scaling_policy=excluded.crew_scaling_policy,
  notes=excluded.notes,
  updated_at=now();

alter table public.program_package_economics enable row level security;
drop policy if exists program_package_economics_management_read on public.program_package_economics;
create policy program_package_economics_management_read on public.program_package_economics
for select to authenticated using (
  exists (
    select 1 from public.profiles p
    where p.id=(select auth.uid()) and p.is_active=true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans')
  )
);
grant select on public.program_package_economics to authenticated;

-- Extra ticketing OUTSIDE the program. It must never be automatically added to package price.
create table if not exists public.program_package_addons (
  addon_code text primary key,
  program_slug text not null,
  addon_name text not null,
  sell_price_per_pax numeric(16,2) not null,
  base_cost_per_pax numeric(16,2) not null,
  handling_per_pax numeric(16,2) not null,
  included_in_package boolean not null default false,
  is_active boolean not null default true,
  notes text,
  updated_at timestamptz not null default now()
);

insert into public.program_package_addons(addon_code,program_slug,addon_name,sell_price_per_pax,base_cost_per_pax,handling_per_pax,included_in_package,is_active,notes)
values
('EXTRA-TICKET-ONE-WAY','edukasi-di-atas-kereta','Tiket Tambahan Pergi',7000,3000,4000,false,true,'Hanya tiket tambahan di luar program. Tiket utama program sudah termasuk harga paket.'),
('EXTRA-TICKET-ROUND-TRIP','edukasi-di-atas-kereta','Tiket Tambahan PP',10000,6000,4000,false,true,'Hanya tiket tambahan di luar program. Tiket utama program sudah termasuk harga paket.')
on conflict (addon_code) do update set
  program_slug=excluded.program_slug,
  addon_name=excluded.addon_name,
  sell_price_per_pax=excluded.sell_price_per_pax,
  base_cost_per_pax=excluded.base_cost_per_pax,
  handling_per_pax=excluded.handling_per_pax,
  included_in_package=false,
  is_active=true,
  notes=excluded.notes,
  updated_at=now();

alter table public.program_package_addons enable row level security;
drop policy if exists program_package_addons_management_read on public.program_package_addons;
create policy program_package_addons_management_read on public.program_package_addons
for select to authenticated using (
  exists (
    select 1 from public.profiles p
    where p.id=(select auth.uid()) and p.is_active=true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans','Admin','Sales')
  )
);
grant select on public.program_package_addons to authenticated;

comment on table public.sales_portfolio_targets is 'Monthly Sales target across ALL GMU EduTrans programs; not a station-only target.';
comment on view public.v_sales_portfolio_monthly is 'Automatic monthly aggregation: paid pax across different programs based on first verified positive payment per booking.';
comment on table public.program_package_economics is 'Internal package economics. Never expose HPP/fees/profit through public web catalog.';
comment on table public.program_package_addons is 'Optional extra ticketing outside the main Edukasi di Atas Kereta program package.';
