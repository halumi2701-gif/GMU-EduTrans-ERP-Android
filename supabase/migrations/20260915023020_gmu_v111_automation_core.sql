-- GMU EduTrans ERP v11.1 — Transactional Automation Core
-- Applied to production project gtgnwasijweewmaubvyg as migration 20260915023020.

create extension if not exists pgcrypto;

create table if not exists public.automation_events (
  id uuid primary key default gen_random_uuid(), event_key text not null unique, event_type text not null,
  entity_type text not null, entity_id text not null, booking_id text,
  actor_id uuid references public.profiles(id) on delete set null,
  payload jsonb not null default '{}'::jsonb,
  status text not null default 'PENDING' check (status in ('PENDING','PROCESSED','FAILED','IGNORED')),
  occurred_at timestamptz not null default now(), processed_at timestamptz, error_text text,
  created_at timestamptz not null default now()
);

create table if not exists public.automation_tasks (
  id uuid primary key default gen_random_uuid(), task_key text not null unique,
  source_event_id uuid references public.automation_events(id) on delete set null, booking_id text,
  assigned_to uuid references public.profiles(id) on delete set null, assigned_role text,
  task_type text not null, title text not null, description text, due_at timestamptz,
  priority text not null default 'NORMAL' check (priority in ('LOW','NORMAL','HIGH','CRITICAL')),
  status text not null default 'OPEN' check (status in ('OPEN','IN_PROGRESS','WAITING_APPROVAL','DONE','CANCELLED','OVERDUE')),
  evidence_required boolean not null default false, evidence jsonb not null default '{}'::jsonb,
  approval_required boolean not null default false, approval_id text references public.approvals(id) on delete set null,
  completed_by uuid references public.profiles(id) on delete set null, completed_at timestamptz,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);

create table if not exists public.approval_details (
  id uuid primary key default gen_random_uuid(), approval_id text not null unique references public.approvals(id) on delete cascade,
  reason text, before_data jsonb not null default '{}'::jsonb, after_data jsonb not null default '{}'::jsonb,
  financial_impact numeric(16,2), risk_level text not null default 'NORMAL' check (risk_level in ('LOW','NORMAL','HIGH','CRITICAL')),
  decision_deadline timestamptz, evidence jsonb not null default '{}'::jsonb,
  created_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);

create table if not exists public.payroll_entries (
  id uuid primary key default gen_random_uuid(), staff_id uuid not null references public.profiles(id) on delete restrict,
  booking_id text, assignment_id uuid references public.staff_assignments(id) on delete set null,
  component_type text not null, amount numeric(16,2) not null default 0 check (amount >= 0),
  state text not null default 'PENDING' check (state in ('PENDING','EARNED','APPROVED','PAID','VOID')),
  earned_at timestamptz, approved_by uuid references public.profiles(id) on delete set null,
  approved_at timestamptz, paid_at timestamptz, payment_reference text, notes text,
  created_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
  constraint payroll_assignment_component_unique unique (assignment_id, component_type)
);

create table if not exists public.recruitment_cases (
  id uuid primary key default gen_random_uuid(), position_title text not null,
  employment_type text not null default 'FREELANCER' check (employment_type in ('FREELANCER','PART_TIME','FULL_TIME','CONTRACT')),
  reason text not null, need_by date,
  status text not null default 'NEED_REVIEW' check (status in ('NEED_REVIEW','APPROVED','SOURCING','INTERVIEW','OFFER','ONBOARDING','ACTIVE','REJECTED','CANCELLED')),
  candidate_name text, candidate_contact text, owner_id uuid references public.profiles(id) on delete set null,
  approved_by uuid references public.profiles(id) on delete set null, approved_at timestamptz,
  onboarding_stage text, notes text, created_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);

