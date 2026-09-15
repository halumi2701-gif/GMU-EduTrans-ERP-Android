-- GMU EduTrans ERP v21.6 — Edukasi Stasiun target 200 pax + two-tier pricing
-- Keeps the program target additive while making the September 2026 target explicit and margin-controlled.

alter table public.program_sales_targets
  add column if not exists price_tiers jsonb not null default '[]'::jsonb;

alter table public.program_sales_targets
  add column if not exists minimum_margin_pct numeric(8,2) not null default 35
  check (minimum_margin_pct >= 0 and minimum_margin_pct <= 100);

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
  minimum_margin_pct,
  price_tiers,
  status,
  notes
)
values (
  date '2026-09-01',
  'EDU_STATION',
  'Edukasi Profesi & Lingkungan Stasiun',
  55000,
  60,
  100,
  200,
  300,
  600000,
  2500,
  250000,
  3220000,
  35.00,
  35.00,
  jsonb_build_array(
    jsonb_build_object('label','Reguler','price_per_pax',55000,'minimum_pax',20),
    jsonb_build_object('label','Volume','price_per_pax',46000,'minimum_pax',30)
  ),
  'ACTIVE',
  'September 2026: BEP 60 pax; produktif 100 pax; target utama 200 paid pax; stretch 300 pax; outstanding 400 pax. Tier Reguler Rp55.000/pax minimum 20 pax. Tier Volume Rp46.000/pax minimum 30 pax. Margin minimum 35%. Sales: retainer Rp600.000 + Rp2.500/paid pax + bonus Rp250.000 saat 200 paid pax tercapai, cash-in masuk, dan margin terjaga.'
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
  minimum_margin_pct = excluded.minimum_margin_pct,
  price_tiers = excluded.price_tiers,
  status = excluded.status,
  notes = excluded.notes,
  updated_at = now();

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
  9200000,
  100,
  200,
  20,
  4,
  200,
  9200000,
  jsonb_build_object(
    'program_key','EDU_STATION',
    'program_name','Edukasi Profesi & Lingkungan Stasiun',
    'bep_pax',60,
    'productive_pax',100,
    'target_pax',200,
    'stretch_pax',300,
    'outstanding_pax',400,
    'price_tiers',jsonb_build_array(
      jsonb_build_object('label','Reguler','price_per_pax',55000,'minimum_pax',20),
      jsonb_build_object('label','Volume','price_per_pax',46000,'minimum_pax',30)
    ),
    'minimum_margin_pct',35,
    'sales_retainer',600000,
    'sales_fee_per_pax',2500,
    'target_bonus',250000,
    'minimum_revenue_target',9200000,
    'maximum_revenue_target',11000000,
    'minimum_contribution_target',3220000,
    'basis','PAID_PAX_CASH_IN_AND_MARGIN'
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
