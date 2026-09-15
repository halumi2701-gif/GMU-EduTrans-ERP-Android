-- GMU EduTrans ERP v21.4 — Company Control, Payment Request, KPI, Fee Accrual and Recruitment Detail
-- Additive on top of v21.3 Compensation Capacity Autopilot. Existing ERP tables remain authoritative.

create extension if not exists pgcrypto;

-- 1) Company target and financial guardrail registry.
create table if not exists public.company_control_settings (
  id uuid primary key default gen_random_uuid(),
  setting_key text not null unique,
  numeric_value numeric(18,2),
  text_value text,
  boolean_value boolean,
  description text,
  updated_by uuid references public.profiles(id) on delete set null,
  updated_at timestamptz not null default now()
);

insert into public.company_control_settings(setting_key,numeric_value,description)
values
  ('MONTHLY_REVENUE_TARGET',50000000,'Target omzet bulanan GMU EduTrans'),
  ('HEALTHY_MARGIN_FLOOR_PCT',25,'Margin minimum sehat setelah biaya langsung dan fee'),
  ('CRITICAL_MARGIN_PCT',20,'Margin kritis; wajib eskalasi Direktur/Owner'),
  ('PIPELINE_COVERAGE_TARGET_X',3,'Pipeline minimum dibanding target omzet'),
  ('VARIABLE_INCENTIVE_CAP_PCT',6.25,'Batas total variable incentive terhadap cash revenue'),
  ('MIN_OPERATING_PROFIT_AFTER_BONUS_PCT',10,'Laba operasi minimum setelah bonus')
on conflict (setting_key) do update
set numeric_value=excluded.numeric_value,description=excluded.description,updated_at=now();

