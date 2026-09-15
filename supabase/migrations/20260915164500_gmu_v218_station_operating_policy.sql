-- GMU EduTrans ERP v21.8 — Persist full operating policy for Edukasi Profesi & Lingkungan Stasiun
-- Additive to v21.6. Uses the existing RLS-protected program_sales_targets table.

alter table public.program_sales_targets
  add column if not exists operating_policy jsonb not null default '{}'::jsonb;

update public.program_sales_targets
set
  operating_policy = jsonb_build_object(
    'target_levels', jsonb_build_object(
      'bep_pax', 60,
      'productive_pax', 100,
      'target_pax', 200,
      'stretch_pax', 300,
      'outstanding_pax', 400
    ),
    'pricing', jsonb_build_object(
      'minimum_margin_pct', 35,
      'tiers', jsonb_build_array(
        jsonb_build_object('label','Reguler','price_per_pax',55000,'minimum_pax',20),
        jsonb_build_object('label','Volume','price_per_pax',46000,'minimum_pax',30)
      ),
      'approval_below_margin', true,
      'approval_roles', jsonb_build_array('Owner','Director','Direktur','Manager','Manager EduTrans')
    ),
    'sales', jsonb_build_object(
      'paid_pax_monthly', 200,
      'paid_pax_weekly', 50,
      'lead_monthly', 100,
      'followup_monthly', 200,
      'quotation_monthly', 20,
      'booking_monthly', 4,
      'minimum_cash_in', 9200000,
      'maximum_cash_in', 11000000,
      'retainer_monthly', 600000,
      'fee_per_paid_pax', 2500,
      'target_bonus_at_200_pax', 250000,
      'fee_basis', 'PAID_PAX',
      'bonus_requires_cash_in', true,
      'bonus_requires_margin', true
    ),
    'workforce', jsonb_build_object(
      'manager', jsonb_build_object('monthly_retainer',1000000,'per_session',30000),
      'admin_cs', jsonb_build_object('monthly_allocation',300000),
      'finance', jsonb_build_object('monthly_allocation',300000),
      'ops_documentation', jsonb_build_object('per_session',40000),
      'tl_mc', jsonb_build_object('per_session',110000),
      'station_head', jsonb_build_object('per_session',50000),
      'six_resource_persons', jsonb_build_object('per_session_total',120000)
    ),
    'direct_costs', jsonb_build_object(
      'partner_per_paid_pax',2500,
      'crew_meal_per_session',40000,
      'snack_per_pax',2500,
      'certificate_per_pax',500,
      'worksheet_per_pax',500
    ),
    'guardrails', jsonb_build_object(
      'minimum_margin_pct',35,
      'quotation_below_margin_requires_approval',true,
      'unauthorized_discount_forbidden',true,
      'sales_fee_paid_pax_only',true,
      'sales_bonus_requires_target_cash_in_and_margin',true
    )
  ),
  updated_at = now()
where period_month = date '2026-09-01'
  and program_key = 'EDU_STATION';

update public.performance_targets
set
  revenue_target = 9200000,
  lead_target = 100,
  followup_target = 200,
  quotation_target = 20,
  booking_target = 4,
  pax_target = 200,
  collection_target = 9200000,
  kpi_weight = coalesce(kpi_weight, '{}'::jsonb) || jsonb_build_object(
    'paid_pax_weekly',50,
    'minimum_margin_pct',35,
    'sales_income_at_target',1350000,
    'minimum_cash_in',9200000,
    'maximum_cash_in',11000000,
    'target_basis','PAID_PAX_CASH_IN_MARGIN'
  ),
  status = 'ACTIVE',
  updated_at = now()
where period_month = date '2026-09-01'
  and role_name = 'Sales'
  and staff_id is null;