create table if not exists public.capa_cases (
  id uuid primary key default gen_random_uuid(), ticket_id uuid references public.customer_support_tickets(id) on delete set null,
  booking_id text, severity text not null default 'NORMAL' check (severity in ('LOW','NORMAL','HIGH','CRITICAL')),
  root_cause text, immediate_correction text, corrective_action text, preventive_action text, service_recovery text,
  owner_id uuid references public.profiles(id) on delete set null, due_at timestamptz,
  status text not null default 'OPEN' check (status in ('OPEN','ANALYSIS','ACTION','VERIFY','CLOSED','CANCELLED')),
  evidence jsonb not null default '{}'::jsonb, closed_at timestamptz,
  created_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create unique index if not exists capa_cases_ticket_unique on public.capa_cases(ticket_id) where ticket_id is not null;

create table if not exists public.workforce_plans (
  id uuid primary key default gen_random_uuid(), period_start date not null, period_end date not null, role_name text not null,
  capacity_hours numeric(12,2) not null default 0, workload_hours numeric(12,2) not null default 0,
  staffing_gap numeric(12,2) not null default 0, cost_impact numeric(16,2) not null default 0,
  recommendation text, status text not null default 'DRAFT' check (status in ('DRAFT','REVIEW','APPROVED','ACTIONED','CLOSED')),
  owner_id uuid references public.profiles(id) on delete set null, created_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now(), check (period_end >= period_start)
);

create table if not exists public.risk_register (
  id uuid primary key default gen_random_uuid(), risk_code text not null unique, category text not null, title text not null,
  description text, likelihood smallint not null default 1 check (likelihood between 1 and 5), impact smallint not null default 1 check (impact between 1 and 5),
  residual_likelihood smallint check (residual_likelihood between 1 and 5), residual_impact smallint check (residual_impact between 1 and 5),
  owner_id uuid references public.profiles(id) on delete set null, mitigation text, due_at timestamptz,
  status text not null default 'OPEN' check (status in ('OPEN','MITIGATING','MONITORING','ACCEPTED','CLOSED')),
  last_reviewed_at timestamptz, created_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);

create table if not exists public.crm_activities (
  id uuid primary key default gen_random_uuid(), booking_id text, customer_id text,
  sales_id uuid references public.profiles(id) on delete set null,
  channel text not null check (channel in ('WHATSAPP','TELEPON','EMAIL','KUNJUNGAN','RAPAT','LAINNYA')),
  activity_type text not null, outcome text, lead_source text, next_follow_up_at timestamptz, lost_reason text, notes text,
  created_by uuid references public.profiles(id) on delete set null, created_at timestamptz not null default now()
);

create table if not exists public.ai_action_drafts (
  id uuid primary key default gen_random_uuid(), rule_key text not null, booking_id text, category text not null,
  severity text not null default 'NORMAL' check (severity in ('LOW','NORMAL','HIGH','CRITICAL')),
  title text not null, rationale text not null, recommended_action text not null, owner_role text,
  requires_approval boolean not null default true,
  status text not null default 'DRAFT' check (status in ('DRAFT','APPROVED','DISMISSED','EXECUTED','EXPIRED')),
  approved_by uuid references public.profiles(id) on delete set null, approved_at timestamptz, executed_at timestamptz,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);

create index if not exists automation_events_status_idx on public.automation_events(status, occurred_at desc);
create index if not exists automation_events_booking_idx on public.automation_events(booking_id, occurred_at desc);
create index if not exists automation_tasks_assignee_idx on public.automation_tasks(assigned_to, status, due_at);
create index if not exists automation_tasks_role_idx on public.automation_tasks(assigned_role, status, due_at);
create index if not exists automation_tasks_booking_idx on public.automation_tasks(booking_id, status);
create index if not exists payroll_entries_staff_idx on public.payroll_entries(staff_id, state, created_at desc);
create index if not exists payroll_entries_booking_idx on public.payroll_entries(booking_id, state);
create index if not exists recruitment_cases_status_idx on public.recruitment_cases(status, need_by);
create index if not exists capa_cases_status_idx on public.capa_cases(status, severity, due_at);
create index if not exists workforce_plans_period_idx on public.workforce_plans(period_start, period_end, role_name);
create index if not exists risk_register_status_idx on public.risk_register(status, category, due_at);
create index if not exists crm_activities_sales_idx on public.crm_activities(sales_id, created_at desc);
create index if not exists crm_activities_followup_idx on public.crm_activities(next_follow_up_at) where next_follow_up_at is not null;
create index if not exists ai_action_drafts_status_idx on public.ai_action_drafts(status, severity, created_at desc);

alter table public.automation_events enable row level security;
alter table public.automation_tasks enable row level security;
alter table public.approval_details enable row level security;
alter table public.payroll_entries enable row level security;
alter table public.recruitment_cases enable row level security;
alter table public.capa_cases enable row level security;
alter table public.workforce_plans enable row level security;
alter table public.risk_register enable row level security;
alter table public.crm_activities enable row level security;
alter table public.ai_action_drafts enable row level security;

create policy automation_events_read on public.automation_events for select to authenticated using (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans'))
);
create policy automation_tasks_read on public.automation_tasks for select to authenticated using (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and (p.role::text in ('Owner','Director','Manager','Manager EduTrans') or assigned_to=(select auth.uid()) or assigned_role=p.role::text))
);
create policy automation_tasks_insert on public.automation_tasks for insert to authenticated with check (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans'))
);
create policy automation_tasks_update on public.automation_tasks for update to authenticated using (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and (p.role::text in ('Owner','Director','Manager','Manager EduTrans') or assigned_to=(select auth.uid()) or assigned_role=p.role::text))
) with check (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and (p.role::text in ('Owner','Director','Manager','Manager EduTrans') or assigned_to=(select auth.uid()) or assigned_role=p.role::text))
);