-- 2) Monthly KPI targets and snapshots. This complements, not replaces, existing scorecards.
create table if not exists public.performance_targets (
  id uuid primary key default gen_random_uuid(),
  period_month date not null,
  role_name text not null,
  staff_id uuid references public.profiles(id) on delete cascade,
  revenue_target numeric(18,2) not null default 0,
  lead_target integer not null default 0,
  followup_target integer not null default 0,
  quotation_target integer not null default 0,
  booking_target integer not null default 0,
  pax_target integer not null default 0,
  collection_target numeric(18,2) not null default 0,
  kpi_weight jsonb not null default '{}'::jsonb,
  status text not null default 'ACTIVE' check (status in ('DRAFT','ACTIVE','CLOSED','CANCELLED')),
  created_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index if not exists performance_targets_staff_unique
  on public.performance_targets(period_month,role_name,staff_id) where staff_id is not null;
create unique index if not exists performance_targets_role_unique
  on public.performance_targets(period_month,role_name) where staff_id is null;

create table if not exists public.performance_snapshots (
  id uuid primary key default gen_random_uuid(),
  period_month date not null,
  staff_id uuid not null references public.profiles(id) on delete cascade,
  role_name text not null,
  revenue_realized numeric(18,2) not null default 0,
  cash_collected numeric(18,2) not null default 0,
  pipeline_value numeric(18,2) not null default 0,
  lead_count integer not null default 0,
  followup_count integer not null default 0,
  quotation_count integer not null default 0,
  booking_count integer not null default 0,
  pax_count integer not null default 0,
  overdue_followup_count integer not null default 0,
  kpi_score numeric(6,2) not null default 0 check (kpi_score between 0 and 100),
  health_status text not null default 'WATCH' check (health_status in ('ON_TARGET','WATCH','AT_RISK','CRITICAL')),
  calculated_at timestamptz not null default now(),
  unique(period_month,staff_id)
);

-- 3) Fee/bonus accrual staging ledger. Existing payroll_entries remains the final payroll ledger.
create table if not exists public.compensation_policies (
  id uuid primary key default gen_random_uuid(),
  policy_code text not null unique,
  role_name text not null,
  component_type text not null,
  basis text not null check (basis in ('PACKAGE_FIXED','COLLECTED_REVENUE_PCT','TEAM_POOL_PCT','FIXED','QUALITY_MULTIPLIER')),
  rate numeric(10,4) not null default 0,
  threshold_min_pct numeric(10,4),
  threshold_max_pct numeric(10,4),
  multiplier numeric(10,4) not null default 1,
  margin_floor_pct numeric(10,4) not null default 25,
  active boolean not null default true,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

insert into public.compensation_policies(policy_code,role_name,component_type,basis,rate,threshold_min_pct,threshold_max_pct,multiplier,margin_floor_pct,notes)
values
  ('SALES_DEFAULT','Sales','SALES_COMMISSION','COLLECTED_REVENUE_PCT',3,0,99.99,1,25,'Default only when package master has no explicit Sales fee'),
  ('SALES_ACCEL_100','Sales','SALES_COMMISSION','COLLECTED_REVENUE_PCT',3,100,119.99,1.10,25,'Accelerator 100-119% target'),
  ('SALES_ACCEL_120','Sales','SALES_COMMISSION','COLLECTED_REVENUE_PCT',3,120,null,1.25,25,'Accelerator >=120% target'),
  ('MANAGER_BONUS_100','Manager EduTrans','TARGET_BONUS','COLLECTED_REVENUE_PCT',0.50,100,119.99,1,25,'Bonus Manager if company target is achieved and safety gates pass'),
  ('MANAGER_BONUS_120','Manager EduTrans','TARGET_BONUS','COLLECTED_REVENUE_PCT',0.75,120,null,1,25,'Bonus Manager if achievement >=120% and safety gates pass'),
  ('TEAM_POOL_100','Support','TEAM_BONUS','TEAM_POOL_PCT',1.00,100,119.99,1,25,'Team pool at 100-119% target'),
  ('TEAM_POOL_120','Support','TEAM_BONUS','TEAM_POOL_PCT',1.50,120,null,1,25,'Team pool at >=120% target')
on conflict (policy_code) do update set
  rate=excluded.rate,threshold_min_pct=excluded.threshold_min_pct,threshold_max_pct=excluded.threshold_max_pct,
  multiplier=excluded.multiplier,margin_floor_pct=excluded.margin_floor_pct,active=true,notes=excluded.notes,updated_at=now();

create table if not exists public.compensation_accruals (
  id uuid primary key default gen_random_uuid(),
  staff_id uuid not null references public.profiles(id) on delete restrict,
  booking_id text,
  policy_code text references public.compensation_policies(policy_code) on delete set null,
  source_amount numeric(18,2) not null default 0,
  calculated_amount numeric(18,2) not null default 0,
  kpi_multiplier numeric(10,4) not null default 1,
  collection_ratio numeric(10,4) not null default 0 check (collection_ratio between 0 and 1),
  margin_pct numeric(10,4),
  state text not null default 'ESTIMATED' check (state in ('ESTIMATED','ACCRUED','ELIGIBLE','PAYABLE','ON_HOLD','PAID','REVERSED','VOID')),
  hold_reason text,
  approved_by uuid references public.profiles(id) on delete set null,
  approved_at timestamptz,
  paid_at timestamptz,
  payment_reference text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- 4) One controlled entry point for every outgoing payment request.
create table if not exists public.payment_requests (
  id uuid primary key default gen_random_uuid(),
  request_no text not null unique,
  category text not null check (category in ('VENDOR','CREW','PAYROLL','COMMISSION','BONUS','REFUND','REIMBURSEMENT','OFFICE','TAX','OTHER')),
  booking_id text,
  payee_name text not null,
  payee_reference text,
  amount numeric(18,2) not null check (amount > 0),
  due_date date,
  purpose text not null,
  supporting_docs jsonb not null default '[]'::jsonb,
  requested_by uuid references public.profiles(id) on delete set null,
  requested_at timestamptz not null default now(),
  approval_state text not null default 'DRAFT' check (approval_state in ('DRAFT','SUBMITTED','APPROVED','REJECTED','CANCELLED')),
  payment_state text not null default 'UNPAID' check (payment_state in ('UNPAID','SCHEDULED','PAID','FAILED','REVERSED')),
  approved_by uuid references public.profiles(id) on delete set null,
  approved_at timestamptz,
  paid_by uuid references public.profiles(id) on delete set null,
  paid_at timestamptz,
  payment_method text,
  payment_reference text,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- 5) Recruitment detail on top of existing recruitment_cases/workforce_plans.
create table if not exists public.recruitment_candidates (
  id uuid primary key default gen_random_uuid(),
  recruitment_case_id uuid not null references public.recruitment_cases(id) on delete cascade,
  full_name text not null,
  contact text,
  source text,
  cv_reference text,
  stage text not null default 'APPLIED' check (stage in ('APPLIED','SCREENING','INTERVIEW','TRIAL','OFFER','HIRED','REJECTED','WITHDRAWN')),
  competency_score numeric(6,2) check (competency_score between 0 and 100),
  communication_score numeric(6,2) check (communication_score between 0 and 100),
  role_fit_score numeric(6,2) check (role_fit_score between 0 and 100),
  notes text,
  decided_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.recruitment_gate_reviews (
  id uuid primary key default gen_random_uuid(),
  recruitment_case_id uuid not null references public.recruitment_cases(id) on delete cascade,
  revenue_coverage_pct numeric(10,2) not null default 0,
  current_team_kpi_pct numeric(10,2) not null default 0,
  workload_utilization_pct numeric(10,2) not null default 0,
  cash_coverage_months numeric(10,2) not null default 0,
  recommendation text not null check (recommendation in ('RECRUIT','OPTIMIZE_EXISTING','FREELANCE_POOL','HOLD')),
  rationale text,
  reviewed_by uuid references public.profiles(id) on delete set null,
  reviewed_at timestamptz not null default now()
);

create index if not exists payment_requests_due_idx on public.payment_requests(payment_state,due_date);
create index if not exists payment_requests_booking_idx on public.payment_requests(booking_id,category);
create index if not exists compensation_accruals_staff_idx on public.compensation_accruals(staff_id,state,created_at desc);
create index if not exists performance_snapshots_period_idx on public.performance_snapshots(period_month,health_status);
create index if not exists recruitment_candidates_case_idx on public.recruitment_candidates(recruitment_case_id,stage);

-- 6) RLS and explicit Data API grants.
alter table public.company_control_settings enable row level security;
alter table public.performance_targets enable row level security;
alter table public.performance_snapshots enable row level security;
alter table public.compensation_policies enable row level security;
alter table public.compensation_accruals enable row level security;
alter table public.payment_requests enable row level security;
alter table public.recruitment_candidates enable row level security;
alter table public.recruitment_gate_reviews enable row level security;

drop policy if exists v214_company_control_settings_read on public.company_control_settings;
create policy v214_company_control_settings_read on public.company_control_settings for select to authenticated using (
  exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true)
);
drop policy if exists v214_company_control_settings_write on public.company_control_settings;
create policy v214_company_control_settings_write on public.company_control_settings for all to authenticated using (
  exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director'))
) with check (
  exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director'))
);

