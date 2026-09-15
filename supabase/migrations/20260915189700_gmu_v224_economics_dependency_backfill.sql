-- GMU EduTrans v22.4 dependency backfill.
-- Production may not yet contain the v22.1 program_package_economics table.
-- This migration makes v22.4 self-contained without exposing HPP to Sales.

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

alter table public.program_package_economics enable row level security;

drop policy if exists program_package_economics_management_read on public.program_package_economics;
create policy program_package_economics_management_read on public.program_package_economics
for select to authenticated using (
  exists (
    select 1 from public.profiles p
    where p.id=(select auth.uid())
      and p.is_active=true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans')
  )
);

grant select on public.program_package_economics to authenticated;