create policy approval_details_read on public.approval_details for select to authenticated using (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Finance'))
  or exists (select 1 from public.approvals a where a.id=approval_id and (a.requested_by=(select auth.uid()) or a.approved_by=(select auth.uid())))
);
create policy approval_details_write on public.approval_details for all to authenticated using (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Finance'))
) with check (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Finance'))
);

create policy payroll_entries_read on public.payroll_entries for select to authenticated using (
  staff_id=(select auth.uid()) or exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Finance'))
);
create policy payroll_entries_write on public.payroll_entries for all to authenticated using (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Finance'))
) with check (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Finance'))
);

create policy recruitment_cases_manage on public.recruitment_cases for all to authenticated using (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans'))
) with check (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans'))
);
create policy capa_cases_read on public.capa_cases for select to authenticated using (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Admin','Operation'))
);
create policy capa_cases_write on public.capa_cases for all to authenticated using (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Admin','Operation'))
) with check (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Admin','Operation'))
);
create policy workforce_plans_manage on public.workforce_plans for all to authenticated using (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans'))
) with check (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans'))
);
create policy risk_register_read on public.risk_register for select to authenticated using (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Finance','Operation'))
);
create policy risk_register_write on public.risk_register for all to authenticated using (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans'))
) with check (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans'))
);
create policy crm_activities_read on public.crm_activities for select to authenticated using (
  sales_id=(select auth.uid()) or exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Admin'))
);
create policy crm_activities_insert on public.crm_activities for insert to authenticated with check (
  sales_id=(select auth.uid()) or exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans','Admin','Sales'))
);
create policy crm_activities_update on public.crm_activities for update to authenticated using (
  sales_id=(select auth.uid()) or exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans'))
) with check (
  sales_id=(select auth.uid()) or exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans'))
);
create policy ai_action_drafts_read on public.ai_action_drafts for select to authenticated using (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and (p.role::text in ('Owner','Director','Manager','Manager EduTrans') or owner_role=p.role::text))
);
create policy ai_action_drafts_write on public.ai_action_drafts for all to authenticated using (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans'))
) with check (
  exists (select 1 from public.profiles p where p.id=(select auth.uid()) and p.is_active=true and p.role::text in ('Owner','Director','Manager','Manager EduTrans'))
);

grant select on public.automation_events to authenticated;
grant select,insert,update on public.automation_tasks to authenticated;
grant select,insert,update,delete on public.approval_details to authenticated;
grant select,insert,update,delete on public.payroll_entries to authenticated;
grant select,insert,update,delete on public.recruitment_cases to authenticated;
grant select,insert,update,delete on public.capa_cases to authenticated;
grant select,insert,update,delete on public.workforce_plans to authenticated;
grant select,insert,update,delete on public.risk_register to authenticated;
grant select,insert,update on public.crm_activities to authenticated;
grant select,insert,update,delete on public.ai_action_drafts to authenticated;