drop policy if exists v214_performance_targets_read on public.performance_targets;
create policy v214_performance_targets_read on public.performance_targets for select to authenticated using (
  staff_id=(select auth.uid()) or exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans'))
);
drop policy if exists v214_performance_targets_manage on public.performance_targets;
create policy v214_performance_targets_manage on public.performance_targets for all to authenticated using (
  exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans'))
) with check (
  exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans'))
);

drop policy if exists v214_performance_snapshots_read on public.performance_snapshots;
create policy v214_performance_snapshots_read on public.performance_snapshots for select to authenticated using (
  staff_id=(select auth.uid()) or exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans'))
);
drop policy if exists v214_performance_snapshots_manage on public.performance_snapshots;
create policy v214_performance_snapshots_manage on public.performance_snapshots for all to authenticated using (
  exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans'))
) with check (
  exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans'))
);

drop policy if exists v214_compensation_policies_read on public.compensation_policies;
create policy v214_compensation_policies_read on public.compensation_policies for select to authenticated using (
  exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true)
);
drop policy if exists v214_compensation_policies_write on public.compensation_policies;
create policy v214_compensation_policies_write on public.compensation_policies for all to authenticated using (
  exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director'))
) with check (
  exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director'))
);

drop policy if exists v214_compensation_accruals_read on public.compensation_accruals;
create policy v214_compensation_accruals_read on public.compensation_accruals for select to authenticated using (
  staff_id=(select auth.uid()) or exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Finance'))
);
drop policy if exists v214_compensation_accruals_manage on public.compensation_accruals;
create policy v214_compensation_accruals_manage on public.compensation_accruals for all to authenticated using (
  exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Finance'))
) with check (
  exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Finance'))
);

