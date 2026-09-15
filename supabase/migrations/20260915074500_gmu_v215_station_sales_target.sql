-- GMU EduTrans ERP v21.5 — Program Sales Targeting: Edukasi Stasiun
-- Additive on top of v21.4. Stores program-level monthly targeting separately from company target cascade.

create table if not exists public.program_sales_targets (
  id uuid primary key default gen_random_uuid(),
  period_month date not null,
  program_key text not null,
  program_name text not null,
  price_per_pax numeric(18,2) not null check (price_per_pax >= 0),
  bep_pax integer not null default 0 check (bep_pax >= 0),
  productive_pax integer not null default 0 check (productive_pax >= 0),
  target_pax integer not null default 0 check (target_pax >= 0),
  next_target_pax integer not null default 0 check (next_target_pax >= 0),
  sales_retainer numeric(18,2) not null default 0 check (sales_retainer >= 0),
  sales_fee_per_pax numeric(18,2) not null default 0 check (sales_fee_per_pax >= 0),
  target_bonus numeric(18,2) not null default 0 check (target_bonus >= 0),
  contribution_target numeric(18,2) not null default 0 check (contribution_target >= 0),
  contribution_margin_pct numeric(8,2) not null default 0 check (contribution_margin_pct >= 0 and contribution_margin_pct <= 100),
  status text not null default 'ACTIVE' check (status in ('DRAFT','ACTIVE','ACHIEVED','SUPERSEDED','CANCELLED')),
  notes text,
  created_by uuid references public.profiles(id) on delete set null,
  updated_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(period_month, program_key)
);

create index if not exists program_sales_targets_period_status_idx
  on public.program_sales_targets(period_month,status,program_key);

alter table public.program_sales_targets enable row level security;

drop policy if exists v215_program_sales_targets_read on public.program_sales_targets;
create policy v215_program_sales_targets_read
on public.program_sales_targets
for select
to authenticated
using (
  exists (
    select 1
    from public.profiles p
    where p.id = (select auth.uid())
      and p.is_active = true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans','Sales')
  )
);

drop policy if exists v215_program_sales_targets_manage on public.program_sales_targets;
create policy v215_program_sales_targets_manage
on public.program_sales_targets
for all
to authenticated
using (
  exists (
    select 1
    from public.profiles p
    where p.id = (select auth.uid())
      and p.is_active = true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans')
  )
)
with check (
  exists (
    select 1
    from public.profiles p
    where p.id = (select auth.uid())
      and p.is_active = true
      and p.role::text in ('Owner','Director','Direktur','Manager','Manager EduTrans')
  )
);

grant select on public.program_sales_targets to authenticated;
grant insert,update,delete on public.program_sales_targets to authenticated;

insert into public.program_sales_targets(
  period_month,
  program_key,
  program_name,
  price_per_pax,
  bep_pax,
  productive_pax,
  target_pax,
  next_target_pax,
  sales_retainer,
  sales_fee_per_pax,
  target_bonus,
  contribution_target,
  contribution_margin_pct,
  status,
  notes
)
values (
  date '2026-09-01',
  'EDU_STATION',
  'Edukasi Stasiun',
  46000,
  60,
  200,
  400,
  600,
  600000,
  2500,
  250000,
  8000000,
  43.50,
  'ACTIVE',
  'Target September 2026: BEP 60 pax; minimum produktif 200 pax; target bulan ini 400 paid pax; target normal berikutnya 600 pax. Fee Sales Rp2.500/paid pax = Rp50.000/20 pax = Rp100.000/40 pax. Bonus hanya bila cash-in dan margin sehat.'
)
on conflict (period_month, program_key) do update set
  program_name = excluded.program_name,
  price_per_pax = excluded.price_per_pax,
  bep_pax = excluded.bep_pax,
  productive_pax = excluded.productive_pax,
  target_pax = excluded.target_pax,
  next_target_pax = excluded.next_target_pax,
  sales_retainer = excluded.sales_retainer,
  sales_fee_per_pax = excluded.sales_fee_per_pax,
  target_bonus = excluded.target_bonus,
  contribution_target = excluded.contribution_target,
  contribution_margin_pct = excluded.contribution_margin_pct,
  status = excluded.status,
  notes = excluded.notes,
  updated_at = now();

-- Keep the existing generic performance target aligned with the active September program target.
insert into public.performance_targets(
  period_month,
  role_name,
  staff_id,
  revenue_target,
  lead_target,
  followup_target,
  quotation_target,
  booking_target,
  pax_target,
  collection_target,
  kpi_weight,
  status
)
values (
  date '2026-09-01',
  'Sales',
  null,
  18400000,
  100,
  200,
  20,
  4,
  400,
  18400000,
  jsonb_build_object(
    'program_key','EDU_STATION',
    'program_name','Edukasi Stasiun',
    'bep_pax',60,
    'productive_pax',200,
    'target_pax',400,
    'next_target_pax',600,
    'price_per_pax',46000,
    'sales_retainer',600000,
    'sales_fee_per_pax',2500,
    'target_bonus',250000,
    'target_revenue',18400000,
    'contribution_target',8000000,
    'contribution_margin_pct',43.5,
    'basis','PAID_PAX_AND_CASH_IN'
  ),
  'ACTIVE'
)
on conflict (period_month,role_name) where staff_id is null do update set
  revenue_target = excluded.revenue_target,
  lead_target = excluded.lead_target,
  followup_target = excluded.followup_target,
  quotation_target = excluded.quotation_target,
  booking_target = excluded.booking_target,
  pax_target = excluded.pax_target,
  collection_target = excluded.collection_target,
  kpi_weight = excluded.kpi_weight,
  status = 'ACTIVE',
  updated_at = now();