drop policy if exists v214_payment_requests_read on public.payment_requests;
create policy v214_payment_requests_read on public.payment_requests for select to authenticated using (
  requested_by=(select auth.uid()) or exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Finance'))
);
drop policy if exists v214_payment_requests_create on public.payment_requests;
create policy v214_payment_requests_create on public.payment_requests for insert to authenticated with check (
  requested_by=(select auth.uid()) and exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Finance','Admin','Operation'))
);
drop policy if exists v214_payment_requests_manage on public.payment_requests;
create policy v214_payment_requests_manage on public.payment_requests for update to authenticated using (
  exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Finance'))
) with check (
  exists(select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Finance'))
);

drop policy if exists v214_recruitment_candidates_manage on public.recruitment_candidates;
create policy v214_recruitment_candidates_manage on public.recruitment_candidates for all to authenticated using (private.gmu_v200_is_management()) with check (private.gmu_v200_is_management());
drop policy if exists v214_recruitment_gate_reviews_manage on public.recruitment_gate_reviews;
create policy v214_recruitment_gate_reviews_manage on public.recruitment_gate_reviews for all to authenticated using (private.gmu_v200_is_management()) with check (private.gmu_v200_is_management());

grant select on public.company_control_settings,public.compensation_policies to authenticated;
grant select,insert,update on public.performance_targets,public.performance_snapshots,public.compensation_accruals,public.payment_requests,public.recruitment_candidates,public.recruitment_gate_reviews to authenticated;

-- 7) Management command view. security_invoker preserves all underlying RLS rules.
create or replace view public.v_company_command_center
with (security_invoker=true)
as
select
  date_trunc('month',now())::date as period_month,
  (select numeric_value from public.company_control_settings where setting_key='MONTHLY_REVENUE_TARGET') as revenue_target,
  (select numeric_value from public.company_control_settings where setting_key='HEALTHY_MARGIN_FLOOR_PCT') as healthy_margin_floor_pct,
  (select numeric_value from public.company_control_settings where setting_key='PIPELINE_COVERAGE_TARGET_X') as pipeline_coverage_target_x,
  coalesce((select sum(revenue_realized) from public.performance_snapshots where period_month=date_trunc('month',now())::date),0) as revenue_realized,
  coalesce((select sum(cash_collected) from public.performance_snapshots where period_month=date_trunc('month',now())::date),0) as cash_collected,
  coalesce((select sum(pipeline_value) from public.performance_snapshots where period_month=date_trunc('month',now())::date),0) as pipeline_value,
  coalesce((select count(*) from public.payment_requests where approval_state='SUBMITTED'),0) as payments_waiting_approval,
  coalesce((select count(*) from public.payment_requests where payment_state='UNPAID' and due_date<current_date),0) as overdue_payments,
  coalesce((select count(*) from public.recruitment_cases where status in ('NEED_REVIEW','APPROVED','SOURCING','INTERVIEW','OFFER','ONBOARDING')),0) as open_recruitments,
  coalesce((select count(*) from public.compensation_accruals where state='ON_HOLD'),0) as compensation_on_hold;

grant select on public.v_company_command_center to authenticated;
